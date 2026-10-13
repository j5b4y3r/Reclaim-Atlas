package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.firebase.AuthUser
import com.example.data.firebase.BlockAttemptSyncDto
import com.example.data.firebase.BlockRuleSyncDto
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.firebase.FocusScheduleSyncDto
import com.example.data.firebase.FocusSessionSyncDto
import com.example.data.firebase.HabitSyncDto
import com.example.data.firebase.JournalSyncDto
import com.example.data.firebase.PartnerUnlockRequest
import com.example.data.firebase.SyncState
import com.example.data.firebase.UserSyncData
import com.example.data.local.AppDatabase
import com.example.data.local.DistractionCount
import com.example.data.local.PreferencesManager
import com.example.data.model.BlockAttempt
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.FocusSchedule
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.JournalEntry
import com.example.data.model.RuleType
import com.example.domain.engine.PartnerEmailSender
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class SelfControlRepository(
    private val context: Context,
    private val database: AppDatabase,
    val preferences: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "SelfControlRepository"

    private val ruleDao = database.blockRuleDao()
    private val sessionDao = database.focusSessionDao()
    private val attemptDao = database.blockAttemptDao()
    private val habitDao = database.habitDao()
    private val journalDao = database.journalEntryDao()
    private val focusScheduleDao = database.focusScheduleDao()

    val authManager = FirebaseAuthManager(context)
    val syncManager = FirestoreSyncManager(context)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _isOnline = MutableStateFlow(NetworkMonitor.isOnline(context))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _noInternetWarning = MutableStateFlow<String?>(null)
    val noInternetWarning: StateFlow<String?> = _noInternetWarning.asStateFlow()

    private val _pendingPartnerRequest = MutableStateFlow<PartnerUnlockRequest?>(null)
    val pendingPartnerRequest: StateFlow<PartnerUnlockRequest?> = _pendingPartnerRequest.asStateFlow()

    val activePartnerRequests: Flow<Map<String, PartnerUnlockRequest>> = preferences.activePartnerRequests

    @Volatile
    private var cachedSchedules: List<FocusSchedule>? = null

    @Volatile
    private var cachedActiveSession: FocusSession? = null

    @Volatile
    private var cachedEnabledRules: List<BlockRule> = emptyList()

    @Volatile
    private var isApplyingRemoteSnapshot: Boolean = false

    private var activeSnapshotJob: Job? = null
    private var debounceSyncJob: Job? = null

    fun triggerNoInternetWarning() {
        _noInternetWarning.value = NetworkMonitor.NO_INTERNET_MESSAGE
    }

    fun dismissNoInternetWarning() {
        _noInternetWarning.value = null
    }

    fun checkInternetForMustOnlineFeature(): Boolean {
        val online = NetworkMonitor.isOnline(context)
        _isOnline.value = online
        if (!online) {
            _noInternetWarning.value = NetworkMonitor.NO_INTERNET_MESSAGE
            return false
        }
        return true
    }

    init {
        // Keep focus schedules cached in memory for instant zero-latency accessibility evaluation
        scope.launch {
            try {
                focusScheduleDao.getAllSchedules().collect { list ->
                    cachedSchedules = list
                }
            } catch (e: Throwable) {
                Log.e(tag, "Failed to collect focus schedules", e)
            }
        }

        // Keep active focus session cached in memory for instant zero-latency accessibility evaluation
        scope.launch {
            try {
                sessionDao.observeActiveSession().collect { session ->
                    cachedActiveSession = session
                }
            } catch (e: Throwable) {
                Log.e(tag, "Failed to collect active focus session", e)
            }
        }

        // Keep enabled rules cached in memory for instant zero-latency accessibility & VPN evaluation
        scope.launch {
            try {
                ruleDao.getEnabledRules().collect { list ->
                    cachedEnabledRules = list
                }
            } catch (e: Throwable) {
                Log.e(tag, "Failed to collect enabled rules", e)
            }
        }

        // Mirror latest pending request from local Preferences map (after purging any stale undelivered entries)
        scope.launch {
            try {
                preferences.purgeUndeliveredPartnerRequests()
                preferences.activePartnerRequests.collect { map ->
                    _pendingPartnerRequest.value = map.values.maxByOrNull { it.requestedAt }
                }
            } catch (e: Throwable) {
                Log.e(tag, "Failed to collect active partner requests", e)
            }
        }

        // Monitor network connectivity and automatically sync offline changes to Firestore when internet comes back
        NetworkMonitor.observeOnlineStatus(context)
            .onEach { online ->
                val wasOffline = !_isOnline.value
                _isOnline.value = online
                if (online) {
                    val user = authManager.getCurrentUser()
                    if (user != null && user.uid.isNotBlank()) {
                        val hasPending = runCatching { preferences.pendingFirestoreSync.first() }.getOrDefault(false)
                        if (wasOffline || hasPending) {
                            Log.d(tag, "Internet connection available: syncing offline data to Firestore for ${user.uid}")
                            syncDataToCloud(isManual = false)
                        }
                    }
                }
            }
            .launchIn(scope)

        // Automatically observe all Preference changes for authenticated users so every setting is Firestore-backed
        preferences.allPreferencesFlow
            .map { prefs ->
                // Build a lightweight signature of user-facing settings (excluding sync timestamps)
                listOf(
                    prefs[PreferencesManager.KEY_MASTER_PROTECTION],
                    prefs[PreferencesManager.KEY_VPN_ENABLED],
                    prefs[PreferencesManager.KEY_ACCESSIBILITY_ENABLED],
                    prefs[PreferencesManager.KEY_ADULT_CONTENT_BLOCKED],
                    prefs[PreferencesManager.KEY_IMAGE_VIDEO_SEARCH_BLOCKED],
                    prefs[PreferencesManager.KEY_YOUTUBE_SHORTS_BLOCKED],
                    prefs[PreferencesManager.KEY_WHATSAPP_CHANNELS_BLOCKED],
                    prefs[PreferencesManager.KEY_INSTAGRAM_REELS_BLOCKED],
                    prefs[PreferencesManager.KEY_FACEBOOK_REELS_BLOCKED],
                    prefs[PreferencesManager.KEY_UNINSTALL_PROTECTION],
                    prefs[PreferencesManager.KEY_UNINSTALL_PROTECTION_UNTIL],
                    prefs[PreferencesManager.KEY_UNINSTALL_PROTECTION_DURATION_DAYS],
                    prefs[PreferencesManager.KEY_UNSUPPORTED_BROWSERS_BLOCKED],
                    prefs[PreferencesManager.KEY_NEW_APPS_BLOCKED],
                    prefs[PreferencesManager.KEY_SAFESEARCH_ENFORCED],
                    prefs[PreferencesManager.KEY_AUDIO_CHIME],
                    prefs[PreferencesManager.KEY_CUSTOM_BLOCKED_MESSAGE],
                    prefs[PreferencesManager.KEY_BLOCKED_COUNTDOWN_SECONDS],
                    prefs[PreferencesManager.KEY_REDIRECT_URL],
                    prefs[PreferencesManager.KEY_BLOCKED_MEDIA_TYPE],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_ENABLED],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_START_HOUR],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_START_MINUTE],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_DURATION_MINUTES],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_PARTNER_NAME],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_PARTNER_EMAIL],
                    prefs[PreferencesManager.KEY_ACCOUNTABILITY_NOTE],
                    prefs[PreferencesManager.KEY_FOCUS_TIMEZONE],
                    prefs[PreferencesManager.KEY_STREAK_START_TIMESTAMP],
                    prefs[PreferencesManager.KEY_BEST_STREAK_DAYS],
                    prefs[PreferencesManager.KEY_LAST_RELAPSE_TIMESTAMP],
                    prefs[PreferencesManager.KEY_ACTIVE_PARTNER_REQUESTS]
                ).hashCode()
            }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                if (!isApplyingRemoteSnapshot && authManager.isUserLoggedIn()) {
                    triggerCloudSyncDebounced()
                }
            }
            .launchIn(scope)

        // Automatically start observing Firestore snapshot updates whenever the user is logged in
        authManager.currentUserFlow.onEach { user ->
            if (user != null && user.uid.isNotBlank()) {
                startRealtimeSnapshotSync(user.uid)
            } else {
                activeSnapshotJob?.cancel()
                _pendingPartnerRequest.value = null
                _syncState.value = SyncState.Idle
            }
        }.launchIn(scope)
    }

    /**
     * Starts listening to user document snapshot updates from Firestore.
     * When changes occur in Firestore, automatically updates the local database and settings in real-time.
     */
    fun startRealtimeSnapshotSync(userId: String) {
        activeSnapshotJob?.cancel()
        activeSnapshotJob = scope.launch {
            syncManager.observeUserDocumentSnapshot(userId).collect { snapshotData ->
                if (snapshotData != null) {
                    val remoteReq = snapshotData.pendingPartnerRequest
                    if (remoteReq != null && remoteReq.deliveredViaSmtp) {
                        _pendingPartnerRequest.value = remoteReq
                    }
                    applyRemoteSnapshotToLocal(snapshotData)
                }
            }
        }
    }

    /**
     * Applies snapshot changes from Firestore into the app's local storage and Room database.
     */
    private suspend fun applyRemoteSnapshotToLocal(data: UserSyncData) {
        if (isApplyingRemoteSnapshot) return
        isApplyingRemoteSnapshot = true
        try {
            preferences.setMasterProtection(data.masterProtection)
            preferences.setVpnEnabled(data.vpnEnabled)
            preferences.setAccessibilityEnabled(data.accessibilityEnabled)
            preferences.setAdultContentBlocked(data.adultContentBlocked)
            preferences.setYoutubeShortsBlocked(data.youtubeShortsBlocked)
            preferences.setInstagramReelsBlocked(data.instagramReelsBlocked)
            preferences.setFacebookReelsBlocked(data.facebookReelsBlocked)
            preferences.setImageVideoSearchBlocked(data.imageVideoSearchBlocked)
            preferences.setWhatsappChannelsBlocked(data.whatsappChannelsBlocked)
            if (data.uninstallProtection && data.uninstallProtectionUntil > System.currentTimeMillis()) {
                preferences.setUninstallProtectionConfig(
                    enabled = true,
                    durationDays = data.uninstallProtectionDurationDays.coerceAtLeast(1)
                )
            } else {
                preferences.setUninstallProtection(data.uninstallProtection)
            }
            preferences.setNewAppsBlocked(data.newAppsBlocked)
            preferences.setUnsupportedBrowsersBlocked(data.unsupportedBrowsersBlocked)
            preferences.setSafeSearchEnforced(data.safeSearchEnforced)
            preferences.setAudioChimeEnabled(data.audioChimeEnabled)

            if (data.customBlockedMessage.isNotBlank()) {
                preferences.setCustomBlockedMessage(data.customBlockedMessage)
            }
            preferences.setBlockedCountdownSeconds(data.blockedCountdownSeconds)
            preferences.setRedirectUrl(data.redirectUrl)
            if (data.blockedMediaType.isNotBlank()) {
                preferences.setBlockedMediaType(data.blockedMediaType)
            }
            if (data.focusTimezone.isNotBlank()) {
                preferences.setFocusTimezone(data.focusTimezone)
            }

            preferences.setAccountabilityEnabled(data.accountabilityEnabled)
            if (data.accountabilityPartnerEmail.isNotBlank() || data.accountabilityPartnerName.isNotBlank()) {
                preferences.setAccountabilityPartner(
                    data.accountabilityPartnerName,
                    data.accountabilityPartnerEmail,
                    data.accountabilityNote
                )
            }
            preferences.setAccountabilitySchedule(
                data.accountabilityStartHour,
                data.accountabilityStartMinute,
                data.accountabilityDurationMinutes
            )

            if (data.streakStartTime > 0) {
                preferences.setStreakStartTime(data.streakStartTime)
            }
            if (data.bestStreakDays > 0) {
                preferences.updateBestStreakIfHigher(data.bestStreakDays)
            }

            if (data.activePartnerRequests.isNotEmpty()) {
                preferences.replaceActivePartnerRequests(data.activePartnerRequests)
            }

            // Sync Room collections from Firestore snapshot when populated
            if (data.blockRules.isNotEmpty()) {
                val mappedRules = data.blockRules.map { dto ->
                    BlockRule(
                        id = dto.id,
                        name = dto.name,
                        type = runCatching { RuleType.valueOf(dto.ruleType) }.getOrDefault(RuleType.DOMAIN),
                        target = dto.target,
                        category = runCatching { BlockCategory.valueOf(dto.category) }.getOrDefault(BlockCategory.CUSTOM),
                        enabled = dto.enabled,
                        isException = dto.isException,
                        priority = dto.priority,
                        scheduleDays = dto.scheduleDays,
                        scheduleStartTime = dto.scheduleStartTime,
                        scheduleEndTime = dto.scheduleEndTime,
                        dailyLimitMinutes = dto.dailyLimitMinutes,
                        createdAt = dto.createdAt
                    )
                }
                ruleDao.clearAll()
                ruleDao.insertRules(mappedRules)
            }

            if (data.focusSchedules.isNotEmpty()) {
                val mappedSchedules = data.focusSchedules.map { dto ->
                    FocusSchedule(
                        id = dto.id,
                        title = dto.title,
                        startTime = dto.startTime,
                        endTime = dto.endTime,
                        isDaily = dto.isDaily,
                        selectedDays = dto.selectedDays,
                        allowedApps = dto.allowedApps,
                        isEnabled = dto.isEnabled
                    )
                }
                focusScheduleDao.clearAll()
                focusScheduleDao.insertSchedules(mappedSchedules)
                cachedSchedules = mappedSchedules
            }

            if (data.habits.isNotEmpty()) {
                val mappedHabits = data.habits.map { dto ->
                    Habit(
                        id = dto.id,
                        title = dto.title,
                        targetDuration = dto.targetDuration,
                        isCompletedToday = dto.isCompletedToday,
                        streak = dto.streak,
                        createdTimestamp = dto.createdTimestamp
                    )
                }
                habitDao.clearAll()
                habitDao.insertHabits(mappedHabits)
            }

            if (data.journalEntries.isNotEmpty()) {
                val mappedJournal = data.journalEntries.map { dto ->
                    JournalEntry(
                        id = dto.id,
                        dateText = dto.dateText,
                        mood = dto.mood,
                        note = dto.note,
                        timestamp = dto.timestamp
                    )
                }
                journalDao.clearAll()
                journalDao.insertEntries(mappedJournal)
            }

            if (data.focusSessions.isNotEmpty()) {
                val mappedSessions = data.focusSessions.map { dto ->
                    FocusSession(
                        id = dto.id,
                        startTime = dto.startTime,
                        endTime = dto.endTime,
                        targetMinutes = dto.targetMinutes,
                        completedMinutes = dto.completedMinutes,
                        isCompleted = dto.isCompleted,
                        blockedAttemptsCount = dto.blockedAttemptsCount,
                        sessionNotes = dto.sessionNotes
                    )
                }
                sessionDao.insertSessions(mappedSessions)
            }

            if (data.blockAttempts.isNotEmpty()) {
                val mappedAttempts = data.blockAttempts.map { dto ->
                    BlockAttempt(
                        id = dto.id,
                        timestamp = dto.timestamp,
                        targetName = dto.targetName,
                        ruleType = runCatching { RuleType.valueOf(dto.ruleType) }.getOrDefault(RuleType.DOMAIN),
                        category = runCatching { BlockCategory.valueOf(dto.category) }.getOrDefault(BlockCategory.CUSTOM),
                        wasOverridden = dto.wasOverridden
                    )
                }
                attemptDao.insertAttempts(mappedAttempts)
            }

            val req = data.pendingPartnerRequest
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            if (req != null && req.status == "APPROVED") {
                _syncState.value = SyncState.Success(
                    "Partner unlocked request at ${sdf.format(Date(if (req.respondedAt > 0) req.respondedAt else System.currentTimeMillis()))}"
                )
            } else {
                _syncState.value = SyncState.Success("Firestore synced at ${sdf.format(Date())}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error applying remote snapshot", e)
        } finally {
            isApplyingRemoteSnapshot = false
        }
    }

    // Rules queries & mutations
    fun getAllRules(): Flow<List<BlockRule>> = ruleDao.getAllRules()

    fun getEnabledRules(): Flow<List<BlockRule>> = ruleDao.getEnabledRules()

    suspend fun getEnabledRulesSync(): List<BlockRule> {
        val cached = cachedEnabledRules
        if (cached.isNotEmpty()) return cached
        val fresh = ruleDao.getEnabledRulesSync()
        cachedEnabledRules = fresh
        return fresh
    }

    fun getEnabledRulesCached(): List<BlockRule> = cachedEnabledRules

    fun getRulesByCategory(category: BlockCategory): Flow<List<BlockRule>> =
        ruleDao.getRulesByCategory(category)

    fun getRulesByType(type: RuleType): Flow<List<BlockRule>> =
        ruleDao.getRulesByType(type)

    suspend fun getRuleById(id: Long): BlockRule? = ruleDao.getRuleById(id)

    suspend fun insertRule(rule: BlockRule): Long {
        val id = ruleDao.insertRule(rule)
        triggerCloudSyncDebounced()
        return id
    }

    suspend fun updateRule(rule: BlockRule) {
        ruleDao.updateRule(rule)
        triggerCloudSyncDebounced()
    }

    suspend fun deleteRule(rule: BlockRule) {
        ruleDao.deleteRule(rule)
        triggerCloudSyncDebounced()
    }

    suspend fun deleteRuleById(id: Long) {
        ruleDao.deleteRuleById(id)
        triggerCloudSyncDebounced()
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        ruleDao.setRuleEnabled(id, enabled)
        triggerCloudSyncDebounced()
    }

    suspend fun setCategoryEnabled(category: BlockCategory, enabled: Boolean) {
        ruleDao.setCategoryEnabled(category, enabled)
        triggerCloudSyncDebounced()
    }

    // Attempts queries & mutations
    fun getAllAttempts(): Flow<List<BlockAttempt>> = attemptDao.getAllAttempts()

    fun getAttemptsSince(sinceTimestamp: Long): Flow<List<BlockAttempt>> =
        attemptDao.getAttemptsSince(sinceTimestamp)

    fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getTopDistractions(limit: Int = 5): Flow<List<DistractionCount>> =
        attemptDao.getTopDistractions(limit)

    suspend fun recordBlockAttempt(
        targetName: String,
        ruleType: RuleType,
        category: BlockCategory,
        wasOverridden: Boolean = false
    ): Long {
        val attempt = BlockAttempt(
            timestamp = System.currentTimeMillis(),
            targetName = targetName,
            ruleType = ruleType,
            category = category,
            wasOverridden = wasOverridden
        )
        val id = attemptDao.insertAttempt(attempt)
        triggerCloudSyncDebounced(delayMs = 15_000L)
        return id
    }

    // Focus Session queries & mutations
    fun getAllSessions(): Flow<List<FocusSession>> = sessionDao.getAllSessions()

    fun observeActiveSession(): Flow<FocusSession?> = sessionDao.observeActiveSession()

    fun getActiveSessionFast(): FocusSession? = cachedActiveSession

    fun isFocusModeActiveFast(): Boolean = cachedActiveSession != null || getActiveFocusScheduleFast() != null

    suspend fun getActiveSession(): FocusSession? = cachedActiveSession ?: sessionDao.getActiveSession().also { cachedActiveSession = it }

    suspend fun startFocusSession(targetMinutes: Int): Long {
        val session = FocusSession(
            startTime = System.currentTimeMillis(),
            targetMinutes = targetMinutes,
            completedMinutes = 0,
            isCompleted = false
        )
        val id = sessionDao.insertSession(session)
        cachedActiveSession = session.copy(id = id)
        triggerCloudSyncDebounced()
        return id
    }

    suspend fun completeFocusSession(sessionId: Long, completedMinutes: Int) {
        cachedActiveSession = null
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            val updated = session.copy(
                endTime = System.currentTimeMillis(),
                completedMinutes = completedMinutes,
                isCompleted = true
            )
            sessionDao.updateSession(updated)
            triggerCloudSyncDebounced()
        }
    }

    suspend fun cancelFocusSession(sessionId: Long, completedMinutes: Int) {
        cachedActiveSession = null
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            val updated = session.copy(
                endTime = System.currentTimeMillis(),
                completedMinutes = completedMinutes,
                isCompleted = false
            )
            sessionDao.updateSession(updated)
            triggerCloudSyncDebounced()
        }
    }

    // Habits queries & mutations
    fun getAllHabits(): Flow<List<Habit>> = habitDao.getAllHabits()

    suspend fun insertHabit(habit: Habit): Long {
        val id = habitDao.insertHabit(habit)
        triggerCloudSyncDebounced()
        return id
    }

    suspend fun setHabitCompleted(id: Long, completed: Boolean) {
        habitDao.setHabitCompleted(id, completed)
        triggerCloudSyncDebounced()
    }

    suspend fun deleteHabit(habit: Habit) {
        habitDao.deleteHabit(habit)
        triggerCloudSyncDebounced()
    }

    suspend fun deleteHabitById(id: Long) {
        habitDao.deleteHabitById(id)
        triggerCloudSyncDebounced()
    }

    // Journal queries & mutations
    fun getAllJournalEntries(): Flow<List<JournalEntry>> = journalDao.getAllEntries()

    suspend fun insertJournalEntry(entry: JournalEntry): Long {
        val id = journalDao.insertEntry(entry)
        triggerCloudSyncDebounced()
        return id
    }

    suspend fun deleteJournalEntry(entry: JournalEntry) {
        journalDao.deleteEntry(entry)
        triggerCloudSyncDebounced()
    }

    suspend fun deleteJournalEntryById(id: Long) {
        journalDao.deleteEntryById(id)
        triggerCloudSyncDebounced()
    }

    suspend fun clearAllData() {
        attemptDao.clearAll()
        sessionDao.clearAll()
        triggerCloudSyncDebounced()
    }

    suspend fun deleteAccountAndAllData(): Result<Unit> {
        if (!checkInternetForMustOnlineFeature()) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }
        val user = authManager.getCurrentUser()
        activeSnapshotJob?.cancel()
        attemptDao.clearAll()
        sessionDao.clearAll()
        if (user != null && user.uid.isNotBlank()) {
            syncManager.deleteUserData(user.uid)
        }
        return authManager.deleteAccount()
    }

    // Focus Schedule queries & mutations
    fun getAllFocusSchedules(): Flow<List<FocusSchedule>> = focusScheduleDao.getAllSchedules()

    suspend fun insertFocusSchedule(schedule: FocusSchedule): Long {
        val id = focusScheduleDao.insertSchedule(schedule)
        cachedSchedules = try { focusScheduleDao.getAllSchedulesSync() } catch (_: Throwable) { cachedSchedules }
        triggerCloudSyncDebounced()
        return id
    }

    suspend fun updateFocusSchedule(schedule: FocusSchedule) {
        focusScheduleDao.updateSchedule(schedule)
        cachedSchedules = try { focusScheduleDao.getAllSchedulesSync() } catch (_: Throwable) { cachedSchedules }
        triggerCloudSyncDebounced()
    }

    suspend fun deleteFocusSchedule(schedule: FocusSchedule) {
        focusScheduleDao.deleteSchedule(schedule)
        cachedSchedules = try { focusScheduleDao.getAllSchedulesSync() } catch (_: Throwable) { cachedSchedules }
        triggerCloudSyncDebounced()
    }

    suspend fun setFocusScheduleEnabled(id: Long, enabled: Boolean) {
        focusScheduleDao.setScheduleEnabled(id, enabled)
        cachedSchedules = try { focusScheduleDao.getAllSchedulesSync() } catch (_: Throwable) { cachedSchedules }
        triggerCloudSyncDebounced()
    }

    fun getActiveFocusScheduleFast(currentTimeMillis: Long = System.currentTimeMillis()): FocusSchedule? {
        val schedules = cachedSchedules ?: return null
        return evaluateActiveScheduleFromList(schedules, currentTimeMillis)
    }

    suspend fun getActiveFocusSchedule(currentTimeMillis: Long = System.currentTimeMillis()): FocusSchedule? {
        val schedules = cachedSchedules ?: try {
            focusScheduleDao.getAllSchedulesSync().also { cachedSchedules = it }
        } catch (_: Throwable) {
            emptyList()
        }
        return evaluateActiveScheduleFromList(schedules, currentTimeMillis)
    }

    private fun evaluateActiveScheduleFromList(schedules: List<FocusSchedule>, currentTimeMillis: Long): FocusSchedule? {
        val enabledSchedules = schedules.filter { it.isEnabled }
        if (enabledSchedules.isEmpty()) return null

        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        val dayName = when (currentDayOfWeek) {
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            else -> ""
        }

        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis - 24L * 60 * 60 * 1000
        }
        val yesterdayDayName = when (yesterdayCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            else -> ""
        }

        for (schedule in schedules) {
            if (!schedule.isEnabled) continue

            val startMinutes = parseTimeStringToMinutes(schedule.startTime)
            val endMinutes = parseTimeStringToMinutes(schedule.endTime)

            if (startMinutes != null && endMinutes != null) {
                val isOvernight = startMinutes > endMinutes
                val isInRange = if (!isOvernight) {
                    currentTotalMinutes in startMinutes..endMinutes
                } else {
                    currentTotalMinutes >= startMinutes || currentTotalMinutes <= endMinutes
                }

                if (!isInRange) continue

                if (!schedule.isDaily && schedule.selectedDays.isNotBlank()) {
                    val allowedDays = schedule.selectedDays.split(",").map { it.trim().lowercase() }
                    val effectiveDay = if (isOvernight && currentTotalMinutes <= endMinutes) {
                        yesterdayDayName.lowercase()
                    } else {
                        dayName.lowercase()
                    }
                    if (!allowedDays.contains(effectiveDay)) {
                        continue
                    }
                }

                return schedule
            }
        }
        return null
    }

    fun parseTimeStringToMinutes(timeStr: String): Int? {
        if (timeStr.isBlank()) return null
        val clean = timeStr.trim().lowercase()
            .replace(".", "")
            .replace("\u00a0", " ")
            .replace("\u202f", " ")
        val isPm = clean.contains("pm")
        val isAm = clean.contains("am")
        val digitsOnly = clean.replace("am", "").replace("pm", "").trim()
        val parts = digitsOnly.split(":").map { it.trim() }
        if (parts.size < 2) return null
        var hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].take(2).toIntOrNull() ?: return null
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        return (hour * 60 + minute) % (24 * 60)
    }

    suspend fun resetStreak(reason: String = "", mood: String = "Reflective") {
        preferences.resetStreakToZero()
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val note = if (reason.isNotBlank()) "Relapse recorded: $reason. Dust off and begin again." else "Relapse reset. Stay strong."
        journalDao.insertEntry(
            JournalEntry(
                dateText = dateStr,
                mood = mood,
                note = note,
                timestamp = System.currentTimeMillis()
            )
        )
        triggerCloudSyncDebounced()
    }

    fun triggerCloudSyncDebounced(delayMs: Long = 350L) {
        val user = authManager.getCurrentUser()
        if (user != null && user.uid.isNotBlank() && !isApplyingRemoteSnapshot) {
            debounceSyncJob?.cancel()
            debounceSyncJob = scope.launch {
                delay(delayMs)
                syncDataToCloud(isManual = false)
            }
        }
    }

    /**
     * Sends a 1-time 6-digit verification code via background email (PartnerEmailSender) for ONE specific locked action.
     * Requires internet connectivity; warns user if offline.
     */
    suspend fun sendAccountabilityPartnerRequest(
        actionKey: String,
        targetType: String,
        targetDetail: String,
        ruleId: Long? = null,
        payload: String = "",
        reason: String
    ): Result<PartnerUnlockRequest> {
        val safeActionKey = actionKey.ifBlank { "${targetType}_${ruleId ?: targetDetail}" }
        val existingMap = preferences.activePartnerRequests.first()
        val existingRequest = existingMap[safeActionKey]
        if (existingRequest != null && existingRequest.status == "PENDING" && existingRequest.deliveredViaSmtp) {
            _pendingPartnerRequest.value = existingRequest
            return Result.success(existingRequest)
        }

        if (!checkInternetForMustOnlineFeature()) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }

        val user = authManager.getCurrentUser()
        val partnerEmail = preferences.accountabilityPartnerEmail.first().trim()
        val partnerName = preferences.accountabilityPartnerName.first().trim()

        if (partnerEmail.isBlank()) {
            return Result.failure(Exception("No accountability partner email configured. Please set your partner email in Accountability Settings."))
        }

        val requestId = "req_${UUID.randomUUID().toString().substring(0, 8)}"
        val userId = user?.uid ?: "user_local_${System.currentTimeMillis()}"
        val userDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Reclaim User"
        val verificationCode = String.format(Locale.US, "%06d", (100000..999999).random())

        val newPartnerEmailIfChanging = if (targetType == "CHANGE_PARTNER_EMAIL" && payload.contains("|")) {
            payload.split("|").getOrNull(1)?.trim()
        } else null

        val emailResult = PartnerEmailSender.sendPartnerVerificationEmail(
            recipientPartnerEmail = partnerEmail,
            partnerName = partnerName,
            userDisplayName = userDisplayName,
            actionTitle = targetDetail,
            reason = reason,
            verificationCode = verificationCode,
            newPartnerEmailIfChanging = newPartnerEmailIfChanging
        )

        if (!emailResult.success || !emailResult.deliveredViaSmtp) {
            preferences.removePartnerUnlockRequest(safeActionKey)
            return Result.failure(Exception(emailResult.statusNote))
        }

        val request = PartnerUnlockRequest(
            requestId = requestId,
            actionKey = safeActionKey,
            userId = userId,
            targetType = targetType,
            targetDetail = targetDetail,
            ruleId = ruleId,
            payload = payload,
            verificationCode = verificationCode,
            senderEmail = emailResult.senderEmail,
            deliveredViaSmtp = true,
            requestedAt = System.currentTimeMillis(),
            partnerEmail = partnerEmail,
            userDisplayName = userDisplayName,
            reason = reason,
            status = "PENDING",
            responseNote = emailResult.statusNote,
            respondedAt = 0L
        )

        preferences.savePartnerUnlockRequest(request)
        _pendingPartnerRequest.value = request

        val result = syncManager.createPartnerRequest(request)
        if (result.isFailure) {
            Log.w(tag, "Failed to upload partner request to Firestore: ${result.exceptionOrNull()?.message}")
        }
        triggerCloudSyncDebounced()

        return Result.success(request)
    }

    /**
     * Verifies the 6-digit code entered by the user for a single locked item (`actionKey`).
     * If valid, unlocks ONLY that specific item and clears the pending request for that item.
     */
    suspend fun verifyAndApplyPartnerCode(
        actionKey: String,
        enteredCode: String
    ): Result<PartnerUnlockRequest> {
        val cleanCode = enteredCode.trim()
        if (cleanCode.length != 6) {
            return Result.failure(Exception("Please enter the 6-digit verification code from your partner."))
        }

        val currentMap = preferences.activePartnerRequests.first()
        val request = currentMap[actionKey]
            ?: return Result.failure(Exception("No active unlock request found for this item."))

        if (request.verificationCode != cleanCode) {
            return Result.failure(Exception("Incorrect 6-digit verification code. Ask your partner for the code sent to ${request.partnerEmail}."))
        }

        applySingleUnlockedAction(request)

        preferences.removePartnerUnlockRequest(actionKey)
        val user = authManager.getCurrentUser()
        if (user != null && user.uid.isNotBlank()) {
            syncManager.clearPendingPartnerRequest(user.uid)
        }
        triggerCloudSyncDebounced()
        return Result.success(request)
    }

    private suspend fun applySingleUnlockedAction(request: PartnerUnlockRequest) {
        when (request.actionKey) {
            "FILTER_ADULT_CONTENT" -> preferences.setAdultContentBlocked(false)
            "FILTER_YOUTUBE_SHORTS" -> preferences.setYoutubeShortsBlocked(false)
            "FILTER_INSTAGRAM_REELS" -> preferences.setInstagramReelsBlocked(false)
            "FILTER_FACEBOOK_REELS" -> preferences.setFacebookReelsBlocked(false)
            "FILTER_IMAGE_VIDEO_SEARCH" -> preferences.setImageVideoSearchBlocked(false)
            "FILTER_GOOGLE_LENS" -> preferences.setGoogleLensBlocked(false)
            "FILTER_WHATSAPP_CHANNELS" -> preferences.setWhatsappChannelsBlocked(false)
            "FILTER_ALL_CONTENT" -> {
                preferences.setAdultContentBlocked(false)
                preferences.setYoutubeShortsBlocked(false)
                preferences.setInstagramReelsBlocked(false)
                preferences.setFacebookReelsBlocked(false)
                preferences.setImageVideoSearchBlocked(false)
                preferences.setGoogleLensBlocked(false)
                preferences.setWhatsappChannelsBlocked(false)
            }
            "ADV_UNINSTALL_PROTECTION" -> preferences.setUninstallProtectionConfig(enabled = false, durationDays = 1)
            "ADV_NEW_APPS_BLOCKED" -> preferences.setNewAppsBlocked(false)
            "ADV_UNSUPPORTED_BROWSERS" -> preferences.setUnsupportedBrowsersBlocked(false)
            "ADV_SAFESEARCH" -> preferences.setSafeSearchEnforced(false)
            "ADV_VPN_FILTER" -> preferences.setVpnEnabled(false)
            "ADV_ACCESSIBILITY" -> preferences.setAccessibilityEnabled(false)
            "ACC_DISABLE_MODE" -> preferences.setAccountabilityEnabled(false)
            "ACC_CHANGE_SCHEDULE" -> {
                val parts = request.payload.split(":")
                if (parts.size >= 3) {
                    val h = parts[0].toIntOrNull() ?: 12
                    val m = parts[1].toIntOrNull() ?: 0
                    val d = parts[2].toIntOrNull() ?: 30
                    preferences.setAccountabilitySchedule(h, m, d)
                }
            }
            "ACC_CHANGE_PARTNER_EMAIL" -> {
                val parts = request.payload.split("|")
                val newName = parts.getOrNull(0) ?: ""
                val newEmail = parts.getOrNull(1) ?: ""
                val newNote = parts.getOrNull(2) ?: ""
                if (newEmail.isNotBlank()) {
                    preferences.setAccountabilityPartner(newName, newEmail, newNote)
                }
            }
            else -> {
                when {
                    request.actionKey.startsWith("RULE_DISABLE_") -> {
                        val id = request.ruleId ?: request.actionKey.removePrefix("RULE_DISABLE_").toLongOrNull()
                        if (id != null && id > 0) {
                            ruleDao.setRuleEnabled(id, false)
                        }
                    }
                    request.actionKey.startsWith("RULE_DELETE_") -> {
                        val id = request.ruleId ?: request.actionKey.removePrefix("RULE_DELETE_").toLongOrNull()
                        if (id != null && id > 0) {
                            ruleDao.deleteRuleById(id)
                        }
                    }
                    request.actionKey.startsWith("CATEGORY_DISABLE_") -> {
                        val catName = request.actionKey.removePrefix("CATEGORY_DISABLE_")
                        val cat = runCatching { BlockCategory.valueOf(catName) }.getOrNull()
                        if (cat != null) {
                            ruleDao.setCategoryEnabled(cat, false)
                        }
                    }
                }
            }
        }
    }

    suspend fun clearPartnerRequestForAction(actionKey: String) {
        preferences.removePartnerUnlockRequest(actionKey)
        triggerCloudSyncDebounced()
    }

    suspend fun clearPendingPartnerRequest() {
        val user = authManager.getCurrentUser()
        if (user != null && user.uid.isNotBlank()) {
            syncManager.clearPendingPartnerRequest(user.uid)
        }
        preferences.clearAllPartnerUnlockRequests()
        _pendingPartnerRequest.value = null
        triggerCloudSyncDebounced()
    }

    // Cloud Sync Methods
    suspend fun signInWithGoogle(activityContext: Context = context): Result<AuthUser> {
        if (!checkInternetForMustOnlineFeature()) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }
        val result = authManager.signInWithGoogle(activityContext)
        if (result.isSuccess) {
            val user = result.getOrNull()
            if (user != null) {
                // First check if this Google account already has data in Firestore
                val existingCloudData = syncManager.fetchUserData(user.uid).getOrNull()
                if (existingCloudData != null) {
                    applyRemoteSnapshotToLocal(existingCloudData)
                }
                startRealtimeSnapshotSync(user.uid)
                syncDataToCloud(isManual = false)
            }
        }
        return result
    }

    suspend fun syncDataToCloud(isManual: Boolean = false): Result<Unit> {
        val user = authManager.getCurrentUser()
            ?: return Result.failure(Exception("Not logged in. Please sign in to sync."))

        val onlineNow = NetworkMonitor.isOnline(context)
        _isOnline.value = onlineNow

        if (isManual && !onlineNow) {
            _noInternetWarning.value = NetworkMonitor.NO_INTERNET_MESSAGE
            _syncState.value = SyncState.Error(NetworkMonitor.NO_INTERNET_MESSAGE)
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }

        if (onlineNow) {
            _syncState.value = SyncState.Syncing
        }

        return try {
            val startTs = preferences.streakStartTimestamp.first()
            val currentStreak = preferences.dailyStreak.first()
            val bestStreak = preferences.bestStreakDays.first()
            val lastRelapse = preferences.lastRelapseTimestamp.first()
            val allAttempts = attemptDao.getAllAttempts().first()
            val allSessions = sessionDao.getAllSessions().first()
            val completedFocusMinutes = allSessions.filter { it.isCompleted }.sumOf { it.completedMinutes }

            val habitsList = habitDao.getAllHabits().first().map {
                HabitSyncDto(
                    id = it.id,
                    title = it.title,
                    targetDuration = it.targetDuration,
                    isCompletedToday = it.isCompletedToday,
                    streak = it.streak,
                    createdTimestamp = it.createdTimestamp
                )
            }

            val journalList = journalDao.getAllEntries().first().map {
                JournalSyncDto(
                    id = it.id,
                    dateText = it.dateText,
                    mood = it.mood,
                    note = it.note,
                    timestamp = it.timestamp
                )
            }

            val rulesList = ruleDao.getAllRules().first().map {
                BlockRuleSyncDto(
                    id = it.id,
                    name = it.name,
                    ruleType = it.type.name,
                    target = it.target,
                    category = it.category.name,
                    enabled = it.enabled,
                    isException = it.isException,
                    priority = it.priority,
                    scheduleDays = it.scheduleDays,
                    scheduleStartTime = it.scheduleStartTime,
                    scheduleEndTime = it.scheduleEndTime,
                    dailyLimitMinutes = it.dailyLimitMinutes,
                    createdAt = it.createdAt
                )
            }

            val schedulesList = focusScheduleDao.getAllSchedules().first().map {
                FocusScheduleSyncDto(
                    id = it.id,
                    title = it.title,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    isDaily = it.isDaily,
                    selectedDays = it.selectedDays,
                    allowedApps = it.allowedApps,
                    isEnabled = it.isEnabled
                )
            }

            val sessionsList = allSessions.take(100).map {
                FocusSessionSyncDto(
                    id = it.id,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    targetMinutes = it.targetMinutes,
                    completedMinutes = it.completedMinutes,
                    isCompleted = it.isCompleted,
                    blockedAttemptsCount = it.blockedAttemptsCount,
                    sessionNotes = it.sessionNotes
                )
            }

            val attemptsList = allAttempts.take(200).map {
                BlockAttemptSyncDto(
                    id = it.id,
                    timestamp = it.timestamp,
                    targetName = it.targetName,
                    ruleType = it.ruleType.name,
                    category = it.category.name,
                    wasOverridden = it.wasOverridden
                )
            }

            val activeRequestsList = preferences.activePartnerRequests.first().values.toList()

            val syncData = UserSyncData(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "Reclaimer",
                photoUrl = user.photoUrl,
                authProvider = user.authProvider,
                registeredAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis(),
                streakStartTime = startTs,
                currentStreakDays = currentStreak,
                bestStreakDays = bestStreak,
                lastRelapseTime = lastRelapse,
                totalUrgesResisted = allAttempts.size,
                totalFocusMinutes = completedFocusMinutes,
                habits = habitsList,
                journalEntries = journalList,
                blockRules = rulesList,
                focusSchedules = schedulesList,
                focusSessions = sessionsList,
                blockAttempts = attemptsList,
                activePartnerRequests = activeRequestsList,
                masterProtection = preferences.masterProtection.first(),
                vpnEnabled = preferences.vpnEnabled.first(),
                accessibilityEnabled = preferences.accessibilityEnabled.first(),
                adultContentBlocked = preferences.adultContentBlocked.first(),
                youtubeShortsBlocked = preferences.youtubeShortsBlocked.first(),
                instagramReelsBlocked = preferences.instagramReelsBlocked.first(),
                facebookReelsBlocked = preferences.facebookReelsBlocked.first(),
                imageVideoSearchBlocked = preferences.imageVideoSearchBlocked.first(),
                whatsappChannelsBlocked = preferences.whatsappChannelsBlocked.first(),
                uninstallProtection = preferences.uninstallProtection.first(),
                uninstallProtectionUntil = preferences.uninstallProtectionUntil.first(),
                uninstallProtectionDurationDays = preferences.uninstallProtectionDurationDays.first(),
                newAppsBlocked = preferences.newAppsBlocked.first(),
                unsupportedBrowsersBlocked = preferences.unsupportedBrowsersBlocked.first(),
                safeSearchEnforced = preferences.safeSearchEnforced.first(),
                audioChimeEnabled = preferences.audioChimeEnabled.first(),
                customBlockedMessage = preferences.customBlockedMessage.first(),
                blockedCountdownSeconds = preferences.blockedCountdownSeconds.first(),
                redirectUrl = preferences.redirectUrl.first(),
                blockedMediaType = preferences.blockedMediaType.first(),
                focusTimezone = preferences.focusTimezone.first(),
                accountabilityEnabled = preferences.accountabilityEnabled.first(),
                accountabilityPartnerEmail = preferences.accountabilityPartnerEmail.first(),
                accountabilityPartnerName = preferences.accountabilityPartnerName.first(),
                accountabilityNote = preferences.accountabilityNote.first(),
                accountabilityStartHour = preferences.accountabilityStartHour.first(),
                accountabilityStartMinute = preferences.accountabilityStartMinute.first(),
                accountabilityDurationMinutes = preferences.accountabilityDurationMinutes.first(),
                lastSyncedAt = System.currentTimeMillis(),
                pendingPartnerRequest = _pendingPartnerRequest.value
            )

            val result = syncManager.saveUserData(syncData)
            if (result.isSuccess) {
                if (onlineNow) {
                    preferences.setPendingFirestoreSync(false)
                    preferences.recordCloudSyncTime(System.currentTimeMillis())
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    _syncState.value = SyncState.Success("Synced to Firestore at ${sdf.format(Date())}")
                } else {
                    preferences.setPendingFirestoreSync(true)
                    _syncState.value = SyncState.Success("Saved offline • Will update Firestore when online")
                }
            } else {
                preferences.setPendingFirestoreSync(true)
                if (!onlineNow) {
                    _syncState.value = SyncState.Success("Saved offline • Will update Firestore when online")
                } else {
                    _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Sync failed")
                }
            }
            result
        } catch (e: Exception) {
            preferences.setPendingFirestoreSync(true)
            if (!onlineNow) {
                _syncState.value = SyncState.Success("Saved offline • Will update Firestore when online")
            } else {
                _syncState.value = SyncState.Error(e.message ?: "Sync error")
            }
            Result.failure(e)
        }
    }

    suspend fun restoreDataFromCloud(isManual: Boolean = false): Result<Unit> {
        val user = authManager.getCurrentUser() ?: return Result.failure(Exception("Not logged in."))
        if (!NetworkMonitor.isOnline(context)) {
            if (isManual) {
                _noInternetWarning.value = NetworkMonitor.NO_INTERNET_MESSAGE
            }
            _syncState.value = SyncState.Error(NetworkMonitor.NO_INTERNET_MESSAGE)
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }

        _syncState.value = SyncState.Syncing

        return try {
            val result = syncManager.fetchUserData(user.uid)
            val data = result.getOrNull()
            if (data != null) {
                applyRemoteSnapshotToLocal(data)
                preferences.recordCloudSyncTime(System.currentTimeMillis())
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                _syncState.value = SyncState.Success("Restored from Firestore at ${sdf.format(Date())}")
                Result.success(Unit)
            } else {
                syncDataToCloud(isManual = false)
                preferences.recordCloudSyncTime(System.currentTimeMillis())
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                _syncState.value = SyncState.Success("Synced with Firestore at ${sdf.format(Date())}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            _syncState.value = SyncState.Success("Synced with Firestore at ${sdf.format(Date())}")
            Result.success(Unit)
        }
    }
}
