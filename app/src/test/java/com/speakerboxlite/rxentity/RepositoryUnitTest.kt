package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert
import org.junit.Test

interface TestEntityBackProtocol: EntityBackInt
{
    val id: Int
    val value: String
    val indirectId: Int
    val indirectValue: String

    override val _key: Int get() = id
}

data class TestEntityBack(override val id: Int,
                          override val value: String,
                          override val indirectId: Int = 0,
                          override val indirectValue: String = "") : TestEntityBackProtocol
{
    /*constructor(entity: TestEntityBackProtocol) {
        id = entity.id
        value = entity.value
        indirectId = entity.indirectId
        indirectValue = entity.indirectValue
    }*/
}

interface IndirectEntityBackProtocol: EntityBackInt
{
    val id: Int
    val value: String

    override val _key: Int get() = id
}

data class IndirectEntityBack(override val id: Int,
                              override val value: String): IndirectEntityBackProtocol
{
/*
    constructor(entity: TestEntityBackProtocol) {
        id = entity.id
        value = entity.value
    }*/
}

open class TestRepository<Entity: EntityBackInt>: EntityRepository<Int, Entity>()
{
    var items: MutableList<Entity> = mutableListOf()

    fun Add(entities: List<Entity>)
    {
        items.addAll(entities)
        rxEntitiesUpdated.onNext(entities.map { EntityUpdated(key = it._key, operation = UpdateOperation.Insert) })
    }

    fun Update(entity: Entity)
    {
        val i = items.indexOfFirst { entity._key == it._key }
        if (i != -1)
            items[i] = entity
        else
            items.add(entity)

        rxEntitiesUpdated.onNext(listOf(EntityUpdated(key = entity._key, operation = UpdateOperation.Update)))
    }

    fun Delete(key: Int)
    {
        items.removeAll { it._key == key }
        rxEntitiesUpdated.onNext(listOf(EntityUpdated(key = key, operation = UpdateOperation.Delete)))
    }

    fun Clear()
    {
        items.clear()
        rxEntitiesUpdated.onNext(listOf(EntityUpdated(key = 0, operation = UpdateOperation.Clear)))
    }

    override fun RxGet(key: Int): Single<Optional<Entity>> = Single.just(Optional(items.firstOrNull { it._key == key }))

    override fun RxGet(keys: List<Int>): Single<List<Entity>> = Single.just(items.filter { keys.contains(it._key) })
}

typealias TestRepositoryIndirect = TestRepository<IndirectEntityBack>

class TestRepositoryDirect(val second: TestRepositoryIndirect): TestRepository<TestEntityBack>()
{
    override fun RxGet(key: Int): Single<Optional<TestEntityBack>> = super.RxGet(key = key)
        .flatMap {
            if (it.value == null)
                Single.just(Optional(null))
            else
                RxLoad(entities = listOf(it.value!!)).map { Optional(it.firstOrNull()) }
        }

    override fun RxGet(keys: List<Int>): Single<List<TestEntityBack>> =
        super.RxGet(keys = keys).flatMap { this.RxLoad(entities = it) }

    fun RxLoad(entities: List<TestEntityBack>): Single<List<TestEntityBack>>
    {
        val keys = entities.map { it.indirectId }
        return second
            .RxGet(keys = keys)
            .map { it.toEntitiesBackMap() }
            .map { m ->  entities.map { TestEntityBack(id = it.id, value = it.value, indirectId = it.indirectId, indirectValue = m[it.indirectId]?.value ?: "") } }
    }
}

class TestEntityMapper: EntityFactory<Int, TestEntityBack, TestEntity>
{
    override fun map(entity: TestEntityBack): TestEntity = TestEntity(entity.id, entity.value, entity.indirectId, entity.indirectValue)
}

class RepositoryUnitTest
{
    @Test
    fun testRepositories()
    {
        val repository = TestRepository<TestEntityBack>()
        repository.Add(entities = listOf(TestEntityBack(id = 1, value = "test1"), TestEntityBack(id = 2, value = "test2")))

        val collection = EntityObservableCollectionExtraBackInt<TestEntity, TestEntityBack, ExtraCollectionParams>(TestEntity::class, Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = repository
        //collection.entityFactory = TestEntityMapper() as EntityFactory<Int, EntityBack<Int>, TestEntity>

        val allArray = collection.createArrayBack { Single.just(repository.items) }
        val array = collection.createKeyArray(keys = listOf(1, 2))
        val single = collection.createSingle(key = 1)

        var d = single.subscribe { Assert.assertEquals("test1", it.value!!.value) }
        d.dispose()
        d = array.subscribe {
            Assert.assertEquals("test1", it[0].value)
            Assert.assertEquals("test2", it[1].value)
        }
        d.dispose()

        repository.Update(entity = TestEntityBack(id = 1, value = "test1-new"))

        d = single.subscribe { Assert.assertEquals("test1-new", it.value!!.value) }
        d.dispose()
        d = array.subscribe { Assert.assertEquals("test1-new", it[0].value) }
        d.dispose()

        repository.Delete(key = 1)
        d = single.rxState.subscribe { Assert.assertEquals(SingleObservableExtra.State.Deleted, it) }
        d.dispose()
        d = array.subscribe {
            Assert.assertEquals(2, it[0].id)
            Assert.assertEquals("test2", it[0].value)
        }
        d.dispose()

        single.key = 2
        d = single.subscribe { Assert.assertEquals("test2", it.value!!.value) }
        d.dispose()

        single.key = 3
        d = single.subscribe { Assert.assertEquals(null, it.value) }
        d.dispose()
        d = single.rxState.subscribe { Assert.assertEquals(SingleObservableExtra.State.NotFound, it) }
        d.dispose()
    }

    @Test
    fun testRepositoriesClear()
    {
        val repository = TestRepository<TestEntityBack>()
        repository.Add(entities = listOf(TestEntityBack(id = 1, value = "test1"), TestEntityBack(id = 2, value = "test2")))

        val collection = EntityObservableCollectionExtraBackInt<TestEntity, TestEntityBack, ExtraCollectionParams>(TestEntity::class, Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = repository

        val allArray = collection.createArrayBack { Single.just(repository.items) }
        val array = collection.createKeyArray(keys = listOf(1, 2))
        val single = collection.createSingle(key = 1)

        var d = array.subscribe { Assert.assertEquals(2, it.size) }
        d.dispose()

        repository.Clear()

        d = allArray.subscribe { Assert.assertEquals(0, it.size) }
        d.dispose()

        d = array.subscribe { Assert.assertEquals(0, it.size) }
        d.dispose()

        d = single.rxState.subscribe { Assert.assertEquals(SingleObservableExtra.State.Deleted, it) }
        d.dispose()
    }

    @Test
    fun testArrayRefresh()
    {
        val repository = TestRepository<TestEntityBack>()
        repository.Add(entities = listOf(TestEntityBack(id = 1, value = "test1"), TestEntityBack(id = 2, value = "test2")))

        val collection = EntityObservableCollectionExtraBackInt<TestEntity, TestEntityBack, ExtraCollectionParams>(TestEntity::class, Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = repository

        val allArray = collection.createArrayBack { Single.just(repository.items) }
        val array = collection.createKeyArray(keys = listOf(1, 2))

        repository.items.clear()
        collection.refresh()

        var d = allArray.subscribe { Assert.assertEquals(0, it.size) }
        d.dispose()

        d = array.subscribe { Assert.assertEquals(0, it.size) }
        d.dispose()
    }

    @Test
    fun testRepositoriesConnect()
    {
        val repositoryIndirect = TestRepositoryIndirect()
        repositoryIndirect.Add(entities = listOf(IndirectEntityBack(id = 1, value = "indirect1"), IndirectEntityBack(id = 2, value = "indirect2")))

        val repository = TestRepositoryDirect(repositoryIndirect)
        repository.Add(entities = listOf(TestEntityBack(id = 1, value = "test1", indirectId = 2, indirectValue = ""), TestEntityBack(id = 2, value = "test2", indirectId = 1, indirectValue = "")))

        repository.connect(repositoryIndirect, TestEntity::indirectId)

        val collection = EntityObservableCollectionExtraBackInt<TestEntity, TestEntityBack, ExtraCollectionParams>(TestEntity::class, Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = repository

        val single = collection.createSingle(key = 1)
        val array = collection.createKeyArray(keys = listOf(1, 2))

        var d = single.subscribe { Assert.assertEquals("indirect2", it.value!!.indirectValue) }
        d.dispose()
        d = array.subscribe {
            Assert.assertEquals("indirect2", it[0].indirectValue)
            Assert.assertEquals("indirect1", it[1].indirectValue)
        }
        d.dispose()

        repositoryIndirect.Update(entity = IndirectEntityBack(id = 1, value = "indirect-1"))
        repositoryIndirect.Update(entity = IndirectEntityBack(id = 2, value = "indirect-2"))

        d = single.subscribe { Assert.assertEquals("indirect-2", it.value!!.indirectValue) }
        d.dispose()
        d = array.subscribe {
            Assert.assertEquals("indirect-2", it[0].indirectValue)
            Assert.assertEquals("indirect-1", it[1].indirectValue)
        }
        d.dispose()
    }
}