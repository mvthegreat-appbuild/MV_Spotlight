package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.DataSource
import com.example.data.repository.LiveQuoteRepository
import com.example.data.repository.PicksFetchResult
import com.example.data.repository.PicksRepository

class DailyPicksSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val picksRepo = PicksRepository(applicationContext)
        val liveRepo = LiveQuoteRepository(applicationContext)

        return when (val result = picksRepo.fetchPicks()) {
            is PicksFetchResult.Success -> {
                if (result.source == DataSource.REMOTE) {
                    val stocks = result.data.picks.map { it.stock }
                    if (stocks.isNotEmpty()) {
                        liveRepo.fetchLiveQuotes(stocks)
                    }
                    DailySyncScheduler.recordLastAutoSync(applicationContext)
                    Result.success()
                } else {
                    // Network was unreachable or returned invalid response; retry later
                    Result.retry()
                }
            }

            is PicksFetchResult.Error -> Result.retry()
        }
    }
}
