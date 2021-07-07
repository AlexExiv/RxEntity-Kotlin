package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject

open class SingleObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                        val queue: Scheduler,
                                                                        open var key: K?,
                                                                        extra: Extra? = null): EntityObservable<K, E, Optional<E?>>(holder)
{
    enum class State
    {
        Initializing, Ready, NotFound, Deleted
    }

    val rxState = BehaviorSubject.createDefault(State.Initializing)
    protected val rxPublish = BehaviorSubject.create<Optional<E?>>()

    var extra: Extra? = extra
        private set

    val entity: E? get() = rxPublish.value?.value

    override fun update(source: String, entity: E)
    {
        if (key == entity._key && source != uuid)
        {
            publish(entity)
        }
    }

    override fun update(source: String, entities: Map<K, E>)
    {
        if (entities[key] != null && source != uuid)
        {
            publish(entities[key])
        }
    }

    override fun update(entities: Map<K, E>, operation: UpdateOperation)
    {
        val e = entities[key]
        if (e != null)
        {
            when (operation)
            {
                UpdateOperation.None, UpdateOperation.Insert, UpdateOperation.Update ->
                {
                    publish(e)
                    rxState.onNext(State.Ready)
                }
                UpdateOperation.Delete, UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun update(entities: Map<K, E>, operations: Map<K, UpdateOperation>)
    {
        val e = entities[key]
        val o = operations[key]

        if ( e != null && o != null)
        {
            when (o)
            {
                UpdateOperation.None, UpdateOperation.Insert, UpdateOperation.Update ->
                {
                    publish(e)
                    rxState.onNext(State.Ready)
                }
                UpdateOperation.Delete, UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun delete(keys: Set<K>)
    {
        if (keys.contains(key))
            clear()
    }

    override fun clear()
    {
        rxState.onNext(State.Deleted)
    }

    override fun subscribeActual(observer: Observer<in Optional<E?>>?)
    {
        rxPublish.subscribe(observer)
    }

    open fun refresh(resetCache: Boolean = false, extra: Extra? = null)
    {

    }

    open fun _refresh(resetCache: Boolean = false, extra: Extra? = null)
    {
        this.extra = extra ?: this.extra
    }

    protected fun publish(entity: E?)
    {
        rxPublish.onNext(Optional(entity))
    }
}

typealias SingleObservable<K, Entity> = SingleObservableExtra<K, Entity, EntityCollectionExtraParamsEmpty>
typealias SingleObservableInt<Entity> = SingleObservable<Int, Entity>
typealias SingleObservableLong<Entity> = SingleObservable<Long, Entity>
typealias SingleObservableString<Entity> = SingleObservable<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: SingleObservableExtra<K, E, Extra>, resetCache: Boolean = false): Disposable
{
    return subscribe { to._refresh(resetCache = resetCache, extra = it) }
}