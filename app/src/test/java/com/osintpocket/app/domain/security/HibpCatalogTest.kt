package com.osintpocket.app.domain.security

import com.osintpocket.app.data.remote.HibpBreach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HibpCatalogTest {

    private val adobe = HibpBreach(name = "Adobe", title = "Adobe", domain = "adobe.com", breachDate = "2013-10-04",
        addedDate = "2013-12-04T00:00:00Z", pwnCount = 152_445_165, dataClasses = listOf("Email addresses", "Passwords", "Password hints"))
    private val linkedin = HibpBreach(name = "LinkedIn", title = "LinkedIn", domain = "linkedin.com", breachDate = "2012-05-05",
        addedDate = "2016-05-21T21:35:40Z", pwnCount = 164_611_595, dataClasses = listOf("Email addresses", "Passwords"))
    private val spam = HibpBreach(name = "Collection1", title = "Collection #1", domain = "", breachDate = "2019-01-07",
        addedDate = "2019-01-16T21:46:07Z", pwnCount = 772_904_991, dataClasses = listOf("Email addresses", "Passwords"))
    private val all = listOf(adobe, linkedin, spam)

    @Test fun sortsByAddedDateByDefault() =
        assertEquals(listOf("Collection1", "LinkedIn", "Adobe"), HibpCatalog.filter(all).map { it.name })

    @Test fun sortsBySize() =
        assertEquals(listOf("Collection1", "LinkedIn", "Adobe"), HibpCatalog.filter(all, sort = CatalogSort.SIZE).map { it.name })

    @Test fun searchesTitleAndDomain() {
        assertEquals(listOf("Adobe"), HibpCatalog.filter(all, "ADOBE.c").map { it.name })
        assertEquals(listOf("Collection1"), HibpCatalog.filter(all, "collection").map { it.name })
    }

    @Test fun filtersByDataClass() =
        assertEquals(listOf("Adobe"), HibpCatalog.filter(all, dataClass = "password hints").map { it.name })

    @Test fun normalizesDomains() {
        assertEquals("linkedin.com", HibpCatalog.normalizeDomain("https://www.LinkedIn.com/in/pepe?x=1"))
        assertEquals("adobe.com", HibpCatalog.normalizeDomain("user@adobe.com"))
        assertNull(HibpCatalog.normalizeDomain("no es dominio"))
        assertNull(HibpCatalog.normalizeDomain("localhost"))
    }

    @Test fun matchesSubdomainsButNotLookalikes() {
        assertEquals(listOf("Adobe"), HibpCatalog.forDomain(all, "accounts.adobe.com").map { it.name })
        assertTrue(HibpCatalog.forDomain(all, "notadobe.com").isEmpty())
        assertTrue(HibpCatalog.forDomain(all, "example.com").isEmpty())
    }

    @Test fun checksSeveralServices() {
        val r = HibpCatalog.forServices(all, "linkedin.com, ejemplo.org\nadobe.com linkedin.com")
        assertEquals(listOf("linkedin.com", "ejemplo.org", "adobe.com"), r.keys.toList())
        assertEquals(1, r.getValue("linkedin.com").size)
        assertTrue(r.getValue("ejemplo.org").isEmpty())
    }

    @Test fun statsAndLatest() {
        assertEquals("Collection1", HibpCatalog.latest(all)?.name)
        assertEquals(152_445_165L + 164_611_595L + 772_904_991L, HibpCatalog.totalAccounts(all))
        assertEquals("Email addresses" to 3, HibpCatalog.dataClasses(all).first())
    }
}
