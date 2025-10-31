package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
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
                                                                                                 perPage: Int = ARRAY_PER_PAGE,
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
                .doOnNext { weak.get()?.updateLoading(if (it.first) Loading.FirstLoading else Loading.Loading) }
                .switchMap {
                    fetch(it)
                        .toObservable()
                        .onErrorReturn {
                            weak.get()?.updateLoading(Loading.None, it)
                            return@onErrorReturn listOf<E>()
                        }
                }
                .observeOn(queue)
                .doOnNext { weak.get()?.updateLoading(Loading.None) }
                .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(source = weak.get()?.uuid ?: "", entities = it)?.toObservable() ?: just(listOf()) }
                .subscribe { weak.get()?.setEntities(weak.get()?.append(it) ?: listOf()) }

        dispBag.add(disp)

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
