package com.speakerboxlite.rxentity

import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.functions.Function4
import io.reactivex.functions.Function5
import io.reactivex.functions.Function6
import io.reactivex.functions.Function7
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.lang.ref.WeakReference

data class PageParams<K, Extra, CollectionExtra>(val page: Int,
                                              val perPage: Int,
                                              val refreshing: Boolean = false,
                                              val resetCache: Boolean = false,
                                              val first: Boolean = false,
                                              val extra: Extra? = null,
                                              val collectionExtra: CollectionExtra? = null)

typealias PageFetchCallback<K, E, Extra, CollectionExtra> = (PageParams<K, Extra, CollectionExtra>) -> Single<List<E>>

class PaginatorObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(holder: EntityCollection<K, E>,
                                                                                                 queue: Scheduler,
                                                                                                 extra: Extra? = null,
                                                                                                 collectionExtra: CollectionExtra? = null,
                                                                                                 perPage: Int = 35,
                                                                                                 start: Boolean = true,
                                                                                                 fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): PaginatorObservableExtra<K, E, Extra>(holder, queue, perPage, extra)
{
    protected val rxPage = PublishSubject.create<PageParams<K, Extra, CollectionExtra>>()

    var collectionExtra: CollectionExtra? = collectionExtra
        protected set
    protected var started = false

    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: List<E>, fetch: PageFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, collectionExtra = collectionExtra, start = false, fetch = fetch)
    {
        val weak = WeakReference(this)
        val disp = Single.just(true)
            .observeOn(queue)
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(source = weak.get()?.uuid ?: "", entities = initial) ?: Single.just(listOf()) }
            .subscribe { v -> weak.get()?.setEntities(entities = v) }

        dispBag.add(disp)

        started = true
        page = PAGINATOR_END
    }

    init
    {
        val weak = WeakReference(this)
        val disp = rxPage
                .filter { it.page >= 0 }
                .doOnNext { weak.get()?.rxLoader?.onNext(if (it.first) Loading.FirstLoading else Loading.Loading) }
                .switchMap {
                    fetch(it)
                        .toObservable()
                        .onErrorReturn {
                            weak.get()?.rxError?.onNext(it)
                            weak.get()?.rxLoader?.onNext(Loading.None)
                            return@onErrorReturn listOf<E>()
                        }
                }
                .observeOn(queue)
                .doOnNext { weak.get()?.rxLoader?.onNext(Loading.None) }
                .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(source = weak.get()?.uuid ?: "", entities = it)?.toObservable() ?: just(listOf()) }
                .subscribe { weak.get()?.setEntities(weak.get()?.append(it) ?: listOf()) }

        dispBag.add(disp)
/*
        dispBag.add(obs.subscribe { weak.get()?.rxMiddleware?.onNext(it) })
        obs = rxMiddleware
        combineSources.forEach { ms ->
            obs = when (ms.sources.size)
            {
                1 -> combineLatest(obs, ms.sources[0], BiFunction { es, t -> es.map { ms.combine.apply(it, arrayOf(t)) } })
                2 -> combineLatest(obs, ms.sources[0], ms.sources[1], Function3 { es, t0, t1 -> es.map { ms.combine.apply(it, arrayOf(t0, t1)) } })
                3 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], Function4 { es, t0, t1, t2 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2)) } })
                4 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], Function5 { es, t0, t1, t2, t3 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3)) } })
                5 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], Function6 { es, t0, t1, t2, t3, t4 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3, t4)) } })
                6 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], ms.sources[5], Function7 { es, t0, t1, t2, t3, t4, t5 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3, t4, t5)) } })
                else -> combineLatest(obs, ms.sources[0], BiFunction { es, t -> es.map { ms.combine.apply(it, arrayOf(t)) } })
            }
        }
        dispBag.add(obs.subscribe { weak.get()?.rxPublish?.onNext(it) })
*/
        if (start)
        {
            started = true
            rxPage.onNext(PageParams(page = 0, perPage = perPage, first = true, extra = extra, collectionExtra = collectionExtra))
        }
    }

    override fun refresh(resetCache: Boolean, extra: Extra?)
    {
        collectionRefresh(resetCache = resetCache, extra = extra)
    }

    override fun _refresh(resetCache: Boolean, extra: Extra?)
    {
        _collectionRefresh(resetCache = resetCache, extra = extra)
    }

    override fun refreshData(resetCache: Boolean, data: Any?)
    {
        _collectionRefresh(resetCache = resetCache, collectionExtra = data as? CollectionExtra)
    }

    override fun next()
    {
        if (rxLoader.value!!.isLoading)
            return

        if (started)
            rxPage.onNext(PageParams(page = page + 1, perPage = perPage, extra = extra, collectionExtra = collectionExtra))
        else
            refresh()
    }

    fun collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null)
    {
        val weak = WeakReference(this)
        dispBag.add(Single.create<Boolean> {
                    weak.get()?._collectionRefresh(resetCache = resetCache, extra = extra, collectionExtra = collectionExtra)
                    it.onSuccess(true)
                }
                .observeOn(queue)
                .subscribeOn(queue)
                .subscribe())
    }

    fun _collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null)
    {
        super._refresh(resetCache = resetCache, extra = extra)
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        rxPage.onNext(PageParams(page = page + 1, perPage = perPage, refreshing = true, resetCache = resetCache, first = !started, extra = this.extra, collectionExtra = this.collectionExtra))
        started = true
    }
}

typealias PaginatorObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionInt<Entity, Extra> = PaginatorObservableCollectionExtraInt<Entity, Extra, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionLong<Entity, Extra> = PaginatorObservableCollectionExtraLong<Entity, Extra, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableCollectionExtraString<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionString<Entity, Extra> = PaginatorObservableCollectionExtraString<Entity, Extra, EntityCollectionExtraParamsEmpty>
