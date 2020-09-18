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

data class SingleParams<K, E, Extra, CollectionExtra>(val refreshing: Boolean = false,
                                                      val resetCache: Boolean = false,
                                                      val first: Boolean = false,
                                                      val key: K? = null,
                                                      val last: E? = null,
                                                      val extra: Extra? = null,
                                                      val collectionExtra: CollectionExtra? = null)

typealias SingleFetchCallback<K, E, Extra, CollectionExtra> = (SingleParams<K, E, Extra, CollectionExtra>) -> Single<E>

class SingleObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(holder: EntityCollection<K, E>,
                                                                                              queue: Scheduler,
                                                                                              key: K? = null,
                                                                                              extra: Extra? = null,
                                                                                              collectionExtra: CollectionExtra? = null,
                                                                                              start: Boolean = true,
                                                                                              combineSources: List<CombineSource<E>> = listOf(),
                                                                                              fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>(holder, queue, key, extra, combineSources)
{
    protected val rxMiddleware = BehaviorSubject.create<E>()
    private val _rxRefresh = PublishSubject.create<SingleParams<K, E, Extra, CollectionExtra>>()
    private var started = false

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: E, refresh: Boolean, combineSources: List<CombineSource<E>> = listOf(), fetch: SingleFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, key = initial._key, collectionExtra = collectionExtra, start = refresh, combineSources = combineSources, fetch = fetch)
    {
        rxMiddleware.onNext(initial)
        started = !refresh
    }

    init
    {
        val weak = WeakReference(this)
        var obs = _rxRefresh
            .doOnNext { weak.get()?.rxLoader?.onNext( true ) }
            .switchMap {
                e: SingleParams<K, E, Extra, CollectionExtra> ->
                fetch( e )
                    .toObservable()
                    .doOnNext { this.key = it._key }
                    .onErrorResumeNext {
                        t: Throwable ->
                        weak.get()?.rxError?.onNext(t)
                        weak.get()?.rxLoader?.onNext( false )
                        empty<E>()
                    }
            }
            .doOnNext { weak.get()?.rxLoader?.onNext( false ) }
            .switchMap { weak.get()?.collection?.get()?.RxUpdate(entity = it)?.toObservable() ?: empty<E>() }
            .observeOn(queue)

        dispBag.add(obs.subscribe { weak.get()?.rxMiddleware?.onNext(it) })
        obs = rxMiddleware
        combineSources.forEach { ms ->
            obs = when (ms.sources.size)
            {
                1 -> combineLatest(obs, ms.sources[0], BiFunction { es, t -> ms.combine.apply(es, arrayOf(t)) })
                2 -> combineLatest(obs, ms.sources[0], ms.sources[1], Function3 { es, t0, t1 -> ms.combine.apply(es, arrayOf(t0, t1)) })
                3 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], Function4 { es, t0, t1, t2 -> ms.combine.apply(es, arrayOf(t0, t1, t2)) })
                4 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], Function5 { es, t0, t1, t2, t3 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3)) })
                5 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], Function6 { es, t0, t1, t2, t3, t4 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3, t4)) })
                6 -> combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], ms.sources[5], Function7 { es, t0, t1, t2, t3, t4, t5 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3, t4, t5)) })
                else -> combineLatest(obs, ms.sources[0], BiFunction { es, t -> ms.combine.apply(es, arrayOf(t)) })
            }
        }
        dispBag.add(obs.subscribe { weak.get()?.rxPublish?.onNext(it) })

        if (start)
        {
            started = true
            _rxRefresh.onNext(SingleParams(first = true, key = key, last = entity, extra = extra, collectionExtra = collectionExtra))
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

    fun collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null)
    {
        val weak = WeakReference(this)
        dispBag.add(
                Single.create<Boolean> {
                    weak.get()?._collectionRefresh(resetCache = resetCache, extra = extra, collectionExtra = collectionExtra)
                    it.onSuccess(true)
                }
                .subscribeOn( queue )
                .observeOn( queue )
                .subscribe())
    }

    fun _collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null)
    {
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be updated only from the specified in the constructor OperationQueue" )

        super._refresh(resetCache = resetCache, extra = extra)
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        _rxRefresh.onNext(SingleParams(refreshing = true, resetCache = resetCache, first = !started, key = key, last = entity, extra = this.extra, collectionExtra = this.collectionExtra))
        started = true
    }
}

typealias SingleObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraString<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
