package com.example.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SelfControlApp
import com.example.data.model.BlockCategory
import com.example.domain.engine.EvaluationResult
import com.example.domain.engine.RuleEngine
import com.example.domain.engine.SafeSearchHelper
import com.example.ui.screens.InterventionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class SelfControlVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.service.vpn.START"
        const val ACTION_STOP = "com.example.service.vpn.STOP"
        const val NOTIFICATION_CHANNEL_ID = "vpn_channel"
        const val NOTIFICATION_ID = 2001

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var totalBlockedRequests = 0
            private set

        fun startVpn(context: Context) {
            val intent = Intent(context, SelfControlVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, SelfControlVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val domainEvaluationCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private var upstreamDnsSocket: DatagramSocket? = null
    private val upstreamDnsAddress: InetAddress by lazy { InetAddress.getByName("1.1.1.3") }
    private val socketLock = Any()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnInternal()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
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
            Log.e("VpnService", "startForeground error: ${e.message}", e)
        }
        startVpnTunnel()
        return START_STICKY
    }

    private fun startVpnTunnel() {
        if (isRunning) return
        isRunning = true

        try {
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress("10.0.0.2", 32)
                .addDnsServer("1.1.1.3") // Cloudflare Family DNS Primary (blocks adult content & malware)
                .addDnsServer("1.0.0.3") // Cloudflare Family DNS Secondary
                .addRoute("1.1.1.3", 32) // Route DNS packets into VPN tunnel for local + Cloudflare inspection
                .addRoute("1.0.0.3", 32)
                .addRoute("10.0.0.0", 8)
                .setMtu(1500)
                .setBlocking(true)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                isRunning = false
                stopSelf()
                return
            }

            try {
                upstreamDnsSocket = DatagramSocket().apply {
                    protect(this)
                    soTimeout = 1800
                }
            } catch (e: Throwable) {
                Log.w("VpnService", "Could not pre-bind persistent upstream socket: ${e.message}")
            }

            vpnJob = serviceScope.launch {
                runPacketLoop(vpnInterface!!)
            }

            // Record restart timestamp for diagnostics
            serviceScope.launch {
                SelfControlApp.repository.preferences.recordServiceRestart()
            }
        } catch (e: Exception) {
            Log.e("VpnService", "Failed to establish VPN", e)
            isRunning = false
            stopSelf()
        }
    }

    private suspend fun runPacketLoop(descriptor: ParcelFileDescriptor) {
        val inputStream = FileInputStream(descriptor.fileDescriptor)
        val outputStream = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        while (serviceScope.isActive && isRunning) {
            try {
                val length = inputStream.read(packet.array())
                if (length > 0) {
                    packet.limit(length)
                    handlePacket(packet, outputStream)
                    packet.clear()
                }
            } catch (e: Exception) {
                if (!isRunning) break
            }
        }
    }

    private suspend fun handlePacket(packet: ByteBuffer, outputStream: FileOutputStream) {
        val buffer = packet.array()
        val length = packet.limit()

        // Check if IPv4 packet (first nibble == 4)
        if (length < 20 || (buffer[0].toInt() and 0xF0) != 0x40) {
            return
        }

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != 17) { // UDP = 17
            return
        }

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return

        val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        if (destPort != 53) {
            return // Not DNS
        }

        // Parse DNS query
        val dnsOffset = ihl + 8
        val domain = extractDomainFromDns(buffer, dnsOffset, length) ?: return

        val repo = SelfControlApp.repository
        val prefs = repo.preferences
        val isTempUnlocked = prefs.isTemporaryUnlockActiveFast()
        val isAdultBlocked = prefs.cachedAdultContentBlocked
        val isImageSearchBlocked = prefs.cachedImageVideoSearchBlocked
        val rules = repo.getEnabledRulesCached()

        // Fast O(1) in-memory decision cache to bypass rule evaluation on identical domains
        var evalCategory: BlockCategory = BlockCategory.CUSTOM
        val cachedResult = domainEvaluationCache[domain]
        val isBlocked = if (cachedResult != null) {
            cachedResult
        } else {
            var eval = RuleEngine.evaluateDomain(domain, rules, isTempUnlocked, isAdultBlocked)
            if (!eval.isBlocked && isImageSearchBlocked) {
                val lower = domain.lowercase()
                if (lower.startsWith("images.google") || lower.startsWith("video.google") || lower == "imghp" ||
                    lower.startsWith("images.search.yahoo") || lower.startsWith("video.search.yahoo") ||
                    lower.startsWith("images.yandex") || lower.startsWith("video.yandex")
                ) {
                    eval = EvaluationResult(
                        isBlocked = true,
                        category = BlockCategory.IMAGE_VIDEO_SEARCH,
                        reason = "Image & Video search blocked"
                    )
                }
            }
            if (domainEvaluationCache.size > 2000) {
                domainEvaluationCache.clear()
            }
            domainEvaluationCache[domain] = eval.isBlocked
            evalCategory = eval.category
            eval.isBlocked
        }

        if (isBlocked) {
            totalBlockedRequests++
            repo.recordBlockAttempt(
                targetName = domain,
                ruleType = com.example.data.model.RuleType.DOMAIN,
                category = evalCategory
            )

            // Only trigger UI intervention from VPN if AccessibilityService is not running
            if (!com.example.service.accessibility.SelfControlAccessibilityService.isRunning &&
                !InterventionActivity.isShowing &&
                !InterventionActivity.isInDismissCooldown()
            ) {
                InterventionActivity.start(this, domain, evalCategory.displayName)
            }

            // Send back NXDOMAIN DNS response (RCODE = 3)
            sendDnsBlockResponse(buffer, ihl, dnsOffset, length, outputStream)
        } else {
            // Forward non-blocked DNS query upstream to 1.1.1.3 (Cloudflare Family DNS)
            forwardDnsQuery(buffer, ihl, dnsOffset, length, outputStream, domain, isAdultBlocked)
        }
    }

    private fun forwardDnsQuery(
        buffer: ByteArray,
        ihl: Int,
        dnsOffset: Int,
        totalLength: Int,
        outputStream: FileOutputStream,
        domain: String,
        isAdultBlocked: Boolean
    ) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dnsPacket = buffer.copyOfRange(dnsOffset, totalLength)
                val responseBuffer = ByteArray(2048)
                val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)

                synchronized(socketLock) {
                    var socket = upstreamDnsSocket
                    if (socket == null || socket.isClosed) {
                        socket = DatagramSocket().apply {
                            protect(this)
                            soTimeout = 1800
                        }
                        upstreamDnsSocket = socket
                    }
                    val outPacket = DatagramPacket(dnsPacket, dnsPacket.size, upstreamDnsAddress, 53)
                    socket.send(outPacket)
                    socket.receive(inPacket)
                }

                val responseLength = inPacket.length

                // Layer 1 Inspection: Check if Cloudflare Family DNS (1.1.1.3) sinkholed this domain to 0.0.0.0
                if (isAdultBlocked && com.example.domain.engine.AdultBlockingEngine.isCloudflareBlockedDnsResponse(responseBuffer, responseLength)) {
                    com.example.domain.engine.AdultBlockingEngine.rememberAdultDomain(domain, "cloudflare_family_dns")
                    totalBlockedRequests++
                    domainEvaluationCache[domain] = true
                    SelfControlApp.repository.recordBlockAttempt(
                        targetName = domain,
                        ruleType = com.example.data.model.RuleType.DOMAIN,
                        category = com.example.data.model.BlockCategory.ADULT
                    )
                    if (!com.example.service.accessibility.SelfControlAccessibilityService.isRunning &&
                        !InterventionActivity.isShowing &&
                        !InterventionActivity.isInDismissCooldown()
                    ) {
                        InterventionActivity.start(this@SelfControlVpnService, domain, BlockCategory.ADULT.displayName)
                    }
                    synchronized(outputStream) {
                        sendDnsBlockResponse(buffer, ihl, dnsOffset, totalLength, outputStream)
                    }
                    return@launch
                }
                val fullResponse = ByteArray(ihl + 8 + responseLength)

                // Copy IPv4 header
                System.arraycopy(buffer, 0, fullResponse, 0, ihl)
                // Swap IP addresses
                for (i in 0 until 4) {
                    val temp = fullResponse[12 + i]
                    fullResponse[12 + i] = fullResponse[16 + i]
                    fullResponse[16 + i] = temp
                }
                // Update total length in IPv4 header
                val totalLen = ihl + 8 + responseLength
                fullResponse[2] = ((totalLen shr 8) and 0xFF).toByte()
                fullResponse[3] = (totalLen and 0xFF).toByte()

                // UDP header
                val srcPort0 = buffer[ihl]
                val srcPort1 = buffer[ihl + 1]
                fullResponse[ihl] = buffer[ihl + 2]     // Dest port becomes src
                fullResponse[ihl + 1] = buffer[ihl + 3]
                fullResponse[ihl + 2] = srcPort0        // Src port becomes dest
                fullResponse[ihl + 3] = srcPort1
                val udpLen = 8 + responseLength
                fullResponse[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte()
                fullResponse[ihl + 5] = (udpLen and 0xFF).toByte()
                fullResponse[ihl + 6] = 0 // Checksum 0
                fullResponse[ihl + 7] = 0

                // Copy DNS payload
                System.arraycopy(responseBuffer, 0, fullResponse, ihl + 8, responseLength)

                synchronized(outputStream) {
                    outputStream.write(fullResponse, 0, fullResponse.size)
                }
            } catch (_: Exception) {
                // Upstream timeout or socket error
            }
        }
    }

    private fun extractDomainFromDns(buffer: ByteArray, dnsOffset: Int, totalLength: Int): String? {
        try {
            // DNS header is 12 bytes
            var pos = dnsOffset + 12
            val domainBuilder = StringBuilder()

            while (pos < totalLength) {
                val labelLength = buffer[pos].toInt() and 0xFF
                if (labelLength == 0) break
                pos++
                if (pos + labelLength > totalLength) return null

                if (domainBuilder.isNotEmpty()) {
                    domainBuilder.append(".")
                }
                for (i in 0 until labelLength) {
                    domainBuilder.append(buffer[pos + i].toInt().toChar())
                }
                pos += labelLength
            }
            return domainBuilder.toString()
        } catch (_: Exception) {
            return null
        }
    }

    private fun sendDnsBlockResponse(
        buffer: ByteArray,
        ihl: Int,
        dnsOffset: Int,
        totalLength: Int,
        outputStream: FileOutputStream
    ) {
        try {
            val response = buffer.copyOf(totalLength)
            // Swap source and destination IP in IPv4 header
            for (i in 0 until 4) {
                val temp = response[12 + i]
                response[12 + i] = response[16 + i]
                response[16 + i] = temp
            }

            // Swap source and destination ports in UDP header
            val srcPort0 = response[ihl]
            val srcPort1 = response[ihl + 1]
            response[ihl] = response[ihl + 2]
            response[ihl + 1] = response[ihl + 3]
            response[ihl + 2] = srcPort0
            response[ihl + 3] = srcPort1

            // Set DNS Response flag and NXDOMAIN (RCODE = 3)
            response[dnsOffset + 2] = 0x81.toByte() // QR = 1, RD = 1
            response[dnsOffset + 3] = 0x83.toByte() // RA = 1, RCODE = 3 (NXDOMAIN)

            outputStream.write(response, 0, totalLength)
        } catch (_: Exception) {
            // Ignore socket write errors
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_vpn_active))
            .setContentText(getString(R.string.notification_vpn_desc))
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
                "VPN Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing protection status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopVpnInternal() {
        isRunning = false
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        synchronized(socketLock) {
            try {
                upstreamDnsSocket?.close()
            } catch (_: Throwable) {}
            upstreamDnsSocket = null
        }
        domainEvaluationCache.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }
}
