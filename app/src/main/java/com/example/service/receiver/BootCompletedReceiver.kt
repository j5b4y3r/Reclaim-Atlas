package com.example.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.SelfControlApp
import com.example.service.accessibility.SelfControlAccessibilityService
import com.example.service.vpn.SelfControlVpnService
import com.example.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SelfControlAccessibilityService.isDeviceAdminActiveCached =
                        PermissionHelper.isDeviceAdminActive(context)
                    val prefs = SelfControlApp.repository.preferences
                    prefs.startDataStoreCollectionIfUnlocked()
                    prefs.checkAndExpireUninstallProtection()

                    val masterProtection = try {
                        prefs.masterProtection.first()
                    } catch (_: Throwable) {
                        prefs.cachedMasterProtection
                    }
                    val vpnEnabled = try {
                        prefs.vpnEnabled.first()
                    } catch (_: Throwable) {
                        prefs.cachedVpnEnabled
                    }
                    val adultBlocked = prefs.cachedAdultContentBlocked
                    if (masterProtection && adultBlocked) {
                        com.example.util.PermissionHelper.tryApplyCleanBrowsingPrivateDns(context)
                    }
                } catch (e: Throwable) {
                    Log.w("BootCompletedReceiver", "Autostart on boot error: ${e.message}")
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
