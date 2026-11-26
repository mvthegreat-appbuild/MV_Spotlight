package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.DataSource
import com.example.data.repository.LiveQuoteRepository
import com.example.data.repository.PicksFetchResult
import com.example.data.repository.PicksRepository
import kotlinx.coroutines.CancellationException

class DailyPicksSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val picksRepo = PicksRepository(applicationContext)
            val liveRepo = LiveQuoteRepository(applicationContext)

            when (val result = picksRepo.fetchPicks()) {
                is PicksFetchResult.Success -> {
                    if (result.source == DataSource.REMOTE) {
                        val stocks = result.data.picks.map { it.stock }
                        if (stocks.isNotEmpty()) {
                            liveRepo.fetchLiveQuotes(stocks)
                        }
                        DailySyncScheduler.recordLastAutoSync(applicationContext)
                        Result.success()
                    } else {
                        retryOrFail()
                    }
                }

                is PicksFetchResult.Error -> retryOrFail()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result {
        return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            DailySyncScheduler.recordLastAutoSyncFailure(applicationContext)
            Result.failure()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
