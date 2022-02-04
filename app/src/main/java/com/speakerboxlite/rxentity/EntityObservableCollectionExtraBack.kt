package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.isAccessible

typealias SingleFetchBackCallback<K, E, EB, Extra, CollectionExtra> = (SingleParams<K, E, Extra, CollectionExtra>) -> Single<Optional<EB>>
typealias ArrayFetchBackCallback<K, EB, Extra, CollectionExtra> = (KeyParams<K, Extra, CollectionExtra>) -> Single<List<EB>>
typealias PageFetchBackCallback<K, EB, Extra, CollectionExtra> = (PageParams<K, Extra, CollectionExtra>) -> Single<List<EB>>

class EntityObservableCollectionExtraBack<K: Comparable<K>, E: Entity<K>, EB: EntityBack<K>, CollectionExtra>(val clazz: KClass<E>, queue: Scheduler, collectionExtra: CollectionExtra? = null):
    EntityObservableCollectionExtra<K, E, CollectionExtra>(queue, collectionExtra)
{
    companion object
    {
        inline fun <K: Comparable<K>, reified E: Entity<K>, EB: EntityBack<K>, CollectionExtra> create(queue: Scheduler, collectionExtra: CollectionExtra? = null) =
            EntityObservableCollectionExtraBack<K, E, EB, CollectionExtra>(E::class, queue, collectionExtra)
    }

    var repository: EntityRepositoryInterface<K, EB>? = null
        set(value)
        {
            field = value
            if (value != null)
            {
                singleFetchCallback = {
                    if (it.key == null)
                        Single.just(Optional(null))
                    else
                        value
                            .RxGet(it.key)
                            .map { Optional(if (it.value == null) null else map(it.value)) }
                }

                arrayFetchCallback = { value.RxGet(it.keys).map { it.map { map(it) } } }
                if (value is EntityAllRepositoryInterface<K, EB>)
                {
                    allArrayFetchCallback = { value.RxFetchAll().map { it.map { map(it) } } }
                }
                else
                {
                    allArrayFetchCallback = null
                }

                reposDisp?.dispose()

                reposDisp = value.rxEntitiesUpdated
                    .observeOn(queue)
                    .subscribe {
                        val keys = it.filter { it.entity == null && it.fieldPath == null }
                        val entities = it.filter { it.entity != null && it.fieldPath == null }
                        val indirect = it.filter { it.fieldPath != null }
                            .map { k -> _sharedEntities.values.filter { k.fieldPath!!.get(it)?.equals(k.key) == true }.map { it._key } }
                            .flatten()

                        print( "Repository requested update: $it" )
                        print( "Total updates: KEYS - ${keys.size}; ENTITIES - ${entities.size}; INDIRECT - ${indirect.size}" )

                        if (keys.size == 1)
                        {
                            commitByKey(key = keys[0].key as K, operation = keys[0].operation)
                        }
                        else if (keys.size > 1)
                        {
                            commitByKeys(keys = keys.map { it.key as K }, operations = keys.map { it.operation })
                        }

                        if (indirect.size == 1)
                        {
                            commitByKey(key = indirect[0], operation = UpdateOperation.Update)
                        }
                        else if (indirect.size > 1)
                        {
                            commitByKeys(keys = indirect, operation = UpdateOperation.Update)
                        }

                        if (entities.size > 0)
                        {
                            commit(entities = entities.map { it.entity!! }.map { map(it as EB) }, operations = entities.map { it.operation })
                        }
                    }

                dispBag.add(reposDisp!!)
            }
            else
            {
                singleFetchCallback = null
                arrayFetchCallback = null
                allArrayFetchCallback = null
            }
        }

    protected var reposDisp: Disposable? = null
    protected var ebConstructor: KFunction<E>? = null

    protected fun getConstructor(): KFunction<E>
    {
        lock.lock()
        try
        {
            if (ebConstructor == null)
            {
                for (f in clazz.constructors)
                {
                    if (f.parameters.size == 1 && (f.parameters[0].type.classifier as? KClass<EB>) != null)
                    {
                        ebConstructor = f
                        f.isAccessible = true
                        break
                    }
                }

                if (ebConstructor == null)
                    assert(false) { "${clazz.simpleName} have to has constructor" }
            }

            return ebConstructor!!
        }
        finally
        {
            lock.unlock()
        }
    }

    protected fun map(eb: EB): E = getConstructor().call(eb)

    /**
     * TODO
     *
     * @param key the unique field of a entity by using it Single retrieve the entity
     * @param start the flag indicated that SingleObservable must fetch first entity immediately after it has been created
     * @param fetch the closure callback that specify method to get entities from repository
     * @return
     */
    fun createSingleBack(key: K? = null, start: Boolean = true, fetch: SingleFetchBackCallback<K, E, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>): SingleObservable<K, E>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { Optional(if (it.value == null) null else map(it.value)) } })
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
    fun <Extra> createSingleBackExtra(key: K? = null, extra: Extra? = null, start: Boolean = true, fetch: SingleFetchBackCallback<K, E, EB, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, extra = extra, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { Optional(if (it.value == null) null else map(it.value)) } })
    }

    fun createKeyArrayBack(keys: List<K> = listOf(), start: Boolean = true, fetch: ArrayFetchBackCallback<K, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>): ArrayKeyObservable<K, E>
    {
        return ArrayKeyObservableCollectionExtra(holder = this, queue = queue, keys = keys, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }

    fun <Extra> createKeyArrayBackExtra(keys: List<K> = listOf(), extra: Extra? = null, start: Boolean = true, fetch: ArrayFetchBackCallback<K, EB, Extra, CollectionExtra>): ArrayKeyObservableExtra<K, E, Extra>
    {
        return ArrayKeyObservableCollectionExtra(holder = this, queue = queue, keys = keys, extra = extra, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }

    fun createArrayBack(start: Boolean = true, fetch: PageFetchBackCallback<K, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>): ArrayObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }

    fun <Extra> createArrayBackExtra(extra: Extra? = null, start: Boolean = true, fetch: PageFetchBackCallback<K, EB, Extra, CollectionExtra>): ArrayObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }

    fun createPaginatorBack(perPage: Int = 35, start: Boolean = true, fetch: PageFetchBackCallback<K, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>): PaginatorObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, perPage = perPage, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }

    fun <Extra> createPaginatorBackExtra(extra: Extra? = null, perPage: Int = 35, start: Boolean = true, fetch: PageFetchBackCallback<K, EB, Extra, CollectionExtra>): PaginatorObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, perPage = perPage, start = start,
            fetch = { fetch(it).map { it.map { map(it) } } })
    }
}

typealias EntityObservableCollectionBack<K, Entity, EntityBack> = EntityObservableCollectionExtraBack<K, Entity, EntityBack, EntityCollectionExtraParamsEmpty>

typealias EntityObservableCollectionExtraBackInt<Entity, EntityBack, CollectionExtra> = EntityObservableCollectionExtraBack<Int, Entity, EntityBack, CollectionExtra>
typealias EntityObservableCollectionBackInt<Entity, EntityBack> = EntityObservableCollectionBack<Int, Entity, EntityBack>

typealias EntityObservableCollectionExtraBackLong<Entity, EntityBack, CollectionExtra> = EntityObservableCollectionExtraBack<Long, Entity, EntityBack, CollectionExtra>
typealias EntityObservableCollectionBackLong<Entity, EntityBack> = EntityObservableCollectionBack<Long, Entity, EntityBack>

typealias EntityObservableCollectionExtraBackString<Entity, EntityBack, CollectionExtra> = EntityObservableCollectionExtraBack<String, Entity, EntityBack, CollectionExtra>
typealias EntityObservableCollectionBackString<Entity, EntityBack> = EntityObservableCollectionBack<String, Entity, EntityBack>
