package com.osintpocket.app.domain.exif

import java.io.ByteArrayOutputStream

enum class ImageFormat(val mime: String, val ext: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    HEIF("image/heif", "heic"),
}

/**
 * removed = bloques de metadatos eliminados (también sirve como inventario de lo que trae el archivo).
 * kept = bloques conservados por ser necesarios para mostrar bien la imagen (p. ej. perfil ICC).
 */
class StripResult(val bytes: ByteArray, val removed: List<String>, val kept: List<String>)

/**
 * Elimina metadatos SIN recomprimir: copia los segmentos/chunks de imagen tal cual y descarta
 * EXIF, XMP, IPTC, comentarios, miniaturas y datos añadidos tras el final de la imagen.
 * JVM puro, sin dependencias de Android.
 */
object MetadataStripper {

    fun detect(b: ByteArray): ImageFormat? = when {
        b.size >= 3 && u(b, 0) == 0xFF && u(b, 1) == 0xD8 && u(b, 2) == 0xFF -> ImageFormat.JPEG
        b.size >= 8 && b.startsWith(PNG_SIG, 0) -> ImageFormat.PNG
        b.size >= 12 && b.ascii(0, 4) == "RIFF" && b.ascii(8, 4) == "WEBP" -> ImageFormat.WEBP
        b.size >= 12 && b.ascii(4, 4) == "ftyp" &&
            b.ascii(8, 4) in setOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif") -> ImageFormat.HEIF
        else -> null
    }

    /** null si el formato no admite limpieza sin pérdida (HEIF u otros): hay que recodificar. */
    fun strip(b: ByteArray): StripResult? = when (detect(b)) {
        ImageFormat.JPEG -> stripJpeg(b)
        ImageFormat.PNG -> stripPng(b)
        ImageFormat.WEBP -> stripWebp(b)
        else -> null
    }

    // ───────────────────────── JPEG ─────────────────────────

    private fun stripJpeg(b: ByteArray): StripResult {
        val out = ByteArrayOutputStream(b.size)
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        out.write(b, 0, 2) // SOI
        var i = 2

        while (i < b.size) {
            if (u(b, i) != 0xFF) throw IllegalArgumentException("JPEG corrupto (offset $i)")
            while (i + 1 < b.size && u(b, i + 1) == 0xFF) i++ // bytes de relleno
            if (i + 1 >= b.size) break
            val marker = u(b, i + 1)

            when {
                marker == 0xD9 -> { // EOI
                    out.write(b, i, 2)
                    i += 2
                    if (i < b.size) removed += "Datos tras el fin de imagen (${b.size - i} bytes)"
                    return StripResult(out.toByteArray(), removed.distinct(), kept.distinct())
                }
                marker == 0x01 || marker in 0xD0..0xD7 -> { // marcadores sin longitud
                    out.write(b, i, 2)
                    i += 2
                }
                else -> {
                    if (i + 3 >= b.size) throw IllegalArgumentException("JPEG truncado")
                    val len = (u(b, i + 2) shl 8) or u(b, i + 3)
                    val end = i + 2 + len
                    if (len < 2 || end > b.size) throw IllegalArgumentException("Segmento JPEG truncado")

                    val (remove, label) = classifyJpeg(marker, b, i + 4, end)
                    if (remove) {
                        removed += label ?: "APP${marker - 0xE0}"
                    } else {
                        label?.let { kept += it }
                        out.write(b, i, end - i)
                    }
                    i = end

                    if (marker == 0xDA) { // SOS: copiar datos codificados hasta el siguiente marcador real
                        var j = i
                        while (j < b.size) {
                            if (u(b, j) == 0xFF && j + 1 < b.size) {
                                val next = u(b, j + 1)
                                if (next == 0x00 || next in 0xD0..0xD7) { j += 2; continue }
                                break
                            }
                            j++
                        }
                        out.write(b, i, j - i)
                        i = j
                    }
                }
            }
        }
        return StripResult(out.toByteArray(), removed.distinct(), kept.distinct())
    }

    /** (eliminar, etiqueta legible) */
    private fun classifyJpeg(m: Int, b: ByteArray, start: Int, end: Int): Pair<Boolean, String?> {
        fun starts(s: String) = end - start >= s.length && b.ascii(start, s.length) == s
        return when (m) {
            0xE0 -> when {
                starts("JFIF\u0000") -> false to null
                starts("JFXX\u0000") -> true to "Miniatura JFXX (APP0)"
                else -> true to "APP0"
            }
            0xE1 -> when {
                starts("Exif\u0000") -> true to "EXIF (APP1)"
                starts("http://ns.adobe.com/xap/1.0/\u0000") -> true to "XMP (APP1)"
                starts("http://ns.adobe.com/xmp/extension/\u0000") -> true to "XMP extendido (APP1)"
                else -> true to "APP1"
            }
            0xE2 -> when {
                starts("ICC_PROFILE\u0000") -> false to "Perfil de color ICC"
                starts("MPF\u0000") -> true to "Multi-imagen MPF (APP2)"
                else -> true to "APP2"
            }
            0xED -> true to "IPTC / Photoshop (APP13)"
            0xEE -> if (starts("Adobe")) false to null else true to "APP14"
            in 0xE3..0xEF -> true to "APP${m - 0xE0}"
            0xFE -> true to "Comentario (COM)"
            else -> false to null
        }
    }

    // ───────────────────────── PNG ─────────────────────────

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Auxiliares que afectan al render o a la animación (APNG): se conservan. */
    private val PNG_KEEP = setOf("tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs", "bKGD", "acTL", "fcTL", "fdAT")

    private fun stripPng(b: ByteArray): StripResult {
        val out = ByteArrayOutputStream(b.size)
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        out.write(b, 0, 8)
        var i = 8
        while (i + 8 <= b.size) {
            val len = be32(b, i)
            val type = b.ascii(i + 4, 4)
            val end = i + 12L + len
            if (len < 0 || end > b.size) throw IllegalArgumentException("Chunk PNG truncado ($type)")
            val critical = type[0].isUpperCase()
            if (critical || type in PNG_KEEP) {
                out.write(b, i, (end - i).toInt())
                if (type == "iCCP") kept += "Perfil de color ICC"
            } else {
                removed += when (type) {
                    "eXIf" -> "EXIF (eXIf)"
                    "tEXt", "zTXt", "iTXt" -> "Texto/XMP ($type)"
                    "tIME" -> "Fecha de modificación (tIME)"
                    else -> "Bloque $type"
                }
            }
            i = end.toInt()
            if (type == "IEND") {
                if (i < b.size) removed += "Datos tras el fin de imagen (${b.size - i} bytes)"
                break
            }
        }
        return StripResult(out.toByteArray(), removed.distinct(), kept.distinct())
    }

    // ───────────────────────── WebP ─────────────────────────

    private fun stripWebp(b: ByteArray): StripResult {
        val body = ByteArrayOutputStream(b.size)
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        var i = 12
        while (i + 8 <= b.size) {
            val fourcc = b.ascii(i, 4)
            val len = le32(b, i + 4)
            var end = i + 8L + len + (len and 1)
            if (len < 0) throw IllegalArgumentException("Chunk WebP inválido")
            if (end > b.size) {
                if (i + 8L + len == b.size.toLong()) end = b.size.toLong() // falta el byte de relleno final
                else throw IllegalArgumentException("Chunk WebP truncado ($fourcc)")
            }
            when (fourcc) {
                "EXIF" -> removed += "EXIF"
                "XMP " -> removed += "XMP"
                else -> {
                    val chunk = b.copyOfRange(i, end.toInt())
                    if (fourcc == "VP8X" && chunk.size > 8) {
                        chunk[8] = (chunk[8].toInt() and (0x08 or 0x04).inv()).toByte() // quitar flags EXIF y XMP
                    }
                    if (fourcc == "ICCP") kept += "Perfil de color ICC"
                    body.write(chunk)
                    if (chunk.size % 2 == 1) body.write(0)
                }
            }
            i = end.toInt()
        }
        val out = ByteArrayOutputStream(body.size() + 12)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(body.size() + 4))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        body.writeTo(out)
        return StripResult(out.toByteArray(), removed.distinct(), kept.distinct())
    }

    // ───────────────────────── utilidades ─────────────────────────

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    private fun be32(b: ByteArray, i: Int): Long =
        (u(b, i).toLong() shl 24) or (u(b, i + 1).toLong() shl 16) or (u(b, i + 2).toLong() shl 8) or u(b, i + 3).toLong()

    private fun le32(b: ByteArray, i: Int): Long =
        u(b, i).toLong() or (u(b, i + 1).toLong() shl 8) or (u(b, i + 2).toLong() shl 16) or (u(b, i + 3).toLong() shl 24)

    private fun le32Bytes(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun ByteArray.ascii(start: Int, len: Int): String =
        if (start + len > size) "" else String(this, start, len, Charsets.ISO_8859_1)

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
        size >= at + prefix.size && prefix.indices.all { this[at + it] == prefix[it] }
}
