package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.internal.disposables.DisposableHelper
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.atomic.AtomicReference

const val ARRAY_PER_PAGE = 999999

open class ArrayObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                       val queue: Scheduler,
                                                                       perPage: Int = ARRAY_PER_PAGE,
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

    var perPage: Int = perPage
        protected set

    val entities: List<E> get() = _entities
    protected var _entities: MutableList<E> = mutableListOf()

    var updatePolicy: UpdatePolicy = UpdatePolicy.Update

    fun toObservable(): Observable<List<E>> = rxPublish

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
                rxPublish.onNext(_entities.toMutableList())
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
                    rxPublish.onNext(_entities.toMutableList())
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
                            else -> {}
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
                            else -> {}
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
            val entities = _entities.toList()
            entities.forEach {
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
                rxPublish.onNext(_entities.toMutableList())
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
            rxPublish.onNext(_entities.toMutableList())
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
            rxPublish.onNext(_entities.toMutableList())
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
            rxPublish.onNext(_entities.toMutableList())
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
            rxPublish.onNext(_entities.toMutableList())
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
        if (perPage != ARRAY_PER_PAGE || resetCache)
        {
            setEntities(listOf())
        }
    }

    override fun subscribeActual(observer: Observer<in List<E>>)
    {
        if (disposed)
            throw IllegalStateException("Trying to subscribe to the EntityObservable that has been disposed already. Maybe you forgot to make it singleton?")

        incrSubscribedAndTest()
        val lc = EntityObservableCoordinator(this, observer)
        lc.subscribe(rxPublish)
    }

    internal class EntityObservableCoordinator<K: Comparable<K>, E: Entity<K>, EL>(val parent: EntityObservable<K, E, EL>,
                                                                                   val downstream: Observer<in List<E>>):
        AtomicReference<Disposable>(), Observer<List<E>>, Disposable
    {
        @Volatile
        var cancelled = false

        override fun dispose()
        {
            if (!cancelled)
            {
                parent.dispose()
                cancelled = true
            }
        }

        override fun isDisposed(): Boolean = cancelled

        override fun onSubscribe(d: Disposable)
        {
            DisposableHelper.setOnce(this, d)
        }

        override fun onNext(t: List<E>)
        {
            downstream.onNext(t)
        }

        override fun onError(e: Throwable)
        {

        }

        override fun onComplete()
        {

        }

        fun subscribe(source: Observable<List<E>>)
        {
            downstream.onSubscribe(this)
            source.subscribe(this)
        }
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