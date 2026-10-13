package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SelfControlApp
import com.example.data.firebase.PartnerUnlockRequest
import com.example.data.firebase.SyncState
import com.example.data.local.DistractionCount
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.FocusSession
import com.example.data.model.RuleType
import com.example.domain.engine.AccountabilityHelper
import com.example.domain.engine.AccountabilityLockStatus
import com.example.domain.engine.LockedActionTarget
import com.example.service.focus.FocusModeService
import com.example.service.vpn.SelfControlVpnService
import com.example.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SelfControlApp.repository
    private val prefs = repository.preferences

    val allRules: StateFlow<List<BlockRule>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val installedAppRules: StateFlow<List<BlockRule>> = allRules.map { rules ->
        rules.filter { it.type == RuleType.APP }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val domainRules: StateFlow<List<BlockRule>> = allRules.map { rules ->
        rules.filter { it.type == RuleType.DOMAIN }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val keywordRules: StateFlow<List<BlockRule>> = allRules.map { rules ->
        rules.filter { it.type == RuleType.KEYWORD }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allAttempts = repository.getAllAttempts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val todayAttempts = repository.getAttemptsSince(repository.getStartOfToday())
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topDistractions: StateFlow<List<DistractionCount>> = repository.getTopDistractions(5)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeSession: StateFlow<FocusSession?> = repository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allSessions: StateFlow<List<FocusSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalTimeReclaimedMinutes: StateFlow<Long> = combine(
        repository.getAllAttempts(),
        repository.getAllSessions()
    ) { attempts, sessions ->
        val urgeMinutes = attempts.size * 15L
        val focusMinutes = sessions.filter { it.isCompleted }.sumOf { it.completedMinutes.toLong() }
        urgeMinutes + focusMinutes
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val allFocusSchedules: StateFlow<List<com.example.data.model.FocusSchedule>> = repository.getAllFocusSchedules()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val focusTimezone: StateFlow<String> = prefs.focusTimezone
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val onboardingCompleted: StateFlow<Boolean> = prefs.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val masterProtection: StateFlow<Boolean> = prefs.masterProtection
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val vpnEnabled: StateFlow<Boolean> = prefs.vpnEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val accessibilityEnabled: StateFlow<Boolean> = prefs.accessibilityEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Specific Content Protection Toggles
    val adultContentBlocked: StateFlow<Boolean> = prefs.adultContentBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val imageVideoSearchBlocked: StateFlow<Boolean> = prefs.imageVideoSearchBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val googleLensBlocked: StateFlow<Boolean> = prefs.googleLensBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val youtubeShortsBlocked: StateFlow<Boolean> = prefs.youtubeShortsBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val whatsappChannelsBlocked: StateFlow<Boolean> = prefs.whatsappChannelsBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val instagramReelsBlocked: StateFlow<Boolean> = prefs.instagramReelsBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val facebookReelsBlocked: StateFlow<Boolean> = prefs.facebookReelsBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Advanced Protection Toggles
    val uninstallProtection: StateFlow<Boolean> = prefs.uninstallProtection
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val uninstallProtectionUntil: StateFlow<Long> = prefs.uninstallProtectionUntil
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val uninstallProtectionDurationDays: StateFlow<Int> = prefs.uninstallProtectionDurationDays
        .stateIn(viewModelScope, SharingStarted.Lazily, 1)

    val unsupportedBrowsersBlocked: StateFlow<Boolean> = prefs.unsupportedBrowsersBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val newAppsBlocked: StateFlow<Boolean> = prefs.newAppsBlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)


    val safeSearchEnforced: StateFlow<Boolean> = prefs.safeSearchEnforced
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val audioChimeEnabled: StateFlow<Boolean> = prefs.audioChimeEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Blocked Screen Customization
    val customBlockedMessage: StateFlow<String> = prefs.customBlockedMessage
        .stateIn(viewModelScope, SharingStarted.Lazily, "Take a breath. You chose to block this to stay focused.")

    val blockedCountdownSeconds: StateFlow<Int> = prefs.blockedCountdownSeconds
        .stateIn(viewModelScope, SharingStarted.Lazily, 15)

    val redirectUrl: StateFlow<String> = prefs.redirectUrl
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val blockedMediaType: StateFlow<String> = prefs.blockedMediaType
        .stateIn(viewModelScope, SharingStarted.Lazily, "BREATHING")

    val blockScreenBgType: StateFlow<String> = prefs.blockScreenBgType
        .stateIn(viewModelScope, SharingStarted.Lazily, "DEFAULT")

    val blockScreenMediaPath: StateFlow<String> = prefs.blockScreenMediaPath
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val blockScreenTitle: StateFlow<String> = prefs.blockScreenTitle
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val blockScreenSubtitle: StateFlow<String> = prefs.blockScreenSubtitle
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val blockScreenButtonText: StateFlow<String> = prefs.blockScreenButtonText
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val blockScreenAlignment: StateFlow<String> = prefs.blockScreenAlignment
        .stateIn(viewModelScope, SharingStarted.Lazily, "CENTER")

    val blockScreenShowStreak: StateFlow<Boolean> = prefs.blockScreenShowStreak
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowLock: StateFlow<Boolean> = prefs.blockScreenShowLock
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowTitle: StateFlow<Boolean> = prefs.blockScreenShowTitle
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowSubtitle: StateFlow<Boolean> = prefs.blockScreenShowSubtitle
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowButton: StateFlow<Boolean> = prefs.blockScreenShowButton
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowSpeaker: StateFlow<Boolean> = prefs.blockScreenShowSpeaker
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowTimer: StateFlow<Boolean> = prefs.blockScreenShowTimer
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowTagline: StateFlow<Boolean> = prefs.blockScreenShowTagline
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenShowDarkOverlay: StateFlow<Boolean> = prefs.blockScreenShowDarkOverlay
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val persistentBlockScreen: StateFlow<Boolean> = prefs.persistentBlockScreen
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blockScreenVideoMuted: StateFlow<Boolean> = prefs.blockScreenVideoMuted
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val blockScreenMediaList: StateFlow<List<com.example.data.local.BlockMediaItem>> = prefs.blockScreenMediaList
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val dailyStreak: StateFlow<Int> = prefs.dailyStreak
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val bestStreakDays: StateFlow<Int> = prefs.bestStreakDays
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val streakStartTimestamp: StateFlow<Long> = prefs.streakStartTimestamp
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val lastRelapseTimestamp: StateFlow<Long> = prefs.lastRelapseTimestamp
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    private val _currentLiveTimeMs = MutableStateFlow(System.currentTimeMillis())
    val currentLiveTimeMs: StateFlow<Long> = _currentLiveTimeMs.asStateFlow()

    val currentUser: StateFlow<com.example.data.firebase.AuthUser?> = repository.authManager.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.authManager.getCurrentUser())

    val syncState: StateFlow<SyncState> = repository.syncState

    val isOnline: StateFlow<Boolean> = repository.isOnline

    val noInternetWarning: StateFlow<String?> = repository.noInternetWarning

    val pendingPartnerRequest: StateFlow<PartnerUnlockRequest?> = repository.pendingPartnerRequest

    fun dismissNoInternetWarning() {
        repository.dismissNoInternetWarning()
    }

    fun checkInternetForMustOnlineFeature(): Boolean {
        return repository.checkInternetForMustOnlineFeature()
    }

    val activePartnerRequests: StateFlow<Map<String, PartnerUnlockRequest>> = repository.activePartnerRequests
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allHabits: StateFlow<List<com.example.data.model.Habit>> = repository.getAllHabits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allJournalEntries: StateFlow<List<com.example.data.model.JournalEntry>> = repository.getAllJournalEntries()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tempUnlockSeconds = MutableStateFlow(0)
    val tempUnlockSeconds: StateFlow<Int> = _tempUnlockSeconds.asStateFlow()

    // Accountability state flows
    val accountabilityEnabled: StateFlow<Boolean> = prefs.accountabilityEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val accountabilityStartHour: StateFlow<Int> = prefs.accountabilityStartHour
        .stateIn(viewModelScope, SharingStarted.Lazily, 12)

    val accountabilityStartMinute: StateFlow<Int> = prefs.accountabilityStartMinute
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val accountabilityDurationMinutes: StateFlow<Int> = prefs.accountabilityDurationMinutes
        .stateIn(viewModelScope, SharingStarted.Lazily, 30)

    val accountabilityPartnerName: StateFlow<String> = prefs.accountabilityPartnerName
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val accountabilityPartnerEmail: StateFlow<String> = prefs.accountabilityPartnerEmail
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val accountabilityNote: StateFlow<String> = prefs.accountabilityNote
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _isAccountabilityWindowOpen = MutableStateFlow(false)
    val isAccountabilityWindowOpen: StateFlow<Boolean> = _isAccountabilityWindowOpen.asStateFlow()

    private val _accountabilityTimeUntilText = MutableStateFlow("")
    val accountabilityTimeUntilText: StateFlow<String> = _accountabilityTimeUntilText.asStateFlow()

    private val _accountabilityRemainingMinutes = MutableStateFlow(0)
    val accountabilityRemainingMinutes: StateFlow<Int> = _accountabilityRemainingMinutes.asStateFlow()

    private val _accountabilityLockDialogState = MutableStateFlow<AccountabilityLockStatus?>(null)
    val accountabilityLockDialogState: StateFlow<AccountabilityLockStatus?> = _accountabilityLockDialogState.asStateFlow()

    private val _accountabilityRequiredDialogState = MutableStateFlow<String?>(null)
    val accountabilityRequiredDialogState: StateFlow<String?> = _accountabilityRequiredDialogState.asStateFlow()

    fun showAccountabilityRequiredDialog(actionName: String) {
        _accountabilityRequiredDialogState.value = actionName
    }

    fun dismissAccountabilityRequiredDialog() {
        _accountabilityRequiredDialogState.value = null
    }

    fun checkAccountabilityPrerequisite(actionName: String): Boolean {
        if (!accountabilityEnabled.value) {
            _accountabilityRequiredDialogState.value = actionName
            return false
        }
        return true
    }

    init {
        // Initialize default streak if not already set so day and time counting starts immediately
        viewModelScope.launch {
            val start = prefs.streakStartTimestamp.first()
            if (start == 0L) {
                prefs.setStreakStartTime(System.currentTimeMillis())
            }
        }

        // Monitor temporary unlock countdown, uninstall timer expiry, and live time ticker
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _currentLiveTimeMs.value = now
                _tempUnlockSeconds.value = prefs.getRemainingUnlockSeconds()

                // Auto-unblock Uninstall Protection when stated duration (1 day or custom days) finishes
                val until = uninstallProtectionUntil.value
                if (until > 0L && now >= until) {
                    prefs.checkAndExpireUninstallProtection()
                }

                _isAccountabilityWindowOpen.value = false
                _accountabilityRemainingMinutes.value = 0
                _accountabilityTimeUntilText.value = ""
                delay(1000L)
            }
        }
    }

    fun checkAccountabilityLock(lockedTarget: LockedActionTarget? = null): AccountabilityLockStatus {
        if (!accountabilityEnabled.value) {
            return AccountabilityLockStatus(isLocked = false, lockedTarget = lockedTarget)
        }
        return AccountabilityLockStatus(
            isLocked = true,
            windowRangeText = "",
            timeUntilNextWindowText = "",
            selectedTimeText = "",
            lockedTarget = lockedTarget
        )
    }

    fun dismissAccountabilityLockDialog() {
        _accountabilityLockDialogState.value = null
    }

    fun triggerAccountabilityLockDialog(status: AccountabilityLockStatus? = null) {
        _accountabilityLockDialogState.value = status ?: checkAccountabilityLock()
    }

    fun triggerLockedItemDialog(target: LockedActionTarget) {
        val status = checkAccountabilityLock(target)
        _accountabilityLockDialogState.value = status.copy(isLocked = true, lockedTarget = target)
    }

    fun startFocusSession(durationMinutes: Int) {
        if (!checkAccountabilityPrerequisite("Focus Session")) return
        FocusModeService.startFocus(getApplication(), durationMinutes)
    }

    fun stopFocusSession() {
        FocusModeService.stopFocus(getApplication())
    }

    fun toggleRule(ruleId: Long, enabled: Boolean): Boolean {
        val rule = allRules.value.find { it.id == ruleId }
        val ruleLabel = rule?.name?.ifBlank { rule.target } ?: "Rule #$ruleId"
        val actionKey = "RULE_DISABLE_$ruleId"
        if (enabled) {
            if (!checkAccountabilityPrerequisite("Block Rule")) return false
        } else {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(
                    actionKey = actionKey,
                    actionTitle = "Disable Block: $ruleLabel",
                    targetType = "DISABLE_RULE",
                    ruleId = ruleId
                )
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return false
            }
        }
        viewModelScope.launch {
            repository.setRuleEnabled(ruleId, enabled)
            if (enabled) {
                repository.clearPartnerRequestForAction(actionKey)
                repository.clearPartnerRequestForAction("RULE_DELETE_$ruleId")
            }
        }
        return true
    }

    fun toggleCategory(category: BlockCategory, enabled: Boolean): Boolean {
        val actionKey = "CATEGORY_DISABLE_${category.name}"
        if (enabled) {
            if (!checkAccountabilityPrerequisite("Category Blocking")) return false
        } else {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(
                    actionKey = actionKey,
                    actionTitle = "Disable Category: ${category.name}",
                    targetType = "SINGLE_ACTION"
                )
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return false
            }
        }
        viewModelScope.launch {
            repository.setCategoryEnabled(category, enabled)
            if (enabled) {
                repository.clearPartnerRequestForAction(actionKey)
            }
        }
        return true
    }

    fun addCustomDomain(domain: String) {
        if (!checkAccountabilityPrerequisite("Website Block")) return
        viewModelScope.launch {
            repository.insertRule(
                BlockRule(
                    name = "Site: $domain",
                    type = RuleType.DOMAIN,
                    target = domain,
                    category = BlockCategory.CUSTOM,
                    enabled = true,
                    priority = 90
                )
            )
        }
    }

    fun addCustomWebsite(domain: String) = addCustomDomain(domain)

    fun addKeywordRule(keyword: String) {
        if (!checkAccountabilityPrerequisite("Keyword Block")) return
        viewModelScope.launch {
            repository.insertRule(
                BlockRule(
                    name = "Keyword: $keyword",
                    type = RuleType.KEYWORD,
                    target = keyword,
                    category = BlockCategory.KEYWORDS,
                    enabled = true,
                    priority = 90
                )
            )
        }
    }

    fun addCustomKeyword(keyword: String) = addKeywordRule(keyword)

    fun addAppRule(packageName: String, appName: String? = null) {
        if (!checkAccountabilityPrerequisite("App Block")) return
        viewModelScope.launch {
            val displayName = appName ?: packageName
            repository.insertRule(
                BlockRule(
                    name = "App: $displayName",
                    type = RuleType.APP,
                    target = packageName,
                    category = BlockCategory.CUSTOM,
                    enabled = true,
                    priority = 90
                )
            )
        }
    }

    fun addCustomApp(packageName: String, appName: String? = null) = addAppRule(packageName, appName)

    fun addWhitelistDomain(domain: String) {
        viewModelScope.launch {
            repository.insertRule(
                BlockRule(
                    name = "Allowed: $domain",
                    type = RuleType.DOMAIN,
                    target = domain,
                    category = BlockCategory.CUSTOM,
                    enabled = true,
                    isException = true,
                    priority = 200
                )
            )
        }
    }

    fun addWhitelistApp(packageName: String) {
        viewModelScope.launch {
            repository.insertRule(
                BlockRule(
                    name = "Allowed App: $packageName",
                    type = RuleType.APP,
                    target = packageName,
                    category = BlockCategory.CUSTOM,
                    enabled = true,
                    isException = true,
                    priority = 200
                )
            )
        }
    }

    fun deleteRule(ruleId: Long): Boolean {
        val rule = allRules.value.find { it.id == ruleId }
        val ruleLabel = rule?.name?.ifBlank { rule.target } ?: "Rule #$ruleId"
        val lockStatus = checkAccountabilityLock(
            LockedActionTarget(
                actionKey = "RULE_DELETE_$ruleId",
                actionTitle = "Delete Block: $ruleLabel",
                targetType = "DELETE_RULE",
                ruleId = ruleId
            )
        )
        if (lockStatus.isLocked) {
            _accountabilityLockDialogState.value = lockStatus
            return false
        }
        viewModelScope.launch {
            repository.deleteRuleById(ruleId)
            repository.clearPartnerRequestForAction("RULE_DELETE_$ruleId")
            repository.clearPartnerRequestForAction("RULE_DISABLE_$ruleId")
        }
        return true
    }

    fun requestTemporaryUnlock(durationMinutes: Int) {
        viewModelScope.launch {
            prefs.setTemporaryUnlock(durationMinutes)
        }
    }

    fun cancelTemporaryUnlock() {
        viewModelScope.launch {
            prefs.clearTemporaryUnlock()
        }
    }

    fun setMasterProtection(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMasterProtection(enabled)
        }
    }

    fun setVpnEnabled(enabled: Boolean) {
        val actionKey = "ADV_VPN_FILTER"
        if (enabled) {
            if (!checkAccountabilityPrerequisite("DNS Content Filter")) return
        } else {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off DNS Content Filter")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setVpnEnabled(enabled)
            if (enabled) {
                repository.clearPartnerRequestForAction(actionKey)
                SelfControlVpnService.startVpn(getApplication())
            } else {
                SelfControlVpnService.stopVpn(getApplication())
            }
        }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        val actionKey = "ADV_ACCESSIBILITY"
        if (enabled) {
            if (!checkAccountabilityPrerequisite("Accessibility Blocker")) return
        } else {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off Accessibility Blocker")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setAccessibilityEnabled(enabled)
            if (enabled) {
                repository.clearPartnerRequestForAction(actionKey)
            }
        }
    }

    fun setAdultContentBlocked(blocked: Boolean) {
        val actionKey = "FILTER_ADULT_CONTENT"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Adult Content'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setAdultContentBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setImageVideoSearchBlocked(blocked: Boolean) {
        val actionKey = "FILTER_IMAGE_VIDEO_SEARCH"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Image & Video Search'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setImageVideoSearchBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setGoogleLensBlocked(blocked: Boolean) {
        val actionKey = "FILTER_GOOGLE_LENS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Google Scan & Lens'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setGoogleLensBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setYoutubeShortsBlocked(blocked: Boolean) {
        val actionKey = "FILTER_YOUTUBE_SHORTS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block YouTube Shorts'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setYoutubeShortsBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setWhatsappChannelsBlocked(blocked: Boolean) {
        val actionKey = "FILTER_WHATSAPP_CHANNELS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block WhatsApp Channels'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setWhatsappChannelsBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setInstagramReelsBlocked(blocked: Boolean) {
        val actionKey = "FILTER_INSTAGRAM_REELS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Instagram Reels'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setInstagramReelsBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setFacebookReelsBlocked(blocked: Boolean) {
        val actionKey = "FILTER_FACEBOOK_REELS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Facebook Reels'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setFacebookReelsBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setAllContentFilters(enabled: Boolean) {
        val actionKey = "FILTER_ALL_CONTENT"
        if (!enabled) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off All Content Filters")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setAdultContentBlocked(enabled)
            prefs.setYoutubeShortsBlocked(enabled)
            prefs.setInstagramReelsBlocked(enabled)
            prefs.setFacebookReelsBlocked(enabled)
            prefs.setImageVideoSearchBlocked(enabled)
            prefs.setWhatsappChannelsBlocked(enabled)
            if (enabled) {
                repository.clearPartnerRequestForAction(actionKey)
                repository.clearPartnerRequestForAction("FILTER_ADULT_CONTENT")
                repository.clearPartnerRequestForAction("FILTER_YOUTUBE_SHORTS")
                repository.clearPartnerRequestForAction("FILTER_INSTAGRAM_REELS")
                repository.clearPartnerRequestForAction("FILTER_FACEBOOK_REELS")
                repository.clearPartnerRequestForAction("FILTER_IMAGE_VIDEO_SEARCH")
                repository.clearPartnerRequestForAction("FILTER_WHATSAPP_CHANNELS")
            }
        }
    }

    fun activateUninstallProtection(durationDays: Int) {
        prefs.setUninstallProtectionConfigSync(enabled = true, durationDays = durationDays)
        viewModelScope.launch {
            prefs.setUninstallProtectionConfig(enabled = true, durationDays = durationDays)
            repository.clearPartnerRequestForAction("ADV_UNINSTALL_PROTECTION")
        }
    }

    fun tryDisableUninstallProtection(): Boolean {
        val now = System.currentTimeMillis()
        val until = uninstallProtectionUntil.value
        val isAccEnabled = accountabilityEnabled.value
        val lockStatus = checkAccountabilityLock(
            LockedActionTarget(
                actionKey = "ADV_UNINSTALL_PROTECTION",
                actionTitle = "Turn Off 'Prevent Uninstall'"
            )
        )

        val isTimerFinished = until <= 0L || now >= until

        // When Accountability is enabled, changing or disabling Prevent Uninstall requires partner code
        if (isAccEnabled && lockStatus.isLocked) {
            _accountabilityLockDialogState.value = lockStatus
            return false
        }

        if (!isTimerFinished) {
            _accountabilityLockDialogState.value = lockStatus
            return false
        }

        prefs.setUninstallProtectionConfigSync(enabled = false, durationDays = 1)
        viewModelScope.launch {
            prefs.setUninstallProtectionConfig(enabled = false, durationDays = 1)
        }
        return true
    }

    fun setUninstallProtection(enabled: Boolean, durationDays: Int = 1) {
        if (enabled) {
            prefs.setUninstallProtectionConfigSync(enabled = true, durationDays = durationDays)
            viewModelScope.launch {
                prefs.setUninstallProtectionConfig(enabled = true, durationDays = durationDays)
                repository.clearPartnerRequestForAction("ADV_UNINSTALL_PROTECTION")
            }
            // Request battery optimization exemption to prevent Samsung/OEM battery savers
            // from killing the app process and revoking Device Admin when swiped from recents
            if (!PermissionHelper.isBatteryOptimizationExempt(getApplication())) {
                PermissionHelper.requestBatteryOptimizationExemption(getApplication())
            }
        } else {
            tryDisableUninstallProtection()
        }
    }

    fun setUnsupportedBrowsersBlocked(blocked: Boolean) {
        val actionKey = "ADV_UNSUPPORTED_BROWSERS"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block Unsupported Browsers'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setUnsupportedBrowsersBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setNewAppsBlocked(blocked: Boolean) {
        val actionKey = "ADV_NEW_APPS_BLOCKED"
        if (!blocked) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'Block New App Installs'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setNewAppsBlocked(blocked)
            if (blocked) repository.clearPartnerRequestForAction(actionKey)
        }
    }


    fun setSafeSearchEnforced(enforced: Boolean) {
        val actionKey = "ADV_SAFESEARCH"
        if (enforced) {
            if (!checkAccountabilityPrerequisite("SafeSearch Enforcer")) return
        } else {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off 'SafeSearch Enforcer'")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setSafeSearchEnforced(enforced)
            if (enforced) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setAudioChimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAudioChimeEnabled(enabled)
        }
    }

    fun setPersistentBlockScreen(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPersistentBlockScreen(enabled)
        }
    }

    // Accountability options actions
    fun setAccountabilityEnabled(enabled: Boolean) {
        val actionKey = "ACC_DISABLE_MODE"
        if (!enabled) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(actionKey = actionKey, actionTitle = "Turn Off Accountability Mode")
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setAccountabilityEnabled(enabled)
            if (enabled) repository.clearPartnerRequestForAction(actionKey)
        }
    }

    fun setAccountabilitySchedule(startHour: Int, startMinute: Int, durationMinutes: Int) {
        val actionKey = "ACC_CHANGE_SCHEDULE"
        if (accountabilityEnabled.value) {
            val newRange = AccountabilityHelper.formatWindowRange(startHour, startMinute, durationMinutes)
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(
                    actionKey = actionKey,
                    actionTitle = "Change Unlock Window to $newRange",
                    targetType = "CHANGE_SCHEDULE",
                    payload = "$startHour:$startMinute:$durationMinutes"
                )
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return
            }
        }
        viewModelScope.launch {
            prefs.setAccountabilitySchedule(startHour, startMinute, durationMinutes)
            repository.clearPartnerRequestForAction(actionKey)
        }
    }

    /**
     * Updates Partner details. If a Partner Email is already set and Accountability is currently locked
     * outside the change window, changing the email address requires a 1-time verification code sent
     * to the current partner email first.
     */
    fun setAccountabilityPartner(name: String, email: String, note: String): Boolean {
        if (!repository.checkInternetForMustOnlineFeature()) {
            return false
        }
        val currentEmail = accountabilityPartnerEmail.value.trim()
        val cleanNewEmail = email.trim()
        val isChangingExistingEmail = currentEmail.isNotBlank() &&
                !currentEmail.equals(cleanNewEmail, ignoreCase = true)

        if (isChangingExistingEmail && accountabilityEnabled.value) {
            val lockStatus = checkAccountabilityLock(
                LockedActionTarget(
                    actionKey = "ACC_CHANGE_PARTNER_EMAIL",
                    actionTitle = "Change Partner Email to ${cleanNewEmail.ifBlank { "None" }}",
                    targetType = "CHANGE_PARTNER_EMAIL",
                    payload = "${name.trim()}|$cleanNewEmail|${note.trim()}"
                )
            )
            if (lockStatus.isLocked) {
                _accountabilityLockDialogState.value = lockStatus
                return false
            }
        }

        viewModelScope.launch {
            prefs.setAccountabilityPartner(name.trim(), cleanNewEmail, note.trim())
            repository.clearPartnerRequestForAction("ACC_CHANGE_PARTNER_EMAIL")
            repository.triggerCloudSyncDebounced()
        }
        return true
    }

    fun sendAccountabilityPartnerRequest(
        actionKey: String = "SINGLE_ACTION",
        targetType: String = "SINGLE_ACTION",
        targetDetail: String,
        ruleId: Long? = null,
        payload: String = "",
        reason: String,
        onResult: (Result<PartnerUnlockRequest>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.sendAccountabilityPartnerRequest(
                actionKey = actionKey,
                targetType = targetType,
                targetDetail = targetDetail,
                ruleId = ruleId,
                payload = payload,
                reason = reason
            )
            onResult(res)
        }
    }

    fun verifyPartnerUnlockCode(
        actionKey: String,
        enteredCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.verifyAndApplyPartnerCode(actionKey, enteredCode)
            if (res.isSuccess) {
                val req = res.getOrNull()
                onResult(true, "Unlocked: ${req?.targetDetail ?: "Item"}")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Invalid verification code.")
            }
        }
    }

    fun clearPendingPartnerRequest() {
        viewModelScope.launch {
            repository.clearPendingPartnerRequest()
        }
    }

    fun setCustomBlockedMessage(message: String) {
        viewModelScope.launch {
            prefs.setCustomBlockedMessage(message)
        }
    }

    fun setBlockedCountdownSeconds(seconds: Int) {
        viewModelScope.launch {
            prefs.setBlockedCountdownSeconds(seconds)
        }
    }

    fun setRedirectUrl(url: String) {
        viewModelScope.launch {
            prefs.setRedirectUrl(url)
        }
    }

    fun setBlockedMediaType(type: String) {
        viewModelScope.launch {
            prefs.setBlockedMediaType(type)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
        }
    }

    // Habits actions
    fun toggleHabit(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setHabitCompleted(id, completed)
        }
    }

    fun addHabit(title: String, duration: String) {
        viewModelScope.launch {
            repository.insertHabit(
                com.example.data.model.Habit(
                    title = title,
                    targetDuration = duration,
                    isCompletedToday = false
                )
            )
        }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch {
            repository.deleteHabitById(id)
        }
    }

    // Journal actions
    fun addJournalEntry(mood: String, note: String) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date())
            repository.insertJournalEntry(
                com.example.data.model.JournalEntry(
                    dateText = dateStr,
                    mood = mood,
                    note = note
                )
            )
        }
    }

    fun deleteJournalEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteJournalEntryById(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    fun addFocusSchedule(schedule: com.example.data.model.FocusSchedule) {
        viewModelScope.launch {
            repository.insertFocusSchedule(schedule)
        }
    }

    fun updateFocusSchedule(schedule: com.example.data.model.FocusSchedule) {
        viewModelScope.launch {
            repository.updateFocusSchedule(schedule)
        }
    }

    fun toggleFocusSchedule(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setFocusScheduleEnabled(scheduleId, enabled)
        }
    }

    fun deleteFocusSchedule(schedule: com.example.data.model.FocusSchedule) {
        viewModelScope.launch {
            repository.deleteFocusSchedule(schedule)
        }
    }

    fun startInstantFocus(context: android.content.Context, durationMinutes: Int) {
        com.example.service.focus.FocusModeService.startFocus(context, durationMinutes)
    }

    fun stopInstantFocus(context: android.content.Context) {
        com.example.service.focus.FocusModeService.stopFocus(context)
    }

    fun resetStreak(reason: String = "", mood: String = "Reflective") {
        viewModelScope.launch {
            repository.resetStreak(reason, mood)
        }
    }

    fun signInWithGoogle(
        activityContext: android.content.Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.signInWithGoogle(activityContext)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Google Sign In failed")
            }
        }
    }

    fun signOut() {
        repository.authManager.signOut()
    }

    fun deleteAccountAndData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAccountAndAllData()
            onComplete()
        }
    }

    fun syncDataToCloud() {
        viewModelScope.launch {
            repository.syncDataToCloud(isManual = true)
        }
    }

    fun restoreDataFromCloud() {
        viewModelScope.launch {
            repository.restoreDataFromCloud(isManual = true)
        }
    }

    fun updateBlockScreenCustomization(
        bgType: String? = null,
        mediaPath: String? = null,
        title: String? = null,
        subtitle: String? = null,
        buttonText: String? = null,
        alignment: String? = null,
        showStreak: Boolean? = null,
        showLock: Boolean? = null,
        showTitle: Boolean? = null,
        showSubtitle: Boolean? = null,
        showButton: Boolean? = null,
        showSpeaker: Boolean? = null,
        showTimer: Boolean? = null,
        showTagline: Boolean? = null,
        showDarkOverlay: Boolean? = null,
        videoMuted: Boolean? = null
    ) {
        viewModelScope.launch {
            prefs.setBlockScreenCustomization(
                bgType = bgType,
                mediaPath = mediaPath,
                title = title,
                subtitle = subtitle,
                buttonText = buttonText,
                alignment = alignment,
                showStreak = showStreak,
                showLock = showLock,
                showTitle = showTitle,
                showSubtitle = showSubtitle,
                showButton = showButton,
                showSpeaker = showSpeaker,
                showTimer = showTimer,
                showTagline = showTagline,
                showDarkOverlay = showDarkOverlay,
                videoMuted = videoMuted
            )
        }
    }

    fun setBlockScreenVideoMuted(muted: Boolean) {
        viewModelScope.launch {
            prefs.setBlockScreenVideoMuted(muted)
        }
    }

    fun setBlockScreenShowDarkOverlay(show: Boolean) {
        viewModelScope.launch {
            prefs.setBlockScreenShowDarkOverlay(show)
        }
    }

    fun resetBlockScreenCustomization() {
        viewModelScope.launch {
            val currentPath = prefs.blockScreenMediaPath.first()
            if (currentPath.isNotBlank()) {
                try {
                    val file = java.io.File(currentPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            prefs.resetBlockScreenCustomization()
        }
    }

    fun removeBlockMediaItem(item: com.example.data.local.BlockMediaItem) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(item.path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            prefs.removeBlockMediaItem(item.id)
        }
    }

    fun clearAllBlockMedia() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = prefs.blockScreenMediaList.first()
            for (item in list) {
                try {
                    val file = java.io.File(item.path)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            prefs.clearAllBlockMediaItems()
        }
    }

    fun importMultipleCustomBlockMedia(
        uris: List<android.net.Uri>,
        forcedType: String? = null,
        onComplete: (Int, String?) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val contentResolver = context.contentResolver
            val newItems = mutableListOf<com.example.data.local.BlockMediaItem>()
            var lastError: String? = null

            for ((index, uri) in uris.withIndex()) {
                try {
                    val mimeType = contentResolver.getType(uri) ?: ""
                    val isVideo = if (forcedType != null) {
                        forcedType == "VIDEO"
                    } else {
                        mimeType.startsWith("video", ignoreCase = true) || uri.toString().contains("video", ignoreCase = true)
                    }
                    val type = if (isVideo) "VIDEO" else "IMAGE"
                    val ext = if (isVideo) "mp4" else "jpg"

                    var displayName = "media_${System.currentTimeMillis()}_$index.$ext"
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                displayName = cursor.getString(nameIndex) ?: displayName
                            }
                        }
                    } catch (_: Exception) {}

                    val destFile = java.io.File(
                        context.filesDir,
                        "custom_block_${System.currentTimeMillis()}_${index}_${java.util.UUID.randomUUID().toString().take(6)}.$ext"
                    )

                    val streamCopied = contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                        true
                    } ?: false

                    if (!streamCopied) {
                        lastError = "Could not read media: $displayName"
                        continue
                    }

                    newItems.add(
                        com.example.data.local.BlockMediaItem(
                            type = type,
                            path = destFile.absolutePath,
                            name = displayName
                        )
                    )
                } catch (e: Exception) {
                    lastError = e.localizedMessage
                }
            }

            if (newItems.isNotEmpty()) {
                prefs.addBlockMediaItems(newItems)
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (newItems.isNotEmpty()) {
                    onComplete(newItems.size, null)
                } else {
                    onComplete(0, lastError ?: "No media could be imported")
                }
            }
        }
    }

    fun importCustomBlockMedia(
        uri: android.net.Uri,
        isVideo: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        importMultipleCustomBlockMedia(
            uris = listOf(uri),
            forcedType = if (isVideo) "VIDEO" else "IMAGE"
        ) { count, error ->
            onComplete(count > 0, error)
        }
    }
}
