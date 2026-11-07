package com.osintpocket.app.domain.profile

import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.data.GravatarResult
import com.osintpocket.app.data.LeakItem
import com.osintpocket.app.data.LeakProvider
import com.osintpocket.app.data.ProviderResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReportTest {

    private val gravatarHit = GravatarResult(
        hasAvatar = true, avatarUrl = "a", profileUrl = "p",
        fields = listOf(KeyValue("Nombre", "Pepe")), accounts = listOf(KeyValue("github", "https://github.com/pepe")),
    )
    private val gravatarMiss = GravatarResult(false, "a", "p", emptyList(), emptyList())
    private val xonHit = ProviderResult(LeakProvider.XON, true, "1 filtración",
        listOf(LeakItem("Adobe", listOf(KeyValue("Fecha", "2013")))))
    private val lcMiss = ProviderResult(LeakProvider.LEAKCHECK, false, "No aparece", emptyList())

    @Test fun countsHitsIncludingGravatar() {
        assertEquals(2, ProfileReport.hitCount(gravatarHit, listOf(xonHit, lcMiss)))
        assertEquals(0, ProfileReport.hitCount(gravatarMiss, listOf(lcMiss)))
    }

    @Test fun summaryWordsMatchHits() {
        assertTrue(ProfileReport.summary(gravatarHit, listOf(xonHit, lcMiss)).contains("2 de 3"))
        assertTrue(ProfileReport.summary(gravatarMiss, listOf(lcMiss)).contains("Sin rastro"))
    }

    @Test fun reportContainsAllSections() {
        val r = ProfileReport.build("pepe@x.com", gravatarHit, listOf(xonHit, lcMiss))
        assertTrue(r.contains("Ficha OSINT de pepe@x.com"))
        assertTrue(r.contains("== Gravatar =="))
        assertTrue(r.contains("Nombre: Pepe"))
        assertTrue(r.contains("== XposedOrNot =="))
        assertTrue(r.contains("Adobe (Fecha: 2013)"))
        assertTrue(r.contains("== LeakCheck =="))
    }

    @Test fun reportWithoutGravatarOrHibp() {
        val r = ProfileReport.build("x@y.com", null, listOf(xonHit))
        assertTrue(!r.contains("== Gravatar =="))
        assertTrue(!r.contains("Have I Been Pwned"))
    }
}
