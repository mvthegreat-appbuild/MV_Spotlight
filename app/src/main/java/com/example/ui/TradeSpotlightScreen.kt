package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PickFormatters
import com.example.data.repository.DataSource
import com.example.ui.components.PickCard
import com.example.ui.theme.AmberOffline
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldPillBg
import com.example.ui.theme.EmeraldPillBorder
import com.example.ui.theme.EmeraldPillText
import com.example.ui.theme.HeaderBottomGradient
import com.example.ui.theme.HeaderTopGradient
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.PillTextInactive
import com.example.ui.theme.RefreshBtnBg
import com.example.ui.theme.RefreshBtnBorder
import com.example.ui.theme.RefreshBtnText
import com.example.ui.theme.SearchFieldBg
import com.example.ui.theme.TextFooter
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSpotlightScreen(
    viewModel: TradeSpotlightViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    // Refresh when returning to foreground, matching the original visibilitychange listener
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadPicks(isUserRefresh = false)
    }

    Scaffold(
        containerColor = DeepNavyBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HeaderBanner(
                uiState = uiState,
                onRefreshClick = { viewModel.loadPicks(isUserRefresh = true) },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val columns = if (maxWidth >= 600.dp) 2 else 1

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.loadPicks(isUserRefresh = true) },
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("picks_list"),
                    ) {
                        // Search Bar
                        item(span = { GridItemSpan(columns) }) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        color = TextMuted,
                                        fontSize = 14.sp,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = TextMuted,
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") },
                                            modifier = Modifier.testTag("clear_search_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.clear_search),
                                                tint = TextMuted,
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SearchFieldBg,
                                    unfocusedContainerColor = SearchFieldBg,
                                    focusedBorderColor = EmeraldPillBorder,
                                    unfocusedBorderColor = BorderSlate,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = EmeraldGreen,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_input"),
                            )
                        }

                        // Filter Pills Row
                        if (uiState.picksResponse != null) {
                            item(span = { GridItemSpan(columns) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterPill(
                                        label = "${stringResource(R.string.filter_all)} ${uiState.allCount}",
                                        selected = uiState.selectedFilter == CallFilter.ALL,
                                        onClick = { viewModel.onFilterSelected(CallFilter.ALL) },
                                        testTag = "filter_all",
                                    )
                                    FilterPill(
                                        label = "${stringResource(R.string.filter_buy)} ${uiState.buyCount}",
                                        selected = uiState.selectedFilter == CallFilter.BUY,
                                        onClick = { viewModel.onFilterSelected(CallFilter.BUY) },
                                        testTag = "filter_buy",
                                    )
                                    FilterPill(
                                        label = "${stringResource(R.string.filter_sell)} ${uiState.sellCount}",
                                        selected = uiState.selectedFilter == CallFilter.SELL,
                                        onClick = { viewModel.onFilterSelected(CallFilter.SELL) },
                                        testTag = "filter_sell",
                                    )
                                }
                            }
                        }

                        // Body Content: Loading / Error / Empty / Pick Cards
                        when {
                            uiState.isInitialLoading -> {
                                item(span = { GridItemSpan(columns) }) {
                                    EmptyStateCard(
                                        message = stringResource(R.string.status_loading),
                                        showSpinner = true,
                                    )
                                }
                            }

                            uiState.picksResponse == null -> {
                                item(span = { GridItemSpan(columns) }) {
                                    EmptyStateCard(
                                        message = uiState.errorMessage
                                            ?: stringResource(R.string.empty_no_data),
                                        showSpinner = false,
                                    )
                                }
                            }

                            uiState.filteredPicks.isEmpty() -> {
                                item(span = { GridItemSpan(columns) }) {
                                    EmptyStateCard(
                                        message = stringResource(R.string.empty_no_picks),
                                        showSpinner = false,
                                    )
                                }
                            }

                            else -> {
                                items(
                                    items = uiState.filteredPicks,
                                    key = { pick -> "${pick.stock}|${pick.call}|${pick.date}|${pick.broker.orEmpty()}" },
                                ) { pick ->
                                    PickCard(
                                        pick = pick,
                                        liveQuote = uiState.liveQuotes[pick.stock],
                                        isLoadingQuotes = uiState.isLoadingQuotes,
                                        onOpenArticle = { url ->
                                            try {
                                                uriHandler.openUri(url)
                                            } catch (_: Exception) {
                                                // Ignore malformed or unresolvable URLs safely
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Refresh Button & Disclaimer Footer
                        item(span = { GridItemSpan(columns) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 16.dp),
                            ) {
                                Button(
                                    onClick = { viewModel.loadPicks(isUserRefresh = true) },
                                    enabled = !uiState.isRefreshing,
                                    shape = RoundedCornerShape(9.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RefreshBtnBg,
                                        contentColor = RefreshBtnText,
                                        disabledContainerColor = RefreshBtnBg.copy(alpha = 0.6f),
                                        disabledContentColor = RefreshBtnText.copy(alpha = 0.7f),
                                    ),
                                    border = BorderStroke(1.dp, RefreshBtnBorder),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("refresh_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.refresh),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = stringResource(R.string.disclaimer_footer),
                                    color = TextFooter,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBanner(
    uiState: TradeSpotlightUiState,
    onRefreshClick: () -> Unit,
) {
    val headerBrush = Brush.linearGradient(
        colors = listOf(HeaderTopGradient, HeaderBottomGradient),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = headerBrush)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(EmeraldPillBg)
                        .border(1.dp, EmeraldPillBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
            }

            IconButton(
                onClick = onRefreshClick,
                enabled = !uiState.isRefreshing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("header_refresh_button"),
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        color = EmeraldGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.refresh_picks),
                        tint = EmeraldGreen,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val (statusText, statusColor) = when {
            uiState.isInitialLoading -> stringResource(R.string.status_loading) to TextMuted
            uiState.picksResponse != null -> when (uiState.dataSource) {
                DataSource.REMOTE -> stringResource(R.string.status_refreshed) to EmeraldGreen
                DataSource.OFFLINE_CACHE -> stringResource(R.string.status_offline_cache) to AmberOffline
                DataSource.BUNDLED_SNAPSHOT -> stringResource(R.string.status_bundled_fallback) to AmberOffline
                null -> "" to TextMuted
            }
            else -> stringResource(R.string.status_error) to CoralRed
        }

        if (statusText.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }
        }

        val response = uiState.picksResponse
        if (response != null) {
            val formattedDate = remember(response.updated) {
                PickFormatters.formatUpdatedTimestamp(response.updated)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Data time: $formattedDate · Articles: ${response.articles}",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 16.sp,
            )
            if (uiState.appCheckedAt.isNotEmpty()) {
                val quoteStatus = if (uiState.isLoadingQuotes) " · Updating live CMP…" else ""
                Text(
                    text = "App checked at: ${uiState.appCheckedAt}$quoteStatus",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp,
                )
            }
            Text(
                text = stringResource(R.string.auto_update_schedule),
                fontSize = 11.sp,
                color = EmeraldGreen.copy(alpha = 0.9f),
                lineHeight = 16.sp,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderSlate),
    )
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(if (selected) EmeraldPillBg else NavySurfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) EmeraldPillBorder else BorderSlate,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) EmeraldPillText else PillTextInactive,
        )
    }
}

@Composable
private fun EmptyStateCard(
    message: String,
    showSpinner: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurfaceVariant)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = EmeraldGreen,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = message,
            color = TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
