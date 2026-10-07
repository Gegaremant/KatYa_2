#!/usr/bin/env bash
# Качает компоненты песочницы, которые вшиваются в `full`-сборку, и раскладывает их
# в `androidApp/src/full/assets/katya-components/`.
#
# Зачем: полевые проверки показали, что песочница чаще всего не встаёт не из-за кода,
# а потому что `github.com` и `release-assets.githubusercontent.com` отдают
# `Socket timeout`. Если нужное уже лежит в APK, сеть вообще не участвует.
#
# В git эти файлы не попадают — это ~140 МБ, которые не место в истории репозитория.
# Скрипт идемпотентен: уже скачанное перекачивать не нужно, достаточно проверить размер.
#
# Запуск:  tools/fetch-bundled-components.sh [каталог]
set -euo pipefail

DEST="${1:-androidApp/src/full/assets/katya-components}"
ROOTFS_VERSION="4.29.0"
NATIVE_VERSION="3.1.3"
NATIVE_REPO="Gegaremant/KatYa_2"

mkdir -p "$DEST"

# Feedback 07.10: при targetSdk 37 Android не даёт напрямую выполнять бинарник из
# app_data_file — отказ приходит как `error=13, Permission denied`, хотя access(X_OK)
# разрешает, владелец совпадает с приложением, категории SELinux одинаковые и noexec
# на сегменте нет. Штатный обход — отдать библиотеки как jniLibs: пакетный менеджер
# распакует их в nativeLibraryDir сам, и там приложению разрешено запускать свой
# нативный код. Поэтому в full-сборку natives кладутся и в assets (чтобы installer
# умел их поставить и в lite-схеме), и в jniLibs (чтобы они были доступны сразу).
JNI_DIR="${JNI_DIR:-androidApp/src/full/jniLibs}"

# abi -> имя каталога в proot-distro
declare -A ARCH=(
  ["arm64-v8a"]="aarch64"
  ["armeabi-v7a"]="arm"
  ["x86_64"]="x86_64"
)

# Кладёт natives из скачанного zip в jniLibs.
#
# Имена оставляем как в архиве. Имя `libtalloc.so.2` в jniLibs не годится: Gradle
# считает нативной библиотекой только `lib*.so`, и `.so.2` молча выпадает из сборки
# (проверено — в APK такой файл не попадает). Поэтому в jniLibs лежит `libtalloc.so`,
# а нужное линкеру имя `libtalloc.so.2` приложение создаёт само в каталоге, который
# доступен для записи и стоит в LD_LIBRARY_PATH.
stage_natives() {
  local abi="$1" zip="$2"
  local out="${JNI_DIR}/${abi}"
  if [ ! -f "${out}/libproot.so" ]; then
    mkdir -p "$out"
    local tmp
    tmp=$(mktemp -d)
    unzip -qo "$zip" -d "$tmp"
    local f
    for f in "$tmp"/*; do
      [ -f "$f" ] || continue
      case "$(basename "$f")" in
        libtalloc.so) cp -f "$f" "${out}/libtalloc.so" ;;
        libxray.so) cp -f "$f" "${out}/libxray.so" ;;
        libproot*) cp -f "$f" "$out/" ;;
      esac
    done
    rm -rf "$tmp"
    chmod 755 "$out"/* 2>/dev/null || true
    echo "  jniLibs/${abi}: $(ls "$out" | tr '\n' ' ')"
  else
    echo "  jniLibs/${abi}: уже есть"
  fi
}

fetch() {
  local url="$1" out="$2"
  if [ -s "$out" ]; then
    echo "  уже есть: $(basename "$out") ($(du -h "$out" | cut -f1))"
    return 0
  fi
  echo "  качаю: $(basename "$out")"
  # -C - докачивает начатое, -f обрывается на HTTP-ошибке, чтобы не оставить битый файл.
  # -# вместо таблицы: 137 МБ полноэкранного прогресса засоряют лог CI на сотни строк.
  curl -fL --progress-bar --retry 3 --retry-delay 3 -C - -o "$out.part" "$url"
  mv "$out.part" "$out"
}

for abi in "${!ARCH[@]}"; do
  arch="${ARCH[$abi]}"
  echo "== $abi ($arch)"

  # Имя файла несёт «v» перед версией — без него ассет не находится (404).
  rootfs="debian-trixie-${arch}-pd-v${ROOTFS_VERSION}.tar.xz"
  fetch "https://github.com/termux/proot-distro/releases/download/v${ROOTFS_VERSION}/${rootfs}" \
        "${DEST}/debian_${abi}.tar.xz"

  fetch "https://github.com/${NATIVE_REPO}/releases/download/v${NATIVE_VERSION}/native-${abi}.zip" \
        "${DEST}/native_${abi}.zip"

  stage_natives "$abi" "${DEST}/native_${abi}.zip"
done

echo
echo "Готово. Содержимое ${DEST}:"
ls -lh "$DEST" | tail -n +2
du -sh "$DEST"