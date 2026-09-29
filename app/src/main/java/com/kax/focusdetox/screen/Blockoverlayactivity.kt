package com.kax.focusdetox.screen


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kax.focusdetox.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  BlockOverlayActivity
//
//  Full-screen activity launched by DetoxAccessibilityService when an app
//  reaches its daily limit. Replaces the old "go to Home screen" redirect with
//  a branded, calm block screen.
//
//  Launch from the service:
//
//    val intent = BlockOverlayActivity.createIntent(
//        context      = this,
//        packageName  = blockedPackage,
//        appName      = getAppName(blockedPackage),
//        usedMs       = getDailyUsageMs(blockedPackage),
//        limitMs      = getDailyLimitFor(blockedPackage) ?: 0L,
//        canSnooze    = !snoozeUsedForCurrentSession,
//    )
//    startActivity(intent)
//
//  Register in AndroidManifest.xml:
//    <activity
//        android:name=".ui.screens.BlockOverlayActivity"
//        android:excludeFromRecents="true"
//        android:launchMode="singleTask"
//        android:taskAffinity=""
//        android:exported="false"
//        android:theme="@style/Theme.FocusDetox" />
// ─────────────────────────────────────────────────────────────────────────────

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG       = "pkg"
        private const val EXTRA_APP_NAME  = "app_name"
        private const val EXTRA_USED_MS   = "used_ms"
        private const val EXTRA_LIMIT_MS  = "limit_ms"
        private const val EXTRA_CAN_SNOOZE = "can_snooze"
        const val ACTION_SNOOZE = "com.kax.focusdetox.ACTION_SNOOZE"

        fun createIntent(
            context: Context,
            packageName: String,
            appName: String,
            usedMs: Long,
            limitMs: Long,
            canSnooze: Boolean,
        ): Intent = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(EXTRA_PKG, packageName)
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_USED_MS, usedMs)
            putExtra(EXTRA_LIMIT_MS, limitMs)
            putExtra(EXTRA_CAN_SNOOZE, canSnooze)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName  = intent.getStringExtra(EXTRA_APP_NAME) ?: "this app"
        val usedMs   = intent.getLongExtra(EXTRA_USED_MS, 0L)
        val limitMs  = intent.getLongExtra(EXTRA_LIMIT_MS, 0L)
        val canSnooze = intent.getBooleanExtra(EXTRA_CAN_SNOOZE, false)

        setContent {
            // Force dark theme on the block screen regardless of system setting
            FocusDetoxTheme(darkTheme = true) {
                BlockScreen(
                    appName   = appName,
                    usedMs    = usedMs,
                    limitMs   = limitMs,
                    canSnooze = canSnooze,
                    onSnooze  = {
                        // Signal the service to grant the snooze
                        sendBroadcast(Intent(ACTION_SNOOZE).apply {
                            putExtra(EXTRA_PKG, intent.getStringExtra(EXTRA_PKG))
                        })
                        finish()
                    },
                    onGoHome  = { finish() },
                )
            }
        }
    }
}

// ─── Block Screen Composable ──────────────────────────────────────────────────

@Composable
fun BlockScreen(
    appName: String,
    usedMs: Long,
    limitMs: Long,
    canSnooze: Boolean,
    onSnooze: () -> Unit,
    onGoHome: () -> Unit,
) {
    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy950),
        contentAlignment = Alignment.Center,
    ) {

        // Subtle gradient wash behind the icon
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(pulseScale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Blue800.copy(alpha = 0.35f),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
        ) {

            // ── Icon ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Blue800),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    tint = Blue200,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Headline ────────────────────────────────────────────────────
            Text(
                text = "Time's up on $appName",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Blue50,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "You've used ${formatMs(usedMs)} today — your ${formatMs(limitMs)} daily limit has been reached.",
                fontSize = 15.sp,
                color = Blue200,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            // ── Usage bar ───────────────────────────────────────────────────
            Spacer(Modifier.height(32.dp))
            UsageBarBlock(usedMs = usedMs, limitMs = limitMs)

            Spacer(Modifier.height(40.dp))

            // ── Motivational nudge ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Navy800,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = motivationalQuote(),
                    fontSize = 13.sp,
                    color = Blue200,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Actions ─────────────────────────────────────────────────────
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue600,
                    contentColor   = Blue50,
                ),
            ) {
                Text("Go home", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }

            if (canSnooze) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(Blue700)
                    ),
                ) {
                    Text(
                        "5 more minutes (1× snooze)",
                        color = Blue200,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Snooze already used for this session",
                    fontSize = 12.sp,
                    color = Blue800,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─── Usage bar inside the block screen ───────────────────────────────────────

@Composable
private fun UsageBarBlock(usedMs: Long, limitMs: Long) {
    val fraction = if (limitMs > 0) (usedMs.toFloat() / limitMs).coerceIn(0f, 1f) else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatMs(usedMs), fontSize = 12.sp, color = Blue200)
            Text(formatMs(limitMs), fontSize = 12.sp, color = Blue800)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Navy800),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Blue400, Color(0xFFE24B4A)))
                    ),
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

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

private val Blue700 = Color(0xFF1A5A9E)

private fun motivationalQuote(): String {
    val quotes = listOf(
        "\"Almost everything will work again if you unplug it for a few minutes — including you.\"",
        "\"The key is not to prioritize what's on your schedule, but to schedule your priorities.\"",
        "\"Focus is not about saying yes. It's about saying no to almost everything.\"",
        "\"Distraction is the enemy of vision.\"",
        "\"You have the same 24 hours as everyone else. Use them well.\"",
    )
    return quotes.random()
}