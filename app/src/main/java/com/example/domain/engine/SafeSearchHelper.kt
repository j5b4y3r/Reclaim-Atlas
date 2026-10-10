package com.example.domain.engine

object SafeSearchHelper {

    // IP addresses for safe search enforcement
    const val GOOGLE_SAFE_SEARCH_IP = "216.239.38.120" // forcesafesearch.google.com
    const val BING_STRICT_SEARCH_IP = "204.79.197.220" // strict.bing.com
    const val YOUTUBE_RESTRICT_IP = "216.239.38.119" // restrict.youtube.com

    /**
     * Checks if the given hostname should be routed to a SafeSearch VIP IP.
     */
    fun getSafeSearchIpForHost(hostname: String): String? {
        val lower = hostname.lowercase().trim()

        // Google search domains
        if (lower == "google.com" || lower == "www.google.com" ||
            lower.endsWith(".google.com") || lower.startsWith("google.")
        ) {
            return GOOGLE_SAFE_SEARCH_IP
        }

        // Bing search domains
        if (lower == "bing.com" || lower == "www.bing.com") {
            return BING_STRICT_SEARCH_IP
        }

        // YouTube restricted mode
        if (lower == "youtube.com" || lower == "www.youtube.com" ||
            lower == "m.youtube.com" || lower == "youtubei.googleapis.com"
        ) {
            return YOUTUBE_RESTRICT_IP
        }

        return null
    }

    /**
     * Appends or rewrites query parameters in a search URL to ensure SafeSearch is turned on.
     */
    fun enforceSafeSearchInUrl(url: String): String {
        var modified = url
        if (url.contains("google.") && url.contains("/search")) {
            if (!url.contains("safe=active")) {
                modified = if (url.contains("?")) "$url&safe=active" else "$url?safe=active"
            }
        } else if (url.contains("bing.com/search")) {
            if (!url.contains("adlt=strict")) {
                modified = if (url.contains("?")) "$url&adlt=strict" else "$url?adlt=strict"
            }
        } else if (url.contains("duckduckgo.com")) {
            if (!url.contains("kp=1")) {
                modified = if (url.contains("?")) "$url&kp=1" else "$url?kp=1"
            }
        }
        return modified
    }
}
