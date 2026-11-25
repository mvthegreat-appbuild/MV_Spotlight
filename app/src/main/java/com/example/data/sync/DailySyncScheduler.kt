package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DailySyncScheduler {

    const val UNIQUE_WORK_NAME = "daily_8am_trade_spotlight_sync"
    private const val PREFS_NAME = "trade_spotlight_sync_prefs"
    private const val KEY_LAST_AUTO_SYNC_MS = "last_auto_sync_ms"

    fun scheduleDaily8AmSync(context: Context) {
        val delayMillis = calculateInitialDelayToNext8AmMillis()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyPicksSyncWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun calculateInitialDelayToNext8AmMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val target = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(0L)
    }

    fun recordLastAutoSync(context: Context, timestampMillis: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_SYNC_MS, timestampMillis)
            .apply()
    }

    fun getLastAutoSyncMillis(context: Context): Long? {
        val value = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTO_SYNC_MS, -1L)
        return if (value > 0L) value else null
    }
}
