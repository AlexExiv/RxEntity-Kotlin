package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Scheduler

const val PAGINATOR_END = -9999

open class PaginatorObservableExtra<K: Comparable<K>, E: Entity<K>, Extra>(holder: EntityCollection<K, E>,
                                                                           queue: Scheduler,
                                                                           perPage: Int,
                                                                           extra: Extra? = null): ArrayObservableExtra<K, E, Extra>(holder, queue, perPage, extra)
{
    open fun next()
    {

    }

    protected open fun append(entities: List<E>): List<E>
    {
        lock.lock()
        try
        {
            if (perPage == ARRAY_PER_PAGE)
            {
                page = PAGINATOR_END
                return entities
            }

            val newEntities = _entities.toMutableList()
            newEntities.appendOrReplaceEntity(entities)
            page = if (entities.size >= perPage) page + 1 else PAGINATOR_END
            return newEntities
        }
        finally
        {
            lock.unlock()
        }
    }
}

typealias PaginatorObservable<K, Entity> = PaginatorObservableExtra<K, Entity, EntityCollectionExtraParamsEmpty>

typealias PaginatorObservableExtraInt<Entity, Extra> = PaginatorObservableExtra<Int, Entity, Extra>
typealias PaginatorObservableInt<Entity> = PaginatorObservable<Int, Entity>

typealias PaginatorObservableExtraLong<Entity, Extra> = PaginatorObservableExtra<Long, Entity, Extra>
typealias PaginatorObservableLong<Entity> = PaginatorObservable<Long, Entity>

typealias PaginatorObservableExtraString<Entity, Extra> = PaginatorObservableExtra<String, Entity, Extra>
typealias PaginatorObservableString<Entity> = PaginatorObservable<String, Entity>