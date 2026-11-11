@echo off
rem Software rendering - use if the window is black (old video cards)
cd /d "%~dp0"
start "" "runtime\bin\javaw.exe" -Dfile.encoding=UTF-8 -Xmx1024m -Dskiko.renderApi=SOFTWARE -Dcompose.application.resources.dir="%~dp0app\resources" -cp "app\*" com.adultaristocrat.cinema.MainKt
