package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject

open class ArrayObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                       val queue: Scheduler,
                                                                       extra: Extra? = null): EntityObservable<K, E, List<E>>(holder)
{
    val rxPublish = BehaviorSubject.create<List<E>>()

    var extra: Extra? = extra
        protected set

    var page: Int = -1
        protected set

    var perPage: Int = 999999
        protected set

    val entities: List<E>? get() = rxPublish.value
    val entitiesNotNull: List<E> get() = rxPublish.value ?: listOf()

    override fun update(source: String, entity: E)
    {
        //assert( queue.operationQueue == OperationQueue.current, "Single observable can be updated only from the same queue with the parent collection" )

        val i = entities?.indexOfFirst { it._key == entity._key }
        if (i != null && source != uuid)
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

typealias ArrayObservableExtraInt<Entity, Extra> = ArrayObservableExtra<Int, Entity, Extra>
typealias ArrayObservableInt<Entity> = ArrayObservableExtraInt<Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayObservableExtraLong<Entity, Extra> = ArrayObservableExtra<Long, Entity, Extra>
typealias ArrayObservableLong<Entity> = ArrayObservableExtraInt<Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayObservableExtraString<Entity, Extra> = ArrayObservableExtra<String, Entity, Extra>
typealias ArrayObservableString<Entity> = ArrayObservableExtraString<Entity, EntityCollectionExtraParamsEmpty>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: ArrayObservableExtra<K, E, Extra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, extra = it) }