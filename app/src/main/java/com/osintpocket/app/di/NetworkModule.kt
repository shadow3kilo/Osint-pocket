package com.osintpocket.app.di

import com.osintpocket.app.BuildConfig
import com.osintpocket.app.core.network.HostRateLimitInterceptor
import com.osintpocket.app.core.AppPrefs
import com.osintpocket.app.core.network.RetryInterceptor
import com.osintpocket.app.core.network.TorProxySelector
import com.osintpocket.app.core.network.UserAgentInterceptor
import com.osintpocket.app.data.remote.CrtShApi
import com.osintpocket.app.data.remote.DohApi
import com.osintpocket.app.data.remote.HibpApi
import com.osintpocket.app.data.remote.HibpCatalogApi
import com.osintpocket.app.data.remote.HudsonRockApi
import com.osintpocket.app.data.remote.LeakCheckApi
import com.osintpocket.app.data.remote.XonApi
import com.osintpocket.app.data.remote.XonPasswordApi
import com.osintpocket.app.data.remote.PwnedPasswordsApi
import com.osintpocket.app.data.remote.InternetDbApi
import com.osintpocket.app.data.remote.IpApiService
import com.osintpocket.app.data.remote.RdapApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Igual para todos los usuarios (sin versión de Android): identifica la app, no al dispositivo. */
    private val USER_AGENT = "OSINTPocket/${BuildConfig.VERSION_NAME} (passive-recon)"

    /** Intervalo mínimo entre peticiones por host (ms). ip-api gratis: 45 req/min. */
    private val HOST_INTERVALS = mapOf(
        "ip-api.com" to 1_400L,
        "crt.sh" to 2_000L,
        "rdap.org" to 500L,
        "internetdb.shodan.io" to 300L,
        "cloudflare-dns.com" to 50L,
        "haveibeenpwned.com" to 6_500L, // plan HIBP más bajo: 10 peticiones/minuto
        "api.xposedornot.com" to 600L, // 2/s (y 25/h, 100/día por IP)
        "passwords.xposedornot.com" to 300L,
        "leakcheck.io" to 1_000L,
        "cavalier.hudsonrock.com" to 1_100L, // ~1/s
    )

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(prefs: AppPrefs): OkHttpClient {
        return OkHttpClient.Builder()
            .proxySelector(TorProxySelector(prefs))
            .connectTimeout(30, TimeUnit.SECONDS) // los circuitos de Tor tardan más en abrirse
            .readTimeout(60, TimeUnit.SECONDS) // crt.sh puede tardar con dominios grandes
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor(USER_AGENT))
            .addInterceptor(RetryInterceptor())
            .addInterceptor(HostRateLimitInterceptor(HOST_INTERVALS, defaultIntervalMs = 250))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    @WebClient
    fun provideWebClient(prefs: AppPrefs): OkHttpClient {
        return OkHttpClient.Builder()
            .proxySelector(TorProxySelector(prefs))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor(USER_AGENT))
            .addInterceptor(HostRateLimitInterceptor(emptyMap(), defaultIntervalMs = 500))
            .build()
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideRdap(c: OkHttpClient, j: Json): RdapApi = retrofit("https://rdap.org/", c, j).create()

    @Provides @Singleton
    fun provideDoh(c: OkHttpClient, j: Json): DohApi = retrofit("https://cloudflare-dns.com/", c, j).create()

    @Provides @Singleton
    fun provideCrtSh(c: OkHttpClient, j: Json): CrtShApi = retrofit("https://crt.sh/", c, j).create()

    @Provides @Singleton
    fun provideInternetDb(c: OkHttpClient, j: Json): InternetDbApi =
        retrofit("https://internetdb.shodan.io/", c, j).create()

    @Provides @Singleton
    fun provideHibp(c: OkHttpClient, j: Json): HibpApi =
        retrofit("https://haveibeenpwned.com/api/v3/", c, j).create()

    @Provides @Singleton
    fun provideHibpCatalog(c: OkHttpClient, j: Json): HibpCatalogApi =
        retrofit("https://haveibeenpwned.com/api/v3/", c, j).create()

    @Provides @Singleton
    fun providePwnedPasswords(c: OkHttpClient, j: Json): PwnedPasswordsApi =
        retrofit("https://api.pwnedpasswords.com/", c, j).create()

    @Provides @Singleton
    fun provideXon(c: OkHttpClient, j: Json): XonApi = retrofit("https://api.xposedornot.com/", c, j).create()

    @Provides @Singleton
    fun provideXonPasswords(c: OkHttpClient, j: Json): XonPasswordApi =
        retrofit("https://passwords.xposedornot.com/", c, j).create()

    @Provides @Singleton
    fun provideLeakCheck(c: OkHttpClient, j: Json): LeakCheckApi = retrofit("https://leakcheck.io/", c, j).create()

    @Provides @Singleton
    fun provideHudsonRock(c: OkHttpClient, j: Json): HudsonRockApi =
        retrofit("https://cavalier.hudsonrock.com/", c, j).create()

    @Provides @Singleton
    fun provideIpApi(c: OkHttpClient, j: Json): IpApiService = retrofit("http://ip-api.com/", c, j).create()
}
