package com.poyal.perilog.calendar

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitCalendar(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if(continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

class GoogleCalendarAuth(private val context: Context) {
    companion object {
        val scopes = listOf("https://www.googleapis.com/auth/calendar.calendarlist.readonly", "https://www.googleapis.com/auth/calendar.events")
    }
    fun request(account: String) = AuthorizationRequest.builder()
        .setAccount(Account(account, "com.google"))
        .setRequestedScopes(scopes.map { Scope(it) }).build()

    suspend fun authorize(account: String): AuthorizationResult = Identity.getAuthorizationClient(context)
        .authorize(request(account)).awaitCalendar()

    fun token(result: AuthorizationResult): String {
        if(result.hasResolution() || !result.grantedScopes.containsAll(scopes) || result.accessToken.isNullOrBlank())
            throw CalendarAccessException("구글 캘린더 권한을 모두 허용해 주세요.")
        return result.accessToken!!
    }

    suspend fun accessToken(account: String): String = try { token(authorize(account)) }
    catch(e: kotlinx.coroutines.CancellationException) { throw e }
    catch(e: CalendarAccessException) { throw e }
    catch(e: com.google.android.gms.common.api.ApiException) {
        if(e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR)
            throw CalendarRetryException("구글 인증 서버에 연결하지 못했어요. 자동으로 다시 시도해요.")
        throw CalendarAccessException("구글 계정 권한과 앱의 구글 연결 설정을 확인해 주세요.")
    }
    catch(_: Exception) { throw CalendarAccessException("구글 연결을 다시 확인해 주세요.") }

    suspend fun clear(token: String) {
        Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitCalendar()
    }
}
