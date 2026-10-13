package com.example.service.receiver

import android.accessibilityservice.AccessibilityService
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.example.SelfControlApp
import com.example.service.accessibility.SelfControlAccessibilityService
import com.example.ui.screens.InterventionActivity
import com.example.util.PermissionHelper

class SelfControlDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        @Volatile
        var lastAdminEnabledTimeMs: Long = 0L

        fun isRecentlyEnabled(windowMs: Long = 4000L): Boolean {
            val elapsed = SystemClock.uptimeMillis() - lastAdminEnabledTimeMs
            return lastAdminEnabledTimeMs > 0L && elapsed in 0L..windowMs
        }

        fun isProtectionCurrentlyActive(context: Context): Boolean {
            if (PermissionHelper.isRemovingAdminFromApp()) return false
            try {
                val repo = SelfControlApp.repository
                val prefs = repo.preferences
                if (prefs.isTemporaryUnlockActiveFast()) return false
                if (prefs.isUninstallProtectionActiveFast()) return true
                if (prefs.cachedAccountabilityEnabled || repo.isFocusModeActiveFast()) return true
            } catch (_: Throwable) {}

            try {
                val cachePrefs = context.getSharedPreferences("block_screen_sync_cache", Context.MODE_PRIVATE)
                val enabled = cachePrefs.getBoolean("uninstall_protection", false)
                val until = cachePrefs.getLong("uninstall_protection_until", 0L)
                if (enabled && (until == 0L || until > System.currentTimeMillis())) {
                    return true
                }
            } catch (_: Throwable) {}

            try {
                val legacyPrefs = context.getSharedPreferences("selfcontrol_sync_prefs", Context.MODE_PRIVATE)
                val enabled = legacyPrefs.getBoolean("uninstall_protection", false)
                val until = legacyPrefs.getLong("uninstall_protection_until", 0L)
                if (enabled && (until == 0L || until > System.currentTimeMillis())) {
                    return true
                }
            } catch (_: Throwable) {}

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val dpPrefs = context.createDeviceProtectedStorageContext()
                        .getSharedPreferences("block_screen_sync_cache", Context.MODE_PRIVATE)
                    val enabled = dpPrefs.getBoolean("uninstall_protection", false)
                    val until = dpPrefs.getLong("uninstall_protection_until", 0L)
                    if (enabled && (until == 0L || until > System.currentTimeMillis())) {
                        return true
                    }
                }
            } catch (_: Throwable) {}

            return false
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        lastAdminEnabledTimeMs = SystemClock.uptimeMillis()
        try {
            SelfControlAccessibilityService.isDeviceAdminActiveCached = true
            Toast.makeText(context, "Uninstall Protection Activated", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device admin disable requested!")
        if (!PermissionHelper.isRemovingAdminFromApp() &&
            !com.example.MainActivity.isForeground &&
            !com.example.MainActivity.isUninstallPopupVisible &&
            isProtectionCurrentlyActive(context)
        ) {
            val appCtx = context.applicationContext
            val mainHandler = Handler(Looper.getMainLooper())

            // Immediately dismiss the system confirmation dialog / settings page via AccessibilityService
            try {
                SelfControlAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (_: Throwable) {}

            // Launch the Block Screen immediately and reinforce after dialog transition settles
            val launchBlockScreen = Runnable {
                if (!InterventionActivity.isShowing &&
                    !com.example.MainActivity.isForeground &&
                    !com.example.MainActivity.isUninstallPopupVisible
                ) {
                    try {
                        SelfControlAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    } catch (_: Throwable) {}
                    try {
                        val launchCtx = SelfControlAccessibilityService.instance ?: appCtx
                        InterventionActivity.start(
                            context = launchCtx,
                            target = "Device Admin Protected",
                            reason = "Deactivating Device Administrator is blocked while Prevent Uninstall or Accountability Lock is active.",
                            packageName = "com.android.settings",
                            bypassCooldown = true
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to launch InterventionActivity on disable request", e)
                    }
                }
            }

            mainHandler.post(launchBlockScreen)
            mainHandler.postDelayed(launchBlockScreen, 120L)
            mainHandler.postDelayed(launchBlockScreen, 320L)
        }
        return "Uninstall protection is active to help you maintain digital self-control. Deactivating Device Admin is locked while Prevent Uninstall is enabled."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin was disabled!")
        try {
            SelfControlAccessibilityService.isDeviceAdminActiveCached = false
        } catch (_: Exception) {}

        try {
            if (!PermissionHelper.isRemovingAdminFromApp() &&
                !com.example.MainActivity.isForeground &&
                !com.example.MainActivity.isUninstallPopupVisible &&
                isProtectionCurrentlyActive(context)
            ) {
                Log.w(TAG, "Uninstall protection is still active but admin was revoked — blocking and re-prompting")
                val appCtx = context.applicationContext
                try {
                    InterventionActivity.start(
                        context = appCtx,
                        target = "Device Admin Protected",
                        reason = "Deactivating Device Administrator is blocked while Prevent Uninstall is active.",
                        packageName = "com.android.settings",
                        bypassCooldown = true
                    )
                } catch (_: Throwable) {}

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val reactivateIntent = PermissionHelper.getDeviceAdminIntent(appCtx).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        appCtx.startActivity(reactivateIntent)
                    } catch (_: Throwable) {}
                }, 400L)
            } else {
                try {
                    Toast.makeText(context, "Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking protection state on admin disable", e)
        }
    }
}
