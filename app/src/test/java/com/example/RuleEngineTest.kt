package com.example

import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleType
import com.example.domain.engine.RuleEngine
import com.example.domain.engine.SafeSearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun testNormalizeDomain() {
        assertEquals("tiktok.com", RuleEngine.normalizeDomain("https://www.tiktok.com/"))
        assertEquals("pornhub.com", RuleEngine.normalizeDomain("http://pornhub.com:8080/browse"))
        assertEquals("reddit.com", RuleEngine.normalizeDomain("www.reddit.com"))
    }

    @Test
    fun testDomainMatches() {
        assertTrue(RuleEngine.domainMatches("m.tiktok.com", "tiktok.com"))
        assertTrue(RuleEngine.domainMatches("api.instagram.com", "instagram.com"))
        assertTrue(RuleEngine.domainMatches("tiktok.com", "tiktok.com"))
        assertFalse(RuleEngine.domainMatches("nottiktok.com", "tiktok.com"))
    }

    @Test
    fun testWhitelistPrecedence() {
        val rules = listOf(
            BlockRule(
                name = "Block Social",
                type = RuleType.CATEGORY,
                target = "SOCIAL_MEDIA",
                category = BlockCategory.SOCIAL_MEDIA,
                enabled = true
            ),
            BlockRule(
                name = "Allow LinkedIn for Job Search",
                type = RuleType.DOMAIN,
                target = "linkedin.com",
                category = BlockCategory.CUSTOM,
                enabled = true,
                isException = true
            )
        )

        // Reddit should be blocked by social category
        val redditResult = RuleEngine.evaluateDomain("reddit.com", rules)
        assertTrue(redditResult.isBlocked)

        // LinkedIn should be allowed because of whitelist exception rule
        val linkedInResult = RuleEngine.evaluateDomain("linkedin.com", rules)
        assertFalse(linkedInResult.isBlocked)
    }

    @Test
    fun testSafeSearchHelper() {
        assertNotNull(SafeSearchHelper.getSafeSearchIpForHost("google.com"))
        assertNotNull(SafeSearchHelper.getSafeSearchIpForHost("www.google.com"))
        assertNotNull(SafeSearchHelper.getSafeSearchIpForHost("bing.com"))
        assertNotNull(SafeSearchHelper.getSafeSearchIpForHost("youtube.com"))
        assertEquals(null, SafeSearchHelper.getSafeSearchIpForHost("wikipedia.org"))
    }

    @Test
    fun testShortsAndReelsDetection() {
        // YouTube Shorts
        val ytShorts = RuleEngine.evaluateShorts(
            packageName = "com.google.android.youtube",
            viewContentDescription = "Shorts",
            viewText = "Shorts feed",
            viewId = "com.google.android.youtube:id/reel_recycler",
            isYouTubeShortsBlocked = true
        )
        assertTrue(ytShorts.isBlocked)

        // Instagram Reels Full Player
        val igReels = RuleEngine.evaluateShorts(
            packageName = "com.instagram.android",
            viewContentDescription = "Reels",
            viewText = "Clips viewer",
            viewId = "com.instagram.android:id/clips_viewer_container",
            isInstagramReelsBlocked = true
        )
        assertTrue(igReels.isBlocked)

        // Instagram Reels Full Player by description ("Reel by creator")
        val igReelsByDesc = RuleEngine.evaluateShorts(
            packageName = "com.instagram.android",
            viewContentDescription = "Reel by nature_channel · Original audio",
            viewText = "",
            viewId = "com.instagram.android:id/row_feed_button",
            isInstagramReelsBlocked = true
        )
        assertTrue(igReelsByDesc.isBlocked)

        // Instagram Lite Reels Full Player
        val igLiteReels = RuleEngine.evaluateShorts(
            packageName = "com.instagram.lite",
            viewContentDescription = "Reel by travel_guide, Original audio",
            viewText = "Remix",
            viewId = "com.instagram.lite:id/video_root",
            isInstagramReelsBlocked = true
        )
        assertTrue(igLiteReels.isBlocked)

        // Instagram Main Feed post should NOT be blocked
        val igFeedPost = RuleEngine.evaluateShorts(
            packageName = "com.instagram.android",
            viewContentDescription = "Photo by john_doe",
            viewText = "Great view today!",
            viewId = "com.instagram.android:id/news_feed_row",
            isInstagramReelsBlocked = true
        )
        assertFalse(igFeedPost.isBlocked)

        // Facebook Reels Full Player
        val fbReels = RuleEngine.evaluateShorts(
            packageName = "com.facebook.katana",
            viewContentDescription = "Reels",
            viewText = "Short videos",
            viewId = "com.facebook.katana:id/reels_tab",
            isFacebookReelsBlocked = true
        )
        assertTrue(fbReels.isBlocked)

        // Facebook Reels Full Player by description ("Reel by creator")
        val fbReelsByDesc = RuleEngine.evaluateShorts(
            packageName = "com.facebook.katana",
            viewContentDescription = "Reel by funny_clips · Watch more reels",
            viewText = "Remix this reel",
            viewId = "com.facebook.katana:id/content",
            isFacebookReelsBlocked = true
        )
        assertTrue(fbReelsByDesc.isBlocked)

        // Facebook Lite Reels Full Player
        val fbLiteReels = RuleEngine.evaluateShorts(
            packageName = "com.facebook.lite",
            viewContentDescription = "Facebook Reels: Next reel, Remix reel",
            viewText = "Reels video",
            viewId = "com.facebook.lite:id/view",
            isFacebookReelsBlocked = true
        )
        assertTrue(fbLiteReels.isBlocked)

        // Facebook Main Feed post should NOT be blocked
        val fbFeedPost = RuleEngine.evaluateShorts(
            packageName = "com.facebook.katana",
            viewContentDescription = "News Feed: Post by Alice Smith",
            viewText = "What's on your mind?",
            viewId = "com.facebook.katana:id/news_feed",
            isFacebookReelsBlocked = true
        )
        assertFalse(fbFeedPost.isBlocked)
    }

    @Test
    fun testImageVideoSearchEvaluation() {
        val googleImageSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?tbm=isch&q=mountain",
            isImageVideoSearchBlocked = true
        )
        assertTrue(googleImageSearch.isBlocked)

        val googleModernImageSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?q=cars&udm=2",
            isImageVideoSearchBlocked = true
        )
        assertTrue(googleModernImageSearch.isBlocked)

        val googleModernVideoSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?q=trailer&udm=7",
            isImageVideoSearchBlocked = true
        )
        assertTrue(googleModernVideoSearch.isBlocked)

        val googleClassicVideoSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?q=trailer&tbm=vid",
            isImageVideoSearchBlocked = true
        )
        assertTrue(googleClassicVideoSearch.isBlocked)

        val bingVideoSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.bing.com/videos/search?q=test",
            isImageVideoSearchBlocked = true
        )
        assertTrue(bingVideoSearch.isBlocked)

        val ddgVideoSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://duckduckgo.com/?q=nature&ia=videos",
            isImageVideoSearchBlocked = true
        )
        assertTrue(ddgVideoSearch.isBlocked)

        val regularWebSearch = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?q=android+jetpack+compose",
            isImageVideoSearchBlocked = true
        )
        assertFalse(regularWebSearch.isBlocked)

        // Google Scan / Lens should NOT be blocked by evaluateImageVideoSearch!
        val googleLensUrl = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://lens.google.com",
            isImageVideoSearchBlocked = true
        )
        assertFalse(googleLensUrl.isBlocked)
    }

    @Test
    fun testWhatsAppChannelsEvaluation() {
        val updatesTab = RuleEngine.evaluateWhatsAppChannels(
            packageName = "com.whatsapp",
            viewContentDescription = "Updates",
            viewText = "Channels",
            viewId = "com.whatsapp:id/updates_tab",
            isWhatsAppChannelsBlocked = true
        )
        assertTrue(updatesTab.isBlocked)

        val chatsTab = RuleEngine.evaluateWhatsAppChannels(
            packageName = "com.whatsapp",
            viewContentDescription = "Chats",
            viewText = "Mom",
            viewId = "com.whatsapp:id/conversations_row",
            isWhatsAppChannelsBlocked = true
        )
        assertFalse(chatsTab.isBlocked)
    }

    @Test
    fun testUnsupportedBrowsersEvaluation() {
        val operaMiniEval = RuleEngine.evaluatePackage(
            packageName = "com.opera.mini.native",
            rules = emptyList(),
            isBlockUnsupportedBrowsers = true
        )
        assertTrue(operaMiniEval.isBlocked)

        val chromeEval = RuleEngine.evaluatePackage(
            packageName = "com.android.chrome",
            rules = emptyList(),
            isBlockUnsupportedBrowsers = true
        )
        assertFalse(chromeEval.isBlocked)
    }

    @Test
    fun testUninstallProtectionEvaluation() {
        // 1. Protection disabled: should never block
        val disabledAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Uninstall app",
            viewText = "Uninstall Reclaim",
            viewId = "com.android.settings:id/button1",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = true
        )
        assertFalse(disabledAttempt.isBlocked)

        // 2. Admin not active yet, but Uninstall Protection is enabled: MUST block uninstall attempt!
        val adminInactiveAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Uninstall app",
            viewText = "Uninstall Reclaim",
            viewId = "com.android.settings:id/button1",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = false
        )
        assertTrue(adminInactiveAttempt.isBlocked)

        // 3. Activating device admin: should never block
        val activationAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Activate device admin app",
            viewText = "Activate this device admin app Reclaim",
            viewId = "com.android.settings:id/action_button",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertFalse(activationAttempt.isBlocked)

        // 4. Protection enabled + Admin active + Trying to uninstall: MUST block
        val uninstallSettingsAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Uninstall app",
            viewText = "Reclaim Uninstall",
            viewId = "com.android.settings:id/button1",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(uninstallSettingsAttempt.isBlocked)

        // 5. Protection enabled + Admin active + Trying to turn off accessibility: MUST block
        val accessibilityTamperAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Reclaim Accessibility",
            viewText = "Turn off Use Reclaim accessibility service",
            viewId = "com.android.settings:id/switch_widget",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(accessibilityTamperAttempt.isBlocked)

        // 6. Protection enabled + Admin active + Trying to deactivate device admin: MUST block
        val deactivateAdminAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Deactivate",
            viewText = "Deactivate this device admin app Reclaim",
            viewId = "com.android.settings:id/action_button",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(deactivateAdminAttempt.isBlocked)

        // 7. Viewing Device Admin Apps list or Special App Access: should NEVER block
        val viewAdminListAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Device admin apps",
            viewText = "Device admin apps Reclaim Find My Device",
            viewId = "com.android.settings:id/list",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertFalse(viewAdminListAttempt.isBlocked)

        // 8. Samsung Galaxy A22 (com.samsung.accessibility) while Accountability is locked (even if Prevent Uninstall is off): MUST block turning off Accessibility
        val samsungAccAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.samsung.accessibility",
            viewContentDescription = "Installed apps",
            viewText = "Reclaim On Reclaim shortcut",
            viewId = "com.samsung.accessibility:id/switch_widget",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = false,
            isAccountabilityLocked = true
        )
        assertTrue(samsungAccAttempt.isBlocked)

        // 9. Prevent Uninstall is OFF while Accountability is locked: Uninstalling is NOT blocked by Accessibility
        val uninstallWhenPreventUninstallOff = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.google.android.packageinstaller",
            viewContentDescription = "Uninstall",
            viewText = "Do you want to uninstall Reclaim?",
            viewId = "android:id/button1",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = false,
            isAccountabilityLocked = true
        )
        assertFalse(uninstallWhenPreventUninstallOff.isBlocked)

        // 10. General Accessibility Settings page & Installed/Downloaded apps list page: MUST NOT block so user can change other apps
        val samsungInstalledAppsList = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.samsung.accessibility",
            viewContentDescription = "Navigate up",
            viewText = "Installed apps Reclaim On Bitwarden Off Auto Clicker Off",
            viewId = "com.samsung.accessibility:id/recycler_view",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true,
            isAccountabilityLocked = true
        )
        assertFalse(samsungInstalledAppsList.isBlocked)

        val pixelAccessibilityMainPage = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Accessibility",
            viewText = "Accessibility Downloaded apps Reclaim On TalkBack Off Display size and text",
            viewId = "com.android.settings:id/recycler_view",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true,
            isAccountabilityLocked = true
        )
        assertFalse(pixelAccessibilityMainPage.isBlocked)

        val otherAppAccessibilityPage = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Bitwarden",
            viewText = "Bitwarden Use Bitwarden Bitwarden shortcut Off",
            viewId = "com.android.settings:id/switch_bar",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true,
            isAccountabilityLocked = true
        )
        assertFalse(otherAppAccessibilityPage.isBlocked)

        // 11. Reclaim's own Accessibility page across device models (Pixel, Samsung, Xiaomi, Oppo/Realme, Vivo): MUST block
        val reclaimPageByDescription = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Reclaim",
            viewText = "Reclaim On Detects short-form video players (YouTube Shorts, Reels), distracting apps, and blocked websites to offer mindful pause interventions.",
            viewId = "",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = false,
            isAccountabilityLocked = true
        )
        assertTrue(reclaimPageByDescription.isBlocked)

        val reclaimPageCustomOemSwitchBar = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.coloros.settings",
            viewContentDescription = "Navigate up",
            viewText = "Reclaim On",
            viewId = "switch_bar",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = false,
            isAccountabilityLocked = true
        )
        assertTrue(reclaimPageCustomOemSwitchBar.isBlocked)

        val reclaimTurnOffDialog = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.samsung.accessibility",
            viewContentDescription = "",
            viewText = "Turn off Reclaim? Cancel Turn off",
            viewId = "android:id/button2",
            isUninstallProtectionEnabled = false,
            isDeviceAdminActive = false,
            isAccountabilityLocked = true
        )
        assertTrue(reclaimTurnOffDialog.isBlocked)
    }

    @Test
    fun testThreeLayerAdultDomainBlocking() {
        // Known curated & long-tail adult domains
        val knownAdult = RuleEngine.evaluateDomain("https://www.spankbang.com/video/123", emptyList(), false, isAdultBlocked = true)
        assertTrue(knownAdult.isBlocked)

        val longTailAdult = RuleEngine.evaluateDomain("https://m.javhd-free-hentai.net/watch", emptyList(), false, isAdultBlocked = true)
        assertTrue(longTailAdult.isBlocked)

        val adultTld = RuleEngine.evaluateDomain("https://livecams.xxx", emptyList(), false, isAdultBlocked = true)
        assertTrue(adultTld.isBlocked)

        // Safe Scunthorpe domains must NOT be blocked
        val safeEssex = RuleEngine.evaluateDomain("https://www.essex.ac.uk", emptyList(), false, isAdultBlocked = true)
        assertFalse(safeEssex.isBlocked)

        val safeSussex = RuleEngine.evaluateDomain("https://www.sussex.gov.uk", emptyList(), false, isAdultBlocked = true)
        assertFalse(safeSussex.isBlocked)
    }

    @Test
    fun testUnsupportedBrowserDetection() {
        val ucBrowser = RuleEngine.evaluateUnsupportedBrowser(
            packageName = "com.UCMobile.intl",
            isBlockUnsupportedBrowsersEnabled = true,
            isDetectedBrowser = true
        )
        assertTrue(ucBrowser.isBlocked)

        val viaBrowser = RuleEngine.evaluateUnsupportedBrowser(
            packageName = "mark.via.gp",
            isBlockUnsupportedBrowsersEnabled = true,
            isDetectedBrowser = false
        )
        assertTrue(viaBrowser.isBlocked)

        val chromeBrowser = RuleEngine.evaluateUnsupportedBrowser(
            packageName = "com.android.chrome",
            isBlockUnsupportedBrowsersEnabled = true,
            isDetectedBrowser = true
        )
        assertFalse(chromeBrowser.isBlocked)
    }

    @Test
    fun testSearchQueryAndMediaSearchBlocking() {
        val customKeywordEval = RuleEngine.evaluateSearchQuery(
            query = "https://www.google.com/search?q=badword+video&oq=badword",
            isSearchProtectionEnabled = true,
            customKeywords = listOf("badword"),
            isTemporaryUnlocked = false,
            isAdultBlocked = false
        )
        assertTrue(customKeywordEval.isBlocked)

        val adultQueryEval = RuleEngine.evaluateSearchQuery(
            query = "uncensored hentai videos - Google Search",
            isSearchProtectionEnabled = true,
            customKeywords = emptyList(),
            isTemporaryUnlocked = false,
            isAdultBlocked = true
        )
        assertTrue(adultQueryEval.isBlocked)

        val mediaQueryEval = RuleEngine.evaluateSearchQueryForMedia(
            query = "cute cats images",
            isImageVideoSearchBlocked = true
        )
        assertTrue(mediaQueryEval.isBlocked)

        val imageSearchUrlEval = RuleEngine.evaluateImageVideoSearch(
            urlOrQuery = "https://www.google.com/search?q=nature&udm=2",
            isImageVideoSearchBlocked = true
        )
        assertTrue(imageSearchUrlEval.isBlocked)
    }

    @Test
    fun testGoogleLensAndPictureScanEvaluation() {
        // Test Google Lens URLs
        val lensUrlEval = RuleEngine.evaluateGoogleLens(
            target = "https://lens.google.com/search?p=123",
            isGoogleLensBlocked = true
        )
        assertTrue(lensUrlEval.isBlocked)

        val searchByImageUrl = RuleEngine.evaluateGoogleLens(
            target = "https://www.google.com/searchbyimage?image_url=xyz",
            isGoogleLensBlocked = true
        )
        assertTrue(searchByImageUrl.isBlocked)

        val googleComLens = RuleEngine.evaluateGoogleLens(
            target = "https://www.google.com/lens?hl=en",
            isGoogleLensBlocked = true
        )
        assertTrue(googleComLens.isBlocked)

        // Test Google Lens standalone packages
        val standaloneLensPackage = RuleEngine.evaluateGoogleLens(
            target = "com.google.ar.lens",
            isGoogleLensBlocked = true
        )
        assertTrue(standaloneLensPackage.isBlocked)

        val googleSearchLensAction = RuleEngine.evaluateGoogleLens(
            target = "com.google.android.apps.search.lens.LensActivity",
            isGoogleLensBlocked = true
        )
        assertTrue(googleSearchLensAction.isBlocked)

        // Allowed when Google Lens blocking is disabled
        val allowedWhenDisabled = RuleEngine.evaluateGoogleLens(
            target = "https://lens.google.com",
            isGoogleLensBlocked = false
        )
        assertFalse(allowedWhenDisabled.isBlocked)
    }

    @Test
    fun testUninstallProtectionWithoutDeviceAdmin() {
        // Testing that even if Device Admin is not active, isUninstallProtectionEnabled blocks Settings App Info
        val appInfoAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Reclaim",
            viewText = "Reclaim Force stop Uninstall Permissions Storage & cache",
            viewId = "com.android.settings:id/button_uninstall",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = false
        )
        assertTrue(appInfoAttempt.isBlocked)

        // Testing that permission tampering and force stop ARE blocked when protection is active
        val permissionAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.google.android.permissioncontroller",
            viewContentDescription = "Reclaim permissions",
            viewText = "App permissions Reclaim Allowed Not allowed",
            viewId = "com.android.permissioncontroller:id/permission_list",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = false
        )
        assertTrue(permissionAttempt.isBlocked)

        val forceStopAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Force stop",
            viewText = "Force stop Reclaim",
            viewId = "com.android.settings:id/force_stop_button",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = false
        )
        assertTrue(forceStopAttempt.isBlocked)

        // 12. Deactivating Device Admin via android dialog popup: MUST block
        val deviceAdminDeactivateDialog = RuleEngine.evaluateUninstallAttempt(
            packageName = "android",
            viewContentDescription = "",
            viewText = "Deactivate device admin? Reclaim Cancel Deactivate",
            viewId = "android:id/button1",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(deviceAdminDeactivateDialog.isBlocked)

        // 13. Accessibility deactivation dialog: MUST block
        val accessibilityStopDialog = RuleEngine.evaluateUninstallAttempt(
            packageName = "android",
            viewContentDescription = "",
            viewText = "Stop service? Tapping Stop will turn off Reclaim. Cancel Stop",
            viewId = "android:id/button1",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(accessibilityStopDialog.isBlocked)

        // 14. Developer Options screen: MUST block
        val devOptionsAttempt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.android.settings",
            viewContentDescription = "Developer options",
            viewText = "Developer options USB debugging OEM unlocking",
            viewId = "com.android.settings:id/development_settings",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertTrue(devOptionsAttempt.isBlocked)

        // 15. Runtime permission prompt asking to grant permission (e.g. notifications): MUST NOT block
        val grantPermissionPrompt = RuleEngine.evaluateUninstallAttempt(
            packageName = "com.google.android.permissioncontroller",
            viewContentDescription = "",
            viewText = "Allow Reclaim to send you notifications? Allow Don't allow",
            viewId = "com.android.permissioncontroller:id/permission_allow_button",
            isUninstallProtectionEnabled = true,
            isDeviceAdminActive = true
        )
        assertFalse(grantPermissionPrompt.isBlocked)
    }
}
