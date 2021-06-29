package com.speakerboxlite.rxentity

import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.lang.IllegalArgumentException
import java.lang.ref.WeakReference

data class SingleParams<K, E, Extra, CollectionExtra>(val refreshing: Boolean = false,
                                                      val resetCache: Boolean = false,
                                                      val first: Boolean = false,
                                                      val key: K? = null,
                                                      val last: E? = null,
                                                      val extra: Extra? = null,
                                                      val collectionExtra: CollectionExtra? = null)

interface EntityFetchExceptionInterface
{
    val code: Int
}

class EntityFetchException(override val code: Int): IllegalArgumentException(), EntityFetchExceptionInterface

typealias SingleFetchCallback<K, E, Extra, CollectionExtra> = (SingleParams<K, E, Extra, CollectionExtra>) -> Single<Optional<E>>

class SingleObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(holder: EntityCollection<K, E>,
                                                                                              queue: Scheduler,
                                                                                              key: K? = null,
                                                                                              extra: Extra? = null,
                                                                                              collectionExtra: CollectionExtra? = null,
                                                                                              start: Boolean = true,
                                                                                              fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>(holder, queue, key, extra)
{
    private val _rxRefresh = PublishSubject.create<SingleParams<K, E, Extra, CollectionExtra>>()
    private var started = false

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: E, refresh: Boolean, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, key = initial._key, collectionExtra = collectionExtra, start = refresh, fetch = fetch)
    {
        val weak = WeakReference(this)

        val disp = Single.just(true)
            .observeOn(queue)
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(weak.get()?.uuid ?: "", initial) ?: Single.just(initial) }
            .subscribe { v ->
                weak.get()?.rxPublish?.onNext(v)
                weak.get()?.rxState?.onNext(State.Ready)
            }

        dispBag.add(disp)
        started = !refresh
    }

    init
    {
        val weak = WeakReference(this)
        val disp = _rxRefresh
            .doOnNext { weak.get()?.rxLoader?.onNext(if (it.first) Loading.FirstLoading else Loading.Loading) }
            .switchMap {
                e: SingleParams<K, E, Extra, CollectionExtra> ->
                fetch( e )
                    .toObservable()
                    .flatMap {
                        if (it.value == null) error(EntityFetchException(404)) else just(it.value)
                    }
                    .onErrorResumeNext {
                        t: Throwable ->
                        if (t is EntityFetchExceptionInterface)
                            weak.get()?.rxState?.onNext(State.NotFound)
                        else
                            weak.get()?.rxError?.onNext(t)
                        weak.get()?.rxLoader?.onNext(Loading.None)
                        empty<E>()
                    }
            }
            .observeOn(queue)
            .doOnNext {
                weak.get()?.key = it._key
                weak.get()?.rxLoader?.onNext(Loading.None)
                weak.get()?.rxState?.onNext(State.Ready)
            }
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(weak.get()?.uuid ?: "", entity = it)?.toObservable() ?: empty<E>() }
            .subscribe { weak.get()?.rxPublish?.onNext(it) }

        dispBag.add(disp)
/*
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
*/
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
        super._refresh(resetCache = resetCache, extra = extra)
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        _rxRefresh.onNext(SingleParams(refreshing = true, resetCache = resetCache, first = !started, key = key, last = entity, extra = this.extra, collectionExtra = this.collectionExtra))
        started = true
    }
}

typealias SingleObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraString<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
