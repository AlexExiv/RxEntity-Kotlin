package com.speakerboxlite.rxentity

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Test

import org.junit.Assert.*

data class TestEntity(val id: Int, val value: String): Entity<Int>
{
    override val key: Int
        get() = id
}

data class ExtraParams(val test: String)
data class ExtraCollectionParams(val test: String)

class EntityObservableUnitTest
{
    @Test
    fun test()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val single0 = collection.createSingle { Single.just(TestEntity(1, "1")) }
        var disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
        }

        disp.dispose()

        val pages0 = collection.createPaginator { Single.just(listOf(TestEntity(1, "2"), TestEntity(2, "3"))) }
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
    }

    @Test
    fun testExtra()
    {
        val collection = EntityObservableCollectionInt<TestEntity>(Schedulers.trampoline())
        val single0 = collection.createSingleExtra(extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.extra!!.test, "1")
            else
            {
                assertEquals(it.extra!!.test, "2")
                assertEquals(it.refreshing, true)
            }

            Single.just(TestEntity(1, it.extra!!.test))
        }

        var disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
        }

        disp.dispose()

        single0.refresh(extra = ExtraParams("2"))
        disp = single0.subscribe {
            assertEquals(it.id, 1)
            assertEquals(it.value, "2")
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

        disp = page0.subscribe {
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
        val single0 = collection.createSingleExtra(extra = ExtraParams(test = "1")) {
            if (it.first)
                assertEquals(it.collectionExtra!!.test, "2")
            else
            {
                assertEquals(it.extra!!.test, "1")
                assertEquals(it.collectionExtra!!.test, "4")
                assertEquals(it.refreshing, true)
            }

            Single.just(TestEntity(1, it.extra!!.test))
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
            assertEquals(it.id, 1)
            assertEquals(it.value, "1")
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
}
