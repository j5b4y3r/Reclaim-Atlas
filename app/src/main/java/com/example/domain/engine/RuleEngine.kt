package com.example.domain.engine

import com.example.data.local.DefaultBlocklistData
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleType
import java.util.Calendar

data class EvaluationResult(
    val isBlocked: Boolean,
    val matchedRule: BlockRule? = null,
    val category: BlockCategory = BlockCategory.CUSTOM,
    val reason: String = ""
)

object RuleEngine {

    /**
     * Checks whether a rule is currently active based on day of week and time of day.
     */
    fun isScheduleActive(rule: BlockRule, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (!rule.enabled) return false

        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, ..., 7=Saturday
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Day of week check
        if (rule.scheduleDays.isNotBlank()) {
            val allowedDays = rule.scheduleDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (allowedDays.isNotEmpty() && !allowedDays.contains(currentDay)) {
                return false
            }
        }

        // Time of day check (supports overnight ranges e.g. 22:00 -> 07:00)
        if (rule.scheduleStartTime.isNotBlank() && rule.scheduleEndTime.isNotBlank()) {
            val startMinutes = parseTimeToMinutes(rule.scheduleStartTime)
            val endMinutes = parseTimeToMinutes(rule.scheduleEndTime)

            if (startMinutes != null && endMinutes != null) {
                if (startMinutes <= endMinutes) {
                    if (currentTotalMinutes < startMinutes || currentTotalMinutes > endMinutes) {
                        return false
                    }
                } else {
                    if (currentTotalMinutes < startMinutes && currentTotalMinutes > endMinutes) {
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun parseTimeToMinutes(timeStr: String): Int? {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /**
     * Normalizes a host/domain name to strip scheme, port, query parameters, path, and www prefix.
     */
    fun normalizeDomain(domain: String): String {
        var clean = domain.trim().lowercase()
        if (clean.startsWith("http://")) clean = clean.substring(7)
        if (clean.startsWith("https://")) clean = clean.substring(8)
        val questionIndex = clean.indexOf('?')
        if (questionIndex != -1) clean = clean.substring(0, questionIndex)
        val hashIndex = clean.indexOf('#')
        if (hashIndex != -1) clean = clean.substring(0, hashIndex)
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) clean = clean.substring(0, slashIndex)
        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) clean = clean.substring(0, colonIndex)
        if (clean.startsWith("www.")) clean = clean.substring(4)
        return clean.trimEnd('.')
    }

    /**
     * Checks if a domain/URL matches a rule target.
     * Handles:
     * - Exact matches (e.g. facebook.com == facebook.com)
     * - Subdomains (e.g. subdomain.maindomain.com matches maindomain.com)
     * - Specific subdomain target rules (e.g. rule for subdomain.maindomain.com matches subdomain.maindomain.com and a.subdomain.maindomain.com)
     * - Reverse subdomains and path-stripped containment
     */
    fun domainMatches(queryDomain: String, ruleTarget: String): Boolean {
        val q = normalizeDomain(queryDomain)
        val t = normalizeDomain(ruleTarget)
        if (q.isBlank() || t.isBlank()) return false
        if (q == t) return true
        if (q.endsWith(".$t")) return true
        if (t.endsWith(".$q")) return true
        if (q.contains('.') && t.contains('.')) {
            if (q.contains(".$t.") || q.startsWith("$t.")) return true
            if (t.contains(".$q.") || t.startsWith("$q.")) return true
        }
        return false
    }

    /**
     * Evaluates a requested domain against all enabled rules and categories.
     */
    fun evaluateDomain(
        domain: String,
        rules: List<BlockRule>,
        isTemporaryUnlocked: Boolean = false,
        isAdultBlocked: Boolean = false,
        isSearchProtectionEnabled: Boolean = true
    ): EvaluationResult {
        if (isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false, reason = "Temporary unlock is active")
        }

        val normalized = normalizeDomain(domain)

        // 1. Check Whitelist / Exceptions first (priority)
        for (rule in rules) {
            if (rule.enabled && rule.isException && rule.type == RuleType.DOMAIN) {
                if (domainMatches(normalized, rule.target)) {
                    return EvaluationResult(isBlocked = false, matchedRule = rule, reason = "Whitelisted domain")
                }
            }
        }

        // 1B. Check for unsupported search engines to prevent filter bypass
        val unsupportedSearchEval = evaluateUnsupportedSearchEngine(normalized, isSearchProtectionEnabled)
        if (unsupportedSearchEval.isBlocked) {
            return unsupportedSearchEval
        }

        // 2. Check explicitly defined domain rules
        for (rule in rules) {
            if (rule.enabled && !rule.isException && rule.type == RuleType.DOMAIN && isScheduleActive(rule)) {
                if (domainMatches(normalized, rule.target)) {
                    return EvaluationResult(
                        isBlocked = true,
                        matchedRule = rule,
                        category = rule.category,
                        reason = "Domain blocked: ${rule.name}"
                    )
                }
            }
        }

        // 3. Check adult content blocking (Layer 2: 500,000+ local adult domain database & structural engine + cached DNS blocks)
        if (isAdultBlocked) {
            if (AdultBlockingEngine.isAdultDomainSync(normalized) ||
                DefaultBlocklistData.ADULT_DOMAINS.any { domainMatches(normalized, it) }
            ) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.ADULT,
                    reason = "Adult website blocked"
                )
            }
        }

        // 4. Check category rules against curated defaults
        for (rule in rules) {
            if (rule.enabled && !rule.isException && rule.type == RuleType.CATEGORY && isScheduleActive(rule)) {
                when (rule.category) {
                    BlockCategory.ADULT -> {
                        if (AdultBlockingEngine.isAdultDomainSync(normalized) ||
                            DefaultBlocklistData.ADULT_DOMAINS.any { domainMatches(normalized, it) }
                        ) {
                            return EvaluationResult(
                                isBlocked = true,
                                matchedRule = rule,
                                category = BlockCategory.ADULT,
                                reason = "Adult content blocked"
                            )
                        }
                    }
                    BlockCategory.GAMBLING -> {
                        if (DefaultBlocklistData.GAMBLING_DOMAINS.any { domainMatches(normalized, it) }) {
                            return EvaluationResult(
                                isBlocked = true,
                                matchedRule = rule,
                                category = BlockCategory.GAMBLING,
                                reason = "Gambling site blocked"
                            )
                        }
                    }
                    BlockCategory.SOCIAL_MEDIA -> {
                        if (DefaultBlocklistData.SOCIAL_MEDIA_DOMAINS.any { domainMatches(normalized, it) }) {
                            return EvaluationResult(
                                isBlocked = true,
                                matchedRule = rule,
                                category = BlockCategory.SOCIAL_MEDIA,
                                reason = "Social media site blocked"
                            )
                        }
                    }
                    BlockCategory.SHORT_VIDEO -> {
                        if (DefaultBlocklistData.SHORT_VIDEO_TARGETS.any { domainMatches(normalized, it) }) {
                            return EvaluationResult(
                                isBlocked = true,
                                matchedRule = rule,
                                category = BlockCategory.SHORT_VIDEO,
                                reason = "Short-form video site blocked"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Multi-layer asynchronous domain evaluation (combines Layer 2 local 500,000+ adult domain DB
     * + Layer 1 Cloudflare Family DNS 1.1.1.3 / 1.0.0.3 verification).
     */
    suspend fun evaluateDomainMultiLayer(
        domain: String,
        rules: List<BlockRule>,
        isTemporaryUnlocked: Boolean = false,
        isAdultBlocked: Boolean = false
    ): EvaluationResult {
        val syncResult = evaluateDomain(domain, rules, isTemporaryUnlocked, isAdultBlocked)
        if (syncResult.isBlocked || isTemporaryUnlocked) {
            return syncResult
        }

        val normalized = normalizeDomain(domain)
        if (normalized.isBlank() || !normalized.contains('.')) {
            return syncResult
        }

        for (rule in rules) {
            if (rule.enabled && rule.isException && rule.type == RuleType.DOMAIN && domainMatches(normalized, rule.target)) {
                return EvaluationResult(isBlocked = false, matchedRule = rule, reason = "Whitelisted domain")
            }
        }

        val isAdultCategoryEnabled = rules.any {
            it.enabled && !it.isException && it.type == RuleType.CATEGORY && it.category == BlockCategory.ADULT && isScheduleActive(it)
        }

        if (isAdultBlocked || isAdultCategoryEnabled) {
            if (AdultBlockingEngine.isAdultDomainMultiLayer(normalized)) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.ADULT,
                    reason = "Adult website blocked (3-Layer Filter)"
                )
            }
        }

        return EvaluationResult(isBlocked = false)
    }

    private val VIDEO_SEARCH_QUERY_TOKENS = setOf(
        "video", "videos", "vid", "vids", "shorts", "reels", "clips", "clip",
        "mp4", "webm", "mkv", "avi", "vidéo", "vidéos", "vídeo", "vídeos", "ভিডিও",
        "watch", "trailer", "movie", "movies", "film", "films", "livestream", "stream",
        "streams", "youtube", "tiktok", "vimeo", "dailymotion", "rumble", "bilibili"
    )

    private val IMAGE_SEARCH_QUERY_TOKENS = setOf(
        "image", "images", "img", "imgs", "pic", "pics", "photo", "photos",
        "picture", "pictures", "wallpaper", "wallpapers", "gif", "gifs", "ছবি", "চিত্র",
        "png", "jpg", "jpeg", "svg", "backgrounds", "gallery", "illustration", "illustrations",
        "drawing", "drawings", "portrait", "portraits", "meme", "memes", "screenshot", "screenshots",
        "lookbook", "imágenes", "bilder", "imagens", "lockscreen"
    )

    /**
     * Checks if a search query text on a search engine is explicitly searching for videos or images.
     */
    fun evaluateSearchQueryForMedia(
        query: String,
        isImageVideoSearchBlocked: Boolean,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (!isImageVideoSearchBlocked || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }
        val clean = query.lowercase()
            .removeSuffix("- google search")
            .removeSuffix("- bing")
            .removeSuffix("- duckduckgo")
            .trim()
        val tokens = clean.split(Regex("[^a-zA-Z0-9éí]+")).filter { it.isNotBlank() }.toSet()
        if (tokens.any { VIDEO_SEARCH_QUERY_TOKENS.contains(it) }) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Video search is blocked"
            )
        }
        if (tokens.any { IMAGE_SEARCH_QUERY_TOKENS.contains(it) }) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Image search is blocked"
            )
        }
        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates whether a URL in a browser address bar or search app is an image or video search.
     */
    fun evaluateImageVideoSearch(
        urlOrQuery: String,
        isImageVideoSearchBlocked: Boolean,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (!isImageVideoSearchBlocked || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }

        val lower = urlOrQuery.lowercase().trim()
        if (lower.contains("gemini.google.com") || lower.contains("bard.google.com")) {
            return EvaluationResult(isBlocked = false)
        }

        // 1. Google Images & Videos (udm=7 for videos, udm=39/15 for short videos, udm=2 for images, #fpstate=ive & vld= for inline video player, tbm=vid, tbm=isch)
        val isGoogleSearchUrl = (lower.contains("google.") || lower.startsWith("search?") || lower.startsWith("/search?") || (lower.contains("google.") && lower.contains("/search"))) && !lower.contains("youtube.com") && !lower.contains("tiktok.com") && !lower.contains("vimeo") && !lower.contains("snaptube") && !lower.contains("dailymotion")
        if (isGoogleSearchUrl) {
            val isGoogleImage = lower.contains("udm=2") ||
                    lower.contains("tbm=isch") ||
                    lower.contains("/imghp") ||
                    lower.contains("images.google") ||
                    lower.contains("image.google") ||
                    lower.contains("source=lnms&tbm=isch")

            val isGoogleVideo = lower.contains("udm=7") ||
                    lower.contains("udm=39") ||
                    lower.contains("udm=15") ||
                    lower.contains("udm=48") ||
                    lower.contains("udm=44") ||
                    lower.contains("udm=12") ||
                    lower.contains("udm=19") ||
                    lower.contains("tbm=vid") ||
                    lower.contains("/videohp") ||
                    lower.contains("video.google") ||
                    lower.contains("videos.google") ||
                    lower.contains("source=lnms&tbm=vid") ||
                    lower.contains("fpstate=ive") ||
                    lower.contains("vld=cid") ||
                    lower.contains("&vld=") ||
                    lower.contains("tbs=dur") ||
                    lower.contains("tbs=vid") ||
                    (lower.contains("google.") && (lower.contains("/video") || lower.contains("/videos")))

            if (isGoogleVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isGoogleImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 2. Bing Images & Videos
        if (lower.contains("bing.com")) {
            val isBingImage = lower.contains("/images") ||
                    lower.contains("scope=images") ||
                    lower.contains("scope=image")
            val isBingVideo = lower.contains("/videos") ||
                    lower.contains("scope=video") ||
                    lower.contains("v=search")

            if (isBingVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isBingImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 3. DuckDuckGo Images & Videos
        if (lower.contains("duckduckgo.com")) {
            val isDdgImage = lower.contains("ia=images") ||
                    lower.contains("iax=images") ||
                    lower.contains("iar=images")
            val isDdgVideo = lower.contains("ia=videos") ||
                    lower.contains("iax=videos") ||
                    lower.contains("iar=videos")

            if (isDdgVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isDdgImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 4. Yahoo Images & Videos
        if (lower.contains("yahoo.com") || lower.contains("search.yahoo")) {
            val isYahooImage = lower.contains("images.search") ||
                    lower.contains("/search/images") ||
                    lower.contains("/images/")
            val isYahooVideo = lower.contains("video.search") ||
                    lower.contains("videos.search") ||
                    lower.contains("/search/video") ||
                    lower.contains("/video/")

            if (isYahooVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isYahooImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 5. Yandex Images & Videos
        if (lower.contains("yandex.") || lower.contains("ya.ru")) {
            val isYandexImage = lower.contains("images.yandex") ||
                    lower.contains("/images") ||
                    lower.contains("ya.ru/images")
            val isYandexVideo = lower.contains("video.yandex") ||
                    lower.contains("/video") ||
                    lower.contains("ya.ru/video")

            if (isYandexVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isYandexImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 6. Ecosia Images & Videos
        if (lower.contains("ecosia.org")) {
            val isEcosiaImage = lower.contains("ecosia.org/images") ||
                    lower.contains("type=image")
            val isEcosiaVideo = lower.contains("ecosia.org/videos") ||
                    lower.contains("type=video")

            if (isEcosiaVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isEcosiaImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 7. Brave Search Images & Videos
        if (lower.contains("search.brave.com") || (lower.contains("brave.com") && lower.contains("/search"))) {
            val isBraveImage = lower.contains("/images") ||
                    lower.contains("source=images")
            val isBraveVideo = lower.contains("/videos") ||
                    lower.contains("source=videos")

            if (isBraveVideo) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Video search is blocked"
                )
            }
            if (isBraveImage) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.IMAGE_VIDEO_SEARCH,
                    reason = "Image search is blocked"
                )
            }
            return EvaluationResult(isBlocked = false)
        }

        // 8. Unsupported search engines when Block Image & Video Search is enabled
        val unsupportedEval = evaluateUnsupportedSearchEngine(
            target = lower,
            isSearchProtectionEnabled = true,
            isTemporaryUnlocked = isTemporaryUnlocked
        )
        if (unsupportedEval.isBlocked) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Unsupported search engine blocked. Supported engines: Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, Brave."
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Set of supported search engine host stems that Reclaim inspects for image/video tabs:
     * Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, and Brave.
     */
    val SUPPORTED_SEARCH_ENGINE_STEMS = setOf(
        "google.", "bing.com", "duckduckgo.com", "yahoo.com", "search.yahoo",
        "yandex.", "ya.ru", "ecosia.org", "search.brave.com", "brave.com"
    )

    /**
     * Common unsupported / alternative search engines that users use to bypass search filters and detection.
     */
    val UNSUPPORTED_SEARCH_ENGINE_DOMAINS = setOf(
        "baidu.com", "startpage.com", "qwant.com", "ask.com", "search.aol.com", "aol.com",
        "dogpile.com", "wolframalpha.com", "lycos.com", "metacrawler.com", "webcrawler.com",
        "excite.com", "info.com", "searx.me", "searx.org", "searxng.org", "swisscows.com",
        "mojeek.com", "gibiru.com", "yippy.com", "gigablast.com", "hotbot.com", "alltheweb.com",
        "goodsearch.com", "you.com", "kagi.com", "presearch.com", "presearch.org",
        "andi.search", "andisearch.com", "perplexity.ai", "phind.com", "metager.org",
        "metager.de", "privado.com", "disconnect.me", "onesearch.com", "peekier.com",
        "ecosia.com", "teoma.com", "naver.com", "daum.net", "sogou.com", "so.com",
        "searchencrypt.com", "entireweb.com", "izito.com", "zapmeta.com", "feedproxy.com"
    )

    /**
     * Checks if a domain/host is one of the 7 supported search engines:
     * Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, Brave.
     */
    fun isKnownSearchEngine(hostOrUrl: String): Boolean {
        val lower = hostOrUrl.lowercase().trim()
        val normalized = normalizeDomain(lower)
        return SUPPORTED_SEARCH_ENGINE_STEMS.any { stem ->
            if (stem.endsWith(".")) {
                normalized.contains(stem) || lower.contains(stem)
            } else {
                normalized == stem || normalized.endsWith(".$stem") || lower.contains(stem)
            }
        }
    }

    /**
     * Evaluates whether a domain, URL, or page title belongs to an unsupported search engine.
     */
    fun evaluateUnsupportedSearchEngine(
        target: String,
        isSearchProtectionEnabled: Boolean = true,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (!isSearchProtectionEnabled || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }
        val lower = target.lowercase().trim()
        val normalized = normalizeDomain(lower)

        // If it's a known supported search engine, it's not unsupported
        if (isKnownSearchEngine(normalized)) {
            return EvaluationResult(isBlocked = false)
        }

        // 1. Direct domain match against known unsupported search engines
        val isDomainMatch = UNSUPPORTED_SEARCH_ENGINE_DOMAINS.any { domainMatches(normalized, it) }
        if (isDomainMatch) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Unsupported search engine blocked. Supported engines: Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, Brave."
            )
        }

        // 2. Generic heuristic detection for search engine URLs/titles trying to bypass detection:
        // e.g., "search.", "/search?", "searx.", ending with "- search"
        val isSearchEngineHeuristic = (
                normalized.startsWith("search.") ||
                normalized.contains(".search.") ||
                normalized.startsWith("searx.") ||
                normalized.contains("searxng.") ||
                normalized.contains("metasearch.")
        ) && (lower.contains("?q=") || lower.contains("&q=") || lower.contains("/search") || lower.contains("?query="))

        if (isSearchEngineHeuristic) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Unsupported search engine blocked. Supported engines: Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, Brave."
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates Google Lens, Circle to Search, and Picture Scan requests.
     */
    fun evaluateGoogleLens(
        target: String,
        isGoogleLensBlocked: Boolean,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (!isGoogleLensBlocked || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }
        val lower = target.lowercase().trim()
        val isLens = lower.contains("lens") ||
                lower.contains("visual search") ||
                lower.contains("visualsearch") ||
                lower.contains("circle to search") ||
                lower.contains("circle_to_search") ||
                lower.contains("picture scan") ||
                lower.contains("google scan") ||
                lower.contains("search with your camera") ||
                lower.contains("search with camera") ||
                lower.contains("search using camera") ||
                lower.contains("search what you see") ||
                lower.contains("search inside photo") ||
                lower.contains("search by image") ||
                lower.contains("results for image") ||
                lower.contains("visual matches") ||
                lower.contains("lens_camera_button") ||
                lower.contains("lens_overlay") ||
                lower.contains("পিকচার স্ক্যান") ||
                lower.contains("ছবি স্ক্যান") ||
                lower.contains("ক্যামেরা দিয়ে অনুসন্ধান") ||
                lower.contains("lens.google") ||
                lower.contains("google.com/lens") ||
                lower.contains("google.com/searchbyimage") ||
                lower.contains("com.google.ar.lens") ||
                lower.contains("com.google.android.apps.lens")

        if (isLens) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.IMAGE_VIDEO_SEARCH,
                reason = "Google Scan, Lens & Visual Search is blocked."
            )
        }
        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates whether an application package is an unsupported browser.
     */
    fun evaluateUnsupportedBrowser(
        packageName: String,
        isBlockUnsupportedBrowsersEnabled: Boolean,
        isDetectedBrowser: Boolean = false
    ): EvaluationResult {
        if (!isBlockUnsupportedBrowsersEnabled || packageName.isBlank()) {
            return EvaluationResult(isBlocked = false)
        }

        // Supported browsers are allowed
        if (DefaultBlocklistData.SUPPORTED_BROWSERS.any { it.equals(packageName, ignoreCase = true) }) {
            return EvaluationResult(isBlocked = false)
        }

        // Never treat Google Search App, launchers, Settings, or core non-browser apps as an unsupported browser
        val lowerPkg = packageName.lowercase()
        if (DefaultBlocklistData.GOOGLE_SEARCH_PACKAGES.any { it.equals(packageName, ignoreCase = true) } ||
            lowerPkg.startsWith("com.android.settings") ||
            lowerPkg == "com.android.systemui" ||
            lowerPkg == "com.android.vending"
        ) {
            return EvaluationResult(isBlocked = false)
        }

        val isKnownUnsupported = DefaultBlocklistData.UNSUPPORTED_BROWSERS.any {
            it.equals(packageName, ignoreCase = true)
        }

        val isHeuristicBrowserPkg = lowerPkg.contains(".browser") ||
                lowerPkg.endsWith("browser") ||
                lowerPkg.contains("browser.") ||
                lowerPkg.contains("ucmobile") ||
                lowerPkg.contains("opera") ||
                lowerPkg.contains("brave") ||
                lowerPkg.contains("duckduckgo") ||
                lowerPkg.contains("vivaldi") ||
                lowerPkg.contains("puffin") ||
                lowerPkg.contains("alohamobile") ||
                lowerPkg.contains("torproject") ||
                lowerPkg.contains("kiwibrowser") ||
                lowerPkg.contains("tunnybrowser") ||
                lowerPkg.contains("soulbrowser") ||
                lowerPkg.contains("mark.via") ||
                lowerPkg.contains("cromite") ||
                lowerPkg.contains("bromite") ||
                lowerPkg.contains("iceraven") ||
                lowerPkg.contains("waterfox") ||
                lowerPkg.contains("privacybrowser") ||
                lowerPkg.contains("hibrowser") ||
                lowerPkg.contains("amanbrowser") ||
                lowerPkg.contains("vidmate") ||
                lowerPkg.contains("snaptube") ||
                lowerPkg.contains("tubemate")

        // Check if package is in known unsupported browsers list or dynamically detected as a browser/downloader
        if (isKnownUnsupported || isHeuristicBrowserPkg || isDetectedBrowser) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.UNSUPPORTED_BROWSERS,
                reason = "Unsupported browser blocked. Only verified safe browsers (Chrome, Firefox, Samsung Internet) are allowed."
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates an application package against app rules, whitelist, and focus mode.
     */
    fun evaluatePackage(
        packageName: String,
        rules: List<BlockRule>,
        isFocusMode: Boolean = false,
        isTemporaryUnlocked: Boolean = false,
        isBlockUnsupportedBrowsers: Boolean = false
    ): EvaluationResult {
        if (isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false, reason = "Temporary unlock active")
        }

        // Essential system apps are ALWAYS allowed for safety
        val essentialSystemApps = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.systemui",
            "com.android.emergency",
            "com.google.android.emergency"
        )
        if (essentialSystemApps.contains(packageName)) {
            return EvaluationResult(isBlocked = false, reason = "Essential system service")
        }

        // Self package is always allowed
        if (packageName == "com.example" || packageName == "com.healinglab.reclaim.app") {
            return EvaluationResult(isBlocked = false)
        }

        // Check Whitelists / Exceptions first
        for (rule in rules) {
            if (rule.enabled && rule.isException && rule.type == RuleType.APP && rule.target == packageName) {
                return EvaluationResult(isBlocked = false, matchedRule = rule, reason = "Whitelisted application")
            }
        }

        // Unsupported browser check
        val browserCheck = evaluateUnsupportedBrowser(packageName, isBlockUnsupportedBrowsers)
        if (browserCheck.isBlocked) {
            return browserCheck
        }

        // Focus mode blocking
        if (isFocusMode) {
            val isDistraction = DefaultBlocklistData.SOCIAL_MEDIA_PACKAGES.contains(packageName) ||
                    DefaultBlocklistData.SHORT_VIDEO_PACKAGES.contains(packageName) ||
                    DefaultBlocklistData.BROWSER_PACKAGES.contains(packageName)
            if (isDistraction) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.EXTREME_DISTRACTION,
                    reason = "Blocked during active Focus Session"
                )
            }
        }

        // Custom App rules
        for (rule in rules) {
            if (rule.enabled && !rule.isException && rule.type == RuleType.APP && isScheduleActive(rule)) {
                if (rule.target == packageName) {
                    return EvaluationResult(
                        isBlocked = true,
                        matchedRule = rule,
                        category = rule.category,
                        reason = "Application blocked: ${rule.name}"
                    )
                }
            }
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Checks if a package name is YouTube or any modded variant (ReVanced, RVX, Vanced, NewPipe, LibreTube, Pure Tuber).
     */
    fun isYouTubePackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "com.google.android.youtube" ||
                lower.contains("revanced") ||
                lower.contains("vanced") ||
                lower.contains("newpipe") ||
                lower.contains("libretube") ||
                lower.contains("tubular") ||
                lower.contains("piped") ||
                lower.contains("pure_tuber") ||
                lower.contains("free.tube") ||
                (lower.contains("youtube") && !lower.contains("music"))
    }

    /**
     * Checks if a package name is Instagram or Instagram Lite.
     */
    fun isInstagramPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "com.instagram.android" || lower == "com.instagram.lite" || lower.contains("instagram")
    }

    /**
     * Checks if a package name is Facebook or Facebook Lite.
     */
    fun isFacebookPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "com.facebook.katana" || lower == "com.facebook.lite" || lower.contains("facebook")
    }

    /**
     * Evaluates YouTube Shorts, Instagram Reels, and Facebook Reels.
     */
    fun evaluateShorts(
        packageName: String,
        viewContentDescription: String,
        viewText: String,
        viewId: String,
        isYouTubeShortsBlocked: Boolean = false,
        isInstagramReelsBlocked: Boolean = false,
        isFacebookReelsBlocked: Boolean = false,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }

        val vId = viewId.lowercase()
        val desc = viewContentDescription.lowercase()
        val text = viewText.lowercase()
        val combined = "$desc $text $vId"

        // 1. YouTube Shorts detection (Original & Modded: ReVanced, RVX, Vanced, NewPipe, LibreTube, etc.)
        if (isYouTubePackage(packageName) && isYouTubeShortsBlocked) {
            val isShortsPlayer = vId.contains("reel_recycler") || 
                                 vId.contains("shorts_player") || 
                                 vId.contains("shorts_video_container") ||
                                 vId.contains("reel_player") ||
                                 vId.contains("reel_watch") ||
                                 vId.contains("shorts_container") ||
                                 vId.contains("shorts_surface") ||
                                 vId.contains("reel_container") ||
                                 vId.contains("reel_view_pager") ||
                                 vId.contains("shorts_playback") ||
                                 (desc.contains("shorts player") && !desc.contains("tab")) ||
                                 (vId.contains("shorts") && (vId.contains("player") || vId.contains("recycler") || vId.contains("pager") || vId.contains("surface") || vId.contains("pivot")))
            
            if (isShortsPlayer) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.SHORT_VIDEO,
                    reason = "YouTube Shorts detected"
                )
            }
        }

        // 2. Instagram Reels detection (Only block fullscreen Reels viewer / full player, NOT main feed video posts)
        if (isInstagramPackage(packageName) && isInstagramReelsBlocked) {
            val isFeedContext = vId.contains("news_feed") || vId.contains("feed_unit") ||
                                vId.contains("story_tray") || vId.contains("stories_tray") ||
                                vId.contains("main_feed")

            val isReelsPlayerViewId = vId.contains("clips_viewer") || 
                                      vId.contains("clips_video_container") ||
                                      vId.contains("clips_swipe_refresh") ||
                                      vId.contains("clips_media_interact") ||
                                      vId.contains("clips_viewer_fragment_container") ||
                                      vId.contains("reel_viewer_fragment_container") ||
                                      vId.contains("clips_attached_scrubber") ||
                                      vId.contains("clips_immersive_player") ||
                                      vId.contains("clips_action_bar") ||
                                      vId.contains("clips_author_container") ||
                                      vId.contains("reels_viewer_root_view") ||
                                      vId.contains("clips_playback") ||
                                      (vId.contains("reel_viewer") && !vId.contains("story_tray") && !vId.contains("feed"))

            val isReelsPlayerTextOrDesc = desc.contains("reel by") || text.contains("reel by") ||
                                          desc.contains("reels video") || desc.contains("watch reels") ||
                                          desc.contains("remix with this reel") || desc.contains("remix this reel") ||
                                          desc.contains("reels scrubber") || (desc.contains("scrubber") && desc.contains("reel")) ||
                                          desc.contains("double tap to play or pause") || desc.contains("double-tap to play or pause") ||
                                          (desc.contains("like reel") || desc.contains("comment on reel") || desc.contains("share reel") || desc.contains("send reel")) ||
                                          // Instagram Lite & mobile web full player signatures:
                                          (packageName == "com.instagram.lite" && (
                                              desc.contains("original audio") || desc.contains("audio by") || desc.contains("reels audio") ||
                                              (desc.contains("remix") && desc.contains("audio")) ||
                                              ((desc.contains("reel") || text.contains("reel")) && (desc.contains("audio") || desc.contains("remix") || desc.contains("video")))
                                          ))

            if (!isFeedContext && (isReelsPlayerViewId || isReelsPlayerTextOrDesc)) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.SHORT_VIDEO,
                    reason = "Instagram Reels detected"
                )
            }
        }

        // 3. Facebook Reels detection (Only block fullscreen Reels viewer / full player, NOT main feed video posts)
        if (isFacebookPackage(packageName) && isFacebookReelsBlocked) {
            val isFeedContext = vId.contains("news_feed") || vId.contains("feed_unit") ||
                                vId.contains("story_tray") || desc.contains("what's on your mind") ||
                                text.contains("what's on your mind")

            val isReelsPlayerViewId = vId.contains("reels_tab") ||
                                      vId.contains("fb_reels") ||
                                      vId.contains("fb_reels_viewer") || 
                                      vId.contains("fb_reels_fullscreen_player") ||
                                      vId.contains("fb_reels_immersive_viewer") ||
                                      vId.contains("reels_video_player") ||
                                      vId.contains("reels_consumption_container") ||
                                      vId.contains("reels_viewer_root_view") ||
                                      vId.contains("short_form_video_chaining_player") ||
                                      vId.contains("reels_tray_fullscreen") ||
                                      vId.contains("reels_vp2_container") ||
                                      vId.contains("reels_overlay_container") ||
                                      vId.contains("short_form_video_view") ||
                                      vId.contains("fb_reels_swipe_container") ||
                                      (vId.contains("clips_viewer") && !vId.contains("news_feed") && !vId.contains("feed_unit"))

            val isReelsPlayerTextOrDesc = desc.contains("facebook reels") || text.contains("facebook reels") ||
                                          desc.contains("reel by") || text.contains("reel by") ||
                                          desc.contains("watch more reels") || desc.contains("reels player") ||
                                          desc.contains("remix this reel") || desc.contains("remix reel") ||
                                          desc.contains("reels audio") || (desc.contains("use music") && desc.contains("reel")) ||
                                          // Facebook Lite full player signatures:
                                          (packageName == "com.facebook.lite" && (
                                              desc.contains("next reel") || desc.contains("previous reel") || desc.contains("watch reel") ||
                                              ((desc.contains("reels") || text.contains("reels")) && (desc.contains("remix") || desc.contains("audio") || desc.contains("original audio")))
                                          ))

            if (!isFeedContext && (isReelsPlayerViewId || isReelsPlayerTextOrDesc)) {
                return EvaluationResult(
                    isBlocked = true,
                    category = BlockCategory.SHORT_VIDEO,
                    reason = "Facebook Reels detected"
                )
            }
        }

        // 4. TikTok / Musically
        if ((packageName == "com.zhiliaoapp.musically" || packageName == "com.zhiliaoapp.musically.go" || packageName == "com.ss.android.ugc.trill") && 
            (isYouTubeShortsBlocked || isInstagramReelsBlocked || isFacebookReelsBlocked)) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.SHORT_VIDEO,
                reason = "TikTok short-form video detected"
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates whether WhatsApp Channels / Updates feed is currently opened.
     */
    fun evaluateWhatsAppChannels(
        packageName: String,
        viewContentDescription: String,
        viewText: String,
        viewId: String,
        isWhatsAppChannelsBlocked: Boolean,
        isTemporaryUnlocked: Boolean = false
    ): EvaluationResult {
        if (!isWhatsAppChannelsBlocked || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }

        if (!DefaultBlocklistData.WHATSAPP_PACKAGES.contains(packageName)) {
            return EvaluationResult(isBlocked = false)
        }

        val combined = "$viewContentDescription $viewText $viewId".lowercase()

        // Detect Updates / Channels tab, Channel list, or Channel viewer
        val isChannelsOrUpdates = viewId.contains("updates_tab") || 
                                  viewId.contains("newsletter_view") || 
                                  viewId.contains("channel_view") ||
                                  (viewContentDescription.contains("updates") && viewContentDescription.contains("tab"))
        
        if (isChannelsOrUpdates) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.WHATSAPP_CHANNELS,
                reason = "WhatsApp Channels blocked"
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates whether user is attempting to uninstall or force stop the protection app in Settings.
     */
    /**
     * Evaluates whether an action in system Settings, Accessibility settings, Permission Controller,
     * or Package Installer represents an attempt to uninstall, force stop, revoke permissions,
     * clear data, deactivate device admin, or turn off accessibility service.
     */
    fun evaluateUninstallAttempt(
        packageName: String,
        viewContentDescription: String,
        viewText: String,
        viewId: String,
        isUninstallProtectionEnabled: Boolean,
        isDeviceAdminActive: Boolean = false,
        isAccountabilityLocked: Boolean = false,
        isAccountabilityEnabled: Boolean = false
    ): EvaluationResult {
        val canEnforceUninstall = isUninstallProtectionEnabled || isDeviceAdminActive
        val canEnforceTamper = isUninstallProtectionEnabled || isAccountabilityLocked || isAccountabilityEnabled || isDeviceAdminActive

        if (!canEnforceUninstall && !canEnforceTamper) {
            return EvaluationResult(isBlocked = false)
        }

        val settingsPackages = setOf(
            "android",
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
            "com.samsung.android.permissioncontroller",
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
            "com.transsion.phonemaster",
            "com.google.android.marvin.talkback",
            "com.android.vending",
            "com.sec.android.app.samsungapps"
        )
        val isSettingsOrAccPkg = settingsPackages.contains(packageName) ||
                packageName == "android" ||
                packageName.contains("accessibility", ignoreCase = true) ||
                packageName.contains("settings", ignoreCase = true) ||
                packageName.contains("packageinstaller", ignoreCase = true) ||
                packageName.contains("permissioncontroller", ignoreCase = true) ||
                packageName.contains("securitycenter", ignoreCase = true) ||
                packageName.contains("deviceadmin", ignoreCase = true) ||
                packageName.contains("devicepolicy", ignoreCase = true) ||
                packageName.contains("safecenter", ignoreCase = true) ||
                packageName.contains("knox", ignoreCase = true) ||
                packageName.contains("security", ignoreCase = true) ||
                packageName.contains("admin", ignoreCase = true) ||
                packageName.contains("policy", ignoreCase = true) ||
                packageName.contains("file", ignoreCase = true) ||
                packageName.contains("myfiles", ignoreCase = true) ||
                packageName.contains("documentsui", ignoreCase = true) ||
                packageName.contains("explorer", ignoreCase = true)
        if (!isSettingsOrAccPkg) {
            return EvaluationResult(isBlocked = false)
        }

        val combined = "$viewContentDescription $viewText $viewId".lowercase()

        // Detect mentions of Reclaim / app identity or our Accessibility Service description
        val isTargetingReclaim = combined.contains("reclaim") ||
                combined.contains("healinglab") ||
                combined.contains("copsli") ||
                combined.contains("selfcontrol") ||
                combined.contains("digital self-control") ||
                combined.contains("com.healinglab.reclaim.app") ||
                combined.contains("com.example") ||
                combined.contains("detects short-form video players") ||
                combined.contains("mindful pause interventions") ||
                combined.contains("helps you stay focused by blocking") ||
                combined.contains("blocking distracting content") ||
                combined.contains("reclaim_accessibility_detail_page")

        // Samsung-specific admin-related keywords (One UI may use these instead of stock "device admin")
        val hasAdminKeyword = combined.contains("device admin") ||
                combined.contains("device administrator") ||
                combined.contains("admin app") ||
                combined.contains("device management") ||
                combined.contains("device manager") ||
                combined.contains("admin rights") ||
                combined.contains("ডিভাইস অ্যাডমিন") ||
                combined.contains("ডিভাইস ম্যানেজমেন্ট") ||
                viewId.contains("device_admin") ||
                viewId.contains("dm_") ||
                viewId.contains("admin")

        // General list screens that must NOT be blocked (so user can manage other apps freely)
        val isGeneralAccessibilityListPage = (combined.contains("installed apps") ||
                combined.contains("installed services") ||
                combined.contains("downloaded apps") ||
                combined.contains("downloaded services") ||
                combined.contains("more downloaded services") ||
                combined.contains("user downloaded services") ||
                combined.contains("talkback") ||
                combined.contains("select to speak") ||
                combined.contains("visibility enhancements") ||
                combined.contains("hearing enhancements") ||
                combined.contains("interaction and dexterity") ||
                combined.contains("caption preferences") ||
                combined.contains("live caption") ||
                combined.contains("magnification") ||
                combined.contains("switch access") ||
                combined.contains("voice access") ||
                combined.contains("color correction") ||
                combined.contains("color inversion") ||
                combined.contains("text-to-speech") ||
                combined.contains("display size and text") ||
                combined.contains("accessibility features") ||
                combined.contains("shortcuts & accessibility") ||
                combined.contains("accessibility & convenience")) &&
                !combined.contains("reclaim_accessibility_detail_page") &&
                !combined.contains("use reclaim") &&
                !combined.contains("reclaim shortcut") &&
                !combined.contains("stop reclaim") &&
                !combined.contains("turn off reclaim")

        val isAppListScreen = (combined.contains("all apps") ||
                combined.contains("see all") ||
                combined.contains("installed apps") ||
                combined.contains("search apps") ||
                combined.contains("default apps") ||
                combined.contains("manage apps")) &&
                !combined.contains("force stop") &&
                !viewId.contains("force_stop") &&
                !combined.contains("do you want to uninstall") &&
                !combined.contains("device admin") &&
                !combined.contains("device administrator") &&
                !combined.contains("deactivate") &&
                !combined.contains("software information") &&
                !combined.contains("software info") &&
                !combined.contains("build number") &&
                !combined.contains("বিল্ড নম্বর") &&
                !combined.contains("reclaim")

        val isGeneralPermissionOrSpecialAccessList = (combined.contains("usage access") ||
                combined.contains("usage data") ||
                combined.contains("appear on top") ||
                combined.contains("display over other apps") ||
                combined.contains("draw over other apps") ||
                combined.contains("special app access") ||
                combined.contains("special access")) &&
                !combined.contains("device admin") &&
                !combined.contains("device administrator") &&
                !combined.contains("device manager") &&
                !combined.contains("deactivate") && !combined.contains("force stop")

        // 1. Double layer: Block "Device admin apps" list page completely
        val isDeviceAdminAppsScreen = canEnforceTamper && (
                combined.contains("device admin apps") ||
                combined.contains("device admin app") ||
                combined.contains("device administrators") ||
                combined.contains("device administrator") ||
                combined.contains("device management") ||
                combined.contains("ডিভাইস অ্যাডমিন") ||
                combined.contains("enforce password requirements") ||
                combined.contains("other security policies") ||
                combined.contains("employers often use device admin") ||
                (combined.contains("device admin") && combined.contains("reclaim")) ||
                viewId.contains("device_admin") ||
                viewId.contains("dm_")
        )

        if (isDeviceAdminAppsScreen) {
            android.util.Log.d("RuleEngine", "BLOCKED: Device Admin Apps screen (double layer)")
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Accessing Device Admin apps is blocked while Uninstall Protection or Accountability is active."
            )
        }

        // 1A. EXACT match for Reclaim's Device Admin Deactivation page (from user screenshot)
        val isReclaimDeactivationPageExact = canEnforceTamper && (
                combined.contains("reclaim_device_admin_detail_page") ||
                combined.contains("this admin app is active") ||
                combined.contains("allows reclaim to perform") ||
                combined.contains("admin app is active") ||
                (combined.contains("reclaim") && combined.contains("deactivate")) ||
                (combined.contains("this admin app is active") && combined.contains("deactivate")) ||
                (combined.contains("this admin app is active") && combined.contains("cancel")) ||
                (isTargetingReclaim && combined.contains("deactivate"))
        )

        if (isReclaimDeactivationPageExact) {
            android.util.Log.d("RuleEngine", "BLOCKED: Reclaim Device Admin deactivation page (exact match from screenshot)")
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Deactivating Device Administrator is blocked while Uninstall Protection or Accountability is active."
            )
        }

        // 1B. Block Software Information screen completely (user request: "block going to software information page")
        val isSoftwareInfoScreen = canEnforceTamper && (
                combined.contains("software information") ||
                combined.contains("software info") ||
                combined.contains("সফটওয়্যার তথ্য") ||
                combined.contains("সফটওয়্যার ইনফো") ||
                viewId.contains("software_info") ||
                viewId.contains("software_information") ||
                combined.contains("build number") ||
                combined.contains("বিল্ড নম্বর")
        )

        if (isSoftwareInfoScreen) {
            android.util.Log.d("RuleEngine", "BLOCKED: Software information screen completely")
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Accessing Software Information is blocked while Prevent Uninstall or Accountability Lock is active."
            )
        }

        // 1C. Explicit deactivation keywords / actions targeting Device Admin
        val isExplicitDeactivate = canEnforceTamper && (
                combined.contains("deactivate this device admin") ||
                combined.contains("deactivate device admin") ||
                combined.contains("deactivate device admin app") ||
                combined.contains("deactivate this device admin app") ||
                combined.contains("deactivate this admin app") ||
                combined.contains("remove device admin") ||
                combined.contains("deactivate this app") ||
                combined.contains("turn off device admin") ||
                combined.contains("disable device admin") ||
                (combined.contains("deactivate") && (isTargetingReclaim || hasAdminKeyword)) ||
                (combined.contains("নিষ্ক্রিয়") && (isTargetingReclaim || hasAdminKeyword)) ||
                (combined.contains("turn off") && (hasAdminKeyword || isTargetingReclaim)) ||
                (combined.contains("disable") && (hasAdminKeyword || isTargetingReclaim)) ||
                viewId.contains("deactivate") ||
                (isTargetingReclaim && hasAdminKeyword && (combined.contains("deactivate") || combined.contains("remove") || combined.contains("turn off") || combined.contains("disable") || combined.contains("uninstall")))
        )

        // Dialog popup confirming deactivation of device administrator
        val isDeviceAdminDeactivationDialog = canEnforceTamper &&
                (combined.contains("deactivate") || combined.contains("remove") || combined.contains("turn off") || combined.contains("disable") || combined.contains("নিষ্ক্রিয়")) &&
                (hasAdminKeyword || isTargetingReclaim) &&
                (combined.contains("cancel") || viewId.contains("button1") || viewId.contains("button2"))

        // Activating Device Admin prompt during setup (ALWAYS allowed!)
        val isActivatingDeviceAdmin = !isExplicitDeactivate && !isDeviceAdminDeactivationDialog && (
                combined.contains("activate this device admin") ||
                combined.contains("activate device admin") ||
                combined.contains("activate this admin app") ||
                combined.contains("activate this device") ||
                combined.contains("activate admin app") ||
                (combined.contains("activate") && (combined.contains("device admin") || combined.contains("admin app")))
        )

        val isDeviceAdminDeactivation = canEnforceTamper && (
                isExplicitDeactivate ||
                isDeviceAdminDeactivationDialog ||
                isReclaimDeactivationPageExact ||
                isDeviceAdminAppsScreen
        )

        if (isDeviceAdminDeactivation) {
            android.util.Log.d("RuleEngine", "BLOCKED: Device Admin deactivation")
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Deactivating Device Administrator is blocked while Uninstall Protection or Accountability is active."
            )
        }

        if (isActivatingDeviceAdmin) {
            return EvaluationResult(isBlocked = false)
        }


        // 3. Direct uninstall attempts targeting Reclaim (enforced when Prevent Uninstall or Accountability is active)
        val isUninstallAction = canEnforceUninstall && (
            (packageName.contains("packageinstaller") && combined.contains("uninstall") && (isTargetingReclaim || combined.contains("do you want to uninstall"))) ||
            (isTargetingReclaim && (
                combined.contains("do you want to uninstall") ||
                combined.contains("uninstall this app") ||
                combined.contains("uninstall reclaim") ||
                viewId.contains("button_uninstall") ||
                viewId.contains("uninstall_button") ||
                (combined.contains("uninstall") && (viewId.contains("button1") || viewId.contains("action_button") || combined.contains("delete app") || combined.contains("uninstalling")))
            )) ||
            ((packageName == "com.android.vending" || packageName == "com.sec.android.app.samsungapps") &&
                isTargetingReclaim && (combined.contains("uninstall") || combined.contains("do you want to uninstall")))
        )

        // Block updating / downgrading / reinstalling Reclaim (via package installer, file manager, etc.)
        val isReclaimUpdateAction = canEnforceUninstall && isTargetingReclaim && (
            combined.contains("install") ||
            combined.contains("update") ||
            combined.contains("replace") ||
            combined.contains("downgrade") ||
            combined.contains("do you want to install") ||
            combined.contains("want to update") ||
            combined.contains("existing app") ||
            combined.contains("existing application") ||
            combined.contains("app will be replaced") ||
            combined.contains("version of this app") ||
            combined.contains("ইনস্টল") ||
            combined.contains("আপডেট")
        ) && !combined.contains("uninstall")

        // 4. Force Stop attempts targeting Reclaim (enforced when Accountability or Prevent Uninstall is active)
        val isForceStopAction = canEnforceTamper && (
            combined.contains("if you force stop an app") ||
            combined.contains("may misbehave") ||
            (isTargetingReclaim && (
                combined.contains("force stop") ||
                combined.contains("force_stop") ||
                viewId.contains("force_stop") ||
                viewId.contains("right_button") ||
                (combined.contains("force") && combined.contains("stop"))
            ))
        )

        // 5. Initial setup / grant prompts (must NEVER be blocked)
        val isInitialAccessibilityGrantPrompt = (combined.contains("allow reclaim to have full control") ||
                combined.contains("allow reclaim to") ||
                (isTargetingReclaim && combined.contains("observe your actions") && combined.contains("retrieve window content"))) &&
                (combined.contains("allow") || combined.contains("deny")) &&
                !combined.contains("turn off") && !combined.contains("stop") && !combined.contains("disable")

        val isRuntimePermissionGrantPrompt = packageName.contains("permissioncontroller") && (
                combined.contains("while using the app") ||
                combined.contains("only this time") ||
                combined.contains("allow ") ||
                combined.contains("allow reclaim") ||
                (combined.contains("allow") && combined.contains("don't allow"))
        ) && !combined.contains("app permissions") && !combined.contains("remove permissions")

        // Revoking or undoing permissions targeting Reclaim (enforced when Accountability or Prevent Uninstall is active)
        val isPermissionTampering = canEnforceTamper && isTargetingReclaim && !isRuntimePermissionGrantPrompt && (
            (packageName.contains("permissioncontroller") && (
                (combined.contains("remove permissions") && combined.contains("unused")) ||
                (combined.contains("app permissions") && (combined.contains("deny") || combined.contains("don't allow") || combined.contains("not allowed")))
            )) ||
            (!isAppListScreen && !isGeneralAccessibilityListPage && !isGeneralPermissionOrSpecialAccessList && (
                combined.contains("app permissions") && (combined.contains("remove") || combined.contains("deny") || combined.contains("turn off"))
            ))
        )

        // 6. Accessing Reclaim's App Info page in Settings
        val isAppInfoPage = canEnforceTamper && isTargetingReclaim &&
                !isGeneralAccessibilityListPage && !isAppListScreen && !isGeneralPermissionOrSpecialAccessList && (
                    combined.contains("app info") ||
                    combined.contains("app details") ||
                    combined.contains("installed app details") ||
                    (combined.contains("open") && (combined.contains("uninstall") || combined.contains("disable") || combined.contains("force stop"))) ||
                    (combined.contains("storage") && combined.contains("cache")) ||
                    (combined.contains("mobile data") && combined.contains("battery"))
                )

        // 7. Clearing app storage or data for Reclaim
        val isClearDataAttempt = canEnforceTamper && isTargetingReclaim && (
            combined.contains("clear storage") ||
            combined.contains("clear data") ||
            combined.contains("clear all data") ||
            viewId.contains("clear_data") ||
            viewId.contains("clear_user_data")
        )

        // 8. Accessibility tampering indicators for Reclaim's Accessibility service
        val isReclaimAccessibilityDeactivationDialog = (combined.contains("turn off reclaim") ||
                combined.contains("stop reclaim") ||
                combined.contains("disable reclaim") ||
                combined.contains("will stop reclaim") ||
                combined.contains("stop service") ||
                combined.contains("turn off service") ||
                combined.contains("tapping stop will turn off reclaim") ||
                (isTargetingReclaim && (combined.contains("tapping stop") || combined.contains("tap stop") || combined.contains("stop") || combined.contains("turn off") || combined.contains("disable")) && (combined.contains("cancel") || viewId.contains("button1") || viewId.contains("button2"))))

        val isReclaimAccessibilityDetailPage = isTargetingReclaim && (
                combined.contains("reclaim_accessibility_detail_page") ||
                combined.contains("detects short-form video players") ||
                combined.contains("mindful pause interventions") ||
                combined.contains("use reclaim") ||
                combined.contains("reclaim shortcut") ||
                combined.contains("reclaim options") ||
                (viewId.contains("switch_bar") || viewId.contains("sesl_switchbar") || viewId.contains("main_switch") || viewId.contains("switch_widget")) ||
                (combined.contains("reclaim") && combined.contains("switch"))
        ) && !isGeneralAccessibilityListPage && !isInitialAccessibilityGrantPrompt

        val isAccessibilityTampering = canEnforceTamper && !isActivatingDeviceAdmin && !isInitialAccessibilityGrantPrompt && (
                isReclaimAccessibilityDeactivationDialog ||
                isReclaimAccessibilityDetailPage ||
                combined.contains("turn off digital self-control") ||
                combined.contains("stop digital self-control")
        )

        // 9. Developer options / USB debugging tampering & Build number tapping
        val isInsideDeveloperOptionsScreen = (combined.contains("developer options") ||
                combined.contains("developer settings") ||
                combined.contains("development settings") ||
                combined.contains("options for developers") ||
                combined.contains("ডেভেলপার অপশন") ||
                viewId.contains("development_settings") ||
                viewId.contains("developer_options") ||
                viewId.contains("development_switch"))

        val isInsideSoftwareInfoScreen = (combined.contains("software information") ||
                combined.contains("software info") ||
                combined.contains("one ui version") ||
                combined.contains("one ui core version") ||
                combined.contains("build number") ||
                combined.contains("build_number") ||
                combined.contains("baseband version") ||
                combined.contains("kernel version") ||
                combined.contains("knox version") ||
                combined.contains("se for android") ||
                combined.contains("service provider sw") ||
                combined.contains("বিল্ড নম্বর") ||
                combined.contains("সফটওয়্যার তথ্য") ||
                combined.contains("সফটওয়্যার ইনফো") ||
                viewId.contains("software_info") ||
                viewId.contains("build_number") ||
                viewId.contains("sw_update"))

        // Samsung path: Settings → About phone (this is where "Software information" link lives)
        val isAboutPhoneScreen = (combined.contains("about phone") ||
                combined.contains("about device") ||
                combined.contains("about tablet") ||
                combined.contains("ফোন সম্পর্কে") ||
                combined.contains("ডিভাইস সম্পর্কে") ||
                viewId.contains("about_phone") ||
                viewId.contains("about_device")) &&
                (combined.contains("software information") ||
                 combined.contains("build number") ||
                 combined.contains("software info") ||
                 combined.contains("status") ||
                 viewId.contains("software_info"))

        val isDeveloperOptionsAttempt = canEnforceTamper && (
            isInsideDeveloperOptionsScreen ||
            isInsideSoftwareInfoScreen ||
            isAboutPhoneScreen ||
            combined.contains("usb debugging") ||
            combined.contains("wireless debugging") ||
            combined.contains("install via usb") ||
            combined.contains("oem unlocking") ||
            combined.contains("you are now a developer") ||
            combined.contains("steps away from being a developer") ||
            combined.contains("developer mode has been") ||
            combined.contains("turn on developer mode") ||
            combined.contains("enable developer mode") ||
            combined.contains("developer mode is already") ||
            combined.contains("no need, developer mode") ||
            combined.contains("ইউএসবি ডিবাগিং")
        )

        // 11. Changing Date and Time settings to bypass schedules/timers
        val isDateTimeTampering = canEnforceTamper && !isGeneralAccessibilityListPage && (
            (combined.contains("date & time") || combined.contains("date and time") || combined.contains("date_time_settings")) &&
            (combined.contains("set time") || combined.contains("set date") || combined.contains("change date") || combined.contains("automatic date") || combined.contains("use network-provided") || viewId.contains("date_time"))
        )

        // 12. Multiple users / Guest mode / Second Space tampering
        val isMultiUserTampering = canEnforceTamper && !isGeneralAccessibilityListPage && (
            combined.contains("multiple users") ||
            combined.contains("guest user") ||
            combined.contains("add guest") ||
            combined.contains("add user") ||
            combined.contains("second space") ||
            combined.contains("dual apps") ||
            viewId.contains("user_settings") ||
            viewId.contains("user_preference")
        )

        // 13. Factory data reset tampering
        val isFactoryResetTampering = canEnforceTamper && !isGeneralAccessibilityListPage && (
            combined.contains("factory data reset") ||
            combined.contains("erase all data") ||
            combined.contains("factory reset") ||
            viewId.contains("master_clear")
        )

        if (isUninstallAction) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Uninstall Protection is active. Reclaim is protected against uninstallation until your timer finishes or partner unlocks."
            )
        }

        if (isReclaimUpdateAction) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Updating, reinstalling, or downgrading Reclaim is blocked while Prevent Uninstall is active. Disable Prevent Uninstall first."
            )
        }

        if (isForceStopAction) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Force stopping Reclaim is blocked while Accountability or Uninstall Protection is active."
            )
        }

        if (isPermissionTampering) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Modifying or undoing Reclaim's permissions is blocked while Accountability or Uninstall Protection is active."
            )
        }

        if (isClearDataAttempt) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Clearing Reclaim's app storage is blocked while Accountability or Uninstall Protection is active."
            )
        }

        if (isAppInfoPage) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Access to Reclaim's App Info is blocked while Accountability or Uninstall Protection is active."
            )
        }

        if (isDeviceAdminDeactivation) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Deactivating Device Administrator is blocked while Uninstall Protection or Accountability is active."
            )
        }


        if (isAccessibilityTampering) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Reclaim's Accessibility Service cannot be turned off while Accountability Lock or Prevent Uninstall is active."
            )
        }

        if (isDeveloperOptionsAttempt) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Accessing or enabling Developer Options and USB Debugging is blocked while Prevent Uninstall or Accountability Lock is active."
            )
        }

        if (isDateTimeTampering) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Modifying device Date & Time settings is blocked while Accountability Lock or Prevent Uninstall is active."
            )
        }

        if (isMultiUserTampering) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Switching users or creating guest profiles is blocked while Accountability Lock is active."
            )
        }

        if (isFactoryResetTampering) {
            return EvaluationResult(
                isBlocked = true,
                category = BlockCategory.CUSTOM,
                reason = "Device reset is protected while Prevent Uninstall or Accountability Lock is active."
            )
        }

        return EvaluationResult(isBlocked = false)
    }

    /**
     * Evaluates a search query for adult keywords and custom keywords.
     */
    fun evaluateSearchQuery(
        query: String,
        isSearchProtectionEnabled: Boolean,
        customKeywords: List<String> = emptyList(),
        isTemporaryUnlocked: Boolean = false,
        isAdultBlocked: Boolean = true
    ): EvaluationResult {
        if ((!isSearchProtectionEnabled && customKeywords.isEmpty()) || isTemporaryUnlocked) {
            return EvaluationResult(isBlocked = false)
        }

        val rawLower = query.lowercase().trim()
        if (rawLower.isBlank()) return EvaluationResult(isBlocked = false)

        // If the input is a URL with ?q= / &q= / ?p= / ?text=, also extract and decode the query string
        val decodedCandidate = if (rawLower.contains("?") || rawLower.contains("&") || rawLower.contains("%") || rawLower.contains("+")) {
            val qMatch = Regex("[?&](?:q|p|query|text|search_query|wd|keyword)=([^&#]+)").find(rawLower)
            val extracted = qMatch?.groupValues?.getOrNull(1) ?: rawLower
            try {
                java.net.URLDecoder.decode(extracted, "UTF-8").lowercase()
            } catch (_: Exception) {
                extracted.replace("+", " ").replace("%20", " ")
            }
        } else {
            rawLower
        }

        val combinedText = "$rawLower $decodedCandidate"
        val tokens = combinedText.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }.toSet()

        // 1. Custom user-defined blocked keywords (always enforced when present)
        for (kw in customKeywords) {
            val kwLower = kw.lowercase().trim()
            if (kwLower.isNotBlank()) {
                val kwTokens = kwLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
                val matched = if (kwTokens.size == 1) {
                    tokens.contains(kwTokens[0]) || combinedText.contains(kwLower)
                } else {
                    combinedText.contains(kwLower) || (kwTokens.isNotEmpty() && kwTokens.all { tokens.contains(it) })
                }
                if (matched) {
                    return EvaluationResult(
                        isBlocked = true,
                        category = BlockCategory.KEYWORDS,
                        reason = "Blocked keyword: '$kw'"
                    )
                }
            }
        }

        // 2. Adult keywords (Layer 3 Browser & Search App Keyword Scanning)
        if (isSearchProtectionEnabled && isAdultBlocked) {
            for (kw in DefaultBlocklistData.ADULT_KEYWORDS) {
                val kwLower = kw.lowercase().trim()
                if (tokens.contains(kwLower) || (kwLower.contains(" ") && combinedText.contains(kwLower))) {
                    return EvaluationResult(
                        isBlocked = true,
                        category = BlockCategory.ADULT,
                        reason = "Adult search query detected"
                    )
                }
            }
        }

        return EvaluationResult(isBlocked = false)
    }
}
