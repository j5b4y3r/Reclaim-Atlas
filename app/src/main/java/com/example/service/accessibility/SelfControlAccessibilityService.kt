package com.example.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.example.SelfControlApp
import com.example.data.local.DefaultBlocklistData
import com.example.data.model.BlockCategory
import com.example.data.model.RuleType
import com.example.domain.engine.AccountabilityHelper
import com.example.domain.engine.RuleEngine
import com.example.service.receiver.PackageInstallReceiver
import com.example.ui.screens.InterventionActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SelfControlAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isDeviceAdminActiveCached = false

        @Volatile
        var shortsInterventionsCount = 0
            private set

        private var lastInterventionTime = 0L
        private const val INTERVENTION_THROTTLE_MS = 2500L

        private val SHORTS_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.vanced.android.youtube",
            "org.schabi.newpipe",
            "org.schabi.newpipe.debug",
            "org.polymorphicshade.newpipe",
            "com.github.libretube",
            "com.pure_tubers.video_music_stream",
            "free.tube.premium.advanced.tuber",
            "com.instagram.android",
            "com.instagram.lite",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.trill"
        )

        private val BROWSER_PACKAGES = DefaultBlocklistData.BROWSER_PACKAGES

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.settings.intelligence",
            "com.samsung.android.settings",
            "com.samsung.accessibility",
            "com.samsung.android.lool",
            "com.samsung.android.sm_cn",
            "com.sec.android.app.controlpanel",
            "com.samsung.android.app.settings.bixby",
            "com.samsung.android.app.telephonyui",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oppo.packageinstaller",
            "com.vivo.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.miui.cleanmaster",
            "com.coloros.safecenter",
            "com.coloros.settings",
            "com.coloros.securitypermission",
            "com.oppo.safecenter",
            "com.oplus.safecenter",
            "com.oplus.securitypermission",
            "com.vivo.permissionmanager",
            "com.vivo.abe",
            "com.iqoo.secure",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.oneplus.security",
            "com.transsion.phonemaster"
        )

        private fun isSettingsOrAccessibilityPkg(packageName: String): Boolean {
            return SETTINGS_PACKAGES.contains(packageName) ||
                    packageName == "android" ||
                    packageName.contains("accessibility", ignoreCase = true) ||
                    packageName.contains("settings", ignoreCase = true) ||
                    packageName.contains("packageinstaller", ignoreCase = true) ||
                    packageName.contains("permissioncontroller", ignoreCase = true) ||
                    packageName.contains("deviceadmin", ignoreCase = true) ||
                    packageName.contains("devicepolicy", ignoreCase = true) ||
                    packageName.contains("securitycenter", ignoreCase = true) ||
                    packageName.contains("safecenter", ignoreCase = true) ||
                    packageName.contains("knox", ignoreCase = true) ||
                    packageName.contains("security", ignoreCase = true) ||
                    packageName.contains("admin", ignoreCase = true) ||
                    packageName.contains("policy", ignoreCase = true) ||
                    packageName.contains("file", ignoreCase = true) ||
                    packageName.contains("myfiles", ignoreCase = true) ||
                    packageName.contains("documentsui", ignoreCase = true) ||
                    packageName.contains("explorer", ignoreCase = true) ||
                    packageName == "com.android.vending" ||
                    packageName == "com.sec.android.app.samsungapps"
        }

        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oppo.packageinstaller",
            "com.vivo.packageinstaller",
            "com.huawei.appmarket",
            "com.apkpure.aegon",
            "com.aurora.store"
        )

        @Volatile
        var lastBlockedPackage: String? = null
            private set

        @Volatile
        var instance: SelfControlAccessibilityService? = null
            private set

        @Volatile
        private var temporarilyIgnoredPackage: String? = null
        @Volatile
        private var ignoreUntilTime = 0L

        fun ignorePackageTemporarily(packageName: String?, durationMs: Long = 250L) {
            if (packageName == null) return
            temporarilyIgnoredPackage = packageName
            ignoreUntilTime = SystemClock.uptimeMillis() + durationMs.coerceAtMost(500L)
        }

        fun onInterventionDismissed(packageName: String?) {
            if (packageName != null) {
                temporarilyIgnoredPackage = packageName
                ignoreUntilTime = SystemClock.uptimeMillis() + 250L
            }
        }

        fun clearBlockedState() {
            val now = SystemClock.uptimeMillis()
            if (now < ignoreUntilTime) {
                return
            }
            lastBlockedPackage = null
            lastInterventionTime = 0L
            temporarilyIgnoredPackage = null
            ignoreUntilTime = 0L
        }

        /**
         * Safely escapes from the blocked sub-feature (e.g. YouTube Shorts, Reels, blocked tab)
         * by clicking the Home tab or performing back actions to navigate away from the blocked view.
         */
        fun resetAppToSafeState(packageName: String?) {
            val service = instance ?: return
            service.navigateCurrentAppToSafeView(packageName)
        }
    }

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AccessibilityService", "Unhandled error in serviceScope: ${throwable.message}", throwable)
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + serviceExceptionHandler)
    private var maintenanceJob: Job? = null
    private var packageInstallReceiver: PackageInstallReceiver? = null
    private val browserPackageCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var lastEvaluatedPackage: String? = null
    @Volatile
    private var lastEvaluatedTime: Long = 0L
    @Volatile
    private var lastSettingsContentCheckTime: Long = 0L

    private fun isOwnAppPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return pkg == applicationContext.packageName ||
            pkg.startsWith("com.healinglab.reclaim") ||
            pkg.contains("copsli") ||
            pkg == "com.example"
    }

    private fun isReclaimInForegroundOrPopup(): Boolean {
        if (com.example.MainActivity.isForeground || com.example.MainActivity.isUninstallPopupVisible) {
            return true
        }
        try {
            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (isOwnAppPackage(activePkg)) return true
        } catch (_: Throwable) {}
        return false
    }

    private fun isReclaimConfigPopupText(lowerText: String): Boolean {
        return lowerText.contains("activate uninstall protection") ||
            lowerText.contains("configure lock timer") ||
            lowerText.contains("select how long this commitment stays active") ||
            lowerText.contains("please enable the system protection permission on the next screen") ||
            lowerText.contains("how uninstall protection works") ||
            lowerText.contains("lock down protection against impulsive bypasses") ||
            lowerText.contains("enforces android device administrator") ||
            lowerText.contains("device administrator permission is active. tamper protection will lock") ||
            lowerText.contains("device admin permission will be requested first") ||
            lowerText.contains("once activated, uninstall protection is locked") ||
            lowerText.contains("active lock until:")
    }

    private fun getSettingsWindowRoots(eventSource: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (isReclaimInForegroundOrPopup()) return emptyList()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            rootInActiveWindow?.let { activeRoot ->
                val pkg = activeRoot.packageName?.toString()
                if (!pkg.isNullOrBlank() && !isOwnAppPackage(pkg) && pkg != "com.android.systemui" && isSettingsOrAccessibilityPkg(pkg)) {
                    roots.add(activeRoot)
                }
            }
        } catch (_: Throwable) {}

        try {
            windows?.forEach { win ->
                val winRoot = try { win.root } catch (_: Throwable) { null } ?: return@forEach
                val winPkg = try { winRoot.packageName?.toString() } catch (_: Throwable) { null }
                if (!winPkg.isNullOrBlank() && !isOwnAppPackage(winPkg) && winPkg != "com.android.systemui" && isSettingsOrAccessibilityPkg(winPkg)) {
                    if (!roots.contains(winRoot)) {
                        roots.add(winRoot)
                    }
                }
            }
        } catch (_: Throwable) {}

        if (eventSource != null && !isOwnAppPackage(eventSource.packageName?.toString())) {
            var topSource = eventSource
            try {
                var p = topSource.parent
                var depth = 0
                while (p != null && depth < 25) {
                    topSource = p
                    p = topSource.parent
                    depth++
                }
            } catch (_: Throwable) {}
            val topPkg = try { topSource.packageName?.toString() } catch (_: Throwable) { null }
            if (!topPkg.isNullOrBlank() && !isOwnAppPackage(topPkg) && !roots.contains(topSource)) {
                roots.add(topSource)
            }
        }
        return roots
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this

        serviceScope.launch {
            try {
                isDeviceAdminActiveCached = com.example.util.PermissionHelper.isDeviceAdminActive(this@SelfControlAccessibilityService)
                val prefs = SelfControlApp.repository.preferences
                prefs.startDataStoreCollectionIfUnlocked()
                prefs.ensureNewAppsBaselineInitialized()
                if (prefs.cachedMasterProtection && prefs.cachedAdultContentBlocked) {
                    com.example.util.PermissionHelper.tryApplyCleanBrowsingPrivateDns(this@SelfControlAccessibilityService)
                }
            } catch (_: Throwable) {}
        }

        // Dynamically register PackageInstallReceiver so newly installed apps are caught in real time
        try {
            if (packageInstallReceiver == null) {
                val receiver = PackageInstallReceiver()
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                packageInstallReceiver = receiver
            }
        } catch (e: Throwable) {
            Log.w("AccessibilityService", "Could not register PackageInstallReceiver in service", e)
        }

        // Lightweight maintenance job every 5 minutes to expire uninstall protection and keep VPN alive
        maintenanceJob?.cancel()
        maintenanceJob = serviceScope.launch {
            while (isActive) {
                try {
                    val prefs = SelfControlApp.repository.preferences
                    prefs.checkAndExpireUninstallProtection()
                    if (prefs.cachedMasterProtection && prefs.cachedAdultContentBlocked) {
                        com.example.util.PermissionHelper.tryApplyCleanBrowsingPrivateDns(this@SelfControlAccessibilityService)
                    }
                } catch (_: Throwable) {}
                delay(300_000L) // 5 minutes
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        // Ignore events from our own app or whenever Reclaim's UI / popup is in the foreground
        if (isOwnAppPackage(packageName) || isReclaimInForegroundOrPopup()) {
            return
        }

        // Capture all event fields synchronously on the main thread BEFORE onAccessibilityEvent returns
        // (Android recycles AccessibilityEvent immediately after onAccessibilityEvent returns!)
        val eventType = event.eventType
        val eventClassName = try { event.className?.toString()?.lowercase() ?: "" } catch (_: Throwable) { "" }
        val eventTexts = try {
            event.text?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        val eventDesc = try { event.contentDescription?.toString()?.trim() ?: "" } catch (_: Throwable) { "" }
        val eventSource = try { event.source } catch (_: Throwable) { null }

        val repo = SelfControlApp.repository
        val prefs = repo.preferences
        val isUninstallProtection = prefs.isUninstallProtectionActiveFast()
        val isAccEnabled = prefs.cachedAccountabilityEnabled
        val isFocusMode = repo.isFocusModeActiveFast()
        val isAccountabilityLocked = isAccEnabled || isFocusMode
        val canEnforceTamper = (isUninstallProtection || isAccountabilityLocked) &&
            !prefs.isTemporaryUnlockActiveFast()

        // Intercept Developer Mode countdown / activation Toasts (delivered via TYPE_NOTIFICATION_STATE_CHANGED)
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val toastText = (eventTexts.joinToString(" ") + " " + eventDesc).lowercase()
            val isDevToast = toastText.contains("steps away from being a developer") ||
                toastText.contains("step away from being a developer") ||
                toastText.contains("you are now a developer") ||
                toastText.contains("you're now a developer") ||
                toastText.contains("already a developer") ||
                toastText.contains("developer mode has been") ||
                toastText.contains("developer options are enabled") ||
                toastText.contains("ডেভেলপার")
            if (isDevToast && canEnforceTamper) {
                Log.d("AccessibilityService", "TOAST-BLOCKED: Developer Mode toast detected: $toastText")
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}
                triggerInterventionWithReinforcement(
                    target = "Developer Options Blocked",
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = "Turning on Developer Mode by tapping Build Number is blocked while Prevent Uninstall or Accountability Lock is active.",
                    appPackage = "com.android.settings"
                )
                return
            }
        }

        // Whenever the user returns to Home Launcher or SystemUI (Recents / Status Bar), check active window
        if (packageName == "com.android.systemui" || getAllHomeLauncherPackages().contains(packageName)) {
            val activeRoot = try { rootInActiveWindow } catch (_: Throwable) { null }
            val activeRootPkg = activeRoot?.packageName?.toString() ?: ""

            // Never inspect if our own app is the active window
            if (isOwnAppPackage(activeRootPkg)) {
                return
            }

            // If SystemUI emitted a status/navigation bar update while Settings is on screen, enforce Settings tamper rules!
            if (activeRoot != null && isSettingsOrAccessibilityPkg(activeRootPkg) && canEnforceTamper) {
                val nowMs = SystemClock.uptimeMillis()
                if (nowMs - lastSettingsContentCheckTime >= 80L) {
                    lastSettingsContentCheckTime = nowMs
                    if (enforceSettingsTamperFast(
                            packageName = activeRootPkg,
                            eventClassName = "",
                            eventTexts = emptyList(),
                            eventDesc = "",
                            eventSource = activeRoot,
                            isUninstallProtection = isUninstallProtection,
                            isAccountabilityLocked = isAccountabilityLocked
                        )
                    ) {
                        return
                    }
                }
                return
            }

            if (isUninstallProtection) {
                val root = activeRoot ?: eventSource
                if (root != null && !isOwnAppPackage(root.packageName?.toString())) {
                    val quickText = collectQuickWindowText(root).lowercase()
                    if (quickText.contains("uninstall") && (quickText.contains("reclaim") || quickText.contains("healinglab") || quickText.contains("copsli"))) {
                        serviceScope.launch {
                            inspectNodeForUninstall(root, packageName, isUninstallProtection = true, isDeviceAdmin = true)
                        }
                        return
                    }
                }
            }
            clearBlockedState()
            return
        }

        val now = SystemClock.uptimeMillis()
        val isSettings = isSettingsOrAccessibilityPkg(packageName)
        if (!isSettings && InterventionActivity.isInDismissCooldown(packageName)) {
            return
        }

        // --- SYNCHRONOUS 0MS MAIN-THREAD FAST-PATH FOR SETTINGS TAMPERING (SAMSUNG ONE UI / ANDROID 13) ---
        if (isSettings && canEnforceTamper) {
            val isImmediateEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED

            if (isImmediateEvent || (now - lastSettingsContentCheckTime >= 80L)) {
                lastSettingsContentCheckTime = now
                if (enforceSettingsTamperFast(
                        packageName = packageName,
                        eventClassName = eventClassName,
                        eventTexts = eventTexts,
                        eventDesc = eventDesc,
                        eventSource = eventSource,
                        isUninstallProtection = isUninstallProtection,
                        isAccountabilityLocked = isAccountabilityLocked
                    )
                ) {
                    return
                }
            }

            // Schedule settled window checks at 100ms, 240ms, and 420ms for Samsung One UI SubSettings / RecyclerView delayed inflation
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            ) {
                val capturedPkg = packageName
                serviceScope.launch(Dispatchers.Main) {
                    for (delayMs in listOf(100L, 240L, 420L)) {
                        delay(delayMs)
                        if (InterventionActivity.isShowing || isReclaimInForegroundOrPopup()) return@launch
                        val curPrefs = SelfControlApp.repository.preferences
                        val curUninstall = curPrefs.isUninstallProtectionActiveFast()
                        val curAccLocked = curPrefs.cachedAccountabilityEnabled || SelfControlApp.repository.isFocusModeActiveFast()
                        if ((!curUninstall && !curAccLocked) || curPrefs.isTemporaryUnlockActiveFast()) return@launch
                        val settledRoot = try { rootInActiveWindow } catch (_: Throwable) { null }
                        val settledPkg = settledRoot?.packageName?.toString() ?: capturedPkg
                        if (isOwnAppPackage(settledPkg) || isReclaimInForegroundOrPopup()) return@launch
                        if (!isSettingsOrAccessibilityPkg(settledPkg)) continue
                        if (enforceSettingsTamperFast(
                                packageName = settledPkg,
                                eventClassName = "",
                                eventTexts = emptyList(),
                                eventDesc = "",
                                eventSource = settledRoot,
                                isUninstallProtection = curUninstall,
                                isAccountabilityLocked = curAccLocked
                            )
                        ) {
                            return@launch
                        }
                    }
                }
            }
        }

        // Debounce rapid content/scroll events on the same package to eliminate IPC binder thrashing
        val debounceWindowMs = if (isSettings) 80L else 140L
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SELECTED &&
            packageName == lastEvaluatedPackage &&
            (now - lastEvaluatedTime < debounceWindowMs)
        ) {
            return
        }
        lastEvaluatedPackage = packageName
        lastEvaluatedTime = now

        // When the user opens or switches to an app window, clear any temporary ignore window so detection is 100% instant (0ms delay)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isCoreSystemOrLauncherPackage(packageName)) {
            temporarilyIgnoredPackage = null
            ignoreUntilTime = 0L
        }

        serviceScope.launch {
            try {
                if (isOwnAppPackage(packageName) || isReclaimInForegroundOrPopup()) return@launch

                // Fast-path: Catch direct taps on "Build number", "Device admin", "Images", "Videos", "Short videos", etc.
                if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
                ) {
                    if (handleDirectClickOrSelectEvent(packageName, eventSource, eventTexts, eventDesc)) {
                        return@launch
                    }
                }

                checkAndEnforce(packageName, eventSource)

                // For window transitions, run a follow-up check after views settle
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    delay(160L)
                    if (!isReclaimInForegroundOrPopup() && (!InterventionActivity.isShowing || packageName == lastBlockedPackage || isSettings)) {
                        checkAndEnforce(packageName, null)
                    }
                }
            } catch (t: Throwable) {
                Log.e("AccessibilityService", "Error during checkAndEnforce for $packageName: ${t.message}", t)
            }
        }
    }

    /**
     * Ultra-fast synchronous Settings tamper detector (runs in <2ms on Main thread).
     * Blocks Reclaim's Device Admin permission page and Software Information / Build Number / Developer Options
     * while NEVER touching Reclaim's own UI.
     */
    private fun enforceSettingsTamperFast(
        packageName: String,
        eventClassName: String,
        eventTexts: List<String>,
        eventDesc: String,
        eventSource: AccessibilityNodeInfo?,
        isUninstallProtection: Boolean,
        isAccountabilityLocked: Boolean
    ): Boolean {
        if (isOwnAppPackage(packageName) || isReclaimInForegroundOrPopup()) return false

        val activeRoot = try { rootInActiveWindow } catch (_: Throwable) { null }
        val activeRootPkg = activeRoot?.packageName?.toString()
        if (isOwnAppPackage(activeRootPkg)) return false
        if (eventSource != null && isOwnAppPackage(eventSource.packageName?.toString())) return false

        val allWindowTitles = try {
            windows?.mapNotNull { w ->
                val wPkg = try { w.root?.packageName?.toString() } catch (_: Throwable) { null }
                if (isOwnAppPackage(wPkg)) null else w?.title?.toString()?.trim()?.lowercase()
            } ?: emptyList()
        } catch (_: Throwable) { emptyList() }
        val windowTitleText = allWindowTitles.joinToString(" ")

        val textSb = StringBuilder()
        val viewIdsSb = StringBuilder()
        var cancelNode: AccessibilityNodeInfo? = null

        val rootsToScan = getSettingsWindowRoots(eventSource)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        rootsToScan.forEach { queue.add(it) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            val t = try { n.text?.toString()?.trim() ?: "" } catch (_: Throwable) { "" }
            val d = try { n.contentDescription?.toString()?.trim() ?: "" } catch (_: Throwable) { "" }
            val vId = try { n.viewIdResourceName ?: "" } catch (_: Throwable) { "" }
            if (t.isNotBlank()) textSb.append(" ").append(t)
            if (d.isNotBlank()) textSb.append(" ").append(d)
            if (vId.isNotBlank()) viewIdsSb.append(" ").append(vId)

            if (cancelNode == null && (
                t.equals("Cancel", ignoreCase = true) ||
                d.equals("Cancel", ignoreCase = true) ||
                vId.endsWith("button2") ||
                vId.contains("cancel_button") ||
                vId.contains("negative_button")
            )) {
                cancelNode = n
            }

            val childCount = try { n.childCount } catch (_: Throwable) { 0 }
            for (i in 0 until childCount) {
                try {
                    n.getChild(i)?.let { queue.add(it) }
                } catch (_: Throwable) {}
            }
        }

        val allText = (eventTexts.joinToString(" ") + " " + eventDesc + " " + textSb.toString() + " " + windowTitleText).lowercase()
        if (isReclaimConfigPopupText(allText)) {
            return false
        }
        val allViewIds = viewIdsSb.toString().lowercase()
        val className = eventClassName.lowercase()

        val isRealAdmin = com.example.util.PermissionHelper.isDeviceAdminActive(this)
        val isInitialActivationPrompt = !isRealAdmin ||
            (!isUninstallProtection && com.example.util.PermissionHelper.isActivatingAdminFromApp()) ||
            com.example.service.receiver.SelfControlDeviceAdminReceiver.isRecentlyEnabled(4000L)

        // 1. Reclaim's Device Admin permission page (DeviceAdminAdd) or Device Admin Apps page
        val hasAdminDetailViewIds = allViewIds.contains(":id/admin_name") ||
            allViewIds.contains(":id/admin_warning") ||
            allViewIds.contains(":id/admin_policies") ||
            allViewIds.contains(":id/admin_description") ||
            allViewIds.contains(":id/restricted_action")

        val isReclaimAdminPage = !isInitialActivationPrompt && (
            className.contains("deviceadminadd") ||
            className.contains("deviceadmin") ||
            className.contains("device_admin") ||
            className.contains("devicepolicy") ||
            (hasAdminDetailViewIds && (allText.contains("reclaim") || allText.contains("deactivate") || allText.contains("নিষ্ক্রিয়"))) ||
            allText.contains("this admin app is active") ||
            allText.contains("allows reclaim to perform") ||
            allText.contains("admin app is active") ||
            allText.contains("deactivate this device admin") ||
            allText.contains("deactivate device admin") ||
            allText.contains("deactivate this admin app") ||
            allText.contains("prevents uninstallation of reclaim") ||
            allText.contains("prevents impulsive uninstallation") ||
            allText.contains("maintain digital self-control") ||
            (allText.contains("reclaim") && (
                allText.contains("deactivate") ||
                allText.contains("নিষ্ক্রিয়") ||
                allText.contains("device admin") ||
                allText.contains("device administrator") ||
                allText.contains("ডিভাইস অ্যাডমিন") ||
                allText.contains("ডিভাইস ম্যানেজার") ||
                (hasAdminDetailViewIds && allText.contains("cancel"))
            )) ||
            ((allText.contains("device admin apps") || allText.contains("device admin app") || allText.contains("device administrators") || allText.contains("ডিভাইস অ্যাডমিন")) &&
                allText.contains("reclaim")) ||
            allWindowTitles.any { t ->
                (t.contains("device admin") || t.contains("device administrator") || t.contains("ডিভাইস অ্যাডমিন")) &&
                    (allText.contains("reclaim") || allText.contains("deactivate") || hasAdminDetailViewIds)
            }
        )

        if (isReclaimAdminPage) {
            Log.d("AccessibilityService", "FAST-BLOCKED: Reclaim Device Admin page. className=$className")
            try {
                var clickable = cancelNode
                while (clickable != null && !clickable.safeIsClickable()) {
                    clickable = clickable.parent
                }
                clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Throwable) {}
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (_: Throwable) {}
            triggerInterventionWithReinforcement(
                target = "Device Admin Protected",
                ruleType = RuleType.APP,
                category = BlockCategory.CUSTOM,
                reason = "Deactivating Device Administrator is blocked while Prevent Uninstall or Accountability Lock is active.",
                appPackage = packageName
            )
            return true
        }

        // 2. Software Information / Build Number / Developer Options page
        val isSoftwareInfoPage =
            className.contains("softwareinfo") ||
            className.contains("developmentsettings") ||
            className.contains("softwareinformation") ||
            className.contains("buildnumber") ||
            allViewIds.contains("software_info") ||
            allViewIds.contains("software_information") ||
            allViewIds.contains("build_number") ||
            allViewIds.contains("development_settings") ||
            allViewIds.contains("developer_options") ||
            allText.contains("software information") ||
            allText.contains("software info") ||
            allText.contains("developer options") ||
            allText.contains("developer settings") ||
            allText.contains("development settings") ||
            allText.contains("build number") ||
            allText.contains("steps away from being a developer") ||
            allText.contains("step away from being a developer") ||
            allText.contains("you are now a developer") ||
            allText.contains("already a developer") ||
            allText.contains("বিল্ড নম্বর") ||
            allText.contains("সফটওয়্যার তথ্য") ||
            allText.contains("সফটওয়্যার ইনফো") ||
            allText.contains("ডেভেলপার অপশন") ||
            allText.contains("one ui version") ||
            allText.contains("kernel version") ||
            allText.contains("baseband version") ||
            allText.contains("knox version") ||
            allText.contains("se for android status") ||
            allText.contains("security software version") ||
            allWindowTitles.any { t ->
                t == "software information" || t == "software info" ||
                t == "developer options" || t == "developer settings" ||
                t.contains("software information") || t.contains("software info") ||
                t.contains("developer option") || t.contains("developer setting") ||
                t.contains("সফটওয়্যার") || t.contains("ডেভেলপার")
            }

        if (isSoftwareInfoPage) {
            Log.d("AccessibilityService", "FAST-BLOCKED: Software Info / Build Number / Developer Options. className=$className")
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (_: Throwable) {}
            triggerInterventionWithReinforcement(
                target = "Developer Options Blocked",
                ruleType = RuleType.APP,
                category = BlockCategory.CUSTOM,
                reason = "Accessing Software Information, Build Number, or Developer Options is blocked while Prevent Uninstall or Accountability Lock is active.",
                appPackage = packageName
            )
            return true
        }

        return false
    }

    private suspend fun handleDirectClickOrSelectEvent(
        packageName: String,
        eventSource: AccessibilityNodeInfo?,
        eventTexts: List<String>,
        eventDesc: String
    ): Boolean {
        val prefs = SelfControlApp.repository.preferences
        if (prefs.isTemporaryUnlockActiveFast()) return false

        val combinedLabels = mutableListOf<String>()
        combinedLabels.addAll(eventTexts)
        if (eventDesc.isNotBlank()) combinedLabels.add(eventDesc)
        if (eventSource != null) {
            eventSource.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { combinedLabels.add(it) }
            eventSource.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { combinedLabels.add(it) }
            val childTxt = collectDirectChildrenText(eventSource)
            if (childTxt.isNotBlank()) combinedLabels.add(childTxt)
        }

        // 1B. If Prevent Uninstall is enabled and user tries to tap "Uninstall" or "Update" in Play Store or Galaxy Store on Reclaim
        if ((packageName == "com.android.vending" || packageName == "com.sec.android.app.samsungapps") &&
            prefs.cachedUninstallProtection
        ) {
            val isClickingUninstall = combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                l == "uninstall" || l.startsWith("uninstall ")
            }
            val isClickingUpdate = combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                l == "update" || l == "install" || l == "enable" ||
                l.startsWith("update ") || l.startsWith("install ")
            }
            if (isClickingUninstall || isClickingUpdate) {
                val isReclaim = try {
                    val root = rootInActiveWindow ?: eventSource
                    val txt = root?.let { collectQuickWindowText(it) }?.lowercase() ?: ""
                    txt.contains("reclaim") || txt.contains("healinglab") || txt.contains("copsli")
                } catch (_: Throwable) { false }
                if (isReclaim) {
                    val target = if (isClickingUninstall) "Uninstall Protection" else "Update/Reinstall Blocked"
                    val reason = if (isClickingUninstall)
                        "Uninstalling Reclaim is locked while Prevent Uninstall is active."
                    else
                        "Updating or reinstalling Reclaim is blocked while Prevent Uninstall is active. Disable Prevent Uninstall first."
                    triggerIntervention(
                        target = target,
                        ruleType = RuleType.APP,
                        category = BlockCategory.CUSTOM,
                        reason = reason,
                        appPackage = packageName,
                        bypassCooldown = true
                    )
                    return true
                }
            }
        }

        // 1C. Intercept taps on Developer Options, Build Number, Software Info, or Device Admin deactivation in Settings
        val isSettings = isSettingsOrAccessibilityPkg(packageName) && !isOwnAppPackage(packageName) && !com.example.MainActivity.isForeground
        val isUninstallActive = prefs.isUninstallProtectionActiveFast()
        val isAccActive = prefs.cachedAccountabilityEnabled || SelfControlApp.repository.isFocusModeActiveFast()
        val canEnforceTamper = isUninstallActive || isAccActive

        if (isSettings && canEnforceTamper) {
            val vId = eventSource?.viewIdResourceName?.lowercase() ?: ""
            // Also scan the parent and grandparent node subtrees to get full row context
            val parentText = try {
                val sb = StringBuilder()
                eventSource?.parent?.let { p ->
                    p.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { sb.append(" ").append(it) }
                    p.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { sb.append(" ").append(it) }
                    val pChildren = collectDirectChildrenText(p)
                    if (pChildren.isNotBlank()) sb.append(" ").append(pChildren)
                }
                sb.toString()
            } catch (_: Throwable) { "" }
            val allClickLabels = (combinedLabels.joinToString(" ") + " " + parentText).lowercase()

            val isDevTap = combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                l == "build number" || l.contains("build number") ||
                l == "software information" || l == "software info" ||
                l == "developer options" || l == "developer settings" ||
                l == "usb debugging" || l == "wireless debugging" ||
                l == "miui version" || l == "hyperos version" || l == "os version" || l == "version number" ||
                l == "বিল্ড নম্বর" || l == "সফটওয়্যার তথ্য" || l == "সফটওয়্যার ইনফো" ||
                l.contains("developer mode") || l.contains("developer option") ||
                l.contains("developer setting")
            } || vId.contains("build_number") || vId.contains("software_info") || vId.contains("development_settings") ||
               vId.contains("developer_options") || allClickLabels.contains("build number") ||
               allClickLabels.contains("বিল্ড নম্বর") ||
               allClickLabels.contains("software information") || allClickLabels.contains("developer option") ||
               allClickLabels.contains("miui version") || allClickLabels.contains("hyperos version")

            if (isDevTap) {
                Log.d("AccessibilityService", "CLICK-BLOCKED: Dev Options/Build Number tap. labels=$combinedLabels vId=$vId")
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}
                triggerInterventionWithReinforcement(
                    target = "Developer Options Blocked",
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = "Accessing or enabling Developer Options by tapping Build Number is blocked while Prevent Uninstall or Accountability Lock is active.",
                    appPackage = packageName
                )
                return true
            }

            // Intercept the tap on "Device admin apps" row (before navigation) AND any deactivate button
            val isRealAdminNow = com.example.util.PermissionHelper.isDeviceAdminActive(this)
            val isActivatingNow = !isRealAdminNow ||
                (!isUninstallActive && com.example.util.PermissionHelper.isActivatingAdminFromApp()) ||
                com.example.service.receiver.SelfControlDeviceAdminReceiver.isRecentlyEnabled(4000L)
            val isClickingActivateBtn = combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                (l.startsWith("activate") || l.contains("activate this") || l.contains("সক্রিয়")) && !l.contains("deactivate")
            }
            val isDeviceManagerTap = !isActivatingNow && !isClickingActivateBtn && (combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                l.contains("device admin") || l.contains("device administrator") ||
                l.contains("device management") || l.contains("device manager") ||
                l.contains("ডিভাইস অ্যাডমিন") || l.contains("ডিভাইস ম্যানেজার") ||
                l == "deactivate" || l.contains("deactivate this") || l.contains("deactivate device") || l == "নিষ্ক্রিয়"
            } || vId.contains("device_admin") || vId.contains("deactivate") ||
               allClickLabels.contains("device admin") || allClickLabels.contains("device administrator") ||
               allClickLabels.contains("deactivate"))

            if (isDeviceManagerTap) {
                Log.d("AccessibilityService", "CLICK-BLOCKED: Device Admin tap. labels=$combinedLabels vId=$vId")
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}
                triggerInterventionWithReinforcement(
                    target = "Device Admin Protected",
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = "Accessing or modifying Device Administrator settings is completely blocked while Uninstall Protection or Accountability is active.",
                    appPackage = packageName
                )
                return true
            }

            // User request: "block reclaim instaltion via file manager (not other apps)"
            val isInstallTap = combinedLabels.any { lbl ->
                val l = lbl.lowercase().trim()
                l == "install" || l == "update" || l == "reinstall" || l == "replace" ||
                l.startsWith("install ") || l.startsWith("update ") ||
                l == "ইনস্টল" || l == "আপডেট"
            }
            if (isInstallTap) {
                val isReclaim = try {
                    val root = rootInActiveWindow ?: eventSource
                    val txt = root?.let { collectQuickWindowText(it) }?.lowercase() ?: ""
                    txt.contains("reclaim") || txt.contains("healinglab") || txt.contains("copsli") ||
                    txt.contains("com.example") || txt.contains("digital self-control") ||
                    txt.contains("selfcontrol")
                } catch (_: Throwable) { false }
                if (isReclaim) {
                    triggerIntervention(
                        target = "Update/Reinstall Blocked",
                        ruleType = RuleType.APP,
                        category = BlockCategory.CUSTOM,
                        reason = "Updating, reinstalling, or downgrading Reclaim is blocked while Prevent Uninstall is active. Disable Prevent Uninstall first.",
                        appPackage = packageName,
                        bypassCooldown = true
                    )
                    return true
                }
            }
        }

        // 2. If Google Scan & Lens is blocked and user taps Google Lens / Picture Scan / Camera Search
        val isLensBlocked = prefs.cachedGoogleLensBlocked
        if (isLensBlocked) {
            val vId = eventSource?.viewIdResourceName?.lowercase() ?: ""
            val isLensAction = vId.contains("lens") || vId.contains("visual_search") || vId.contains("circle_to_search") || vId.contains("gleam") ||
                    vId.contains("camera_icon") || vId.contains("lens_icon") || vId.contains("voice_and_lens") ||
                    ((packageName.contains("google") || packageName.contains("search")) && (vId.contains("camera") || vId.contains("scanner"))) ||
                    combinedLabels.any { lbl ->
                        val l = lbl.lowercase().trim()
                        l == "google lens" || l == "lens" || l == "visual search" ||
                        l == "search with camera" || l == "search with your camera" ||
                        l == "search using camera" || l == "search using your camera" ||
                        l == "picture scan" || l == "google scan" || l == "image scan" ||
                        l == "scan image" || l == "circle to search" || l == "search what you see" ||
                        l == "search inside photo" || l == "search by image" ||
                        l == "camera" || l == "scanner" || l == "ক্যামেরা" || l == "স্ক্যান" ||
                        l.contains("search with camera") || l.contains("search with your camera") ||
                        l.contains("search with your photo") || l.contains("lens visual search") ||
                        l.contains("lens_camera_button") || l.contains("পিকচার স্ক্যান") ||
                        l.contains("ছবি স্ক্যান") || l.contains("ক্যামেরা দিয়ে অনুসন্ধান") ||
                        l.contains("ক্যামেরা দিয়ে খুঁজুন") || l.contains("চিত্র অনুসন্ধান")
                    }

            if (isLensAction) {
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}
                triggerIntervention(
                    target = "Google Scan & Lens",
                    ruleType = RuleType.IMAGE_SEARCH,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Google Scan, Lens & Visual Search is blocked.",
                    appPackage = packageName,
                    bypassCooldown = true
                )
                return true
            }
        }

        // 3. If Image & Video Search is blocked and user taps "Images", "Videos", or "Short videos" tab in Google App
        val isGoogleApp = (DefaultBlocklistData.GOOGLE_SEARCH_PACKAGES.any { it.equals(packageName, ignoreCase = true) } ||
                packageName.contains("googlequicksearchbox", ignoreCase = true) ||
                packageName.contains("searchlite", ignoreCase = true)) &&
                !packageName.equals("com.google.android.apps.bard", ignoreCase = true) &&
                !packageName.contains("bard", ignoreCase = true)

        if (isGoogleApp && prefs.cachedImageVideoSearchBlocked) {
            val vId = eventSource?.viewIdResourceName?.lowercase() ?: ""
            for (lbl in combinedLabels) {
                val l = lbl.lowercase().trim()
                // Never block Google Scan / Lens / Camera under Image & Video search
                if (l.contains("lens") || l.contains("camera") || l.contains("scanner") || l.contains("স্ক্যান") || l.contains("ক্যামেরা")) {
                    continue
                }

                // Ignore Discover feed items or home navigation tabs
                val isHomeOrDiscover = vId.contains("feed") || vId.contains("discover") || vId.contains("bottom_nav") ||
                        l == "home" || l == "discover" || l == "saved"

                if (isHomeOrDiscover) continue

                val isVideoTabTap = l == "videos" || l == "short videos" || l == "videos tab" || l == "search for videos" ||
                        l == "vídeos" || l == "vidéos" || l == "ভিডিও" || l == "videos filter" ||
                        vId.contains("video_tab") || vId.contains("video_filter")

                val isImageTabTap = l == "images" || l == "images tab" || l == "search for images" ||
                        l == "imágenes" || l == "bilder" || l == "imagens" || l == "ছবি" || l == "চিত্র" || l == "images filter" ||
                        vId.contains("image_tab") || vId.contains("image_filter")

                if (isVideoTabTap) {
                    triggerIntervention(
                        target = "Video Search",
                        ruleType = RuleType.IMAGE_SEARCH,
                        category = BlockCategory.IMAGE_VIDEO_SEARCH,
                        reason = "Video search is blocked.",
                        appPackage = packageName
                    )
                    return true
                }
                if (isImageTabTap) {
                    triggerIntervention(
                        target = "Image Search",
                        ruleType = RuleType.IMAGE_SEARCH,
                        category = BlockCategory.IMAGE_VIDEO_SEARCH,
                        reason = "Image search is blocked.",
                        appPackage = packageName
                    )
                    return true
                }
            }
        }

        // 4. If YouTube Shorts, Instagram Reels, or Facebook Reels is blocked and user taps Reels/Shorts tab
        val isIG = RuleEngine.isInstagramPackage(packageName)
        val isFB = RuleEngine.isFacebookPackage(packageName)
        val isYT = RuleEngine.isYouTubePackage(packageName)

        if ((isIG && prefs.cachedInstagramReelsBlocked) || (isFB && prefs.cachedFacebookReelsBlocked) || (isYT && prefs.cachedYoutubeShortsBlocked)) {
            val vId = eventSource?.viewIdResourceName?.lowercase() ?: ""
            for (lbl in combinedLabels) {
                val l = lbl.lowercase().trim()
                val isReelsOrShortsTap = (l == "reels" || l == "reels tab" || l.startsWith("reels,") || l.startsWith("selected, reels") ||
                        vId.contains("reels_tab") || vId.contains("clips_tab")) ||
                        (isYT && (l == "shorts" || l == "shorts tab" || l.startsWith("shorts,") || vId.contains("shorts_tab")))

                if (isReelsOrShortsTap) {
                    val label = if (isIG) "Instagram Reels" else if (isFB) "Facebook Reels" else "YouTube Shorts"
                    triggerIntervention(
                        target = label,
                        ruleType = RuleType.SHORTS,
                        category = BlockCategory.SHORT_VIDEO,
                        reason = "$label tab detected",
                        appPackage = packageName
                    )
                    return true
                }
            }
        }

        return false
    }

    private fun isCoreSystemOrLauncherPackage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName ||
            packageName == "com.example" ||
            packageName == "com.healinglab.reclaim.app" ||
            packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName == "com.android.vending" ||
            packageName == "com.sec.android.app.samsungapps" ||
            packageName.contains("inputmethod") ||
            packageName.contains("honeyboard") ||
            packageName.contains("swiftkey") ||
            packageName.contains("dialer") ||
            packageName.contains("incallui") ||
            packageName.contains("telecom") ||
            packageName == "com.android.phone" ||
            SETTINGS_PACKAGES.contains(packageName) ||
            getAllHomeLauncherPackages().contains(packageName)
        ) {
            return true
        }
        return false
    }

    private suspend fun checkAndEnforce(packageName: String, eventSource: AccessibilityNodeInfo? = null) {
        val isSettings = isSettingsOrAccessibilityPkg(packageName)
        if (InterventionActivity.isShowing) {
            if (!isSettings) {
                return
            }
        }
        val now = SystemClock.uptimeMillis()
        if (!isSettings) {
            if (InterventionActivity.isInDismissCooldown(packageName)) {
                return
            }
            if (packageName == temporarilyIgnoredPackage && now < ignoreUntilTime) {
                return
            }
        }

        val repo = SelfControlApp.repository
        val prefs = repo.preferences

        val isTempUnlocked = prefs.isTemporaryUnlockActiveFast()
        if (isTempUnlocked) return

        // 0. Focus Mode & Accountability Lock evaluation (0ms in-memory fast path)
        val activeSession = repo.getActiveSessionFast()
        val activeSchedule = repo.getActiveFocusScheduleFast()
        val isFocusMode = activeSession != null || activeSchedule != null

        val isAccEnabled = prefs.cachedAccountabilityEnabled
        val isAccountabilityLocked = isAccEnabled || isFocusMode
        val isAccountabilityActive = isAccEnabled || isFocusMode
        val isRealDeviceAdminActive = com.example.util.PermissionHelper.isDeviceAdminActive(this)
        val isUninstallProtection = prefs.isUninstallProtectionActiveFast()

        if (com.example.MainActivity.isForeground || isOwnAppPackage(packageName)) return

        // 0A. Block updating / downgrading / reinstalling Reclaim via Package Installer, File Manager, Browser, etc.
        // Runs whenever Uninstall Protection is active, independent of whether "Block New Apps" is enabled!
        if (isUninstallProtection) {
            val rootNode = try { rootInActiveWindow ?: eventSource } catch (_: Throwable) { eventSource }
            if (rootNode != null && !isOwnAppPackage(rootNode.packageName?.toString())) {
                val txt = collectQuickWindowText(rootNode).lowercase()
                val isReclaimTarget = txt.contains("reclaim") ||
                        txt.contains("healinglab") ||
                        txt.contains("copsli") ||
                        txt.contains("com.healinglab.reclaim.app") ||
                        txt.contains("com.example") ||
                        txt.contains("digital self-control") ||
                        txt.contains("selfcontrol")
                val isInstallAction = txt.contains("install") ||
                        txt.contains("update") ||
                        txt.contains("replace") ||
                        txt.contains("downgrade") ||
                        txt.contains("do you want to install") ||
                        txt.contains("want to update") ||
                        txt.contains("existing app") ||
                        txt.contains("existing application") ||
                        txt.contains("app will be replaced") ||
                        txt.contains("version of this app") ||
                        txt.contains("ইনস্টল") ||
                        txt.contains("আপডেট")
                val isUninstalling = txt.contains("do you want to uninstall") || txt.contains("delete this app")
                if (isReclaimTarget && isInstallAction && !isUninstalling) {
                    Log.d("AccessibilityService", "BLOCKED: Attempt to update/downgrade/reinstall Reclaim")
                    triggerIntervention(
                        target = "Update/Reinstall Blocked",
                        ruleType = RuleType.APP,
                        category = BlockCategory.CUSTOM,
                        reason = "Updating, reinstalling, or downgrading Reclaim is blocked while Prevent Uninstall is active. Disable Prevent Uninstall first.",
                        appPackage = packageName,
                        bypassCooldown = true
                    )
                    return
                }
            }
        }

        // Check Settings for Accessibility Tampering, Force Stop, Permission Tampering, Developer Options or Uninstall Attempt
        // ONLY when Prevent Uninstall or Accountability Lock is active.
        if (isSettings && (isUninstallProtection || isAccountabilityLocked)) {
            val rootNode = try { rootInActiveWindow ?: eventSource } catch (_: Throwable) { eventSource }
                ?: getSettingsWindowRoots(eventSource).firstOrNull()
            if (rootNode != null && !isOwnAppPackage(rootNode.packageName?.toString())) {
                val blocked = inspectNodeForUninstall(
                    root = rootNode,
                    packageName = packageName,
                    isUninstallProtection = isUninstallProtection,
                    isAccountabilityLocked = isAccountabilityLocked,
                    isAccountabilityEnabled = isAccEnabled,
                    isDeviceAdmin = isRealDeviceAdminActive
                )
                if (blocked) return
            }
        }

        if (isSettings && (InterventionActivity.isInDismissCooldown(packageName) || (packageName == temporarilyIgnoredPackage && now < ignoreUntilTime))) {
            return
        }

        if (isFocusMode) {
            val allowedPackages = mutableSetOf(
                applicationContext.packageName,
                "com.example",
                "com.healinglab.reclaim.app",
                "com.android.systemui",
                "com.android.phone",
                "com.android.server.telecom",
                "com.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.samsung.android.incallui",
                "com.android.emergency",
                "com.google.android.emergency",
                "com.google.android.apps.messaging",
                "com.android.mms",
                "com.samsung.android.messaging",
                "com.google.android.permissioncontroller",
                "com.android.permissioncontroller",
                // Soft keyboards / IMEs
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.touchtype.swiftkey",
                "com.android.inputmethod.latin"
            )
            allowedPackages.addAll(getAllHomeLauncherPackages())

            // Add user's allowed apps from active schedule
            activeSchedule?.allowedApps?.split(",")?.forEach { pkg ->
                val trimmed = pkg.trim()
                if (trimmed.isNotEmpty()) allowedPackages.add(trimmed)
            }

            if (!allowedPackages.contains(packageName)) {
                val appLabel = getAppLabel(packageName)
                val scheduleTitle = activeSchedule?.title?.takeIf { it.isNotBlank() } ?: "Focus Session"
                Log.d("AccessibilityService", "Focus Mode blocking package: $packageName")
                triggerIntervention(
                    target = appLabel.ifBlank { packageName },
                    ruleType = RuleType.APP,
                    category = BlockCategory.EXTREME_DISTRACTION,
                    reason = "Focus Mode is active ($scheduleTitle). Only allowed apps and phone/SMS are accessible.",
                    appPackage = packageName
                )
                return
            }
        }

        val isMasterActive = prefs.cachedMasterProtection
        if (!isMasterActive) return

        val isNewAppsBlocked = prefs.cachedNewAppsBlocked
        val isUnsupportedBrowsersBlocked = prefs.cachedUnsupportedBrowsersBlocked
        val isAdultBlocked = prefs.cachedAdultContentBlocked
        val isImageVideoSearchBlocked = prefs.cachedImageVideoSearchBlocked
        val isGoogleLensBlocked = prefs.cachedGoogleLensBlocked
        val isShortsBlocked = prefs.cachedYoutubeShortsBlocked
        val isWhatsappChannelsBlocked = prefs.cachedWhatsappChannelsBlocked
        val isInstagramReelsBlocked = prefs.cachedInstagramReelsBlocked
        val isFacebookReelsBlocked = prefs.cachedFacebookReelsBlocked

        // Check for Google Lens / Visual Search standalone packages
        val isLensApp = DefaultBlocklistData.GOOGLE_LENS_PACKAGES.contains(packageName) ||
                packageName == "com.google.ar.lens" ||
                packageName == "com.google.android.apps.lens"
        if (isLensApp && isGoogleLensBlocked) {
            triggerIntervention(
                target = "Google Scan & Lens",
                ruleType = RuleType.APP,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Google Scan, Lens & Visual Search is blocked.",
                appPackage = packageName
            )
            return
        }

        // 1. Block New App Installs & Newly Installed Apps (installed from Play Store or APK file after the block was enabled)
        if (isNewAppsBlocked) {
            prefs.ensureNewAppsBaselineInitialized()

            // 1A. Check Package Installers and File Managers for Uninstall or Update/Downgrade/Reinstall attempts targeting Reclaim
            val isApkInstallerPkg = PACKAGE_INSTALLER_PACKAGES.contains(packageName) ||
                    packageName.contains("packageinstaller", ignoreCase = true) ||
                    packageName.contains("file", ignoreCase = true) ||
                    packageName.contains("myfiles", ignoreCase = true) ||
                    packageName.contains("explorer", ignoreCase = true) ||
                    packageName.contains("documentsui", ignoreCase = true) ||
                    packageName.contains("nbu.files", ignoreCase = true)
            if (isApkInstallerPkg) {
                val r = try { rootInActiveWindow ?: eventSource } catch (_: Throwable) { null }
                val txt = r?.let { collectQuickWindowText(it) }?.lowercase() ?: ""
                val isUninstalling = txt.contains("uninstall") || txt.contains("do you want to uninstall") || txt.contains("delete")
                if (isUninstalling && isUninstallProtection) {
                    r?.let {
                        inspectNodeForUninstall(
                            root = it,
                            packageName = packageName,
                            isUninstallProtection = true,
                            isAccountabilityLocked = isAccountabilityLocked,
                            isAccountabilityEnabled = isAccEnabled
                        )
                    }
                }

                // 1A-2. Block update/downgrade/reinstall of Reclaim when Prevent Uninstall is active
                if (isUninstallProtection) {
                    val isReclaimTarget = txt.contains("reclaim") ||
                            txt.contains("healinglab") ||
                            txt.contains("copsli") ||
                            txt.contains("com.healinglab.reclaim.app") ||
                            txt.contains("com.example") ||
                            txt.contains("digital self-control") ||
                            txt.contains("selfcontrol")
                    val isInstallAction = txt.contains("install") ||
                            txt.contains("update") ||
                            txt.contains("replace") ||
                            txt.contains("downgrade") ||
                            txt.contains("do you want to install") ||
                            txt.contains("want to update") ||
                            txt.contains("an existing app") ||
                            txt.contains("existing application") ||
                            txt.contains("replace the current") ||
                            txt.contains("app will be replaced") ||
                            txt.contains("version of this app") ||
                            txt.contains("ইনস্টল") ||
                            txt.contains("আপডেট")
                    if (isReclaimTarget && isInstallAction && !isUninstalling) {
                        Log.d("AccessibilityService", "BLOCKED: Attempt to update/downgrade/reinstall Reclaim via package installer")
                        triggerIntervention(
                            target = "Update/Reinstall Blocked",
                            ruleType = RuleType.APP,
                            category = BlockCategory.CUSTOM,
                            reason = "Updating, downgrading, or reinstalling Reclaim is blocked while Prevent Uninstall is active. Disable Prevent Uninstall first.",
                            appPackage = packageName,
                            bypassCooldown = true
                        )
                        return
                    }
                }
            }

            // 1B. Check if this opened app was newly installed (from Play Store or from an APK file) while the block is enabled
            if (!isCoreSystemOrLauncherPackage(packageName)) {
                val hiddenNewApps = prefs.hiddenNewlyInstalledPackages.first()
                val blockedSince = prefs.newAppsBlockedSince.first()
                val baselinePackages = prefs.baselineInstalledPackages.first()

                val isNewlyInstalled = com.example.domain.engine.NewAppGuardTracker.isPackageNewlyInstalled(
                    context = this,
                    packageName = packageName,
                    persistedHiddenNewApps = hiddenNewApps,
                    persistedBlockedSince = blockedSince,
                    persistedBaseline = baselinePackages
                )

                if (isNewlyInstalled) {
                    if (!hiddenNewApps.contains(packageName)) {
                        prefs.addHiddenNewlyInstalledPackage(packageName)
                    }
                    val appLabel = getAppLabel(packageName)
                    triggerIntervention(
                        target = appLabel.ifBlank { packageName },
                        ruleType = RuleType.APP,
                        category = BlockCategory.NEW_APPS,
                        reason = "This newly installed app is blocked while Block New App Installs is active.",
                        appPackage = packageName
                    )
                    return
                }
            }
        }

        // 2. Check for unsupported browsers (only allowed browsers are permitted when enabled)
        val isDetectedAsBrowser = isBrowserPackage(packageName)
        if (isUnsupportedBrowsersBlocked) {
            val browserEval = RuleEngine.evaluateUnsupportedBrowser(
                packageName = packageName,
                isBlockUnsupportedBrowsersEnabled = true,
                isDetectedBrowser = isDetectedAsBrowser
            )
            if (browserEval.isBlocked) {
                val appLabel = getAppLabel(packageName)
                triggerIntervention(
                    target = appLabel.ifBlank { "Unsupported Browser" },
                    ruleType = RuleType.APP,
                    category = BlockCategory.UNSUPPORTED_BROWSERS,
                    reason = "This browser is blocked to ensure content safety rules cannot be bypassed. Please use an allowed browser like Google Chrome, Firefox, or Samsung Internet.",
                    appPackage = packageName
                )
                return
            }
        }

        val rules = repo.getEnabledRulesCached()

        // 3. App package blocking evaluation
        val appEval = RuleEngine.evaluatePackage(
            packageName = packageName,
            rules = rules,
            isFocusMode = isFocusMode,
            isTemporaryUnlocked = isTempUnlocked,
            isBlockUnsupportedBrowsers = isUnsupportedBrowsersBlocked
        )
        if (appEval.isBlocked) {
            val appLabel = getAppLabel(packageName)
            triggerIntervention(
                target = appLabel.ifBlank { packageName },
                ruleType = RuleType.APP,
                category = appEval.category,
                reason = appEval.reason,
                appPackage = packageName
            )
            return
        }

        // 4. Node inspection for relevant apps
        val isShortsApp = SHORTS_PACKAGES.contains(packageName) || 
                          RuleEngine.isYouTubePackage(packageName) || 
                          RuleEngine.isInstagramPackage(packageName) || 
                          RuleEngine.isFacebookPackage(packageName)
        val isBrowserApp = BROWSER_PACKAGES.any { it.equals(packageName, ignoreCase = true) } || isDetectedAsBrowser
        val isGoogleApp = DefaultBlocklistData.GOOGLE_SEARCH_PACKAGES.any { it.equals(packageName, ignoreCase = true) } || 
                          packageName.contains("googlequicksearchbox", ignoreCase = true) || 
                          packageName.contains("searchlite", ignoreCase = true)
        val isWhatsApp = DefaultBlocklistData.WHATSAPP_PACKAGES.contains(packageName)

        if (!isShortsApp && !isBrowserApp && !isGoogleApp && !isWhatsApp && !isSettings) {
            return
        }

        try {
            val rootNode = rootInActiveWindow ?: eventSource ?: return

            // Settings uninstall/tamper inspection already ran in step 0B above
            if (isSettings) {
                return
            }

            // Check Google App for searches, adult websites, keywords, images, videos & Google Lens
            if (isGoogleApp) {
                inspectGoogleSearchApp(
                    root = rootNode,
                    packageName = packageName,
                    rules = rules,
                    isAdultBlocked = isAdultBlocked,
                    isImageVideoSearchBlocked = isImageVideoSearchBlocked,
                    isGoogleLensBlocked = isGoogleLensBlocked
                )
                return
            }

            // Check WhatsApp for Channels / Updates tab
            if (isWhatsApp && isWhatsappChannelsBlocked) {
                inspectNodeForWhatsAppChannels(rootNode, packageName)
                return
            }

            // Check Shorts and Reels
            if (isShortsApp) {
                inspectNodeForShorts(
                    root = rootNode,
                    packageName = packageName,
                    ytBlocked = isShortsBlocked,
                    igBlocked = isInstagramReelsBlocked,
                    fbBlocked = isFacebookReelsBlocked
                )
            }

            // Check Browser for URL / Image & Video Search / Subdomains
            if (isBrowserApp) {
                inspectNodeForBrowserUrl(
                    root = rootNode,
                    packageName = packageName,
                    rules = rules,
                    isAdultBlocked = isAdultBlocked,
                    isImageVideoSearchBlocked = isImageVideoSearchBlocked,
                    isGoogleLensBlocked = isGoogleLensBlocked
                )
            }
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (DefaultBlocklistData.GOOGLE_SEARCH_PACKAGES.any { it.equals(packageName, ignoreCase = true) } ||
            isCoreSystemOrLauncherPackage(packageName)
        ) {
            return false
        }
        browserPackageCache[packageName]?.let { return it }

        val detected = try {
            val pm = packageManager
            val testIntents = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
                Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            )
            var matchesBrowserIntent = false
            for (intent in testIntents) {
                val flagsList = listOf(PackageManager.MATCH_ALL, PackageManager.MATCH_DEFAULT_ONLY, 0)
                for (flag in flagsList) {
                    val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flag.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, flag)
                    }
                    if (list.any { it.activityInfo?.packageName.equals(packageName, ignoreCase = true) }) {
                        matchesBrowserIntent = true
                        break
                    }
                }
                if (matchesBrowserIntent) break
            }

            val labelLower = getAppLabel(packageName).lowercase()
            val matchesLabel = labelLower.contains("browser") || labelLower.contains("navegador") || labelLower.contains("navigateur")

            matchesBrowserIntent || matchesLabel
        } catch (_: Exception) {
            false
        }

        browserPackageCache[packageName] = detected
        return detected
    }

    private fun collectAllWindowRoots(initialRoot: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        roots.add(initialRoot)
        try {
            val active = rootInActiveWindow
            if (active != null && active != initialRoot) {
                roots.add(active)
            }
            val winList = windows
            if (winList != null) {
                for (w in winList) {
                    val wRoot = w.root
                    if (wRoot != null && !roots.contains(wRoot)) {
                        roots.add(wRoot)
                    }
                }
            }
        } catch (_: Throwable) {}
        return roots
    }

    private fun isNodeOrParentSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.safeIsSelected() || node.safeIsChecked()) return true
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val stateDesc = node.stateDescription?.toString()?.lowercase() ?: ""
                if (stateDesc.contains("select") || stateDesc.contains("check") || stateDesc.contains("on") || stateDesc.contains("active")) {
                    return true
                }
            }
        } catch (_: Throwable) {}
        return try {
            val p1 = node.parent
            if (p1 != null) {
                if (p1.safeIsSelected() || p1.safeIsChecked()) return true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val stateDesc = p1.stateDescription?.toString()?.lowercase() ?: ""
                    if (stateDesc.contains("select") || stateDesc.contains("check") || stateDesc.contains("on") || stateDesc.contains("active")) {
                        return true
                    }
                }
                val p2 = p1.parent
                if (p2 != null) {
                    if (p2.safeIsSelected() || p2.safeIsChecked()) return true
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val stateDesc = p2.stateDescription?.toString()?.lowercase() ?: ""
                        if (stateDesc.contains("select") || stateDesc.contains("check") || stateDesc.contains("on") || stateDesc.contains("active")) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun collectDirectChildrenText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            val initialChildren = node.childCount.coerceAtMost(10)
            for (i in 0 until initialChildren) {
                node.getChild(i)?.let { queue.add(it) }
            }
            var visited = 0
            while (queue.isNotEmpty() && visited < 30) {
                val cur = queue.removeFirst()
                visited++
                cur.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { sb.append(" ").append(it) }
                cur.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { sb.append(" ").append(it) }
                val cCount = cur.childCount.coerceAtMost(8)
                for (j in 0 until cCount) {
                    cur.getChild(j)?.let { queue.add(it) }
                }
            }
        } catch (_: Throwable) {}
        return sb.toString().trim()
    }

    private suspend fun inspectGoogleSearchApp(
        root: AccessibilityNodeInfo,
        packageName: String,
        rules: List<com.example.data.model.BlockRule>,
        isAdultBlocked: Boolean,
        isImageVideoSearchBlocked: Boolean,
        isGoogleLensBlocked: Boolean = false
    ) {
        if (packageName.equals("com.google.android.apps.bard", ignoreCase = true) || packageName.contains("bard", ignoreCase = true)) {
            return
        }

        val candidateTexts = mutableListOf<String>()
        val candidateSearchQueries = mutableListOf<String>()
        val candidateUrls = mutableListOf<String>()
        var isImageTabActive = false
        var isVideoTabActive = false
        var isLensActive = false
        var videoResultCount = 0
        var imageResultCount = 0
        var isGeminiActiveInGoogleApp = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        collectAllWindowRoots(root).forEach { queue.add(it) }
        var count = 0

        val customKeywords = rules.filter { it.enabled && it.type == RuleType.KEYWORD }.map { it.target }

        while (queue.isNotEmpty() && count < 180) {
            val node = queue.removeFirst()
            count++

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            val lowerText = text.lowercase()
            val lowerDesc = desc.lowercase()
            val effectiveLabel = if (lowerText.isNotBlank()) lowerText else lowerDesc

            // Detect Gemini tab/overlay inside Google App
            if (viewId.contains("gemini") || viewId.contains("bard") || lowerText.contains("gemini") || lowerDesc.contains("gemini")) {
                if (lowerDesc.contains("selected") || lowerText.contains("selected") || node.safeIsSelected() || node.safeIsChecked()) {
                    isGeminiActiveInGoogleApp = true
                }
            }

            // 0. Detect Google Lens / Picture Scan / Circle to Search inside Google App ONLY when isGoogleLensBlocked is true!
            if (isGoogleLensBlocked) {
                val isLensNode = viewId.contains("lens") ||
                        viewId.contains("visual_search") ||
                        viewId.contains("circle_to_search") ||
                        viewId.contains("gleam") ||
                        className.contains("Lens", ignoreCase = true) ||
                        className.contains("VisualSearch", ignoreCase = true) ||
                        effectiveLabel.contains("google lens") ||
                        effectiveLabel.contains("search with your camera") ||
                        effectiveLabel.contains("search with camera") ||
                        effectiveLabel.contains("search using camera") ||
                        effectiveLabel.contains("visual search") ||
                        effectiveLabel.contains("visual matches") ||
                        effectiveLabel.contains("results for image") ||
                        effectiveLabel.contains("circle to search") ||
                        effectiveLabel.contains("picture scan") ||
                        effectiveLabel.contains("google scan") ||
                        effectiveLabel.contains("search what you see") ||
                        effectiveLabel.contains("search inside photo") ||
                        effectiveLabel.contains("search by image") ||
                        effectiveLabel.contains("lens_camera_button") ||
                        effectiveLabel.contains("পিকচার স্ক্যান") ||
                        effectiveLabel.contains("ছবি স্ক্যান") ||
                        effectiveLabel.contains("ক্যামেরা দিয়ে অনুসন্ধান")

                if (isLensNode) {
                    isLensActive = true
                }
            }

            // 1. Detect Images / Videos / Short videos tabs and chip filters in Google Search results
            if (isImageVideoSearchBlocked && !isGeminiActiveInGoogleApp) {
                // Ignore lens/camera labels so they never trigger video/image search blocks
                val isCameraOrLens = effectiveLabel.contains("lens") || effectiveLabel.contains("camera") ||
                        effectiveLabel.contains("scan") || effectiveLabel.contains("ক্যামেরা")

                if (!isCameraOrLens) {
                    val isVideoTabLabel = effectiveLabel == "videos" || effectiveLabel == "short videos" ||
                            lowerText == "videos" || lowerText == "short videos" ||
                            lowerDesc.contains("videos tab") || lowerDesc.contains("search for videos") ||
                            lowerDesc.contains("short videos tab") || lowerDesc.contains("filter by video") ||
                            lowerDesc.contains("videos filter") || lowerDesc.contains("videos, selected") ||
                            lowerDesc.contains("selected, videos") || lowerDesc.contains("videos (selected)") ||
                            lowerDesc.contains("current filter: videos") || viewId.contains("video_tab") || viewId.contains("video_filter")

                    val isImageTabLabel = effectiveLabel == "images" ||
                            lowerText == "images" ||
                            lowerDesc.contains("images tab") || lowerDesc.contains("search for images") ||
                            lowerDesc.contains("images filter") || lowerDesc.contains("filter by image") ||
                            lowerDesc.contains("images, selected") || lowerDesc.contains("selected, images") ||
                            lowerDesc.contains("images (selected)") || lowerDesc.contains("current filter: images") ||
                            lowerDesc.contains("imágenes") || lowerDesc.contains("bilder") || viewId.contains("image_tab") || viewId.contains("image_filter")

                    // Check if the TAB NODE ITSELF is selected or marked as active
                    val isNodeDirectlySelected = node.safeIsSelected() ||
                            node.safeIsChecked() ||
                            lowerDesc.contains("selected") ||
                            lowerDesc.contains("current") ||
                            lowerDesc.contains("active") ||
                            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && node.stateDescription?.toString()?.lowercase()?.let { it.contains("select") || it.contains("check") || it.contains("on") } == true)

                    if (isVideoTabLabel && isNodeDirectlySelected) {
                        isVideoTabActive = true
                    }
                    if (isImageTabLabel && isNodeDirectlySelected) {
                        isImageTabActive = true
                    }

                    // Count explicit video result cards (excluding clock times like 10:15)
                    if (viewId.contains("video_card") || viewId.contains("video_result") ||
                        viewId.contains("short_videos_card") || lowerDesc.contains("video result") ||
                        viewId.contains("video_preview")
                    ) {
                        videoResultCount++
                    }

                    // Count explicit image result cards
                    if (lowerText.contains("images may be subject to copyright") ||
                        lowerDesc.contains("image result") ||
                        lowerDesc.contains("photo result") ||
                        viewId.contains("image_result") ||
                        viewId.contains("image_card") ||
                        viewId.contains("image_preview") ||
                        viewId.contains("universal_image") ||
                        viewId.contains("grid_image") ||
                        viewId.contains("isch")
                    ) {
                        imageResultCount++
                    }
                }
            }

            // 2. Search query boxes / EditTexts / Query chips / Header texts
            val isSearchInput = className.contains("EditText", ignoreCase = true) ||
                    node.safeIsEditable() ||
                    viewId.contains("search_box") ||
                    viewId.contains("search_plate") ||
                    viewId.contains("search_src_text") ||
                    viewId.contains("url_bar") ||
                    viewId.contains("open_search_view_edit_text") ||
                    lowerText.endsWith("- google search") ||
                    lowerDesc.endsWith("- google search")

            if (text.isNotBlank() && text.length >= 2) {
                candidateTexts.add(text)
                if (isSearchInput) {
                    val isPlaceholder = text.equals("Search", ignoreCase = true) ||
                            text.equals("Search or ask anything", ignoreCase = true) ||
                            text.equals("Ask Gemini", ignoreCase = true) ||
                            text.equals("Say \"Hey Google\"", ignoreCase = true) ||
                            text.equals("Search...", ignoreCase = true)
                    if (!isPlaceholder) {
                        candidateSearchQueries.add(text)
                    }
                }
                if (text.startsWith("http://") || text.startsWith("https://") || text.contains("google.com/search")) {
                    candidateUrls.add(text)
                }
            }

            if (desc.isNotBlank() && desc.length >= 2) {
                candidateTexts.add(desc)
                if (isSearchInput) {
                    val isPlaceholder = desc.equals("Search", ignoreCase = true) ||
                            desc.equals("Search or ask anything", ignoreCase = true) ||
                            desc.equals("Ask Gemini", ignoreCase = true) ||
                            desc.equals("Say \"Hey Google\"", ignoreCase = true) ||
                            desc.equals("Search...", ignoreCase = true)
                    if (!isPlaceholder) {
                        candidateSearchQueries.add(desc)
                    }
                }
                if (desc.startsWith("http://") || desc.startsWith("https://") || desc.contains("google.com/search")) {
                    candidateUrls.add(desc)
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }

        // Evaluation 0: Google Scan & Lens inside Google App ONLY if isGoogleLensBlocked is true!
        if (isLensActive && isGoogleLensBlocked) {
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (_: Throwable) {}
            triggerIntervention(
                target = "Google Scan & Lens",
                ruleType = RuleType.IMAGE_SEARCH,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Google Scan, Lens & Visual Search is blocked.",
                appPackage = packageName
            )
            return
        }

        // Evaluation B: Domain visited via Google App / Custom Tab (Layer 2 500k+ Adult Blocking + Custom Rules)
        for (url in candidateUrls) {
            val domainEval = RuleEngine.evaluateDomain(
                domain = url,
                rules = rules,
                isTemporaryUnlocked = false,
                isAdultBlocked = isAdultBlocked
            )
            if (domainEval.isBlocked) {
                triggerIntervention(
                    target = url,
                    ruleType = RuleType.DOMAIN,
                    category = domainEval.category,
                    reason = domainEval.reason,
                    appPackage = packageName
                )
                return
            } else if (isAdultBlocked) {
                // Adult websites are blocked at the DNS layer via CleanBrowsing DNS (making them completely unreachable).
                // Do not rely on or trigger block screen (InterventionActivity) for adult website visits.
                val cleanDomain = RuleEngine.normalizeDomain(url)
                if (cleanDomain.contains('.') && !RuleEngine.isKnownSearchEngine(cleanDomain)) {
                    serviceScope.launch {
                        com.example.domain.engine.AdultBlockingEngine.isAdultDomainMultiLayer(cleanDomain)
                    }
                }
            }
        }

        // Evaluation C: Search queries & Adult / Custom blocked keywords (Layer 3 Keyword Scanning)
        for (queryText in candidateSearchQueries) {
            val searchEval = RuleEngine.evaluateSearchQuery(
                query = queryText,
                isSearchProtectionEnabled = true,
                customKeywords = customKeywords,
                isTemporaryUnlocked = false,
                isAdultBlocked = isAdultBlocked
            )
            if (searchEval.isBlocked) {
                triggerIntervention(
                    target = queryText,
                    ruleType = RuleType.KEYWORD,
                    category = searchEval.category,
                    reason = searchEval.reason,
                    appPackage = packageName
                )
                return
            }
        }
    }

    private fun collectQuickWindowText(root: AccessibilityNodeInfo): String {
        if (com.example.MainActivity.isForeground || isOwnAppPackage(root.packageName?.toString())) {
            return ""
        }
        val sb = StringBuilder()
        var topRoot = root
        try {
            var parent = topRoot.parent
            var depth = 0
            while (parent != null && depth < 20) {
                topRoot = parent
                parent = topRoot.parent
                depth++
            }
        } catch (_: Throwable) {}

        if (isOwnAppPackage(topRoot.packageName?.toString())) {
            return ""
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(topRoot)
        var count = 0
        while (queue.isNotEmpty() && count < 180) {
            val n = queue.removeFirst()
            count++
            n.text?.let { if (it.isNotBlank()) sb.append(" ").append(it) }
            n.contentDescription?.let { if (it.isNotBlank()) sb.append(" ").append(it) }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString()
    }

    private suspend fun inspectNodeForUninstall(
        root: AccessibilityNodeInfo,
        packageName: String,
        isUninstallProtection: Boolean,
        isAccountabilityLocked: Boolean = false,
        isAccountabilityEnabled: Boolean = false,
        isDeviceAdmin: Boolean = false
    ): Boolean {
        if (isOwnAppPackage(packageName) || isReclaimInForegroundOrPopup()) {
            return false
        }
        val effectiveAdmin = com.example.util.PermissionHelper.isDeviceAdminActive(this)
        if (!isUninstallProtection && !isAccountabilityLocked && !isAccountabilityEnabled) {
            return false
        }

        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()

        // Walk up `root` to its true window root in case `eventSource` was a child node
        var topRoot = root
        try {
            var parent = topRoot.parent
            var depth = 0
            while (parent != null && depth < 20) {
                topRoot = parent
                parent = topRoot.parent
                depth++
            }
        } catch (_: Throwable) {}
        if (!isOwnAppPackage(topRoot.packageName?.toString())) {
            candidateRoots.add(topRoot)
        }

        try {
            rootInActiveWindow?.let { activeRoot ->
                if (!isOwnAppPackage(activeRoot.packageName?.toString()) && !candidateRoots.contains(activeRoot)) {
                    candidateRoots.add(activeRoot)
                }
            }
        } catch (_: Throwable) {}

        // Also include all interactive dialog windows (e.g. "Turn off Reclaim?", "Deactivate?") that belong to Settings/PermissionController/PackageInstaller
        try {
            windows?.forEach { win ->
                win.root?.let { winRoot ->
                    val winPkg = winRoot.packageName?.toString() ?: ""
                    if (winPkg != "com.android.systemui" &&
                        !isOwnAppPackage(winPkg) &&
                        (winPkg == packageName || isSettingsOrAccessibilityPkg(winPkg))) {
                        if (!candidateRoots.contains(winRoot)) {
                            candidateRoots.add(winRoot)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        for (winRoot in candidateRoots) {
            val nodesToInspect = ArrayDeque<AccessibilityNodeInfo>()
            nodesToInspect.add(winRoot)

            val windowTextBuilder = StringBuilder()
            val windowViewIdsBuilder = StringBuilder()
            var cancelNode: AccessibilityNodeInfo? = null
            var hasSwitchControl = false
            var firstHeaderTitle: String? = null
            var count = 0

            while (nodesToInspect.isNotEmpty() && count < 500) {
                val node = nodesToInspect.removeFirst()
                count++

                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val text = node.text?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName ?: ""
                val className = node.className?.toString() ?: ""

                if (desc.isNotBlank()) windowTextBuilder.append(" ").append(desc)
                if (text.isNotBlank()) windowTextBuilder.append(" ").append(text)
                if (viewId.isNotBlank()) windowViewIdsBuilder.append(" ").append(viewId)

                if (firstHeaderTitle == null && text.isNotBlank()) {
                    val lowerT = text.lowercase()
                    if (lowerT != "navigate up" && lowerT != "back" && lowerT != "search" && lowerT != "more options") {
                        firstHeaderTitle = lowerT
                    }
                }

                if (!hasSwitchControl && (
                    className.contains("Switch", ignoreCase = true) ||
                    className.contains("CompoundButton", ignoreCase = true) ||
                    className.contains("ToggleButton", ignoreCase = true) ||
                    viewId.contains("switch_widget", ignoreCase = true) ||
                    viewId.contains("switch_bar", ignoreCase = true) ||
                    viewId.contains("sesl_switchbar", ignoreCase = true) ||
                    viewId.contains("main_switch", ignoreCase = true) ||
                    node.safeIsCheckable()
                )) {
                    hasSwitchControl = true
                }

                if (cancelNode == null && (
                    text.equals("Cancel", ignoreCase = true) ||
                    desc.equals("Cancel", ignoreCase = true) ||
                    text.equals("Dismiss", ignoreCase = true) ||
                    desc.equals("Dismiss", ignoreCase = true) ||
                    viewId.endsWith("button2") ||
                    viewId.contains("cancel_button") ||
                    viewId.contains("negative_button")
                )) {
                    cancelNode = node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) nodesToInspect.add(child)
                }
            }

            if ((firstHeaderTitle?.contains("reclaim") == true || firstHeaderTitle?.contains("digital self-control") == true) && hasSwitchControl) {
                windowViewIdsBuilder.append(" switch_bar ")
                windowTextBuilder.append(" reclaim_accessibility_detail_page ")
            }

            val lowerCollectedText = windowTextBuilder.toString().lowercase()
            if ((firstHeaderTitle?.contains("reclaim") == true || lowerCollectedText.contains("reclaim")) &&
                (lowerCollectedText.contains("deactivate") || lowerCollectedText.contains("this admin app is active") || lowerCollectedText.contains("admin app is active") || lowerCollectedText.contains("allows reclaim"))
            ) {
                windowViewIdsBuilder.append(" device_admin ")
                windowTextBuilder.append(" reclaim_device_admin_detail_page ")
            }

            val windowText = windowTextBuilder.toString()
            val windowViewIds = windowViewIdsBuilder.toString()
            val lowerWText = windowText.lowercase()
            if (isReclaimConfigPopupText(lowerWText)) {
                continue
            }
            val lowerWIds = windowViewIds.lowercase()

            val isInitialActivationPrompt = !effectiveAdmin ||
                (!isUninstallProtection && com.example.util.PermissionHelper.isActivatingAdminFromApp()) ||
                com.example.service.receiver.SelfControlDeviceAdminReceiver.isRecentlyEnabled(4000L)

            val hasAdminDetailViewIds = lowerWIds.contains(":id/admin_name") ||
                lowerWIds.contains(":id/admin_warning") ||
                lowerWIds.contains(":id/admin_policies") ||
                lowerWIds.contains(":id/admin_description") ||
                lowerWIds.contains(":id/restricted_action")

            // Direct check for Device Admin target (double layer: list page + detail page)
            val isDeviceAdminTarget = !isInitialActivationPrompt && (
                lowerWText.contains("reclaim_device_admin_detail_page") ||
                (hasAdminDetailViewIds && (lowerWText.contains("reclaim") || lowerWText.contains("deactivate") || lowerWText.contains("নিষ্ক্রিয়"))) ||
                lowerWText.contains("this admin app is active") ||
                lowerWText.contains("allows reclaim to perform") ||
                lowerWText.contains("admin app is active") ||
                lowerWText.contains("deactivate this device admin") ||
                lowerWText.contains("deactivate device admin") ||
                lowerWText.contains("deactivate this admin app") ||
                lowerWText.contains("prevents uninstallation of reclaim") ||
                lowerWText.contains("prevents impulsive uninstallation") ||
                lowerWText.contains("maintain digital self-control") ||
                (lowerWText.contains("reclaim") && lowerWText.contains("deactivate")) ||
                (lowerWText.contains("this admin app is active") && lowerWText.contains("deactivate")) ||
                (lowerWText.contains("this admin app is active") && lowerWText.contains("cancel")) ||
                ((lowerWText.contains("device admin apps") || lowerWText.contains("device admin app") || lowerWText.contains("device administrators") || lowerWText.contains("ডিভাইস অ্যাডমিন")) &&
                    lowerWText.contains("reclaim")) ||
                (lowerWText.contains("device admin") && lowerWText.contains("reclaim")) ||
                lowerWIds.contains("device_admin")
            )

            // Direct check for Software Information / Build Number target
            val isSoftwareInfoTarget = (
                lowerWText.contains("software information") ||
                lowerWText.contains("software info") ||
                lowerWText.contains("সফটওয়্যার তথ্য") ||
                lowerWText.contains("সফটওয়্যার ইনফো") ||
                lowerWText.contains("build number") ||
                lowerWText.contains("বিল্ড নম্বর") ||
                lowerWText.contains("steps away from being a developer") ||
                lowerWText.contains("step away from being a developer") ||
                lowerWText.contains("you are now a developer") ||
                lowerWText.contains("already a developer") ||
                lowerWText.contains("one ui version") ||
                lowerWText.contains("kernel version") ||
                lowerWText.contains("baseband version") ||
                lowerWText.contains("knox version") ||
                lowerWText.contains("se for android status") ||
                lowerWText.contains("security software version") ||
                lowerWIds.contains("software_info") ||
                lowerWIds.contains("software_information") ||
                lowerWIds.contains("build_number")
            )

            if (isDeviceAdminTarget) {
                try {
                    var clickable = cancelNode
                    while (clickable != null && !clickable.safeIsClickable()) {
                        clickable = clickable.parent
                    }
                    clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Throwable) {}
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}

                triggerInterventionWithReinforcement(
                    target = "Device Admin Protected",
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = "Deactivating Device Administrator is blocked while Uninstall Protection or Accountability is active.",
                    appPackage = packageName
                )
                return true
            }

            if (isSoftwareInfoTarget) {
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}
                triggerInterventionWithReinforcement(
                    target = "Developer Options Blocked",
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = "Accessing Software Information or Build Number is blocked while Prevent Uninstall or Accountability Lock is active.",
                    appPackage = packageName
                )
                return true
            }

            val eval = RuleEngine.evaluateUninstallAttempt(
                packageName = packageName,
                viewContentDescription = "",
                viewText = windowText,
                viewId = windowViewIds,
                isUninstallProtectionEnabled = isUninstallProtection,
                isDeviceAdminActive = effectiveAdmin,
                isAccountabilityLocked = isAccountabilityLocked,
                isAccountabilityEnabled = isAccountabilityEnabled
            )

            if (eval.isBlocked) {
                val isAdminBlockReason = eval.reason.contains("Device Administrator", ignoreCase = true) ||
                    eval.reason.contains("Device Admin", ignoreCase = true)
                if (isAdminBlockReason && isInitialActivationPrompt) {
                    continue
                }
                try {
                    var clickable = cancelNode
                    while (clickable != null && !clickable.safeIsClickable()) {
                        clickable = clickable.parent
                    }
                    clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Throwable) {}
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (_: Throwable) {}

                val blockTarget = when {
                    isAdminBlockReason -> "Device Admin Protected"
                    eval.reason.contains("Accessibility Service", ignoreCase = true) -> "Accessibility Lock Active"
                    eval.reason.contains("Developer Options", ignoreCase = true) || eval.reason.contains("Build Number", ignoreCase = true) || eval.reason.contains("Software Information", ignoreCase = true) -> "Developer Options Blocked"
                    eval.reason.contains("Force stop", ignoreCase = true) -> "Force Stop Blocked"
                    eval.reason.contains("permission", ignoreCase = true) -> "Permission Tamper Blocked"
                    eval.reason.contains("App Info", ignoreCase = true) -> "App Info Blocked"
                    eval.reason.contains("storage", ignoreCase = true) -> "Storage Tamper Blocked"
                    eval.reason.contains("Date & Time", ignoreCase = true) -> "Date & Time Locked"
                    else -> "Uninstall Protection"
                }

                triggerInterventionWithReinforcement(
                    target = blockTarget,
                    ruleType = RuleType.APP,
                    category = BlockCategory.CUSTOM,
                    reason = eval.reason.ifBlank { "Uninstalling Reclaim or tampering with protection is locked during your active commitment." },
                    appPackage = packageName
                )
                return true
            }
        }
        return false
    }

    private suspend fun inspectNodeForWhatsAppChannels(root: AccessibilityNodeInfo, packageName: String) {
        val nodesToInspect = ArrayDeque<AccessibilityNodeInfo>()
        nodesToInspect.add(root)

        var count = 0
        while (nodesToInspect.isNotEmpty() && count < 50) {
            val node = nodesToInspect.removeFirst()
            count++

            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""

            val eval = RuleEngine.evaluateWhatsAppChannels(
                packageName = packageName,
                viewContentDescription = desc,
                viewText = text,
                viewId = viewId,
                isWhatsAppChannelsBlocked = true
            )

            if (eval.isBlocked) {
                triggerIntervention(
                    target = "WhatsApp Channels",
                    ruleType = RuleType.WHATSAPP_CHANNELS,
                    category = BlockCategory.WHATSAPP_CHANNELS,
                    reason = "WhatsApp Channels & Status feed are blocked.",
                    appPackage = packageName
                )
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) nodesToInspect.add(child)
            }
        }
    }

    private suspend fun inspectNodeForShorts(
        root: AccessibilityNodeInfo,
        packageName: String,
        ytBlocked: Boolean,
        igBlocked: Boolean,
        fbBlocked: Boolean
    ) {
        val isYT = RuleEngine.isYouTubePackage(packageName)
        val isIG = RuleEngine.isInstagramPackage(packageName)
        val isFB = RuleEngine.isFacebookPackage(packageName)

        // Fast-path: check known fullscreen Reels / Shorts player container IDs using zero-cost C++ binder lookups
        if (isYT && ytBlocked) {
            val ytIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/shorts_player",
                "com.google.android.youtube:id/shorts_video_container",
                "com.google.android.youtube:id/reel_player"
            )
            for (id in ytIds) {
                try {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) {
                        shortsInterventionsCount++
                        triggerIntervention(
                            target = "YouTube Shorts",
                            ruleType = RuleType.SHORTS,
                            category = BlockCategory.SHORT_VIDEO,
                            reason = "YouTube Shorts detected",
                            appPackage = packageName
                        )
                        return
                    }
                } catch (_: Throwable) {}
            }
        }

        if (isIG && igBlocked) {
            val igIds = listOf(
                "com.instagram.android:id/clips_viewer_fragment_container",
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/clips_media_interact",
                "com.instagram.android:id/clips_attached_scrubber",
                "com.instagram.android:id/reel_viewer_fragment_container"
            )
            for (id in igIds) {
                try {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) {
                        shortsInterventionsCount++
                        triggerIntervention(
                            target = "Instagram Reels",
                            ruleType = RuleType.SHORTS,
                            category = BlockCategory.SHORT_VIDEO,
                            reason = "Instagram Reels detected",
                            appPackage = packageName
                        )
                        return
                    }
                } catch (_: Throwable) {}
            }
        }

        if (isFB && fbBlocked) {
            val fbIds = listOf(
                "com.facebook.katana:id/fb_reels_fullscreen_player",
                "com.facebook.katana:id/fb_reels_viewer",
                "com.facebook.katana:id/reels_consumption_container",
                "com.facebook.katana:id/short_form_video_chaining_player",
                "com.facebook.katana:id/reels_video_player"
            )
            for (id in fbIds) {
                try {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) {
                        shortsInterventionsCount++
                        triggerIntervention(
                            target = "Facebook Reels",
                            ruleType = RuleType.SHORTS,
                            category = BlockCategory.SHORT_VIDEO,
                            reason = "Facebook Reels detected",
                            appPackage = packageName
                        )
                        return
                    }
                } catch (_: Throwable) {}
            }
        }

        val nodesToInspect = ArrayDeque<AccessibilityNodeInfo>()
        nodesToInspect.add(root)

        var count = 0
        while (nodesToInspect.isNotEmpty() && count < 85) {
            val node = nodesToInspect.removeFirst()
            count++

            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val isSelectedNode = node.safeIsSelected()

            // 1. YouTube: Check if Shorts tab is selected
            if (isYT && ytBlocked) {
                val isShortsTab = desc.equals("Shorts", ignoreCase = true) || 
                                  text.equals("Shorts", ignoreCase = true) || 
                                  viewId.contains("shorts_tab")
                if (isShortsTab && (isSelectedNode || desc.contains("selected", ignoreCase = true) || text.contains("selected", ignoreCase = true))) {
                    shortsInterventionsCount++
                    triggerIntervention(
                        target = "YouTube Shorts",
                        ruleType = RuleType.SHORTS,
                        category = BlockCategory.SHORT_VIDEO,
                        reason = "YouTube Shorts detected",
                        appPackage = packageName
                    )
                    return
                }
            }

            // 2. Instagram: Check if dedicated Reels tab is selected in bottom navigation bar
            if (isIG && igBlocked) {
                val isReelsTab = (desc.equals("Reels", ignoreCase = true) || desc.equals("Clips", ignoreCase = true) || 
                                  text.equals("Reels", ignoreCase = true) || viewId.contains("clips_tab") || viewId.contains("reels_tab"))
                if (isReelsTab && (isSelectedNode || desc.contains("selected", ignoreCase = true) || text.contains("selected", ignoreCase = true))) {
                    shortsInterventionsCount++
                    triggerIntervention(
                        target = "Instagram Reels",
                        ruleType = RuleType.SHORTS,
                        category = BlockCategory.SHORT_VIDEO,
                        reason = "Instagram Reels tab detected",
                        appPackage = packageName
                    )
                    return
                }
            }

            // 3. Facebook: Check if dedicated Reels tab is selected in navigation
            if (isFB && fbBlocked) {
                val isReelsTab = (desc.equals("Reels", ignoreCase = true) || text.equals("Reels", ignoreCase = true) || viewId.contains("reels_tab"))
                if (isReelsTab && (isSelectedNode || desc.contains("selected", ignoreCase = true) || text.contains("selected", ignoreCase = true))) {
                    shortsInterventionsCount++
                    triggerIntervention(
                        target = "Facebook Reels",
                        ruleType = RuleType.SHORTS,
                        category = BlockCategory.SHORT_VIDEO,
                        reason = "Facebook Reels tab detected",
                        appPackage = packageName
                    )
                    return
                }
            }

            val shortsEval = RuleEngine.evaluateShorts(
                packageName = packageName,
                viewContentDescription = desc,
                viewText = text,
                viewId = viewId,
                isYouTubeShortsBlocked = ytBlocked,
                isInstagramReelsBlocked = igBlocked,
                isFacebookReelsBlocked = fbBlocked
            )

            if (shortsEval.isBlocked) {
                shortsInterventionsCount++
                triggerIntervention(
                    target = shortsEval.reason,
                    ruleType = RuleType.SHORTS,
                    category = BlockCategory.SHORT_VIDEO,
                    reason = shortsEval.reason,
                    appPackage = packageName
                )
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    nodesToInspect.add(child)
                }
            }
        }
    }

    private fun isBrowserHomeOrNewTab(urlOrText: String): Boolean {
        val lower = urlOrText.lowercase().trim()
        if (lower.isBlank()) return true

        // Internal browser home / blank URIs
        if (lower == "about:blank" || lower == "about:home" || lower == "about:newtab" ||
            lower == "about:privatebrowsing" || lower.startsWith("chrome://newtab") ||
            lower.startsWith("chrome-native://newtab") || lower.startsWith("chrome://") ||
            lower.startsWith("edge://newtab") || lower.startsWith("edge://") ||
            lower.startsWith("brave://newtab") || lower.startsWith("brave://") ||
            lower.startsWith("internet://newtab") || lower.startsWith("internet://") ||
            lower.startsWith("samsung://") || lower == "quickaccess" || lower.startsWith("about:")
        ) {
            return true
        }

        // Standard placeholders and home tab labels across browsers/languages
        val homeKeywords = listOf(
            "search or type url",
            "search or type web address",
            "search or enter address",
            "search or enter web address",
            "search or type a url",
            "search or enter website name",
            "search or enter url",
            "search or type",
            "type a url",
            "search the web",
            "new tab",
            "home tab",
            "nueva pestaña",
            "nouvel onglet",
            "neuer tab",
            "nuova scheda",
            "nova guia",
            "nová karta",
            "nowa karta",
            "новый вкладка",
            "новая вкладка",
            "নতুন ট্যাব",
            "নয়া ট্যাব",
            "새 탭",
            "新しいタブ",
            "新标签页",
            "新分頁"
        )
        if (homeKeywords.any { lower == it || lower.startsWith(it) }) {
            return true
        }

        // Default clean search engine homepages without active queries or path
        if (lower == "google.com" || lower == "www.google.com" || lower == "https://www.google.com" ||
            lower == "https://www.google.com/" || lower == "https://google.com" || lower == "https://google.com/" ||
            lower == "http://www.google.com" || lower == "http://google.com" ||
            lower == "bing.com" || lower == "www.bing.com" || lower == "https://www.bing.com" || lower == "https://www.bing.com/" ||
            lower == "duckduckgo.com" || lower == "https://duckduckgo.com" || lower == "https://duckduckgo.com/" ||
            lower == "yahoo.com" || lower == "www.yahoo.com" || lower == "https://www.yahoo.com" || lower == "https://www.yahoo.com/"
        ) {
            return true
        }

        return false
    }

    private suspend fun inspectNodeForBrowserUrl(
        root: AccessibilityNodeInfo,
        packageName: String,
        rules: List<com.example.data.model.BlockRule>,
        isAdultBlocked: Boolean,
        isImageVideoSearchBlocked: Boolean,
        isGoogleLensBlocked: Boolean = false
    ) {
        val candidateUrls = mutableListOf<String>()
        val candidateSearchQueries = mutableListOf<String>()
        var isBrowserAtHomeOrNewTab = false

        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.android.chrome:id/location_bar",
            "org.mozilla.firefox:id/toolbar_url",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )

        val allRoots = collectAllWindowRoots(root)
        for (winRoot in allRoots) {
            for (id in urlBarIds) {
                try {
                    val nodes = winRoot.findAccessibilityNodeInfosByViewId(id)
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        if (text.isBlank() || isBrowserHomeOrNewTab(text)) {
                            isBrowserAtHomeOrNewTab = true
                        } else {
                            candidateUrls.add(text)
                            candidateSearchQueries.add(text)
                        }
                        if (desc.isNotBlank()) {
                            if (isBrowserHomeOrNewTab(desc)) {
                                isBrowserAtHomeOrNewTab = true
                            } else {
                                candidateUrls.add(desc)
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // If the address bar is verified to be blank or on Home/New Tab, do not inspect page content
        if (isBrowserAtHomeOrNewTab && candidateUrls.isEmpty()) {
            return
        }

        // Deep traversal across all window roots (including Chrome's WebView tree)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        allRoots.forEach { queue.add(it) }
        var count = 0
        var isImageTabActive = false
        var isVideoTabActive = false
        var videoResultCount = 0
        var imageResultCount = 0

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val className = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val isNodeOrAncSelected = isNodeOrParentSelected(node)
            val childCombinedText = if (isNodeOrAncSelected && text.isBlank() && desc.isBlank()) {
                collectDirectChildrenText(node).lowercase()
            } else ""

            val lowerText = text.lowercase()
            val lowerDesc = desc.lowercase()
            val effectiveLabel = if (lowerText.isNotBlank()) lowerText else if (lowerDesc.isNotBlank()) lowerDesc else childCombinedText

            // Check extras for targetUrl (exposed by Chrome enhanced web accessibility)
            try {
                val extras = node.extras
                if (extras != null) {
                    for (key in extras.keySet()) {
                        if (key.contains("url", ignoreCase = true)) {
                            val extraVal = extras.getCharSequence(key)?.toString()?.trim()
                                ?: extras.getString(key)?.trim()
                            if (!extraVal.isNullOrBlank() && !isBrowserHomeOrNewTab(extraVal)) {
                                candidateUrls.add(extraVal)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            val isUrlOrSearchBox = className.contains("EditText", ignoreCase = true) ||
                    node.safeIsEditable() ||
                    viewId.contains("url") ||
                    viewId.contains("location_bar") ||
                    viewId.contains("address") ||
                    viewId.contains("omnibox") ||
                    viewId.contains("search_box") ||
                    lowerText.endsWith("- google search") ||
                    lowerDesc.endsWith("- google search") ||
                    lowerText.endsWith("- bing") ||
                    lowerText.endsWith("at duckduckgo")

            if (text.isNotBlank() && text.length >= 2 && !isBrowserHomeOrNewTab(text)) {
                if (isUrlOrSearchBox) {
                    candidateSearchQueries.add(text)
                    candidateUrls.add(text)
                } else if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
                    candidateUrls.add(text)
                }
            }
            if (desc.isNotBlank() && desc.length >= 2 && !isBrowserHomeOrNewTab(desc)) {
                if (isUrlOrSearchBox) {
                    candidateSearchQueries.add(desc)
                    candidateUrls.add(desc)
                } else if (desc.startsWith("http://", ignoreCase = true) || desc.startsWith("https://", ignoreCase = true)) {
                    candidateUrls.add(desc)
                }
            }

            // Check if Video or Image search tab is selected/active on web page in browser
            if (isImageVideoSearchBlocked) {
                val isVideoTabMatch = effectiveLabel == "videos" || effectiveLabel == "video" ||
                        effectiveLabel == "short videos" || effectiveLabel == "short video" ||
                        lowerText == "videos" || lowerText == "video" || lowerText == "short videos" ||
                        lowerDesc == "videos" || lowerDesc == "video" || lowerDesc == "short videos" ||
                        lowerDesc.contains("videos tab") || lowerDesc.contains("search for videos") ||
                        lowerDesc.contains("short videos tab") ||
                        lowerDesc.contains("videos filter") || lowerDesc.contains("videos, selected") ||
                        lowerDesc.contains("selected, videos") || lowerDesc.contains("videos (selected)") ||
                        lowerDesc.contains("current filter: videos") ||
                        (lowerDesc.contains("video") && (lowerDesc.contains("selected") || lowerDesc.contains("active") || lowerDesc.contains("current"))) ||
                        (lowerText.contains("video") && (lowerText.contains("selected") || lowerText.contains("active") || lowerText.contains("current")))

                val isImageTabMatch = effectiveLabel == "images" || effectiveLabel == "image" ||
                        lowerText == "images" || lowerText == "image" ||
                        lowerDesc == "images" || lowerDesc == "image" ||
                        lowerDesc.contains("images tab") || lowerDesc.contains("search for images") ||
                        lowerDesc.contains("images filter") || lowerDesc.contains("images, selected") ||
                        lowerDesc.contains("selected, images") || lowerDesc.contains("images (selected)") ||
                        lowerDesc.contains("current filter: images") ||
                        (lowerDesc.contains("image") && (lowerDesc.contains("selected") || lowerDesc.contains("active") || lowerDesc.contains("current"))) ||
                        (lowerText.contains("image") && (lowerText.contains("selected") || lowerText.contains("active") || lowerText.contains("current")))

                if (isVideoTabMatch && (isNodeOrAncSelected || lowerDesc.contains("selected") || lowerDesc.contains("current") || lowerText.contains("selected"))) {
                    isVideoTabActive = true
                }
                if (isImageTabMatch && (isNodeOrAncSelected || lowerDesc.contains("selected") || lowerDesc.contains("current") || lowerText.contains("selected"))) {
                    isImageTabActive = true
                }

                val isSupportedSearchEngineSite = candidateUrls.any { u ->
                    RuleEngine.isKnownSearchEngine(u)
                }

                if (isSupportedSearchEngineSite) {
                    if (lowerText.contains("youtube · ") || lowerText.contains("tiktok · ") || lowerText.contains("vimeo · ") ||
                        viewId.contains("video_card") || viewId.contains("video_result")
                    ) {
                        videoResultCount++
                    }

                    if (lowerText.contains("images may be subject to copyright") ||
                        lowerDesc.contains("image result") ||
                        viewId.contains("image_result") ||
                        viewId.contains("isch")
                    ) {
                        imageResultCount++
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }

        // Trigger if Video or Image search tab is active in browser on a supported search engine
        if (isImageVideoSearchBlocked) {
            val isSupportedSearchEngineSite = candidateUrls.any { u ->
                RuleEngine.isKnownSearchEngine(u)
            }

            if (isSupportedSearchEngineSite && (isVideoTabActive || videoResultCount >= 3)) {
                triggerIntervention(
                    target = "Video Search",
                    ruleType = RuleType.IMAGE_SEARCH,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked in search engines.",
                    appPackage = packageName
                )
                return
            }

            if (isSupportedSearchEngineSite && (isImageTabActive || imageResultCount >= 2)) {
                triggerIntervention(
                    target = "Image Search",
                    ruleType = RuleType.IMAGE_SEARCH,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked in search engines.",
                    appPackage = packageName
                )
                return
            }
        }

        val customKeywords = rules.filter { it.enabled && it.type == RuleType.KEYWORD }.map { it.target }

        for (urlText in candidateUrls) {
            // 0. Check Google Lens & Visual Search
            if (isGoogleLensBlocked) {
                val lensEval = RuleEngine.evaluateGoogleLens(
                    target = urlText,
                    isGoogleLensBlocked = true
                )
                if (lensEval.isBlocked) {
                    try {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    } catch (_: Throwable) {}
                    triggerIntervention(
                        target = "Google Scan & Lens",
                        ruleType = RuleType.IMAGE_SEARCH,
                        category = BlockCategory.IMAGE_VIDEO_SEARCH,
                        reason = lensEval.reason,
                        appPackage = packageName
                    )
                    return
                }
            }

            // 1. Check Image & Video Search & Unsupported Search Engines
            val imageSearchEval = RuleEngine.evaluateImageVideoSearch(
                urlOrQuery = urlText,
                isImageVideoSearchBlocked = isImageVideoSearchBlocked
            )
            if (imageSearchEval.isBlocked) {
                triggerIntervention(
                    target = if (imageSearchEval.reason.contains("Unsupported", ignoreCase = true)) "Unsupported Search Engine" else "Image & Video Search",
                    ruleType = RuleType.IMAGE_SEARCH,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = imageSearchEval.reason,
                    appPackage = packageName
                )
                return
            }

            // 2. Check Domain / Subdomain matching (Layer 2 500k+ Adult Blocking + Custom Rules)
            val domainEval = RuleEngine.evaluateDomain(
                domain = urlText,
                rules = rules,
                isTemporaryUnlocked = false,
                isAdultBlocked = isAdultBlocked
            )
            if (domainEval.isBlocked) {
                triggerIntervention(
                    target = urlText,
                    ruleType = RuleType.DOMAIN,
                    category = domainEval.category,
                    reason = domainEval.reason,
                    appPackage = packageName
                )
                return
            } else if (isAdultBlocked) {
                // Adult websites are blocked at the DNS layer via CleanBrowsing DNS (making them completely unreachable).
                // Do not rely on or trigger block screen (InterventionActivity) for adult website visits.
                val cleanDomain = RuleEngine.normalizeDomain(urlText)
                if (cleanDomain.contains('.') && !RuleEngine.isKnownSearchEngine(cleanDomain)) {
                    serviceScope.launch {
                        com.example.domain.engine.AdultBlockingEngine.isAdultDomainMultiLayer(cleanDomain)
                    }
                }
            }
        }

        // 3. Check Keyword Search across candidate search queries (Layer 3 Adult keywords + Custom user keywords)
        for (txt in candidateSearchQueries) {
            val searchEval = RuleEngine.evaluateSearchQuery(
                query = txt,
                isSearchProtectionEnabled = true,
                customKeywords = customKeywords,
                isTemporaryUnlocked = false,
                isAdultBlocked = isAdultBlocked
            )
            if (searchEval.isBlocked) {
                triggerIntervention(
                    target = "Blocked Search Query",
                    ruleType = RuleType.KEYWORD,
                    category = searchEval.category,
                    reason = searchEval.reason,
                    appPackage = packageName
                )
                return
            }
        }
    }

    private fun triggerInterventionWithReinforcement(
        target: String,
        ruleType: RuleType,
        category: BlockCategory,
        reason: String,
        appPackage: String? = null
    ) {
        if (isReclaimInForegroundOrPopup()) return
        triggerIntervention(
            target = target,
            ruleType = ruleType,
            category = category,
            reason = reason,
            appPackage = appPackage,
            bypassCooldown = true
        )
        // Reinforce after window transition settles so Settings sub-page launches cannot cover the Block Screen
        serviceScope.launch(Dispatchers.Main) {
            for (delayMs in listOf(110L, 160L, 200L)) {
                delay(delayMs)
                if (isReclaimInForegroundOrPopup()) return@launch
                val activePkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Throwable) { null }
                if (isOwnAppPackage(activePkg)) return@launch
                val isSettingsStillOnTop = activePkg != null && !isOwnAppPackage(activePkg) && isSettingsOrAccessibilityPkg(activePkg)
                if (!InterventionActivity.isShowing || isSettingsStillOnTop) {
                    if (isSettingsStillOnTop) {
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        } catch (_: Throwable) {}
                    }
                    try {
                        InterventionActivity.start(
                            context = this@SelfControlAccessibilityService,
                            target = target,
                            reason = reason,
                            packageName = appPackage,
                            bypassCooldown = true
                        )
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun triggerIntervention(
        target: String,
        ruleType: RuleType,
        category: BlockCategory,
        reason: String,
        appPackage: String? = null,
        bypassCooldown: Boolean = false
    ) {
        if (isReclaimInForegroundOrPopup()) {
            return
        }
        if (InterventionActivity.isShowing && !bypassCooldown) {
            return
        }
        if (!bypassCooldown && InterventionActivity.isInDismissCooldown(appPackage)) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (!bypassCooldown) {
            if (now - lastInterventionTime < 250L) {
                return
            }
        } else if (InterventionActivity.isShowing && now - lastInterventionTime < 60L) {
            return
        }
        lastInterventionTime = now
        lastBlockedPackage = appPackage

        serviceScope.launch {
            try {
                SelfControlApp.repository.recordBlockAttempt(
                    targetName = target,
                    ruleType = ruleType,
                    category = category
                )
            } catch (_: Throwable) {}
        }

        serviceScope.launch(Dispatchers.Main) {
            if (isReclaimInForegroundOrPopup()) return@launch
            try {
                InterventionActivity.start(
                    context = this@SelfControlAccessibilityService,
                    target = target,
                    reason = reason,
                    packageName = appPackage,
                    bypassCooldown = bypassCooldown
                )
            } catch (e: Throwable) {
                Log.e("AccessibilityService", "InterventionActivity.start failed: ${e.message}", e)
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Safely navigates the blocked application away from the forbidden tab (like YouTube Shorts,
     * Reels, WhatsApp Channels, or blocked browser URLs) before minimizing or closing.
     *
     * Multi-stage resilient approach:
     * 1. If in a browser, redirect the tab or close it / navigate back.
     * 2. Search bottom navigation bar or top navigation tabs for "Home", "Feed", "Chats", "Explore",
     *    or first/safe tab, and click it.
     * 3. Perform GLOBAL_ACTION_BACK to pop fullscreen player modal/backstack.
     * 4. Relaunch the app's clean root launcher activity with FLAG_ACTIVITY_CLEAR_TOP so it resets.
     */
    fun navigateCurrentAppToSafeView(targetPackage: String?) {
        val pkg = targetPackage ?: lastBlockedPackage ?: return

        if (isSettingsOrAccessibilityPkg(pkg)) {
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (_: Throwable) {}
            return
        }

        if (isBrowserPackage(pkg)) {
            ignorePackageTemporarily(pkg, 1500L)
            try {
                // Navigate browser away from blocked page to a clean blank tab
                val blankIntent = Intent(Intent.ACTION_VIEW, Uri.parse("about:blank")).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(blankIntent)
            } catch (_: Throwable) {
                try {
                    packageManager.getLaunchIntentForPackage(pkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(this)
                    }
                } catch (_: Throwable) {}
            }
            return
        }

        // 2. Search active window nodes for safe landing tabs (e.g. Home, Feed, Subscriptions, Chats)
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val safeKeywords = listOf(
                    "home", "inicio", "accueil", "feed", "subscriptions", 
                    "abonnements", "chats", "direct", "messages", "inbox"
                )
                
                var safeNodeClicked = false
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                var count = 0

                while (queue.isNotEmpty() && count < 80 && !safeNodeClicked) {
                    val node = queue.removeFirst()
                    count++

                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    val text = node.text?.toString()?.lowercase() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""

                    val isSafeTab = safeKeywords.any { kw ->
                        desc.contains(kw) || text.contains(kw) || viewId.contains(kw)
                    }

                    // Ensure we do NOT click Shorts / Reels / Channels tabs
                    val isUnsafeTab = desc.contains("shorts") || text.contains("shorts") ||
                                      desc.contains("reel") || text.contains("reel") ||
                                      desc.contains("channel") || text.contains("channel")

                    if (isSafeTab && !isUnsafeTab) {
                        // Find clickable node (either this node or its clickable parent)
                        var clickable: AccessibilityNodeInfo? = node
                        while (clickable != null && !clickable.isClickable) {
                            clickable = clickable.parent
                        }
                        if (clickable != null && clickable.isClickable) {
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            safeNodeClicked = true
                            break
                        }
                    }

                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) queue.add(child)
                    }
                }

                // If no standard Home/Feed tab keyword was matched in the bottom bar,
                // find the very first tab in the bottom bar (usually the primary feed) and click it!
                if (!safeNodeClicked) {
                    val tabQueue = ArrayDeque<AccessibilityNodeInfo>()
                    tabQueue.add(root)
                    var tabCount = 0
                    while (tabQueue.isNotEmpty() && tabCount < 80) {
                        val node = tabQueue.removeFirst()
                        tabCount++
                        val className = node.className?.toString() ?: ""
                        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                        val text = node.text?.toString()?.lowercase() ?: ""
                        val isUnsafe = desc.contains("shorts") || text.contains("shorts") ||
                                       desc.contains("reel") || text.contains("reel") ||
                                       desc.contains("channel") || text.contains("channel")

                        if (!isUnsafe && (className.contains("Tab") || className.contains("BottomNavigation") || node.safeIsCheckable() || !node.safeIsSelected())) {
                            var clickable: AccessibilityNodeInfo? = node
                            while (clickable != null && !clickable.safeIsClickable()) {
                                clickable = clickable.parent
                            }
                            if (clickable != null && clickable.safeIsClickable()) {
                                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                safeNodeClicked = true
                                break
                            }
                        }

                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i)
                            if (child != null) tabQueue.add(child)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        maintenanceJob?.cancel()
        try {
            packageInstallReceiver?.let { unregisterReceiver(it) }
        } catch (_: Throwable) {}
        packageInstallReceiver = null
        isRunning = false
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun getHomeLauncherPackage(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }

    @Volatile
    private var homeLauncherCache: Set<String>? = null
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun getAllHomeLauncherPackages(): Set<String> {
        homeLauncherCache?.let { return it }
        val set = mutableSetOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.vivo.launcher",
            "com.oneplus.launcher"
        )
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val list = packageManager.queryIntentActivities(intent, 0)
            for (info in list) {
                info.activityInfo?.packageName?.let { set.add(it) }
            }
            getHomeLauncherPackage()?.let { set.add(it) }
        } catch (_: Exception) {}
        homeLauncherCache = set
        return set
    }

    private fun getAppLabel(packageName: String): String {
        return appLabelCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                packageName
            }
        }
    }
}

private fun AccessibilityNodeInfo?.safeIsSelected(): Boolean {
    if (this == null) return false
    return try {
        this.isSelected
    } catch (_: Throwable) {
        false
    }
}

private fun AccessibilityNodeInfo?.safeIsChecked(): Boolean {
    if (this == null) return false
    return try {
        this.isChecked
    } catch (_: Throwable) {
        false
    }
}

private fun AccessibilityNodeInfo?.safeIsFocused(): Boolean {
    if (this == null) return false
    return try {
        this.isFocused
    } catch (_: Throwable) {
        false
    }
}

private fun AccessibilityNodeInfo?.safeIsCheckable(): Boolean {
    if (this == null) return false
    return try {
        this.isCheckable
    } catch (_: Throwable) {
        false
    }
}

private fun AccessibilityNodeInfo?.safeIsClickable(): Boolean {
    if (this == null) return false
    return try {
        this.isClickable
    } catch (_: Throwable) {
        false
    }
}

private fun AccessibilityNodeInfo?.safeIsEditable(): Boolean {
    if (this == null) return false
    return try {
        this.isEditable
    } catch (_: Throwable) {
        false
    }
}

