package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.lang.ref.WeakReference
import java.util.*

abstract class EntityObservable<K: Comparable<K>, E: Entity<K>, EL>(holder: EntityCollection<K, E>,
                                                                    val combineSources: List<CombineSource<E>> = listOf()): Observable<EL>()
{
    val rxLoader = BehaviorSubject.createDefault(false)
    val rxError = PublishSubject.create<Throwable>()

    var dispBag = CompositeDisposable()

    val uuid = UUID.randomUUID().toString()
    val collection = WeakReference<EntityCollection<K, E>>(holder)

    init
    {
        holder.add(obs = this)
    }

    fun dispose()
    {
        dispBag.dispose()
        collection.get()?.remove(obs = this)
        print("EntityObservable has been disposed. UUID - $uuid")
    }

    open fun update(source: String, entity: E)
    {

    }

    open fun update(source: String, entities: Map<K, E>)
    {

    }

    open fun refreshData(resetCache: Boolean, data: Any?)
    {

    }
}