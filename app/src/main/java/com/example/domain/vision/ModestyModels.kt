package com.example.domain.vision

import android.graphics.RectF

enum class ModestySensitivity(val displayName: String, val description: String) {
    STRICT_AWRAH(
        displayName = "Strict Awrah (Recommended)",
        description = "Detects exposed cleavage, midriff, thighs, bikini, dance reels, and uncovering"
    ),
    MODERATE(
        displayName = "Moderate",
        description = "Detects swimwear, lingerie, and explicit bodily exposure"
    );

    companion object {
        fun fromString(value: String): ModestySensitivity =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: STRICT_AWRAH
    }
}

enum class ModestyShieldStyle(val displayName: String, val description: String) {
    TARGETED_BLUR(
        displayName = "Targeted Blur over Offending Area",
        description = "Blurs out only the detected exposed body area with adjustable blur intensity"
    ),
    BLACK_OVERLAY(
        displayName = "Targeted Blackout Shield",
        description = "Places a solid discreet dark shield directly over the detected body region"
    ),
    FULL_SCREEN(
        displayName = "Full-Screen 'Lower Your Gaze' Shield",
        description = "Covers the entire screen with a frosted shield, spiritual reminder, and exit button"
    );

    companion object {
        fun fromString(value: String): ModestyShieldStyle =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: TARGETED_BLUR
    }
}

enum class ModestyViolationType(val label: String, val isStrictOnly: Boolean) {
    EXPOSED_CLEAVAGE("Exposed Cleavage / Chest", isStrictOnly = true),
    EXPOSED_MIDRIFF("Exposed Midriff / Belly", isStrictOnly = true),
    EXPOSED_THIGHS("Exposed Thighs / Short Attire", isStrictOnly = true),
    SWIMWEAR_LINGERIE("Swimwear / Lingerie / Bikini", isStrictOnly = false),
    EXPLICIT_NUDITY("Explicit Bodily Exposure", isStrictOnly = false)
}

data class DetectedModestyRegion(
    val normalizedBounds: RectF, // 0.0 to 1.0 coordinates relative to screen/media
    val violationType: ModestyViolationType,
    val confidence: Float
)

data class ModestyScanResult(
    val isViolating: Boolean,
    val regions: List<DetectedModestyRegion>,
    val summary: String = ""
)
