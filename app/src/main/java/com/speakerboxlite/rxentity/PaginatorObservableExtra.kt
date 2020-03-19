package com.speakerboxlite.rxentity

import io.reactivex.Scheduler

const val PAGINATOR_END = 9999

open class PaginatorObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                      queue: Scheduler,
                                                                      extra: Extra? = null): ArrayObservableExtra<K, E, Extra>(holder, queue, extra)
{
    open fun next()
    {

    }

    protected open fun append( entities: List<E> ): List<E>
    {
        //assert( queue.operationQueue == OperationQueue.current, "Append can be updated only from the specified in the constructor OperationQueue" )

        val _entities = this.entities?.toMutableList() ?: mutableListOf()
        _entities.addAll(  entities )
        page = if (entities.size == perPage) page + 1 else PAGINATOR_END
        return _entities
    }
}

typealias PaginatorObservableExtraInt<Entity, Extra> = PaginatorObservableExtra<Int, Entity, Extra>
typealias PaginatorObservableInt<Entity> = PaginatorObservableExtraInt<Entity, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableExtraLong<Entity, Extra> = PaginatorObservableExtra<Long, Entity, Extra>
typealias PaginatorObservableLong<Entity> = PaginatorObservableExtraInt<Entity, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableExtraString<Entity, Extra> = PaginatorObservableExtra<String, Entity, Extra>
typealias PaginatorObservableString<Entity> = PaginatorObservableExtraString<Entity, EntityCollectionExtraParamsEmpty>