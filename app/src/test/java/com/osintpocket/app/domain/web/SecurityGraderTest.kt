package com.osintpocket.app.domain.web

import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityGraderTest {

    private fun check(r: SecurityReport, title: String) = r.checks.first { it.title == title }

    @Test fun hardenedSiteGetsA() {
        val r = SecurityGrader.grade(
            isHttps = true,
            headers = headersOf(
                "Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload",
                "Content-Security-Policy", "default-src 'self'; script-src 'self' 'nonce-abc'; frame-ancestors 'none'",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "strict-origin-when-cross-origin",
                "Permissions-Policy", "camera=(), microphone=()",
                "Server", "nginx",
                "Set-Cookie", "sid=1; Secure; HttpOnly; SameSite=Lax",
            ),
        )
        assertEquals('A', r.letter)
        assertEquals(100, r.score)
        assertEquals(Grade.GOOD, check(r, "Anti-clickjacking").grade)
    }

    @Test fun bareSiteGetsF() {
        val r = SecurityGrader.grade(isHttps = true, headers = headersOf("Server", "Apache/2.4.41 (Ubuntu)"))
        assertEquals('F', r.letter)
        assertEquals(Grade.BAD, check(r, "HSTS").grade)
        assertEquals(Grade.WARN, check(r, "Divulgación").grade)
    }

    @Test fun httpIsCappedAt40() {
        val r = SecurityGrader.grade(
            isHttps = false,
            headers = headersOf(
                "Content-Security-Policy", "default-src 'self'",
                "X-Frame-Options", "DENY",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "no-referrer",
                "Permissions-Policy", "geolocation=()",
            ),
        )
        assertTrue(r.score <= 40)
        assertEquals(Grade.BAD, r.light)
    }

    @Test fun unsafeInlineWithoutNonceIsWeak() {
        val r = SecurityGrader.grade(true, headersOf("Content-Security-Policy", "script-src 'self' 'unsafe-inline'"))
        assertEquals(Grade.WARN, check(r, "CSP").grade)
    }

    @Test fun unsafeInlineWithNonceIsFine() {
        val r = SecurityGrader.grade(true, headersOf("Content-Security-Policy", "script-src 'nonce-x' 'unsafe-inline' 'strict-dynamic' https:"))
        assertEquals(Grade.GOOD, check(r, "CSP").grade)
    }

    @Test fun shortHstsIsWarn() {
        val r = SecurityGrader.grade(true, headersOf("Strict-Transport-Security", "max-age=86400"))
        assertEquals(Grade.WARN, check(r, "HSTS").grade)
    }

    @Test fun referrerUsesLastValidToken() {
        val r = SecurityGrader.grade(true, headersOf("Referrer-Policy", "unsafe-url, strict-origin-when-cross-origin"))
        assertEquals(Grade.GOOD, check(r, "Referrer-Policy").grade)
    }

    @Test fun cookieMissingFlagsIsWarn() {
        val r = SecurityGrader.grade(true, headersOf("Set-Cookie", "PHPSESSID=abc; path=/"))
        val c = check(r, "Cookies")
        assertEquals(Grade.WARN, c.grade)
        assertTrue(c.value!!.contains("Secure"))
    }

    @Test fun noCookiesMeansNotApplicable() {
        val r = SecurityGrader.grade(true, headersOf())
        assertTrue(r.checks.none { it.title == "Cookies" })
    }
}
