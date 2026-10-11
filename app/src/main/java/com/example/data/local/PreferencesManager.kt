package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.firebase.PartnerUnlockRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "self_control_prefs")

data class BlockMediaItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "VIDEO" or "IMAGE"
    val path: String,
    val name: String
)

private fun serializeMediaList(items: List<BlockMediaItem>): String {
    val array = org.json.JSONArray()
    for (item in items) {
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("type", item.type)
        obj.put("path", item.path)
        obj.put("name", item.name)
        array.put(obj)
    }
    return array.toString()
}

private fun deserializeMediaList(jsonStr: String): List<BlockMediaItem> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<BlockMediaItem>()
    try {
        val array = org.json.JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                BlockMediaItem(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    type = obj.optString("type", "VIDEO"),
                    path = obj.optString("path", ""),
                    name = obj.optString("name", "Media")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_MASTER_PROTECTION = booleanPreferencesKey("master_protection")
        val KEY_VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val KEY_ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
        val KEY_SHORTS_BLOCKED = booleanPreferencesKey("shorts_blocked")
        val KEY_SAFESEARCH_ENFORCED = booleanPreferencesKey("safesearch_enforced")
        val KEY_INSTALL_PROTECTION = booleanPreferencesKey("install_protection")
        val KEY_BROWSER_PROTECTION = booleanPreferencesKey("browser_protection")
        val KEY_AUDIO_CHIME = booleanPreferencesKey("audio_chime")
        val KEY_INTERVENTION_DURATION = intPreferencesKey("intervention_duration")
        val KEY_TEMP_UNLOCK_UNTIL = longPreferencesKey("temp_unlock_until")
        val KEY_DAILY_STREAK = intPreferencesKey("daily_streak")
        val KEY_STREAK_START_TIMESTAMP = longPreferencesKey("streak_start_timestamp")
        val KEY_BEST_STREAK_DAYS = intPreferencesKey("best_streak_days")
        val KEY_LAST_RELAPSE_TIMESTAMP = longPreferencesKey("last_relapse_timestamp")
        val KEY_LAST_CLOUD_SYNC_TIMESTAMP = longPreferencesKey("last_cloud_sync_timestamp")
        val KEY_LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val KEY_FOCUS_DURATION = intPreferencesKey("focus_duration")
        val KEY_LAST_SERVICE_RESTART = longPreferencesKey("last_service_restart")

        // Specific Content Protection Keys
        val KEY_ADULT_CONTENT_BLOCKED = booleanPreferencesKey("adult_content_blocked")
        val KEY_IMAGE_VIDEO_SEARCH_BLOCKED = booleanPreferencesKey("image_video_search_blocked")
        val KEY_GOOGLE_LENS_BLOCKED = booleanPreferencesKey("google_lens_blocked")
        val KEY_YOUTUBE_SHORTS_BLOCKED = booleanPreferencesKey("youtube_shorts_blocked")
        val KEY_WHATSAPP_CHANNELS_BLOCKED = booleanPreferencesKey("whatsapp_channels_blocked")
        val KEY_INSTAGRAM_REELS_BLOCKED = booleanPreferencesKey("instagram_reels_blocked")
        val KEY_FACEBOOK_REELS_BLOCKED = booleanPreferencesKey("facebook_reels_blocked")

        // Advanced Blocking Keys
        val KEY_UNINSTALL_PROTECTION = booleanPreferencesKey("uninstall_protection")
        val KEY_UNINSTALL_PROTECTION_UNTIL = longPreferencesKey("uninstall_protection_until")
        val KEY_UNINSTALL_PROTECTION_DURATION_DAYS = intPreferencesKey("uninstall_protection_duration_days")
        val KEY_UNSUPPORTED_BROWSERS_BLOCKED = booleanPreferencesKey("unsupported_browsers_blocked")
        val KEY_NEW_APPS_BLOCKED = booleanPreferencesKey("new_apps_blocked")
        val KEY_NEW_APPS_BLOCKED_SINCE = longPreferencesKey("new_apps_blocked_since")
        val KEY_BASELINE_INSTALLED_PACKAGES = stringSetPreferencesKey("baseline_installed_packages")
        val KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES = stringSetPreferencesKey("hidden_newly_installed_packages")


        // Blocked Screen Customization Keys
        val KEY_CUSTOM_BLOCKED_MESSAGE = stringPreferencesKey("custom_blocked_message")
        val KEY_BLOCKED_COUNTDOWN_SECONDS = intPreferencesKey("blocked_countdown_seconds")
        val KEY_REDIRECT_URL = stringPreferencesKey("redirect_url")
        val KEY_BLOCKED_MEDIA_TYPE = stringPreferencesKey("blocked_media_type")
        val KEY_BLOCK_SCREEN_BG_TYPE = stringPreferencesKey("block_screen_bg_type")
        val KEY_BLOCK_SCREEN_MEDIA_PATH = stringPreferencesKey("block_screen_media_path")
        val KEY_BLOCK_SCREEN_TITLE = stringPreferencesKey("block_screen_title")
        val KEY_BLOCK_SCREEN_SUBTITLE = stringPreferencesKey("block_screen_subtitle")
        val KEY_BLOCK_SCREEN_BUTTON_TEXT = stringPreferencesKey("block_screen_button_text")
        val KEY_BLOCK_SCREEN_ALIGNMENT = stringPreferencesKey("block_screen_alignment")
        val KEY_BLOCK_SCREEN_SHOW_STREAK = booleanPreferencesKey("block_screen_show_streak")
        val KEY_BLOCK_SCREEN_SHOW_LOCK = booleanPreferencesKey("block_screen_show_lock")
        val KEY_BLOCK_SCREEN_SHOW_TITLE = booleanPreferencesKey("block_screen_show_title")
        val KEY_BLOCK_SCREEN_SHOW_SUBTITLE = booleanPreferencesKey("block_screen_show_subtitle")
        val KEY_BLOCK_SCREEN_SHOW_BUTTON = booleanPreferencesKey("block_screen_show_button")
        val KEY_BLOCK_SCREEN_SHOW_SPEAKER = booleanPreferencesKey("block_screen_show_speaker")
        val KEY_BLOCK_SCREEN_SHOW_TIMER = booleanPreferencesKey("block_screen_show_timer")
        val KEY_BLOCK_SCREEN_SHOW_TAGLINE = booleanPreferencesKey("block_screen_show_tagline")
        val KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY = booleanPreferencesKey("block_screen_show_dark_overlay")
        val KEY_BLOCK_SCREEN_MUTED = booleanPreferencesKey("block_screen_muted")
        val KEY_BLOCK_SCREEN_MEDIA_LIST = stringPreferencesKey("block_screen_media_list")
        val KEY_PERSISTENT_BLOCK_SCREEN = booleanPreferencesKey("persistent_block_screen")

        // Accountability Keys
        val KEY_ACCOUNTABILITY_ENABLED = booleanPreferencesKey("accountability_enabled")
        val KEY_ACCOUNTABILITY_START_HOUR = intPreferencesKey("accountability_start_hour")
        val KEY_ACCOUNTABILITY_START_MINUTE = intPreferencesKey("accountability_start_minute")
        val KEY_ACCOUNTABILITY_DURATION_MINUTES = intPreferencesKey("accountability_duration_minutes")
        val KEY_ACCOUNTABILITY_PARTNER_NAME = stringPreferencesKey("accountability_partner_name")
        val KEY_ACCOUNTABILITY_PARTNER_EMAIL = stringPreferencesKey("accountability_partner_email")
        val KEY_ACCOUNTABILITY_NOTE = stringPreferencesKey("accountability_note")
        val KEY_FOCUS_TIMEZONE = stringPreferencesKey("focus_timezone")
        val KEY_ACTIVE_PARTNER_REQUESTS = stringSetPreferencesKey("active_partner_unlock_requests_v2")
        val KEY_PENDING_FIRESTORE_SYNC = booleanPreferencesKey("pending_firestore_sync")
    }

    private val cacheScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val syncPrefs = context.getSharedPreferences("block_screen_sync_cache", android.content.Context.MODE_PRIVATE)

    @Volatile var cachedMasterProtection: Boolean = true
        private set
    @Volatile var cachedVpnEnabled: Boolean = true
        private set
    @Volatile var cachedAccessibilityEnabled: Boolean = true
        private set
    @Volatile var cachedAdultContentBlocked: Boolean = false
        private set
    @Volatile var cachedImageVideoSearchBlocked: Boolean = false
        private set
    @Volatile var cachedGoogleLensBlocked: Boolean = false
        private set
    @Volatile var cachedYoutubeShortsBlocked: Boolean = false
        private set
    @Volatile var cachedWhatsappChannelsBlocked: Boolean = false
        private set
    @Volatile var cachedInstagramReelsBlocked: Boolean = false
        private set
    @Volatile var cachedFacebookReelsBlocked: Boolean = false
        private set
    @Volatile var cachedUninstallProtection: Boolean = syncPrefs.getBoolean("uninstall_protection", false)
        private set
    @Volatile var cachedUninstallProtectionUntil: Long = syncPrefs.getLong("uninstall_protection_until", 0L)
        private set
    @Volatile var cachedNewAppsBlocked: Boolean = false
        private set
    @Volatile var cachedUnsupportedBrowsersBlocked: Boolean = false
        private set
    @Volatile var cachedAccountabilityEnabled: Boolean = false
        private set
    @Volatile var cachedAccountabilityStartHour: Int = 12
        private set
    @Volatile var cachedAccountabilityStartMinute: Int = 0
        private set
    @Volatile var cachedAccountabilityDurationMinutes: Int = 30
        private set
    @Volatile var cachedTempUnlockUntil: Long = 0L
        private set
    @Volatile var cachedSafeSearchEnforced: Boolean = false
        private set
    @Volatile var cachedBlockScreenBgType: String = syncPrefs.getString("bg_type", "DEFAULT") ?: "DEFAULT"
        private set
    @Volatile var cachedBlockScreenMediaPath: String = syncPrefs.getString("media_path", "") ?: ""
        private set
    @Volatile var cachedBlockScreenMediaList: List<BlockMediaItem> = deserializeMediaList(syncPrefs.getString("media_list", "") ?: "")
        private set
    @Volatile var cachedBlockScreenShowDarkOverlay: Boolean = syncPrefs.getBoolean("dark_overlay", true)
        private set
    @Volatile var cachedBlockScreenVideoMuted: Boolean = syncPrefs.getBoolean("video_muted", false)
        private set
    @Volatile var cachedBlockScreenTitle: String = syncPrefs.getString("title", "") ?: ""
        private set
    @Volatile var cachedBlockScreenSubtitle: String = syncPrefs.getString("subtitle", "") ?: ""
        private set
    @Volatile var cachedBlockScreenButtonText: String = syncPrefs.getString("button_text", "") ?: ""
        private set
    @Volatile var cachedBlockScreenAlignment: String = syncPrefs.getString("alignment", "CENTER") ?: "CENTER"
        private set
    @Volatile var cachedBlockScreenShowStreak: Boolean = syncPrefs.getBoolean("show_streak", true)
        private set
    @Volatile var cachedBlockScreenShowLock: Boolean = syncPrefs.getBoolean("show_lock", true)
        private set
    @Volatile var cachedBlockScreenShowTitle: Boolean = syncPrefs.getBoolean("show_title", true)
        private set
    @Volatile var cachedBlockScreenShowSubtitle: Boolean = syncPrefs.getBoolean("show_subtitle", true)
        private set
    @Volatile var cachedBlockScreenShowButton: Boolean = syncPrefs.getBoolean("show_button", true)
        private set
    @Volatile var cachedBlockScreenShowSpeaker: Boolean = syncPrefs.getBoolean("show_speaker", true)
        private set
    @Volatile var cachedBlockScreenShowTimer: Boolean = syncPrefs.getBoolean("show_timer", true)
        private set
    @Volatile var cachedBlockScreenShowTagline: Boolean = syncPrefs.getBoolean("show_tagline", true)
        private set
    @Volatile var cachedPersistentBlockScreen: Boolean = syncPrefs.getBoolean("persistent_block_screen", true)
        private set

    init {
        cacheScope.launch {
            try {
                context.dataStore.data.collect { prefs ->
                    cachedMasterProtection = prefs[KEY_MASTER_PROTECTION] ?: true
                    cachedVpnEnabled = prefs[KEY_VPN_ENABLED] ?: true
                    cachedAccessibilityEnabled = prefs[KEY_ACCESSIBILITY_ENABLED] ?: true
                    cachedAdultContentBlocked = prefs[KEY_ADULT_CONTENT_BLOCKED] ?: false
                    cachedImageVideoSearchBlocked = prefs[KEY_IMAGE_VIDEO_SEARCH_BLOCKED] ?: false
                    cachedGoogleLensBlocked = prefs[KEY_GOOGLE_LENS_BLOCKED] ?: false
                    cachedYoutubeShortsBlocked = prefs[KEY_YOUTUBE_SHORTS_BLOCKED] ?: false
                    cachedWhatsappChannelsBlocked = prefs[KEY_WHATSAPP_CHANNELS_BLOCKED] ?: false
                    cachedInstagramReelsBlocked = prefs[KEY_INSTAGRAM_REELS_BLOCKED] ?: false
                    cachedFacebookReelsBlocked = prefs[KEY_FACEBOOK_REELS_BLOCKED] ?: false
                    cachedUninstallProtection = prefs[KEY_UNINSTALL_PROTECTION] ?: false
                    cachedUninstallProtectionUntil = prefs[KEY_UNINSTALL_PROTECTION_UNTIL] ?: 0L
                    cachedNewAppsBlocked = prefs[KEY_NEW_APPS_BLOCKED] ?: false
                    cachedUnsupportedBrowsersBlocked = prefs[KEY_UNSUPPORTED_BROWSERS_BLOCKED] ?: false
                    cachedAccountabilityEnabled = prefs[KEY_ACCOUNTABILITY_ENABLED] ?: false
                    cachedAccountabilityStartHour = prefs[KEY_ACCOUNTABILITY_START_HOUR] ?: 12
                    cachedAccountabilityStartMinute = prefs[KEY_ACCOUNTABILITY_START_MINUTE] ?: 0
                    cachedAccountabilityDurationMinutes = prefs[KEY_ACCOUNTABILITY_DURATION_MINUTES] ?: 30
                    cachedTempUnlockUntil = prefs[KEY_TEMP_UNLOCK_UNTIL] ?: 0L
                    cachedSafeSearchEnforced = prefs[KEY_SAFESEARCH_ENFORCED] ?: false
                    
                    val bg = prefs[KEY_BLOCK_SCREEN_BG_TYPE] ?: "DEFAULT"
                    val path = prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] ?: ""
                    val rawMediaList = prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] ?: ""
                    val list = if (rawMediaList.isNotBlank()) {
                        deserializeMediaList(rawMediaList)
                    } else {
                        if (path.isNotBlank() && (bg == "VIDEO" || bg == "IMAGE")) {
                            listOf(BlockMediaItem(type = bg, path = path, name = java.io.File(path).name))
                        } else {
                            emptyList()
                        }
                    }
                    val darkOverlay = prefs[KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY] ?: true
                    val muted = prefs[KEY_BLOCK_SCREEN_MUTED] ?: false
                    val title = prefs[KEY_BLOCK_SCREEN_TITLE] ?: ""
                    val subtitle = prefs[KEY_BLOCK_SCREEN_SUBTITLE] ?: ""
                    val btn = prefs[KEY_BLOCK_SCREEN_BUTTON_TEXT] ?: ""
                    val align = prefs[KEY_BLOCK_SCREEN_ALIGNMENT] ?: "CENTER"
                    val streak = prefs[KEY_BLOCK_SCREEN_SHOW_STREAK] ?: true
                    val lock = prefs[KEY_BLOCK_SCREEN_SHOW_LOCK] ?: true
                    val tVis = prefs[KEY_BLOCK_SCREEN_SHOW_TITLE] ?: true
                    val subVis = prefs[KEY_BLOCK_SCREEN_SHOW_SUBTITLE] ?: true
                    val btnVis = prefs[KEY_BLOCK_SCREEN_SHOW_BUTTON] ?: true
                    val spkVis = prefs[KEY_BLOCK_SCREEN_SHOW_SPEAKER] ?: true
                    val timVis = prefs[KEY_BLOCK_SCREEN_SHOW_TIMER] ?: true
                    val tagVis = prefs[KEY_BLOCK_SCREEN_SHOW_TAGLINE] ?: true

                    cachedBlockScreenBgType = bg
                    cachedBlockScreenMediaPath = path
                    cachedBlockScreenMediaList = list
                    cachedBlockScreenShowDarkOverlay = darkOverlay
                    cachedBlockScreenVideoMuted = muted
                    cachedBlockScreenTitle = title
                    cachedBlockScreenSubtitle = subtitle
                    cachedBlockScreenButtonText = btn
                    cachedBlockScreenAlignment = align
                    cachedBlockScreenShowStreak = streak
                    cachedBlockScreenShowLock = lock
                    cachedBlockScreenShowTitle = tVis
                    cachedBlockScreenShowSubtitle = subVis
                    cachedBlockScreenShowButton = btnVis
                    cachedBlockScreenShowSpeaker = spkVis
                    cachedBlockScreenShowTimer = timVis
                    cachedBlockScreenShowTagline = tagVis

                    syncPrefs.edit()
                        .putString("bg_type", bg)
                        .putString("media_path", path)
                        .putString("media_list", serializeMediaList(list))
                        .putBoolean("dark_overlay", darkOverlay)
                        .putBoolean("video_muted", muted)
                        .putString("title", title)
                        .putString("subtitle", subtitle)
                        .putString("button_text", btn)
                        .putString("alignment", align)
                        .putBoolean("show_streak", streak)
                        .putBoolean("show_lock", lock)
                        .putBoolean("show_title", tVis)
                        .putBoolean("show_subtitle", subVis)
                        .putBoolean("show_button", btnVis)
                        .putBoolean("show_speaker", spkVis)
                        .putBoolean("show_timer", timVis)
                        .putBoolean("show_tagline", tagVis)
                        .apply()
                }
            } catch (_: Throwable) {}
        }
    }

    val allPreferencesFlow: Flow<Preferences> = context.dataStore.data

    val pendingFirestoreSync: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PENDING_FIRESTORE_SYNC] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val masterProtection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MASTER_PROTECTION] ?: true
    }

    val vpnEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VPN_ENABLED] ?: true
    }

    val accessibilityEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESSIBILITY_ENABLED] ?: true
    }

    val shortsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHORTS_BLOCKED] ?: false
    }

    val safeSearchEnforced: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SAFESEARCH_ENFORCED] ?: false
    }

    val installProtection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_INSTALL_PROTECTION] ?: false
    }

    val browserProtection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BROWSER_PROTECTION] ?: false
    }

    val audioChimeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_CHIME] ?: true
    }

    val interventionDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_INTERVENTION_DURATION] ?: 15
    }

    val tempUnlockUntil: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEMP_UNLOCK_UNTIL] ?: 0L
    }

    val streakStartTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_STREAK_START_TIMESTAMP] ?: 0L
    }

    val bestStreakDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BEST_STREAK_DAYS] ?: 0
    }

    val lastRelapseTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_RELAPSE_TIMESTAMP] ?: 0L
    }

    val lastCloudSyncTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_CLOUD_SYNC_TIMESTAMP] ?: 0L
    }

    val dailyStreak: Flow<Int> = context.dataStore.data.map { prefs ->
        val start = prefs[KEY_STREAK_START_TIMESTAMP] ?: 0L
        if (start == 0L) {
            0
        } else {
            val diffMs = System.currentTimeMillis() - start
            val days = (diffMs / (24 * 60 * 60 * 1000L)).toInt()
            days.coerceAtLeast(0)
        }
    }

    val focusDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOCUS_DURATION] ?: 25
    }

    val lastServiceRestart: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SERVICE_RESTART] ?: System.currentTimeMillis()
    }

    // New flows
    val adultContentBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADULT_CONTENT_BLOCKED] ?: false
    }

    val imageVideoSearchBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMAGE_VIDEO_SEARCH_BLOCKED] ?: false
    }

    val googleLensBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GOOGLE_LENS_BLOCKED] ?: false
    }

    val youtubeShortsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_YOUTUBE_SHORTS_BLOCKED] ?: false
    }

    val whatsappChannelsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHATSAPP_CHANNELS_BLOCKED] ?: false
    }

    val instagramReelsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_INSTAGRAM_REELS_BLOCKED] ?: false
    }

    val facebookReelsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FACEBOOK_REELS_BLOCKED] ?: false
    }

    val uninstallProtection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val enabled = prefs[KEY_UNINSTALL_PROTECTION] ?: false
        val until = prefs[KEY_UNINSTALL_PROTECTION_UNTIL] ?: 0L
        if (enabled && until > 0L && System.currentTimeMillis() >= until) {
            false
        } else {
            enabled
        }
    }

    val uninstallProtectionUntil: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNINSTALL_PROTECTION_UNTIL] ?: 0L
    }

    val uninstallProtectionDurationDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNINSTALL_PROTECTION_DURATION_DAYS] ?: 1
    }

    val unsupportedBrowsersBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNSUPPORTED_BROWSERS_BLOCKED] ?: false
    }


    val newAppsBlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NEW_APPS_BLOCKED] ?: false
    }

    val newAppsBlockedSince: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_NEW_APPS_BLOCKED_SINCE] ?: 0L
    }

    val baselineInstalledPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASELINE_INSTALLED_PACKAGES] ?: emptySet()
    }

    val hiddenNewlyInstalledPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES] ?: emptySet()
    }

    val customBlockedMessage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_BLOCKED_MESSAGE] ?: "Take a breath. You chose to block this to stay focused."
    }

    val blockedCountdownSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_COUNTDOWN_SECONDS] ?: 15
    }

    val redirectUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_REDIRECT_URL] ?: ""
    }

    val blockedMediaType: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_MEDIA_TYPE] ?: "BREATHING"
    }

    val blockScreenBgType: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_BG_TYPE] ?: "DEFAULT"
    }

    val blockScreenMediaPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] ?: ""
    }

    val blockScreenTitle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_TITLE] ?: ""
    }

    val blockScreenSubtitle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SUBTITLE] ?: ""
    }

    val blockScreenButtonText: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_BUTTON_TEXT] ?: ""
    }

    val blockScreenAlignment: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_ALIGNMENT] ?: "CENTER"
    }

    val blockScreenShowStreak: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_STREAK] ?: true
    }

    val blockScreenShowLock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_LOCK] ?: true
    }

    val blockScreenShowTitle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_TITLE] ?: true
    }

    val blockScreenShowSubtitle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_SUBTITLE] ?: true
    }

    val blockScreenShowButton: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_BUTTON] ?: true
    }

    val blockScreenShowSpeaker: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_SPEAKER] ?: true
    }

    val persistentBlockScreen: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PERSISTENT_BLOCK_SCREEN] ?: true
    }

    val blockScreenShowTimer: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_TIMER] ?: true
    }

    val blockScreenShowTagline: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_TAGLINE] ?: true
    }

    val blockScreenShowDarkOverlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY] ?: true
    }

    val blockScreenVideoMuted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCK_SCREEN_MUTED] ?: false
    }

    val blockScreenMediaList: Flow<List<BlockMediaItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] ?: ""
        if (raw.isNotBlank()) {
            deserializeMediaList(raw)
        } else {
            val singlePath = prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] ?: ""
            val singleType = prefs[KEY_BLOCK_SCREEN_BG_TYPE] ?: "DEFAULT"
            if (singlePath.isNotBlank() && (singleType == "VIDEO" || singleType == "IMAGE")) {
                listOf(BlockMediaItem(type = singleType, path = singlePath, name = java.io.File(singlePath).name))
            } else {
                emptyList()
            }
        }
    }

    // Accountability flows
    val accountabilityEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_ENABLED] ?: false
    }

    val accountabilityStartHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_START_HOUR] ?: 12 // Default 12:00 PM
    }

    val accountabilityStartMinute: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_START_MINUTE] ?: 0
    }

    val accountabilityDurationMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_DURATION_MINUTES] ?: 30 // Default 30 min window
    }

    val accountabilityPartnerName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_PARTNER_NAME] ?: ""
    }

    val accountabilityPartnerEmail: Flow<String> = context.dataStore.data.map { prefs ->
        val raw = (prefs[KEY_ACCOUNTABILITY_PARTNER_EMAIL] ?: "").trim()
        if (raw.equals("bayerahmmedyunus@gmail.com", ignoreCase = true) ||
            raw.equals("ubayerahmmedyunus@gmail.com", ignoreCase = true)
        ) {
            "jubayerahmmedyunus@gmail.com"
        } else {
            raw
        }
    }

    val accountabilityNote: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNTABILITY_NOTE] ?: "I commit to keeping my boundaries strong."
    }

    val focusTimezone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOCUS_TIMEZONE] ?: ""
    }

    val activePartnerRequests: Flow<Map<String, PartnerUnlockRequest>> = context.dataStore.data.map { prefs ->
        val rawSet = prefs[KEY_ACTIVE_PARTNER_REQUESTS] ?: emptySet()
        val result = mutableMapOf<String, PartnerUnlockRequest>()
        rawSet.forEach { encoded ->
            val req = PartnerUnlockRequest.fromStorageString(encoded)
            if (req != null && req.actionKey.isNotBlank() && req.status == "PENDING" && req.deliveredViaSmtp) {
                result[req.actionKey] = req
            }
        }
        result
    }

    // Setters
    suspend fun setFocusTimezone(tzId: String) {
        context.dataStore.edit { it[KEY_FOCUS_TIMEZONE] = tzId }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMasterProtection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MASTER_PROTECTION] = enabled }
    }

    suspend fun setVpnEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VPN_ENABLED] = enabled }
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ACCESSIBILITY_ENABLED] = enabled }
    }

    suspend fun setShortsBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_SHORTS_BLOCKED] = blocked }
    }

    suspend fun setSafeSearchEnforced(enforced: Boolean) {
        context.dataStore.edit { it[KEY_SAFESEARCH_ENFORCED] = enforced }
    }

    suspend fun setInstallProtection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_INSTALL_PROTECTION] = enabled }
    }

    suspend fun setBrowserProtection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BROWSER_PROTECTION] = enabled }
    }

    suspend fun setAudioChime(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_CHIME] = enabled }
    }

    suspend fun setInterventionDuration(seconds: Int) {
        context.dataStore.edit { it[KEY_INTERVENTION_DURATION] = seconds }
    }

    suspend fun setAdultContentBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_ADULT_CONTENT_BLOCKED] = blocked }
    }

    suspend fun setImageVideoSearchBlocked(blocked: Boolean) {
        cachedImageVideoSearchBlocked = blocked
        context.dataStore.edit { it[KEY_IMAGE_VIDEO_SEARCH_BLOCKED] = blocked }
    }

    suspend fun setGoogleLensBlocked(blocked: Boolean) {
        cachedGoogleLensBlocked = blocked
        context.dataStore.edit { it[KEY_GOOGLE_LENS_BLOCKED] = blocked }
    }

    suspend fun setYoutubeShortsBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_YOUTUBE_SHORTS_BLOCKED] = blocked }
    }

    suspend fun setWhatsappChannelsBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_WHATSAPP_CHANNELS_BLOCKED] = blocked }
    }

    suspend fun setInstagramReelsBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_INSTAGRAM_REELS_BLOCKED] = blocked }
    }

    suspend fun setFacebookReelsBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_FACEBOOK_REELS_BLOCKED] = blocked }
    }

    suspend fun setUninstallProtectionConfig(enabled: Boolean, durationDays: Int = 1) {
        val safeDays = durationDays.coerceAtLeast(1)
        val untilTime = if (enabled) {
            System.currentTimeMillis() + (safeDays.toLong() * 24L * 60L * 60L * 1000L)
        } else {
            0L
        }
        cachedUninstallProtection = enabled
        cachedUninstallProtectionUntil = untilTime
        if (!enabled) {
            com.example.util.PermissionHelper.removeDeviceAdmin(context)
        }
        syncPrefs.edit()
            .putBoolean("uninstall_protection", enabled)
            .putLong("uninstall_protection_until", untilTime)
            .apply()

        context.dataStore.edit {
            it[KEY_UNINSTALL_PROTECTION] = enabled
            it[KEY_UNINSTALL_PROTECTION_UNTIL] = untilTime
            it[KEY_UNINSTALL_PROTECTION_DURATION_DAYS] = safeDays
        }
        if (!enabled) {
            com.example.util.PermissionHelper.removeDeviceAdmin(context)
        }
    }

    suspend fun checkAndExpireUninstallProtection(): Boolean {
        val enabled = cachedUninstallProtection
        val until = cachedUninstallProtectionUntil
        if (enabled && until > 0L && System.currentTimeMillis() >= until) {
            setUninstallProtectionConfig(enabled = false, durationDays = 1)
            return true
        }
        return false
    }

    suspend fun setUninstallProtection(enabled: Boolean) {
        if (enabled) {
            val currentUntil = context.dataStore.data.map { it[KEY_UNINSTALL_PROTECTION_UNTIL] ?: 0L }.first()
            val currentDays = context.dataStore.data.map { it[KEY_UNINSTALL_PROTECTION_DURATION_DAYS] ?: 1 }.first()
            if (currentUntil > System.currentTimeMillis()) {
                context.dataStore.edit { it[KEY_UNINSTALL_PROTECTION] = true }
            } else {
                setUninstallProtectionConfig(true, currentDays.coerceAtLeast(1))
            }
        } else {
            setUninstallProtectionConfig(false, 1)
        }
    }

    suspend fun setUnsupportedBrowsersBlocked(blocked: Boolean) {
        context.dataStore.edit { it[KEY_UNSUPPORTED_BROWSERS_BLOCKED] = blocked }
    }


    suspend fun setNewAppsBlocked(blocked: Boolean) {
        if (blocked) {
            val installedNow = com.example.domain.engine.NewAppGuardTracker.captureInstalledPackagesBaseline(context)
            val now = System.currentTimeMillis()
            context.dataStore.edit { prefs ->
                val wasAlreadyBlocked = prefs[KEY_NEW_APPS_BLOCKED] ?: false
                val existingSince = prefs[KEY_NEW_APPS_BLOCKED_SINCE] ?: 0L
                val existingBaseline = prefs[KEY_BASELINE_INSTALLED_PACKAGES] ?: emptySet()
                prefs[KEY_NEW_APPS_BLOCKED] = true
                if (!wasAlreadyBlocked || existingSince <= 0L || existingBaseline.isEmpty()) {
                    val effectiveNow = if (existingSince > 0L) existingSince else now
                    prefs[KEY_NEW_APPS_BLOCKED_SINCE] = effectiveNow
                    if (installedNow.isNotEmpty() && existingBaseline.isEmpty()) {
                        prefs[KEY_BASELINE_INSTALLED_PACKAGES] = installedNow
                        com.example.domain.engine.NewAppGuardTracker.setBaseline(installedNow, effectiveNow)
                    } else if (existingBaseline.isNotEmpty()) {
                        com.example.domain.engine.NewAppGuardTracker.setBaseline(existingBaseline, effectiveNow)
                    }
                }
            }
        } else {
            com.example.domain.engine.NewAppGuardTracker.clearAll()
            context.dataStore.edit { prefs ->
                prefs[KEY_NEW_APPS_BLOCKED] = false
                prefs[KEY_NEW_APPS_BLOCKED_SINCE] = 0L
                prefs.remove(KEY_BASELINE_INSTALLED_PACKAGES)
                prefs.remove(KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES)
            }
        }
    }

    suspend fun ensureNewAppsBaselineInitialized() {
        val prefsSnap = context.dataStore.data.first()
        val isBlocked = prefsSnap[KEY_NEW_APPS_BLOCKED] ?: false
        if (!isBlocked) return

        val existingSince = prefsSnap[KEY_NEW_APPS_BLOCKED_SINCE] ?: 0L
        val existingBaseline = prefsSnap[KEY_BASELINE_INSTALLED_PACKAGES] ?: emptySet()
        if (existingSince <= 0L || existingBaseline.isEmpty()) {
            val installedNow = com.example.domain.engine.NewAppGuardTracker.captureInstalledPackagesBaseline(context)
            val now = System.currentTimeMillis()
            context.dataStore.edit { prefs ->
                val since = if ((prefs[KEY_NEW_APPS_BLOCKED_SINCE] ?: 0L) > 0L) prefs[KEY_NEW_APPS_BLOCKED_SINCE]!! else now
                prefs[KEY_NEW_APPS_BLOCKED_SINCE] = since
                if ((prefs[KEY_BASELINE_INSTALLED_PACKAGES] ?: emptySet()).isEmpty() && installedNow.isNotEmpty()) {
                    prefs[KEY_BASELINE_INSTALLED_PACKAGES] = installedNow
                }
                com.example.domain.engine.NewAppGuardTracker.setBaseline(
                    prefs[KEY_BASELINE_INSTALLED_PACKAGES] ?: installedNow,
                    since
                )
            }
        } else {
            com.example.domain.engine.NewAppGuardTracker.setBaseline(existingBaseline, existingSince)
        }
    }

    suspend fun setCustomBlockedMessage(message: String) {
        context.dataStore.edit { it[KEY_CUSTOM_BLOCKED_MESSAGE] = message }
    }

    suspend fun setBlockedCountdownSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_BLOCKED_COUNTDOWN_SECONDS] = seconds }
    }

    suspend fun setRedirectUrl(url: String) {
        context.dataStore.edit { it[KEY_REDIRECT_URL] = url }
    }

    suspend fun setBlockScreenCustomization(
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
        val editor = syncPrefs.edit()
        bgType?.let { cachedBlockScreenBgType = it; editor.putString("bg_type", it) }
        mediaPath?.let { cachedBlockScreenMediaPath = it; editor.putString("media_path", it) }
        title?.let { cachedBlockScreenTitle = it; editor.putString("title", it) }
        subtitle?.let { cachedBlockScreenSubtitle = it; editor.putString("subtitle", it) }
        buttonText?.let { cachedBlockScreenButtonText = it; editor.putString("button_text", it) }
        alignment?.let { cachedBlockScreenAlignment = it; editor.putString("alignment", it) }
        showStreak?.let { cachedBlockScreenShowStreak = it; editor.putBoolean("show_streak", it) }
        showLock?.let { cachedBlockScreenShowLock = it; editor.putBoolean("show_lock", it) }
        showTitle?.let { cachedBlockScreenShowTitle = it; editor.putBoolean("show_title", it) }
        showSubtitle?.let { cachedBlockScreenShowSubtitle = it; editor.putBoolean("show_subtitle", it) }
        showButton?.let { cachedBlockScreenShowButton = it; editor.putBoolean("show_button", it) }
        showSpeaker?.let { cachedBlockScreenShowSpeaker = it; editor.putBoolean("show_speaker", it) }
        showTimer?.let { cachedBlockScreenShowTimer = it; editor.putBoolean("show_timer", it) }
        showTagline?.let { cachedBlockScreenShowTagline = it; editor.putBoolean("show_tagline", it) }
        showDarkOverlay?.let { cachedBlockScreenShowDarkOverlay = it; editor.putBoolean("dark_overlay", it) }
        videoMuted?.let { cachedBlockScreenVideoMuted = it; editor.putBoolean("video_muted", it) }
        editor.apply()

        context.dataStore.edit { prefs ->
            bgType?.let { prefs[KEY_BLOCK_SCREEN_BG_TYPE] = it }
            mediaPath?.let { prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = it }
            title?.let { prefs[KEY_BLOCK_SCREEN_TITLE] = it }
            subtitle?.let { prefs[KEY_BLOCK_SCREEN_SUBTITLE] = it }
            buttonText?.let { prefs[KEY_BLOCK_SCREEN_BUTTON_TEXT] = it }
            alignment?.let { prefs[KEY_BLOCK_SCREEN_ALIGNMENT] = it }
            showStreak?.let { prefs[KEY_BLOCK_SCREEN_SHOW_STREAK] = it }
            showLock?.let { prefs[KEY_BLOCK_SCREEN_SHOW_LOCK] = it }
            showTitle?.let { prefs[KEY_BLOCK_SCREEN_SHOW_TITLE] = it }
            showSubtitle?.let { prefs[KEY_BLOCK_SCREEN_SHOW_SUBTITLE] = it }
            showButton?.let { prefs[KEY_BLOCK_SCREEN_SHOW_BUTTON] = it }
            showSpeaker?.let { prefs[KEY_BLOCK_SCREEN_SHOW_SPEAKER] = it }
            showTimer?.let { prefs[KEY_BLOCK_SCREEN_SHOW_TIMER] = it }
            showTagline?.let { prefs[KEY_BLOCK_SCREEN_SHOW_TAGLINE] = it }
            showDarkOverlay?.let { prefs[KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY] = it }
            videoMuted?.let { prefs[KEY_BLOCK_SCREEN_MUTED] = it }
        }
    }

    suspend fun setPersistentBlockScreen(enabled: Boolean) {
        cachedPersistentBlockScreen = enabled
        syncPrefs.edit().putBoolean("persistent_block_screen", enabled).apply()
        context.dataStore.edit { it[KEY_PERSISTENT_BLOCK_SCREEN] = enabled }
    }

    suspend fun setBlockScreenVideoMuted(muted: Boolean) {
        cachedBlockScreenVideoMuted = muted
        syncPrefs.edit().putBoolean("video_muted", muted).apply()
        context.dataStore.edit { it[KEY_BLOCK_SCREEN_MUTED] = muted }
    }

    suspend fun setBlockScreenShowDarkOverlay(show: Boolean) {
        cachedBlockScreenShowDarkOverlay = show
        syncPrefs.edit().putBoolean("dark_overlay", show).apply()
        context.dataStore.edit { it[KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY] = show }
    }

    suspend fun resetBlockScreenCustomization() {
        cachedBlockScreenBgType = "DEFAULT"
        cachedBlockScreenMediaPath = ""
        cachedBlockScreenTitle = ""
        cachedBlockScreenSubtitle = ""
        cachedBlockScreenButtonText = ""
        cachedBlockScreenAlignment = "CENTER"
        cachedBlockScreenShowStreak = true
        cachedBlockScreenShowLock = true
        cachedBlockScreenShowTitle = true
        cachedBlockScreenShowSubtitle = true
        cachedBlockScreenShowButton = true
        cachedBlockScreenShowSpeaker = true
        cachedBlockScreenShowTimer = true
        cachedBlockScreenShowTagline = true
        cachedBlockScreenShowDarkOverlay = true
        cachedBlockScreenVideoMuted = false
        cachedBlockScreenMediaList = emptyList()

        syncPrefs.edit()
            .putString("bg_type", "DEFAULT")
            .putString("media_path", "")
            .putString("title", "")
            .putString("subtitle", "")
            .putString("button_text", "")
            .putString("alignment", "CENTER")
            .putBoolean("show_streak", true)
            .putBoolean("show_lock", true)
            .putBoolean("show_title", true)
            .putBoolean("show_subtitle", true)
            .putBoolean("show_button", true)
            .putBoolean("show_speaker", true)
            .putBoolean("show_timer", true)
            .putBoolean("show_tagline", true)
            .putBoolean("dark_overlay", true)
            .putBoolean("video_muted", false)
            .putString("media_list", "")
            .apply()

        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCK_SCREEN_BG_TYPE] = "DEFAULT"
            prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = ""
            prefs[KEY_BLOCK_SCREEN_TITLE] = ""
            prefs[KEY_BLOCK_SCREEN_SUBTITLE] = ""
            prefs[KEY_BLOCK_SCREEN_BUTTON_TEXT] = ""
            prefs[KEY_BLOCK_SCREEN_ALIGNMENT] = "CENTER"
            prefs[KEY_BLOCK_SCREEN_SHOW_STREAK] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_LOCK] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_TITLE] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_SUBTITLE] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_BUTTON] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_SPEAKER] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_TIMER] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_TAGLINE] = true
            prefs[KEY_BLOCK_SCREEN_SHOW_DARK_OVERLAY] = true
            prefs[KEY_BLOCK_SCREEN_MUTED] = false
            prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] = ""
        }
    }

    suspend fun addBlockMediaItems(newItems: List<BlockMediaItem>) {
        val currentList = cachedBlockScreenMediaList.toMutableList()
        currentList.addAll(newItems)
        val serialized = serializeMediaList(currentList)
        val firstType = if (currentList.isNotEmpty()) currentList.first().type else "DEFAULT"
        val firstPath = if (currentList.isNotEmpty()) currentList.first().path else ""

        cachedBlockScreenMediaList = currentList
        cachedBlockScreenBgType = firstType
        cachedBlockScreenMediaPath = firstPath

        syncPrefs.edit()
            .putString("media_list", serialized)
            .putString("bg_type", firstType)
            .putString("media_path", firstPath)
            .apply()

        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] = serialized
            if (currentList.isNotEmpty()) {
                prefs[KEY_BLOCK_SCREEN_BG_TYPE] = firstType
                prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = firstPath
            }
        }
    }

    suspend fun removeBlockMediaItem(id: String) {
        val currentList = cachedBlockScreenMediaList.filter { it.id != id }
        val serialized = serializeMediaList(currentList)
        val firstType = if (currentList.isNotEmpty()) currentList.first().type else "DEFAULT"
        val firstPath = if (currentList.isNotEmpty()) currentList.first().path else ""

        cachedBlockScreenMediaList = currentList
        cachedBlockScreenBgType = firstType
        cachedBlockScreenMediaPath = firstPath

        syncPrefs.edit()
            .putString("media_list", serialized)
            .putString("bg_type", firstType)
            .putString("media_path", firstPath)
            .apply()

        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] = serialized
            if (currentList.isEmpty()) {
                prefs[KEY_BLOCK_SCREEN_BG_TYPE] = "DEFAULT"
                prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = ""
            } else {
                prefs[KEY_BLOCK_SCREEN_BG_TYPE] = firstType
                prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = firstPath
            }
        }
    }

    suspend fun clearAllBlockMediaItems() {
        cachedBlockScreenMediaList = emptyList()
        cachedBlockScreenBgType = "DEFAULT"
        cachedBlockScreenMediaPath = ""

        syncPrefs.edit()
            .putString("media_list", "")
            .putString("bg_type", "DEFAULT")
            .putString("media_path", "")
            .apply()

        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCK_SCREEN_MEDIA_LIST] = ""
            prefs[KEY_BLOCK_SCREEN_BG_TYPE] = "DEFAULT"
            prefs[KEY_BLOCK_SCREEN_MEDIA_PATH] = ""
        }
    }

    suspend fun setAudioChimeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_CHIME] = enabled }
    }

    suspend fun addHiddenNewlyInstalledPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES] ?: emptySet()
            prefs[KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES] = current + packageName
        }
    }

    suspend fun removeHiddenNewlyInstalledPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES] ?: emptySet()
            prefs[KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES] = current - packageName
        }
    }

    suspend fun clearHiddenNewlyInstalledPackages() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_HIDDEN_NEWLY_INSTALLED_PACKAGES)
        }
    }

    suspend fun setBlockedMediaType(type: String) {
        context.dataStore.edit { it[KEY_BLOCKED_MEDIA_TYPE] = type }
    }

    suspend fun setTemporaryUnlock(durationMinutes: Int) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        context.dataStore.edit { it[KEY_TEMP_UNLOCK_UNTIL] = until }
    }

    suspend fun clearTemporaryUnlock() {
        context.dataStore.edit { it[KEY_TEMP_UNLOCK_UNTIL] = 0L }
    }

    suspend fun recordServiceRestart() {
        context.dataStore.edit { it[KEY_LAST_SERVICE_RESTART] = System.currentTimeMillis() }
    }

    suspend fun setStreakStartTime(timestamp: Long) {
        context.dataStore.edit { it[KEY_STREAK_START_TIMESTAMP] = timestamp }
    }

    suspend fun setStreakDays(days: Int) {
        val safeDays = days.coerceAtLeast(0)
        val calculatedStartTime = System.currentTimeMillis() - (safeDays.toLong() * 24 * 60 * 60 * 1000L)
        val currentBest = bestStreakDays.first()
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAK_START_TIMESTAMP] = calculatedStartTime
            if (safeDays > currentBest) {
                prefs[KEY_BEST_STREAK_DAYS] = safeDays
            }
        }
    }

    suspend fun resetStreakToZero(relapseTimestamp: Long = System.currentTimeMillis()) {
        val currentDays = dailyStreak.first()
        val currentBest = bestStreakDays.first()
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAK_START_TIMESTAMP] = relapseTimestamp
            prefs[KEY_LAST_RELAPSE_TIMESTAMP] = relapseTimestamp
            if (currentDays > currentBest) {
                prefs[KEY_BEST_STREAK_DAYS] = currentDays
            }
        }
    }

    suspend fun updateBestStreakIfHigher(days: Int) {
        val currentBest = bestStreakDays.first()
        if (days > currentBest) {
            context.dataStore.edit { it[KEY_BEST_STREAK_DAYS] = days }
        }
    }

    suspend fun recordCloudSyncTime(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[KEY_LAST_CLOUD_SYNC_TIMESTAMP] = timestamp }
    }

    fun isTemporaryUnlockActiveFast(): Boolean {
        return System.currentTimeMillis() < cachedTempUnlockUntil
    }

    suspend fun isTemporaryUnlockActive(): Boolean {
        return isTemporaryUnlockActiveFast()
    }

    suspend fun getRemainingUnlockSeconds(): Int {
        val until = cachedTempUnlockUntil
        val remaining = (until - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }

    suspend fun setAccountabilityEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ACCOUNTABILITY_ENABLED] = enabled }
    }

    suspend fun setAccountabilitySchedule(startHour: Int, startMinute: Int, durationMinutes: Int = 30) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNTABILITY_START_HOUR] = startHour
            prefs[KEY_ACCOUNTABILITY_START_MINUTE] = startMinute
            prefs[KEY_ACCOUNTABILITY_DURATION_MINUTES] = durationMinutes
        }
    }

    suspend fun setAccountabilityPartner(name: String, email: String, note: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNTABILITY_PARTNER_NAME] = name
            prefs[KEY_ACCOUNTABILITY_PARTNER_EMAIL] = email
            prefs[KEY_ACCOUNTABILITY_NOTE] = note
        }
    }

    suspend fun savePartnerUnlockRequest(request: PartnerUnlockRequest) {
        if (request.actionKey.isBlank()) return
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_ACTIVE_PARTNER_REQUESTS] ?: emptySet()
            val filtered = currentSet.filterNot { raw ->
                val existing = PartnerUnlockRequest.fromStorageString(raw)
                existing == null || existing.actionKey == request.actionKey
            }.toMutableSet()
            filtered.add(request.toStorageString())
            prefs[KEY_ACTIVE_PARTNER_REQUESTS] = filtered
        }
    }

    suspend fun removePartnerUnlockRequest(actionKey: String) {
        if (actionKey.isBlank()) return
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_ACTIVE_PARTNER_REQUESTS] ?: emptySet()
            val filtered = currentSet.filterNot { raw ->
                val existing = PartnerUnlockRequest.fromStorageString(raw)
                existing == null || existing.actionKey == actionKey
            }.toSet()
            prefs[KEY_ACTIVE_PARTNER_REQUESTS] = filtered
        }
    }

    suspend fun clearAllPartnerUnlockRequests() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_PARTNER_REQUESTS)
        }
    }

    suspend fun purgeUndeliveredPartnerRequests() {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_ACTIVE_PARTNER_REQUESTS] ?: emptySet()
            val validOnly = currentSet.filter { raw ->
                val existing = PartnerUnlockRequest.fromStorageString(raw)
                existing != null && existing.actionKey.isNotBlank() && existing.status == "PENDING" && existing.deliveredViaSmtp
            }.toSet()
            prefs[KEY_ACTIVE_PARTNER_REQUESTS] = validOnly
        }
    }

    suspend fun replaceActivePartnerRequests(requests: List<PartnerUnlockRequest>) {
        context.dataStore.edit { prefs ->
            val encodedSet = requests
                .filter { it.actionKey.isNotBlank() && it.status == "PENDING" && it.deliveredViaSmtp }
                .map { it.toStorageString() }
                .toSet()
            prefs[KEY_ACTIVE_PARTNER_REQUESTS] = encodedSet
        }
    }

    suspend fun setPendingFirestoreSync(pending: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_FIRESTORE_SYNC] = pending
        }
    }
}
