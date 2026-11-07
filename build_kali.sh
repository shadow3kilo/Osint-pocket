#!/usr/bin/env bash
# OSINT Pocket: compila la APK en Kali/Debian y la instala por adb.
#
#   ./build_kali.sh              -> APK debug + instalación si hay un dispositivo conectado
#   ./build_kali.sh --no-install -> solo compila
#   ./build_kali.sh --release    -> APK release sin firmar (para firmar con tu keystore)
#   ./build_kali.sh --test       -> ejecuta los tests unitarios antes de compilar
#
# Instala si faltan: OpenJDK 17, Android cmdline-tools, platform-tools, platform 35 y build-tools 35.
set -euo pipefail

GRADLE_VERSION="8.11.1"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
ANDROID_PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"

INSTALL=1; VARIANT="debug"; RUN_TESTS=0
for arg in "$@"; do
  case "$arg" in
    --no-install) INSTALL=0 ;;
    --release) VARIANT="release"; INSTALL=0 ;;
    --test) RUN_TESTS=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $arg" >&2; exit 2 ;;
  esac
done

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

c_ok()   { printf '\033[1;32m[✓]\033[0m %s\n' "$*"; }
c_info() { printf '\033[1;34m[*]\033[0m %s\n' "$*"; }
c_err()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; }

need_sudo() { if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi; }

# ── 1. Utilidades del sistema ─────────────────────────────────────────────
missing=()
for bin in curl unzip; do command -v "$bin" >/dev/null || missing+=("$bin"); done
if [ ${#missing[@]} -gt 0 ]; then
  c_info "Instalando: ${missing[*]}"
  need_sudo apt-get update -qq && need_sudo apt-get install -y -qq "${missing[@]}"
fi

# ── 2. JDK 17 (AGP 8.7 lo requiere; Kali trae a veces 21, que también sirve) ──
java_major() { java -version 2>&1 | awk -F'"' '/version/ {split($2,v,"."); print (v[1]=="1"?v[2]:v[1])}'; }
if ! command -v java >/dev/null || [ "$(java_major)" -lt 17 ]; then
  c_info "Instalando OpenJDK 17"
  need_sudo apt-get update -qq
  need_sudo apt-get install -y -qq openjdk-17-jdk-headless || need_sudo apt-get install -y -qq openjdk-21-jdk-headless
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac || command -v java)")")")"
  export JAVA_HOME
fi
c_ok "Java $(java_major) en $JAVA_HOME"

# ── 3. Android SDK ────────────────────────────────────────────────────────
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  c_info "Descargando Android cmdline-tools en $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ] || [ ! -d "$ANDROID_HOME/platform-tools" ]; then
  c_info "Instalando platform-tools, $ANDROID_PLATFORM y $BUILD_TOOLS"
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" --install "platform-tools" "$ANDROID_PLATFORM" "$BUILD_TOOLS" >/dev/null
fi
c_ok "Android SDK listo"

echo "sdk.dir=$ANDROID_HOME" > local.properties

# ── 4. Gradle wrapper (se genera la primera vez) ──────────────────────────
if [ ! -f gradlew ] || [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  c_info "Generando Gradle wrapper $GRADLE_VERSION"
  GRADLE_CACHE="$HOME/.cache/osint-pocket/gradle-$GRADLE_VERSION"
  if [ ! -x "$GRADLE_CACHE/bin/gradle" ]; then
    mkdir -p "$(dirname "$GRADLE_CACHE")"
    tmp="$(mktemp -d)"
    curl -fL --retry 3 -o "$tmp/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    unzip -q "$tmp/gradle.zip" -d "$tmp"
    rm -rf "$GRADLE_CACHE"
    mv "$tmp/gradle-$GRADLE_VERSION" "$GRADLE_CACHE"
    rm -rf "$tmp"
  fi
  "$GRADLE_CACHE/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin -q
fi
chmod +x gradlew
c_ok "Gradle wrapper listo"

# ── 5. Tests y compilación ────────────────────────────────────────────────
if [ "$RUN_TESTS" -eq 1 ]; then
  c_info "Ejecutando tests unitarios"
  ./gradlew --no-daemon testDebugUnitTest
fi

TASK="assemble${VARIANT^}"
c_info "Compilando ($TASK)…"
./gradlew --no-daemon "$TASK"

if [ "$VARIANT" = "debug" ]; then
  APK="app/build/outputs/apk/debug/app-debug.apk"
else
  APK="app/build/outputs/apk/release/app-release-unsigned.apk"
fi
[ -f "$APK" ] || { c_err "No se encontró la APK en $APK"; exit 1; }
mkdir -p dist
cp "$APK" "dist/osint-pocket-$VARIANT.apk"
c_ok "APK: $PROJECT_DIR/dist/osint-pocket-$VARIANT.apk ($(du -h "$APK" | cut -f1))"

# ── 6. Instalación ────────────────────────────────────────────────────────
if [ "$INSTALL" -eq 1 ]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
  if "$ADB" devices | awk 'NR>1 && $2=="device"' | grep -q .; then
    c_info "Instalando en el dispositivo"
    "$ADB" install -r "dist/osint-pocket-$VARIANT.apk"
    c_ok "Instalada. Ábrela desde el cajón de apps."
  else
    c_info "No hay dispositivos conectados (activa la depuración USB). APK en dist/."
  fi
fi
