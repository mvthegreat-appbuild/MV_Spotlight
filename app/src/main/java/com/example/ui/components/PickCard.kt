package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LiveQuote
import com.example.data.model.PickFormatters
import com.example.data.model.PickStatus
import com.example.data.model.StockPick
import com.example.ui.theme.ActiveBadgeBg
import com.example.ui.theme.ActiveBadgeBorder
import com.example.ui.theme.ActiveBadgeText
import com.example.ui.theme.BuyBadgeBg
import com.example.ui.theme.BuyBadgeText
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CardBottomGradient
import com.example.ui.theme.CardTopGradient
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldPillBorder
import com.example.ui.theme.EmeraldPillText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LinkCyan
import com.example.ui.theme.LiveCmpCyan
import com.example.ui.theme.MetricTileBg
import com.example.ui.theme.SellBadgeBg
import com.example.ui.theme.SellBadgeText
import com.example.ui.theme.TargetPillBg
import com.example.ui.theme.TargetPillBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TileBorderSlate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickCard(
    pick: StockPick,
    liveQuote: LiveQuote?,
    isLoadingQuotes: Boolean,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(pick, liveQuote) { PickFormatters.computeMetrics(pick, liveQuote) }
    val shortDate = remember(pick.date) { PickFormatters.formatShortDisplayDate(pick.date) }
    val relativeDate = remember(pick.date) { PickFormatters.formatRelativeDays(pick.date) }
    val brokerLabel = pick.broker?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.broker_not_named)
    val periodSuffix = pick.period?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""

    val cardShape = RoundedCornerShape(14.dp)
    val cardBrush = Brush.linearGradient(
        colors = listOf(CardTopGradient, CardBottomGradient),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(brush = cardBrush)
            .border(width = 1.dp, color = CardBorderSlate, shape = cardShape)
            .padding(14.dp)
            .testTag("pick_card_${pick.stock}"),
    ) {
        // Header Row: Stock + Subtitle left, Status + BUY/SELL pills stacked right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Text(
                    text = pick.stock,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val datePart = if (relativeDate.isNotEmpty()) "$shortDate ($relativeDate)" else shortDate
                Text(
                    text = "$datePart · $brokerLabel$periodSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 16.sp,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusBadge(status = metrics.status)
                Surface(
                    color = if (metrics.isBuy) BuyBadgeBg else SellBadgeBg,
                    shape = RoundedCornerShape(7.dp),
                ) {
                    Text(
                        text = pick.call.uppercase(),
                        color = if (metrics.isBuy) BuyBadgeText else SellBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3-Column Price Grid: Entry · CMP | CMP (SYMBOL) | Stop Loss
        val liveCmpLabel = if (liveQuote != null) {
            stringResource(R.string.label_live_cmp, liveQuote.shortSymbol)
        } else {
            stringResource(R.string.label_live_cmp_plain)
        }
        val liveCmpValue = when {
            liveQuote != null -> PickFormatters.formatInr(liveQuote.price)
            isLoadingQuotes -> "…"
            else -> "—"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PriceTile(
                label = stringResource(R.string.label_cmp),
                value = PickFormatters.formatInr(pick.cmp),
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            PriceTile(
                label = liveCmpLabel,
                value = liveCmpValue,
                valueColor = LiveCmpCyan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("live_cmp_${pick.stock}"),
            )
            PriceTile(
                label = stringResource(R.string.label_stop_loss),
                value = PickFormatters.formatInr(pick.stopLoss),
                valueColor = CoralRed,
                modifier = Modifier.weight(1f),
            )
        }

        // Target Pill(s) Row: T1 ₹..., T2 ₹...
        if (pick.targets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pick.targets.forEachIndexed { index, targetVal ->
                    val label = if (index == 0) {
                        stringResource(R.string.label_target_1)
                    } else {
                        stringResource(R.string.label_target_n, index + 1)
                    }
                    Surface(
                        color = TargetPillBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TargetPillBorder),
                    ) {
                        Text(
                            text = "$label ${PickFormatters.formatInr(targetVal)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = EmeraldPillText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Metrics Rows matching reference screenshot
        val moveColor = when (metrics.isMovePositive) {
            true -> EmeraldGreen
            false -> CoralRed
            null -> TextMuted
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InlineMetric(
                label = stringResource(R.string.label_move),
                value = metrics.moveText,
                valueColor = moveColor,
            )
            InlineMetric(
                label = stringResource(R.string.label_risk),
                value = metrics.riskText,
                valueColor = CoralRed,
            )
            InlineMetric(
                label = stringResource(R.string.label_upside),
                value = metrics.toT1Text,
                valueColor = EmeraldGreen,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (pick.targets.size > 1) {
                InlineMetric(
                    label = stringResource(R.string.label_to_last),
                    value = metrics.toLastText,
                    valueColor = EmeraldGreen,
                )
            }
            InlineMetric(
                label = stringResource(R.string.label_rr_t1),
                value = metrics.rrT1Text,
                valueColor = TextPrimary,
            )
            if (pick.targets.size > 1) {
                InlineMetric(
                    label = stringResource(R.string.label_rr_last),
                    value = metrics.rrLastText,
                    valueColor = TextPrimary,
                )
            }
        }

        // Read Original Article Link
        if (pick.link.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenArticle(pick.link) }
                    .padding(vertical = 6.dp, horizontal = 2.dp)
                    .testTag("read_original_${pick.stock}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.read_original),
                    color = LinkCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.read_original),
                    tint = LinkCyan,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: PickStatus) {
    val (label, bg, border, textColor) = when (status) {
        PickStatus.ACTIVE -> Quad(
            stringResource(R.string.status_active),
            ActiveBadgeBg,
            ActiveBadgeBorder,
            ActiveBadgeText,
        )
        PickStatus.T1_HIT -> Quad(
            stringResource(R.string.status_t1_hit),
            BuyBadgeBg,
            EmeraldPillBorder,
            BuyBadgeText,
        )
        PickStatus.SL_HIT -> Quad(
            stringResource(R.string.status_sl_hit),
            SellBadgeBg,
            CoralRed.copy(alpha = 0.6f),
            SellBadgeText,
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@Composable
private fun PriceTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MetricTileBg)
            .border(BorderStroke(1.dp, TileBorderSlate), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlineMetric(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            fontSize = 12.sp,
            color = TextMuted,
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}
