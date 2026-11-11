#!/usr/bin/env bash
# Скачивает шрифты (Play, Russo One — с кириллицей) в проект.
# Если скачать не удалось — приложение просто использует системный шрифт.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A="$ROOT/composeApp/src/androidMain/assets/fonts"
D="$ROOT/composeApp/src/desktopMain/resources/fonts"
mkdir -p "$A" "$D"
BASE="https://raw.githubusercontent.com/google/fonts/main/ofl"
for f in play/Play-Regular.ttf play/Play-Bold.ttf russoone/RussoOne-Regular.ttf; do
  n="$(basename "$f")"
  if [ ! -s "$A/$n" ]; then
    curl -fsSL --retry 3 "$BASE/$f" -o "$A/$n" || { echo "WARN: не удалось скачать $n"; rm -f "$A/$n"; }
  fi
  if [ -s "$A/$n" ]; then cp "$A/$n" "$D/$n"; fi
done
ls -la "$A" "$D"
