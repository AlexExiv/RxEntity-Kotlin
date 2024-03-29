package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock

data class EntityCollectionExtraParamsEmpty(val unused: Int = 0)

abstract class EntityCollection<K: Comparable<K>, E: Entity<K>>(val queue: Scheduler)
{
    protected val lock = ReentrantLock()

    protected val items = mutableListOf<WeakReference<EntityObservable<K, E, *>>>()

    val sharedEntities: Map<K, E> get() = _sharedEntities
    protected val _sharedEntities = mutableMapOf<K, E>()

    protected val dispBag = CompositeDisposable()

    fun add(obs: EntityObservable<K, E, *>)
    {
        lock.lock()
        try
        {
            items.add(WeakReference(obs))
        }
        finally
        {
            lock.unlock()
        }
    }

    fun remove(obs: EntityObservable<K, E, *>)
    {
        lock.lock()
        try
        {
            items.removeAll { obs.uuid == it.get()?.uuid }
        }
        finally
        {
            lock.unlock()
        }
    }

    abstract fun RxRequestForCombine(source: String, entity: E, updateChilds: Boolean = true) : Single<E>
    abstract fun RxRequestForCombine(source: String, entities: List<E>, updateChilds: Boolean = true) : Single<List<E>>

    fun RxUpdate(source: String = "", entity: E): Single<E>
    {
        val weak = WeakReference(this)
        return Single.create<E>
                {
                    weak.get()?.update(source = source, entity = entity)
                    it.onSuccess(entity)
                }
                .observeOn(queue)
                .subscribeOn(queue)
    }

    fun RxUpdate(source: String = "", entities: List<E>): Single<List<E>>
    {
        val weak = WeakReference(this)
        return Single.create<List<E>>
                {
                    weak.get()?.update(source = source, entities = entities)
                    it.onSuccess(entities)
                }
                .observeOn(queue)
                .subscribeOn(queue)
    }

    open fun update(source: String = "", entity: E)
    {
        lock.lock()
        try
        {
            _sharedEntities[entity._key] = entity
            items.forEach { it.get()?.update(source = "", entity = entity) }
        }
        finally
        {
            lock.unlock()
        }
    }

    open fun update(source: String = "", entities: List<E>)
    {
        lock.lock()
        try
        {
            val map = entities.associateBy {
                _sharedEntities[it._key] = it
                it._key
            }
            items.forEach { it.get()?.update(source = "", entities = map) }
        }
        finally
        {
            lock.unlock()
        }
    }

    abstract fun commit(entity: E, operation: UpdateOperation)
    abstract fun commitByKey(key: K, operation: UpdateOperation)
    abstract fun commitByKey(key: K, changes: (E) -> E)
    abstract fun commit(entities: List<E>, operation: UpdateOperation)
    abstract fun commit(entities: List<E>, operations: List<UpdateOperation>)
    abstract fun commitByKeys(keys: List<K>, operation: UpdateOperation)
    abstract fun commitByKeys(keys: List<K>, operations: List<UpdateOperation>)
    abstract fun commitByKeys(keys: List<K>, changes: (E) -> E)
    abstract fun commitDeleteByKeys(keys: Set<K>)
    abstract fun commitClear()

    abstract fun createSingle(initial: E, refresh: Boolean = false): SingleObservable<K, E>
    abstract fun createKeyArray(initial: List<E>): ArrayKeyObservable<K, E>
}