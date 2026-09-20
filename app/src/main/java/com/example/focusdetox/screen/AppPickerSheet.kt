package com.example.focusdetox.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.focusdetox.data.AppInfo
import com.example.focusdetox.data.AppListProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Global thread-safe memory cache
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    blockedPackages: Set<String>,
    onToggleApp: (packageName: String, isBlocked: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        installedApps = AppListProvider.getInstalledLauncherApps(context)
        isLoading = false
    }

    val filteredApps by remember {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isBlank()) {
                installedApps
            } else {
                installedApps.filter { it.name.contains(query, ignoreCase = true) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Select Apps to Block",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app_row" }
                    ) { app ->
                        AppPickerRow(
                            app = app,
                            isBlocked = blockedPackages.contains(app.packageName),
                            onToggle = { isChecked ->
                                onToggleApp(app.packageName, isChecked)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: AppInfo,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncAppIcon(
            packageName = app.packageName,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = isBlocked,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun AsyncAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Check cache instantly on main thread before launching any coroutines
    var iconBitmap by remember(packageName) { mutableStateOf(iconCache[packageName]) }

    if (iconBitmap == null) {
        LaunchedEffect(packageName) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    // Smaller 64x64 bitmap footprint reduces RAM usage during fast fling
                    val bitmap = drawable.toBitmap(width = 64, height = 64).asImageBitmap()
                    iconCache[packageName] = bitmap
                    iconBitmap = bitmap
                } catch (_: Exception) {
                    // Fallback to placeholder UI on error
                }
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}