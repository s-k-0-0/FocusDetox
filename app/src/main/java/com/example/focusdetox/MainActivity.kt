package com.example.focusdetox

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focusdetox.data.AppInfo
import com.example.focusdetox.data.AppListProvider
import com.example.focusdetox.data.DetoxSettings
import com.example.focusdetox.data.DetoxSettingsRepository
import com.example.focusdetox.service.DetoxAccessibilityService
import com.example.focusdetox.screen.AppPickerBottomSheet
import com.example.focusdetox.ui.theme.WAHIcons
import com.example.focusdetox.ui.theme.delete
import com.example.focusdetox.ui.theme.notifications_active
import com.example.focusdetox.ui.theme.shield
import com.example.focusdetox.ui.theme.shield_moon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF81C784),
                    primaryContainer = Color(0xFF1B382B),
                    surface = Color(0xFF121413),
                    surfaceContainerHigh = Color(0xFF1E211F)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    DetoxControlCenter()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetoxControlCenter() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DetoxSettingsRepository(context) }
    val settingsState = repository.settingsFlow.collectAsState(initial = DetoxSettings())
    val settings = settingsState.value

    var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    var blockedAppInfos by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(settings.blockedPackages) {
        val installed = AppListProvider.getInstalledLauncherApps(context)
        blockedAppInfos = installed.filter { settings.blockedPackages.contains(it.packageName) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Focus Detox", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isServiceEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isServiceEnabled) WAHIcons.shield else WAHIcons.shield_moon,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isServiceEnabled) "Detox Active" else "Protection Off",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceEnabled) "Monitoring target apps" else "Enable permission to start",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (!isServiceEnabled) showDisclosureDialog = true
                                else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isServiceEnabled) "Manage" else "Turn On")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Blocking Rules",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ModernToggleRow(
                            icon = Icons.Rounded.Lock,
                            title = "Instant App Lock",
                            subtitle = "Block selected apps as soon as opened",
                            checked = settings.isAppLockEnabled,
                            onCheckedChange = { scope.launch { repository.updateAppLockEnabled(it) } }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )

                        ModernToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Session Time Limit",
                            subtitle = "Lock apps after continuous usage time",
                            checked = settings.isSessionTimerEnabled,
                            onCheckedChange = { scope.launch { repository.updateSessionTimerEnabled(it) } }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )

                        ModernToggleRow(
                            icon = WAHIcons.notifications_active,
                            title = "Alert Notifications",
                            subtitle = "Notify when session time or app lockout triggers",
                            checked = settings.isNotificationEnabled,
                            onCheckedChange = { scope.launch { repository.updateNotificationEnabled(it) } }
                        )
                    }
                }
            }

            // Lag-Free Session Duration Slider
            item {
                AnimatedVisibility(
                    visible = settings.isSessionTimerEnabled,
                    enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Session Time Limit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Allowed continuous usage: ${settings.maxSessionMinutes} minutes",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Local state slider prevents stutter/lag during dragging
                            var sliderPosition by remember(settings.maxSessionMinutes) {
                                mutableStateOf(settings.maxSessionMinutes.toFloat())
                            }

                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = {
                                    scope.launch { repository.updateMaxSessionMinutes(sliderPosition.roundToInt()) }
                                },
                                valueRange = 1f..60f,
                                steps = 58
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Protected Apps", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAppPickerSheet = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage Apps")
                    }
                }
            }

            if (blockedAppInfos.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No apps selected. Tap 'Manage Apps' to add YouTube, Instagram, or TikTok.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(blockedAppInfos, key = { it.packageName }) { app ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconBitmap = remember(app.icon) {
                                app.icon?.toBitmap(width = 80, height = 80)?.asImageBitmap()
                            }
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = app.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    scope.launch { repository.togglePackageBlocked(app.packageName, false) }
                                }
                            ) {
                                Icon(
                                    WAHIcons.delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAppPickerSheet) {
        AppPickerBottomSheet(
            blockedPackages = settings.blockedPackages,
            onToggleApp = { pkg, isBlocked ->
                scope.launch { repository.togglePackageBlocked(pkg, isBlocked) }
            },
            onDismiss = { showAppPickerSheet = false }
        )
    }

    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
            title = { Text("Focus Protection Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "FocusDetox uses Accessibility Services to detect when " +
                            "your target apps are open or usage time limits are exceeded.\n\n" +
                            "• Redirects you to Home when limits are reached.\n" +
                            "• Zero personal data is recorded or shared."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisclosureDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) {
                    Text("Agree & Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModernToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedService = "${context.packageName}/${DetoxAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)

    while (splitter.hasNext()) {
        if (splitter.next().equals(expectedService, ignoreCase = true)) return true
    }
    return false
}