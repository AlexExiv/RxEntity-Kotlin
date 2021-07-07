package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlin.reflect.KProperty1

enum class UpdateOperation
{
    None, Insert, Update, Delete, Clear
}

data class EntityUpdated(val key: Any,
                         val fieldPath: KProperty1<Any, *>? = null,
                         val entity: EntityBack<*>? = null,
                         val operation: UpdateOperation = UpdateOperation.None)

interface EntityRepositoryInterface<Key: Comparable<Key>, EB: EntityBack<Key>>
{
    val rxEntitiesUpdated: PublishSubject<List<EntityUpdated>>

    fun RxGet(key: Key): Single<Optional<EB>>
    fun RxGet(keys: List<Key>): Single<List<EB>>
}

interface EntityAllRepositoryInterface<Key: Comparable<Key>, EB: EntityBack<Key>>: EntityRepositoryInterface<Key, EB>
{
    fun RxFetchAll(): Single<List<EB>>
}

abstract class EntityRepository<Key: Comparable<Key>, EB: EntityBack<Key>>: EntityRepositoryInterface<Key, EB>
{
    override var rxEntitiesUpdated = PublishSubject.create<List<EntityUpdated>>()
    val dispBag = CompositeDisposable()

    fun <KeyN: Comparable<KeyN>, E, T> connect(repository: EntityRepositoryInterface<KeyN, *>, property: KProperty1<E, T>)
    {
        val d = repository
            .rxEntitiesUpdated
            .map { it.map { EntityUpdated(key = it.key, fieldPath = property as KProperty1<Any, *>, operation = it.operation) } }
            .subscribe {
                rxEntitiesUpdated.onNext(it)
            }

        dispBag.add(d)
    }
}