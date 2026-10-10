package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.util.NetworkMonitor
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreSyncManager(private val context: Context) {

    private val tag = "FirestoreSyncManager"
    private var activeSnapshotListener: ListenerRegistration? = null
    @Volatile
    private var lastSyncedSnapshotMemoryCache: UserSyncData? = null

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            }
            if (app != null) {
                val instance = FirebaseFirestore.getInstance(app)
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    instance.firestoreSettings = settings
                } catch (_: Exception) {
                    // Settings already applied
                }
                instance
            } else {
                Log.w(tag, "Firebase is not configured. Missing google-services.json.")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Firestore not available: ${e.message}")
            null
        }
    }

    /**
     * Real-time Snapshot Listener for user settings, block rules, habits, schedules, journal, and accountability updates.
     */
    fun observeUserDocumentSnapshot(userId: String): Flow<UserSyncData?> = callbackFlow {
        val db = firestore
        if (db == null || userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = db.collection("users").document(userId)
        val registration = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                Log.i(tag, "Firestore Snapshot listener notice for $userId: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // Only apply remote updates (not local pending writes that we just triggered ourselves)
                if (!snapshot.metadata.hasPendingWrites()) {
                    val data = parseUserSyncData(snapshot)
                    lastSyncedSnapshotMemoryCache = data
                    trySend(data)
                }
            } else {
                trySend(null)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Stores all user data for an authenticated user into Firestore under `users/{userId}`.
     * Supports offline usage via Firestore's persistent cache and flushes to cloud when online.
     */
    suspend fun saveUserData(userData: UserSyncData): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized."))
        if (userData.userId.isBlank()) {
            return Result.failure(Exception("User ID is required to sync data."))
        }

        val isOnlineNow = NetworkMonitor.isOnline(context)

        return try {
            val userDoc = db.collection("users").document(userData.userId)

            val habitsMapList = userData.habits.map { habit ->
                hashMapOf(
                    "id" to habit.id,
                    "title" to habit.title,
                    "targetDuration" to habit.targetDuration,
                    "isCompletedToday" to habit.isCompletedToday,
                    "streak" to habit.streak,
                    "createdTimestamp" to habit.createdTimestamp
                )
            }

            val journalMapList = userData.journalEntries.map { entry ->
                hashMapOf(
                    "id" to entry.id,
                    "dateText" to entry.dateText,
                    "mood" to entry.mood,
                    "note" to entry.note,
                    "timestamp" to entry.timestamp
                )
            }

            val rulesMapList = userData.blockRules.map { rule ->
                hashMapOf(
                    "id" to rule.id,
                    "name" to rule.name,
                    "ruleType" to rule.ruleType,
                    "target" to rule.target,
                    "category" to rule.category,
                    "enabled" to rule.enabled,
                    "isException" to rule.isException,
                    "priority" to rule.priority,
                    "scheduleDays" to rule.scheduleDays,
                    "scheduleStartTime" to rule.scheduleStartTime,
                    "scheduleEndTime" to rule.scheduleEndTime,
                    "dailyLimitMinutes" to rule.dailyLimitMinutes,
                    "createdAt" to rule.createdAt
                )
            }

            val schedulesMapList = userData.focusSchedules.map { sched ->
                hashMapOf(
                    "id" to sched.id,
                    "title" to sched.title,
                    "startTime" to sched.startTime,
                    "endTime" to sched.endTime,
                    "isDaily" to sched.isDaily,
                    "selectedDays" to sched.selectedDays,
                    "allowedApps" to sched.allowedApps,
                    "isEnabled" to sched.isEnabled
                )
            }

            val sessionsMapList = userData.focusSessions.take(100).map { sess ->
                hashMapOf(
                    "id" to sess.id,
                    "startTime" to sess.startTime,
                    "endTime" to sess.endTime,
                    "targetMinutes" to sess.targetMinutes,
                    "completedMinutes" to sess.completedMinutes,
                    "isCompleted" to sess.isCompleted,
                    "blockedAttemptsCount" to sess.blockedAttemptsCount,
                    "sessionNotes" to sess.sessionNotes
                )
            }

            val attemptsMapList = userData.blockAttempts.take(200).map { att ->
                hashMapOf(
                    "id" to att.id,
                    "timestamp" to att.timestamp,
                    "targetName" to att.targetName,
                    "ruleType" to att.ruleType,
                    "category" to att.category,
                    "wasOverridden" to att.wasOverridden
                )
            }

            val activeRequestsMapList = userData.activePartnerRequests.map { req ->
                partnerRequestToMap(req)
            }

            val profileMap = hashMapOf<String, Any?>(
                "userId" to userData.userId,
                "email" to userData.email,
                "displayName" to userData.displayName,
                "photoUrl" to (userData.photoUrl ?: ""),
                "authProvider" to userData.authProvider,
                "registeredAt" to userData.registeredAt,
                "lastLoginAt" to userData.lastLoginAt,
                "streakStartTime" to userData.streakStartTime,
                "currentStreakDays" to userData.currentStreakDays,
                "bestStreakDays" to userData.bestStreakDays,
                "lastRelapseTime" to userData.lastRelapseTime,
                "totalUrgesResisted" to userData.totalUrgesResisted,
                "totalFocusMinutes" to userData.totalFocusMinutes,
                "masterProtection" to userData.masterProtection,
                "vpnEnabled" to userData.vpnEnabled,
                "accessibilityEnabled" to userData.accessibilityEnabled,
                "adultContentBlocked" to userData.adultContentBlocked,
                "youtubeShortsBlocked" to userData.youtubeShortsBlocked,
                "instagramReelsBlocked" to userData.instagramReelsBlocked,
                "facebookReelsBlocked" to userData.facebookReelsBlocked,
                "imageVideoSearchBlocked" to userData.imageVideoSearchBlocked,
                "whatsappChannelsBlocked" to userData.whatsappChannelsBlocked,
                "uninstallProtection" to userData.uninstallProtection,
                "uninstallProtectionUntil" to userData.uninstallProtectionUntil,
                "uninstallProtectionDurationDays" to userData.uninstallProtectionDurationDays,
                "newAppsBlocked" to userData.newAppsBlocked,
                "unsupportedBrowsersBlocked" to userData.unsupportedBrowsersBlocked,
                "safeSearchEnforced" to userData.safeSearchEnforced,
                "audioChimeEnabled" to userData.audioChimeEnabled,
                "customBlockedMessage" to userData.customBlockedMessage,
                "blockedCountdownSeconds" to userData.blockedCountdownSeconds,
                "redirectUrl" to userData.redirectUrl,
                "blockedMediaType" to userData.blockedMediaType,
                "focusTimezone" to userData.focusTimezone,
                "accountabilityEnabled" to userData.accountabilityEnabled,
                "accountabilityPartnerEmail" to userData.accountabilityPartnerEmail,
                "accountabilityPartnerName" to userData.accountabilityPartnerName,
                "accountabilityNote" to userData.accountabilityNote,
                "accountabilityStartHour" to userData.accountabilityStartHour,
                "accountabilityStartMinute" to userData.accountabilityStartMinute,
                "accountabilityDurationMinutes" to userData.accountabilityDurationMinutes,
                "habitsList" to habitsMapList,
                "journalList" to journalMapList,
                "blockRulesList" to rulesMapList,
                "focusSchedulesList" to schedulesMapList,
                "focusSessionsList" to sessionsMapList,
                "blockAttemptsList" to attemptsMapList,
                "activePartnerRequestsList" to activeRequestsMapList,
                "lastSyncedAt" to System.currentTimeMillis()
            )

            profileMap["pendingPartnerRequest"] = userData.pendingPartnerRequest?.let { partnerRequestToMap(it) }
            lastSyncedSnapshotMemoryCache = userData

            val writeTask = userDoc.set(profileMap, SetOptions.merge())
            if (isOnlineNow) {
                runCatching {
                    withTimeoutOrNull(6000L) {
                        writeTask.await()
                    }
                }.onFailure { err ->
                    Log.i(tag, "Firestore cloud write queued/cached locally: ${err.message}")
                }
            }

            Log.d(tag, "Saved user data to Firestore (online=$isOnlineNow) for ${userData.userId}")
            Result.success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.i(tag, "Firestore exception handled via persistent cache: ${e.message}")
            Result.success(Unit)
        } catch (e: Exception) {
            if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                Result.success(Unit)
            } else {
                Log.e(tag, "Error saving data to Firestore", e)
                Result.failure(e)
            }
        }
    }

    private fun partnerRequestToMap(req: PartnerUnlockRequest): HashMap<String, Any?> {
        return hashMapOf(
            "requestId" to req.requestId,
            "actionKey" to req.actionKey,
            "userId" to req.userId,
            "targetType" to req.targetType,
            "targetDetail" to req.targetDetail,
            "ruleId" to (req.ruleId ?: 0L),
            "payload" to req.payload,
            "verificationCode" to req.verificationCode,
            "senderEmail" to req.senderEmail,
            "deliveredViaSmtp" to req.deliveredViaSmtp,
            "requestedAt" to req.requestedAt,
            "partnerEmail" to req.partnerEmail,
            "userDisplayName" to req.userDisplayName,
            "reason" to req.reason,
            "status" to req.status,
            "responseNote" to req.responseNote,
            "respondedAt" to req.respondedAt
        )
    }

    /**
     * Creates an accountability partner request in Firestore.
     */
    suspend fun createPartnerRequest(request: PartnerUnlockRequest): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            val reqMap = partnerRequestToMap(request)
            val isOnlineNow = NetworkMonitor.isOnline(context)

            val task1 = db.collection("partner_requests").document(request.requestId).set(reqMap)
            val task2 = db.collection("users").document(request.userId).set(
                mapOf("pendingPartnerRequest" to reqMap),
                SetOptions.merge()
            )
            if (isOnlineNow) {
                runCatching {
                    withTimeoutOrNull(5000L) {
                        task1.await()
                        task2.await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.i(tag, "Partner request cached locally: ${e.message}")
            Result.success(Unit)
        }
    }

    suspend fun clearPendingPartnerRequest(userId: String): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            val task = db.collection("users").document(userId).set(
                mapOf("pendingPartnerRequest" to null),
                SetOptions.merge()
            )
            if (NetworkMonitor.isOnline(context)) {
                runCatching {
                    withTimeoutOrNull(4000L) { task.await() }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * One-time fetch of full user profile from Firestore (supports both server and local cache).
     */
    suspend fun fetchUserData(userId: String): Result<UserSyncData?> {
        val db = firestore ?: return Result.success(lastSyncedSnapshotMemoryCache)
        if (userId.isBlank()) return Result.failure(Exception("User ID is required."))

        return try {
            val doc = runCatching {
                withTimeoutOrNull(6000L) {
                    db.collection("users").document(userId).get().await()
                }
            }.getOrElse {
                runCatching {
                    db.collection("users").document(userId).get(Source.CACHE).await()
                }.getOrNull()
            }

            if (doc == null || !doc.exists()) {
                return Result.success(lastSyncedSnapshotMemoryCache)
            }

            val data = parseUserSyncData(doc)
            lastSyncedSnapshotMemoryCache = data
            Result.success(data)
        } catch (e: Exception) {
            Log.i(tag, "Falling back to cached Firestore snapshot: ${e.message}")
            Result.success(lastSyncedSnapshotMemoryCache)
        }
    }

    suspend fun deleteUserData(userId: String): Result<Unit> {
        lastSyncedSnapshotMemoryCache = null
        val db = firestore ?: return Result.success(Unit)
        if (userId.isBlank()) return Result.success(Unit)
        return try {
            val task = db.collection("users").document(userId).delete()
            if (NetworkMonitor.isOnline(context)) {
                runCatching {
                    withTimeoutOrNull(5000L) { task.await() }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.i(tag, "Cleared user data locally: ${e.message}")
            Result.success(Unit)
        }
    }

    private fun parsePartnerRequestMap(pendingMap: Map<*, *>, fallbackUserId: String): PartnerUnlockRequest {
        val parsedRuleId = (pendingMap["ruleId"] as? Number)?.toLong() ?: 0L
        return PartnerUnlockRequest(
            requestId = pendingMap["requestId"] as? String ?: "",
            actionKey = pendingMap["actionKey"] as? String ?: "",
            userId = pendingMap["userId"] as? String ?: fallbackUserId,
            targetType = pendingMap["targetType"] as? String ?: "",
            targetDetail = pendingMap["targetDetail"] as? String ?: "",
            ruleId = if (parsedRuleId > 0L) parsedRuleId else null,
            payload = pendingMap["payload"] as? String ?: "",
            verificationCode = pendingMap["verificationCode"] as? String ?: "",
            senderEmail = (pendingMap["senderEmail"] as? String)?.let {
                if (it.equals("bayerahmmedyunus@gmail.com", ignoreCase = true) ||
                    it.equals("ubayerahmmedyunus@gmail.com", ignoreCase = true)
                ) "jubayerahmmedyunus@gmail.com" else it
            } ?: "jubayerahmmedyunus@gmail.com",
            deliveredViaSmtp = pendingMap["deliveredViaSmtp"] as? Boolean ?: false,
            requestedAt = (pendingMap["requestedAt"] as? Number)?.toLong() ?: 0L,
            partnerEmail = pendingMap["partnerEmail"] as? String ?: "",
            userDisplayName = pendingMap["userDisplayName"] as? String ?: "",
            reason = pendingMap["reason"] as? String ?: "",
            status = pendingMap["status"] as? String ?: "PENDING",
            responseNote = pendingMap["responseNote"] as? String ?: "",
            respondedAt = (pendingMap["respondedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun parseUserSyncData(doc: DocumentSnapshot): UserSyncData {
        val userId = doc.id

        val pendingMap = doc.get("pendingPartnerRequest") as? Map<*, *>
        val pendingPartnerReq = pendingMap?.let { parsePartnerRequestMap(it, userId) }

        val habitsRaw = doc.get("habitsList") as? List<*>
        val habits = habitsRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            HabitSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                title = m["title"] as? String ?: "",
                targetDuration = m["targetDuration"] as? String ?: "10 min",
                isCompletedToday = m["isCompletedToday"] as? Boolean ?: false,
                streak = (m["streak"] as? Number)?.toInt() ?: 0,
                createdTimestamp = (m["createdTimestamp"] as? Number)?.toLong() ?: 0L
            )
        } ?: emptyList()

        val journalRaw = doc.get("journalList") as? List<*>
        val journalEntries = journalRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            JournalSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                dateText = m["dateText"] as? String ?: "",
                mood = m["mood"] as? String ?: "Good",
                note = m["note"] as? String ?: "",
                timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L
            )
        } ?: emptyList()

        val rulesRaw = doc.get("blockRulesList") as? List<*>
        val blockRules = rulesRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            BlockRuleSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                name = m["name"] as? String ?: "",
                ruleType = m["ruleType"] as? String ?: "DOMAIN",
                target = m["target"] as? String ?: "",
                category = m["category"] as? String ?: "CUSTOM",
                enabled = m["enabled"] as? Boolean ?: true,
                isException = m["isException"] as? Boolean ?: false,
                priority = (m["priority"] as? Number)?.toInt() ?: 90,
                scheduleDays = m["scheduleDays"] as? String ?: "",
                scheduleStartTime = m["scheduleStartTime"] as? String ?: "",
                scheduleEndTime = m["scheduleEndTime"] as? String ?: "",
                dailyLimitMinutes = (m["dailyLimitMinutes"] as? Number)?.toInt() ?: 0,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
            )
        } ?: emptyList()

        val schedulesRaw = doc.get("focusSchedulesList") as? List<*>
        val focusSchedules = schedulesRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            FocusScheduleSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                title = m["title"] as? String ?: "",
                startTime = m["startTime"] as? String ?: "09:00",
                endTime = m["endTime"] as? String ?: "17:00",
                isDaily = m["isDaily"] as? Boolean ?: true,
                selectedDays = m["selectedDays"] as? String ?: "",
                allowedApps = m["allowedApps"] as? String ?: "",
                isEnabled = m["isEnabled"] as? Boolean ?: true
            )
        } ?: emptyList()

        val sessionsRaw = doc.get("focusSessionsList") as? List<*>
        val focusSessions = sessionsRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            FocusSessionSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                startTime = (m["startTime"] as? Number)?.toLong() ?: 0L,
                endTime = (m["endTime"] as? Number)?.toLong() ?: 0L,
                targetMinutes = (m["targetMinutes"] as? Number)?.toInt() ?: 25,
                completedMinutes = (m["completedMinutes"] as? Number)?.toInt() ?: 0,
                isCompleted = m["isCompleted"] as? Boolean ?: false,
                blockedAttemptsCount = (m["blockedAttemptsCount"] as? Number)?.toInt() ?: 0,
                sessionNotes = m["sessionNotes"] as? String ?: ""
            )
        } ?: emptyList()

        val attemptsRaw = doc.get("blockAttemptsList") as? List<*>
        val blockAttempts = attemptsRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            BlockAttemptSyncDto(
                id = (m["id"] as? Number)?.toLong() ?: 0L,
                timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L,
                targetName = m["targetName"] as? String ?: "",
                ruleType = m["ruleType"] as? String ?: "DOMAIN",
                category = m["category"] as? String ?: "CUSTOM",
                wasOverridden = m["wasOverridden"] as? Boolean ?: false
            )
        } ?: emptyList()

        val activeReqsRaw = doc.get("activePartnerRequestsList") as? List<*>
        val activePartnerRequests = activeReqsRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            parsePartnerRequestMap(m, userId)
        } ?: emptyList()

        return UserSyncData(
            userId = userId,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "",
            photoUrl = doc.getString("photoUrl"),
            authProvider = doc.getString("authProvider") ?: "google",
            registeredAt = doc.getLong("registeredAt") ?: System.currentTimeMillis(),
            lastLoginAt = doc.getLong("lastLoginAt") ?: System.currentTimeMillis(),
            streakStartTime = doc.getLong("streakStartTime") ?: 0L,
            currentStreakDays = doc.getLong("currentStreakDays")?.toInt() ?: 0,
            bestStreakDays = doc.getLong("bestStreakDays")?.toInt() ?: 0,
            lastRelapseTime = doc.getLong("lastRelapseTime") ?: 0L,
            totalUrgesResisted = doc.getLong("totalUrgesResisted")?.toInt() ?: 0,
            totalFocusMinutes = doc.getLong("totalFocusMinutes")?.toInt() ?: 0,
            habits = habits,
            journalEntries = journalEntries,
            blockRules = blockRules,
            focusSchedules = focusSchedules,
            focusSessions = focusSessions,
            blockAttempts = blockAttempts,
            activePartnerRequests = activePartnerRequests,
            masterProtection = doc.getBoolean("masterProtection") ?: true,
            vpnEnabled = doc.getBoolean("vpnEnabled") ?: true,
            accessibilityEnabled = doc.getBoolean("accessibilityEnabled") ?: true,
            adultContentBlocked = doc.getBoolean("adultContentBlocked") ?: false,
            youtubeShortsBlocked = doc.getBoolean("youtubeShortsBlocked") ?: false,
            instagramReelsBlocked = doc.getBoolean("instagramReelsBlocked") ?: false,
            facebookReelsBlocked = doc.getBoolean("facebookReelsBlocked") ?: false,
            imageVideoSearchBlocked = doc.getBoolean("imageVideoSearchBlocked") ?: false,
            whatsappChannelsBlocked = doc.getBoolean("whatsappChannelsBlocked") ?: false,
            uninstallProtection = doc.getBoolean("uninstallProtection") ?: false,
            uninstallProtectionUntil = doc.getLong("uninstallProtectionUntil") ?: 0L,
            uninstallProtectionDurationDays = doc.getLong("uninstallProtectionDurationDays")?.toInt() ?: 1,
            newAppsBlocked = doc.getBoolean("newAppsBlocked") ?: false,
            unsupportedBrowsersBlocked = doc.getBoolean("unsupportedBrowsersBlocked") ?: false,
            safeSearchEnforced = doc.getBoolean("safeSearchEnforced") ?: false,
            audioChimeEnabled = doc.getBoolean("audioChimeEnabled") ?: true,
            customBlockedMessage = doc.getString("customBlockedMessage") ?: "",
            blockedCountdownSeconds = doc.getLong("blockedCountdownSeconds")?.toInt() ?: 15,
            redirectUrl = doc.getString("redirectUrl") ?: "",
            blockedMediaType = doc.getString("blockedMediaType") ?: "BREATHING",
            focusTimezone = doc.getString("focusTimezone") ?: "",
            accountabilityEnabled = doc.getBoolean("accountabilityEnabled") ?: false,
            accountabilityPartnerEmail = doc.getString("accountabilityPartnerEmail") ?: "",
            accountabilityPartnerName = doc.getString("accountabilityPartnerName") ?: "",
            accountabilityNote = doc.getString("accountabilityNote") ?: "",
            accountabilityStartHour = doc.getLong("accountabilityStartHour")?.toInt() ?: 12,
            accountabilityStartMinute = doc.getLong("accountabilityStartMinute")?.toInt() ?: 0,
            accountabilityDurationMinutes = doc.getLong("accountabilityDurationMinutes")?.toInt() ?: 30,
            lastSyncedAt = doc.getLong("lastSyncedAt") ?: System.currentTimeMillis(),
            pendingPartnerRequest = pendingPartnerReq
        )
    }
}
