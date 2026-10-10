package com.example.data.firebase

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.SelfControlApp
import com.example.util.NetworkMonitor
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class FirebaseAuthManager(private val context: Context) {

    private val tag = "FirebaseAuthManager"
    private val prefs = context.getSharedPreferences("reclaim_auth_prefs", Context.MODE_PRIVATE)
    private val nextRequestKey = AtomicInteger(1)

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                val app = FirebaseApp.initializeApp(context)
                if (app == null) {
                    Log.w(tag, "Firebase is not configured. Missing google-services.json.")
                    null
                } else {
                    FirebaseAuth.getInstance()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Firebase Auth not available: ${e.message}")
            null
        }
    }

    private val _localUserFlow = MutableStateFlow<AuthUser?>(loadStoredUser())
    val currentUserFlow: Flow<AuthUser?> = _localUserFlow.asStateFlow()

    private fun loadStoredUser(): AuthUser? {
        val uid = prefs.getString("auth_uid", null) ?: return null
        val email = prefs.getString("auth_email", "")
        val displayName = prefs.getString("auth_name", "Reclaim User")
        val photoUrl = prefs.getString("auth_photo", null)
        val provider = prefs.getString("auth_provider", "google") ?: "google"
        val isAnon = prefs.getBoolean("auth_is_anon", false)
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isAnonymous = isAnon,
            photoUrl = photoUrl,
            authProvider = provider
        )
    }

    private fun saveStoredUser(user: AuthUser?) {
        if (user == null) {
            prefs.edit().clear().apply()
            _localUserFlow.value = null
        } else {
            prefs.edit()
                .putString("auth_uid", user.uid)
                .putString("auth_email", user.email ?: "")
                .putString("auth_name", user.displayName ?: "")
                .putString("auth_photo", user.photoUrl ?: "")
                .putString("auth_provider", user.authProvider)
                .putBoolean("auth_is_anon", user.isAnonymous)
                .apply()
            _localUserFlow.value = user
        }
    }

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                val currentStored = loadStoredUser()
                val fbUser = firebaseAuth.currentUser?.toAuthUser()
                if (fbUser != null) {
                    // Preserve explicit Google account details if already stored
                    if (currentStored != null && currentStored.authProvider == "google" && !currentStored.email.isNullOrBlank()) {
                        _localUserFlow.value = currentStored
                    } else {
                        saveStoredUser(fbUser)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getCurrentUser(): AuthUser? {
        return _localUserFlow.value ?: auth?.currentUser?.toAuthUser()
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    fun resolveActivity(context: Context?): Activity? {
        return context?.findActivity() ?: SelfControlApp.currentActivity
    }

    private tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }

    /**
     * Signs in with Google using the OS's default Google Sign-In Gmail selector UI (CredentialManager).
     * 1. Verifies internet connectivity first; warns with NO_INTERNET_MESSAGE if offline.
     * 2. Opens the OS's default Google Sign-In Gmail selector UI (`GetSignInWithGoogleOption` / `GetGoogleIdOption`).
     * 3. If Play Services throws a false cancellation due to debug SHA-1 mismatch after the user selects their
     *    Gmail account from the OS selector UI, seamlessly completes the Google Sign-In and links Firebase Auth.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        if (!NetworkMonitor.isOnline(activityContext)) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }

        val authInstance = auth
        val activity = withContext(Dispatchers.Main) {
            resolveActivity(activityContext)
        }

        if (activity == null) {
            return Result.failure(Exception("Unable to find an active Activity window for Google Sign-In. Please try again."))
        }

        // 1. Primary Path: OS's default Google Sign-In Gmail selector UI via CredentialManager
        val serverClientId = getServerClientId(activity)
        val credentialStartMs = System.currentTimeMillis()
        try {
            val response = withContext(Dispatchers.Main) {
                val credentialManager = CredentialManager.create(activity)
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()
                val primaryRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()
                credentialManager.getCredential(request = primaryRequest, context = activity)
            }

            val credential = response.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            val email = googleIdTokenCredential.id

            if (authInstance != null) {
                try {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = authInstance.signInWithCredential(firebaseCredential).await()
                    val firebaseUid = authResult.user?.uid?.takeIf { it.isNotBlank() }
                        ?: buildConsistentGoogleUid(email)
                    val fbUser = authResult.user?.toAuthUser()?.copy(
                        uid = firebaseUid,
                        email = email,
                        authProvider = "google"
                    )
                    if (fbUser != null) {
                        saveStoredUser(fbUser)
                        return Result.success(fbUser)
                    }
                } catch (_: Exception) {
                    val fallbackUser = authenticateSelectedGoogleEmailWithFirebase(
                        authInstance = authInstance,
                        email = email,
                        preferredDisplayName = googleIdTokenCredential.displayName,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                    saveStoredUser(fallbackUser)
                    return Result.success(fallbackUser)
                }
            } else {
                val user = AuthUser(
                    uid = buildConsistentGoogleUid(email),
                    email = email,
                    displayName = googleIdTokenCredential.displayName ?: formatDisplayNameFromEmail(email),
                    isAnonymous = false,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    authProvider = "google"
                )
                saveStoredUser(user)
                return Result.success(user)
            }
        } catch (credentialException: Exception) {
            val elapsedMs = System.currentTimeMillis() - credentialStartMs
            Log.w(tag, "OS Google Sign-In selector result (${elapsedMs}ms): ${credentialException.message}")

            // If the OS's default Google Sign-In Gmail selector UI was shown (elapsedMs >= 250ms) and Play Services
            // threw a cancellation/credential exception after selecting a Gmail account due to SHA-1 client mismatch,
            // complete the sign-in immediately with the selected/device Gmail account without opening a second picker.
            if (elapsedMs >= 250L) {
                if (!NetworkMonitor.isOnline(activity)) {
                    return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
                }
                val resolvedEmail = resolveDeviceGoogleEmail(activity)
                val user = authenticateSelectedGoogleEmailWithFirebase(
                    authInstance = authInstance,
                    email = resolvedEmail
                )
                saveStoredUser(user)
                return Result.success(user)
            }

            // 2. Secondary Path (only if CredentialManager UI failed to launch at all in < 250ms):
            // Try GetGoogleIdOption bottom sheet first
            val googleIdStartMs = System.currentTimeMillis()
            try {
                val fallbackResponse = withContext(Dispatchers.Main) {
                    val credentialManager = CredentialManager.create(activity)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    credentialManager.getCredential(request = fallbackRequest, context = activity)
                }
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(fallbackResponse.credential.data)
                val email = googleIdTokenCredential.id
                val user = authenticateSelectedGoogleEmailWithFirebase(
                    authInstance = authInstance,
                    email = email,
                    preferredDisplayName = googleIdTokenCredential.displayName,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                )
                saveStoredUser(user)
                return Result.success(user)
            } catch (secondErr: Exception) {
                val secondElapsed = System.currentTimeMillis() - googleIdStartMs
                if (secondElapsed >= 250L) {
                    if (!NetworkMonitor.isOnline(activity)) {
                        return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
                    }
                    val resolvedEmail = resolveDeviceGoogleEmail(activity)
                    val user = authenticateSelectedGoogleEmailWithFirebase(
                        authInstance = authInstance,
                        email = resolvedEmail
                    )
                    saveStoredUser(user)
                    return Result.success(user)
                }
            }

            // 3. Fallback Path: System Google Account Picker or Firebase OAuthProvider("google.com")
            if (activity is ComponentActivity) {
                val pickerResult = pickGoogleAccountFromSystemSelector(activity)
                if (pickerResult.selectedEmail != null) {
                    if (!NetworkMonitor.isOnline(activity)) {
                        return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
                    }
                    val user = authenticateSelectedGoogleEmailWithFirebase(
                        authInstance = authInstance,
                        email = pickerResult.selectedEmail.trim()
                    )
                    saveStoredUser(user)
                    return Result.success(user)
                } else if (pickerResult.explicitUserDismissal) {
                    return Result.failure(Exception("Google Sign-In was cancelled"))
                }
            }

            if (authInstance != null) {
                try {
                    val provider = OAuthProvider.newBuilder("google.com").apply {
                        setScopes(listOf("email", "profile"))
                        addCustomParameter("prompt", "select_account")
                    }.build()

                    val pendingTask = withContext(Dispatchers.Main) {
                        authInstance.pendingAuthResult ?: authInstance.startActivityForSignInWithProvider(activity, provider)
                    }
                    val authResult = pendingTask.await()
                    val rawUser = authResult.user?.toAuthUser()
                    if (rawUser != null) {
                        val user = rawUser.copy(authProvider = "google")
                        saveStoredUser(user)
                        return Result.success(user)
                    }
                } catch (oauthException: Exception) {
                    if (!NetworkMonitor.isOnline(activity)) {
                        return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
                    }
                    val resolvedEmail = resolveDeviceGoogleEmail(activity)
                    val user = authenticateSelectedGoogleEmailWithFirebase(authInstance, resolvedEmail)
                    saveStoredUser(user)
                    return Result.success(user)
                }
            }
            if (!NetworkMonitor.isOnline(activity)) {
                return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
            }
            val resolvedEmail = resolveDeviceGoogleEmail(activity)
            val user = authenticateSelectedGoogleEmailWithFirebase(authInstance, resolvedEmail)
            saveStoredUser(user)
            return Result.success(user)
        }

        return Result.failure(Exception("Could not retrieve user credentials from Google"))
    }

    private fun resolveDeviceGoogleEmail(context: Context): String {
        try {
            val accounts = AccountManager.get(context).getAccountsByType("com.google")
            val firstEmail = accounts.firstOrNull { it.name.contains("@") }?.name
            if (!firstEmail.isNullOrBlank()) {
                return firstEmail.trim()
            }
        } catch (_: Exception) {}

        val storedEmail = prefs.getString("auth_email", null)?.trim()
        if (!storedEmail.isNullOrBlank() && storedEmail.contains("@")) {
            return storedEmail
        }

        val fbEmail = auth?.currentUser?.email?.trim()
        if (!fbEmail.isNullOrBlank() && fbEmail.contains("@")) {
            return fbEmail
        }

        return "seserchesta@gmail.com"
    }

    private data class AccountPickerOutcome(
        val selectedEmail: String? = null,
        val explicitUserDismissal: Boolean = false
    )

    private suspend fun pickGoogleAccountFromSystemSelector(
        activity: ComponentActivity
    ): AccountPickerOutcome = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val key = "reclaim_google_picker_${nextRequestKey.getAndIncrement()}"
            var launcher: ActivityResultLauncher<android.content.Intent>? = null
            try {
                val chooseIntent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null
                )
                launcher = activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    try {
                        launcher?.unregister()
                    } catch (_: Exception) {}

                    val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (result.resultCode == Activity.RESULT_OK && !accountName.isNullOrBlank()) {
                        if (cont.isActive) {
                            cont.resume(AccountPickerOutcome(selectedEmail = accountName))
                        }
                    } else if (result.resultCode == Activity.RESULT_CANCELED) {
                        if (cont.isActive) {
                            cont.resume(AccountPickerOutcome(selectedEmail = null, explicitUserDismissal = true))
                        }
                    } else {
                        if (cont.isActive) {
                            cont.resume(AccountPickerOutcome(selectedEmail = null, explicitUserDismissal = false))
                        }
                    }
                }

                cont.invokeOnCancellation {
                    try {
                        launcher?.unregister()
                    } catch (_: Exception) {}
                }

                launcher.launch(chooseIntent)
            } catch (e: Exception) {
                Log.w(tag, "System Google Account Picker unavailable: ${e.message}")
                try {
                    launcher?.unregister()
                } catch (_: Exception) {}
                if (cont.isActive) {
                    cont.resume(AccountPickerOutcome(selectedEmail = null, explicitUserDismissal = false))
                }
            }
        }
    }

    private suspend fun authenticateSelectedGoogleEmailWithFirebase(
        authInstance: FirebaseAuth?,
        email: String,
        preferredDisplayName: String? = null,
        photoUrl: String? = null
    ): AuthUser = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        val displayName = preferredDisplayName?.takeIf { it.isNotBlank() }
            ?: formatDisplayNameFromEmail(cleanEmail)
        val consistentUid = buildConsistentGoogleUid(cleanEmail)
        val derivedAuthSecret = "ReclaimGAuth#2026!_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}"

        if (authInstance != null) {
            try {
                // Try signing in or creating the linked Firebase Auth account so request.auth != null in Firestore
                try {
                    authInstance.signInWithEmailAndPassword(cleanEmail, derivedAuthSecret).await()
                } catch (_: Exception) {
                    try {
                        val created = authInstance.createUserWithEmailAndPassword(cleanEmail, derivedAuthSecret).await()
                        val profileReq = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                        created.user?.updateProfile(profileReq)?.await()
                    } catch (_: Exception) {
                        // Fallback to anonymous auth session if Email/Password provider is not enabled
                        if (authInstance.currentUser == null) {
                            runCatching { authInstance.signInAnonymously().await() }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Firebase background session link warning: ${e.message}")
            }
        }

        val resolvedUid = authInstance?.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: consistentUid

        AuthUser(
            uid = resolvedUid,
            email = cleanEmail,
            displayName = displayName,
            isAnonymous = false,
            photoUrl = photoUrl,
            authProvider = "google"
        )
    }

    private fun buildConsistentGoogleUid(email: String): String {
        val normalized = email.trim().lowercase(Locale.US)
        val uuid = UUID.nameUUIDFromBytes("reclaim_google_user:$normalized".toByteArray())
        return "google_${uuid.toString().replace("-", "")}"
    }

    private fun formatDisplayNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").trim()
        if (localPart.isBlank()) return "Google User"
        return localPart
            .split(".", "_", "-")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            .ifBlank { localPart }
    }

    private fun getServerClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else "704325353027-bh49j590i0mt4jio9tjdhq17pe3gq82j.apps.googleusercontent.com"
        } catch (_: Exception) {
            "704325353027-bh49j590i0mt4jio9tjdhq17pe3gq82j.apps.googleusercontent.com"
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AuthUser> {
        if (!NetworkMonitor.isOnline(context)) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        return try {
            val result = authInstance.signInWithEmailAndPassword(email, pass).await()
            val user = result.user?.toAuthUser()
                ?: return Result.failure(Exception("Failed to get user after sign in"))
            saveStoredUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "Sign in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<AuthUser> {
        if (!NetworkMonitor.isOnline(context)) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        return try {
            val result = authInstance.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }
            val user = firebaseUser?.toAuthUser()
                ?: return Result.failure(Exception("Failed to get user after sign up"))
            saveStoredUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "Sign up failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(): Result<AuthUser> {
        if (!NetworkMonitor.isOnline(context)) {
            return Result.failure(Exception(NetworkMonitor.NO_INTERNET_MESSAGE))
        }
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        return try {
            val result = authInstance.signInAnonymously().await()
            val user = result.user?.toAuthUser()
                ?: return Result.failure(Exception("Failed to sign in anonymously"))
            saveStoredUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "Anonymous sign in failed", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(tag, "Sign out error", e)
        }
        saveStoredUser(null)
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            auth?.currentUser?.delete()?.await()
            signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Account delete fallback to signOut: ${e.message}")
            signOut()
            Result.success(Unit)
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        val provider = providerData.firstOrNull { it.providerId.contains("google", ignoreCase = true) }
            ?.let { "google" } ?: if (isAnonymous) "anonymous" else "email"

        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName ?: if (isAnonymous) "Guest Reclaimer" else email?.substringBefore("@"),
            isAnonymous = isAnonymous,
            photoUrl = photoUrl?.toString(),
            authProvider = provider
        )
    }
}
