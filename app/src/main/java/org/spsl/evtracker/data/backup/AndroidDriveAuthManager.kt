package org.spsl.evtracker.data.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthManager.AuthResult

@Singleton
class AndroidDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveAuthManager {

    private val client = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest by lazy {
        AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
    }

    override suspend fun authorize(): AuthResult = await()

    override suspend fun silentToken(): AuthResult = when (val r = await()) {
        is AuthResult.NeedsResolution -> AuthResult.Failed("consent required")
        else -> r
    }

    private suspend fun await(): AuthResult = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { result: AuthorizationResult ->
                val pending = result.pendingIntent
                if (pending != null) {
                    cont.resume(AuthResult.NeedsResolution(pending.intentSender))
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrEmpty()) cont.resume(AuthResult.Success(token))
                    else cont.resume(AuthResult.Failed("no token returned"))
                }
            }
            .addOnFailureListener { t ->
                cont.resume(AuthResult.Failed(t.message ?: "authorize failed", t))
            }
            .addOnCanceledListener {
                cont.resume(AuthResult.Failed("cancelled"))
            }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
