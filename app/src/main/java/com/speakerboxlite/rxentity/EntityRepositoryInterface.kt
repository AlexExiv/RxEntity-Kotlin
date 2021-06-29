package com.speakerboxlite.rxentity

import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import kotlin.reflect.KProperty1

enum class UpdateOperation
{
    None, Insert, Update, Delete, Clear
}

data class EntityUpdated<Key: Comparable<Key>>(val key: Key,
                                               val fieldPath: KProperty1<Any, *>? = null,
                                               val entity: EntityBack<Key>? = null,
                                               val operation: UpdateOperation = UpdateOperation.None)

interface EntityRepositoryInterface<Key: Comparable<Key>>
{
    val rxEntitiesUpdated: PublishSubject<List<EntityUpdated<Key>>>

    fun _RxGet(key: Key): Single<Optional<EntityBack<Key>>>
    fun _RxGet(keys: List<Key>): Single<List<EntityBack<Key>>>
}

typealias EntityRepositoryInterfaceInt = EntityRepositoryInterface<Int>
typealias EntityRepositoryInterfaceLong = EntityRepositoryInterface<Long>
typealias EntityRepositoryInterfaceString = EntityRepositoryInterface<String>

interface EntityAllRepositoryInterface<Key: Comparable<Key>>: EntityRepositoryInterface<Key>
{
    fun _RxFetchAll(): Single<List<EntityBack<Key>>>
}
