package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.subjects.BehaviorSubject

const val ARRAY_PER_PAGE = 999999

open class ArrayObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                       val queue: Scheduler,
                                                                       extra: Extra? = null): EntityObservable<K, E, List<E>>(holder)
{
    enum class UpdatePolicy
    {
        Update, Reload
    }

    protected val rxPublish = BehaviorSubject.create<List<E>>()

    var extra: Extra? = extra
        protected set

    var page: Int = -1
        protected set

    var perPage: Int = ARRAY_PER_PAGE
        protected set

    val entities: List<E> get() = _entities
    protected var _entities: MutableList<E> = mutableListOf()

    var updatePolicy: UpdatePolicy = UpdatePolicy.Update

    operator fun get(i: Int): SingleObservable<K, E>
    {
        lock.lock()
        try
        {
            return collection.get()!!.createSingle(_entities[i])
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun update(source: String, entity: E)
    {
        lock.lock()
        try
        {
            val i = _entities.indexOfFirst { it._key == entity._key }
            if (i != -1 && source != uuid)
            {
                _entities[i] = entity
                rxPublish.onNext(entities)
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun update(source: String, entities: Map<K, E>)
    {
        lock.lock()
        try
        {
            if (source != uuid)
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
        finally
        {
            lock.unlock()
        }
    }

    override fun update(entities: Map<K, E>, operation: UpdateOperation)
    {
        lock.lock()
        try
        {
            if (operation == UpdateOperation.Insert || (updatePolicy == UpdatePolicy.Reload && operation == UpdateOperation.Update))
                refresh(extra = extra)
            else if (operation == UpdateOperation.Clear) clear()
            else
            {
                _entities.forEach {
                    val e = entities[it._key]
                    if (e != null)
                    {
                        when (operation)
                        {
                            UpdateOperation.Update -> setEntity(entity = e)
                            UpdateOperation.Delete -> remove(key = e._key)
                        }
                    }
                }
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun update(entities: Map<K, E>, operations: Map<K, UpdateOperation>)
    {
        lock.lock()
        try
        {
            if (operations.values.contains(UpdateOperation.Insert) || (updatePolicy == UpdatePolicy.Reload && operations.values.contains(UpdateOperation.Update)))
            {
                refresh(extra = extra)
            }
            else
            {
                val _entities = this.entities
                _entities.forEach {
                    val e = entities[it._key]
                    val o = operations[it._key]
                    if (e != null && o != null)
                    {
                        when (o)
                        {
                            UpdateOperation.Update -> setEntity(entity = e)
                            UpdateOperation.Delete -> remove(key = e._key)
                        }
                    }
                }
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun delete(keys: Set<K>)
    {
        lock.lock()
        try
        {
            _entities.forEach {
                if (keys.contains(it._key))
                    remove(key = it._key)
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun clear()
    {
        setEntities(entities = listOf())
    }

    //MARK: - Set
    fun setEntity(entity: E)
    {
        lock.lock()
        try
        {
            val i = _entities.indexOfFirst { it._key == entity._key }
            if (i != -1)
            {
                _entities[i] = entity
                rxPublish.onNext(_entities)
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    fun setEntities(entities: List<E>)
    {
        lock.lock()
        try
        {
            _entities = entities.toMutableList()
            rxPublish.onNext(_entities)
        }
        finally
        {
            lock.unlock()
        }
    }

    open fun add(entity: E)
    {
        lock.lock()
        try
        {
            _entities.appendNotExistEntity(entity = entity)
            rxPublish.onNext(_entities)
        }
        finally
        {
            lock.unlock()
        }
    }

    open fun remove(entity: E)
    {
        lock.lock()
        try
        {
            _entities.removeEntity(entity = entity)
            rxPublish.onNext(_entities)
        }
        finally
        {
            lock.unlock()
        }
    }

    open fun remove(key: K)
    {
        lock.lock()
        try
        {
            _entities.removeEntityByKey(key = key)
            rxPublish.onNext(_entities)
        }
        finally
        {
            lock.unlock()
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