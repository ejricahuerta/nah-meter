package dev.edmel.offerchecker.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.edmel.offerchecker.service.OfferAccessibilityService
import dev.edmel.offerchecker.service.UBER_PACKAGE

/** Checks for each setup step and the Android settings pages that fix them. */
object SystemSetup {
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, OfferAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notificationsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun uberInstalled(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(UBER_PACKAGE, 0) }.isSuccess

    val brand: String get() = Build.MANUFACTURER.lowercase()
    val isSamsung get() = brand.contains("samsung")
    val isXiaomi get() = brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")

    fun openAccessibility(ctx: Context) = start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** App info page, where Android 13+ shows ⋮ → Allow restricted settings. */
    fun openAppInfo(ctx: Context) = start(
        ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun openNotificationSettings(ctx: Context) = start(
        ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
    )

    @SuppressLint("BatteryLife")
    fun requestUnrestrictedBattery(ctx: Context) {
        val ok = start(
            ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        )
        if (!ok) openAppInfo(ctx)
    }

    /** Samsung "Never sleeping apps" / Xiaomi Autostart + battery. Falls back to app info. */
    fun openBrandBattery(ctx: Context) {
        val candidates = when {
            isSamsung -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ).map { Intent().setComponent(it) }
            isXiaomi -> listOf(
                Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ),
                Intent().setComponent(
                    ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                ).putExtra("package_name", ctx.packageName).putExtra("package_label", "Offer Checker"),
            )
            else -> emptyList()
        }
        if (candidates.none { start(ctx, it) }) openAppInfo(ctx)
    }

    fun openUber(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(UBER_PACKAGE)?.let { start(ctx, it) }
    }

    private fun start(ctx: Context, intent: Intent): Boolean = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
