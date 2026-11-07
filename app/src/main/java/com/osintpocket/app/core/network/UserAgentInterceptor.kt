package com.osintpocket.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** User-Agent identificable para que los servicios sepan qué cliente consulta. */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build()
        )
}
