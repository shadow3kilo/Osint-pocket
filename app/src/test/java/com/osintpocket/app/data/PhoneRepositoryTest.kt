package com.osintpocket.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Usa la librería real de Google; se ejecuta en la JVM de CI. */
class PhoneRepositoryTest {

    private val repo = PhoneRepository()

    @Test fun spanishMobileE164() {
        val r = repo.lookup("+34 612 345 678", null)
        assertTrue(r.valid)
        assertEquals("+34612345678", r.e164)
        assertEquals(34, r.countryCode)
        assertEquals("ES", r.regionCode)
        assertEquals("Móvil", r.type)
    }

    @Test fun nationalNumberWithDefaultRegion() {
        val r = repo.lookup("612 345 678", "ES")
        assertTrue(r.valid)
        assertEquals("+34612345678", r.e164)
    }

    @Test fun usTollFree() {
        val r = repo.lookup("+1 800 234 5678", null)
        assertEquals(1, r.countryCode)
        assertEquals("US", r.regionCode)
    }

    @Test fun invalidReturnsNoteNotCrash() {
        val r = repo.lookup("abc", "ES")
        assertFalse(r.valid)
        assertEquals("—", r.type)
        assertTrue(r.note != null)
    }

    @Test fun missingPlusWithoutRegionIsHandled() {
        val r = repo.lookup("612345678", null)
        assertFalse(r.valid)
        assertTrue(r.note!!.contains("prefijo", ignoreCase = true))
    }
}
