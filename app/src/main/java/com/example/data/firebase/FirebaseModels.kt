package com.example.data.firebase

data class AuthUser(
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val isAnonymous: Boolean = false,
    val photoUrl: String? = null,
    val authProvider: String = "google" // "google", "email", "anonymous"
)

data class UserSyncData(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val authProvider: String = "google",
    val registeredAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val streakStartTime: Long = 0L,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastRelapseTime: Long = 0L,
    val totalUrgesResisted: Int = 0,
    val totalFocusMinutes: Int = 0,
    val habits: List<HabitSyncDto> = emptyList(),
    val journalEntries: List<JournalSyncDto> = emptyList(),
    val blockRules: List<BlockRuleSyncDto> = emptyList(),
    val focusSchedules: List<FocusScheduleSyncDto> = emptyList(),
    val focusSessions: List<FocusSessionSyncDto> = emptyList(),
    val blockAttempts: List<BlockAttemptSyncDto> = emptyList(),
    val activePartnerRequests: List<PartnerUnlockRequest> = emptyList(),
    val masterProtection: Boolean = true,
    val vpnEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = true,
    val adultContentBlocked: Boolean = true,
    val youtubeShortsBlocked: Boolean = true,
    val instagramReelsBlocked: Boolean = true,
    val facebookReelsBlocked: Boolean = true,
    val imageVideoSearchBlocked: Boolean = false,
    val whatsappChannelsBlocked: Boolean = false,
    val uninstallProtection: Boolean = true,
    val uninstallProtectionUntil: Long = 0L,
    val uninstallProtectionDurationDays: Int = 1,
    val newAppsBlocked: Boolean = false,
    val unsupportedBrowsersBlocked: Boolean = false,
    val safeSearchEnforced: Boolean = false,
    val audioChimeEnabled: Boolean = true,
    val customBlockedMessage: String = "",
    val blockedCountdownSeconds: Int = 15,
    val redirectUrl: String = "",
    val blockedMediaType: String = "BREATHING",
    val focusTimezone: String = "",
    val accountabilityEnabled: Boolean = false,
    val accountabilityPartnerEmail: String = "",
    val accountabilityPartnerName: String = "",
    val accountabilityNote: String = "",
    val accountabilityStartHour: Int = 12,
    val accountabilityStartMinute: Int = 0,
    val accountabilityDurationMinutes: Int = 30,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    // Partner unlock/disable request state
    val pendingPartnerRequest: PartnerUnlockRequest? = null
)

data class PartnerUnlockRequest(
    val requestId: String = "",
    val actionKey: String = "", // Unique key per locked item, e.g. "FILTER_YOUTUBE_SHORTS", "RULE_DELETE_5", "ACC_DISABLE_MODE"
    val userId: String = "",
    val targetType: String = "", // "SINGLE_ACTION", "DISABLE_RULE", "DELETE_RULE", "CHANGE_PARTNER_EMAIL", "CHANGE_SCHEDULE"
    val targetDetail: String = "", // e.g. "Turn Off 'Block YouTube Shorts'"
    val ruleId: Long? = null,
    val payload: String = "", // Optional action parameters (e.g. new partner email or new schedule time)
    val verificationCode: String = "", // 6-digit code sent only to partner email
    val senderEmail: String = "jubayerahmmedyunus@gmail.com",
    val deliveredViaSmtp: Boolean = false,
    val requestedAt: Long = System.currentTimeMillis(),
    val partnerEmail: String = "",
    val userDisplayName: String = "",
    val reason: String = "",
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED", "APPLIED"
    val responseNote: String = "",
    val respondedAt: Long = 0L
) {
    fun toStorageString(): String {
        fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
        return listOf(
            enc(requestId),
            enc(actionKey),
            enc(userId),
            enc(targetType),
            enc(targetDetail),
            (ruleId ?: -1L).toString(),
            enc(payload),
            enc(verificationCode),
            enc(senderEmail),
            deliveredViaSmtp.toString(),
            requestedAt.toString(),
            enc(partnerEmail),
            enc(userDisplayName),
            enc(reason),
            enc(status),
            enc(responseNote),
            respondedAt.toString()
        ).joinToString("|")
    }

    companion object {
        fun fromStorageString(raw: String): PartnerUnlockRequest? {
            return try {
                fun dec(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")
                val parts = raw.split("|")
                if (parts.size < 17) return null
                val parsedRuleId = parts[5].toLongOrNull() ?: -1L
                val rawSender = dec(parts[8])
                val normalizedSender = if (
                    rawSender.equals("bayerahmmedyunus@gmail.com", ignoreCase = true) ||
                    rawSender.equals("ubayerahmmedyunus@gmail.com", ignoreCase = true)
                ) {
                    "jubayerahmmedyunus@gmail.com"
                } else {
                    rawSender
                }
                PartnerUnlockRequest(
                    requestId = dec(parts[0]),
                    actionKey = dec(parts[1]),
                    userId = dec(parts[2]),
                    targetType = dec(parts[3]),
                    targetDetail = dec(parts[4]),
                    ruleId = if (parsedRuleId >= 0L) parsedRuleId else null,
                    payload = dec(parts[6]),
                    verificationCode = dec(parts[7]),
                    senderEmail = normalizedSender,
                    deliveredViaSmtp = parts[9].toBoolean(),
                    requestedAt = parts[10].toLongOrNull() ?: 0L,
                    partnerEmail = dec(parts[11]),
                    userDisplayName = dec(parts[12]),
                    reason = dec(parts[13]),
                    status = dec(parts[14]),
                    responseNote = dec(parts[15]),
                    respondedAt = parts[16].toLongOrNull() ?: 0L
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class HabitSyncDto(
    val id: Long = 0L,
    val title: String = "",
    val targetDuration: String = "",
    val isCompletedToday: Boolean = false,
    val streak: Int = 0,
    val createdTimestamp: Long = 0L
)

data class JournalSyncDto(
    val id: Long = 0L,
    val dateText: String = "",
    val mood: String = "",
    val note: String = "",
    val timestamp: Long = 0L
)

data class BlockRuleSyncDto(
    val id: Long = 0L,
    val name: String = "",
    val ruleType: String = "DOMAIN", // DOMAIN, APP, KEYWORD, etc.
    val target: String = "",
    val category: String = "CUSTOM",
    val enabled: Boolean = true,
    val isException: Boolean = false,
    val priority: Int = 90,
    val scheduleDays: String = "",
    val scheduleStartTime: String = "",
    val scheduleEndTime: String = "",
    val dailyLimitMinutes: Int = 0,
    val createdAt: Long = 0L
)

data class FocusScheduleSyncDto(
    val id: Long = 0L,
    val title: String = "",
    val startTime: String = "09:00",
    val endTime: String = "17:00",
    val isDaily: Boolean = true,
    val selectedDays: String = "",
    val allowedApps: String = "",
    val isEnabled: Boolean = true
)

data class FocusSessionSyncDto(
    val id: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val targetMinutes: Int = 25,
    val completedMinutes: Int = 0,
    val isCompleted: Boolean = false,
    val blockedAttemptsCount: Int = 0,
    val sessionNotes: String = ""
)

data class BlockAttemptSyncDto(
    val id: Long = 0L,
    val timestamp: Long = 0L,
    val targetName: String = "",
    val ruleType: String = "DOMAIN",
    val category: String = "CUSTOM",
    val wasOverridden: Boolean = false
)

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val lastSyncedTimeText: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
