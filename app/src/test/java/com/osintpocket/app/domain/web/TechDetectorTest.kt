package com.osintpocket.app.domain.web

import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechDetectorTest {

    private fun names(t: List<Tech>) = t.map { it.name }.toSet()

    @Test fun headersAndCookies() {
        val t = TechDetector.detect(
            headersOf(
                "Server", "cloudflare",
                "CF-RAY", "8a1b2c3d4e-MAD",
                "X-Powered-By", "PHP/8.2.1",
                "Set-Cookie", "PHPSESSID=x; path=/",
            ),
            html = null,
        )
        val n = names(t)
        assertTrue("cloudflare" in n)
        assertTrue("PHP/8.2.1" in n)
        assertTrue("PHP" in n)
    }

    @Test fun wordpressFromHtml() {
        val html = """
            <html><head>
            <meta name="generator" content="WordPress 6.6.2" />
            <link rel="stylesheet" href="/wp-content/themes/x/style.css">
            <script src="/wp-includes/js/jquery/jquery.min.js"></script>
            </head></html>
        """.trimIndent()
        val n = names(TechDetector.detect(headersOf(), html))
        assertTrue("WordPress 6.6.2" in n)
        assertTrue("WordPress" in n)
        assertTrue("jQuery" in n)
    }

    @Test fun nextJs() {
        val html = """<script src="/_next/static/chunks/main.js"></script>"""
        assertTrue("Next.js" in names(TechDetector.detect(headersOf(), html)))
    }

    @Test fun deduplicatesCaseInsensitive() {
        val t = TechDetector.detect(headersOf("x-drupal-cache", "HIT", "x-drupal-dynamic-cache", "MISS"), null)
        assertEquals(1, t.count { it.name == "Drupal" })
    }
}
