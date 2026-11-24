package com.example.data.repository

import android.content.Context
import com.example.data.model.LiveQuote
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

interface LiveQuoteDataSource {
    suspend fun loadCachedQuotes(): Map<String, LiveQuote>
    suspend fun fetchLiveQuotes(stockNames: List<String>): Map<String, LiveQuote>
}

class LiveQuoteRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) : LiveQuoteDataSource {

    private val appContext = context.applicationContext
    private val quotesCacheFile = File(appContext.filesDir, QUOTES_CACHE_FILE)
    private val symbolsCacheFile = File(appContext.filesDir, SYMBOLS_CACHE_FILE)
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val quotesMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        LiveQuote::class.java,
    )
    private val quotesAdapter = moshi.adapter<Map<String, LiveQuote>>(quotesMapType)
    private val stringMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        String::class.java,
    )
    private val symbolsAdapter = moshi.adapter<Map<String, String>>(stringMapType)

    private val symbolCache = ConcurrentHashMap<String, String>().apply {
        putAll(KNOWN_NSE_SYMBOLS)
        putAll(readSymbolCache())
    }

    override suspend fun loadCachedQuotes(): Map<String, LiveQuote> = withContext(Dispatchers.IO) {
        readQuotesCache()
    }

    override suspend fun fetchLiveQuotes(stockNames: List<String>): Map<String, LiveQuote> =
        withContext(Dispatchers.IO) {
            val distinctNames = stockNames.distinct()
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            val existingCache = readQuotesCache().toMutableMap()

            val results = coroutineScope {
                distinctNames.map { stockName ->
                    async {
                        semaphore.withPermit {
                            fetchSingleStockQuote(stockName)?.let { quote ->
                                stockName to quote
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (results.isNotEmpty()) {
                results.forEach { (name, quote) -> existingCache[name] = quote }
                writeQuotesCache(existingCache)
                writeSymbolCache(symbolCache)
            }

            existingCache
        }

    private fun fetchSingleStockQuote(stockName: String): LiveQuote? {
        val cleanName = cleanStockName(stockName)
        val cachedSymbol = symbolCache[cleanName.lowercase()]
        if (cachedSymbol != null) {
            val price = fetchChartPrice(cachedSymbol)
            if (price != null && price > 0.0) {
                return LiveQuote(
                    symbol = cachedSymbol,
                    shortSymbol = cachedSymbol.substringBefore('.'),
                    price = price,
                )
            }
        }

        val resolvedSymbol = searchIndianSymbol(cleanName) ?: return null
        symbolCache[cleanName.lowercase()] = resolvedSymbol
        val price = fetchChartPrice(resolvedSymbol) ?: return null
        if (price <= 0.0) return null

        return LiveQuote(
            symbol = resolvedSymbol,
            shortSymbol = resolvedSymbol.substringBefore('.'),
            price = price,
        )
    }

    private fun searchIndianSymbol(cleanName: String): String? {
        return try {
            val encoded = URLEncoder.encode(cleanName, "UTF-8")
            val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=8&newsCount=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val quotes = json.optJSONArray("quotes") ?: return null
                var bseFallback: String? = null
                for (i in 0 until quotes.length()) {
                    val item = quotes.optJSONObject(i) ?: continue
                    val symbol = item.optString("symbol")
                    val quoteType = item.optString("quoteType")
                    if (quoteType.isNotEmpty() && quoteType != "EQUITY") continue
                    if (symbol.endsWith(".NS", ignoreCase = true)) {
                        return symbol
                    }
                    if (bseFallback == null && symbol.endsWith(".BO", ignoreCase = true)) {
                        bseFallback = symbol
                    }
                }
                bseFallback
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchChartPrice(symbol: String): Double? {
        return try {
            val encoded = URLEncoder.encode(symbol, "UTF-8")
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=1d"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val chart = json.optJSONObject("chart") ?: return null
                val resultArr = chart.optJSONArray("result") ?: return null
                if (resultArr.length() == 0) return null
                val meta = resultArr.optJSONObject(0)?.optJSONObject("meta") ?: return null
                val price = meta.optDouble("regularMarketPrice", Double.NaN)
                if (price.isNaN() || price <= 0.0) {
                    val prev = meta.optDouble("chartPreviousClose", Double.NaN)
                    if (prev.isNaN() || prev <= 0.0) null else prev
                } else {
                    price
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readQuotesCache(): Map<String, LiveQuote> {
        return try {
            if (!quotesCacheFile.exists()) return emptyMap()
            quotesAdapter.fromJson(quotesCacheFile.readText(Charsets.UTF_8)) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeQuotesCache(map: Map<String, LiveQuote>) {
        try {
            quotesCacheFile.writeText(quotesAdapter.toJson(map), Charsets.UTF_8)
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private fun readSymbolCache(): Map<String, String> {
        return try {
            if (!symbolsCacheFile.exists()) return emptyMap()
            symbolsAdapter.fromJson(symbolsCacheFile.readText(Charsets.UTF_8)) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeSymbolCache(map: Map<String, String>) {
        try {
            symbolsCacheFile.writeText(symbolsAdapter.toJson(map), Charsets.UTF_8)
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    companion object {
        private const val QUOTES_CACHE_FILE = "live_quotes_cache.json"
        private const val SYMBOLS_CACHE_FILE = "stock_symbols_cache.json"
        private const val MAX_CONCURRENT_REQUESTS = 6
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

        fun cleanStockName(stock: String): String {
            return stock
                .replace(Regex("""\s+Futures?$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+Ltd\.?$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+Limited$""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        private val KNOWN_NSE_SYMBOLS = mapOf(
            "federal bank" to "FEDERALBNK.NS",
            "eih" to "EIHOTEL.NS",
            "bajaj holdings and investment" to "BAJAJHLDNG.NS",
            "power grid corporation of india" to "POWERGRID.NS",
            "coromandel international" to "COROMANDEL.NS",
            "indian hotels company" to "INDHOTEL.NS",
            "fortis healthcare" to "FORTIS.NS",
            "bajaj finserv" to "BAJAJFINSV.NS",
            "nestle india" to "NESTLEIND.NS",
            "tvs motor company" to "TVSMOTOR.NS",
            "quess corp" to "QUESS.NS",
            "gabriel india" to "GABRIEL.NS",
            "avenue supermarts" to "DMART.NS",
            "bharat electronics" to "BEL.NS",
            "indian railway finance corporation" to "IRFC.NS",
            "pidilite industries" to "PIDILITIND.NS",
            "gujarat fluorochemicals" to "FLUOROCHEM.NS",
            "state bank of india" to "SBIN.NS",
            "godrej consumer products" to "GODREJCP.NS",
            "indraprastha gas" to "IGL.NS",
            "bank of baroda" to "BANKBARODA.NS",
            "vijaya diagnostic centre" to "VIJAYA.NS",
            "prestige estates projects" to "PRESTIGE.NS",
            "emami" to "EMAMILTD.NS",
            "lemon tree hotels" to "LEMONTREE.NS",
            "poonawalla fincorp" to "POONAWALLA.NS",
            "deepak fertilisers and petrochemicals corporation" to "DEEPAKFERT.NS",
            "adani energy solutions" to "ADANIENSOL.NS",
            "honasa consumer" to "HONASA.NS",
            "international gemological institute" to "IGIL.NS",
            "coforge" to "COFORGE.NS",
            "hyundai motor india" to "HYUNDAI.NS",
            "canara bank" to "CANBK.NS",
            "general insurance corporation of india" to "GICRE.NS",
            "ge vernova t&d india" to "GVT&D.NS",
            "aditya birla real estate" to "ABREL.NS",
            "blue star" to "BLUESTARCO.NS",
            "kpr mill" to "KPRMILL.NS",
            "mrs bectors food specialities" to "BECTORFOOD.NS",
            "kalyan jewellers india" to "KALYANKJIL.NS",
            "eternal" to "ETERNAL.NS",
            "alicon castalloy" to "ALICON.NS",
            "ramkrishna forgings" to "RKFORGE.NS",
            "pine labs" to "PINELABS.NS",
            "lloyds engineering works" to "LLOYDSENGG.NS",
            "indus towers" to "INDUSTOWER.NS",
            "acutaas chemicals" to "ACUTAAS.NS",
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
