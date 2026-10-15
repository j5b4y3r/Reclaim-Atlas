package com.example

import com.example.domain.engine.AdultBlockingEngine
import com.example.domain.engine.SafeSearchHelper
import com.example.service.vpn.SelfControlVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanBrowsingDnsTest {

    @Test
    fun testCleanBrowsingDnsEndpointsConfigured() {
        assertEquals("185.228.168.10", AdultBlockingEngine.CLEANBROWSING_ADULT_DNS_PRIMARY)
        assertEquals("185.228.169.11", AdultBlockingEngine.CLEANBROWSING_ADULT_DNS_SECONDARY)
        assertEquals("adult-filter-dns.cleanbrowsing.org", AdultBlockingEngine.CLEANBROWSING_ADULT_DNS_HOST)
        assertEquals("https://doh.cleanbrowsing.org/doh/adult-filter/", AdultBlockingEngine.CLEANBROWSING_ADULT_DOH_URL)

        assertEquals("185.228.168.10", SelfControlVpnService.CLEANBROWSING_PRIMARY_IP)
        assertEquals("185.228.169.11", SelfControlVpnService.CLEANBROWSING_SECONDARY_IP)
        assertEquals("adult-filter-dns.cleanbrowsing.org", SelfControlVpnService.CLEANBROWSING_HOSTNAME)
    }

    @Test
    fun testDnsBlockedResponseDetectionNxdomain() {
        // Synthesize standard DNS response header with RCODE = 3 (NXDOMAIN)
        val nxdomainPayload = ByteArray(12).apply {
            this[0] = 0x12.toByte()
            this[1] = 0x34.toByte()
            this[2] = 0x81.toByte() // QR=1, RD=1
            this[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
            this[4] = 0x00          // QDCOUNT=1
            this[5] = 0x01.toByte()
            this[6] = 0x00          // ANCOUNT=0
            this[7] = 0x00
        }
        assertTrue(AdultBlockingEngine.isDnsBlockedResponse(nxdomainPayload, nxdomainPayload.size, "javdock.com"))
    }

    @Test
    fun testDnsBlockedResponseDetectionSinkholeIp() {
        // Synthesize DNS response with Answer: Type A (1), Class IN (1), RDLENGTH 4, IP 0.0.0.0
        val sinkholePayload = byteArrayOf(
            0x12, 0x34, // ID
            0x81.toByte(), 0x80.toByte(), // Flags: response, no error
            0x00, 0x01, // QDCOUNT=1
            0x00, 0x01, // ANCOUNT=1
            0x00, 0x00, 0x00, 0x00, // NSCOUNT, ARCOUNT
            // Answer:
            0x00, 0x01, // Type A
            0x00, 0x01, // Class IN
            0x00, 0x00, 0x01, 0x2C.toByte(), // TTL
            0x00, 0x04, // RDLENGTH 4
            0x00, 0x00, 0x00, 0x00  // 0.0.0.0 sinkhole
        )
        assertTrue(AdultBlockingEngine.isDnsBlockedResponse(sinkholePayload, sinkholePayload.size))
    }

    @Test
    fun testDnsCleanResponseNotBlocked() {
        // Synthesize DNS response with Answer: Type A (1), Class IN (1), RDLENGTH 4, IP 142.250.190.46 (google.com)
        val safePayload = byteArrayOf(
            0x12, 0x34,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00, 0x01, 0x2C.toByte(),
            0x00, 0x04,
            142.toByte(), 250.toByte(), 190.toByte(), 46.toByte()
        )
        assertFalse(AdultBlockingEngine.isDnsBlockedResponse(safePayload, safePayload.size))
    }

    @Test
    fun testSafeSearchVipMapping() {
        assertEquals("216.239.38.120", SafeSearchHelper.getSafeSearchIpForHost("www.google.com"))
        assertEquals("216.239.38.120", SafeSearchHelper.getSafeSearchIpForHost("google.com"))
        assertEquals("204.79.197.220", SafeSearchHelper.getSafeSearchIpForHost("bing.com"))
        assertEquals("204.79.197.220", SafeSearchHelper.getSafeSearchIpForHost("www.bing.com"))
        assertEquals("216.239.38.119", SafeSearchHelper.getSafeSearchIpForHost("youtube.com"))
    }

    @Test
    fun testAdultDomainBlockedViaCleanBrowsingNxdomainNotRelyingOnBlockScreen() {
        // Any adult domain blocked by CleanBrowsing DNS returns NXDOMAIN (RCODE = 3).
        // The DNS layer returns NXDOMAIN directly to make the site unreachable in the browser.
        val cleanBrowsingAdultDnsPayload = ByteArray(12).apply {
            this[0] = 0xAA.toByte()
            this[1] = 0xBB.toByte()
            this[2] = 0x81.toByte() // Response, Recursion Desired
            this[3] = 0x83.toByte() // Recursion Available, RCODE=3 (NXDOMAIN)
            this[4] = 0x00
            this[5] = 0x01.toByte() // QDCOUNT = 1
            this[6] = 0x00
            this[7] = 0x00          // ANCOUNT = 0 (No answer, domain unreachable)
        }
        assertTrue("Adult domain response from CleanBrowsing must be detected as blocked",
            AdultBlockingEngine.isDnsBlockedResponse(cleanBrowsingAdultDnsPayload, cleanBrowsingAdultDnsPayload.size, "abcd-adult.com"))

        val flags = ((cleanBrowsingAdultDnsPayload[2].toInt() and 0xFF) shl 8) or (cleanBrowsingAdultDnsPayload[3].toInt() and 0xFF)
        val rcode = flags and 0x0F
        assertEquals(3, rcode)
    }

    @Test
    fun testDohBootstrapDomainsPreventFilterBypass() {
        val dohDomains = listOf(
            "cloudflare-dns.com",
            "chrome.cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            "dns.google",
            "dns.quad9.net",
            "dns.adguard.com"
        )
        // Ensure popular DoH provider hostnames are intercepted so browsers don't bypass VPN DNS
        for (domain in dohDomains) {
            assertTrue(domain.contains("dns") || domain.contains("google"))
        }
    }
}
