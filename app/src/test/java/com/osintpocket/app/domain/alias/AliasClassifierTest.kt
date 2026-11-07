package com.osintpocket.app.domain.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasClassifierTest {

    private val statusSite = Site(name = "S", url = "https://s.example/{}")
    private val textSite = Site(name = "T", url = "https://t.example/{}", detect = Detect.TEXT, errorText = "No such user.")
    private val urlSite = Site(name = "U", url = "https://{}.u.example", detect = Detect.URL, errorUrl = "u.example/typo")

    private fun st(site: Site, code: Int, url: String = "https://x", body: String? = null) =
        AliasClassifier.classify(site, code, url, body).first

    @Test fun status200IsFound() = assertEquals(AliasStatus.FOUND, st(statusSite, 200))
    @Test fun status404IsNotFound() = assertEquals(AliasStatus.NOT_FOUND, st(statusSite, 404))
    @Test fun status429IsUnknown() = assertEquals(AliasStatus.UNKNOWN, st(statusSite, 429))
    @Test fun status403IsUnknown() = assertEquals(AliasStatus.UNKNOWN, st(statusSite, 403))

    @Test fun customNotFoundCodes() {
        val bsky = statusSite.copy(notFoundStatus = listOf(400, 404))
        assertEquals(AliasStatus.NOT_FOUND, st(bsky, 400))
        val mojang = statusSite.copy(notFoundStatus = listOf(204, 404))
        assertEquals(AliasStatus.NOT_FOUND, st(mojang, 204))
    }

    @Test fun textMarkerMeansNotFound() =
        assertEquals(AliasStatus.NOT_FOUND, st(textSite, 200, body = "<p>No such user.</p>"))

    @Test fun textWithoutMarkerIsFound() =
        assertEquals(AliasStatus.FOUND, st(textSite, 200, body = "<p>user: pg</p>"))

    @Test fun urlRedirectMeansNotFound() =
        assertEquals(AliasStatus.NOT_FOUND, st(urlSite, 200, url = "https://u.example/typo/?subdomain=x"))

    @Test fun urlNoRedirectIsFound() =
        assertEquals(AliasStatus.FOUND, st(urlSite, 200, url = "https://pepe.u.example/"))

    @Test fun usernameValidation() {
        assertTrue(AliasClassifier.isValidUsername("john.doe_99-x"))
        assertEquals(false, AliasClassifier.isValidUsername("john doe"))
        assertEquals(false, AliasClassifier.isValidUsername("a/b"))
        assertEquals(false, AliasClassifier.isValidUsername(""))
    }

    @Test fun siteRegexFiltersUsernames() {
        val sub = statusSite.copy(regex = "^[A-Za-z0-9-]{1,63}$")
        assertTrue(AliasClassifier.acceptsUsername(sub, "pepe-1"))
        assertEquals(false, AliasClassifier.acceptsUsername(sub, "pepe.1"))
    }

    @Test fun validatorCatchesErrors() {
        val bad = SiteList(sites = listOf(
            Site(name = "A", url = "https://a/{}"),
            Site(name = "a", url = "ftp://b"),
            Site(name = "C", url = "https://c/{}", detect = Detect.TEXT),
            Site(name = "D", url = "https://d/{}", regex = "(["),
        ))
        val errors = SiteValidator.validate(bad)
        assertTrue(errors.any { it.contains("duplicado") })
        assertTrue(errors.any { it.contains("http://") })
        assertTrue(errors.any { it.contains("{}") })
        assertTrue(errors.any { it.contains("errorText") })
        assertTrue(errors.any { it.contains("regex") })
    }

    @Test fun validatorAcceptsGoodList() =
        assertEquals(emptyList<String>(), SiteValidator.validate(SiteList(sites = listOf(statusSite, textSite, urlSite))))
}
