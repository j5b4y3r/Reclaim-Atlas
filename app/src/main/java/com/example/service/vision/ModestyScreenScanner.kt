package com.example.service.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.example.SelfControlApp
import com.example.domain.vision.ModestyDetector
import com.example.domain.vision.ModestySensitivity
import com.example.domain.vision.ModestyShieldStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intelligent on-device screen scanner.
 * Adaptive sampling pipeline:
 *  - Checks if visual media (video surface, player, or large image container) is visible within the screen viewport.
 *  - If NO media is visible: zero CPU/GPU/RAM usage (0 FPS).
 *  - If media IS visible: samples frames at the configured rate (default 3 FPS, 333ms interval) and checks modesty.
 */
class ModestyScreenScanner(
    private val service: AccessibilityService,
    private val overlayManager: ModestyOverlayManager
) {
    private val detector = ModestyDetector()
    private val scannerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val executor = Executors.newSingleThreadExecutor()
    private val isEvaluating = AtomicBoolean(false)

    @Volatile
    private var isEnabled = false
    @Volatile
    private var scanFps = 3
    @Volatile
    private var shieldStyle = ModestyShieldStyle.TARGETED_BLUR
    @Volatile
    private var blurIntensity = 80
    @Volatile
    private var sensitivity = ModestySensitivity.STRICT_AWRAH

    private var activeLoopJob: Job? = null
    private var lastMediaDetectedTime = 0L

    companion object {
        private const val TAG = "ModestyScreenScanner"

        private val MEDIA_CLASS_NAMES = setOf(
            "android.view.SurfaceView",
            "android.view.TextureView",
            "android.widget.VideoView",
            "com.google.android.exoplayer2.ui.PlayerView",
            "com.google.android.exoplayer2.ui.StyledPlayerView",
            "androidx.media3.ui.PlayerView",
            "com.instagram.common.ui.widget.imageview.IgImageView",
            "com.instagram.ui.mediaactions.MediaActionsView",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme"
        )
    }

    fun start() {
        if (isEnabled) return
        isEnabled = true

        scannerScope.launch {
            val prefs = SelfControlApp.repository.preferences
            prefs.aiModestyScanFps.collect { scanFps = it }
        }
        scannerScope.launch {
            val prefs = SelfControlApp.repository.preferences
            prefs.aiModestyShieldStyle.collect {
                shieldStyle = ModestyShieldStyle.fromString(it)
            }
        }
        scannerScope.launch {
            val prefs = SelfControlApp.repository.preferences
            prefs.aiModestyBlurIntensity.collect { blurIntensity = it }
        }
        scannerScope.launch {
            val prefs = SelfControlApp.repository.preferences
            prefs.aiModestySensitivity.collect {
                sensitivity = ModestySensitivity.fromString(it)
            }
        }

        startAdaptiveLoop()
    }

    fun stop() {
        isEnabled = false
        activeLoopJob?.cancel()
        activeLoopJob = null
        overlayManager.clearOverlay()
    }

    /**
     * Inspects active accessibility window to determine if any media surface is visible.
     */
    fun onWindowContentChanged(root: AccessibilityNodeInfo?) {
        if (!isEnabled || root == null) return

        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val hasMedia = hasVisibleMediaNode(root, screenWidth, screenHeight)
        if (hasMedia) {
            lastMediaDetectedTime = SystemClock.uptimeMillis()
        }
    }

    private fun startAdaptiveLoop() {
        activeLoopJob?.cancel()
        activeLoopJob = scannerScope.launch {
            while (isActive && isEnabled) {
                val now = SystemClock.uptimeMillis()
                val isMediaActive = (now - lastMediaDetectedTime) < 1500L

                if (isMediaActive) {
                    // Calculate delay interval based on configured FPS
                    val intervalMs = when (scanFps) {
                        5 -> 200L
                        1 -> 1000L
                        else -> 333L // 3 FPS default
                    }

                    captureAndEvaluateScreen()
                    delay(intervalMs)
                } else {
                    // Idle state: No visual media visible on screen, sleep to conserve 100% CPU/battery
                    overlayManager.clearOverlay()
                    delay(500L)
                }
            }
        }
    }

    private fun captureAndEvaluateScreen() {
        if (isEvaluating.getAndSet(true)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                if (bitmap != null) {
                                    // Make a software copy for pixel evaluation
                                    val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    hardwareBuffer.close()
                                    processBitmap(softwareBitmap)
                                } else {
                                    hardwareBuffer.close()
                                    isEvaluating.set(false)
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error processing screenshot buffer", e)
                                isEvaluating.set(false)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            isEvaluating.set(false)
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed calling takeScreenshot", e)
                isEvaluating.set(false)
            }
        } else {
            // Older Android fallback
            isEvaluating.set(false)
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        scannerScope.launch {
            try {
                val result = detector.evaluate(bitmap, sensitivity)
                if (result.isViolating) {
                    overlayManager.showModestyShield(
                        regions = result.regions,
                        shieldStyle = shieldStyle,
                        blurIntensity = blurIntensity,
                        service = service
                    )
                } else {
                    overlayManager.clearOverlay()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error evaluating bitmap", t)
            } finally {
                bitmap.recycle()
                isEvaluating.set(false)
            }
        }
    }

    private fun hasVisibleMediaNode(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        depth: Int = 0
    ): Boolean {
        if (depth > 12) return false

        val className = node.className?.toString() ?: ""
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // Ensure the element is actually within the active screen viewport (not below the height or scrolled off)
        val isVisibleOnScreen = rect.top < screenHeight &&
                rect.bottom > 0 &&
                rect.left < screenWidth &&
                rect.right > 0 &&
                rect.width() >= 120 &&
                rect.height() >= 120

        if (isVisibleOnScreen) {
            if (MEDIA_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }) {
                return true
            }

            // Detect large image views (likely feeds, stories, or reels media rather than small icons)
            if (className.contains("ImageView", ignoreCase = true) &&
                rect.width() > (screenWidth * 0.45f) &&
                rect.height() > 180
            ) {
                return true
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            if (hasVisibleMediaNode(child, screenWidth, screenHeight, depth + 1)) {
                return true
            }
        }

        return false
    }
}
