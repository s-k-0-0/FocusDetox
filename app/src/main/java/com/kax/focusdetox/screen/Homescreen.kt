package com.kax.focusdetox.screen


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kax.focusdetox.ui.theme.*

// ─── Data ────────────────────────────────────────────────────────────────────

data class AppUsageStat(
    val appName: String,
    val packageName: String,
    val usedMs: Long,
    val limitMs: Long,
)

data class HomeUiState(
    val focusScore: Int = 0,
    val dailyBudgetUsedMs: Long = 0L,
    val dailyBudgetTotalMs: Long = 2 * 60 * 60 * 1000L, // 2h total default
    val streakDays: Int = 0,
    val isFocusSessionActive: Boolean = false,
    val focusSessionRemainingMs: Long = 0L,
    val appStats: List<AppUsageStat> = emptyList(),
)

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStartFocusSession: (durationMs: Long) -> Unit,
    onStopFocusSession: () -> Unit,
    onOpenAppLimits: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "FocusDetox",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    )
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onOpenStats,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = colors.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = "Stats",
                        tint = colors.primary,
                    )
                }
            }
        }

        // ── Radial focus ring ─────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FocusRing(
                    usedMs = uiState.dailyBudgetUsedMs,
                    totalMs = uiState.dailyBudgetTotalMs,
                    score = uiState.focusScore,
                    streakDays = uiState.streakDays,
                )
            }
        }

        // ── Focus session button / timer ──────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            if (uiState.isFocusSessionActive) {
                ActiveSessionCard(
                    remainingMs = uiState.focusSessionRemainingMs,
                    onStop = onStopFocusSession,
                )
            } else {
                StartSessionCard(onStart = onStartFocusSession)
            }
        }

        // ── Quick stat chips ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { StatChip(label = "Score today", value = "${uiState.focusScore} pts", icon = Icons.Outlined.Star) }
                item { StatChip(label = "Streak", value = "${uiState.streakDays} days 🔥", icon = Icons.Outlined.Fireplace) }
                item { StatChip(label = "Time saved", value = formatMs(uiState.dailyBudgetTotalMs - uiState.dailyBudgetUsedMs), icon = Icons.Outlined.AccessTime) }
            }
        }

        // ── App usage list ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "App limits today",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onBackground,
                )
                TextButton(onClick = onOpenAppLimits) {
                    Text("Manage", color = colors.primary, fontSize = 13.sp)
                }
            }
        }

        if (uiState.appStats.isEmpty()) {
            item { EmptyAppsCard(onAdd = onOpenAppLimits) }
        } else {
            items(uiState.appStats) { stat ->
                AppUsageRow(stat = stat)
            }
        }
    }
}

// ─── Radial Focus Ring ───────────────────────────────────────────────────────

@Composable
fun FocusRing(
    usedMs: Long,
    totalMs: Long,
    score: Int,
    streakDays: Int,
) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (totalMs > 0) (usedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val ringColor = when {
        fraction < 0.6f -> Blue400
        fraction < 0.85f -> Color(0xFFEF9F27)  // amber
        else -> Color(0xFFE24B4A)               // red
    }
    val trackColor = colors.surfaceVariant

    // Animate the sweep angle on composition
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
        label = "ring_sweep",
    )

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress
            if (animatedFraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$score",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                lineHeight = 48.sp,
            )
            Text(
                text = "focus score",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                letterSpacing = 0.08.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${formatMs(usedMs)} / ${formatMs(totalMs)}",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Session Cards ────────────────────────────────────────────────────────────

@Composable
fun StartSessionCard(onStart: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val durations = listOf(
        "25 min" to 25 * 60 * 1000L,
        "45 min" to 45 * 60 * 1000L,
        "1 hour" to 60 * 60 * 1000L,
        "Custom" to -1L,
    )
    var selected by remember { mutableStateOf(durations[0]) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Focus session",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer,
            )
            Text(
                "Block all distracting apps for a set window",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )

            // Duration chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(durations) { (label, ms) ->
                    FilterChip(
                        selected = selected.first == label,
                        onClick = { selected = label to ms },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onStart(selected.second) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start session", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ActiveSessionCard(remainingMs: Long, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Blue800),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing dot
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_pulse",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Blue200.copy(alpha = alpha))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Session active", fontSize = 12.sp, color = Blue200)
                Text(
                    formatMs(remainingMs),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue50,
                    lineHeight = 28.sp,
                )
                Text("remaining", fontSize = 12.sp, color = Blue200)
            }
            OutlinedButton(
                onClick = onStop,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Blue200)
                ),
            ) {
                Text("Stop", color = Blue200, fontSize = 13.sp)
            }
        }
    }
}

// ─── Stat Chip ────────────────────────────────────────────────────────────────

@Composable
fun StatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 0.dp,
        border = ButtonDefaults.outlinedButtonBorder,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Column {
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

// ─── App Usage Row ────────────────────────────────────────────────────────────

@Composable
fun AppUsageRow(stat: AppUsageStat) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (stat.limitMs > 0) (stat.usedMs.toFloat() / stat.limitMs).coerceIn(0f, 1f) else 0f
    val barColor = when {
        fraction < 0.6f -> Blue400
        fraction < 0.85f -> Color(0xFFEF9F27)
        else -> Color(0xFFE24B4A)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = ButtonDefaults.outlinedButtonBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stat.appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Text(
                    "${formatMs(stat.usedMs)} / ${formatMs(stat.limitMs)}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(600, easing = EaseOutCubic),
                label = "bar_${stat.packageName}",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun EmptyAppsCard(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable(onClick = onAdd),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.AddCircleOutline,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No app limits set yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                "Tap to add your first distracting app",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ─── Utils ────────────────────────────────────────────────────────────────────

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Stay focused"
    }
}