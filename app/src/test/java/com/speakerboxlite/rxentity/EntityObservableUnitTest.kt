package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.junit.Test

import org.junit.Assert.*

data class TestEntity(val id: Int,
                      val value: String,
                      val indirectId: Int = 0,
                      val indirectValue: String = ""): Entity<Int>
{
    override val _key: Int
        get() = id

    constructor(map: Map<String, Any>): this(0, "")

    constructor(entity: TestEntityBackProtocol): this(entity.id, entity.value, entity.indirectId, entity.indirectValue)

}
/*
data class TestEntityBack(val id: Int, val value: String): EntityBack<Int>
{
    override val _key: Int
        get() = id
}
*/
data class ExtraParams(val test: String)
data class ExtraCollectionParams(val test: String)

class EntityObservableUnitTest
{
    /*@Test
    fun testUpdates()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val single0 = collection.createSingle(1) { Observable.just(TestEntity(1, "1")) }
        var disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
        }

        disp.dispose()

        val pages0 = collection.createPaginator { Observable.just(listOf(TestEntity(1, "2"), TestEntity(2, "3"))) }
        disp = pages0.subscribe {
            assertEquals(pages0.page, PAGINATOR_END)

            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "2")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "3")
        }

        disp.dispose()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "2")
        }

        /////////
        disp.dispose()
        disp = collection
                .RxRequestForUpdate(key = 1) { it.copy(value = "5") }
                .subscribe()

        disp.dispose()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "5")
        }

        disp.dispose()
        disp = pages0.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "5")
        }

        /////////
        disp.dispose()
        disp = collection
                .RxRequestForUpdate(keys = listOf(1, 2)) { it.copy(value = "${it.id}") }
                .subscribe()

        disp.dispose()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
        }

        disp.dispose()
        disp = pages0.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "1")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "2")
        }

        /////////
        disp.dispose()
        disp = collection
                .RxUpdate(entity = TestEntity(1, "25"))
                .subscribe()

        disp.dispose()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "25")
        }

        disp.dispose()
        disp = pages0.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "25")
        }

        /////////
        disp.dispose()
        single0.refresh()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
        }

        /////////
        disp.dispose()
        pages0.refresh()
        disp = pages0.subscribe {
            assertEquals(pages0.page, PAGINATOR_END)

            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "2")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "3")
        }

        disp.dispose()
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "2")
        }
    }*/


    @Test
    fun testDisposed()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val single0 = collection.createSingleExtra(key = 1, extra = ExtraParams(test = "1")) { Single.just(Optional(null)) }
        val page0 = collection.createPaginatorExtra(extra = ExtraParams(test = "1")) { Single.just(listOf()) }

        var getInside0 = false
        var getInside1 = false
        val disp0 = single0.subscribe {
            getInside0 = true
        }
        val disp1 = page0.subscribe {
            getInside1 = true
        }

        disp0.dispose()
        disp1.dispose()

        assertEquals(true, single0.disposed)
        assertEquals(true, page0.disposed)

        assertEquals(true, getInside0)
        assertEquals(true, getInside1)
    }

    @Test
    fun testExtra()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val single0 = collection.createSingleExtra(key = 1, extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.extra!!.test, "1")
            else
            {
                assertEquals(it.extra!!.test, "2")
                assertEquals(it.refreshing, true)
            }

            Single.just(Optional(TestEntity(1, it.extra!!.test)))
        }

        var disp = single0.toObservable().subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "1")
        }

        disp.dispose()

        val s = single0 as SingleObservableInt<TestEntity>
        single0.refresh(extra = ExtraParams(test = "2"))

        //single0.refresh(extra = ExtraParams("2"))
        disp = single0.subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "2")
        }

        disp.dispose()

        val page0 = collection.createPaginatorExtra(extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.extra!!.test, "1")
            else
            {
                assertEquals(it.extra!!.test, "2")
                assertEquals(it.refreshing, true)
                assertEquals(it.page, 0)
            }

            Single.just(listOf(TestEntity(1, it.extra!!.test), TestEntity(2, it.extra!!.test + "1")))
        }

        disp = page0.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "1")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "11")
        }

        disp.dispose()

        page0.refresh(extra = ExtraParams("2"))
        disp = page0.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "2")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "21")
        }

        disp.dispose()
    }

    @Test
    fun testCollectionExtra()
    {
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        val single0 = collection.createSingleExtra(key = 1, extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.collectionExtra!!.test, "2")
            else
            {
                assertEquals(it.extra!!.test, "1")
                assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(it.refreshing, true)
            }

            Single.just(Optional(TestEntity(1, it.extra!!.test)))
        }

        val page0 = collection.createPaginatorExtra(extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.collectionExtra!!.test, "2")
            else
            {
                assertEquals(it.extra!!.test, "1")
                assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(it.refreshing, true)
                assertEquals(it.page, 0)
            }

            Single.just(listOf(TestEntity(1, it.extra!!.test), TestEntity(2, it.extra!!.test + "1")))
        }

        collection.refresh(collectionExtra = ExtraCollectionParams(test = "4"))

        var disp = single0.subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "1")
        }

        disp.dispose()
        disp = page0.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "1")

            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "11")
        }

        disp.dispose()
    }

    @Test
    fun testArrayGetSingle()
    {
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.singleFetchCallback = {
            if (it.first)
                assertEquals(it.collectionExtra!!.test, "2")
            else
            {
                //assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(it.refreshing, true)
            }

            Single.just(Optional(TestEntity(it.last!!.id, it.collectionExtra!!.test + it.last!!.id)))
        }

        val page0 = collection.createPaginatorExtra(extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.collectionExtra!!.test, "2")
            else
            {
                assertEquals(it.extra!!.test, "1")
                assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(it.refreshing, true)
                assertEquals(it.page, 0)
            }

            Single.just(listOf(TestEntity(1, it.collectionExtra!!.test + "1"), TestEntity(2, it.collectionExtra!!.test + "2")))
        }

        val single0 = page0[0]
        var disp = single0.toObservable().subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "21")
        }
        disp.dispose()

        val single1 = page0[1]
        disp = single1.toObservable().subscribe {
            assertEquals(it.value!!.id, 2)
            assertEquals(it.value!!.value, "22")
        }
        disp.dispose()

        single0.refresh()
        single1.refresh()


        disp = single0.toObservable().subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "21")
        }
        disp.dispose()

        disp = single1.toObservable().subscribe {
            assertEquals(it.value!!.id, 2)
            assertEquals(it.value!!.value, "22")
        }
        disp.dispose()

        collection.refresh(collectionExtra = ExtraCollectionParams(test = "4"))

        disp = single0.subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "41")
        }
        disp.dispose()

        disp = single1.subscribe {
            assertEquals(it.value!!.id, 2)
            assertEquals(it.value!!.value, "42")
        }
        disp.dispose()
    }

    @Test
    fun testArrayInitial()
    {
        var i = 0
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        collection.arrayFetchCallback = { pp ->
            if (pp.first)
                assertEquals(pp.collectionExtra!!.test, "2")
            else
            {
                //assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(pp.refreshing, true)
            }

            Single.just(pp.keys.map { TestEntity(it, pp.collectionExtra!!.test + it) })
        }

        val array = collection.createKeyArray(initial = listOf(TestEntity(1, "2"), TestEntity(2, "3")))
        var disp = array.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "2")
            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "3")
        }
        disp.dispose()

        collection.refresh()

        disp = array.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "21")
            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "22")
        }
        disp.dispose()

        collection.refresh(collectionExtra = ExtraCollectionParams(test = "4"))

        disp = array.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "41")
            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "42")
        }
        disp.dispose()
    }
/*
    @Test
    fun testArrayResend()
    {
        var i = 0
        val rxSender = BehaviorSubject.create<List<TestEntity>>()
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))
        val arr = collection.createKeyArray {
            rxSender
        }

        rxSender.onNext(listOf(TestEntity(1, "2"), TestEntity(2, "3")))

        var disp = arr.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "2")
            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "3")
        }
        disp.dispose()

        rxSender.onNext(listOf(TestEntity(1, "21"), TestEntity(2, "22"), TestEntity(3, "4")))

        disp = arr.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "21")
            assertEquals(it[1].id, 2)
            assertEquals(it[1].value, "22")
            assertEquals(it[2].id, 3)
            assertEquals(it[2].value, "4")
        }
        disp.dispose()
    }
*/

    @Test
    fun testCollectionPagination()
    {
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test = "2"))

        val page0 = collection.createPaginatorExtra(extra = ExtraParams(test = "1"), perPage = 2) {
            if (it.first)
                Single.just(listOf(TestEntity(1, it.extra!!.test), TestEntity(2, it.extra!!.test + "1")))
            else
                Single.just(listOf(TestEntity(4, it.extra!!.test)))
        }

        assertEquals(page0.page, 0)
        page0.next()
        assertEquals(page0.page, PAGINATOR_END)
    }

    @Test
    fun testMergeWithSingle()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val rxObs = BehaviorSubject.createDefault("2")
        val rxObs1 = BehaviorSubject.createDefault("3")
        collection.combineLatest(rxObs) { e, t -> Pair(e.copy(value = t), true) }
        collection.combineLatest(rxObs1) { e, t -> Pair(e.copy(value = t), true) }
        val single0 = collection.createSingle(1) { Single.just(Optional(TestEntity(1, "1"))) }

        var disp = single0.toObservable().subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "3")
        }
        disp.dispose()

        rxObs.onNext("4")
        rxObs1.onNext("4")
        disp = single0.toObservable().subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "4")
        }
        disp.dispose()

        rxObs1.onNext("5")
        disp = single0.subscribe {
            assertEquals(it.value!!.id, 1)
            assertEquals(it.value!!.value, "5")
        }
        disp.dispose()
    }

    @Test
    fun testMergeWithPaginator()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val rxObs = BehaviorSubject.createDefault("2")
        val rxObs1 = BehaviorSubject.createDefault("3")
        collection.combineLatest(rxObs) { e, t -> Pair(e.copy(value = "${e.id}$t"), true) }
        collection.combineLatest(rxObs1) { e, t -> Pair(e.copy(value = "${e.id}$t"), true) }

        val pager = collection.createPaginator(perPage = 2) {
            if (it.page == 0)
                Single.just(listOf(TestEntity(1, "1"), TestEntity(2, "1")))
            else
                Single.just(listOf(TestEntity(3, "1"), TestEntity(4, "1")))
        }

        var disp = pager.toObservable().subscribe {
            assertEquals(it.size, 2)
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "13")
        }
        disp.dispose()

        rxObs.onNext("4")
        rxObs1.onNext("4")
        disp = pager.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "14")
        }
        disp.dispose()

        rxObs1.onNext("5")
        disp = pager.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "15")
        }
        disp.dispose()

        pager.next()
        disp = pager.subscribe {
            assertEquals(it.size, 4)
            assertEquals(it[2].id, 3)
            assertEquals(it[2].value, "35")
        }
        disp.dispose()
    }

    @Test
    fun testArrayInitialMerge()
    {
        var i = 0
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))

        val rxObs = BehaviorSubject.createDefault("2")
        val rxObs1 = BehaviorSubject.createDefault("3")
        collection.combineLatest(rxObs) { e, t -> Pair(e.copy(value = "${e.id}$t"), true) }
        collection.combineLatest(rxObs1) { e, t -> Pair(e.copy(value = "${e.id}$t"), true) }

        collection.arrayFetchCallback = { pp ->
            Single.just(listOf())
        }

        val array = collection.createKeyArray(initial = listOf(TestEntity(1, "2"), TestEntity(2, "3")))

        var disp = array.toObservable().subscribe {
            assertEquals(it.size, 2)
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "13")
        }
        disp.dispose()

        rxObs.onNext("4")
        rxObs1.onNext("4")
        disp = array.toObservable().subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "14")
        }
        disp.dispose()

        rxObs1.onNext("5")
        disp = array.subscribe {
            assertEquals(it[0].id, 1)
            assertEquals(it[0].value, "15")
        }
        disp.dispose()
    }

    @Test
    fun testCommits()
    {
        val collection = EntityObservableCollectionExtraInt<TestEntity, ExtraCollectionParams>(Schedulers.trampoline(), collectionExtra = ExtraCollectionParams(test="2"))

        collection.singleFetchCallback = { Single.just(Optional(null)) }
        collection.arrayFetchCallback = { Single.just(listOf()) }

        val array = collection.createKeyArray(initial = listOf(TestEntity(id = 1, value = "2"), TestEntity(id = 2, value = "3")))
        val single = collection.createSingle(key = 1)

        collection.commit(TestEntity(id = 1, value = "12"), UpdateOperation.Update)

        var d = single.toObservable().subscribe { assertEquals("12", it.value!!.value) }
        d.dispose()
        d = array.toObservable().subscribe { assertEquals("12", it[0].value) }
        d.dispose()

        collection.commitByKey(1) { TestEntity(id = 1, value = "13") }

        d = single.toObservable().subscribe { assertEquals("13", it.value!!.value) }
        d.dispose()
        d = array.toObservable().subscribe { assertEquals("13", it[0].value) }
        d.dispose()

        collection.commitByKeys(listOf(1, 2)) { TestEntity(id = it.id, value = "${it.id}4") }

        d = single.subscribe { assertEquals("14", it.value!!.value) }
        d.dispose()
        d = array.subscribe {
            assertEquals("14", it[0].value)
            assertEquals("24", it[1].value)
        }
        d.dispose()
    }
}
