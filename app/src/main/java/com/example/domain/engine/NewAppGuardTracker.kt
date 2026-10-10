package com.example.domain.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time tracker for newly installed apps (from Google Play Store, OEM App Stores, or APK files)
 * while "Block New App Installs" is enabled. Works across all Android OS versions and device models.
 */
object NewAppGuardTracker {

    private val newlyInstalledMemorySet: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val baselineMemorySet: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    var blockedSinceMemory: Long = 0L

    fun markPackageNewlyInstalled(packageName: String) {
        if (packageName.isBlank()) return
        newlyInstalledMemorySet.add(packageName)
        baselineMemorySet.remove(packageName)
    }

    fun clearAll() {
        newlyInstalledMemorySet.clear()
        baselineMemorySet.clear()
        blockedSinceMemory = 0L
    }

    fun setBaseline(packages: Set<String>, sinceTimestamp: Long) {
        baselineMemorySet.clear()
        baselineMemorySet.addAll(packages)
        blockedSinceMemory = sinceTimestamp
    }

    /**
     * Collects all currently installed & enabled packages across all Android OS versions and OEMs.
     * Note: Disabled system stub apps are excluded from the baseline so if the user "installs"
     * a preloaded stub app (like Facebook/Instagram/TikTok/Games on Samsung/Xiaomi) from Play Store or APK,
     * it is properly detected as newly installed.
     */
    fun captureInstalledPackagesBaseline(context: Context): Set<String> {
        val pm = context.packageManager
        val pkgs = mutableSetOf<String>()

        try {
            val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            for (info in installedPkgs) {
                val pkgName = info.packageName ?: continue
                val appInfo = info.applicationInfo
                if (appInfo == null || appInfo.enabled) {
                    pkgs.add(pkgName)
                }
            }
        } catch (_: Throwable) {}

        try {
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (appInfo in installedApps) {
                if (appInfo.enabled) {
                    pkgs.add(appInfo.packageName)
                }
            }
        } catch (_: Throwable) {}

        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            }
            for (resolveInfo in list) {
                resolveInfo.activityInfo?.packageName?.let { pkgs.add(it) }
            }
        } catch (_: Throwable) {}

        // Never include anything already flagged as newly installed during the active block session
        pkgs.removeAll(newlyInstalledMemorySet)
        return pkgs
    }

    /**
     * Evaluates whether a package was newly installed (from Play Store or from an APK file)
     * after "Block New App Installs" was enabled.
     */
    fun isPackageNewlyInstalled(
        context: Context,
        packageName: String,
        persistedHiddenNewApps: Set<String>,
        persistedBlockedSince: Long,
        persistedBaseline: Set<String>
    ): Boolean {
        if (packageName.isBlank()) return false

        // 1. Instant check in memory or persisted newly-installed set
        if (newlyInstalledMemorySet.contains(packageName) || persistedHiddenNewApps.contains(packageName)) {
            newlyInstalledMemorySet.add(packageName)
            return true
        }

        val effectiveSince = if (persistedBlockedSince > 0L) persistedBlockedSince else blockedSinceMemory
        val effectiveBaseline = if (persistedBaseline.isNotEmpty()) persistedBaseline else baselineMemorySet

        val pm = context.packageManager

        // 2. Check PackageInfo install & update timestamps + baseline membership
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val appInfo = pkgInfo.applicationInfo
            val isPureSystemApp = appInfo != null &&
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            // 2a. Installed after block was enabled
            if (effectiveSince > 0L && pkgInfo.firstInstallTime >= effectiveSince && !isPureSystemApp) {
                newlyInstalledMemorySet.add(packageName)
                return true
            }

            // 2b. Not in baseline snapshot taken when block was enabled
            if (effectiveBaseline.isNotEmpty() && !effectiveBaseline.contains(packageName)) {
                val hasLaunchIntent = try { pm.getLaunchIntentForPackage(packageName) != null } catch (_: Throwable) { false }
                if (!isPureSystemApp || hasLaunchIntent || (effectiveSince > 0L && pkgInfo.lastUpdateTime >= effectiveSince)) {
                    newlyInstalledMemorySet.add(packageName)
                    return true
                }
            }
        } catch (_: Throwable) {
            // 3. Fallback if getPackageInfo throws on restricted OEM profiles: check baseline membership
            if (effectiveBaseline.isNotEmpty() && !effectiveBaseline.contains(packageName)) {
                newlyInstalledMemorySet.add(packageName)
                return true
            }
        }

        return false
    }
}
