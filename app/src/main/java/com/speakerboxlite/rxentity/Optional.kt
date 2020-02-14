package com.speakerboxlite.rxentity

class Optional<T>(val value: T?)
{
    fun exist() = value != null
}