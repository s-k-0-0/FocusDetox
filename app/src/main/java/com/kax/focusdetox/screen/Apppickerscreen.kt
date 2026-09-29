package com.kax.focusdetox.screen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kax.focusdetox.data.AppLimitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
//  Data model for an installed app
// ─────────────────────────────────────────────────────────────────────────────

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap,
)

// ─────────────────────────────────────────────────────────────────────────────
//  AppPickerScreen
//
//  Shows every user-installed app with an icon, name, and current limit badge.
//  Tapping an app opens a bottom sheet to set (or remove) a daily time limit.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(repository: AppLimitRepository) {

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val colors  = MaterialTheme.colorScheme

    // ── State ─────────────────────────────────────────────────────────────────
    var installedApps  by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(true) }
    var searchQuery    by remember { mutableStateOf("") }
    var selectedApp    by remember { mutableStateOf<InstalledApp?>(null) }

    // Live set of already-limited packages from Room
    val limitedApps by repository.observeEnabledLimits()
        .collectAsState(initial = emptyList())
    val limitedPackages = limitedApps.associate { it.packageName to it.dailyLimitMs }

    // ── Load installed apps once ───────────────────────────────────────────────
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) { loadUserApps(context) }
        isLoading = false
    }

    // ── Filter by search ──────────────────────────────────────────────────────
    val filtered = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true)
        }
    }

    // ── Sort: limited apps first, then alphabetical ───────────────────────────
    val sorted = remember(filtered, limitedPackages) {
        filtered.sortedWith(
            compareByDescending<InstalledApp> { limitedPackages.containsKey(it.packageName) }
                .thenBy { it.appName }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "App limits",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(
                "${limitedPackages.size} apps limited",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ── Search bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps…", fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null,
                    tint = colors.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear",
                            tint = colors.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = colors.primary,
                unfocusedBorderColor = colors.outline,
                focusedContainerColor   = colors.surface,
                unfocusedContainerColor = colors.surface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        )

        // ── List ─────────────────────────────────────────────────────────────
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
            sorted.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps found", color = colors.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(sorted, key = { it.packageName }) { app ->
                        AppRow(
                            app         = app,
                            currentLimit = limitedPackages[app.packageName],
                            onClick     = { selectedApp = app },
                        )
                    }
                }
            }
        }
    }

    // ── Limit setter bottom sheet ─────────────────────────────────────────────
    selectedApp?.let { app ->
        LimitSetterSheet(
            app          = app,
            currentLimit = limitedPackages[app.packageName],
            onSave       = { limitMs ->
                scope.launch {
                    repository.setLimit(
                        packageName  = app.packageName,
                        appName      = app.appName,
                        dailyLimitMs = limitMs,
                    )
                }
                selectedApp = null
            },
            onRemove = {
                scope.launch { repository.removeLimit(app.packageName) }
                selectedApp = null
            },
            onDismiss = { selectedApp = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  App row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app: InstalledApp,
    currentLimit: Long?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLimited = currentLimit != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // App icon
        Image(
            bitmap = app.icon,
            contentDescription = app.appName,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp)),
        )

        // Name + limit badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
            )
            if (isLimited) {
                Text(
                    "Limit: ${formatMsShort(currentLimit!!)} / day",
                    fontSize = 12.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    "No limit set",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        // Status badge
        if (isLimited) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.primaryContainer,
            ) {
                Text(
                    "ON",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Add limit",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp, end = 20.dp),
        color = colors.outlineVariant,
        thickness = 0.5.dp,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Limit setter bottom sheet
//
//  Time entry: two number fields — hours and minutes.
//  Pre-fills with the existing limit if one exists.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSetterSheet(
    app: InstalledApp,
    currentLimit: Long?,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // Pre-fill hours/minutes from existing limit
    val prefillHours   = currentLimit?.let { (it / 3_600_000).toInt() } ?: 0
    val prefillMinutes = currentLimit?.let { ((it % 3_600_000) / 60_000).toInt() } ?: 30

    var hours   by remember { mutableStateOf(prefillHours.toString()) }
    var minutes by remember { mutableStateOf(prefillMinutes.toString()) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val totalMs = ((hours.toIntOrNull() ?: 0) * 3_600_000L) +
            ((minutes.toIntOrNull() ?: 0) * 60_000L)
    val isValid = totalMs > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {

            // App header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column {
                    Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        if (currentLimit != null) "Change daily limit"
                        else "Set daily limit",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // Quick preset chips
            Text("Quick presets", fontSize = 12.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)) {
                listOf(
                    "15m" to (0 to 15),
                    "30m" to (0 to 30),
                    "1h"  to (1 to 0),
                    "2h"  to (2 to 0),
                ).forEach { (label, hm) ->
                    FilterChip(
                        selected = hours == hm.first.toString() && minutes == hm.second.toString(),
                        onClick  = { hours = hm.first.toString(); minutes = hm.second.toString() },
                        label    = { Text(label, fontSize = 13.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor     = colors.primary,
                        ),
                    )
                }
            }

            // Manual hour / minute input
            Text("Or enter manually", fontSize = 12.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                TimeField(
                    value     = hours,
                    label     = "Hours",
                    maxValue  = 23,
                    onChange  = { hours = it },
                    modifier  = Modifier.weight(1f),
                )
                TimeField(
                    value     = minutes,
                    label     = "Minutes",
                    maxValue  = 59,
                    onChange  = { minutes = it },
                    modifier  = Modifier.weight(1f),
                )
            }

            // Summary line
            if (isValid) {
                Text(
                    "Daily limit: ${formatMsShort(totalMs)}",
                    fontSize = 13.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Save button
            Button(
                onClick  = { if (isValid) onSave(totalMs) },
                enabled  = isValid,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text("Save limit", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            // Remove button (only if limit already exists)
            if (currentLimit != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick  = { showRemoveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove limit", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }

    // Confirm remove dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title   = { Text("Remove limit for ${app.appName}?") },
            text    = { Text("The app will no longer be blocked. Usage history is kept.") },
            confirmButton = {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Hour / minute number field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeField(
    value: String,
    label: String,
    maxValue: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value         = value,
        onValueChange = { raw ->
            val n = raw.filter { it.isDigit() }.take(2)
            if (n.isEmpty() || (n.toIntOrNull() ?: 0) <= maxValue) onChange(n)
        },
        label         = { Text(label, fontSize = 13.sp) },
        singleLine    = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction    = ImeAction.Next,
        ),
        shape  = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = colors.primary,
            unfocusedBorderColor  = colors.outline,
        ),
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Load user-installed apps (runs on IO dispatcher)
// ─────────────────────────────────────────────────────────────────────────────

private fun loadUserApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager

    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { info ->
            // Keep only apps the user intentionally installed
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLaunchIntent = pm.getLaunchIntentForPackage(info.packageName) != null

            (!isSystem || isUpdatedSystem) && hasLaunchIntent &&
                    info.packageName != context.packageName  // exclude FocusDetox itself
        }
        .mapNotNull { info ->
            try {
                val name = pm.getApplicationLabel(info).toString()
                val drawable = pm.getApplicationIcon(info.packageName)
                val bitmap = drawable.toBitmap()
                InstalledApp(
                    packageName = info.packageName,
                    appName     = name,
                    icon        = bitmap.asImageBitmap(),
                )
            } catch (e: Exception) {
                null  // skip apps whose icon can't be loaded
            }
        }
        .sortedBy { it.appName }
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

// ─────────────────────────────────────────────────────────────────────────────
//  Format helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMsShort(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0           -> "${h}h"
        else            -> "${m}m"
    }
}