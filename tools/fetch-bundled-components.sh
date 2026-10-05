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

# abi -> имя каталога в proot-distro
declare -A ARCH=(
  ["arm64-v8a"]="aarch64"
  ["armeabi-v7a"]="arm"
  ["x86_64"]="x86_64"
)

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
done

echo
echo "Готово. Содержимое ${DEST}:"
ls -lh "$DEST" | tail -n +2
du -sh "$DEST"