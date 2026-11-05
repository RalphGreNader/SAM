package com.samvedamanikam.sam.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class DeviceAuthException(message: String) : Exception(message)

/**
 * Client side of Helios device auth (api/auth/device endpoints), reusable by any app code in DeviceAuth:Apps.
 *
 * - [pair] is called once with a username/password; afterwards only the device key is used.
 * - [interceptor] adds a short-lived JWT to every request; [authenticator] fetches a new one on 401.
 * - When the server says the device is unknown/revoked, the credentials are cleared and [paired] becomes false.
 */
class DeviceAuthClient(
    private val store: DeviceCredentialStore,
    private val app: String,
    private val http: OkHttpClient,
    private val json: Json
) {
    private val _paired = MutableStateFlow(store.get() != null)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

    private val lock = Any()
    private var token: String? = null
    private var tokenExpiresAtMs = 0L

    fun credentials(): DeviceCredentials? = store.get()

    suspend fun pair(serverUrl: String, username: String, password: String, deviceName: String) = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(serverUrl)
        val body = json.encodeToString(RegisterRequest.serializer(), RegisterRequest(app, username.trim(), password, deviceName.trim()))
        val request = Request.Builder().url(server + "api/auth/device/register").post(body.toRequestBody(JSON)).build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw DeviceAuthException("Can't reach $server. Check the address and your connection.")
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) throw DeviceAuthException(
                when (it.code) {
                    401 -> "Wrong username or password."
                    429 -> "Too many attempts. Wait a few minutes and try again."
                    else -> errorMessage(text) ?: "Pairing failed (HTTP ${it.code})."
                }
            )
            val registered = json.decodeFromString(RegisterResponse.serializer(), text)
            store.save(
                DeviceCredentials(
                    serverUrl = server,
                    app = registered.app ?: app,
                    deviceId = registered.deviceId,
                    deviceKey = registered.deviceKey,
                    userName = username.trim(),
                    deviceName = deviceName.trim(),
                    pairedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        synchronized(lock) { token = null; tokenExpiresAtMs = 0 }
        _paired.value = true
    }

    /**
     * A valid JWT, fetched with the device key when missing or about to expire. Blocking (OkHttp threads).
     * Returns null when the device is not paired or the server no longer knows it; throws IOException when offline.
     */
    fun token(forceRefresh: Boolean = false): String? = synchronized(lock) {
        if (!forceRefresh) token?.takeIf { System.currentTimeMillis() < tokenExpiresAtMs - 60_000 }?.let { return it }
        val credentials = store.get() ?: return null
        val body = json.encodeToString(TokenRequest.serializer(), TokenRequest(credentials.deviceId, credentials.deviceKey))
        val request = Request.Builder().url(credentials.serverUrl + "api/auth/device/token").post(body.toRequestBody(JSON)).build()
        http.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {
                    val issued = json.decodeFromString(TokenResponse.serializer(), response.body.string())
                    token = issued.accessToken
                    tokenExpiresAtMs = System.currentTimeMillis() + issued.expiresIn * 1000L
                    issued.accessToken
                }
                response.code == 401 -> {
                    clearLocal()
                    null
                }
                else -> throw IOException("Token request failed (HTTP ${response.code})")
            }
        }
    }

    /** Adds "Authorization: Bearer <jwt>" for the paired server. */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val jwt = token() ?: return@Interceptor chain.proceed(request)
        chain.proceed(request.newBuilder().header("Authorization", "Bearer $jwt").build())
    }

    /** On 401, retries once with a fresh token (unless another request already refreshed it). */
    val authenticator = Authenticator { _, response ->
        if (response.priorResponse != null) return@Authenticator null
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = synchronized(lock) {
            val current = token
            if (failed != null && current != null && failed != current) current else token(forceRefresh = true)
        } ?: return@Authenticator null
        response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }

    /** "Unpair this phone": revokes the device on the server (best effort) and forgets it locally. */
    suspend fun unpair() = withContext(Dispatchers.IO) {
        val credentials = store.get()
        if (credentials != null) runCatching {
            val jwt = token() ?: return@runCatching
            http.newCall(
                Request.Builder()
                    .url(credentials.serverUrl + "api/auth/device/devices/current")
                    .header("Authorization", "Bearer $jwt")
                    .delete()
                    .build()
            ).execute().close()
        }
        clearLocal()
    }

    private fun clearLocal() {
        store.clear()
        synchronized(lock) { token = null; tokenExpiresAtMs = 0 }
        _paired.value = false
    }

    private fun errorMessage(body: String): String? = runCatching {
        val obj = json.decodeFromString(JsonObject.serializer(), body)
        (obj["message"] ?: obj["error_description"])?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Adds a trailing slash and insists on https (the server also answers plain HTTP). */
        fun normalizeServerUrl(input: String): String {
            val url = input.trim().let { if (it.endsWith("/")) it else "$it/" }
            if (!url.startsWith("https://", ignoreCase = true)) throw DeviceAuthException("The server address must start with https://")
            if (url.toHttpUrlOrNull() == null) throw DeviceAuthException("That server address isn't valid.")
            return url
        }
    }

    @Serializable
    private data class RegisterRequest(
        @SerialName("APP") val app: String,
        @SerialName("USERNAME") val username: String,
        @SerialName("PASSWORD") val password: String,
        @SerialName("DEVICE_NAME") val deviceName: String
    )

    @Serializable
    private data class RegisterResponse(
        @SerialName("DEVICE_ID") val deviceId: String,
        @SerialName("DEVICE_KEY") val deviceKey: String,
        @SerialName("APP") val app: String? = null
    )

    @Serializable
    private data class TokenRequest(
        @SerialName("DEVICE_ID") val deviceId: String,
        @SerialName("DEVICE_KEY") val deviceKey: String
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int
    )
}
