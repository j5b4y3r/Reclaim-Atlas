package com.example.domain.engine

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class EmailDispatchResult(
    val success: Boolean,
    val deliveredViaSmtp: Boolean,
    val senderEmail: String,
    val statusNote: String
)

object PartnerEmailSender {

    private const val TAG = "PartnerEmailSender"
    const val DEFAULT_SENDER_EMAIL = "jubayerahmmedyunus@gmail.com"
    const val SERVER_SENDER_EMAIL = "habitsentryapp@gmail.com"
    const val API_ENDPOINT = "https://habit-sentry.vercel.app/api/send-email"
    const val API_SECRET_KEY = "habit_sentry_secure_app_key_2026"

    fun normalizeSenderEmail(rawEmail: String?): String {
        val fromConfig = rawEmail?.trim()?.removeSurrounding("\"") ?: ""
        return when {
            fromConfig.isBlank() || !fromConfig.contains("@") -> DEFAULT_SENDER_EMAIL
            fromConfig.equals("bayerahmmedyunus@gmail.com", ignoreCase = true) ||
                fromConfig.equals("ubayerahmmedyunus@gmail.com", ignoreCase = true) ->
                "jubayerahmmedyunus@gmail.com"
            else -> fromConfig
        }
    }

    fun getConfiguredSenderEmail(): String {
        return try {
            val email = BuildConfig.SMTP_EMAIL
            if (!email.isNullOrBlank() && email.contains("@")) {
                normalizeSenderEmail(email)
            } else {
                SERVER_SENDER_EMAIL
            }
        } catch (_: Exception) {
            SERVER_SENDER_EMAIL
        }
    }

    /**
     * Sends a 1-time 6-digit verification code directly to the Accountability Partner's email
     * via the Habit Sentry server API (https://habit-sentry.vercel.app/api/send-email).
     */
    suspend fun sendPartnerVerificationEmail(
        recipientPartnerEmail: String,
        partnerName: String,
        userDisplayName: String,
        actionTitle: String,
        reason: String,
        verificationCode: String,
        newPartnerEmailIfChanging: String? = null
    ): EmailDispatchResult = withContext(Dispatchers.IO) {
        val rawRecipient = recipientPartnerEmail.trim()
        val cleanRecipient = if (
            rawRecipient.equals("bayerahmmedyunus@gmail.com", ignoreCase = true) ||
            rawRecipient.equals("ubayerahmmedyunus@gmail.com", ignoreCase = true)
        ) {
            "jubayerahmmedyunus@gmail.com"
        } else {
            rawRecipient
        }

        val cleanCode = verificationCode.trim()

        if (cleanRecipient.isBlank() || !cleanRecipient.contains("@")) {
            return@withContext EmailDispatchResult(
                success = false,
                deliveredViaSmtp = false,
                senderEmail = SERVER_SENDER_EMAIL,
                statusNote = "Invalid partner email address ($cleanRecipient)."
            )
        }

        if (cleanCode.isBlank()) {
            return@withContext EmailDispatchResult(
                success = false,
                deliveredViaSmtp = false,
                senderEmail = SERVER_SENDER_EMAIL,
                statusNote = "Verification code is empty."
            )
        }

        val senderEmail = SERVER_SENDER_EMAIL

        try {
            val payload = JSONObject().apply {
                put("recipientPartnerEmail", cleanRecipient)
                put("partnerName", partnerName.trim().ifBlank { "Partner" })
                put("userDisplayName", userDisplayName.trim().ifBlank { "Your partner" })
                put("actionTitle", actionTitle.trim().ifBlank { "Accountability Request" })
                put("reason", reason.trim())
                put("verificationCode", cleanCode)
                if (!newPartnerEmailIfChanging.isNullOrBlank()) {
                    put("newPartnerEmailIfChanging", newPartnerEmailIfChanging.trim())
                } else {
                    put("newPartnerEmailIfChanging", JSONObject.NULL)
                }
            }

            val url = URL(API_ENDPOINT)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-api-secret", API_SECRET_KEY)
                useCaches = false
            }

            val payloadBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { os ->
                os.write(payloadBytes)
                os.flush()
            }

            val statusCode = connection.responseCode
            val responseBody = try {
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (_: Exception) {
                ""
            }

            Log.d(TAG, "Server response: HTTP $statusCode - $responseBody")

            if (statusCode in 200..299) {
                val jsonResponse = runCatching { JSONObject(responseBody) }.getOrNull()
                val message = jsonResponse?.optString("message") ?: "Email sent successfully"
                Log.i(TAG, "Email successfully dispatched via server: $message")
                EmailDispatchResult(
                    success = true,
                    deliveredViaSmtp = true,
                    senderEmail = senderEmail,
                    statusNote = "Verification email sent to $cleanRecipient via Habit Sentry server."
                )
            } else {
                val jsonResponse = runCatching { JSONObject(responseBody) }.getOrNull()
                val serverError = jsonResponse?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Server returned HTTP $statusCode"
                Log.e(TAG, "Server error sending email: $serverError (HTTP $statusCode)")
                EmailDispatchResult(
                    success = false,
                    deliveredViaSmtp = false,
                    senderEmail = senderEmail,
                    statusNote = "Server error ($statusCode): $serverError"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception communicating with email server: ${e.message}", e)
            EmailDispatchResult(
                success = false,
                deliveredViaSmtp = false,
                senderEmail = senderEmail,
                statusNote = "Could not connect to email server: ${e.localizedMessage ?: e.message ?: "Network error"}"
            )
        }
    }
}
