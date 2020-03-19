package com.speakerboxlite.rxentity

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject

open class SingleObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                        val queue: Scheduler,
                                                                        extra: Extra? = null): EntityObservable<K, E, E>(holder)
{
    val rxPublish = BehaviorSubject.create<E>()

    var extra: Extra? = extra
        private set

    val entity: E? get() = rxPublish.value

    override fun update(source: String, entity: E)
    {
        //assert( queue.operationQueue == OperationQueue.current, "Single observable can be updated only from the same queue with the parent collection" )

        if (this.entity?._key == entity._key && source != uuid)
        {
            rxPublish.onNext(entity)
        }
    }

    override fun update(source: String, entities: Map<K, E>)
    {
        //assert( queue.operationQueue == OperationQueue.current, "Single observable can be updated only from the same queue with the parent collection" )
        val key = entity?._key
        if (key != null && entities[key] != null && source != uuid)
        {
            rxPublish.onNext(entities[key]!!)
        }
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
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be updated only from the specified in the constructor OperationQueue" )
        this.extra = extra ?: this.extra
    }
}

typealias SingleObservableInt<Entity> = SingleObservableExtra<Int, Entity, EntityCollectionExtraParamsEmpty>
typealias SingleObservableLong<Entity> = SingleObservableExtra<Long, Entity, EntityCollectionExtraParamsEmpty>
typealias SingleObservableString<Entity> = SingleObservableExtra<String, Entity, EntityCollectionExtraParamsEmpty>

fun <K: Comparable<K>, E: Entity<K>, Extra> Observable<Extra>.refresh(to: SingleObservableExtra<K, E, Extra>, resetCache: Boolean = false): Disposable
{
    return subscribe { to._refresh(resetCache = resetCache, extra = it) }
}