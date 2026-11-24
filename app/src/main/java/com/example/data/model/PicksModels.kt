package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

@JsonClass(generateAdapter = true)
data class PicksResponse(
    val updated: String,
    val days: Int,
    val articles: Int,
    val picks: List<StockPick>,
)

@JsonClass(generateAdapter = true)
data class StockPick(
    val stock: String,
    val cmp: Double,
    val call: String,
    val targets: List<Double> = emptyList(),
    @Json(name = "stop_loss") val stopLoss: Double? = null,
    val period: String? = null,
    val broker: String? = null,
    val date: String,
    val link: String,
    val title: String = "",
)

@JsonClass(generateAdapter = true)
data class LiveQuote(
    val symbol: String,
    val shortSymbol: String,
    val price: Double,
)

enum class PickStatus {
    ACTIVE,
    T1_HIT,
    SL_HIT,
}

data class ComputedPickMetrics(
    val isBuy: Boolean,
    val target1: Double?,
    val lastTarget: Double?,
    val moveText: String,
    val isMovePositive: Boolean?,
    val riskText: String,
    val toT1Text: String,
    val toLastText: String,
    val rrT1Text: String,
    val rrLastText: String,
    val status: PickStatus,
)

object PickFormatters {
    private val indianLocale: Locale = Locale("en", "IN")

    fun formatInr(amount: Double?): String {
        if (amount == null || amount <= 0.0) return "—"
        val formatter = NumberFormat.getNumberInstance(indianLocale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
        return "₹${formatter.format(amount)}"
    }

    fun computeMetrics(pick: StockPick, liveQuote: LiveQuote? = null): ComputedPickMetrics {
        val isBuy = pick.call.equals("BUY", ignoreCase = true)
        val t1 = pick.targets.firstOrNull()
        val tLast = pick.targets.lastOrNull()
        val cmp = pick.cmp
        val sl = pick.stopLoss
        val livePrice = liveQuote?.price

        val moveVal = if (livePrice != null && livePrice > 0.0 && cmp > 0.0) {
            (if (isBuy) livePrice - cmp else cmp - livePrice) / cmp * 100.0
        } else {
            null
        }
        val moveText = if (moveVal != null) {
            val sign = if (moveVal >= 0.0) "+" else ""
            "$sign${String.format(Locale.US, "%.2f%%", moveVal)}"
        } else {
            "—"
        }

        val riskText = if (sl != null && cmp > 0.0) {
            val pct = (if (isBuy) cmp - sl else sl - cmp) / cmp * 100.0
            String.format(Locale.US, "%.2f%%", pct)
        } else {
            "—"
        }

        val toT1Text = computeTargetPercent(cmp, t1, isBuy)
        val toLastText = computeTargetPercent(cmp, tLast, isBuy)

        val rrT1Text = computeRiskReward(cmp, t1, sl, isBuy)
        val rrLastText = computeRiskReward(cmp, tLast, sl, isBuy)

        val status = when {
            livePrice == null || livePrice <= 0.0 -> PickStatus.ACTIVE
            t1 != null && ((isBuy && livePrice >= t1) || (!isBuy && livePrice <= t1)) -> PickStatus.T1_HIT
            sl != null && ((isBuy && livePrice <= sl) || (!isBuy && livePrice >= sl)) -> PickStatus.SL_HIT
            else -> PickStatus.ACTIVE
        }

        return ComputedPickMetrics(
            isBuy = isBuy,
            target1 = t1,
            lastTarget = tLast,
            moveText = moveText,
            isMovePositive = moveVal?.let { it >= 0.0 },
            riskText = riskText,
            toT1Text = toT1Text,
            toLastText = toLastText,
            rrT1Text = rrT1Text,
            rrLastText = rrLastText,
            status = status,
        )
    }

    private fun computeTargetPercent(cmp: Double, target: Double?, isBuy: Boolean): String {
        if (target == null || cmp <= 0.0) return "—"
        val pct = (if (isBuy) target - cmp else cmp - target) / cmp * 100.0
        return String.format(Locale.US, "%.2f%%", pct)
    }

    private fun computeRiskReward(cmp: Double, target: Double?, sl: Double?, isBuy: Boolean): String {
        if (target == null || sl == null || cmp <= 0.0) return "—"
        val reward = if (isBuy) target - cmp else cmp - target
        val risk = if (isBuy) cmp - sl else sl - cmp
        return if (reward > 0.0 && risk > 0.0) {
            "1 : ${String.format(Locale.US, "%.2f", reward / risk)}"
        } else {
            "—"
        }
    }

    fun formatRelativeDays(dateString: String, nowMillis: Long = System.currentTimeMillis()): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            val parsed = parser.parse(dateString) ?: return ""
            val diffDays = ((nowMillis - parsed.time) / 86_400_000.0).roundToLong()
            if (diffDays <= 0L) "today" else "${diffDays}d ago"
        } catch (_: Exception) {
            ""
        }
    }

    fun formatShortDisplayDate(dateString: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            val parsed = parser.parse(dateString) ?: return dateString
            SimpleDateFormat("dd MMM", Locale.US).format(parsed)
        } catch (_: Exception) {
            dateString
        }
    }

    fun formatUpdatedTimestamp(isoString: String): String {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val normalized = normalizeFractionalSeconds(isoString)
                val date: Date? = sdf.parse(normalized)
                if (date != null) {
                    val out = SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", indianLocale)
                    return out.format(date)
                }
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return isoString
    }

    fun formatCurrentTime(millis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("hh:mm:ss a", indianLocale).format(Date(millis))
    }

    private fun normalizeFractionalSeconds(iso: String): String {
        val regex = Regex("""\.(\d{3})\d+""")
        return regex.replace(iso) { match -> ".${match.groupValues[1]}" }
    }
}
