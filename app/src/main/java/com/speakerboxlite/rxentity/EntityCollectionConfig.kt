package com.speakerboxlite.rxentity

object EntityCollectionConfig
{
    var isLogging = false

    fun log(message: String)
    {
        if (isLogging)
            println(message)
    }
}