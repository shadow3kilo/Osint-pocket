package com.osintpocket.app.domain.dorks

import com.osintpocket.app.domain.security.Gravatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DorksTest {

    @Test fun domainSubdomainDork() {
        val d = Dorks.generate(DorkTarget.DOMAIN, "www.ejemplo.com")
        assertTrue(d.any { it.query == "site:ejemplo.com -www" })
        assertTrue(d.any { it.query.contains("intitle:\"index of\"") })
    }

    @Test fun emptyValueGivesNothing() = assertEquals(emptyList<Dork>(), Dorks.generate(DorkTarget.EMAIL, "  "))

    @Test fun valuesAreQuotedAndSanitized() {
        val d = Dorks.generate(DorkTarget.NAME, "Juan \"el\" Pérez")
        assertTrue(d.first().query.startsWith("\"Juan el Pérez\""))
    }

    @Test fun engineUrlEncodes() {
        val url = SearchEngine.GOOGLE.url("site:ejemplo.com \"a b\"")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("%22") && url.contains("%3A"))
        assertTrue(!url.contains(" "))
    }

    @Test fun gravatarKnownHash() {
        // MD5 de "myemailaddress@example.com" — vector oficial de la documentación de Gravatar.
        assertEquals("0bc83cb571cd1c50ba6f3e8a78ef1346", Gravatar.hash("MyEmailAddress@example.com "))
        assertTrue(Gravatar.avatarUrl("a@b.com").contains("d=404"))
    }
}
