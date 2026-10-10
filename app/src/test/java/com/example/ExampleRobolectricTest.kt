package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.PartnerUnlockRequest
import com.example.data.local.PreferencesManager
import com.example.domain.engine.PartnerEmailSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Reclaim", appName)
  }

  @Test
  fun `partner unlock request serialization preserves actionKey payload and deliveredViaSmtp`() {
    val original = PartnerUnlockRequest(
      requestId = "req-123",
      actionKey = "ACC_CHANGE_PARTNER_EMAIL",
      userId = "user-1",
      targetType = "CHANGE_PARTNER_EMAIL",
      targetDetail = "Change Accountability Partner Email to newpartner@example.com",
      ruleId = null,
      payload = "newpartner@example.com",
      reason = "Partner switched email accounts",
      status = "PENDING",
      verificationCode = "482910",
      partnerEmail = "currentpartner@example.com",
      senderEmail = PartnerEmailSender.getConfiguredSenderEmail(),
      deliveredViaSmtp = true,
      requestedAt = 1700000000000L
    )

    val serialized = original.toStorageString()
    val restored = PartnerUnlockRequest.fromStorageString(serialized)

    assertNotNull(restored)
    assertEquals("req-123", restored?.requestId)
    assertEquals("ACC_CHANGE_PARTNER_EMAIL", restored?.actionKey)
    assertEquals("CHANGE_PARTNER_EMAIL", restored?.targetType)
    assertEquals("newpartner@example.com", restored?.payload)
    assertEquals("482910", restored?.verificationCode)
    assertTrue(restored?.deliveredViaSmtp == true)
  }

  @Test
  fun `undelivered smtp requests are ignored and purged so UI never falsely shows email already sent`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    prefs.clearAllPartnerUnlockRequests()

    // Simulate a failed/unconfigured SMTP request (deliveredViaSmtp = false)
    val undelivered = PartnerUnlockRequest(
      requestId = "req-undelivered",
      actionKey = "FILTER_YOUTUBE_SHORTS",
      targetType = "SINGLE_ACTION",
      targetDetail = "Turn Off 'Block YouTube Shorts'",
      verificationCode = "111222",
      partnerEmail = "partner@example.com",
      deliveredViaSmtp = false,
      status = "PENDING"
    )
    prefs.savePartnerUnlockRequest(undelivered)

    // Should NOT appear in activePartnerRequests
    val activeAfterUndelivered = prefs.activePartnerRequests.first()
    assertFalse(activeAfterUndelivered.containsKey("FILTER_YOUTUBE_SHORTS"))

    // Simulate a real SMTP-delivered request (deliveredViaSmtp = true)
    val delivered = undelivered.copy(
      requestId = "req-delivered",
      deliveredViaSmtp = true
    )
    prefs.savePartnerUnlockRequest(delivered)

    val activeAfterDelivered = prefs.activePartnerRequests.first()
    assertTrue(activeAfterDelivered.containsKey("FILTER_YOUTUBE_SHORTS"))
    assertEquals("111222", activeAfterDelivered["FILTER_YOUTUBE_SHORTS"]?.verificationCode)
  }

  @Test
  fun `sender email normalizes missing j or ju prefix to jubayerahmmedyunus`() {
    assertEquals(
      "jubayerahmmedyunus@gmail.com",
      PartnerEmailSender.normalizeSenderEmail("bayerahmmedyunus@gmail.com")
    )
    assertEquals(
      "jubayerahmmedyunus@gmail.com",
      PartnerEmailSender.normalizeSenderEmail("ubayerahmmedyunus@gmail.com")
    )
    assertEquals(
      "jubayerahmmedyunus@gmail.com",
      PartnerEmailSender.getConfiguredSenderEmail()
    )
  }

  @Test
  fun `main activity launches cleanly`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
  }
}

