package com.speakerboxlite.rxentity

interface Entity<Key: Comparable<Key>>
{
    val _key: Key
}

typealias EntityInt = Entity<Int>
typealias EntityLong = Entity<Long>
typealias EntityString = Entity<String>

interface EntityBack<Key: Comparable<Key>>
{
    val _key: Key
}

typealias EntityBackInt = EntityBack<Int>
typealias EntityBackLong = EntityBack<Long>
typealias EntityBackString = EntityBack<String>

interface EntityFactory<Key: Comparable<Key>, in Source: EntityBack<Key>, out Dest: Entity<Key>>
{
    fun map(entity: Source): Dest
}

interface EntityBackFactory<Key: Comparable<Key>, Source: EntityBack<Key>, Dest: EntityBack<Key>>
{
    fun map(entity: Source): Dest
    {
        throw IllegalArgumentException("${entity::class} is not convertible to ${this::class}")
    }
}
