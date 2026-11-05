package com.samvedamanikam.sam

import android.content.Context
import com.samvedamanikam.sam.auth.DeviceAuthClient
import com.samvedamanikam.sam.auth.DeviceCredentialStore
import com.samvedamanikam.sam.data.JsonCache
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.SamApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency wiring (one place, no DI framework). */
class AppContainer(context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // Generous timeouts: the first request after the app pool sleeps can take ~10 s
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val deviceAuth = DeviceAuthClient(DeviceCredentialStore(context), BuildConfig.DEVICE_AUTH_APP, httpClient, json)

    private val cache = JsonCache(File(context.filesDir, "cache"), json)
    private var repository: Pair<String, SamRepository>? = null

    /** Repository for the paired server (the server URL is chosen when pairing). */
    @Synchronized
    fun repository(): SamRepository {
        val server = deviceAuth.credentials()?.serverUrl ?: BuildConfig.DEFAULT_BASE_URL
        repository?.takeIf { it.first == server }?.let { return it.second }
        val client = httpClient.newBuilder()
            .addInterceptor(deviceAuth.interceptor)
            .authenticator(deviceAuth.authenticator)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()
            .create(SamApi::class.java)
        return SamRepository(api, cache, json).also { repository = server to it }
    }

    /** First-run pairing; starts with an empty cache so nothing from a previous pairing shows. */
    suspend fun pair(serverUrl: String, username: String, password: String, deviceName: String) {
        cache.clear()
        synchronized(this) { repository = null }
        deviceAuth.pair(serverUrl, username, password, deviceName)
    }

    /** Revokes this phone on the server (best effort) and wipes local credentials and cached data. */
    suspend fun unpair() {
        deviceAuth.unpair()
        cache.clear()
        synchronized(this) { repository = null }
    }
}
