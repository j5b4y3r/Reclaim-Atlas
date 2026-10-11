package com.example.service.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.util.PermissionHelper

class SelfControlDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        try {
            com.example.service.accessibility.SelfControlAccessibilityService.isDeviceAdminActiveCached = true
            Toast.makeText(context, "Uninstall Protection Activated", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Uninstall protection is active to help you maintain digital self-control. Are you sure you want to deactivate it?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin was disabled!")
        try {
            com.example.service.accessibility.SelfControlAccessibilityService.isDeviceAdminActiveCached = false
        } catch (_: Exception) {}

        // Check if uninstall protection is still supposed to be active.
        // If yes, the admin was revoked by the system (Samsung battery saver,
        // process death on swipe-from-recents) — re-prompt the user.
        try {
            val syncPrefs = context.getSharedPreferences("selfcontrol_sync_prefs", Context.MODE_PRIVATE)
            val isProtectionEnabled = syncPrefs.getBoolean("uninstall_protection", false)
            val protectionUntil = syncPrefs.getLong("uninstall_protection_until", 0L)
            val isStillValid = isProtectionEnabled && (protectionUntil == 0L || protectionUntil > System.currentTimeMillis())

            if (isStillValid) {
                Log.w(TAG, "Uninstall protection is still active but admin was revoked — re-prompting")
                // Re-open the Device Admin activation prompt
                val reactivateIntent = PermissionHelper.getDeviceAdminIntent(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(reactivateIntent)
            } else {
                try {
                    Toast.makeText(context, "Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking protection state on admin disable", e)
            try {
                Toast.makeText(context, "Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }
}
