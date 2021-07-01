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

    val items = mutableListOf<WeakReference<EntityObservable<K, E, *>>>()
    val sharedEntities = mutableMapOf<K, E>()

    val dispBag = CompositeDisposable()

    fun add(obs: EntityObservable<K, E, *>)
    {
        items.add(WeakReference(obs))
    }

    fun remove(obs: EntityObservable<K, E, *>)
    {
        items.removeAll { obs.uuid == it.get()?.uuid }
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
        sharedEntities[entity._key] = entity
    }

    open fun update(source: String = "", entities: List<E>)
    {
        entities.forEach { sharedEntities[it._key] = it }
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