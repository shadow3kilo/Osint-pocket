package com.osintpocket.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.osintpocket.app.domain.exif.ImageFormat
import com.osintpocket.app.domain.exif.MetadataStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ExifField(val label: String, val value: String)
data class ExifGroup(val title: String, val fields: List<ExifField>)

enum class Risk { HIGH, MEDIUM, LOW }
data class PrivacyFlag(val risk: Risk, val title: String, val detail: String)

data class GpsInfo(val lat: Double, val lon: Double, val altitude: Double?, val timestamp: String?)

data class ImageAnalysis(
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val format: ImageFormat?,
    val gps: GpsInfo?,
    val groups: List<ExifGroup>,
    val flags: List<PrivacyFlag>,
    /** Bloques de metadatos presentes en el archivo (EXIF, XMP, IPTC…). */
    val blocks: List<String>,
    val preview: Bitmap?,
)

/** Imagen cargada: los bytes se quedan fuera del estado de la UI. */
class LoadedImage(val bytes: ByteArray, val analysis: ImageAnalysis, val orientation: Int)

enum class CleanMode { LOSSLESS, REENCODED }

data class CleanResult(
    val file: File,
    val mime: String,
    val suggestedName: String,
    val mode: CleanMode,
    val removed: List<String>,
    val originalBytes: Long,
    val cleanBytes: Long,
    /** Campos sensibles que siguen presentes tras limpiar (debería estar vacío). */
    val sensitiveRemaining: List<String>,
    val note: String?,
)

@Singleton
class ExifRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(uri: Uri): LoadedImage = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readCapped(MAX_BYTES) }
            ?: throw IOException("No se pudo abrir la imagen")
        val (name, size) = queryNameAndSize(uri)
        val format = MetadataStripper.detect(bytes)
        val mime = context.contentResolver.getType(uri) ?: format?.mime ?: "image/*"

        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            ?: ExifInterface.ORIENTATION_UNDEFINED

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val rotated = orientation in setOf(5, 6, 7, 8)
        val w = if (rotated) bounds.outHeight else bounds.outWidth
        val h = if (rotated) bounds.outWidth else bounds.outHeight

        val blocks = runCatching { MetadataStripper.strip(bytes)?.removed }.getOrNull()
            ?: listOfNotNull("EXIF".takeIf { exif?.hasAttribute(ExifInterface.TAG_MAKE) == true })
        val gps = exif?.let(::gpsOf)
        val groups = exif?.let(::groupsOf).orEmpty()
        val flags = flagsOf(exif, gps, blocks)
        val preview = runCatching { decodeOriented(bytes, maxDim = PREVIEW_MAX_DIM) }.getOrNull()

        LoadedImage(
            bytes = bytes,
            orientation = orientation,
            analysis = ImageAnalysis(
                name = name ?: "imagen", mime = mime, sizeBytes = size ?: bytes.size.toLong(),
                width = w.coerceAtLeast(0), height = h.coerceAtLeast(0), format = format,
                gps = gps, groups = groups, flags = flags, blocks = blocks, preview = preview,
            ),
        )
    }

    suspend fun clean(img: LoadedImage): CleanResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CLEAN_DIR).apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val base = img.analysis.name.substringBeforeLast('.').ifBlank { "imagen" }.take(80)
        val stripped = runCatching { MetadataStripper.strip(img.bytes) }.getOrNull()

        val result = if (stripped != null) {
            val format = img.analysis.format ?: ImageFormat.JPEG
            val file = File(dir, "${base}_limpia.${format.ext}")
            file.writeBytes(stripped.bytes)
            // Sin EXIF la foto saldría girada: se reescribe SOLO la orientación.
            val needsOrientation = format == ImageFormat.JPEG &&
                img.orientation !in setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL)
            if (needsOrientation) {
                ExifInterface(file.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, img.orientation.toString())
                    saveAttributes()
                }
            }
            CleanResult(
                file = file, mime = format.mime, suggestedName = file.name, mode = CleanMode.LOSSLESS,
                removed = stripped.removed, originalBytes = img.bytes.size.toLong(), cleanBytes = file.length(),
                sensitiveRemaining = emptyList(),
                note = if (needsOrientation) "Se conserva únicamente la etiqueta de orientación para que no salga girada" else null,
            )
        } else {
            // HEIF y otros: recodificar a JPEG (la orientación queda aplicada en los píxeles).
            val bmp = decodeOriented(img.bytes, maxPixels = MAX_REENCODE_PIXELS)
            val out = ByteArrayOutputStream()
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw IOException("No se pudo recodificar la imagen")
            bmp.recycle()
            val file = File(dir, "${base}_limpia.jpg").apply { writeBytes(out.toByteArray()) }
            CleanResult(
                file = file, mime = "image/jpeg", suggestedName = file.name, mode = CleanMode.REENCODED,
                removed = img.analysis.blocks.ifEmpty { listOf("Todos los metadatos") },
                originalBytes = img.bytes.size.toLong(), cleanBytes = file.length(),
                sensitiveRemaining = emptyList(),
                note = "Formato sin limpieza directa: recodificado a JPEG calidad 95",
            )
        }
        result.copy(sensitiveRemaining = sensitiveTagsIn(result.file))
    }

    suspend fun saveTo(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("No se pudo escribir el archivo")
    }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    // ───────────────────────── lectura EXIF ─────────────────────────

    private fun gpsOf(exif: ExifInterface): GpsInfo? {
        val ll = exif.latLong ?: return null
        val alt = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
        val ts = listOfNotNull(exif.v(ExifInterface.TAG_GPS_DATESTAMP), exif.v(ExifInterface.TAG_GPS_TIMESTAMP))
            .joinToString(" ").ifBlank { null }
        return GpsInfo(ll[0], ll[1], alt, ts)
    }

    private fun groupsOf(e: ExifInterface): List<ExifGroup> {
        val camera = fields(
            "Fabricante" to e.v(ExifInterface.TAG_MAKE),
            "Modelo" to e.v(ExifInterface.TAG_MODEL),
            "Objetivo" to listOfNotNull(e.v(ExifInterface.TAG_LENS_MAKE), e.v(ExifInterface.TAG_LENS_MODEL)).joinToString(" ").ifBlank { null },
            "Nº serie cámara" to e.v(ExifInterface.TAG_BODY_SERIAL_NUMBER),
            "Nº serie objetivo" to e.v(ExifInterface.TAG_LENS_SERIAL_NUMBER),
            "Propietario" to e.v(ExifInterface.TAG_CAMERA_OWNER_NAME),
            "Autor" to e.v(ExifInterface.TAG_ARTIST),
            "Copyright" to e.v(ExifInterface.TAG_COPYRIGHT),
            "Software" to e.v(ExifInterface.TAG_SOFTWARE),
            "ID de imagen" to e.v(ExifInterface.TAG_IMAGE_UNIQUE_ID),
            "Descripción" to e.v(ExifInterface.TAG_IMAGE_DESCRIPTION),
            "Comentario" to e.v(ExifInterface.TAG_USER_COMMENT),
        )
        val capture = fields(
            "Fecha original" to listOfNotNull(e.v(ExifInterface.TAG_DATETIME_ORIGINAL), e.v(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)).joinToString(" ").ifBlank { null },
            "Modificada" to e.v(ExifInterface.TAG_DATETIME),
            "Exposición" to e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0).takeIf { it > 0 }?.let {
                if (it < 1) "1/${(1 / it).roundToInt()} s" else "${fmt(it)} s"
            },
            "Apertura" to e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0).takeIf { it > 0 }?.let { "f/${fmt(it)}" },
            "ISO" to e.v(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            "Focal" to e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0).takeIf { it > 0 }?.let { mm ->
                val eq = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                "${fmt(mm)} mm" + if (eq > 0) " (≈$eq mm eq.)" else ""
            },
            "Flash" to e.getAttributeInt(ExifInterface.TAG_FLASH, -1).takeIf { it >= 0 }?.let {
                if (it and 1 == 1) "Disparado" else "No disparado"
            },
        )
        val image = fields(
            "Orientación" to orientationLabel(e.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)),
            "Miniatura" to if (e.hasThumbnail()) "Incluida" else null,
        )
        return listOf(ExifGroup("Dispositivo y autoría", camera), ExifGroup("Captura", capture), ExifGroup("Imagen", image))
            .filter { it.fields.isNotEmpty() }
    }

    private fun flagsOf(e: ExifInterface?, gps: GpsInfo?, blocks: List<String>): List<PrivacyFlag> = buildList {
        gps?.let { add(PrivacyFlag(Risk.HIGH, "Ubicación GPS", "${fmtCoord(it.lat)}, ${fmtCoord(it.lon)}: revela dónde se hizo la foto")) }
        if (e == null) return@buildList
        listOfNotNull(e.v(ExifInterface.TAG_BODY_SERIAL_NUMBER), e.v(ExifInterface.TAG_LENS_SERIAL_NUMBER)).firstOrNull()?.let {
            add(PrivacyFlag(Risk.HIGH, "Número de serie", "Permite vincular todas las fotos hechas con el mismo equipo"))
        }
        listOfNotNull(e.v(ExifInterface.TAG_CAMERA_OWNER_NAME), e.v(ExifInterface.TAG_ARTIST)).firstOrNull()?.let {
            add(PrivacyFlag(Risk.HIGH, "Nombre de autor o propietario", it))
        }
        e.v(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { add(PrivacyFlag(Risk.MEDIUM, "Fecha y hora exactas", it)) }
        e.v(ExifInterface.TAG_IMAGE_UNIQUE_ID)?.let { add(PrivacyFlag(Risk.MEDIUM, "ID único de imagen", "Identifica esta foto concreta")) }
        if (e.hasThumbnail()) add(PrivacyFlag(Risk.MEDIUM, "Miniatura embebida", "Puede mostrar la foto antes de recortarla o editarla"))
        if (blocks.any { it.contains("XMP") || it.contains("IPTC") || it.contains("Texto") }) {
            add(PrivacyFlag(Risk.MEDIUM, "XMP / IPTC / texto", "Puede incluir historial de edición, autor o ubicación escrita"))
        }
        listOfNotNull(e.v(ExifInterface.TAG_MAKE), e.v(ExifInterface.TAG_MODEL)).takeIf { it.isNotEmpty() }?.let {
            add(PrivacyFlag(Risk.LOW, "Modelo del dispositivo", it.joinToString(" ")))
        }
        e.v(ExifInterface.TAG_SOFTWARE)?.let { add(PrivacyFlag(Risk.LOW, "Software", it)) }
    }

    /** Comprobación final sobre el archivo limpio. */
    private fun sensitiveTagsIn(file: File): List<String> {
        val e = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return emptyList()
        return buildList {
            if (e.latLong != null) add("GPS")
            SENSITIVE_TAGS.forEach { (tag, label) -> if (e.v(tag) != null) add(label) }
        }
    }

    // ───────────────────────── decodificación ─────────────────────────

    /** Decodifica aplicando la orientación EXIF, con reducción opcional por lado máximo o por megapíxeles. */
    private fun decodeOriented(bytes: ByteArray, maxDim: Int? = null, maxPixels: Long? = null): Bitmap {
        fun scaleFor(w: Int, h: Int): Double {
            var s = 1.0
            if (maxDim != null) s = minOf(s, maxDim.toDouble() / maxOf(w, h))
            if (maxPixels != null && w.toLong() * h > maxPixels) s = minOf(s, sqrt(maxPixels.toDouble() / (w.toLong() * h)))
            return s
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = scaleFor(info.size.width, info.size.height)
                if (s < 1.0) {
                    decoder.setTargetSize(
                        (info.size.width * s).toInt().coerceAtLeast(1),
                        (info.size.height * s).toInt().coerceAtLeast(1),
                    )
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val s = scaleFor(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (s < 1.0 && sample * 2 <= (1.0 / s)) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Formato de imagen no soportado en este dispositivo")

        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull() ?: return bmp
        val deg = exif.rotationDegrees
        if (deg == 0 && !exif.isFlipped) return bmp
        val m = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(deg.toFloat())
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    // ───────────────────────── utilidades ─────────────────────────

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (!c.moveToFirst()) return@use null to null
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
                    val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
                    n to s
                }
        }.getOrNull() ?: (null to null)

    private fun ExifInterface.v(tag: String): String? =
        getAttribute(tag)?.trim()?.trim('\u0000')?.takeIf { it.isNotEmpty() }

    private fun fields(vararg pairs: Pair<String, String?>): List<ExifField> =
        pairs.mapNotNull { (k, v) -> v?.let { ExifField(k, it) } }

    private fun fmt(d: Double) = String.format(Locale.ROOT, "%.1f", d).removeSuffix(".0")

    private fun fmtCoord(d: Double) = String.format(Locale.ROOT, "%.5f", d)

    private fun orientationLabel(o: Int): String? = when (o) {
        ExifInterface.ORIENTATION_NORMAL -> "Normal"
        ExifInterface.ORIENTATION_ROTATE_90 -> "Girada 90°"
        ExifInterface.ORIENTATION_ROTATE_180 -> "Girada 180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "Girada 270°"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> "Volteada"
        else -> null
    }

    private fun java.io.InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IOException("Imagen demasiado grande (máx. ${max / 1_048_576} MB)")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 60 * 1_048_576
        const val PREVIEW_MAX_DIM = 1280
        const val MAX_REENCODE_PIXELS = 24_000_000L
        const val CLEAN_DIR = "clean"

        val SENSITIVE_TAGS = listOf(
            ExifInterface.TAG_MAKE to "Fabricante",
            ExifInterface.TAG_MODEL to "Modelo",
            ExifInterface.TAG_DATETIME_ORIGINAL to "Fecha original",
            ExifInterface.TAG_BODY_SERIAL_NUMBER to "Nº serie",
            ExifInterface.TAG_CAMERA_OWNER_NAME to "Propietario",
            ExifInterface.TAG_ARTIST to "Autor",
            ExifInterface.TAG_SOFTWARE to "Software",
        )
    }
}
