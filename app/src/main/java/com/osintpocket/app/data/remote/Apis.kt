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

/** Pwned Passwords (gratis, sin key). Add-Padding evita que el tamaño de la respuesta revele el prefijo. */
interface PwnedPasswordsApi {
    @Headers("Add-Padding: true")
    @GET("range/{prefix}")
    suspend fun range(@Path("prefix") prefix: String): ResponseBody
}

/** ip-api.com gratuito: solo HTTP, 45 peticiones/minuto. */
interface IpApiService {
    @GET("json/{query}")
    suspend fun lookup(
        @Path("query") query: String,
        @Query("fields") fields: String = "status,message,query,country,countryCode,regionName,city,isp,org,as,lat,lon,timezone,hosting,proxy",
    ): IpGeo
}
