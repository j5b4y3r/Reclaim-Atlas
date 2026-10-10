package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Highly optimized, hardware-accelerated video background player.
 * Uses Android's native MediaPlayer + TextureView for smooth looping playback,
 * zero memory leaks, and seamless integration with Compose layers and gradients.
 */
@Composable
fun LoopingVideoPlayer(
    videoPath: String,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mediaPlayerHolder = remember(videoPath) {
        object {
            var player: MediaPlayer? = null
            var surface: Surface? = null
            var isPrepared: Boolean = false
            var videoWidth: Int = 0
            var videoHeight: Int = 0
        }
    }

    // Reactively update volume when muted state changes without restarting playback
    LaunchedEffect(isMuted) {
        try {
            val volume = if (isMuted) 0f else 1.0f
            mediaPlayerHolder.player?.setVolume(volume, volume)
        } catch (_: Throwable) {}
    }

    // Lifecycle observer to pause video when app goes to background and resume when returning
    DisposableEffect(lifecycleOwner, videoPath) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (mediaPlayerHolder.player?.isPlaying == true) {
                            mediaPlayerHolder.player?.pause()
                        }
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        if (mediaPlayerHolder.isPrepared && mediaPlayerHolder.player?.isPlaying == false) {
                            mediaPlayerHolder.player?.start()
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        try {
                            mediaPlayerHolder.player?.stop()
                            mediaPlayerHolder.player?.reset()
                            mediaPlayerHolder.player?.release()
                        } catch (_: Throwable) {}
                        mediaPlayerHolder.player = null
                        mediaPlayerHolder.surface?.release()
                        mediaPlayerHolder.surface = null
                    }
                    else -> {}
                }
            } catch (_: Throwable) {}
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mediaPlayerHolder.player?.stop()
                mediaPlayerHolder.player?.reset()
                mediaPlayerHolder.player?.release()
            } catch (_: Throwable) {}
            mediaPlayerHolder.player = null
            mediaPlayerHolder.surface?.release()
            mediaPlayerHolder.surface = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val textureView = TextureView(ctx)
                textureView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        try {
                            mediaPlayerHolder.surface?.release()
                            val surface = Surface(st)
                            mediaPlayerHolder.surface = surface

                            val player = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                        .build()
                                )
                                setSurface(surface)
                                isLooping = true

                                val file = File(videoPath)
                                if (file.exists()) {
                                    setDataSource(file.absolutePath)
                                } else {
                                    setDataSource(ctx, android.net.Uri.parse(videoPath))
                                }

                                setOnVideoSizeChangedListener { _, vw, vh ->
                                    mediaPlayerHolder.videoWidth = vw
                                    mediaPlayerHolder.videoHeight = vh
                                    updateTextureTransform(textureView, width, height, vw, vh)
                                }

                                setOnPreparedListener { mp ->
                                    mediaPlayerHolder.isPrepared = true
                                    val vol = if (isMuted) 0f else 1.0f
                                    mp.setVolume(vol, vol)
                                    mp.start()
                                }

                                setOnErrorListener { _, what, extra ->
                                    Log.e("LoopingVideoPlayer", "MediaPlayer error: what=$what, extra=$extra")
                                    true
                                }

                                prepareAsync()
                            }
                            mediaPlayerHolder.player = player
                        } catch (e: Throwable) {
                            Log.e("LoopingVideoPlayer", "Failed to initialize video player: ${e.message}")
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        updateTextureTransform(
                            textureView,
                            width,
                            height,
                            mediaPlayerHolder.videoWidth,
                            mediaPlayerHolder.videoHeight
                        )
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        try {
                            mediaPlayerHolder.player?.stop()
                            mediaPlayerHolder.player?.reset()
                            mediaPlayerHolder.player?.release()
                        } catch (_: Throwable) {}
                        mediaPlayerHolder.player = null
                        mediaPlayerHolder.surface?.release()
                        mediaPlayerHolder.surface = null
                        mediaPlayerHolder.isPrepared = false
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }

                addView(textureView)
            }
        }
    )
}

/**
 * Scales and centers the video inside TextureView using matrix transformation (Center-Crop)
 * so the video fills the screen smoothly without aspect ratio distortion.
 */
private fun updateTextureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

    val scaleX: Float
    val scaleY: Float

    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()

    if (viewAspect > videoAspect) {
        // View is wider than video: match width, crop top/bottom
        scaleX = 1f
        scaleY = (viewWidth.toFloat() / videoWidth.toFloat()) / (viewHeight.toFloat() / videoHeight.toFloat())
    } else {
        // View is taller than video: match height, crop left/right
        scaleX = (viewHeight.toFloat() / videoHeight.toFloat()) / (viewWidth.toFloat() / videoWidth.toFloat())
        scaleY = 1f
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    textureView.setTransform(matrix)
}
