@echo off
rem Starts the shop server for daily use: PostgreSQL first, then the application on port 8080.
rem Used by the Windows start-up shortcut (scripts\install-autostart.cmd); can also be run by hand.
rem Log: D:\theo-tech-system\logs\server.log
call "%~dp0_env.cmd"
if not exist "%THEO_ROOT%\logs" mkdir "%THEO_ROOT%\logs"

rem the app is already running? then there is nothing to do
"%PG_BIN%\pg_isready.exe" -h %PGHOST% -p %PGPORT% >nul 2>&1
netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul 2>&1 && (
  echo [serve] server already running on port 8080
  exit /b 0
)

call "%~dp0db-start.cmd" >> "%THEO_ROOT%\logs\server.log" 2>&1 || exit /b 1

set "JAR="
for %%F in ("%THEO_ROOT%\target\theo-tech-system-*.jar") do set "JAR=%%~fF"
if not defined JAR (
  echo [serve] no built application found in target\ - run scripts\build.cmd first >> "%THEO_ROOT%\logs\server.log"
  exit /b 1
)
set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

cd /d "%THEO_ROOT%"
echo [serve] %date% %time% starting %JAR% >> "%THEO_ROOT%\logs\server.log"
"%JAVA%" -Xmx512m -jar "%JAR%" --spring.profiles.active=dev >> "%THEO_ROOT%\logs\server.log" 2>&1
