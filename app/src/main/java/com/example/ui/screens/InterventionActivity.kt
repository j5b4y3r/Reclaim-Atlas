package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.SelfControlApp
import com.example.ui.components.LoopingVideoPlayer
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class InterventionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_PACKAGE = "extra_package"

        @Volatile
        var isShowing: Boolean = false
            private set

        @Volatile
        private var lastStartTime: Long = 0L

        @Volatile
        private var dismissCooldownUntil: Long = 0L

        @Volatile
        private var lastDismissedPackage: String? = null

        fun markDismissed(packageName: String? = null) {
            isShowing = false
            lastDismissedPackage = packageName
            dismissCooldownUntil = android.os.SystemClock.uptimeMillis() + 250L
        }

        fun isInDismissCooldown(packageName: String? = null): Boolean {
            val now = android.os.SystemClock.uptimeMillis()
            if (now >= dismissCooldownUntil) return false
            return packageName == null || lastDismissedPackage == null || packageName == lastDismissedPackage
        }

        fun start(
            context: Context,
            target: String,
            reason: String,
            packageName: String? = null,
            bypassCooldown: Boolean = false
        ) {
            val now = android.os.SystemClock.uptimeMillis()
            if (!bypassCooldown) {
                if (isShowing) {
                    return
                }
                if (isInDismissCooldown(packageName)) {
                    return
                }
                if (now - lastStartTime < 800L) {
                    return
                }
            } else {
                // For high-priority security/tamper blocks (bypassCooldown = true),
                // only debounce ultra-rapid duplicate calls within 60ms while already focused.
                if (isShowing && now - lastStartTime < 60L) {
                    return
                }
            }
            lastStartTime = now

            val intent = Intent(context, InterventionActivity::class.java).apply {
                putExtra(EXTRA_TARGET, target)
                putExtra(EXTRA_REASON, reason)
                if (packageName != null) {
                    putExtra(EXTRA_PACKAGE, packageName)
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            val launchCtx = if (context is android.app.Activity) {
                context
            } else {
                com.example.service.accessibility.SelfControlAccessibilityService.instance ?: context
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        val options = android.app.ActivityOptions.makeBasic()
                        options.setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        launchCtx.startActivity(intent, options.toBundle())
                    } catch (_: Throwable) {
                        launchCtx.startActivity(intent)
                    }
                } else {
                    launchCtx.startActivity(intent)
                }
            } catch (e: Exception) {
                isShowing = false
                android.util.Log.e("InterventionActivity", "Failed to start intervention activity: ${e.message}")
            }
        }
    }

    private var targetState by mutableStateOf("Blocked Content")
    private var reasonState by mutableStateOf("This violates your self-control focus rules.")
    private var packageState by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        isShowing = true
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isShowing = hasFocus
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Do NOT launch homeIntent here as it minimizes the block screen and allows bypassing it.
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        enableEdgeToEdge()
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        targetState = intent.getStringExtra(EXTRA_TARGET) ?: "Blocked Content"
        reasonState = intent.getStringExtra(EXTRA_REASON) ?: "This violates your self-control focus rules."
        packageState = intent.getStringExtra(EXTRA_PACKAGE)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                BlockedInterventionScreen(
                    target = targetState,
                    reason = reasonState,
                    onGoHome = {
                        markDismissed(packageState)
                        com.example.service.accessibility.SelfControlAccessibilityService.onInterventionDismissed(packageState)
                        com.example.service.accessibility.SelfControlAccessibilityService.ignorePackageTemporarily(packageState, 250L)

                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                com.example.SelfControlApp.repository.preferences.clearTemporaryUnlock()
                            } catch (_: Throwable) {}
                            try {
                                com.example.service.accessibility.SelfControlAccessibilityService.resetAppToSafeState(packageState)
                            } catch (_: Throwable) {}
                        }

                        // Start Home intent cleanly
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        try {
                            startActivity(homeIntent)
                        } catch (_: Throwable) {}

                        finish()
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetState = intent.getStringExtra(EXTRA_TARGET) ?: "Blocked Content"
        reasonState = intent.getStringExtra(EXTRA_REASON) ?: "This violates your self-control focus rules."
        packageState = intent.getStringExtra(EXTRA_PACKAGE) ?: packageState
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }
}

data class BlockScreenCustomizationData(
    val bgType: String = "DEFAULT",
    val mediaPath: String = "",
    val mediaList: List<com.example.data.local.BlockMediaItem> = emptyList(),
    val activeMediaItem: com.example.data.local.BlockMediaItem? = null,
    val title: String = "",
    val subtitle: String = "",
    val buttonText: String = "",
    val alignment: String = "CENTER",
    val showStreak: Boolean = true,
    val showLock: Boolean = true,
    val showTitle: Boolean = true,
    val showSubtitle: Boolean = true,
    val showButton: Boolean = true,
    val showSpeaker: Boolean = true,
    val showTimer: Boolean = true,
    val showTagline: Boolean = true,
    val showDarkOverlay: Boolean = true,
    val videoMuted: Boolean = false
)

@Composable
fun BlockedInterventionScreen(
    target: String,
    reason: String,
    onGoHome: () -> Unit,
    onUnlock: () -> Unit = onGoHome,
    overrideConfig: BlockScreenCustomizationData? = null
) {
    var canDismissByBack by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800L)
        canDismissByBack = true
    }

    BackHandler(enabled = true) {
        if (canDismissByBack) {
            onGoHome()
        }
    }

    val context = LocalContext.current
    val prefs = remember { SelfControlApp.repository.preferences }
    val redirectUrl by prefs.redirectUrl.collectAsState(initial = "")
    val defaultCountdown by prefs.blockedCountdownSeconds.collectAsState(initial = 30)
    val customMessage by prefs.customBlockedMessage.collectAsState(
        initial = "You're stronger than this urge.\nTake a deep breath."
    )

    val storedBgType by prefs.blockScreenBgType.collectAsState(initial = prefs.cachedBlockScreenBgType)
    val storedMediaPath by prefs.blockScreenMediaPath.collectAsState(initial = prefs.cachedBlockScreenMediaPath)
    val storedMediaList by prefs.blockScreenMediaList.collectAsState(initial = prefs.cachedBlockScreenMediaList)
    val storedTitle by prefs.blockScreenTitle.collectAsState(initial = prefs.cachedBlockScreenTitle)
    val storedSubtitle by prefs.blockScreenSubtitle.collectAsState(initial = prefs.cachedBlockScreenSubtitle)
    val storedButtonText by prefs.blockScreenButtonText.collectAsState(initial = prefs.cachedBlockScreenButtonText)
    val storedAlignment by prefs.blockScreenAlignment.collectAsState(initial = prefs.cachedBlockScreenAlignment)
    val storedShowStreak by prefs.blockScreenShowStreak.collectAsState(initial = prefs.cachedBlockScreenShowStreak)
    val storedShowLock by prefs.blockScreenShowLock.collectAsState(initial = prefs.cachedBlockScreenShowLock)
    val storedShowTitle by prefs.blockScreenShowTitle.collectAsState(initial = prefs.cachedBlockScreenShowTitle)
    val storedShowSubtitle by prefs.blockScreenShowSubtitle.collectAsState(initial = prefs.cachedBlockScreenShowSubtitle)
    val storedShowButton by prefs.blockScreenShowButton.collectAsState(initial = prefs.cachedBlockScreenShowButton)
    val storedShowSpeaker by prefs.blockScreenShowSpeaker.collectAsState(initial = prefs.cachedBlockScreenShowSpeaker)
    val storedShowTimer by prefs.blockScreenShowTimer.collectAsState(initial = prefs.cachedBlockScreenShowTimer)
    val storedShowTagline by prefs.blockScreenShowTagline.collectAsState(initial = prefs.cachedBlockScreenShowTagline)
    val storedShowDarkOverlay by prefs.blockScreenShowDarkOverlay.collectAsState(initial = prefs.cachedBlockScreenShowDarkOverlay)
    val storedVideoMuted by prefs.blockScreenVideoMuted.collectAsState(initial = prefs.cachedBlockScreenVideoMuted)
    val dailyStreak by prefs.dailyStreak.collectAsState(initial = 0)

    val effectiveBgType = overrideConfig?.bgType ?: storedBgType
    val effectiveMediaPath = overrideConfig?.mediaPath ?: storedMediaPath
    val effectiveMediaList = overrideConfig?.mediaList?.takeIf { it.isNotEmpty() } ?: storedMediaList

    // If activeMediaItem is explicitly provided (e.g. in preview), use it.
    // Otherwise, when custom media exists and bgType != DEFAULT, pick randomly among all loaded media!
    val selectedMediaItem by remember(effectiveMediaList, overrideConfig?.activeMediaItem, effectiveBgType) {
        mutableStateOf(
            overrideConfig?.activeMediaItem
                ?: if (effectiveBgType != "DEFAULT" && effectiveMediaList.isNotEmpty()) {
                    effectiveMediaList.randomOrNull()
                } else null
        )
    }

    val activePath = selectedMediaItem?.path ?: effectiveMediaPath
    val activeType = selectedMediaItem?.type ?: effectiveBgType

    val effectiveTitle = overrideConfig?.title ?: storedTitle
    val effectiveSubtitle = overrideConfig?.subtitle ?: storedSubtitle
    val effectiveButtonText = overrideConfig?.buttonText ?: storedButtonText
    val effectiveAlignment = overrideConfig?.alignment ?: storedAlignment
    val effectiveShowStreak = overrideConfig?.showStreak ?: storedShowStreak
    val effectiveShowLock = overrideConfig?.showLock ?: storedShowLock
    val effectiveShowTitle = overrideConfig?.showTitle ?: storedShowTitle
    val effectiveShowSubtitle = overrideConfig?.showSubtitle ?: storedShowSubtitle
    val effectiveShowButton = overrideConfig?.showButton ?: storedShowButton
    val effectiveShowSpeaker = overrideConfig?.showSpeaker ?: storedShowSpeaker
    val effectiveShowTimer = overrideConfig?.showTimer ?: storedShowTimer
    val effectiveShowTagline = overrideConfig?.showTagline ?: storedShowTagline
    val effectiveShowDarkOverlay = overrideConfig?.showDarkOverlay ?: storedShowDarkOverlay
    val isVideoMuted = overrideConfig?.videoMuted ?: storedVideoMuted

    val isCustomVideo = effectiveBgType != "DEFAULT" && activeType == "VIDEO" && activePath.isNotBlank() && File(activePath).exists()
    val isCustomImage = effectiveBgType != "DEFAULT" && activeType == "IMAGE" && activePath.isNotBlank() && File(activePath).exists()

    val isTamperProtection = target.contains("Uninstall", ignoreCase = true) ||
            target.contains("Permission", ignoreCase = true) ||
            target.contains("Tamper", ignoreCase = true) ||
            target.contains("Accessibility", ignoreCase = true) ||
            target.contains("Force Stop", ignoreCase = true) ||
            target.contains("App Info", ignoreCase = true) ||
            target.contains("Storage", ignoreCase = true) ||
            target.contains("Accountability", ignoreCase = true)
    val isFocusBlock = reason.contains("Focus Mode", ignoreCase = true) ||
            reason.contains("Focus Session", ignoreCase = true)

    val coroutineScope = rememberCoroutineScope()
    var countdownSeconds by remember { mutableIntStateOf(30) }
    var isTimerActive by remember { mutableStateOf(false) }

    LaunchedEffect(defaultCountdown) {
        countdownSeconds = defaultCountdown
    }

    // Countdown loop
    LaunchedEffect(isTimerActive) {
        if (isTimerActive) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            isTimerActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Solid dark canvas instantly prevents bypass without any flashing
            .testTag("blocked_screen")
    ) {
        // Background media (Video, Image, or Default Mountain)
        when {
            isCustomVideo -> {
                LoopingVideoPlayer(
                    videoPath = activePath,
                    isMuted = isVideoMuted,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isCustomImage -> {
                AsyncImage(
                    model = File(activePath),
                    contentDescription = "Custom motivation background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            effectiveBgType == "DEFAULT" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_blocked_mountain_bg),
                    contentDescription = "Atmospheric mountain background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Dark gradient overlay for optimal legibility while allowing background video/image to shine
        if (effectiveShowDarkOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66000000),
                                Color(0x990B120E),
                                Color(0xDD090E0B)
                            )
                        )
                    )
            )
        }

        // Top bar actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onGoHome,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            if (effectiveShowSpeaker) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val nextMuted = !isVideoMuted
                            prefs.setBlockScreenVideoMuted(nextMuted)
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    val isMutedState = isVideoMuted
                    Icon(
                        imageVector = if (!isMutedState) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (!isMutedState) "Mute sound" else "Unmute sound",
                        tint = if (!isMutedState) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        // Content layout aligned to user preference (TOP, CENTER, or BOTTOM)
        val arrangement = when (effectiveAlignment) {
            "TOP" -> Arrangement.Top
            "BOTTOM" -> Arrangement.Bottom
            else -> Arrangement.Center
        }
        val contentPadding = when (effectiveAlignment) {
            "TOP" -> Modifier.padding(top = 110.dp, bottom = 40.dp)
            "BOTTOM" -> Modifier.padding(top = 80.dp, bottom = 90.dp)
            else -> Modifier.padding(vertical = 40.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .then(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = arrangement
        ) {
            // Streak badge (keeps user emotionally obsessed with their streak!)
            if (effectiveShowStreak) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x33FFB300),
                    border = BorderStroke(1.dp, Color(0x88FFB300)),
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dailyStreak > 0) "🔥 $dailyStreak Day Streak Active" else "🔥 Protect Your Streak",
                            color = Color(0xFFFFD54F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Lock icon in translucent circular badge
            if (effectiveShowLock) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = if (isTamperProtection) Color(0xFFFF5252) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Heading: Custom title or default "Focus Mode Active" / "This content is blocked"
            if (effectiveShowTitle) {
                Text(
                    text = when {
                        effectiveTitle.isNotBlank() -> effectiveTitle
                        isFocusBlock -> "Focus Mode Active"
                        isTamperProtection -> "Protection Tampering Blocked"
                        else -> "This content is blocked"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("blocked_title")
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Subtitle / Emotional message
            if (effectiveShowSubtitle) {
                Text(
                    text = when {
                        effectiveSubtitle.isNotBlank() -> effectiveSubtitle
                        isFocusBlock -> {
                            reason.ifBlank { "This app is blocked during your scheduled focus time. Only phone calls, SMS, and allowed apps are permitted." }
                        }
                        isTamperProtection -> {
                            reason.ifBlank { "You cannot turn off permissions or uninstall Reclaim during active protection. Changes are only permitted with your partner's verification code." }
                        }
                        else -> customMessage
                    },
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (target.isNotEmpty() && target != "Blocked Content") {
                Text(
                    text = target,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action Button
            if (effectiveShowButton) {
                Button(
                    onClick = onGoHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(54.dp)
                        .testTag("return_to_safety_button")
                ) {
                    Text(
                        text = when {
                            effectiveButtonText.isNotBlank() -> effectiveButtonText
                            isFocusBlock -> "Return to Home"
                            isTamperProtection -> "Return to Safety"
                            else -> "Stay Disciplined (Go Home)"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Standard urge-surfing timer for content
            if (effectiveShowTimer && !isTamperProtection && !isFocusBlock) {
                Button(
                    onClick = {
                        if (!isTimerActive && countdownSeconds > 0) {
                            isTimerActive = true
                        } else {
                            onGoHome()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp)
                        .testTag("take_seconds_button")
                ) {
                    Text(
                        text = when {
                            isTimerActive -> "Breathe ($countdownSeconds s remaining)"
                            countdownSeconds <= 0 -> "Urge passed • Continue"
                            else -> "Take $countdownSeconds seconds"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Redirect button if custom redirect URL configured
            if (redirectUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            onGoHome()
                        } catch (e: Exception) {
                            onGoHome()
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Redirect",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Redirect to focus space",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Bottom tagline: "A better you is waiting."
        if (effectiveShowTagline) {
            Text(
                text = "A better you is waiting.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}
