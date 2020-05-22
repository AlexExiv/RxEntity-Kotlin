package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
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
                                                                                              fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>(holder, queue, key, extra)
{
    private val _rxRefresh = PublishSubject.create<SingleParams<K, E, Extra, CollectionExtra>>()
    private var started = false

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    constructor(holder: EntityCollection<K, E>, queue: Scheduler, collectionExtra: CollectionExtra? = null, initial: E, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra> ):
            this(holder = holder, queue = queue, key = initial._key, collectionExtra = collectionExtra, start = false, fetch = fetch)
    {
        rxPublish.onNext(initial)
        started = true
    }

    init
    {
        val weak = WeakReference(this)
        dispBag.add(_rxRefresh
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
                        Observable.empty<E>()
                    }
            }
            .doOnNext { weak.get()?.rxLoader?.onNext( false ) }
            .switchMap { weak.get()?.collection?.get()?.RxUpdate(entity = it)?.toObservable() ?: Observable.empty<E>() }
            .observeOn( queue )
            .subscribe { weak.get()?.rxPublish?.onNext(it) })

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
