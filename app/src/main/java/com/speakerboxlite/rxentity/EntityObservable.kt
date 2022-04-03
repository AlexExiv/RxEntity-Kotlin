package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.internal.disposables.DisposableHelper
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

abstract class EntityObservable<K: Comparable<K>, E: Entity<K>, EL>(holder: EntityCollection<K, E>): Observable<EL>()
{
    enum class Loading
    {
        None, FirstLoading, Loading;

        val isLoading: Boolean get() = this == FirstLoading || this == Loading
    }

    val rxLoader = BehaviorSubject.createDefault(Loading.None)
    val rxError = PublishSubject.create<Throwable>()

    protected var dispBag = CompositeDisposable()

    val uuid = UUID.randomUUID().toString()
    protected val lock = ReentrantLock()
    protected val collection = WeakReference<EntityCollection<K, E>>(holder)

    @Volatile
    var disposed = false
        private set

    @Volatile
    var singleton = false

    init
    {
        holder.add(obs = this)
    }

    internal fun dispose()
    {
        synchronized(this) {
            if (disposed || singleton)
                return

            disposed = true
            dispBag.dispose()
            collection.get()?.remove(obs = this)
        }

        println("EntityObservable has been disposed. UUID - $uuid")
    }

    fun share(count: Int) = publish().refCount(count)

    open fun update(source: String, entity: E)
    {

    }

    open fun update(source: String, entities: Map<K, E>)
    {

    }

    open fun Update( entity: E, operation: UpdateOperation )
    {
        update(entities = mapOf(entity._key to entity), operation = operation)
    }

    open fun update(entities: Map<K, E>, operation: UpdateOperation)
    {
        assert(false) { "This method must be overridden" }
    }

    open fun update(entities: Map<K, E>, operations: Map<K, UpdateOperation>)
    {
        assert(false) { "This method must be overridden" }
    }

    open fun delete(keys: Set<K> )
    {
        assert(false) { "This method must be overridden" }
    }

    open fun clear()
    {
        assert(false) { "This method must be overridden" }
    }

    open fun refreshData(resetCache: Boolean, data: Any?)
    {

    }
}