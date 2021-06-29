package com.speakerboxlite.rxentity

fun <K: Comparable<K>, E: Entity<K>> List<E>.toEntitiesMap(): Map<K, E>
{
    val map = mutableMapOf<K, E>()
    forEach { map[it._key] = it }
    return map
}

fun <K: Comparable<K>, E: Entity<K>> MutableList<E>.appendNotExistEntity(entity: E)
{
    if (firstOrNull { entity._key == it._key } == null)
        add(entity)
}

fun <K: Comparable<K>, E: Entity<K>> MutableList<E>.appendOrReplaceEntity(entities: List<E>)
{
    entities.forEach { e ->
        val i = indexOfFirst { e._key == it._key }
        if (i == -1)
            add(e)
        else
            this[i] = e
    }
}

fun <K: Comparable<K>, E: Entity<K>> MutableList<E>.removeEntity(entity: E)
{
    removeEntityByKey(entity._key)
}

fun <K: Comparable<K>, E: Entity<K>> MutableList<E>.removeEntityByKey(key: K)
{
    val i = indexOfFirst { key == it._key }
    if (i != -1)
        removeAt(i)
}

fun <K: Comparable<K>> MutableList<K>.appendNotExistKey(key: K)
{
    if (firstOrNull { key == it } == null)
        add(key)
}

fun <K: Comparable<K>, E: Entity<K>> MutableList<E>.findEntityByKey(key: K) = firstOrNull { key == it._key }
