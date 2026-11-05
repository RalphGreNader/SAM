package com.samvedamanikam.sam

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.samvedamanikam.sam.auth.DeviceAuthClient
import com.samvedamanikam.sam.auth.DeviceCredentialStore
import com.samvedamanikam.sam.data.JsonCache
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.OutboxStore
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.data.api.Dashboard
import com.samvedamanikam.sam.data.api.SamApi
import com.samvedamanikam.sam.sync.SyncWorker
import com.samvedamanikam.sam.widget.SamWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency wiring (one place, no DI framework). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** Widget quick-adds, shares and tap-to-complete wait here until SyncWorker sends them. */
    val outbox = OutboxStore(File(context.filesDir, "outbox.json"), json)

    private val _dashboard = MutableStateFlow(cache.read<Dashboard>("dashboard"))
    /** Last known dashboard (cached, refreshed by the app and SyncWorker); the widget renders this. */
    val dashboard: StateFlow<Dashboard?> = _dashboard.asStateFlow()

    fun cachedCategories(): List<Category> = cache.read("categories") ?: emptyList()

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
        val repo = SamRepository(
            api, cache, json,
            onDashboard = {
                _dashboard.value = it
                refreshWidgets()
            },
            onMutated = { refreshDashboard() }
        )
        return repo.also { repository = server to it }
    }

    /** Queues changes made outside the app (widget, share) and sends them as soon as possible. */
    fun enqueue(ops: List<OutboxOp>) {
        outbox.addAll(ops)
        refreshWidgets()
        requestSync()
    }

    fun requestSync() = SyncWorker.enqueue(appContext)

    fun refreshWidgets() {
        appScope.launch { runCatching { SamWidget().updateAll(appContext) } }
    }

    private fun refreshDashboard() {
        appScope.launch { runCatching { repository().dashboard() } }
    }

    /** First-run pairing; starts with an empty cache so nothing from a previous pairing shows. */
    suspend fun pair(serverUrl: String, username: String, password: String, deviceName: String) {
        cache.clear()
        outbox.clear()
        _dashboard.value = null
        synchronized(this) { repository = null }
        deviceAuth.pair(serverUrl, username, password, deviceName)
        refreshWidgets()
        requestSync()
    }

    /** Revokes this phone on the server (best effort) and wipes local credentials, queue and cached data. */
    suspend fun unpair() {
        deviceAuth.unpair()
        SyncWorker.cancelPending(appContext)
        cache.clear()
        outbox.clear()
        _dashboard.value = null
        synchronized(this) { repository = null }
        refreshWidgets()
    }
}
