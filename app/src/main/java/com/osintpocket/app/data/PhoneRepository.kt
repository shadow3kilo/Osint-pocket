package com.osintpocket.app.data

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.osintpocket.app.core.ui.KeyValue
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class PhoneInfo(
    val valid: Boolean,
    val possible: Boolean,
    val e164: String?,
    val international: String?,
    val national: String?,
    val rfc3966: String?,
    val countryCode: Int,
    val regionCode: String?,
    val country: String?,
    val type: String,
    val carrier: String?,
    val location: String?,
    val note: String?,
) {
    fun asRows(): List<KeyValue> = buildList {
        add(KeyValue("Válido", if (valid) "Sí" else if (possible) "Formato posible, no asignado" else "No"))
        e164?.let { add(KeyValue("E.164", it)) }
        international?.let { add(KeyValue("Internacional", it)) }
        national?.let { add(KeyValue("Nacional", it)) }
        if (countryCode > 0) add(KeyValue("Prefijo país", "+$countryCode"))
        country?.let { add(KeyValue("País", it + (regionCode?.let { r -> " ($r)" } ?: ""))) }
        add(KeyValue("Tipo", type))
        carrier?.takeIf { it.isNotBlank() }?.let { add(KeyValue("Operador (asignación original)", it)) }
        location?.takeIf { it.isNotBlank() }?.let { add(KeyValue("Zona", it)) }
    }
}

/**
 * Información de un número con la librería offline de Google (sin red, sin API key).
 * El operador es el de asignación ORIGINAL del rango; la portabilidad no se refleja.
 */
@Singleton
class PhoneRepository @Inject constructor() {

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder: PhoneNumberOfflineGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val carrierMapper by lazy {
        com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper.getInstance()
    }

    /** defaultRegion (ISO-2, p. ej. "ES") se usa solo si el número no empieza por +. */
    fun lookup(raw: String, defaultRegion: String?): PhoneInfo {
        val spanish = Locale("es", "ES")
        val number = try {
            util.parse(raw.trim(), defaultRegion?.uppercase())
        } catch (e: NumberParseException) {
            return PhoneInfo(
                valid = false, possible = false, e164 = null, international = null, national = null,
                rfc3966 = null, countryCode = 0, regionCode = null, country = null,
                type = "—", carrier = null, location = null,
                note = parseError(e) + if (raw.trim().firstOrNull() != '+') " · incluye el prefijo internacional (+34…) o elige país." else "",
            )
        }

        val valid = util.isValidNumber(number)
        val region = util.getRegionCodeForNumber(number)
        val fmt = PhoneNumberUtil.PhoneNumberFormat
        return PhoneInfo(
            valid = valid,
            possible = util.isPossibleNumber(number),
            e164 = util.format(number, fmt.E164),
            international = util.format(number, fmt.INTERNATIONAL),
            national = util.format(number, fmt.NATIONAL),
            rfc3966 = util.format(number, fmt.RFC3966),
            countryCode = number.countryCode,
            regionCode = region,
            country = region?.let { Locale("", it).getDisplayCountry(spanish) },
            type = typeLabel(util.getNumberType(number)),
            carrier = carrierMapper.getNameForNumber(number, spanish),
            location = geocoder.getDescriptionForNumber(number, spanish),
            note = if (!valid) "El número no es válido para esa región." else null,
        )
    }

    private fun typeLabel(t: PhoneNumberType): String = when (t) {
        PhoneNumberType.MOBILE -> "Móvil"
        PhoneNumberType.FIXED_LINE -> "Fijo"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Fijo o móvil"
        PhoneNumberType.TOLL_FREE -> "Gratuito (900…)"
        PhoneNumberType.PREMIUM_RATE -> "Tarificación especial"
        PhoneNumberType.SHARED_COST -> "Coste compartido"
        PhoneNumberType.VOIP -> "VoIP"
        PhoneNumberType.PERSONAL_NUMBER -> "Número personal"
        PhoneNumberType.PAGER -> "Busca"
        PhoneNumberType.UAN -> "UAN"
        PhoneNumberType.VOICEMAIL -> "Buzón de voz"
        else -> "Desconocido"
    }

    private fun parseError(e: NumberParseException): String = when (e.errorType) {
        NumberParseException.ErrorType.INVALID_COUNTRY_CODE -> "Prefijo de país no reconocido."
        NumberParseException.ErrorType.NOT_A_NUMBER -> "No parece un número de teléfono."
        NumberParseException.ErrorType.TOO_SHORT_NSN,
        NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD -> "Demasiado corto."
        NumberParseException.ErrorType.TOO_LONG -> "Demasiado largo."
        else -> "No se pudo interpretar el número."
    }
}
