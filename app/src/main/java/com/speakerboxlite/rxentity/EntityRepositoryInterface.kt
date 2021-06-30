package com.speakerboxlite.rxentity

import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlin.reflect.KProperty1

enum class UpdateOperation
{
    None, Insert, Update, Delete, Clear
}

data class EntityUpdated(val key: Any,
                         val fieldPath: KProperty1<Any, *>? = null,
                         val entity: EntityBack<*>? = null,
                         val operation: UpdateOperation = UpdateOperation.None)

interface EntityRepositoryInterface<Key: Comparable<Key>>
{
    val rxEntitiesUpdated: PublishSubject<List<EntityUpdated>>

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

abstract class EntityRepository<Key: Comparable<Key>, EB: EntityBack<Key>>: EntityRepositoryInterface<Key>
{
    override var rxEntitiesUpdated = PublishSubject.create<List<EntityUpdated>>()
    val dispBag = CompositeDisposable()

    fun <KeyN: Comparable<KeyN>, E, T> connect(repository: EntityRepositoryInterface<KeyN>, property: KProperty1<E, T>)
    {
        val d = repository
            .rxEntitiesUpdated
            .map { it.map { EntityUpdated(key = it.key, fieldPath = property as KProperty1<Any, *>, operation = it.operation) } }
            .subscribe {
                rxEntitiesUpdated.onNext(it)
            }

        dispBag.add(d)
    }

    override fun _RxGet(key: Key) : Single<Optional<EntityBack<Key>>> = RxGet(key = key).map { Optional(it.value) }
    override fun _RxGet(keys: List<Key>) : Single<List<EntityBack<Key>>> = RxGet(keys = keys).map { it }

    abstract fun RxGet(key: Key) : Single<Optional<EB>>
    abstract fun RxGet(keys: List<Key>) : Single<List<EB>>
}