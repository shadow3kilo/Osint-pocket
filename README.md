# OSINT Pocket

App Android nativa (Kotlin, Jetpack Compose, Material 3, MVVM + Hilt + Retrofit/OkHttp + Room) para
**reconocimiento pasivo** con fuentes abiertas y para **auditar tu propia huella digital**.
No escanea ni ataca nada: solo consulta fuentes públicas. Úsala sobre objetivos propios o con autorización.

Permisos: solo `INTERNET`. Las fotos y archivos se eligen con los selectores del sistema.

## Compilar

### Kali / Debian (un comando)

```bash
chmod +x build_kali.sh
./build_kali.sh               # compila la APK debug y la instala por adb si hay móvil conectado
./build_kali.sh --no-install  # solo compila → dist/osint-pocket-debug.apk
./build_kali.sh --test        # ejecuta los tests unitarios antes
./build_kali.sh --release     # APK release sin firmar
```

El script instala lo que falte: OpenJDK 17, Android cmdline-tools en `~/Android/Sdk`, platform 35,
build-tools 35 y el Gradle wrapper 8.11.1.

### GitHub Actions

Sube el proyecto a un repositorio de GitHub. El workflow `.github/workflows/build.yml` ejecuta los
tests, compila la APK y la deja como artefacto descargable (`osint-pocket-debug-apk`) en la pestaña
**Actions**. También se puede lanzar a mano con *Run workflow*.

### Android Studio

Abre la carpeta del proyecto. Android Studio genera el wrapper y descarga el SDK necesario.

## Módulos y APIs

| Módulo | Fuente | Key | Límites |
|---|---|---|---|
| Dominio/IP: registro | RDAP vía `rdap.org` (redirige al registro autoritativo) | No | Razonable; la app espacia a 0,5 s |
| Dominio/IP: DNS | Cloudflare DNS-over-HTTPS (`cloudflare-dns.com`) | No | Generoso |
| Dominio/IP: subdominios | Certificate Transparency (`crt.sh`) | No | Lento y a veces saturado; la app espacia a 2 s |
| Dominio/IP: exposición | Shodan InternetDB (`internetdb.shodan.io`) | No | Uso no comercial; 404 = sin datos |
| Dominio/IP: geolocalización | `ip-api.com` (solo HTTP en el plan gratuito) | No | 45 peticiones/min; la app espacia a 1,4 s |
| Web | Petición GET directa al sitio + `/.well-known/security.txt` | No | 0,5 s por host |
| Alias | Las URLs de `app/src/main/assets/sites.json` | No | 8 a la vez, 12 s por sitio |
| Brechas (email) | XposedOrNot `breach-analytics` | No | 2/s, 25/hora, 100/día por IP; uso personal |
| Brechas (email) | LeakCheck API pública | No | Solo nombres de fuentes y tipos de datos, nunca valores |
| Infostealers (email) | Hudson Rock Cavalier OSINT | No | ~1/s; contraseñas enmascaradas |
| Brechas (email, opcional) | Have I Been Pwned v3 | **Sí, de pago** | Plan básico: 10 peticiones/min; la app espacia a 6,5 s |
| Contraseñas | Pwned Passwords (SHA-1, envía 5 hex, `Add-Padding`) | No | Sin límite práctico |
| Contraseñas (opcional) | XposedOrNot `pass/anon` (Keccak-512 original, envía 10 hex) | No | Menos anónimo: desactivado por defecto |
| Hash | Local (MD5/SHA-1/SHA-256) + enlace a VirusTotal | No | El archivo nunca se sube |

Todas las peticiones llevan el User-Agent `OSINTPocket/<versión> (Android <v>; passive-recon)` y
reintentos con backoff exponencial ante 429/5xx (respetando `Retry-After`).

La API key de HIBP se guarda con `EncryptedSharedPreferences` (clave del Android Keystore).
El historial vive solo en el móvil (Room), se puede desactivar en Ajustes y nunca guarda
contraseñas ni coordenadas GPS.

## Añadir o corregir sitios del módulo Alias

Desde la app: **Alias → ⋮ → Exportar lista actual**, edita el JSON e **Importar lista JSON**.
Si algo está mal, la importación lo rechaza y dice qué. **Restaurar lista de fábrica** vuelve a la original.

Para cambiar la lista de fábrica, edita `app/src/main/assets/sites.json` y recompila.

```json
{
  "name": "GitHub",
  "category": "Código",
  "url": "https://github.com/{}",
  "probe": "https://api.github.com/users/{}",
  "detect": "status",
  "notFoundStatus": [404],
  "regex": "^[A-Za-z0-9-]{1,39}$"
}
```

| Campo | Obligatorio | Significado |
|---|---|---|
| `name` | sí | Nombre único |
| `url` | sí | Perfil público; `{}` se sustituye por el alias |
| `category` | no | Agrupación (por defecto «Otros») |
| `probe` | no | URL que se consulta de verdad (p. ej. una API); si falta se usa `url` |
| `detect` | no | `status` (por defecto): 2xx = existe · `text`: si el cuerpo contiene `errorText` no existe · `url`: si la URL final tras redirecciones contiene `errorUrl` no existe |
| `notFoundStatus` | no | Códigos que significan «no existe» (por defecto `[404, 410]`) |
| `errorText` | con `detect: text` | Texto que aparece cuando el usuario no existe |
| `errorUrl` | con `detect: url` | Fragmento de la URL de error |
| `regex` | no | Formato de alias admitido; si no casa, el sitio se marca como no aplicable |

Un 403 o un 429 se muestran como **Incierto**, no como encontrado: suelen ser bloqueos anti-bot.

## Tests

```bash
./gradlew testDebugUnitTest
```

Cubren el parser de objetivos, el evaluador de cabeceras, el detector de tecnologías, el clasificador
de alias y el validador de la lista, la limpieza de metadatos JPEG/PNG/WebP, los hashes, Pwned
Passwords y la exportación del historial.
