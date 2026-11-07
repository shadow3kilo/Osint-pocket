package com.osintpocket.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DohResponse(
    @SerialName("Status") val status: Int = 0,
    @SerialName("Answer") val answer: List<DohAnswer> = emptyList(),
)

@Serializable
data class DohAnswer(
    val name: String = "",
    val type: Int = 0,
    @SerialName("TTL") val ttl: Int = 0,
    val data: String = "",
)

@Serializable
data class CrtShEntry(
    @SerialName("name_value") val nameValue: String = "",
    @SerialName("issuer_name") val issuerName: String? = null,
    @SerialName("not_after") val notAfter: String? = null,
)

@Serializable
data class InternetDbHost(
    val ip: String = "",
    val ports: List<Int> = emptyList(),
    val hostnames: List<String> = emptyList(),
    val cpes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val vulns: List<String> = emptyList(),
)

@Serializable
data class HibpBreach(
    @SerialName("Name") val name: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Domain") val domain: String = "",
    @SerialName("BreachDate") val breachDate: String = "",
    @SerialName("PwnCount") val pwnCount: Long = 0,
    @SerialName("DataClasses") val dataClasses: List<String> = emptyList(),
    @SerialName("Description") val description: String = "",
    @SerialName("IsVerified") val isVerified: Boolean = true,
    @SerialName("IsSensitive") val isSensitive: Boolean = false,
    @SerialName("IsSpamList") val isSpamList: Boolean = false,
)

@Serializable
data class IpGeo(
    val status: String = "",
    val message: String? = null,
    val query: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val isp: String? = null,
    val org: String? = null,
    @SerialName("as") val asn: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val hosting: Boolean? = null,
    val proxy: Boolean? = null,
)
