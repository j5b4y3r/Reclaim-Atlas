package com.example.service.vision

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.domain.vision.DetectedModestyRegion
import com.example.domain.vision.ModestyShieldStyle

/**
 * WindowManager overlay controller that renders real-time modesty shields.
 * Supports:
 *  - Targeted Blur directly over the detected body region (intensity 25% - 100%).
 *  - Targeted Solid Blackout shield over the detected body region.
 *  - Full-Screen frosted "Lower Your Gaze" barrier with rapid exit button.
 */
class ModestyOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeOverlayViews = mutableListOf<View>()
    private var isFullScreenShowing = false

    companion object {
        private const val TAG = "ModestyOverlayManager"
    }

    /**
     * Updates or draws modesty shields over offending media regions.
     */
    fun showModestyShield(
        regions: List<DetectedModestyRegion>,
        shieldStyle: ModestyShieldStyle,
        blurIntensity: Int, // 25 to 100
        service: AccessibilityService? = null
    ) {
        if (regions.isEmpty()) {
            clearOverlay()
            return
        }

        mainHandler.post {
            try {
                when (shieldStyle) {
                    ModestyShieldStyle.FULL_SCREEN -> {
                        showFullScreenShield(regions, service)
                    }
                    ModestyShieldStyle.TARGETED_BLUR -> {
                        showTargetedShields(regions, isBlur = true, blurIntensity = blurIntensity)
                    }
                    ModestyShieldStyle.BLACK_OVERLAY -> {
                        showTargetedShields(regions, isBlur = false, blurIntensity = 100)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to render modesty shield overlay", e)
            }
        }
    }

    private fun showTargetedShields(
        regions: List<DetectedModestyRegion>,
        isBlur: Boolean,
        blurIntensity: Int
    ) {
        clearOverlay()

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        for (region in regions) {
            val bounds = region.normalizedBounds
            val viewLeft = (bounds.left * screenWidth).toInt().coerceAtLeast(0)
            val viewTop = (bounds.top * screenHeight).toInt().coerceAtLeast(0)
            val viewWidth = ((bounds.width() * screenWidth).toInt()).coerceIn(60, screenWidth)
            val viewHeight = ((bounds.height() * screenHeight).toInt()).coerceIn(60, screenHeight)

            val overlayView = FrameLayout(context).apply {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f

                    if (isBlur) {
                        // Calculate opacity based on blur intensity (25% to 100%)
                        val alpha = (blurIntensity.coerceIn(25, 100) * 2.55f).toInt().coerceIn(64, 255)
                        setColor(Color.argb(alpha, 18, 20, 28))
                        setStroke(2, Color.argb((alpha * 0.5f).toInt(), 138, 143, 168))
                    } else {
                        // Solid black privacy shield
                        setColor(Color.parseColor("#0C0D14"))
                        setStroke(3, Color.parseColor("#282A3A"))
                    }
                }
                background = drawable

                // Centered discreet modesty shield label
                val label = TextView(context).apply {
                    text = if (isBlur) "🛡️ Filtered" else "🛡️ Modesty Shield"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }
                addView(
                    label,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                viewWidth,
                viewHeight,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = viewLeft
                y = viewTop
            }

            windowManager.addView(overlayView, params)
            activeOverlayViews.add(overlayView)
        }
    }

    private fun showFullScreenShield(
        regions: List<DetectedModestyRegion>,
        service: AccessibilityService?
    ) {
        if (isFullScreenShowing) return
        clearOverlay()
        isFullScreenShowing = true

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E60A0B10")) // 90% frosted dark
            setPadding(48, 64, 48, 64)

            // Shield Icon / Spiritual Header
            val iconText = TextView(context).apply {
                text = "🛡️"
                textSize = 40f
                gravity = Gravity.CENTER
            }
            addView(iconText)

            val titleView = TextView(context).apply {
                text = "Lower Your Gaze"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 8)
            }
            addView(titleView)

            val reminderView = TextView(context).apply {
                text = "“Tell the believing men to lower their gaze and protect their private parts; that is purer for them.”\n(Surah An-Nur 24:30)"
                textSize = 13f
                setTextColor(Color.parseColor("#B0B5C6"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            }
            addView(reminderView)

            val summaryView = TextView(context).apply {
                val types = regions.joinToString { it.violationType.label }
                text = "Protected: $types"
                textSize = 12f
                setTextColor(Color.parseColor("#E06A3B"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            }
            addView(summaryView)

            // Action Buttons
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER

                val btnCloseApp = Button(context).apply {
                    text = "Close App & Home"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#E06A3B"))
                    setOnClickListener {
                        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        clearOverlay()
                    }
                }
                addView(btnCloseApp)

                val btnDismiss = Button(context).apply {
                    text = "Dismiss"
                    setTextColor(Color.parseColor("#B0B5C6"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(32, 0, 0, 0)
                    setOnClickListener {
                        clearOverlay()
                    }
                }
                addView(btnDismiss)
            }
            addView(btnRow)
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(rootLayout, params)
        activeOverlayViews.add(rootLayout)
    }

    /**
     * Safely clears all currently displayed modesty overlays.
     */
    fun clearOverlay() {
        mainHandler.post {
            isFullScreenShowing = false
            for (view in activeOverlayViews) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {}
            }
            activeOverlayViews.clear()
        }
    }
}
