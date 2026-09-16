@echo off
rem Makes the shop server start automatically whenever this Windows user signs in (no administrator rights needed):
rem drops a small launcher into the Start-up folder that runs scripts\serve.cmd without a visible window.
rem Remove again with:  del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\THEO TECH server.vbs"
call "%~dp0_env.cmd"
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "LAUNCHER=%STARTUP%\THEO TECH server.vbs"
> "%LAUNCHER%" echo Set sh = CreateObject("WScript.Shell")
>> "%LAUNCHER%" echo sh.Run """%THEO_ROOT%\scripts\serve.cmd""", 0, False
if exist "%LAUNCHER%" (
  echo [autostart] installed: %LAUNCHER%
) else (
  echo [autostart] FAILED to write %LAUNCHER%
  exit /b 1
)
