package com.speakerboxlite.rxentity

import io.reactivex.Observable
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
                                              val keys: List<K> = listOf(),
                                              val extra: Extra? = null,
                                              val collectionExtra: CollectionExtra? = null)

typealias PageFetchCallback<K, E, Extra, CollectionExtra> = (PageParams<K, Extra, CollectionExtra>) -> Observable<List<E>>

class PaginatorObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(holder: EntityCollection<K, E>,
                                                                                                 queue: Scheduler,
                                                                                                 keys: List<K> = listOf(),
                                                                                                 extra: Extra? = null,
                                                                                                 collectionExtra: CollectionExtra? = null,
                                                                                                 perPage: Int = 35,
                                                                                                 start: Boolean = true,
                                                                                                 combineSources: List<CombineSource<E>> = listOf(),
                                                                                                 fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): PaginatorObservableExtra<K, E, Extra>(holder, queue, keys, perPage, extra, combineSources)
{
    protected val rxPage = PublishSubject.create<PageParams<K, Extra, CollectionExtra>>()
    protected val rxMiddleware = BehaviorSubject.create<List<E>>()

    var collectionExtra: CollectionExtra? = collectionExtra
        protected set
    protected var started = false

    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: List<E>, combineSources: List<CombineSource<E>> = listOf(), fetch: PageFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, keys = initial.map { it._key }, collectionExtra = collectionExtra, start = false, combineSources = combineSources, fetch = fetch)
    {
        rxMiddleware.onNext(initial)
        started = true
        page = PAGINATOR_END
    }

    init
    {
        val weak = WeakReference(this)
        var obs = rxPage
                .filter { it.page >= 0 }
                .doOnNext { weak.get()?.rxLoader?.onNext(true) }
                .switchMap {
                    fetch(it)
                        .doOnNext { this.keys = it.map { it._key } }
                        .onErrorReturn {
                            weak.get()?.rxError?.onNext(it)
                            return@onErrorReturn listOf<E>()
                        }
                }
                .switchMap { weak.get()?.collection?.get()?.RxUpdate(source = weak.get()?.uuid ?: "", entities = it)?.toObservable() ?: just(listOf()) }
                .observeOn(queue)
                .map { weak.get()?.append(entities = it) ?: listOf() }
                .doOnNext { weak.get()?.rxLoader?.onNext(false) }

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

        if (start)
        {
            started = true
            rxPage.onNext(PageParams(page = 0, perPage = perPage, first = true, keys = keys, extra = extra, collectionExtra = collectionExtra))
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
        if (rxLoader.value == true)
            return

        if (started)
            rxPage.onNext(PageParams(page = page + 1, perPage = perPage, keys = keys, extra = extra, collectionExtra = collectionExtra))
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
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be updated only from the specified in the constructor OperationQueue" )

        super._refresh(resetCache = resetCache, extra = extra)
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        rxPage.onNext(PageParams(page = page + 1, perPage = perPage, refreshing = true, resetCache = resetCache, first = !started, keys = keys, extra = this.extra, collectionExtra = this.collectionExtra))
        started = true
    }
}

typealias PaginatorObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionInt<Entity, Extra> = PaginatorObservableCollectionExtraInt<Entity, Extra, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionLong<Entity, Extra> = PaginatorObservableCollectionExtraLong<Entity, Extra, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableCollectionExtraString<Entity, Extra, CollectionExtra> = PaginatorObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
typealias PaginatorObservableCollectionString<Entity, Extra> = PaginatorObservableCollectionExtraString<Entity, Extra, EntityCollectionExtraParamsEmpty>
