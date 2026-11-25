package com.example

import com.example.data.model.LiveQuote
import com.example.data.model.PickFormatters
import com.example.data.model.PickStatus
import com.example.data.model.PicksResponse
import com.example.data.model.StockPick
import com.example.data.repository.DataSource
import com.example.data.repository.LiveQuoteDataSource
import com.example.data.repository.LiveQuoteRepository
import com.example.data.repository.PicksDataSource
import com.example.data.repository.PicksFetchResult
import com.example.ui.CallFilter
import com.example.ui.TradeSpotlightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `computeMetrics matches reference RATNAVEER calculation`() {
        val pick = StockPick(
            stock = "RATNAVEER",
            cmp = 395.0,
            call = "BUY",
            targets = listOf(420.4),
            stopLoss = 382.0,
            period = "MOSt Overnight",
            broker = "Motilal Oswal",
            date = "2026-11-23",
            link = "https://example.com",
        )
        val live = LiveQuote(symbol = "RATNAVEER.NS", shortSymbol = "RATNAVEER", price = 402.5)
        val metrics = PickFormatters.computeMetrics(pick, live)
        assertEquals(true, metrics.isBuy)
        assertEquals("+1.90%", metrics.moveText)
        assertEquals(true, metrics.isMovePositive)
        assertEquals("3.29%", metrics.riskText)
        assertEquals("6.43%", metrics.toT1Text)
        assertEquals("1 : 1.95", metrics.rrT1Text)
        assertEquals(PickStatus.ACTIVE, metrics.status)
    }

    @Test
    fun `computeMetrics detects T1_HIT and SL_HIT for BUY and SELL`() {
        val buyPick = StockPick("StockA", 100.0, "BUY", listOf(110.0, 120.0), 95.0, null, "Broker", "2026-11-22", "https://a")
        assertEquals(PickStatus.T1_HIT, PickFormatters.computeMetrics(buyPick, LiveQuote("A.NS", "A", 112.0)).status)
        assertEquals(PickStatus.SL_HIT, PickFormatters.computeMetrics(buyPick, LiveQuote("A.NS", "A", 94.0)).status)

        val sellPick = StockPick("StockB", 200.0, "SELL", listOf(170.0), 210.0, null, "Broker", "2026-11-22", "https://b")
        val sellMetrics = PickFormatters.computeMetrics(sellPick, LiveQuote("B.NS", "B", 190.0))
        assertEquals("+5.00%", sellMetrics.moveText)
        assertEquals("5.00%", sellMetrics.riskText)
        assertEquals("15.00%", sellMetrics.toT1Text)
        assertEquals("1 : 3.00", sellMetrics.rrT1Text)
        assertEquals(PickStatus.ACTIVE, sellMetrics.status)
    }

    @Test
    fun `cleanStockName strips Futures suffix`() {
        assertEquals("Godrej Consumer Products", LiveQuoteRepository.cleanStockName("Godrej Consumer Products Futures"))
        assertEquals("Blue Star", LiveQuoteRepository.cleanStockName("Blue Star Futures"))
    }

    @Test
    fun `formatInr uses Indian numbering format`() {
        assertEquals("₹11,669", PickFormatters.formatInr(11669.0))
        assertEquals("₹402.5", PickFormatters.formatInr(402.5))
        assertEquals("—", PickFormatters.formatInr(null))
    }

    @Test
    fun `viewModel loads live quotes and filters by call and search query`() = runTest(testDispatcher) {
        val samplePicks = listOf(
            StockPick("Federal Bank", 373.5, "BUY", listOf(385.0), 361.0, null, "Axis Direct", "2026-11-22", "https://a"),
            StockPick("Coforge", 2041.0, "SELL", listOf(1900.0), 2100.0, null, "Renaissance", "2026-11-18", "https://b"),
        )
        val fakeRepo = object : PicksDataSource {
            override suspend fun fetchPicks(): PicksFetchResult {
                return PicksFetchResult.Success(
                    PicksResponse("2026-11-24T04:36:23+00:00", 10, 10, samplePicks),
                    DataSource.REMOTE,
                )
            }
        }
        val fakeLiveRepo = object : LiveQuoteDataSource {
            override suspend fun loadCachedQuotes(): Map<String, LiveQuote> = emptyMap()
            override suspend fun fetchLiveQuotes(stockNames: List<String>): Map<String, LiveQuote> = mapOf(
                "Federal Bank" to LiveQuote("FEDERALBNK.NS", "FEDERALBNK", 380.0),
                "Coforge" to LiveQuote("COFORGE.NS", "COFORGE", 1980.0),
            )
        }
        val vm = TradeSpotlightViewModel(fakeRepo, fakeLiveRepo)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.allCount)
        assertEquals(380.0, vm.uiState.value.liveQuotes["Federal Bank"]?.price ?: 0.0, 0.01)

        vm.onFilterSelected(CallFilter.SELL)
        assertEquals(1, vm.uiState.value.filteredPicks.size)
        assertEquals("Coforge", vm.uiState.value.filteredPicks.first().stock)

        vm.onFilterSelected(CallFilter.ALL)
        vm.onSearchQueryChanged("axis")
        assertEquals(1, vm.uiState.value.filteredPicks.size)
        assertEquals("Federal Bank", vm.uiState.value.filteredPicks.first().stock)
    }

    @Test
    fun `calculateInitialDelayToNext8AmMillis schedules same day before 8am and next day after 8am`() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val before8Am = java.util.Calendar.getInstance(utc).apply {
            set(2026, java.util.Calendar.NOVEMBER, 24, 6, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delayBefore = com.example.data.sync.DailySyncScheduler.calculateInitialDelayToNext8AmMillis(before8Am, utc)
        assertEquals(90L * 60L * 1000L, delayBefore)

        val after8Am = java.util.Calendar.getInstance(utc).apply {
            set(2026, java.util.Calendar.NOVEMBER, 24, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delayAfter = com.example.data.sync.DailySyncScheduler.calculateInitialDelayToNext8AmMillis(after8Am, utc)
        assertEquals(23L * 60L * 60L * 1000L, delayAfter)
    }
}
