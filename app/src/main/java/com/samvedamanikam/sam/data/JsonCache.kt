package com.samvedamanikam.sam.data

import kotlinx.serialization.json.Json
import java.io.File

/** Last good response per screen, so the app shows data instantly (and offline) while it refreshes. */
class JsonCache(val dir: File, val json: Json) {

    inline fun <reified T> read(key: String): T? =
        runCatching { json.decodeFromString<T>(file(key).readText()) }.getOrNull()

    inline fun <reified T> write(key: String, value: T) {
        runCatching {
            dir.mkdirs()
            file(key).writeText(json.encodeToString(value))
        }
    }

    fun file(key: String) = File(dir, "$key.json")

    fun clear() {
        dir.deleteRecursively()
    }
}
