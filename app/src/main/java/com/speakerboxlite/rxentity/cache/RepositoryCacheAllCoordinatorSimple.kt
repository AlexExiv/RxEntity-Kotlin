package com.speakerboxlite.rxentity.cache

import com.speakerboxlite.rxentity.EntityAllRepositoryInterface
import com.speakerboxlite.rxentity.EntityBack
import com.speakerboxlite.rxentity.EntityRepository
import com.speakerboxlite.rxentity.Optional
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class RepositoryCacheAllCoordinatorSimple<K: Comparable<K>, EB: EntityBack<K>>(
    val queue: Scheduler = Schedulers.io(),
    val source: RepositoryCacheAllSourceInterface<K, EB>,
    val storage: RepositoryCacheAllStorageInterface<K, EB>,
    val updateDelay: Int = 1) : EntityRepository<K, EB>(), EntityAllRepositoryInterface<K, EB>
{
    enum class State
    {
        Wait, Updating, Updated;
    }

    var updateState = State.Wait
        protected set

    protected val lock = ReentrantLock()

    init
    {
        val d = storage
            .rxEntitiesUpdated
            .subscribe { rxEntitiesUpdated.onNext(it) }
        dispBag.add(d)
    }

    override fun RxFetchAll() : Single<List<EB>> =
        storage
            .RxFetchAll()
            .flatMap {
                if (it.isEmpty())
                    return@flatMap RxUpdate()

                update()
                Single.just(it)
            }

    override fun RxGet(key: K): Single<Optional<EB>> =
        storage
            .RxGet(key)
            .flatMap {
                if (it.value == null)
                    return@flatMap _RxGet(key)

                update()
                Single.just(it)
            }

    override fun RxGet(keys: List<K>): Single<List<EB>> =
        storage
            .RxGet(keys)
            .flatMap {
                if (it.isEmpty() && keys.isNotEmpty())
                    return@flatMap _RxGet(keys)

                update()
                Single.just(it)
            }

    private fun RxUpdate() : Single<List<EB>> =
        source
            .RxFetchAll()
            .flatMap { storage.RxRewriteAll(entities = it) }
            .doOnSuccess {
                lock.lock()
                updateState = State.Updated
                lock.unlock()
            }

    private fun _RxGet(key: K) : Single<Optional<EB>> =
        source
            .RxGet(key)
            .flatMap { if (it.value == null) Single.just(Optional(null)) else storage.RxSave(listOf(it.value)).map { Optional(it.first()) } }

    private fun _RxGet(keys: List<K>) : Single<List<EB>> =
        source
            .RxGet(keys)
            .flatMap { storage.RxSave(it) }

    private fun update()
    {
        try
        {
            lock.lock()
            if (updateState != State.Wait)
                return

            updateState = State.Updating

            val d = Observable
                .interval(updateDelay.toLong(), 20, TimeUnit.SECONDS, queue)
                .observeOn(queue)
                .concatMap {
                    lock.lock()
                    try
                    {
                        return@concatMap if (updateState != State.Wait) {
                            return@concatMap RxUpdate()
                                .map { true }
                                .onErrorReturnItem(false).toObservable() ?: Observable.just(false)
                        }
                        else
                            Observable.just(false)
                    }
                    finally
                    {
                        lock.unlock()
                    }
                }
                .takeWhile { !it }
                .observeOn(queue)
                .subscribe({}, {}, {
                    lock.lock()
                    updateState = State.Updated
                    lock.unlock()
                })

            dispBag.add(d)
        }
        finally
        {
            lock.unlock()
        }
    }
}