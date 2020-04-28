package com.speakerboxlite.rxentity

import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import java.lang.ref.WeakReference

data class EntityCollectionExtraParamsEmpty(val unused: Int = 0)

abstract class EntityCollection<K: Comparable<K>, E: Entity<K>>(val queue: Scheduler)
{
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
        //assert(queue.operationQueue == OperationQueue.current, "Observable obss collection can be updated only from the specified in the constructor OperationQueue")

        sharedEntities[entity._key] = entity
        items.forEach { it.get()?.update(source = source, entity = entity) }
    }

    open fun update(source: String = "", entities: List<E>)
    {
        //assert(queue.operationQueue == OperationQueue.current, "Observable obss collection can be updated only from the specified in the constructor OperationQueue")

        entities.forEach { sharedEntities[it._key] = it }
        items.forEach { it.get()?.update(source = source, entities = this.sharedEntities) }
    }

    abstract fun createSingle(initial: E): SingleObservableExtra<K, E, EntityCollectionExtraParamsEmpty>
}