package com.osintpocket.app.data

import com.osintpocket.app.core.network.await
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.di.WebClient
import com.osintpocket.app.domain.alias.AliasClassifier
import com.osintpocket.app.domain.alias.AliasStatus
import com.osintpocket.app.domain.alias.Detect
import com.osintpocket.app.domain.alias.Site
import com.osintpocket.app.domain.alias.SiteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Comprueba un alias en una lista de sitios, con concurrencia limitada y timeout por sitio. */
@Singleton
class UsernameChecker @Inject constructor(
    @WebClient private val client: OkHttpClient,
) {

    fun check(
        username: String,
        sites: List<Site>,
        concurrency: Int = 8,
        timeoutMs: Long = 12_000,
    ): Flow<SiteResult> = channelFlow {
        val semaphore = Semaphore(concurrency)
        sites.forEach { site ->
            launch { semaphore.withPermit { send(checkOne(username, site, timeoutMs)) } }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun checkOne(username: String, site: Site, timeoutMs: Long): SiteResult {
        val profileUrl = site.url.replace("{}", username)

        if (!AliasClassifier.acceptsUsername(site, username)) {
            return SiteResult(site, AliasStatus.INVALID, profileUrl, "Este sitio no admite ese formato de alias")
        }

        val request = runCatching {
            Request.Builder()
                .url((site.probe ?: site.url).replace("{}", username))
                .get()
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .build()
        }.getOrElse {
            return SiteResult(site, AliasStatus.UNKNOWN, profileUrl, "URL de sondeo no válida en la lista")
        }

        val start = System.nanoTime()
        return try {
            val outcome = withTimeoutOrNull(timeoutMs) {
                client.newCall(request).await().use { r ->
                    val body = if (site.detect == Detect.TEXT) r.peekBody(MAX_BODY_BYTES).string() else null
                    AliasClassifier.classify(site, r.code, r.request.url.toString(), body)
                }
            } ?: (AliasStatus.UNKNOWN to "Tiempo de espera agotado")
            SiteResult(site, outcome.first, profileUrl, outcome.second, (System.nanoTime() - start) / 1_000_000)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SiteResult(site, AliasStatus.UNKNOWN, profileUrl, e.toUserMessage())
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 512L * 1024
    }
}
