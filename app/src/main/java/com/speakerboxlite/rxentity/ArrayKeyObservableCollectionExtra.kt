package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import java.lang.ref.WeakReference

data class KeyParams<K, Extra, CollectionExtra>(val refreshing: Boolean = false,
                                                val resetCache: Boolean = false,
                                                val first: Boolean = false,
                                                val keys: List<K>,
                                                val extra: Extra? = null,
                                                val collectionExtra: CollectionExtra? = null)

typealias ArrayFetchCallback<K, E, Extra, CollectionExtra> = (KeyParams<K, Extra, CollectionExtra>) -> Single<List<E>>

class ArrayKeyObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, Extra, CollectionExtra>(
    holder: EntityCollection<K, E>,
    keys: List<K>,
    start: Boolean = true,
    extra: Extra? = null,
    protected var collectionExtra: CollectionExtra? = null,
    queue: Scheduler,
    fetch: ArrayFetchCallback<K, E, Extra, CollectionExtra>): ArrayKeyObservableExtra<K, E, Extra>(holder, keys, extra, queue)
{
    val rxKeys = PublishSubject.create<KeyParams<K, Extra, CollectionExtra>>()

    override var _keys: MutableList<K>
        get() = super._keys
        set(value)
        {
            lock.lock()
            super._keys = value
            try
            {
                rxKeys.onNext(KeyParams(keys = value, extra = extra, collectionExtra = collectionExtra))
            }
            finally
            {
                lock.unlock()
            }
        }

    init
    {
        val weak = WeakReference(this)
        val disp = rxKeys
            .filter { it.keys.isNotEmpty() }
            .doOnNext { weak.get()?.updateLoading(if (it.first) Loading.FirstLoading else Loading.Loading, null) }
            .observeOn(queue)
            .switchMap {
                (weak.get()?.RxFetchElements(params = it, fetch = fetch) ?: Single.just(listOf()))
                    .toObservable()
                    .onErrorReturn {
                        weak.get()?.updateLoading(Loading.None, it)
                        listOf()
                    }
            }
            .observeOn(queue)
            .doOnNext { weak.get()?.updateLoading(Loading.None) }
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(source = weak.get()?.uuid ?: "", entities = it)?.toObservable() ?: just(listOf()) }
            .subscribe { v -> weak.get()?.setEntities(entities = v) }

        val keysDisp = rxKeys
            .filter { it.keys.isEmpty() }
            .observeOn(queue)
            .doOnNext { weak.get()?.updateLoading(Loading.None) }
            .subscribe { weak.get()?.setEntities(entities = listOf()) }

        dispBag.add(disp)
        dispBag.add(keysDisp)

        if (start)
            rxKeys.onNext(KeyParams(first = true, keys = keys, extra = extra, collectionExtra = collectionExtra))
    }


    constructor(holder: EntityCollection<K, E>, initial: List<E>, collectionExtra: CollectionExtra? = null, queue: Scheduler, fetch: ArrayFetchCallback<K, E, Extra, CollectionExtra>) :
            this(holder = holder, keys = initial.map { it._key }.toList(), start = false, collectionExtra = collectionExtra, queue = queue, fetch = fetch)
    {
        val weak = WeakReference(this)
        val disp = Single
            .just(true)
            .observeOn(queue)
            .flatMap { weak.get()?.collection?.get()?.RxRequestForCombine(source = weak.get()?.uuid ?: "", entities = initial) ?: Single.just(listOf()) }
            .subscribe { v -> weak.get()?.setEntities(entities = v) }

        dispBag.add(disp)
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

    fun collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null) {
        val disp = Single.create<Boolean> {
                this._collectionRefresh(resetCache = resetCache, extra = extra, collectionExtra = collectionExtra)
                it.onSuccess(true)
            }
            .observeOn(queue)
            .subscribeOn(queue)
            .subscribe()

        dispBag.add(disp)
    }

    fun _collectionRefresh(resetCache: Boolean = false, extra: Extra? = null, collectionExtra: CollectionExtra? = null)
    {
        super._refresh(resetCache = resetCache, extra = extra)

        this.collectionExtra = collectionExtra ?: this.collectionExtra
        rxKeys.onNext(KeyParams(refreshing = true, resetCache = resetCache, keys = keys, extra = this.extra, collectionExtra = this.collectionExtra))
    }

    private fun RxFetchElements(params: KeyParams<K, Extra, CollectionExtra>, fetch: ArrayFetchCallback<K, E, Extra, CollectionExtra>) : Single<List<E>>
    {
        if (params.refreshing)
            return fetch(params)

        val exist = params.keys.mapNotNull { k -> collection.get()?.sharedEntities?.get(k) ?: entities.firstOrNull { k == it._key } }

        if (exist.size != keys.size)
        {
            val _params = KeyParams(refreshing = params.refreshing, resetCache = params.resetCache, first = params.first, keys = params.keys.filter { collection.get()?.sharedEntities?.get(it) == null }, extra = extra, collectionExtra = collectionExtra)
            return zip(just(exist), fetch(_params).toObservable(), { e, n ->
                    val _exist = e.toEntitiesMap()
                    val new = n.toEntitiesMap()
                    params.keys.mapNotNull { _exist[it] ?: new[it] }
                })
                .first(listOf())
        }

        return Single.just(exist)
    }
}