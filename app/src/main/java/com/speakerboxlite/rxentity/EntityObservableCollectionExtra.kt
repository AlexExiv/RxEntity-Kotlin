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

    /**
     *
     *
     * @param initial initial value for the single
     * @return SingleObservable object
     */
    override fun createSingle(initial: E): SingleObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
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
    fun createSingle(key: K, start: Boolean = true): SingleObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
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
    fun createSingle(key: K, start: Boolean = true, fetch: SingleFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): SingleObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
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
    fun <Extra> createSingleExtra(key: K, extra: Extra? = null, start: Boolean = true, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key= key, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun createArray(start: Boolean = true, fetch: (PageParams<EntityCollectionExtraParamsEmpty, CollectionExtra>) -> Single<List<E>>): ArrayObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun <Extra> createArrayExtra(extra: Extra? = null, start: Boolean = true, fetch: (PageParams<Extra, CollectionExtra>) -> Single<List<E>>): ArrayObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun createPaginator(perPage: Int = 35, start: Boolean = true, fetch: (PageParams<EntityCollectionExtraParamsEmpty, CollectionExtra>) -> Single<List<E>>): PaginatorObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, perPage = perPage, start = start, fetch = fetch)
    }

    fun <Extra> createPaginatorExtra(extra: Extra? = null, perPage: Int = 35, start: Boolean = true, fetch: (PageParams<Extra, CollectionExtra>) -> Single<List<E>>): PaginatorObservableExtra<K, E, Extra>
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

typealias EntityObservableCollectionExtraInt<Entity, CollectionExtra> = EntityObservableCollectionExtra<Int, Entity, CollectionExtra>
typealias EntityObservableCollectionInt<Entity> = EntityObservableCollectionExtraInt<Entity, EntityCollectionExtraParamsEmpty>

typealias EntityObservableCollectionExtraLong<Entity, CollectionExtra> = EntityObservableCollectionExtra<Long, Entity, CollectionExtra>
typealias EntityObservableCollectionLong<Entity> = EntityObservableCollectionExtraLong<Entity, EntityCollectionExtraParamsEmpty>

typealias EntityObservableCollectionExtraString<Entity, CollectionExtra> = EntityObservableCollectionExtra<String, Entity, CollectionExtra>
typealias EntityObservableCollectionString<Entity> = EntityObservableCollectionExtraString<Entity, EntityCollectionExtraParamsEmpty>

fun <K: Comparable<K>, E: Entity<K>, CollectionExtra> Observable<CollectionExtra>.refresh(to: EntityObservableCollectionExtra<K, E, CollectionExtra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, collectionExtra = it) }