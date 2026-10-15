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
import com.example.domain.engine.AdultBlockingEngine
import com.example.domain.engine.EvaluationResult
import com.example.domain.engine.RuleEngine
import com.example.domain.engine.SafeSearchHelper
import com.example.ui.screens.InterventionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

        // CleanBrowsing Adult Filter DNS (Primary) - blocks all adult websites and enforces SafeSearch
        const val CLEANBROWSING_PRIMARY_IP = "185.228.168.10"
        const val CLEANBROWSING_SECONDARY_IP = "185.228.169.11"
        const val CLEANBROWSING_HOSTNAME = "adult-filter-dns.cleanbrowsing.org"
        const val CLEANBROWSING_IPV6_PRIMARY = "2a0d:2a00:1::1"
        const val CLEANBROWSING_IPV6_SECONDARY = "2a0d:2a00:2::1"

        // Cloudflare Family DNS (Fallback) - blocks adult content and malware
        const val CLOUDFLARE_PRIMARY_IP = "1.1.1.3"
        const val CLOUDFLARE_SECONDARY_IP = "1.0.0.3"

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
    private val domainEvaluationCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, BlockCategory>>()

    private val upstreamDnsAddresses: List<InetAddress> by lazy {
        listOf(
            InetAddress.getByName(CLEANBROWSING_PRIMARY_IP),
            InetAddress.getByName(CLEANBROWSING_SECONDARY_IP)
        )
    }

    private val DOH_BOOTSTRAP_DOMAINS = setOf(
        "cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "family.cloudflare-dns.com",
        "dns.google",
        "dns.google.com",
        "dns.quad9.net",
        "dns.adguard.com",
        "dns-family.adguard.com",
        "doh.opendns.com",
        "dns.nextdns.io"
    )

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
                .addAddress("10.0.0.2", 24) // /24 standard subnet for point-to-point TUN
                // Primary: CleanBrowsing Adult Filter DNS (blocks adult websites and enforces SafeSearch)
                .addDnsServer(CLEANBROWSING_PRIMARY_IP)
                .addDnsServer(CLEANBROWSING_SECONDARY_IP)
                // Route CleanBrowsing DNS servers through tunnel so their port 53 packets are captured
                .addRoute(CLEANBROWSING_PRIMARY_IP, 32)
                .addRoute(CLEANBROWSING_SECONDARY_IP, 32)
                // Intercept popular public DNS resolvers to prevent bypass
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .addRoute("1.1.1.3", 32)
                .addRoute("1.0.0.3", 32)
                .addRoute("9.9.9.9", 32)
                .addRoute("149.112.112.112", 32)
                .addRoute("208.67.222.222", 32)
                .addRoute("208.67.220.220", 32)
                .addRoute("94.140.14.14", 32)
                .addRoute("94.140.15.15", 32)
                .addRoute("10.0.0.0", 8)
                .setMtu(1500)
                .setBlocking(true)

            // Attempt to add IPv6 DNS server and route if device supports it
            try {
                builder.addAddress("2001:db8:0:1::2", 64)
                builder.addDnsServer(CLEANBROWSING_IPV6_PRIMARY)
                builder.addDnsServer(CLEANBROWSING_IPV6_SECONDARY)
                builder.addRoute(CLEANBROWSING_IPV6_PRIMARY, 128)
                builder.addRoute(CLEANBROWSING_IPV6_SECONDARY, 128)
            } catch (t: Throwable) {
                Log.d("VpnService", "IPv6 route setup skipped: ${t.message}")
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                isRunning = false
                stopSelf()
                return
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

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return

        val protocol = buffer[9].toInt() and 0xFF

        // Handle TCP (Protocol 6)
        if (protocol == 6) {
            if (length < ihl + 20) return
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
            // Immediately reject DoT (port 853) or TCP DNS (port 53) with TCP RST
            // This immediately informs Android's Private DNS and network stack that DoT is not supported,
            // triggering instantaneous fallback to CleanBrowsing UDP port 53 without timeout or leak!
            if (destPort == 853 || destPort == 53) {
                sendTcpRst(buffer, ihl, outputStream)
            }
            return
        }

        if (protocol != 17) { // UDP = 17
            return
        }

        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)
        if (udpLen < 8 || length < ihl + udpLen) return

        val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        if (destPort != 53) {
            return // Not DNS
        }

        // Parse DNS query
        val dnsOffset = ihl + 8
        val dnsLength = (udpLen - 8).coerceAtMost(length - dnsOffset)
        val domain = extractDomainFromDns(buffer, dnsOffset, dnsOffset + dnsLength) ?: return

        // Intercept DoH bootstrap domains to prevent browser DoH bypass
        val isDohBootstrap = DOH_BOOTSTRAP_DOMAINS.any {
            domain.equals(it, ignoreCase = true) || domain.endsWith(".$it", ignoreCase = true)
        }
        if (isDohBootstrap) {
            sendDnsBlockResponse(buffer, ihl, dnsOffset, ihl + udpLen, outputStream)
            return
        }

        val repo = SelfControlApp.repository
        val prefs = repo.preferences
        val isTempUnlocked = prefs.isTemporaryUnlockActiveFast()
        val isAdultBlocked = prefs.cachedAdultContentBlocked
        val isImageSearchBlocked = prefs.cachedImageVideoSearchBlocked
        val rules = repo.getEnabledRulesCached()

        // Fast O(1) in-memory decision cache to bypass rule evaluation on identical domains
        val cached = domainEvaluationCache[domain]
        val (isBlocked, evalCategory) = if (cached != null) {
            cached
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
            val res = Pair(eval.isBlocked, eval.category)
            domainEvaluationCache[domain] = res
            res
        }

        if (isBlocked) {
            totalBlockedRequests++
            repo.recordBlockAttempt(
                targetName = domain,
                ruleType = com.example.data.model.RuleType.DOMAIN,
                category = evalCategory
            )

            // CRITICAL: Do NOT show block screen for adult website block!
            // Adult websites must be completely unreachable via DNS (NXDOMAIN) without block screen.
            // Only trigger UI intervention if it's NOT an adult block (e.g. custom rule where user requested intervention)
            if (evalCategory != BlockCategory.ADULT) {
                if (!com.example.service.accessibility.SelfControlAccessibilityService.isRunning &&
                    !InterventionActivity.isShowing &&
                    !InterventionActivity.isInDismissCooldown()
                ) {
                    InterventionActivity.start(this, domain, evalCategory.displayName)
                }
            }

            // Send back NXDOMAIN DNS response (RCODE = 3)
            sendDnsBlockResponse(buffer, ihl, dnsOffset, ihl + udpLen, outputStream)
        } else {
            // Forward DNS query upstream to CleanBrowsing Adult Filter DNS (adult-filter-dns.cleanbrowsing.org)
            forwardDnsQuery(buffer, ihl, dnsOffset, dnsLength, outputStream, domain, isAdultBlocked)
        }
    }

    private fun forwardDnsQuery(
        buffer: ByteArray,
        ihl: Int,
        dnsOffset: Int,
        dnsLength: Int,
        outputStream: FileOutputStream,
        domain: String,
        isAdultBlocked: Boolean
    ) {
        serviceScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                val dnsPacket = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLength)
                val responseBuffer = ByteArray(2048)
                val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)

                socket = DatagramSocket().apply {
                    protect(this) // Crucial: bypasses the VPN tunnel to the physical network
                    soTimeout = 1500
                }

                // Query CleanBrowsing Adult Filter DNS (Primary/Secondary)
                var received = false
                var actualRespLength = 0
                for (upstream in upstreamDnsAddresses) {
                    try {
                        val outPacket = DatagramPacket(dnsPacket, dnsPacket.size, upstream, 53)
                        socket.send(outPacket)
                        socket.receive(inPacket)
                        received = true
                        actualRespLength = inPacket.length
                        break
                    } catch (_: Throwable) {
                        // Try next upstream server
                    }
                }

                // Fallback to CleanBrowsing DoH if UDP fails or times out
                if (!received) {
                    val dohBytes = queryCleanBrowsingDoh(dnsPacket)
                    if (dohBytes != null && dohBytes.size >= 12) {
                        System.arraycopy(dohBytes, 0, responseBuffer, 0, dohBytes.size)
                        actualRespLength = dohBytes.size
                        received = true
                    }
                }

                if (!received || actualRespLength < 12) return@launch

                // Dynamic CleanBrowsing Adult Filter Check:
                // CleanBrowsing blocks adult domains dynamically by returning RCODE = 3 (NXDOMAIN) or 0.0.0.0
                val flags = ((responseBuffer[2].toInt() and 0xFF) shl 8) or (responseBuffer[3].toInt() and 0xFF)
                val rcode = flags and 0x0F
                val isDnsBlocked = isAdultBlocked && (rcode == 3 || AdultBlockingEngine.isDnsBlockedResponse(responseBuffer, actualRespLength, domain))

                if (isDnsBlocked) {
                    AdultBlockingEngine.rememberAdultDomain(domain, "cleanbrowsing_adult_dns")
                    totalBlockedRequests++
                    domainEvaluationCache[domain] = Pair(true, BlockCategory.ADULT)
                    SelfControlApp.repository.recordBlockAttempt(
                        targetName = domain,
                        ruleType = com.example.data.model.RuleType.DOMAIN,
                        category = BlockCategory.ADULT
                    )
                    // CRITICAL: DO NOT SHOW BLOCK SCREEN (InterventionActivity)!
                    // Adult websites are completely unreachable via DNS blocking, exactly like adding CleanBrowsing DNS in Android DNS settings.
                    // Send back pristine NXDOMAIN response to TUN
                    synchronized(outputStream) {
                        sendDnsBlockResponse(buffer, ihl, dnsOffset, ihl + 8 + dnsLength, outputStream)
                    }
                    return@launch
                }

                // Forward valid DNS response from CleanBrowsing back to TUN interface
                val fullResponse = ByteArray(ihl + 8 + actualRespLength)

                // Copy IPv4 header
                System.arraycopy(buffer, 0, fullResponse, 0, ihl)
                // Swap IP addresses (source becomes destination, destination becomes source)
                for (i in 0 until 4) {
                    val temp = fullResponse[12 + i]
                    fullResponse[12 + i] = fullResponse[16 + i]
                    fullResponse[16 + i] = temp
                }
                // Update total length in IPv4 header
                val totalLen = ihl + 8 + actualRespLength
                fullResponse[2] = ((totalLen shr 8) and 0xFF).toByte()
                fullResponse[3] = (totalLen and 0xFF).toByte()

                // UDP header
                val srcPort0 = buffer[ihl]
                val srcPort1 = buffer[ihl + 1]
                fullResponse[ihl] = buffer[ihl + 2]     // Dest port becomes src
                fullResponse[ihl + 1] = buffer[ihl + 3]
                fullResponse[ihl + 2] = srcPort0        // Src port becomes dest
                fullResponse[ihl + 3] = srcPort1
                val udpLen = 8 + actualRespLength
                fullResponse[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte()
                fullResponse[ihl + 5] = (udpLen and 0xFF).toByte()
                fullResponse[ihl + 6] = 0 // Checksum optional in UDP/IPv4
                fullResponse[ihl + 7] = 0

                // Copy DNS payload
                System.arraycopy(responseBuffer, 0, fullResponse, ihl + 8, actualRespLength)

                // Recalculate IPv4 header checksum so Linux kernel network stack accepts the packet
                updateIpv4Checksum(fullResponse, ihl)

                synchronized(outputStream) {
                    outputStream.write(fullResponse, 0, fullResponse.size)
                }
            } catch (_: Exception) {
                // Ignore transient network errors
            } finally {
                try {
                    socket?.close()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun queryCleanBrowsingDoh(queryWire: ByteArray): ByteArray? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL(AdultBlockingEngine.CLEANBROWSING_ADULT_DOH_URL)
            conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
                connectTimeout = 1200
                readTimeout = 1200
            }
            conn.outputStream.use { it.write(queryWire) }
            if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes() }
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {}
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

            // Set DNS Response flags: QR = 1, RD = 1, RA = 1, RCODE = 3 (NXDOMAIN)
            response[dnsOffset + 2] = 0x81.toByte() // QR = 1, RD = 1
            response[dnsOffset + 3] = 0x83.toByte() // RA = 1, RCODE = 3 (NXDOMAIN)

            // Clear ANCOUNT, NSCOUNT, ARCOUNT so client does not expect answer records
            response[dnsOffset + 6] = 0
            response[dnsOffset + 7] = 0
            response[dnsOffset + 8] = 0
            response[dnsOffset + 9] = 0
            response[dnsOffset + 10] = 0
            response[dnsOffset + 11] = 0

            // UDP checksum set to 0 (optional for IPv4)
            response[ihl + 6] = 0
            response[ihl + 7] = 0

            // Recalculate IPv4 header checksum
            updateIpv4Checksum(response, ihl)

            synchronized(outputStream) {
                outputStream.write(response, 0, totalLength)
            }
        } catch (_: Exception) {
            // Ignore socket write errors
        }
    }

    private fun sendTcpRst(
        buffer: ByteArray,
        ihl: Int,
        outputStream: FileOutputStream
    ) {
        try {
            val rstPacket = ByteArray(ihl + 20)
            System.arraycopy(buffer, 0, rstPacket, 0, ihl)

            val totalLen = ihl + 20
            rstPacket[2] = ((totalLen shr 8) and 0xFF).toByte()
            rstPacket[3] = (totalLen and 0xFF).toByte()

            // Swap IP addresses
            for (i in 0 until 4) {
                val temp = rstPacket[12 + i]
                rstPacket[12 + i] = rstPacket[16 + i]
                rstPacket[16 + i] = temp
            }

            // Swap TCP ports
            rstPacket[ihl] = buffer[ihl + 2]
            rstPacket[ihl + 1] = buffer[ihl + 3]
            rstPacket[ihl + 2] = buffer[ihl]
            rstPacket[ihl + 3] = buffer[ihl + 1]

            val inSeq = ((buffer[ihl + 4].toLong() and 0xFF) shl 24) or
                    ((buffer[ihl + 5].toLong() and 0xFF) shl 16) or
                    ((buffer[ihl + 6].toLong() and 0xFF) shl 8) or
                    (buffer[ihl + 7].toLong() and 0xFF)

            val inAck = ((buffer[ihl + 8].toLong() and 0xFF) shl 24) or
                    ((buffer[ihl + 9].toLong() and 0xFF) shl 16) or
                    ((buffer[ihl + 10].toLong() and 0xFF) shl 8) or
                    (buffer[ihl + 11].toLong() and 0xFF)

            val inFlags = buffer[ihl + 13].toInt() and 0xFF
            val hasAck = (inFlags and 0x10) != 0

            if (hasAck) {
                rstPacket[ihl + 4] = ((inAck shr 24) and 0xFF).toByte()
                rstPacket[ihl + 5] = ((inAck shr 16) and 0xFF).toByte()
                rstPacket[ihl + 6] = ((inAck shr 8) and 0xFF).toByte()
                rstPacket[ihl + 7] = (inAck and 0xFF).toByte()
                rstPacket[ihl + 8] = 0
                rstPacket[ihl + 9] = 0
                rstPacket[ihl + 10] = 0
                rstPacket[ihl + 11] = 0
                rstPacket[ihl + 13] = 0x04 // RST
            } else {
                rstPacket[ihl + 4] = 0
                rstPacket[ihl + 5] = 0
                rstPacket[ihl + 6] = 0
                rstPacket[ihl + 7] = 0
                val ackOut = (inSeq + 1) and 0xFFFFFFFFL
                rstPacket[ihl + 8] = ((ackOut shr 24) and 0xFF).toByte()
                rstPacket[ihl + 9] = ((ackOut shr 16) and 0xFF).toByte()
                rstPacket[ihl + 10] = ((ackOut shr 8) and 0xFF).toByte()
                rstPacket[ihl + 11] = (ackOut and 0xFF).toByte()
                rstPacket[ihl + 13] = 0x14 // RST + ACK
            }

            rstPacket[ihl + 12] = 0x50.toByte() // Data offset: 5 (20 bytes)
            rstPacket[ihl + 14] = 0
            rstPacket[ihl + 15] = 0
            rstPacket[ihl + 16] = 0 // Checksum
            rstPacket[ihl + 17] = 0
            rstPacket[ihl + 18] = 0 // Urgent
            rstPacket[ihl + 19] = 0

            updateTcpChecksum(rstPacket, ihl)
            updateIpv4Checksum(rstPacket, ihl)

            synchronized(outputStream) {
                outputStream.write(rstPacket, 0, rstPacket.size)
            }
        } catch (_: Throwable) {}
    }

    private fun updateTcpChecksum(packet: ByteArray, ihl: Int) {
        try {
            var sum = 0
            // Pseudo header: Src IP (4) + Dst IP (4) + 0 (1) + Protocol 6 (1) + TCP Length 20 (2)
            sum += ((packet[12].toInt() and 0xFF) shl 8) or (packet[13].toInt() and 0xFF)
            sum += ((packet[14].toInt() and 0xFF) shl 8) or (packet[15].toInt() and 0xFF)
            sum += ((packet[16].toInt() and 0xFF) shl 8) or (packet[17].toInt() and 0xFF)
            sum += ((packet[18].toInt() and 0xFF) shl 8) or (packet[19].toInt() and 0xFF)
            sum += 6 // Protocol TCP
            sum += 20 // TCP Length

            // TCP header
            for (i in ihl until ihl + 20 step 2) {
                if (i != ihl + 16) { // Skip existing checksum bytes
                    sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                }
            }

            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            val checksum = sum.inv() and 0xFFFF
            packet[ihl + 16] = ((checksum shr 8) and 0xFF).toByte()
            packet[ihl + 17] = (checksum and 0xFF).toByte()
        } catch (_: Throwable) {}
    }

    /**
     * Recalculates the RFC 791 16-bit one's complement checksum for an IPv4 header.
     */
    private fun updateIpv4Checksum(packet: ByteArray, ihl: Int) {
        try {
            packet[10] = 0
            packet[11] = 0
            var sum = 0
            for (i in 0 until ihl step 2) {
                val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                sum += word
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            val checksum = sum.inv() and 0xFFFF
            packet[10] = ((checksum shr 8) and 0xFF).toByte()
            packet[11] = (checksum and 0xFF).toByte()
        } catch (_: Throwable) {}
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
                "Reclaim CleanBrowsing DNS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Filters adult websites using CleanBrowsing DNS"
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
        domainEvaluationCache.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }
}
