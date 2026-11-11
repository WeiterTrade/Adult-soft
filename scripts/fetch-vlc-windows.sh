#!/usr/bin/env bash
# Скачивает портативный VLC 3.0 (64-бит) и кладёт libvlc + плагины в ресурсы приложения.
# VLC 3.0 работает на Windows 7, 8, 10, 11.
set -euo pipefail
VER="${VLC_VERSION:-3.0.21}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/composeApp/desktopResources/windows/vlc"
TMP="$(mktemp -d)"
curl -fL --retry 3 "https://download.videolan.org/pub/videolan/vlc/$VER/win64/vlc-$VER-win64.zip" -o "$TMP/vlc.zip"
unzip -q "$TMP/vlc.zip" -d "$TMP"
rm -rf "$OUT"
mkdir -p "$OUT"
cp "$TMP/vlc-$VER/libvlc.dll" "$TMP/vlc-$VER/libvlccore.dll" "$OUT/"
cp -r "$TMP/vlc-$VER/plugins" "$OUT/"
# Убираем лишнее (интерфейсы, визуализации, Lua) — меньше размер
rm -rf "$OUT/plugins/gui" "$OUT/plugins/visualization" "$OUT/plugins/lua" "$OUT/plugins/video_splitter" || true
du -sh "$OUT"
