package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.lang.ref.WeakReference

data class SingleParams<Extra, CollectionExtra>(val refreshing: Boolean = false,
                                                val resetCache: Boolean = false,
                                                val first: Boolean = false,
                                                val extra: Extra? = null,
                                                val collectionExtra: CollectionExtra? = null)

class SingleObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(holder: EntityCollection<K, E>,
                                                                                              queue: Scheduler,
                                                                                              extra: Extra? = null,
                                                                                              collectionExtra: CollectionExtra? = null,
                                                                                              start: Boolean = true,
                                                                                              fetch: (SingleParams<Extra, CollectionExtra>) -> Single<E>): SingleObservableExtra<K, E, Extra>(holder, queue, extra)
{
    private val _rxRefresh = PublishSubject.create<SingleParams<Extra, CollectionExtra>>()
    private var started = false

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    init
    {
        val weak = WeakReference(this)
        dispBag.add(_rxRefresh
            .doOnNext { weak.get()?.rxLoader?.onNext( true ) }
            .switchMap {
                e: SingleParams<Extra, CollectionExtra> ->
                fetch( e )
                    .toObservable()
                    .onErrorResumeNext {
                        t: Throwable ->
                        weak.get()?.rxError?.onNext(t)
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
            _rxRefresh.onNext(SingleParams(first = true, extra = extra, collectionExtra = collectionExtra))
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
        _rxRefresh.onNext(SingleParams(refreshing = true, resetCache = resetCache, first = !started, extra = this.extra, collectionExtra = this.collectionExtra))
        started = true
    }
}

typealias SingleObservableCollectionExtraInt<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Int, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraLong<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<Long, Entity, Extra, CollectionExtra>
typealias SingleObservableCollectionExtraString<Entity, Extra, CollectionExtra> = SingleObservableCollectionExtra<String, Entity, Extra, CollectionExtra>
