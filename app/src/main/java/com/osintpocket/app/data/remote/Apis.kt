package com.osintpocket.app.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/** rdap.org redirige al servidor RDAP autoritativo (OkHttp sigue la redirección). */
interface RdapApi {
    @GET("domain/{domain}")
    suspend fun domain(@Path("domain") domain: String): JsonObject

    @GET("ip/{ip}")
    suspend fun ip(@Path("ip") ip: String): JsonObject
}

/** DNS-over-HTTPS de Cloudflare en formato JSON. */
interface DohApi {
    @Headers("Accept: application/dns-json")
    @GET("dns-query")
    suspend fun query(@Query("name") name: String, @Query("type") type: String): DohResponse
}

/** Certificate Transparency. q = "%.dominio" → Retrofit codifica '%' como %25. */
interface CrtShApi {
    @GET("/")
    suspend fun search(
        @Query("q") query: String,
        @Query("output") output: String = "json",
    ): List<CrtShEntry>
}

/** Shodan InternetDB: sin API key. 404 = IP sin datos indexados. */
interface InternetDbApi {
    @GET("{ip}")
    suspend fun host(@Path("ip") ip: String): Response<InternetDbHost>
}

/** Have I Been Pwned v3. Requiere API key (de pago). 404 = sin brechas. */
interface HibpApi {
    @GET("breachedaccount/{account}")
    suspend fun breaches(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String,
        @Query("truncateResponse") truncate: Boolean = false,
    ): Response<List<HibpBreach>>
}

/**
 * Catálogo de brechas de HIBP: GRATIS y sin API key. Licencia CC BY 4.0: hay que atribuir
 * a Have I Been Pwned con un enlace visible. Exige User-Agent (lo pone UserAgentInterceptor).
 */
interface HibpCatalogApi {
    @GET("breaches")
    suspend fun breaches(): List<HibpBreach>
}

/** Pwned Passwords (gratis, sin key). Add-Padding evita que el tamaño de la respuesta revele el prefijo. */
interface PwnedPasswordsApi {
    @Headers("Add-Padding: true")
    @GET("range/{prefix}")
    suspend fun range(@Path("prefix") prefix: String): ResponseBody
}

/** XposedOrNot (gratis, sin key): 2/s, 25/h, 100/día por IP. Uso personal y de bajo volumen. */
interface XonApi {
    @GET("v1/breach-analytics")
    suspend fun breachAnalytics(@Query("email") email: String): Response<JsonObject>
}

/** XposedOrNot contraseñas: prefijo de 10 hex del Keccak-512. */
interface XonPasswordApi {
    @GET("api/v1/pass/anon/{prefix}")
    suspend fun anon(@Path("prefix") prefix: String): Response<JsonObject>
}

/** LeakCheck API pública (gratis, sin key): nombres de fuentes y tipos de datos, nunca valores. */
interface LeakCheckApi {
    @GET("api/public")
    suspend fun check(@Query("check") query: String): Response<JsonObject>
}

/** Hudson Rock Cavalier OSINT (gratis, sin key, ~1 req/s): equipos infectados por infostealers. */
interface HudsonRockApi {
    @GET("api/json/v2/osint-tools/search-by-email")
    suspend fun byEmail(@Query("email") email: String): Response<JsonObject>
}

/** ip-api.com gratuito: solo HTTP, 45 peticiones/minuto. */
interface IpApiService {
    @GET("json/{query}")
    suspend fun lookup(
        @Path("query") query: String,
        @Query("fields") fields: String = "status,message,query,country,countryCode,regionName,city,isp,org,as,lat,lon,timezone,hosting,proxy",
    ): IpGeo
}
