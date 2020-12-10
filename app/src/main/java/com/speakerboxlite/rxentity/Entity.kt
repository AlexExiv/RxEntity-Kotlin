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

typealias EntityBackInt = Entity<Int>
typealias EntityBackLong = Entity<Long>
typealias EntityBackString = Entity<String>

interface EntityBackToEntityMapper<Key: Comparable<Key>, Source: EntityBack<Key>, Dest: Entity<Key>>
{
    fun map(entity: Source): Dest
    {
        throw IllegalArgumentException("${entity::class} is not convertible to ${this::class}")
    }
}