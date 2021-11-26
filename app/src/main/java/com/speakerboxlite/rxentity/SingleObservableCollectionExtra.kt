package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
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
                                                                                              key: K?,
                                                                                              extra: Extra? = null,
                                                                                              collectionExtra: CollectionExtra? = null,
                                                                                              start: Boolean = true,
                                                                                              fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>(holder, queue, key, extra)
{
    private val _rxRefresh = BehaviorSubject.create<SingleParams<K, E, Extra, CollectionExtra>>()
    private var started = false

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    override var key: K?
        get() = super.key
        set(value)
        {
            lock.lock()
            super.key = value

            val params = _rxRefresh.value
            _rxRefresh.onNext(SingleParams(resetCache = true, first = true, key = value, extra = params?.extra, collectionExtra = params?.collectionExtra))

            lock.unlock()
        }


    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: E, refresh: Boolean, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, key = initial._key, collectionExtra = collectionExtra, start = refresh, fetch = fetch)
    {
        val weak = WeakReference(this)

        val disp = Single.just(true)
            .observeOn(queue)
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(weak.get()?.uuid ?: "", initial) ?: Single.just(initial) }
            .subscribe { v ->
                weak.get()?.publish(v)
                weak.get()?.rxState?.onNext(State.Ready)
            }

        dispBag.add(disp)
        started = !refresh
    }

    init
    {
        val weak = WeakReference(this)
        val disp = _rxRefresh
            .doOnNext {
                weak.get()?.rxLoader?.onNext(if (it.first) Loading.FirstLoading else Loading.Loading)
                if (it.first)
                    weak.get()?.rxState?.onNext(State.Initializing)
            }
            .switchMap {
                e: SingleParams<K, E, Extra, CollectionExtra> ->
                fetch( e )
                    .toObservable()
                    .flatMap {
                        if (it.value == null) error(EntityFetchException(404)) else just(Optional(it.value))
                    }
                    .onErrorResumeNext {
                        t: Throwable ->
                        if (t is EntityFetchExceptionInterface)
                            weak.get()?.rxState?.onNext(State.NotFound)
                        else
                            weak.get()?.rxError?.onNext(t)
                        just(Optional(null))
                    }
            }
            .observeOn(queue)
            .doOnNext {
                //weak.get()?.setSuperKey(it.value?._key)
                weak.get()?.rxLoader?.onNext(Loading.None)
                weak.get()?.rxState?.onNext(if (it.exist()) State.Ready else State.NotFound)
            }
            .flatMap {
                if (it.value == null)
                    just(Optional(null))
                else
                    weak.get()?.collection?.get()?.RxRequestForCombine(weak.get()?.uuid ?: "", entity = it.value)?.map { Optional(it) }?.toObservable() ?: empty<Optional<E?>>() }
            .subscribe { weak.get()?.rxPublish?.onNext(it as Optional<E?>) }

        dispBag.add(disp)

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

    private fun setSuperKey(key: K?)
    {
        lock.lock()
        try
        {
            if (key != null)
                super.key = key
        }
        finally
        {
            lock.unlock()
        }
    }
}

typealias SingleObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraString<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
