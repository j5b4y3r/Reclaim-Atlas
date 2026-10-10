package com.example.service.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SelfControlApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusModeService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.focus.START"
        const val ACTION_STOP = "com.example.service.focus.STOP"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val NOTIFICATION_CHANNEL_ID = "focus_channel"
        const val NOTIFICATION_ID = 3001

        @Volatile
        var isFocusActive = false
            private set

        @Volatile
        var remainingMinutes = 0
            private set

        fun startFocus(context: Context, durationMinutes: Int) {
            val intent = Intent(context, FocusModeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopFocus(context: Context) {
            val intent = Intent(context, FocusModeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var countdownJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var targetMinutes = 25
    private var currentSessionId: Long = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFocusInternal(isEarlyCancel = true)
            return START_NOT_STICKY
        }

        targetMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 25) ?: 25
        remainingMinutes = targetMinutes
        isFocusActive = true

        val notification = buildNotification(remainingMinutes)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    0
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            android.util.Log.e("FocusModeService", "startForeground error: ${e.message}", e)
        }

        serviceScope.launch(Dispatchers.IO) {
            currentSessionId = SelfControlApp.repository.startFocusSession(targetMinutes)
        }

        startCountdown()
        return START_STICKY
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (isActive && remainingMinutes > 0) {
                delay(60_000L) // 1 minute
                remainingMinutes--
                updateNotification(remainingMinutes)

                if (remainingMinutes <= 0) {
                    // Session successfully completed!
                    finishFocusSession(isSuccess = true)
                    break
                }
            }
        }
    }

    private fun finishFocusSession(isSuccess: Boolean) {
        val completed = targetMinutes - remainingMinutes
        serviceScope.launch(Dispatchers.IO) {
            if (isSuccess) {
                SelfControlApp.repository.completeFocusSession(currentSessionId, targetMinutes)
            } else {
                SelfControlApp.repository.cancelFocusSession(currentSessionId, completed)
            }
        }
        stopFocusInternal(isEarlyCancel = !isSuccess)
    }

    private fun stopFocusInternal(isEarlyCancel: Boolean) {
        isFocusActive = false
        countdownJob?.cancel()
        if (isEarlyCancel && currentSessionId != 0L) {
            val completed = targetMinutes - remainingMinutes
            serviceScope.launch(Dispatchers.IO) {
                SelfControlApp.repository.cancelFocusSession(currentSessionId, completed)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(minutes: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(minutes))
    }

    private fun buildNotification(minutes: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_focus_title))
            .setContentText(getString(R.string.notification_focus_text, minutes))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Focus Mode Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows remaining focus session time"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isFocusActive = false
        countdownJob?.cancel()
        super.onDestroy()
    }
}
