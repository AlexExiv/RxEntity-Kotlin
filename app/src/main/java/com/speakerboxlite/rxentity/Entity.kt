package com.speakerboxlite.rxentity

interface Entity<Key: Comparable<Key>>
{
    val key: Key
}