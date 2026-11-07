package com.osintpocket.app.domain.exif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MetadataStripperTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    private fun jpegSegment(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return bytes(0xFF, marker, len shr 8, len and 0xFF) + payload
    }

    private val jfif = jpegSegment(0xE0, ascii("JFIF\u0000") + bytes(1, 1, 0, 0, 1, 0, 1, 0, 0))
    private val exif = jpegSegment(0xE1, ascii("Exif\u0000\u0000") + ascii("GPS-SECRET"))
    private val xmp = jpegSegment(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>"))
    private val icc = jpegSegment(0xE2, ascii("ICC_PROFILE\u0000") + bytes(1, 1, 9, 9))
    private val com = jpegSegment(0xFE, ascii("hecho por Pepe"))
    private val dqt = jpegSegment(0xDB, ByteArray(65) { it.toByte() })
    private val sos = jpegSegment(0xDA, bytes(1, 1, 0, 0, 63, 0))
    // Datos de escaneo con byte stuffing (FF00) y un marcador de reinicio (FFD0)
    private val scan = bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56)
    private val eoi = bytes(0xFF, 0xD9)

    private fun jpeg(vararg parts: ByteArray) =
        parts.fold(bytes(0xFF, 0xD8)) { acc, p -> acc + p }

    @Test fun detectsFormats() {
        assertEquals(ImageFormat.JPEG, MetadataStripper.detect(jpeg(eoi)))
        assertEquals(ImageFormat.PNG, MetadataStripper.detect(png()))
        assertEquals(ImageFormat.WEBP, MetadataStripper.detect(webp()))
        assertEquals(ImageFormat.HEIF, MetadataStripper.detect(bytes(0, 0, 0, 24) + ascii("ftypheic") + ByteArray(8)))
        assertNull(MetadataStripper.detect(ascii("GIF89a....")))
    }

    @Test fun jpegRemovesMetadataKeepsImageData() {
        val input = jpeg(jfif, exif, xmp, icc, com, dqt, sos, scan, eoi, ascii("TRAILER-SAMSUNG"))
        val r = MetadataStripper.strip(input)!!
        val expected = jpeg(jfif, icc, dqt, sos, scan, eoi)
        assertTrue("salida distinta de la esperada", expected.contentEquals(r.bytes))
        assertTrue(r.removed.contains("EXIF (APP1)"))
        assertTrue(r.removed.contains("XMP (APP1)"))
        assertTrue(r.removed.contains("Comentario (COM)"))
        assertTrue(r.removed.any { it.startsWith("Datos tras el fin de imagen") })
        assertEquals(listOf("Perfil de color ICC"), r.kept)
        assertTrue(!String(r.bytes, Charsets.ISO_8859_1).contains("GPS-SECRET"))
    }

    @Test fun jpegProgressiveMultipleScans() {
        val dht = jpegSegment(0xC4, ByteArray(20))
        val input = jpeg(exif, dqt, sos, scan, dht, sos, scan, eoi)
        val r = MetadataStripper.strip(input)!!
        assertTrue(jpeg(dqt, sos, scan, dht, sos, scan, eoi).contentEquals(r.bytes))
    }

    @Test fun cleanJpegIsUnchanged() {
        val input = jpeg(jfif, dqt, sos, scan, eoi)
        val r = MetadataStripper.strip(input)!!
        assertTrue(input.contentEquals(r.bytes))
        assertTrue(r.removed.isEmpty())
    }

    // PNG: firma + chunks (CRC ficticio; el stripper no lo recalcula porque copia los chunks intactos)
    private fun pngChunk(type: String, data: ByteArray) =
        bytes(0, 0, data.size shr 8, data.size and 0xFF) + ascii(type) + data + bytes(0, 0, 0, 0)

    private fun png(vararg extra: ByteArray): ByteArray {
        val sig = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdr = pngChunk("IHDR", ByteArray(13))
        val idat = pngChunk("IDAT", ascii("pixels"))
        val iend = pngChunk("IEND", ByteArray(0))
        return extra.fold(sig + ihdr) { acc, p -> acc + p } + idat + iend
    }

    @Test fun pngRemovesTextAndExif() {
        val text = pngChunk("tEXt", ascii("Author\u0000Pepe"))
        val ex = pngChunk("eXIf", ascii("MM*"))
        val srgb = pngChunk("sRGB", bytes(0))
        val input = png(text, ex, srgb) + ascii("trailer")
        val r = MetadataStripper.strip(input)!!
        assertTrue(png(srgb).contentEquals(r.bytes))
        assertTrue(r.removed.contains("EXIF (eXIf)"))
        assertTrue(r.removed.contains("Texto/XMP (tEXt)"))
        assertTrue(r.removed.any { it.startsWith("Datos tras el fin") })
    }

    // WebP extendido: VP8X con flags EXIF (0x08) y XMP (0x04) + chunks EXIF/XMP
    private fun webpChunk(fourcc: String, data: ByteArray): ByteArray {
        val n = data.size
        val pad = if (n % 2 == 1) bytes(0) else ByteArray(0)
        return ascii(fourcc) + bytes(n and 0xFF, (n shr 8) and 0xFF, 0, 0) + data + pad
    }

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { acc, c -> acc + c }
        val size = body.size + 4
        return ascii("RIFF") + bytes(size and 0xFF, (size shr 8) and 0xFF, 0, 0) + ascii("WEBP") + body
    }

    @Test fun webpRemovesExifXmpAndClearsFlags() {
        val vp8x = webpChunk("VP8X", bytes(0x0C, 0, 0, 0, 9, 0, 0, 9, 0, 0))
        val vp8 = webpChunk("VP8 ", ascii("frame"))
        val input = webp(vp8x, vp8, webpChunk("EXIF", ascii("secret")), webpChunk("XMP ", ascii("<x/>")))
        val r = MetadataStripper.strip(input)!!
        val expectedVp8x = webpChunk("VP8X", bytes(0x00, 0, 0, 0, 9, 0, 0, 9, 0, 0))
        assertTrue(webp(expectedVp8x, vp8).contentEquals(r.bytes))
        assertEquals(listOf("EXIF", "XMP"), r.removed)
    }

    @Test fun heifIsNotLossless() {
        assertNull(MetadataStripper.strip(bytes(0, 0, 0, 24) + ascii("ftypheic") + ByteArray(8)))
    }

    @Suppress("unused")
    private fun ByteArrayOutputStream.w(b: ByteArray) = write(b)
}
