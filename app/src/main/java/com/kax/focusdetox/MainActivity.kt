package com.kax.focusdetox

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kax.focusdetox.data.*
import com.kax.focusdetox.service.DetoxAccessibilityService
import com.kax.focusdetox.screen.*
import com.kax.focusdetox.ui.theme.FocusDetoxTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─────────────────────────────────────────────────────────────────────────────
//  MainActivity
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val appRepo      by lazy { AppLimitRepository(applicationContext) }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }

    private val viewModel by lazy {
        ViewModelProvider(this, HomeViewModelFactory(appRepo, settingsRepo))[HomeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FocusDetoxTheme {
                var accessibilityOk by remember { mutableStateOf(isAccessibilityEnabled()) }

                // Poll every second — updates as soon as user grants the permission
                LaunchedEffect(Unit) {
                    while (true) {
                        accessibilityOk = isAccessibilityEnabled()
                        delay(1_000)
                    }
                }

                if (!accessibilityOk) {
                    PermissionGateScreen(
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                } else {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    MainScaffold(
                        uiState      = uiState,
                        appRepo      = appRepo,
                        settingsRepo = settingsRepo,
                        onStartSession = { viewModel.startFocusSession(it) },
                        onStopSession  = { viewModel.stopFocusSession() },
                    )
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val target  = "${packageName}/${DetoxAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(target)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scaffold + bottom nav
// ─────────────────────────────────────────────────────────────────────────────

private enum class NavTab(val label: String, val icon: ImageVector) {
    Home("Home",     Icons.Outlined.Home),
    Apps("Apps",     Icons.Default.Menu),
    Stats("Stats",   Icons.Default.BarChart),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold(
    uiState: HomeUiState,
    appRepo: AppLimitRepository,
    settingsRepo: SettingsRepository,
    onStartSession: (Long) -> Unit,
    onStopSession: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(NavTab.Home) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.surface,
                tonalElevation = 0.dp,
            ) {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab },
                        icon     = { Icon(tab.icon, contentDescription = tab.label) },
                        label    = { Text(tab.label, fontSize = 11.sp) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = colors.primary,
                            selectedTextColor   = colors.primary,
                            indicatorColor      = colors.primaryContainer,
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        containerColor = colors.background,
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                NavTab.Home  -> HomeScreen(
                    uiState             = uiState,
                    onStartFocusSession = onStartSession,
                    onStopFocusSession  = onStopSession,
                    onOpenAppLimits     = { selectedTab = NavTab.Apps },
                    onOpenStats         = { selectedTab = NavTab.Stats },
                )
                NavTab.Apps     -> AppPickerScreen(repository = appRepo)
                NavTab.Stats    -> StatsScreen(repository = appRepo, settingsRepo = settingsRepo)
                NavTab.Settings -> SettingsScreen(settingsRepo = settingsRepo)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Permission gate screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionGateScreen(onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.AccessibilityNew,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "One permission needed",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "FocusDetox needs the Accessibility permission to detect which app is open " +
                    "and enforce your time limits. No personal data is read — only the package name of the active app.",
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Open Accessibility Settings", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap FocusDetox in the list, then toggle it on.",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HomeViewModel  — fully wired, no placeholders
// ─────────────────────────────────────────────────────────────────────────────

class HomeViewModel(
    private val appRepo: AppLimitRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeFocusSessionId = -1L
    private var sessionJob: Job? = null

    init {
        loadData()
        // Refresh every 60 s so usage numbers stay live
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                loadData()
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val limits = appRepo.getEnabledLimitsList()

            // Fetch today's usage for every limited app
            val stats = limits.map { limit ->
                AppUsageStat(
                    appName     = limit.appName,
                    packageName = limit.packageName,
                    usedMs      = appRepo.getTodayUsageMs(limit.packageName),
                    limitMs     = limit.dailyLimitMs,
                )
            }

            val goals      = appRepo.getActiveGoalsList()
            val maxStreak  = goals.maxOfOrNull { it.streakDays } ?: 0
            val totalUsed  = stats.sumOf { it.usedMs }
            val totalLimit = stats.sumOf { it.limitMs }.coerceAtLeast(1L)
            val score      = settingsRepo.getFocusScore()

            _uiState.update { current ->
                current.copy(
                    focusScore           = score,
                    dailyBudgetUsedMs    = totalUsed,
                    dailyBudgetTotalMs   = totalLimit,
                    streakDays           = maxStreak,
                    appStats             = stats,
                )
            }
        }
    }

    fun startFocusSession(durationMs: Long) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            activeFocusSessionId = appRepo.startFocusSession(durationMs)
            _uiState.update { it.copy(isFocusSessionActive = true, focusSessionRemainingMs = durationMs) }

            val endTime = System.currentTimeMillis() + durationMs
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) break
                _uiState.update { it.copy(focusSessionRemainingMs = remaining) }
                delay(1_000)
            }

            // Completed naturally
            if (_uiState.value.isFocusSessionActive) {
                appRepo.endFocusSession(activeFocusSessionId, completed = true)
                _uiState.update { it.copy(isFocusSessionActive = false, focusSessionRemainingMs = 0L) }
                settingsRepo.addFocusScore(50) // bonus for completing a session
                loadData() // refresh score on home screen
            }
        }
    }

    fun stopFocusSession() {
        sessionJob?.cancel()
        viewModelScope.launch {
            if (activeFocusSessionId != -1L) {
                appRepo.endFocusSession(activeFocusSessionId, completed = false)
            }
            _uiState.update { it.copy(isFocusSessionActive = false, focusSessionRemainingMs = 0L) }
        }
    }
}

class HomeViewModelFactory(
    private val appRepo: AppLimitRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(appRepo, settingsRepo) as T
}