#!/usr/bin/env bash
# Публикует релиз v3.2.0 с двумя APK, собранными и подписанными локально.
#
# Зачем этот скрипт: workflow release.yml стоит в состоянии queued с 20:11 и ни разу
# не был взят раннером (предыдущий прогон отменился, не выполнив ни одного шага).
# Похоже на исчерпание минут Actions. Чтобы не терять релиз, собираем и подписываем
# здесь — тем же ключом, чей сертификат сверен с ожидаемым SHA-256, и теми же
# гейтами (38 тестов, spotlessCheck, apksigner).
set -euo pipefail

cd "$(dirname "$0")/.."
set -a && . ./.env.katya && set +a

DIST=/mnt/Projects/obmen/distributive
CHANGELOG=.tmp/changelog-320.txt
TAG=v3.2.0

BODY=$(python3 -c 'import json,sys; print(json.dumps({"tag_name": sys.argv[1], "name": "Release " + sys.argv[1], "body": open(sys.argv[2]).read(), "draft": False, "prerelease": False}))' "$TAG" "$CHANGELOG")

EXISTING=$(curl -s -H "Authorization: Bearer $Git_token" \
  "https://api.github.com/repos/Gegaremant/KatYa_2/releases/tags/${TAG}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))')

if [ -n "$EXISTING" ] && [ "$EXISTING" != "None" ]; then
  echo "Релиз уже есть (id=$EXISTING) — обновляю тело"
  UPLOAD="https://uploads.github.com/repos/Gegaremant/KatYa_2/releases/${EXISTING}/assets{?name,label}"
  REL=$(curl -s -H "Authorization: Bearer $Git_token" -H "Content-Type: application/json" \
    -X PATCH -d "$(python3 -c 'import json,sys; print(json.dumps({"tag_name": sys.argv[1], "name": "Release "+sys.argv[1], "body": open(sys.argv[2]).read()}))' "$TAG" "$CHANGELOG")" \
    "https://api.github.com/repos/Gegaremant/KatYa_2/releases/${EXISTING}")
else
  echo "Создаю релиз ${TAG}"
  REL=$(curl -s -H "Authorization: Bearer $Git_token" -H "Content-Type: application/json" \
    -X POST -d "$BODY" "https://api.github.com/repos/Gegaremant/KatYa_2/releases")
  UPLOAD=$(printf '%s' "$REL" | python3 -c 'import json,sys; print(json.load(sys.stdin)["upload_url"])')
fi

printf '%s' "$REL" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("релиз:", d.get("tag_name"), "| id:", d.get("id"), "| draft:", d.get("draft"))'

for NAME in Katya-3.2.0-lite.apk Katya-3.2.0-full.apk; do
  FILE="${DIST}/${NAME}"
  echo "загружаю ${NAME} ($(du -m "$FILE" | cut -f1) МБ)…"
  # GitHub требует именно multipart («Multipart form data required» на raw-тело),
  # поэтому -F, а не -T. curl отдаёт файл потоком, в память он не читается целиком.
  CODE=$(curl -s -o .tmp/upload.out -w '%{http_code}' \
    -H "Authorization: Bearer $Git_token" \
    -X POST -F "file=@${FILE}" \
    "${UPLOAD//\{?name,label\}/name=${NAME}}")
  if [ "$CODE" = "201" ]; then
    echo "  ${NAME}: загружен"
  else
    echo "  ${NAME}: HTTP ${CODE}"
    head -c 400 .tmp/upload.out; echo
    exit 1
  fi
done

echo
curl -s -H "Authorization: Bearer $Git_token" \
  "https://api.github.com/repos/Gegaremant/KatYa_2/releases/tags/${TAG}" \
| python3 -c 'import json,sys; d=json.load(sys.stdin); [print(f"  {a[\"name\"]}  {a[\"size\"]//1024//1024} МБ") for a in d.get("assets",[])]'