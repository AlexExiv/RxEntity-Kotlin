package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler


open class ArrayKeyObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                          keys: List<K>,
                                                                          extra: Extra? = null,
                                                                          queue: Scheduler): ArrayObservableExtra<K, E, Extra>(holder, queue, extra = extra)
{
    var keys: List<K>
        get() = _keys
        set(value)
        {
            lock.lock()
            _keys = value.toMutableList()
            lock.unlock()
        }

    protected open var _keys = keys.toMutableList()

    override fun update(entities: Map<K, E>, operation: UpdateOperation)
    {
        lock.lock()
        try
        {
            _entities.forEach {
                val e = entities[it._key]
                if (e != null)
                    apply(e, operation)
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
            val _entities = this.entities
            _entities.forEach {
                val e = entities[it._key]
                val o = operations[it._key]
                if (e != null && o != null)
                    apply(e, o)
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    protected fun apply(entity: E, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.None, UpdateOperation.Insert, UpdateOperation.Update -> setEntity(entity = entity)
            UpdateOperation.Delete -> remove(key = entity._key)
            UpdateOperation.Clear -> clear()
        }
    }

    fun add(key: K)
    {
        lock.lock()
        _keys.appendNotExistKey(key)
        lock.unlock()
    }

    override fun add(entity: E)
    {
        lock.lock()
        super.add(entity = entity)

        _keys.appendNotExistKey(entity._key)
        lock.unlock()
    }

    override fun remove(entity: E)
    {
        lock.lock()
        super.remove(entity = entity)

        _keys.remove(entity._key)
        lock.unlock()
    }

    override fun remove(key: K)
    {
        lock.lock()
        super.remove(key = key)

        _keys.remove(key)
        lock.unlock()
    }

    override fun clear()
    {
        _keys = mutableListOf()
    }
}

typealias ArrayKeyObservable<K, Entity> = ArrayKeyObservableExtra<K, Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayKeyObservableExtraInt<Entity, Extra> = ArrayKeyObservableExtra<Int, Entity, Extra>
typealias ArrayKeyObservableInt<Entity> = ArrayKeyObservable<Int, Entity>

typealias ArrayKeyObservableExtraLong<Entity, Extra> = ArrayKeyObservableExtra<Long, Entity, Extra>
typealias ArrayKeyObservableLong<Entity> = ArrayKeyObservable<Long, Entity>

typealias ArrayKeyObservableExtraString<Entity, Extra> = ArrayKeyObservableExtra<String, Entity, Extra>
typealias ArrayKeyObservableString<Entity> = ArrayKeyObservable<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: ArrayKeyObservableExtra<K, E, Extra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, extra = it) }