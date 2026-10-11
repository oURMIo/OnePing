package com.oneping.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oneping.service.ListenerService
import java.util.concurrent.ConcurrentHashMap

/** Permission checks and system-settings intents used by the setup screen. */
object Permissions {

    fun hasListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun listenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    @SuppressLint("BatteryLife") // Keeping the services alive is the core purpose of the app.
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Asks the system to re-bind the listener (helps after the process was killed). */
    fun requestRebind(context: Context) {
        NotificationListenerService.requestRebind(ComponentName(context, ListenerService::class.java))
    }
}

/** Cached app names / icons for package names shown in the UI and in silent copies. */
object AppLabels {
    private val labels = ConcurrentHashMap<String, String>()

    fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun icon(context: Context, pkg: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
}
