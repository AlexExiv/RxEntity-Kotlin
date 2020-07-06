package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.subjects.BehaviorSubject

open class ArrayObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                       val queue: Scheduler,
                                                                       keys: List<K> = listOf(),
                                                                       extra: Extra? = null): EntityObservable<K, E, List<E>>(holder)
{
    protected val rxPublish = BehaviorSubject.create<List<E>>()

    var extra: Extra? = extra
        protected set

    var page: Int = -1
        protected set

    var perPage: Int = 999999
        protected set

    var keys: List<K> = keys
        protected set

    val entities: List<E>? get() = rxPublish.value
    val entitiesNotNull: List<E> get() = rxPublish.value ?: listOf()

    operator fun get(i: Int): SingleObservable<K, E> = collection.get()!!.createSingle(entitiesNotNull[i])

    override fun update(source: String, entity: E)
    {
        //assert( queue.operationQueue == OperationQueue.current, "Single observable can be updated only from the same queue with the parent collection" )

        val i = entities?.indexOfFirst { it._key == entity._key }
        if (i != null && i != -1 && source != uuid)
        {
            val entities = this.entities!!.toMutableList()
            entities[i] = entity
            rxPublish.onNext(entities)
        }
    }

    override fun update(source: String, entities: Map<K, E>)
    {
        //assert( queue.operationQueue == OperationQueue.current, "Single observable can be updated only from the same queue with the parent collection" )
        val _entities = this.entities?.toMutableList()
        if (_entities != null && source != uuid)
        {
            var was = false
            _entities.forEachIndexed { i, e ->
                if (entities[e._key] != null)
                {
                    _entities[i] = entities[e._key]!!
                    was = true
                }
            }

            if (was)
                rxPublish.onNext(_entities)
        }
    }

    open fun refresh(resetCache: Boolean = false, extra: Extra? = null)
    {

    }

    open fun _refresh(resetCache: Boolean = false, extra: Extra? = null)
    {
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be updated only from the specified in the constructor OperationQueue" )
        this.extra = extra ?: this.extra
        page = -1
        rxPublish.onNext(listOf())
    }

    override fun subscribeActual(observer: Observer<in List<E>>)
    {
        rxPublish.subscribe(observer)
    }
}

typealias ArrayObservable<K, Entity> = ArrayObservableExtra<K, Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayObservableExtraInt<Entity, Extra> = ArrayObservableExtra<Int, Entity, Extra>
typealias ArrayObservableInt<Entity> = ArrayObservable<Int, Entity>

typealias ArrayObservableExtraLong<Entity, Extra> = ArrayObservableExtra<Long, Entity, Extra>
typealias ArrayObservableLong<Entity> = ArrayObservable<Long, Entity>

typealias ArrayObservableExtraString<Entity, Extra> = ArrayObservableExtra<String, Entity, Extra>
typealias ArrayObservableString<Entity> = ArrayObservable<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: ArrayObservableExtra<K, E, Extra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, extra = it) }