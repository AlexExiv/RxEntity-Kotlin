package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject

open class SingleObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                        val queue: Scheduler,
                                                                        key: K? = null,
                                                                        extra: Extra? = null): EntityObservable<K, E, E>(holder)
{
    enum class State
    {
        Initializing, Ready, NotFound, Deleted
    }

    val rxState = BehaviorSubject.createDefault(State.Initializing)
    protected val rxPublish = BehaviorSubject.create<E>()

    var extra: Extra? = extra
        private set

    var key: K? = key
        protected set
    val entity: E? get() = rxPublish.value

    override fun update(source: String, entity: E)
    {
        if (this.entity?._key == entity._key && source != uuid)
        {
            rxPublish.onNext(entity)
        }
    }

    override fun update(source: String, entities: Map<K, E>)
    {
        val key = entity?._key
        if (key != null && entities[key] != null && source != uuid)
        {
            rxPublish.onNext(entities[key]!!)
        }
    }

    override fun update(entities: Map<K, E>, operation: UpdateOperation) {
        val k = entity?._key
        val e = entities[k]
        if (k != null && e != null) {
            when (operation) {
                UpdateOperation.None, UpdateOperation.Insert, UpdateOperation.Update -> {
                rxPublish.onNext(e)
                rxState.onNext(State.Ready)
            }
                UpdateOperation.Delete, UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun update(entities: Map<K, E>, operations: Map<K, UpdateOperation>)
    {
        val k = entity?._key
        val e = entities[k]
        val o = operations[k]

        if (k != null && e != null && o != null)
        {
            when (o)
            {
                UpdateOperation.None, UpdateOperation.Insert, UpdateOperation.Update ->
                {
                    rxPublish.onNext(e)
                    rxState.onNext(State.Ready)
                }
                UpdateOperation.Delete, UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun delete(keys: Set<K>)
    {
        val k = entity?._key
        if (k != null && keys.contains(k))
            clear()
    }

    override fun clear()
    {
        rxState.onNext(State.Deleted)
    }

    override fun subscribeActual(observer: Observer<in E>)
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
}

typealias SingleObservable<K, Entity> = SingleObservableExtra<K, Entity, EntityCollectionExtraParamsEmpty>
typealias SingleObservableInt<Entity> = SingleObservable<Int, Entity>
typealias SingleObservableLong<Entity> = SingleObservable<Long, Entity>
typealias SingleObservableString<Entity> = SingleObservable<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: SingleObservableExtra<K, E, Extra>, resetCache: Boolean = false): Disposable
{
    return subscribe { to._refresh(resetCache = resetCache, extra = it) }
}