package com.example.data.model

enum class BlockCategory(val displayName: String, val description: String) {
    ADULT("Adult Content", "Explicit adult sites, redirect domains, and known adult networks"),
    IMAGE_VIDEO_SEARCH("Image & Video Search", "Image and video search queries and result pages"),
    SHORT_VIDEO("Short-Form Video", "YouTube Shorts, Instagram Reels, TikTok, Facebook Reels"),
    WHATSAPP_CHANNELS("WhatsApp Channels", "WhatsApp Channels and status feeds"),
    UNSUPPORTED_BROWSERS("Unsupported Browsers", "Browsers that do not support URL inspection"),
    NEW_APPS("Newly Installed Apps", "Recently installed apps pending user approval"),
    KEYWORDS("Blocked Keywords", "Custom or sensitive search terms"),
    GAMBLING("Gambling", "Online casinos, betting portals, and gambling apps"),
    SOCIAL_MEDIA("Social Media", "Endless feed social networks and feeds"),
    EXTREME_DISTRACTION("Distracting Sites", "News feeds, clickbait, and compulsive browsing"),
    BROWSERS("Alternative Browsers", "Secondary browsers used to circumvent restrictions"),
    CUSTOM("Custom Rules", "Personal domains and apps added by you")
}

enum class RuleType {
    DOMAIN,
    APP,
    CATEGORY,
    KEYWORD,
    SHORTS,
    WHATSAPP_CHANNELS,
    IMAGE_SEARCH,
    FOCUS_SESSION
}

enum class RuleAction {
    BLOCK,
    WARN,
    COOLDOWN
}
