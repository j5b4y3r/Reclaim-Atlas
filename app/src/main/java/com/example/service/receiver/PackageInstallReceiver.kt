package com.example.service.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SelfControlApp
import com.example.data.local.DefaultBlocklistData
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleType
import com.example.domain.engine.NewAppGuardTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName == context.packageName || packageName == "com.example" || packageName == "com.healinglab.reclaim.app") return

            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (action == Intent.ACTION_PACKAGE_ADDED && !isReplacing) {
                // Immediately mark in memory with 0ms latency so tapping "Open" in Play Store or Package Installer is blocked on frame 1
                NewAppGuardTracker.markPackageNewlyInstalled(packageName)
            }

            CoroutineScope(Dispatchers.IO).launch {
                val prefs = SelfControlApp.repository.preferences
                val newAppsBlocked = prefs.newAppsBlocked.first()
                val baseline = prefs.baselineInstalledPackages.first()

                // If it's a fresh install, or a replacement of a package that wasn't in our pre-block baseline
                if (!isReplacing || (baseline.isNotEmpty() && !baseline.contains(packageName))) {
                    NewAppGuardTracker.markPackageNewlyInstalled(packageName)
                    prefs.addHiddenNewlyInstalledPackage(packageName)

                    if (newAppsBlocked) {
                        showInstallProtectedNotification(context, packageName, "New App Guard")
                    }
                }
            }
        }
    }

    private fun showInstallProtectedNotification(context: Context, packageName: String, categoryName: String) {
        val channelId = "install_guard_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Installation Protection Guard",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("App Guarded: $categoryName")
            .setContentText("Added $packageName to protection rules. Review in Digital Self-Control.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(packageName.hashCode(), notification)
    }
}
