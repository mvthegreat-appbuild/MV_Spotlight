package com.example.data.repository

import android.content.Context
import com.example.data.model.PicksResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DataSource {
    REMOTE,
    OFFLINE_CACHE,
    BUNDLED_SNAPSHOT,
}

sealed interface PicksFetchResult {
    data class Success(
        val data: PicksResponse,
        val source: DataSource,
    ) : PicksFetchResult

    data class Error(
        val message: String,
    ) : PicksFetchResult
}

interface PicksDataSource {
    suspend fun fetchPicks(): PicksFetchResult
}

class PicksRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) : PicksDataSource {

    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILENAME)
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(PicksResponse::class.java)

    override suspend fun fetchPicks(): PicksFetchResult = withContext(Dispatchers.IO) {
        val remoteJson = fetchRemoteJson()
        if (remoteJson != null) {
            val parsed = parseJson(remoteJson)
            if (parsed != null) {
                writeCache(remoteJson)
                return@withContext PicksFetchResult.Success(parsed, DataSource.REMOTE)
            }
        }

        val cachedJson = readCache()
        if (cachedJson != null) {
            val parsed = parseJson(cachedJson)
            if (parsed != null) {
                return@withContext PicksFetchResult.Success(parsed, DataSource.OFFLINE_CACHE)
            }
        }

        val bundledJson = readBundledAsset()
        if (bundledJson != null) {
            val parsed = parseJson(bundledJson)
            if (parsed != null) {
                return@withContext PicksFetchResult.Success(parsed, DataSource.BUNDLED_SNAPSHOT)
            }
        }

        PicksFetchResult.Error("Unable to load picks. Check internet connection and try again.")
    }

    private fun fetchRemoteJson(): String? {
        return try {
            val url = "$PICKS_JSON_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(raw: String): PicksResponse? {
        return try {
            adapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(raw: String) {
        try {
            cacheFile.writeText(raw, Charsets.UTF_8)
        } catch (_: Exception) {
            // Non-fatal cache write failure
        }
    }

    private fun readCache(): String? {
        return try {
            if (cacheFile.exists()) cacheFile.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readBundledAsset(): String? {
        return try {
            appContext.assets.open(BUNDLED_ASSET_NAME).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PICKS_JSON_URL =
            "https://raw.githubusercontent.com/mvthegreat-appbuild/MV_TradeSpotlight/main/picks.json"
        private const val CACHE_FILENAME = "picks_cache.json"
        private const val BUNDLED_ASSET_NAME = "picks.json"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
