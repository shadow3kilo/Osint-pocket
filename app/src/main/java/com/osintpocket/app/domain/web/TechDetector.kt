package com.osintpocket.app.domain.web

import okhttp3.Headers

data class Tech(val name: String, val category: String, val evidence: String)

/** Huellas pasivas a partir de cabeceras, nombres de cookie y el HTML inicial. Heurístico: puede fallar. */
object TechDetector {

    private class HeaderRule(val header: String, val name: String, val category: String, val contains: String? = null)
    private class HtmlRule(val pattern: Regex, val name: String, val category: String, val evidence: String)

    private const val CDN = "CDN / Hosting"
    private const val CMS = "CMS / Plataforma"
    private const val FW = "Framework"
    private const val LANG = "Lenguaje / Backend"
    private const val JS = "JavaScript"
    private const val CACHE = "Caché / Proxy"
    private const val ANALYTICS = "Analítica"

    private val headerRules = listOf(
        HeaderRule("cf-ray", "Cloudflare", CDN),
        HeaderRule("x-amz-cf-id", "Amazon CloudFront", CDN),
        HeaderRule("x-fastly-request-id", "Fastly", CDN),
        HeaderRule("x-served-by", "Fastly", CDN, contains = "cache-"),
        HeaderRule("x-vercel-id", "Vercel", CDN),
        HeaderRule("x-nf-request-id", "Netlify", CDN),
        HeaderRule("x-github-request-id", "GitHub Pages", CDN),
        HeaderRule("x-akamai-transformed", "Akamai", CDN),
        HeaderRule("x-azure-ref", "Azure Front Door", CDN),
        HeaderRule("x-drupal-cache", "Drupal", CMS),
        HeaderRule("x-drupal-dynamic-cache", "Drupal", CMS),
        HeaderRule("x-shopify-stage", "Shopify", CMS),
        HeaderRule("x-shopid", "Shopify", CMS),
        HeaderRule("x-wix-request-id", "Wix", CMS),
        HeaderRule("x-litespeed-cache", "LiteSpeed Cache", CACHE),
        HeaderRule("x-varnish", "Varnish", CACHE),
        HeaderRule("x-aspnet-version", "ASP.NET", LANG),
    )

    private val cookieRules = listOf(
        "PHPSESSID" to ("PHP" to LANG),
        "JSESSIONID" to ("Java (servlet)" to LANG),
        "ASP.NET_SessionId" to ("ASP.NET" to LANG),
        "laravel_session" to ("Laravel" to FW),
        "csrftoken" to ("Django" to FW),
        "connect.sid" to ("Express" to FW),
        "ci_session" to ("CodeIgniter" to FW),
        "_shopify_" to ("Shopify" to CMS),
        "wordpress_" to ("WordPress" to CMS),
        "wp-settings" to ("WordPress" to CMS),
    )

    private fun html(p: String, name: String, cat: String, evidence: String) =
        HtmlRule(Regex(p, RegexOption.IGNORE_CASE), name, cat, evidence)

    private val htmlRules = listOf(
        html("/wp-(?:content|includes)/", "WordPress", CMS, "rutas /wp-content/"),
        html("cdn\\.shopify\\.com", "Shopify", CMS, "recursos de cdn.shopify.com"),
        html("Drupal\\.settings|/sites/default/files/", "Drupal", CMS, "Drupal.settings / sites/default"),
        html("/media/jui/|/components/com_", "Joomla", CMS, "rutas de Joomla"),
        html("static\\.wixstatic\\.com", "Wix", CMS, "static.wixstatic.com"),
        html("static1\\.squarespace\\.com", "Squarespace", CMS, "static1.squarespace.com"),
        html("/_next/static/|__NEXT_DATA__", "Next.js", FW, "/_next/static"),
        html("__NUXT__|/_nuxt/", "Nuxt", FW, "/_nuxt/"),
        html("__sveltekit|/_app/immutable/", "SvelteKit", FW, "/_app/immutable"),
        html("ng-version=\"", "Angular", FW, "atributo ng-version"),
        html("data-reactroot|react-dom(?:\\.production)?(?:\\.min)?\\.js", "React", JS, "data-reactroot / react-dom"),
        html("data-v-[0-9a-f]{8}|vue(?:\\.runtime)?(?:\\.global)?(?:\\.prod)?(?:\\.min)?\\.js", "Vue.js", JS, "data-v-* / vue.js"),
        html("jquery(?:[.-]\\d[\\d.]*)?(?:\\.min)?\\.js", "jQuery", JS, "script jquery"),
        html("bootstrap(?:\\.bundle)?(?:\\.min)?\\.(?:css|js)", "Bootstrap", JS, "bootstrap.css/js"),
        html("googletagmanager\\.com/gtm\\.js", "Google Tag Manager", ANALYTICS, "gtm.js"),
        html("googletagmanager\\.com/gtag/js|google-analytics\\.com", "Google Analytics", ANALYTICS, "gtag/analytics.js"),
        html("connect\\.facebook\\.net/[^\"']*/fbevents\\.js", "Meta Pixel", ANALYTICS, "fbevents.js"),
        html("www\\.google\\.com/recaptcha/", "reCAPTCHA", "Seguridad", "script de reCAPTCHA"),
        html("challenges\\.cloudflare\\.com/turnstile", "Cloudflare Turnstile", "Seguridad", "script de Turnstile"),
    )

    private val generatorA = Regex(
        "<meta[^>]+name=[\"']generator[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val generatorB = Regex(
        "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*name=[\"']generator[\"']", RegexOption.IGNORE_CASE)

    fun detect(headers: Headers, html: String?): List<Tech> {
        val found = LinkedHashMap<String, Tech>()
        fun add(t: Tech) { found.putIfAbsent(t.name.lowercase(), t) }

        headers["Server"]?.trim()?.takeIf { it.isNotEmpty() }?.let { server ->
            val cat = if (server.contains("cloudflare", true) || server.contains("AkamaiGHost", true)) CDN else "Servidor web"
            add(Tech(server.substringBefore(' '), cat, "Server: $server"))
        }
        headers["X-Powered-By"]?.let { add(Tech(it.trim(), LANG, "X-Powered-By: $it")) }
        headers["X-Generator"]?.let { add(Tech(it.trim(), CMS, "X-Generator: $it")) }

        headerRules.forEach { r ->
            val v = headers[r.header] ?: return@forEach
            if (r.contains == null || v.contains(r.contains, ignoreCase = true)) {
                add(Tech(r.name, r.category, "cabecera ${r.header}"))
            }
        }

        val cookieNames = headers.values("Set-Cookie").map { it.substringBefore('=').trim() }
        cookieRules.forEach { (prefix, tech) ->
            cookieNames.firstOrNull { it.startsWith(prefix, ignoreCase = true) }?.let {
                add(Tech(tech.first, tech.second, "cookie $it"))
            }
        }

        if (!html.isNullOrEmpty()) {
            (generatorA.find(html) ?: generatorB.find(html))?.groupValues?.get(1)?.trim()?.let {
                add(Tech(it, CMS, "meta generator"))
            }
            htmlRules.forEach { r -> if (r.pattern.containsMatchIn(html)) add(Tech(r.name, r.category, r.evidence)) }
        }
        return found.values.toList()
    }
}
