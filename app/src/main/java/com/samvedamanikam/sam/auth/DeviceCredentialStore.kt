package com.samvedamanikam.sam.auth

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a paired device keeps. The device key never expires; only the server can revoke it. */
@Serializable
data class DeviceCredentials(
    val serverUrl: String,
    val app: String,
    val deviceId: String,
    val deviceKey: String,
    val userName: String,
    val deviceName: String,
    val pairedAtEpochMs: Long
)

/**
 * Stores the credentials encrypted with an Android Keystore key. The app excludes this file from
 * backups (allowBackup=false + extraction rules), so the key never leaves the phone.
 */
class DeviceCredentialStore(
    context: Context,
    prefsName: String = "device_auth",
    keyAlias: String = "device_auth_key"
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(keyAlias)
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: DeviceCredentials? = null
    private var loaded = false

    @Synchronized
    fun get(): DeviceCredentials? {
        if (!loaded) {
            cached = prefs.getString(KEY, null)
                ?.let(cipher::decrypt)
                ?.let { runCatching { json.decodeFromString(DeviceCredentials.serializer(), it) }.getOrNull() }
            loaded = true
        }
        return cached
    }

    @Synchronized
    fun save(credentials: DeviceCredentials) {
        prefs.edit(commit = true) { putString(KEY, cipher.encrypt(json.encodeToString(DeviceCredentials.serializer(), credentials))) }
        cached = credentials
        loaded = true
    }

    @Synchronized
    fun clear() {
        prefs.edit(commit = true) { remove(KEY) }
        cached = null
        loaded = true
    }

    private companion object {
        const val KEY = "credentials"
    }
}
