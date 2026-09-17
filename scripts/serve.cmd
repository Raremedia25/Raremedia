@echo off
rem Starts the shop server for daily use: PostgreSQL first, then the application on port 8080.
rem Used by the Windows start-up shortcut (scripts\install-autostart.cmd); can also be run by hand.
rem Log: D:\theo-tech-system\logs\server.log
call "%~dp0_env.cmd"
if not exist "%THEO_ROOT%\logs" mkdir "%THEO_ROOT%\logs"
set "LOG=%THEO_ROOT%\logs\server.log"

rem the app is already running? then there is nothing to do
netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul 2>&1 && (
  echo [serve] server already running on port 8080
  exit /b 0
)

rem Do NOT redirect this into the log: cmd hands every inheritable handle to the processes it starts, so a
rem PostgreSQL launched here would keep the log file open for its whole life and every later ">> log"
rem (including the java line below) would fail with "file is being used by another process".
call "%~dp0db-start.cmd" >nul 2>&1
if errorlevel 1 (
  echo [serve] %date% %time% PostgreSQL failed to start - see .tooling\pgsql\logfile.txt >> "%LOG%"
  exit /b 1
)
echo [serve] %date% %time% PostgreSQL running on %PGHOST%:%PGPORT% >> "%LOG%"

set "JAR="
for %%F in ("%THEO_ROOT%\target\theo-tech-system-*.jar") do set "JAR=%%~fF"
if not defined JAR (
  echo [serve] no built application found in target\ - run scripts\build.cmd first >> "%LOG%"
  exit /b 1
)
set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

rem Small serial-GC heap: the shop PC has 4 GB and is often short of memory. The app needs well under 300 MB;
rem a bigger default heap made the JVM refuse to start ("Failed to allocate ... mark stack") when memory was tight.
set "JAVA_OPTS=-Xmx320m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -XX:-UsePerfData -Djava.awt.headless=true"

cd /d "%THEO_ROOT%"
echo [serve] %date% %time% starting %JAR% >> "%LOG%"
"%JAVA%" %JAVA_OPTS% -jar "%JAR%" --spring.profiles.active=dev >> "%LOG%" 2>&1
echo [serve] %date% %time% java exited with code %errorlevel% >> "%LOG%"
exit /b %errorlevel%
