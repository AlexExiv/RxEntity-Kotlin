package com.speakerboxlite.rxentity

fun <Key: Comparable<Key>, E: Entity<Key>> MutableList<E>.replaceOrAdd(entities: List<E>)
{
    entities.forEach {
        var replaced = false
        for (i in 0 until size)
        {
            if (this[i]._key == it._key)
            {
                removeAt(i)
                add(i, it)
                replaced = true
                break
            }
        }

        if (!replaced)
            add(it)
    }
}