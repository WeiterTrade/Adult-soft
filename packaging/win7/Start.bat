@echo off
rem Adult Aristocrat launcher (Windows 7 / 8 / 10 / 11)
cd /d "%~dp0"
start "" "runtime\bin\javaw.exe" -Dfile.encoding=UTF-8 -Xmx1024m -Dskiko.renderApi=OPENGL -Dcompose.application.resources.dir="%~dp0app\resources" -cp "app\*" com.adultaristocrat.cinema.MainKt
