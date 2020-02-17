package com.speakerboxlite.rxentity

interface Entity<Key: Comparable<Key>>
{
    val _key: Key
}

typealias EntityInt = Entity<Int>
typealias EntityString = Entity<String>