package com.rentmate.app.network

import com.rentmate.app.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches the stored access token to every request; auth endpoints ignore it server-side. */
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.currentAccessTokenBlocking()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
