# ===================================================================
# Focus Detox - Custom ProGuard / R8 Rules
# ===================================================================

# 1. Services & System Settings Interop (CRITICAL)
-keep class **.service.DetoxAccessibilityService { *; }
-keep class **.service.FocusTileService { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService { *; }
-keepclassmembers class * extends android.service.quicksettings.TileService { *; }

# 2. Data Models & DataStore Persistence
-keepclassmembers class com.example.focusdetox.data.** { *; }
-keepclassmembers class com.example.focusdetox.model.** { *; }

# 3. Preserve Stack Traces for Play Console Crash Reports
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 4. Jetpack Compose & Kotlin Coroutines
-dontwarn androidx.compose.**
-dontwarn androidx.datastore.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**