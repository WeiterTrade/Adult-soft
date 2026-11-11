@echo off
rem Debug launch with console. Send the error text to the developer.
cd /d "%~dp0"
"runtime\bin\java.exe" -Dfile.encoding=UTF-8 -Xmx1024m -Dskiko.renderApi=OPENGL -Dcompose.application.resources.dir="%~dp0app\resources" -cp "app\*" com.adultaristocrat.cinema.MainKt
pause
