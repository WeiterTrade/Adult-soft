package com.adultaristocrat.cinema

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Перед началом фильма ставит на паузу музыку и видео в других программах.
 * Windows 10/11 — через системные «Медиа-элементы управления» (Spotify, браузеры, Яндекс Музыка, AIMP и т.д.).
 * Linux — через MPRIS (dbus): Rhythmbox, Spotify, браузеры, VLC и другие плееры.
 * На Windows 7 такого механизма нет — там ничего не делаем.
 */
object OtherMedia {
    private val os = System.getProperty("os.name").orEmpty().lowercase()

    fun pauseAll() {
        thread(isDaemon = true, name = "pause-other-media") {
            runCatching {
                when {
                    os.contains("win") -> pauseWindows()
                    os.contains("linux") -> pauseLinux()
                }
            }
        }
    }

    private val PS_SCRIPT = """
${'$'}ErrorActionPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
${'$'}asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await(${'$'}op, ${'$'}type) { ${'$'}t = ${'$'}asTask.MakeGenericMethod(${'$'}type).Invoke(${'$'}null, @(${'$'}op)); ${'$'}t.Wait(5000) | Out-Null; ${'$'}t.Result }
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
${'$'}m = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
foreach (${'$'}s in ${'$'}m.GetSessions()) {
  if (${'$'}s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { Await (${'$'}s.TryPauseAsync()) ([bool]) | Out-Null }
}
"""

    private fun pauseWindows() {
        // Windows 7/8 — нет системного управления медиа
        val ver = System.getProperty("os.version").orEmpty().substringBefore('.').toIntOrNull() ?: 0
        if (ver < 10) return
        val encoded = Base64.getEncoder().encodeToString(PS_SCRIPT.toByteArray(Charsets.UTF_16LE))
        val ps = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -EncodedCommand $encoded"
        // Запуск через wscript — без мелькающего чёрного окна консоли
        val vbs = File(System.getProperty("java.io.tmpdir"), "aa-pause-media.vbs")
        vbs.writeText("CreateObject(\"WScript.Shell\").Run \"$ps\", 0, True\r\n")
        val p = ProcessBuilder("wscript.exe", "//B", "//NoLogo", vbs.absolutePath).redirectErrorStream(true).start()
        p.waitFor(15, TimeUnit.SECONDS)
    }

    private fun pauseLinux() {
        // Если есть playerctl — проще всего
        val viaPlayerctl = runCatching {
            val p = ProcessBuilder("playerctl", "-a", "pause").redirectErrorStream(true).start()
            p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
        if (viaPlayerctl) return

        val list = ProcessBuilder(
            "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.DBus",
            "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames",
        ).redirectErrorStream(true).start()
        val out = list.inputStream.bufferedReader().readText()
        list.waitFor(5, TimeUnit.SECONDS)
        Regex("\"(org\\.mpris\\.MediaPlayer2\\.[^\"]+)\"").findAll(out).map { it.groupValues[1] }.forEach { name ->
            runCatching {
                ProcessBuilder(
                    "dbus-send", "--session", "--type=method_call", "--dest=$name",
                    "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player.Pause",
                ).redirectErrorStream(true).start().waitFor(3, TimeUnit.SECONDS)
            }
        }
    }
}
