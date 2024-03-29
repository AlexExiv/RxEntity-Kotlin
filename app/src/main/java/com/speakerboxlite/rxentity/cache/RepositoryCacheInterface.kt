package com.speakerboxlite.rxentity.cache

import com.speakerboxlite.rxentity.EntityAllRepositoryInterface
import com.speakerboxlite.rxentity.EntityBack
import io.reactivex.rxjava3.core.Single

interface RepositoryCacheAllSourceInterface<K: Comparable<K>, EB: EntityBack<K>>: EntityAllRepositoryInterface<K, EB>
{

}

interface RepositoryCacheAllStorageInterface<K: Comparable<K>, EB: EntityBack<K>>: RepositoryCacheAllSourceInterface<K, EB>
{
    fun RxSave(entities: List<EB>): Single<List<EB>>
    fun RxRewriteAll(entities: List<EB>): Single<List<EB>>
}