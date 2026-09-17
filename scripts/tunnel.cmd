@echo off
rem Puts the shop on the internet through a Cloudflare quick tunnel (no account needed) and writes the public
rem link to logs\public-link.txt and to "THEO TECH link.txt" on the Desktop. Started by serve.cmd; can be run by hand.
rem The address changes every time the tunnel restarts - open the Desktop file (or run public-link.cmd) to see the current one.
call "%~dp0_env.cmd"
set "CF=%THEO_ROOT%\.tooling\cloudflared\cloudflared.exe"
set "TLOG=%THEO_ROOT%\logs\tunnel.log"
set "LINKFILE=%THEO_ROOT%\logs\public-link.txt"
if not exist "%THEO_ROOT%\logs" mkdir "%THEO_ROOT%\logs"

if not exist "%CF%" (
  echo [tunnel] cloudflared.exe not found at %CF%
  echo [tunnel] download: https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
  exit /b 1
)

rem already running? keep it (the link in the log is still valid)
tasklist /fi "imagename eq cloudflared.exe" 2>nul | find /i "cloudflared.exe" >nul
if %errorlevel%==0 (
  echo [tunnel] already running
  goto :report
)

rem wait (up to ~2 min) for the app to answer before opening the tunnel
set /a tries=0
:waitapp
powershell -NoProfile -Command "try { (Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { $false }" | find "True" >nul
if %errorlevel%==0 goto :startit
set /a tries+=1
if %tries% geq 24 (
  echo [tunnel] the application did not start; not opening the tunnel
  exit /b 1
)
timeout /t 5 /nobreak >nul
goto :waitapp

:startit
del "%TLOG%" >nul 2>&1
start "" /min "%CF%" tunnel --url http://127.0.0.1:8080 --no-autoupdate --loglevel info --logfile "%TLOG%"
timeout /t 8 /nobreak >nul

:report
set "LINK="
for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command "$t = Get-Content -Raw '%TLOG%' -ErrorAction SilentlyContinue; if ($t -match 'https://[a-z0-9-]+\.trycloudflare\.com') { $Matches[0] }"`) do set "LINK=%%L"
if not defined LINK (
  echo [tunnel] started, but no public link in the log yet - run scripts\public-link.cmd in a minute
  exit /b 0
)
> "%LINKFILE%" echo %LINK%
> "%USERPROFILE%\Desktop\THEO TECH link.txt" (
  echo THEO TECH LTD - shop system
  echo.
  echo Open this address on any phone or computer with internet:
  echo %LINK%
  echo.
  echo On the shop Wi-Fi you can also use: http://%COMPUTERNAME%:8080  or the PC's IP address on port 8080
  echo.
  echo The internet address changes when this PC restarts; this file is rewritten with the new one at each start.
  echo Written %date% %time%
)
echo [tunnel] public link: %LINK%
exit /b 0
