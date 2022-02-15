package com.speakerboxlite.rxentity

import com.speakerboxlite.rxentity.cache.RepositoryCacheAllCoordinatorSimple
import com.speakerboxlite.rxentity.cache.RepositoryCacheAllSourceInterface
import com.speakerboxlite.rxentity.cache.RepositoryCacheAllStorageInterface
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert
import org.junit.Test

interface TestEntityBackInterface: EntityBackInt
{
    val id: Int
    val value: String

    override val _key: Int get() = id
}

private data class TestEntityBackSrc(
    override val id: Int,
    override val value: String) : TestEntityBackInterface
{
    constructor(entity: TestEntityBackInterface): this(entity.id, entity.value)
}

private data class TestEntityBackDst(
    override val id: Int,
    override val value: String) : TestEntityBackInterface
{
    constructor(entity: TestEntityBackInterface): this(entity.id, entity.value)
}

private data class TestEntityOut(
    val id: Int,
    val value: String): EntityInt
{
    override val _key: Int get() = id

    constructor(entity: TestEntityBackInterface): this(entity.id, entity.value)
}

private class RepositorySrc: EntityRepository<Int, TestEntityBackInterface>(), RepositoryCacheAllSourceInterface<Int, TestEntityBackInterface>
{
    val items = mutableListOf<TestEntityBackSrc>()

    fun add(entities: List<TestEntityBackSrc>)
    {
        items.addAll(entities)
        rxEntitiesUpdated.onNext(entities.map { EntityUpdated(key = it._key, operation = UpdateOperation.Insert) })
    }

    override fun RxGet(key: Int): Single<Optional<TestEntityBackInterface>> = Single.just(Optional(items.firstOrNull { it.id == key }))

    override fun RxGet(keys: List<Int>): Single<List<TestEntityBackInterface>> = Single.just(items.filter { keys.contains(it.id) })

    override fun RxFetchAll(): Single<List<TestEntityBackInterface>> = Single.just(items)
}

private class RepositoryDst: EntityRepository<Int, TestEntityBackInterface>(), RepositoryCacheAllStorageInterface<Int, TestEntityBackInterface>
{
    var items = mutableListOf<TestEntityBackDst>()

    override fun RxGet(key: Int): Single<Optional<TestEntityBackInterface>> = Single.just(Optional(items.firstOrNull { it.id == key }))

    override fun RxGet(keys: List<Int>): Single<List<TestEntityBackInterface>> = Single.just(items.filter { keys.contains(it.id) })

    override fun RxFetchAll(): Single<List<TestEntityBackInterface>> = Single.just(items)

    override fun RxSave(entities: List<TestEntityBackInterface>): Single<List<TestEntityBackInterface>>
    {
        entities.forEach { e ->
            val i = items.indexOfFirst { it.id == e.id }
            if (i == -1)
                items.add(TestEntityBackDst(e))
            else
                items[i] = TestEntityBackDst(e)
        }

        return Single.just(entities)
    }

    override fun RxRewriteAll(entities: List<TestEntityBackInterface>): Single<List<TestEntityBackInterface>>
    {
        items.clear()
        items.addAll(entities.map { TestEntityBackDst(it) })

        return Single.just(entities)
    }
}

private class TestEntityOutMapper: EntityFactory<Int, TestEntityBackInterface, TestEntityOut>
{
    override fun map(entity: TestEntityBackInterface): TestEntityOut = TestEntityOut(entity.id, entity.value)
}

class RepositoryCacheAllUnitTest
{
    @Test
    fun testAllArrayCoordinator()
    {
        val srcRep = RepositorySrc()
        val dstRep = RepositoryDst()
        val coordinatorSimple = RepositoryCacheAllCoordinatorSimple(queue = Schedulers.trampoline(), source = srcRep, storage = dstRep)

        srcRep.add(entities = listOf(TestEntityBackSrc(id = 1, value = "test1"), TestEntityBackSrc(id = 2, value = "test2")))

        val collection = EntityCollection.createBackInt<TestEntityOut, TestEntityBackInterface, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = coordinatorSimple

        val allArray = collection.createArray()
        var d = allArray.subscribe {
            Assert.assertEquals(2, it.size)
            Assert.assertEquals("test1", it[0].value)
            Assert.assertEquals("test2", it[1].value)
        }
        d.dispose()

        Assert.assertEquals(RepositoryCacheAllCoordinatorSimple.State.Updated, coordinatorSimple.updateState)
        Assert.assertEquals("test1", dstRep.items[0].value)
        Assert.assertEquals("test2", dstRep.items[1].value)
    }

    @Test
    fun testSingleCoordinator()
    {
        val srcRep = RepositorySrc()
        val dstRep = RepositoryDst()
        val coordinatorSimple = RepositoryCacheAllCoordinatorSimple(queue = Schedulers.trampoline(), source = srcRep, storage = dstRep)

        srcRep.add(entities = listOf(TestEntityBackSrc(id = 1, value = "test1"), TestEntityBackSrc(id = 2, value = "test2")))

        val collection = EntityCollection.createBackInt<TestEntityOut, TestEntityBackInterface, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.repository = coordinatorSimple
        //collection.entityFactory = TestEntityOutMapper()

        val single = collection.createSingle()
        single.key = 1

        var d = single.subscribe {
            Assert.assertEquals("test1", it.value!!.value)
        }
        d.dispose()

        Assert.assertEquals(RepositoryCacheAllCoordinatorSimple.State.Wait, coordinatorSimple.updateState)
        Assert.assertEquals(1, dstRep.items.size)
        Assert.assertEquals("test1", dstRep.items[0].value)
    }
}