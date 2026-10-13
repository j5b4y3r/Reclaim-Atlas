package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.SelfControlRepository
import com.example.service.receiver.PackageInstallReceiver
import com.example.ui.screens.InterventionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class SelfControlApp : Application() {

    companion object {
        lateinit var instance: SelfControlApp
            private set

        lateinit var repository: SelfControlRepository
            private set

        @Volatile
        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    private val packageInstallReceiver = PackageInstallReceiver()

    override fun onCreate() {
        super.onCreate()
        instance = this

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is InterventionActivity) {
                    currentActivityRef = WeakReference(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {
                if (activity !is InterventionActivity) {
                    currentActivityRef = WeakReference(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity !is InterventionActivity) {
                    currentActivityRef = WeakReference(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() === activity) {
                    currentActivityRef = null
                }
            }
        })

        val database = AppDatabase.getInstance(this)
        val preferences = PreferencesManager(this)
        repository = SelfControlRepository(this, database, preferences)

        val initAfterUnlock = {
            try {
                preferences.startDataStoreCollectionIfUnlocked()
                // Initialize 3-layer AdultBlockingEngine (loads 500k+ local DB + Room adult_domains)
                com.example.domain.engine.AdultBlockingEngine.initialize(this, database.adultDomainDao())

                // Ensure baseline installed packages are initialized if Block New App Installs is active
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        preferences.ensureNewAppsBaselineInitialized()
                        preferences.checkAndExpireUninstallProtection()
                        if (preferences.cachedMasterProtection &&
                            preferences.cachedVpnEnabled &&
                            !com.example.service.vpn.SelfControlVpnService.isRunning
                        ) {
                            if (android.net.VpnService.prepare(this@SelfControlApp) == null) {
                                com.example.service.vpn.SelfControlVpnService.startVpn(this@SelfControlApp)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        val userManager = getSystemService(USER_SERVICE) as? android.os.UserManager
        val isUserUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            userManager?.isUserUnlocked != false
        } else true

        if (isUserUnlocked) {
            initAfterUnlock()
        } else {
            // Device is in Direct Boot (before first unlock after restart); wait for user unlock
            try {
                val unlockReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                            initAfterUnlock()
                            try {
                                unregisterReceiver(this)
                            } catch (_: Throwable) {}
                        }
                    }
                }
                val unlockFilter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unlockReceiver, unlockFilter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(unlockReceiver, unlockFilter)
                }
            } catch (_: Throwable) {}
        }

        // Dynamically register PackageInstallReceiver so ACTION_PACKAGE_ADDED is never missed on Android 8+
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageInstallReceiver, filter, RECEIVER_EXPORTED)
            } else {
                ContextCompat.registerReceiver(
                    this,
                    packageInstallReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        } catch (_: Exception) {}

    }
}
