package com.osintpocket.app.domain.security

import com.osintpocket.app.data.history.HistoryEntry
import com.osintpocket.app.data.history.HistoryExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZoneId

class SecurityLogicTest {

    @Test fun hashesKnownVectors() {
        val h = Hashing.hashStream(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", h.md5)
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", h.sha1)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.sha256)
        assertEquals(3L, h.bytes)
    }

    @Test fun hashesLargeStreamAndReportsProgress() {
        val data = ByteArray(9 * 1024 * 1024) { (it % 251).toByte() }
        var last = 0f
        val h = Hashing.hashStream(ByteArrayInputStream(data), data.size.toLong(), onProgress = { last = it })
        assertEquals(data.size.toLong(), h.bytes)
        assertEquals(1f, last)
        assertEquals(64, h.sha256.length)
    }

    @Test fun pwnedQueryUsesFiveCharPrefix() {
        val q = PwnedRange.queryFor("password")
        assertEquals("5BAA6", q.prefix)
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", q.suffix)
    }

    @Test fun pwnedCountParsesResponse() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:10434004\r\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:0"
        assertEquals(10434004, PwnedRange.countFor(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(0, PwnedRange.countFor(body, "FFFFF"))
        assertEquals(0, PwnedRange.countFor(body, "00D4F6E8FA6EECAD2A3AA415EEC418D38EC")) // relleno
    }

    @Test fun emailValidation() {
        assertTrue(EmailValidator.isValid("a.b+tag@example.co.uk"))
        assertEquals(false, EmailValidator.isValid("no-arroba.com"))
        assertEquals(false, EmailValidator.isValid("a@b"))
    }

    private val entries = listOf(
        HistoryEntry(id = 2, module = "Web", query = "https://x.es", summary = "Nota B", details = "línea \"1\"\n\tlínea 2 \\ fin\u0001", createdAt = 1_762_531_200_000),
        HistoryEntry(id = 1, module = "Alias", query = "pepe", summary = "2 encontrados", details = "", createdAt = 1_762_444_800_000),
    )

    @Test fun jsonEscapesEverything() {
        val json = HistoryExporter.toJson(entries, "0.1.0", now = 0)
        assertTrue(json.contains("\"details\": \"línea \\\"1\\\"\\n\\tlínea 2 \\\\ fin\\u0001\""))
        assertTrue(json.contains("\"exportedAt\": \"1970-01-01T00:00:00Z\""))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
    }

    @Test fun jsonEmptyList() {
        assertTrue(HistoryExporter.toJson(emptyList(), "0.1.0", now = 0).contains("\"entries\": []"))
    }

    @Test fun markdownHasHeadingsAndFences() {
        val md = HistoryExporter.toMarkdown(entries, ZoneId.of("UTC"))
        assertTrue(md.startsWith("# Historial de OSINT Pocket"))
        assertTrue(md.contains("## Web · https://x.es"))
        assertTrue(md.contains("*2025-11-07 16:00* — Nota B"))
        assertTrue(md.contains("```\nlínea \"1\""))
    }
}
