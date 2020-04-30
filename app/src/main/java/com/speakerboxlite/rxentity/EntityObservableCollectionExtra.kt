package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import java.lang.ref.WeakReference

class EntityObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, CollectionExtra>(queue: Scheduler, collectionExtra: CollectionExtra? = null): EntityCollection<K, E>(queue)
{
    var collectionExtra: CollectionExtra? = collectionExtra
        protected set

    var singleFetchCallback: SingleFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null
    var arrayFetchCallback: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null

    /**
     *
     *
     * @param initial initial value for the single
     * @return SingleObservable object
     */
    override fun createSingle(initial: E): SingleObservable<K, E>
    {
        assert(singleFetchCallback != null) { "To create Single with initial value you must specify singleFetchCallback before" }
        return SingleObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, initial = initial, fetch = singleFetchCallback!!)
    }

    /**
     * TODO
     *
     * @param start the flag indicated that SingleObservable must fetch first entity immediately after it has been created
     * @return
     */
    fun createSingle(key: K, start: Boolean = true): SingleObservable<K, E>
    {
        assert(singleFetchCallback != null) { "To create Single with default fetch method must specify singleFetchCallback before" }
        return createSingle(key = key, start = start, fetch = singleFetchCallback!!)
    }

    /**
     * TODO
     *
     * @param key the unique field of a entity by using it Single retrieve the entity
     * @param start the flag indicated that SingleObservable must fetch first entity immediately after it has been created
     * @param fetch the closure callback that specify method to get entities from repository
     * @return
     */
    fun createSingle(key: K? = null, start: Boolean = true, fetch: SingleFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): SingleObservable<K, E>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    /**
     * TODO
     *
     * @param Extra
     * @param extra
     * @param start
     * @param fetch
     * @return
     */
    fun <Extra> createSingleExtra(key: K? = null, extra: Extra? = null, start: Boolean = true, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    override fun createArray(initial: List<E>): ArrayObservable<K, E>
    {
        assert(arrayFetchCallback != null) { "To create Array with initial values you must specify arrayFetchCallback before" }
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, initial = initial, fetch = arrayFetchCallback!!)
    }

    fun createArray(keys: List<K>, start: Boolean = true): ArrayObservable<K, E>
    {
        assert(arrayFetchCallback != null) { "To create Array with default fetch method must specify arrayFetchCallback before" }
        return createArray(keys = keys, start = start, fetch = arrayFetchCallback!!)
    }

    fun createArray(keys: List<K> = listOf(), start: Boolean = true, fetch: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): ArrayObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, keys = keys, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun <Extra> createArrayExtra(keys: List<K> = listOf(), extra: Extra? = null, start: Boolean = true, fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): ArrayObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, keys = keys, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun createPaginator(perPage: Int = 35, start: Boolean = true, fetch: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): PaginatorObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, perPage = perPage, start = start, fetch = fetch)
    }

    fun <Extra> createPaginatorExtra(extra: Extra? = null, perPage: Int = 35, start: Boolean = true, fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): PaginatorObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, perPage = perPage, start = start, fetch = fetch)
    }

    fun RxRequestForUpdate(source: String = "", key: K, update: (E) -> E): Single<Optional<E>>
    {
        val weak = WeakReference(this)
        return Single.create<Optional<E>> {
                val entity = weak.get()?.sharedEntities?.get(key)
                if (entity != null)
                {
                    val new = update(entity)
                    weak.get()?.update(source = source, entity = update(entity))
                    it.onSuccess(Optional(new))
                }
                else
                {
                    it.onSuccess(Optional(null))
                }
            }
            .observeOn(queue)
            .subscribeOn(queue)
    }

    fun RxRequestForUpdate(source: String = "", keys: List<K>, update: (E) -> E): Single<List<E>>
    {
        val weak = WeakReference(this)
        return Single.create<List<E>> {
                val updArr = mutableListOf<E>()
                val updMap = mutableMapOf<K, E>()

                keys.forEach {
                    val entity = weak.get()?.sharedEntities?.get(it)
                    if (entity != null)
                    {
                        val new = update(entity)
                        weak.get()?.sharedEntities?.put(it, new)
                        updArr.add(new)
                        updMap[it] = new
                    }
                }

                weak.get()?.items?.forEach { it.get()?.update(source = source, entities = updMap) }
                it.onSuccess(updArr)
            }
            .observeOn(queue)
            .subscribeOn(queue)
    }

    fun RxRequestForUpdate(source: String = "", update: (E) -> E): Single<List<E>>
    {
        return RxRequestForUpdate(source = source, keys = sharedEntities.keys.map { it }, update = update)
    }

    fun refresh(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        val weak = WeakReference(this)
        dispBag.add(Single.create<Boolean> {
                weak.get()?._refresh(resetCache = resetCache, collectionExtra = collectionExtra)
                it.onSuccess(true)
            }
            .subscribeOn(queue)
            .observeOn(queue)
            .subscribe())
    }

    fun _refresh(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be called only from the specified in the constructor OperationQueue" )
        items.forEach {
            it.get()?.refreshData(resetCache = resetCache, data = this.collectionExtra as? Any)
        }
    }
}

typealias EntityObservableCollection<K, Entity> = EntityObservableCollectionExtra<K, Entity, EntityCollectionExtraParamsEmpty>

typealias EntityObservableCollectionExtraInt<Entity, CollectionExtra> = EntityObservableCollectionExtra<Int, Entity, CollectionExtra>
typealias EntityObservableCollectionInt<Entity> = EntityObservableCollection<Int, Entity>

typealias EntityObservableCollectionExtraLong<Entity, CollectionExtra> = EntityObservableCollectionExtra<Long, Entity, CollectionExtra>
typealias EntityObservableCollectionLong<Entity> = EntityObservableCollection<Long, Entity>

typealias EntityObservableCollectionExtraString<Entity, CollectionExtra> = EntityObservableCollectionExtra<String, Entity, CollectionExtra>
typealias EntityObservableCollectionString<Entity> = EntityObservableCollection<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, CollectionExtra> Observable<CollectionExtra>.refresh(to: EntityObservableCollectionExtra<K, E, CollectionExtra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, collectionExtra = it) }