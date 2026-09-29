package com.kax.focusdetox.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kax.focusdetox.data.SettingsRepository
import com.kax.focusdetox.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(settingsRepo: SettingsRepository) {

    val colors = MaterialTheme.colorScheme

    // ── Local state ───────────────────────────────────────────────────────────
    var isPro             by remember { mutableStateOf(settingsRepo.isPro) }
    var isStrictMode      by remember { mutableStateOf(settingsRepo.isStrictModeEnabled) }
    var warningEnabled    by remember { mutableStateOf(settingsRepo.warningNotifEnabled) }
    var blockNotifEnabled by remember { mutableStateOf(settingsRepo.blockNotifEnabled) }
    var snoozeEnabled     by remember { mutableStateOf(settingsRepo.snoozeEnabled) }
    var themeMode         by remember { mutableStateOf(settingsRepo.themeMode) }

    // ── Dialog states ─────────────────────────────────────────────────────────
    var showPinDialog       by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showThemeDialog     by remember { mutableStateOf(false) }
    var showProDialog       by remember { mutableStateOf(false) }
    // callback to run after PIN is verified
    var onPinVerified       by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text("Preferences & account", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }

        // ── Pro upgrade banner ────────────────────────────────────────────────
        if (!isPro) {
            ProBanner(onClick = { showProDialog = true })
            Spacer(Modifier.height(12.dp))
        } else {
            ProBadge()
            Spacer(Modifier.height(4.dp))
        }

        // ── Blocking section ──────────────────────────────────────────────────
        SettingsSection(title = "Blocking") {

            SettingsToggleRow(
                icon    = Icons.Outlined.Lock,
                title   = "Strict mode",
                subtitle = "Require PIN to change any setting or limit",
                checked = isStrictMode,
                onToggle = { newValue ->
                    if (!newValue) {
                        // Turning OFF strict mode — verify PIN first if one exists
                        if (settingsRepo.hasPin()) {
                            onPinVerified = {
                                settingsRepo.isStrictModeEnabled = false
                                settingsRepo.clearPin()
                                isStrictMode = false
                            }
                            showPinVerifyDialog = true
                        } else {
                            settingsRepo.isStrictModeEnabled = false
                            isStrictMode = false
                        }
                    } else {
                        // Turning ON — set a PIN
                        showPinDialog = true
                    }
                },
            )

            if (isStrictMode && settingsRepo.hasPin()) {
                SettingsClickRow(
                    icon     = Icons.Outlined.Password,
                    title    = "Change PIN",
                    subtitle = "Update your strict mode PIN",
                    onClick  = {
                        onPinVerified = { showPinDialog = true }
                        showPinVerifyDialog = true
                    },
                )
            }

            SettingsToggleRow(
                icon     = Icons.Outlined.Snooze,
                title    = "Allow snooze",
                subtitle = "Users can extend a session once by 5 minutes",
                checked  = snoozeEnabled,
                onToggle = { snoozeEnabled = it; settingsRepo.snoozeEnabled = it },
            )
        }

        // ── Notifications section ─────────────────────────────────────────────
        SettingsSection(title = "Notifications") {

            SettingsToggleRow(
                icon     = Icons.Outlined.NotificationsActive,
                title    = "Warning notification",
                subtitle = "Alert 1 minute before the limit is reached",
                checked  = warningEnabled,
                onToggle = { warningEnabled = it; settingsRepo.warningNotifEnabled = it },
            )

            SettingsToggleRow(
                icon     = Icons.Outlined.Block,
                title    = "Block notification",
                subtitle = "Show notification when an app is blocked",
                checked  = blockNotifEnabled,
                onToggle = { blockNotifEnabled = it; settingsRepo.blockNotifEnabled = it },
            )
        }

        // ── Appearance section ────────────────────────────────────────────────
        SettingsSection(title = "Appearance") {
            val themeLabel = when (themeMode) { 1 -> "Light"; 2 -> "Dark"; else -> "Follow system" }
            SettingsClickRow(
                icon     = Icons.Outlined.Brightness6,
                title    = "Theme",
                subtitle = themeLabel,
                onClick  = { showThemeDialog = true },
            )
        }

        // ── About section ─────────────────────────────────────────────────────
        SettingsSection(title = "About") {
            SettingsClickRow(
                icon     = Icons.Outlined.Star,
                title    = "Rate FocusDetox",
                subtitle = "Leave a review on the Play Store",
                onClick  = { /* open Play Store intent */ },
            )
            SettingsClickRow(
                icon     = Icons.Outlined.Shield,
                title    = "Privacy policy",
                subtitle = "How we handle your data",
                onClick  = { /* open browser */ },
            )
            SettingsInfoRow(
                icon     = Icons.Outlined.Info,
                title    = "Version",
                value    = "1.0.0",
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showPinDialog) {
        PinSetDialog(
            onSet = { pin ->
                settingsRepo.setPin(pin)
                settingsRepo.isStrictModeEnabled = true
                isStrictMode = true
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false; isStrictMode = false },
        )
    }

    if (showPinVerifyDialog) {
        PinVerifyDialog(
            onVerified = {
                showPinVerifyDialog = false
                onPinVerified?.invoke()
                onPinVerified = null
            },
            onFailed  = { /* shake or show error — handled inside dialog */ },
            onDismiss = { showPinVerifyDialog = false },
            verify    = { pin -> settingsRepo.verifyPin(pin) },
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            current   = themeMode,
            onPick    = { mode ->
                themeMode = mode
                settingsRepo.themeMode = mode
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showProDialog) {
        ProUpgradeDialog(
            onBuy = {
                // TODO: wire to BillingClient
                settingsRepo.isPro = true
                isPro = true
                showProDialog = false
            },
            onDismiss = { showProDialog = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pro banner / badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProBanner(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Blue600, Blue400)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Upgrade to Pro", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue50)
                Text(
                    "Unlock schedules, strict mode, weekly reports, goals & more",
                    fontSize = 13.sp, color = Blue100, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = Blue50) {
                Text(
                    "₹299",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Blue600,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProBadge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = Blue400, modifier = Modifier.size(16.dp))
        Text("You're on Pro — thank you!", fontSize = 13.sp, color = Blue400, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Settings section wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column { content() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Row types
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
            Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = Blue50,
                checkedTrackColor  = Blue600,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
            Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 16.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onBackground, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = colors.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PIN dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PinSetDialog(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Set a PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("You'll need this to change settings when strict mode is on.", fontSize = 13.sp, color = colors.onSurfaceVariant, lineHeight = 18.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin1 = it },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin2 = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {{ Text(error, color = colors.error) }} else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            when {
                                pin1.length < 4 -> error = "PIN must be 4 digits"
                                pin1 != pin2    -> error = "PINs don't match"
                                else            -> onSet(pin1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun PinVerifyDialog(
    onVerified: () -> Unit,
    onFailed: () -> Unit,
    onDismiss: () -> Unit,
    verify: (String) -> Boolean,
) {
    val colors = MaterialTheme.colorScheme
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Enter PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {{ Text(error, color = colors.error) }} else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (verify(pin)) onVerified()
                            else { error = "Incorrect PIN"; onFailed() }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Theme picker dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemePickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val options = listOf("Follow system" to 0, "Light" to 1, "Dark" to 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme") },
        text = {
            Column {
                options.forEach { (label, mode) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { onPick(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(label, fontSize = 15.sp, color = colors.onBackground)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pro upgrade dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProUpgradeDialog(onBuy: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val features = listOf(
        "Unlimited app limiting",
        "Schedules (weekday / weekend limits)",
        "Strict mode with PIN lock",
        "7-day usage history & charts",
        "Weekly shareable report card",
        "Goals and streaks",
        "Home screen widget",
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("FocusDetox Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                Text("One-time purchase · no subscription", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))

                features.forEach { f ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Check, null, tint = Blue400, modifier = Modifier.size(16.dp))
                        Text(f, fontSize = 14.sp, color = colors.onBackground)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                ) {
                    Text("Upgrade for ₹299", fontWeight = FontWeight.SemiBold, color = Blue50, modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Maybe later", color = colors.onSurfaceVariant)
                }
            }
        }
    }
}