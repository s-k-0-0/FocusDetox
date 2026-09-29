package com.kax.focusdetox.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kax.focusdetox.data.*
import com.kax.focusdetox.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  StatsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(
    repository: AppLimitRepository,
    settingsRepo: SettingsRepository,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val colors  = MaterialTheme.colorScheme

    // ── State ─────────────────────────────────────────────────────────────────
    var dailyTotals    by remember { mutableStateOf<List<DailyTotal>>(emptyList()) }
    var packageTotals  by remember { mutableStateOf<List<PackageTotal>>(emptyList()) }
    var sessions       by remember { mutableStateOf<List<FocusSession>>(emptyList()) }
    var limitedApps    by remember { mutableStateOf<List<AppLimitEntity>>(emptyList()) }
    var focusScore     by remember { mutableStateOf(0) }
    var isLoading      by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            dailyTotals   = repository.getWeeklyDailyTotals()
            packageTotals = repository.getWeeklyPackageTotals()
            sessions      = repository.getRecentSessionsList()
            limitedApps   = repository.getEnabledLimitsList()
            focusScore    = settingsRepo.getFocusScore()
            isLoading     = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    "Your week",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                )
                Text(
                    "Screen time & focus overview",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // ── Focus score card ─────────────────────────────────────────────────
        item {
            FocusScoreCard(score = focusScore, sessions = sessions)
        }

        // ── Weekly bar chart ──────────────────────────────────────────────────
        item {
            SectionHeader("Screen time this week")
            if (dailyTotals.isEmpty()) {
                EmptyStateCard("No usage recorded yet.\nStart using limited apps to see data here.")
            } else {
                WeeklyBarChart(data = dailyTotals)
            }
        }

        // ── Per-app breakdown ─────────────────────────────────────────────────
        item {
            SectionHeader("App breakdown (7 days)")
        }

        if (packageTotals.isEmpty()) {
            item { EmptyStateCard("No limited apps have been used yet.") }
        } else {
            val appNameMap = limitedApps.associate { it.packageName to it.appName }
            items(packageTotals) { pt ->
                val appName = appNameMap[pt.packageName] ?: pt.packageName
                val limitMs = limitedApps.find { it.packageName == pt.packageName }?.dailyLimitMs ?: 0L
                AppBreakdownRow(appName = appName, totalMs = pt.totalMs, dailyLimitMs = limitMs)
            }
        }

        // ── Focus sessions ────────────────────────────────────────────────────
        item {
            SectionHeader("Recent focus sessions")
        }

        if (sessions.isEmpty()) {
            item { EmptyStateCard("Start a focus session from the home screen.") }
        } else {
            items(sessions.take(5)) { session ->
                SessionRow(session = session)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Focus score card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FocusScoreCard(score: Int, sessions: List<FocusSession>) {
    val colors = MaterialTheme.colorScheme
    val completed = sessions.count { it.wasCompleted }
    val totalSessions = sessions.size

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Blue800,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Today's focus score", fontSize = 12.sp, color = Blue200)
                Text(
                    "$score pts",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue50,
                    lineHeight = 40.sp,
                )
                Text(
                    "Earn points by using productive apps",
                    fontSize = 12.sp,
                    color = Blue200,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$completed/$totalSessions",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue50,
                )
                Text("sessions\ncompleted", fontSize = 11.sp, color = Blue200, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Weekly bar chart (Canvas)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyBarChart(data: List<DailyTotal>) {
    val colors = MaterialTheme.colorScheme

    // Build a full 7-day map so missing days show as 0
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val days = (6 downTo 0).map { offset ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
        val dayLabel = SimpleDateFormat("EEE", Locale.US).format(cal.time)
        val isToday = dateStr == today
        val ms = data.find { it.date == dateStr }?.totalMs ?: 0L
        Triple(dayLabel, ms, isToday)
    }

    val maxMs = (data.maxOfOrNull { it.totalMs } ?: 1L).coerceAtLeast(1L)

    // Animate bars on entry
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "bar_anim",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Y-axis label
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "max ${formatMsShort(maxMs)}",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            val barColor     = Blue400
            val barColorToday = Blue600
            val trackColor   = colors.surfaceVariant
            val labelColor   = colors.onSurfaceVariant

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val barWidth  = (size.width - 16.dp.toPx()) / days.size
                val gap       = barWidth * 0.25f
                val netBar    = barWidth - gap

                days.forEachIndexed { i, (_, ms, isToday) ->
                    val x        = i * barWidth + gap / 2
                    val fraction = (ms.toFloat() / maxMs) * animProgress
                    val barH     = (size.height - 24.dp.toPx()) * fraction

                    // Track
                    drawRoundRect(
                        color       = trackColor,
                        topLeft     = Offset(x, 0f),
                        size        = Size(netBar, size.height - 24.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                    // Fill
                    if (barH > 0f) {
                        drawRoundRect(
                            color       = if (isToday) barColorToday else barColor,
                            topLeft     = Offset(x, size.height - 24.dp.toPx() - barH),
                            size        = Size(netBar, barH),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                    }
                }
            }

            // Day labels
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                days.forEach { (label, _, isToday) ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Total screen time across all limited apps",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  App breakdown row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppBreakdownRow(appName: String, totalMs: Long, dailyLimitMs: Long) {
    val colors = MaterialTheme.colorScheme
    val avgPerDay = totalMs / 7
    val fraction  = if (dailyLimitMs > 0) (avgPerDay.toFloat() / dailyLimitMs).coerceIn(0f, 1f) else 0f
    val barColor  = when {
        fraction < 0.6f -> Blue400
        fraction < 0.85f -> Color(0xFFEF9F27)
        else -> Color(0xFFE24B4A)
    }

    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600, easing = EaseOutCubic),
        label = "app_bar_$appName",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMsShort(totalMs), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Text("this week", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avg/day: ${formatMsShort(avgPerDay)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                if (dailyLimitMs > 0) {
                    Text("Limit: ${formatMsShort(dailyLimitMs)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Session row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionRow(session: FocusSession) {
    val colors = MaterialTheme.colorScheme
    val sdf    = SimpleDateFormat("MMM d, h:mm a", Locale.US)
    val start  = sdf.format(Date(session.startTime))
    val actual = if (session.endTime > 0) session.endTime - session.startTime else session.plannedDurationMs

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (session.wasCompleted) TealLight else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (session.wasCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = if (session.wasCompleted) Teal else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (session.wasCompleted) "Completed" else "Ended early",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onBackground,
                )
                Text(start, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMsShort(actual), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(
                    "of ${formatMsShort(session.plannedDurationMs)}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyStateCard(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
    ) {
        Text(
            message,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
            lineHeight = 20.sp,
        )
    }
}

private fun formatMsShort(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val rem = m % 60
    return when {
        h > 0 && rem > 0 -> "${h}h ${rem}m"
        h > 0            -> "${h}h"
        else             -> "${m}m"
    }
}