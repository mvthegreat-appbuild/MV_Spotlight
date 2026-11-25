package com.example

import android.app.Application
import com.example.data.sync.DailySyncScheduler

class TradeSpotlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DailySyncScheduler.scheduleDaily8AmSync(this)
    }
}
