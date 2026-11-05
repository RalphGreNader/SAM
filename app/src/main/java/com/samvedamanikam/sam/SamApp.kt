package com.samvedamanikam.sam

import android.app.Application
import com.samvedamanikam.sam.sync.SyncWorker

class SamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedulePeriodic(this)
    }
}
