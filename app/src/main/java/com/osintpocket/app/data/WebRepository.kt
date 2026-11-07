package com.osintpocket.app.data

import com.osintpocket.app.core.network.await
import com.osintpocket.app.di.WebClient
import com.osintpocket.app.domain.web.SecurityGrader
import com.osintpocket.app.domain.web.SecurityReport
import com.osintpocket.app.domain.web.Tech
import com.osintpocket.app.domain.web.TechDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RedirectHop(val code: Int, val url: String)

data class TlsInfo(
    val protocol: String,
    val cipher: String,
    val subject: String?,
    val issuer: String?,
    val notAfter: String?,
    val daysLeft: Long?,
    val sans: List<String>,
)

data class WebScan(
    val requestedUrl: String,
    val finalUrl: String,
    val isHttps: Boolean,
    val code: Int,
    val message: String,
    val timeMs: Long,
    val redirects: List<RedirectHop>,
    val headers: Headers,
    val contentType: String?,
    val tls: TlsInfo?,
    /** true = presente y válido, false = no encontrado, null = no se pudo comprobar. */
    val securityTxt: Boolean?,
)

data class WebResult(val scan: WebScan, val security: SecurityReport, val tech: List<Tech>)

@Singleton
class WebRepository @Inject constructor(
    @WebClient private val client: OkHttpClient,
) {

    suspend fun scan(url: HttpUrl): WebResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .build()

        val start = System.nanoTime()
        val (scan, html) = client.newCall(request).await().use { resp ->
            val elapsed = (System.nanoTime() - start) / 1_000_000
            val contentType = resp.header("Content-Type")
            val html = if (contentType?.contains("html", ignoreCase = true) == true) {
                resp.peekBody(MAX_BODY_BYTES).string()
            } else null

            val redirects = generateSequence(resp.priorResponse) { it.priorResponse }
                .map { RedirectHop(it.code, it.request.url.toString()) }
                .toList()
                .asReversed()

            WebScan(
                requestedUrl = url.toString(),
                finalUrl = resp.request.url.toString(),
                isHttps = resp.request.url.isHttps,
                code = resp.code,
                message = resp.message,
                timeMs = elapsed,
                redirects = redirects,
                headers = resp.headers,
                contentType = contentType,
                tls = tlsInfo(resp),
                securityTxt = null,
            ) to html
        }

        val withSecTxt = scan.copy(securityTxt = checkSecurityTxt(scan.finalUrl.toHttpUrlOrNull()))
        WebResult(
            scan = withSecTxt,
            security = SecurityGrader.grade(scan.isHttps, scan.headers),
            tech = TechDetector.detect(scan.headers, html),
        )
    }

    private suspend fun checkSecurityTxt(base: HttpUrl?): Boolean? {
        base ?: return null
        val url = base.newBuilder().encodedPath("/.well-known/security.txt").query(null).fragment(null).build()
        return try {
            client.newCall(Request.Builder().url(url).get().build()).await().use { r ->
                r.isSuccessful &&
                    r.header("Content-Type")?.startsWith("text/plain", ignoreCase = true) == true &&
                    r.peekBody(8_192).string().contains("Contact:", ignoreCase = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun tlsInfo(resp: Response): TlsInfo? {
        val hs = resp.handshake ?: return null
        val cert = hs.peerCertificates.firstOrNull() as? X509Certificate
        val notAfter = cert?.notAfter?.toInstant()
        val sans = runCatching {
            cert?.subjectAlternativeNames.orEmpty()
                .filter { (it.getOrNull(0) as? Int) == SAN_DNS }
                .mapNotNull { it.getOrNull(1) as? String }
        }.getOrDefault(emptyList())

        return TlsInfo(
            protocol = hs.tlsVersion.javaName,
            cipher = hs.cipherSuite.javaName,
            subject = cert?.subjectX500Principal?.name?.let { rdn(it, "CN") ?: it },
            issuer = cert?.issuerX500Principal?.name?.let { rdn(it, "CN") ?: rdn(it, "O") ?: it },
            notAfter = notAfter?.toString()?.take(10),
            daysLeft = notAfter?.let { ChronoUnit.DAYS.between(Instant.now(), it) },
            sans = sans,
        )
    }

    private fun rdn(name: String, key: String): String? =
        Regex("(?:^|,)\\s*$key=([^,]+)").find(name)?.groupValues?.get(1)?.trim()

    companion object {
        private const val MAX_BODY_BYTES = 512L * 1024
        private const val SAN_DNS = 2

        /** Acepta "ejemplo.com", "ejemplo.com/ruta" o una URL completa. Por defecto HTTPS. */
        fun normalizeUrl(raw: String): HttpUrl? {
            val s = raw.trim()
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            val withScheme = if (s.contains("://")) s else "https://$s"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.host.contains('.') || it.host.contains(':') }
        }
    }
}
