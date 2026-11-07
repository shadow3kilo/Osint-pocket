package com.osintpocket.app.data

import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.data.remote.CrtShApi
import com.osintpocket.app.data.remote.DohApi
import com.osintpocket.app.data.remote.InternetDbApi
import com.osintpocket.app.data.remote.InternetDbHost
import com.osintpocket.app.data.remote.IpApiService
import com.osintpocket.app.data.remote.IpGeo
import com.osintpocket.app.data.remote.RdapApi
import com.osintpocket.app.data.remote.RdapParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class DnsRecords(
    val records: Map<String, List<String>>, // tipo → valores (solo tipos con respuesta)
    val errors: Map<String, String>,        // tipo → error de esa consulta concreta
) {
    fun firstIp(): String? = records["A"]?.firstOrNull() ?: records["AAAA"]?.firstOrNull()
}

class NxDomainException : Exception("El dominio no existe (NXDOMAIN)")

@Singleton
class DomainRepository @Inject constructor(
    private val rdap: RdapApi,
    private val doh: DohApi,
    private val crt: CrtShApi,
    private val internetDb: InternetDbApi,
    private val ipApi: IpApiService,
) {

    /** RDAP necesita el dominio registrado: si 404, sube un nivel (a.b.example.com → b.example.com → example.com). */
    suspend fun rdapDomain(domain: String): List<KeyValue> {
        var candidate = domain
        while (true) {
            try {
                return RdapParser.summarize(rdap.domain(candidate))
            } catch (e: HttpException) {
                val parent = candidate.substringAfter('.', "")
                if (e.code() == 404 && parent.contains('.')) candidate = parent else throw e
            }
        }
    }

    suspend fun rdapIp(ip: String): List<KeyValue> = RdapParser.summarize(rdap.ip(ip))

    suspend fun dns(domain: String): DnsRecords = coroutineScope {
        val results = RECORD_TYPES.map { (type, code) ->
            async {
                type to safeCall {
                    val resp = doh.query(domain, type)
                    if (resp.status == RCODE_NXDOMAIN) throw NxDomainException()
                    resp.answer.filter { it.type == code }.map { it.data.trim().removeSurrounding("\"") }
                }
            }
        }.awaitAll()

        if (results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!

        DnsRecords(
            records = results.mapNotNull { (t, r) -> r.getOrNull()?.takeIf { it.isNotEmpty() }?.let { t to it } }.toMap(),
            errors = results.mapNotNull { (t, r) -> r.exceptionOrNull()?.let { t to it.toUserMessage() } }.toMap(),
        )
    }

    suspend fun subdomains(domain: String): List<String> {
        val base = domain.removePrefix("www.")
        return crt.search("%.$base").asSequence()
            .flatMap { it.nameValue.lineSequence() }
            .map { it.trim().lowercase().removePrefix("*.") }
            .filter { it == base || it.endsWith(".$base") }
            .distinct()
            .sorted()
            .toList()
    }

    /** null = InternetDB no tiene nada indexado para esa IP. */
    suspend fun exposure(ip: String): InternetDbHost? {
        val resp = internetDb.host(ip)
        return when {
            resp.isSuccessful -> resp.body()
            resp.code() == 404 -> null
            else -> throw HttpException(resp)
        }
    }

    suspend fun geo(ip: String): IpGeo =
        ipApi.lookup(ip).also {
            if (it.status != "success") {
                throw IllegalStateException(
                    when (it.message) {
                        "private range", "reserved range" -> "IP privada o reservada: sin geolocalización"
                        else -> "ip-api: ${it.message ?: "consulta fallida"}"
                    }
                )
            }
        }

    private companion object {
        val RECORD_TYPES = listOf("A" to 1, "AAAA" to 28, "CNAME" to 5, "MX" to 15, "NS" to 2, "TXT" to 16)
        const val RCODE_NXDOMAIN = 3
    }
}
