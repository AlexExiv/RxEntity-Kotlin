package com.speakerboxlite.rxentity

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import java.lang.ref.WeakReference

typealias CombineMethod<E> = BiFunction<E, Array<*>, Pair<E, Boolean>>

data class CombineSource<E>(val sources: List<Observable<*>>,
                            val combine: CombineMethod<E>)

open class EntityObservableCollectionExtra<K: Comparable<K>, E: Entity<K>, CollectionExtra>(queue: Scheduler, collectionExtra: CollectionExtra? = null): EntityCollection<K, E>(queue)
{
    var collectionExtra: CollectionExtra? = collectionExtra
        protected set

    var singleFetchCallback: SingleFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null
    var arrayFetchCallback: ArrayFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null
    var allArrayFetchCallback: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null

    protected val combineSources = mutableListOf<CombineSource<E>>()
    var combineDisp: Disposable? = null

    /**
     *
     *
     * @param initial initial value for the single
     * @return SingleObservable object
     */
    override fun createSingle(initial: E, refresh: Boolean): SingleObservable<K, E>
    {
        assert(singleFetchCallback != null) { "To create Single with initial value you must specify singleFetchCallback before" }
        return SingleObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, initial = initial, refresh = refresh, fetch = singleFetchCallback!!)
    }

    /**
     * TODO
     *
     * @param start the flag indicated that SingleObservable must fetch first entity immediately after it has been created
     * @return
     */
    fun createSingle(key: K? = null, start: Boolean = true, refresh: Boolean = false): SingleObservable<K, E>
    {
        assert(singleFetchCallback != null) { "To create Single with default fetch method must specify singleFetchCallback before" }
        val e = sharedEntities[key]
        return if (e == null) createSingle(key = key, start = start, fetch = singleFetchCallback!!) else createSingle(e, refresh)
    }

    /**
     * TODO
     *
     * @param key the unique field of a entity by using it Single retrieve the entity
     * @param start the flag indicated that SingleObservable must fetch first entity immediately after it has been created
     * @param fetch the closure callback that specify method to get entities from repository
     * @return
     */
    fun createSingle(key: K? = null, start: Boolean = true, fetch: SingleFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): SingleObservable<K, E>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    /**
     * TODO
     *
     * @param Extra
     * @param extra
     * @param start
     * @param fetch
     * @return
     */
    fun <Extra> createSingleExtra(key: K? = null, extra: Extra? = null, start: Boolean = true, fetch: SingleFetchCallback<K, E, Extra, CollectionExtra>): SingleObservableExtra<K, E, Extra>
    {
        return SingleObservableCollectionExtra(holder = this, queue = queue, key = key, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    override fun createKeyArray(initial: List<E>): ArrayKeyObservable<K, E>
    {
        assert(arrayFetchCallback != null) { "To create Array with initial values you must specify arrayFetchCallback before" }
        return ArrayKeyObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, initial = initial, fetch = arrayFetchCallback!!)
    }

    fun createKeyArray(keys: List<K>, start: Boolean = true): ArrayKeyObservable<K, E>
    {
        assert(arrayFetchCallback != null) { "To create Array with default fetch method must specify arrayFetchCallback before" }
        return createKeyArray(keys = keys, start = start, fetch = arrayFetchCallback!!)
    }

    fun createKeyArray(keys: List<K> = listOf(), start: Boolean = true, fetch: ArrayFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): ArrayKeyObservable<K, E>
    {
        return ArrayKeyObservableCollectionExtra(holder = this, queue = queue, keys = keys, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun <Extra> createKeyArrayExtra(keys: List<K> = listOf(), extra: Extra? = null, start: Boolean = true, fetch: ArrayFetchCallback<K, E, Extra, CollectionExtra>): ArrayKeyObservableExtra<K, E, Extra>
    {
        return ArrayKeyObservableCollectionExtra(holder = this, queue = queue, keys = keys, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun createArray(start: Boolean = true): ArrayObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, start = start, fetch = { allArrayFetchCallback!!(it) })
    }

    fun createArray(start: Boolean = true, fetch: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): ArrayObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun <Extra> createArrayExtra(extra: Extra? = null, start: Boolean = true, fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): ArrayObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, start = start, fetch = fetch)
    }

    fun createPaginator(perPage: Int = 35, start: Boolean = true, fetch: PageFetchCallback<K, E, EntityCollectionExtraParamsEmpty, CollectionExtra>): PaginatorObservable<K, E>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, collectionExtra = collectionExtra, perPage = perPage, start = start, fetch = fetch)
    }

    fun <Extra> createPaginatorExtra(extra: Extra? = null, perPage: Int = 35, start: Boolean = true, fetch: PageFetchCallback<K, E, Extra, CollectionExtra>): PaginatorObservableExtra<K, E, Extra>
    {
        return PaginatorObservableCollectionExtra(holder = this, queue = queue, extra = extra, collectionExtra = collectionExtra, perPage = perPage, start = start, fetch = fetch)
    }

    fun <T> combineLatest(source: Observable<T>, merge: (E, T) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source), BiFunction { e, a -> merge(e, a[0] as T) }))
        buildCombines()
    }

    fun <T0, T1> combineLatest(source0: Observable<T0>, source1: Observable<T1>, merge: (E, T0, T1) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source0, source1), BiFunction { e, a -> merge(e, a[0] as T0, a[1] as T1) }))
        buildCombines()
    }

    fun <T0, T1, T2> combineLatest(source0: Observable<T0>, source1: Observable<T1>, source2: Observable<T2>, merge: (E, T0, T1, T2) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source0, source1, source2), BiFunction { e, a -> merge(e, a[0] as T0, a[1] as T1, a[2] as T2) }))
        buildCombines()
    }

    fun <T0, T1, T2, T3> combineLatest(source0: Observable<T0>, source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>, merge: (E, T0, T1, T2, T3) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source0, source1, source2, source3), BiFunction { e, a -> merge(e, a[0] as T0, a[1] as T1, a[2] as T2, a[3] as T3) }))
        buildCombines()
    }

    fun <T0, T1, T2, T3, T4> combineLatest(source0: Observable<T0>, source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>, source4: Observable<T4>, merge: (E, T0, T1, T2, T3, T4) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source0, source1, source2, source3, source4), BiFunction { e, a -> merge(e, a[0] as T0, a[1] as T1, a[2] as T2, a[3] as T3, a[4] as T4) }))
        buildCombines()
    }

    fun <T0, T1, T2, T3, T4, T5> combineLatest(source0: Observable<T0>, source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>, source4: Observable<T4>, source5: Observable<T5>, merge: (E, T0, T1, T2, T3, T4, T5) -> Pair<E, Boolean>)
    {
        combineSources.add(CombineSource(listOf(source0, source1, source2, source3, source4, source5), BiFunction { e, a -> merge(e, a[0] as T0, a[1] as T1, a[2] as T2, a[3] as T3, a[4] as T4, a[5] as T5) }))
        buildCombines()
    }

    override fun RxRequestForCombine(source: String, entity: E, updateChilds: Boolean) : Single<E>
    {
        var obs = Observable.just(entity)
        combineSources.forEach { ms  ->
            obs = when (ms.sources.size)
            {
                1 -> Observable.combineLatest(obs, ms.sources[0], { es, t -> ms.combine.apply(es, arrayOf(t)).first })
                2 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], { es, t0, t1 -> ms.combine.apply(es, arrayOf(t0, t1)).first })
                3 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], { es, t0, t1, t2 -> ms.combine.apply(es, arrayOf(t0, t1, t2)).first })
                4 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], { es, t0, t1, t2, t3 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3)).first })
                5 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], { es, t0, t1, t2, t3, t4 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3, t4)).first })
                6 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], ms.sources[5], { es, t0, t1, t2, t3, t4, t5 -> ms.combine.apply(es, arrayOf(t0, t1, t2, t3, t4, t5)).first })
                else -> Observable.combineLatest(obs, ms.sources[0], { es, t -> ms.combine.apply(es, arrayOf(t)).first })
            }
        }
        return obs
            .take(1)
            .doOnNext { if (updateChilds) update(source = source, entity = it) }
            .firstOrError()
    }

    override fun RxRequestForCombine(source: String, entities: List<E>, updateChilds: Boolean) : Single<List<E>>
    {
        var obs = Observable.just(entities)
        combineSources.forEach { ms  ->
            obs = when (ms.sources.size) {
                1 -> Observable.combineLatest(obs, ms.sources[0], { es, t -> es.map { ms.combine.apply(it, arrayOf(t)).first } })
                2 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], { es, t0, t1 -> es.map { ms.combine.apply(it, arrayOf(t0, t1)).first } })
                3 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], { es, t0, t1, t2 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2)).first } })
                4 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], { es, t0, t1, t2, t3 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3)).first } })
                5 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], { es, t0, t1, t2, t3, t4 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3, t4)).first } })
                6 -> Observable.combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], ms.sources[5], { es, t0, t1, t2, t3, t4, t5 -> es.map { ms.combine.apply(it, arrayOf(t0, t1, t2, t3, t4, t5)).first } })
                else -> Observable.combineLatest(obs, ms.sources[0], { es, t -> es.map { ms.combine.apply(it, arrayOf(t)).first } })
            }
        }
        return obs
            .take(1)
            .doOnNext { if (updateChilds) update(source = source, entities = it) }
            .firstOrError()
    }

    fun buildCombines()
    {
        var obs: Observable<List<Pair<CombineMethod<E>, Array<Any>>>> = Observable.just(listOf<Pair<CombineMethod<E>, Array<Any>>>()).observeOn(queue)
        combineSources.forEach { ms  ->
            when (ms.sources.size) {
                1 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], { arr, t0  -> arr + listOf(Pair(ms.combine, arrayOf(t0))) }) }
                2 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], ms.sources[1], { arr, t0, t1  -> arr + listOf(Pair(ms.combine, arrayOf(t0, t1))) })
                }
                3 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], { arr, t0, t1, t2  -> arr + listOf(Pair(ms.combine, arrayOf(t0, t1, t2))) })
                }
                4 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], { arr, t0, t1, t2, t3  -> arr + listOf(Pair(ms.combine, arrayOf(t0, t1, t2, t3))) })
                }
                5 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], { arr, t0, t1, t2, t3, t4  -> arr + listOf(Pair(ms.combine, arrayOf(t0, t1, t2, t3, t4))) })
                }
                6 -> {
                    obs = Observable
                        .combineLatest(obs, ms.sources[0], ms.sources[1], ms.sources[2], ms.sources[3], ms.sources[4], ms.sources[5], { arr, t0, t1, t2, t3, t4, t5  -> arr + listOf(Pair(ms.combine, arrayOf(t0, t1, t2, t3, t4, t5))) })
                }
                else -> assert(false) { "Unsupported number of the sources" }
            }
        }

        combineDisp?.dispose()
        combineDisp = obs.subscribe { applyCombines(combines = it) }
    }

    protected fun applyCombines(combines: List<Pair<CombineMethod<E>, Array<Any>>>)
    {
        lock.lock()
        try
        {
            val toUpdate = mutableMapOf<K , E>()
            sharedEntities.keys.forEach {
                var e = sharedEntities[it]!!
                var updated = false
                e = combines.fold(e) { a, c ->
                    val r = c.first.apply(a, c.second)
                    updated = updated || r.second
                    r.first
                }

                if (updated) {
                    sharedEntities[it] = e
                    toUpdate[it] = e
                }
            }

            items.forEach { it.get()?.update(source = "", entities = toUpdate) }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun commit(entity: E, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByKeys(keys = setOf(entity._key))
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                lock.lock()
                try
                {
                    sharedEntities[entity._key] = entity
                    items.forEach { it.get()?.Update(entity = entity, operation = operation) }
                }
                finally
                {
                    lock.unlock()
                }
            }
        }
    }

    override fun commitByKey(key: K, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByKeys(keys = setOf(key))
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                val r = singleFetchCallback
                if (r != null)
                {
                    val d = r(SingleParams(key = key))
                        .observeOn(queue)
                        .flatMap {
                            if (it.value == null)
                                Single.just(null)
                            else
                                RxRequestForCombine(source = "", entity = it.value, updateChilds = false).map { it }
                        }
                        .subscribe({  if (it != null) commit(entity = it, operation = operation) }, { })

                    dispBag.add(d)
                }
                else
                {
                    assert(false) { "To create Single with key you must specify singleFetchCallback or singleFetchBackCallback before" }
                }
            }
        }
    }

    override fun commitByKey(key: K, changes: (E) -> E)
    {
        lock.lock()
        try
        {
            val e = sharedEntities[key]
            if (e != null)
            {
                val new = changes(e)
                sharedEntities[key] = new
                items.forEach { it.get()?.Update(entity = new, operation = UpdateOperation.Update) }
            }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun commit(entities: List<E>, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByKeys(keys = entities.map { it._key }.toSet())
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                lock.lock()
                try
                {
                    val forUpdate = mutableMapOf<K, E>()
                    entities.forEach {
                        if (sharedEntities[it._key] != null)
                            forUpdate[it._key] = it

                        sharedEntities[it._key] = it
                    }

                    items.forEach { it.get()?.update(entities = forUpdate, operation = operation) }
                }
                finally
                {
                    lock.unlock()
                }
            }
        }
    }

    override fun commit(entities: List<E>, operations: List<UpdateOperation>)
    {
        val deleteKeys = entities.filterIndexed { i, e -> operations[i] == UpdateOperation.Delete }.map { it._key }.toSet()
        val otherEntities = entities.filterIndexed { i, e -> operations[i] != UpdateOperation.Delete }.map { it }
        val otherOpers = operations.filter { it != UpdateOperation.Delete }

        if (operations.firstOrNull { it == UpdateOperation.Clear } != null)
            commitClear()

        commitDeleteByKeys(keys = deleteKeys)
        lock.lock()
        try
        {
            val forUpdate = mutableMapOf<K , E>()
            val operationUpdate = mutableMapOf<K , UpdateOperation>()
            otherEntities.forEachIndexed { i, e ->
                if (sharedEntities[e._key] != null)
                    forUpdate[e._key] = e

                operationUpdate[e._key] = otherOpers[i]
                sharedEntities[e._key] = e
            }

            items.forEach { it.get()?.update(entities = forUpdate, operations = operationUpdate) }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun commitByKeys(keys: List<K>, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByKeys(keys = keys.toSet())
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                val r = arrayFetchCallback
                if (r != null)
                {
                    val d = r(KeyParams(keys = keys))
                        .observeOn(queue)
                        .flatMap { RxRequestForCombine(source = "", entities = it, updateChilds = false).map { it } }
                        .subscribe({ commit(entities = it, operation = operation) }, { })

                    dispBag.add(d)
                }
                else
                {
                    assert(false) { "To create Single with key you must specify singleFetchCallback or singleFetchBackCallback before" }
                }
            }
        }
    }

    override fun commitByKeys(keys: List<K>, operations: List<UpdateOperation>)
    {
        val deleteKeys = keys.filterIndexed { i, e -> operations[i] == UpdateOperation.Delete }.map { it }.toSet()
        val otherKeys = keys.filterIndexed { i, e -> operations[i] != UpdateOperation.Delete }.map { it }
        val otherOpers = operations.filter { it != UpdateOperation.Delete }

        if (operations.firstOrNull { it == UpdateOperation.Clear } != null)
            commitClear()

        commitDeleteByKeys(keys = deleteKeys)

        val r = arrayFetchCallback
        if (r != null)
        {
            val d = r(KeyParams(keys = otherKeys))
                .observeOn(queue)
                .flatMap { RxRequestForCombine(source = "", entities = it, updateChilds = false).map { it } }
                .subscribe({ commit(entities = it, operations = otherOpers) }, {  })

            dispBag.add(d)
        }
        else
        {
            assert(false) { "To create Single with key you must specify singleFetchCallback or singleFetchBackCallback before" }
        }
    }

    override fun commitByKeys(keys: List<K>, changes: (E) -> E)
    {
        lock.lock()
        try
        {
            val forUpdate = mutableMapOf<K, E>()
            keys.forEach {
                val e = sharedEntities[it]
                if (e != null)
                {
                    val new = changes(e)
                    sharedEntities[it] = new
                    forUpdate[it] = new
                }
            }

            items.forEach { it.get()?.update(entities = forUpdate, operation = UpdateOperation.Update) }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun commitDeleteByKeys(keys: Set<K>)
    {
        lock.lock()
        try
        {
            items.forEach { it.get()?.delete(keys = keys) }
        }
        finally
        {
            lock.unlock()
        }
    }

    override fun commitClear()
    {
        lock.lock()
        try
        {
            items.forEach { it.get()?.clear() }
        }
        finally
        {
            lock.unlock()
        }
    }

    fun RxRequestForUpdate(source: String = "", key: K, update: (E) -> E): Single<Optional<E>>
    {
        val weak = WeakReference(this)
        return Single.create<Optional<E>> {
                val entity = weak.get()?.sharedEntities?.get(key)
                if (entity != null)
                {
                    val new = update(entity)
                    weak.get()?.update(source = source, entity = update(entity))
                    it.onSuccess(Optional(new))
                }
                else
                {
                    it.onSuccess(Optional(null))
                }
            }
            .observeOn(queue)
            .subscribeOn(queue)
    }

    fun RxRequestForUpdate(source: String = "", keys: List<K>, update: (E) -> E): Single<List<E>>
    {
        val weak = WeakReference(this)
        return Single.create<List<E>> {
                val updArr = mutableListOf<E>()
                val updMap = mutableMapOf<K, E>()

                keys.forEach {
                    val entity = weak.get()?.sharedEntities?.get(it)
                    if (entity != null)
                    {
                        val new = update(entity)
                        weak.get()?.sharedEntities?.put(it, new)
                        updArr.add(new)
                        updMap[it] = new
                    }
                }

                weak.get()?.items?.forEach { it.get()?.update(source = source, entities = updMap) }
                it.onSuccess(updArr)
            }
            .observeOn(queue)
            .subscribeOn(queue)
    }

    fun RxRequestForUpdate(source: String = "", update: (E) -> E): Single<List<E>>
    {
        return RxRequestForUpdate(source = source, keys = sharedEntities.keys.map { it }, update = update)
    }

    fun refresh(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        val weak = WeakReference(this)
        dispBag.add(Single.create<Boolean> {
                weak.get()?._refresh(resetCache = resetCache, collectionExtra = collectionExtra)
                it.onSuccess(true)
            }
            .subscribeOn(queue)
            .observeOn(queue)
            .subscribe())
    }

    fun _refresh(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        //assert( queue.operationQueue == OperationQueue.current, "_Refresh can be called only from the specified in the constructor OperationQueue" )
        items.forEach {
            it.get()?.refreshData(resetCache = resetCache, data = this.collectionExtra as? Any)
        }
    }
}

typealias EntityObservableCollection<K, Entity> = EntityObservableCollectionExtra<K, Entity, EntityCollectionExtraParamsEmpty>

typealias EntityObservableCollectionExtraInt<Entity, CollectionExtra> = EntityObservableCollectionExtra<Int, Entity, CollectionExtra>
typealias EntityObservableCollectionInt<Entity> = EntityObservableCollection<Int, Entity>

typealias EntityObservableCollectionExtraLong<Entity, CollectionExtra> = EntityObservableCollectionExtra<Long, Entity, CollectionExtra>
typealias EntityObservableCollectionLong<Entity> = EntityObservableCollection<Long, Entity>

typealias EntityObservableCollectionExtraString<Entity, CollectionExtra> = EntityObservableCollectionExtra<String, Entity, CollectionExtra>
typealias EntityObservableCollectionString<Entity> = EntityObservableCollection<String, Entity>

fun <K: Comparable<K>, E: Entity<K>, CollectionExtra> Observable<CollectionExtra>.refresh(to: EntityObservableCollectionExtra<K, E, CollectionExtra>, resetCache: Boolean = false)
        = subscribe { to._refresh(resetCache = resetCache, collectionExtra = it) }