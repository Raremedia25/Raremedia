@echo off
rem Shows the shop's current internet address (from the running tunnel) and opens it in the browser.
call "%~dp0_env.cmd"
call "%~dp0tunnel.cmd"
if exist "%THEO_ROOT%\logs\public-link.txt" (
  set /p LINK=<"%THEO_ROOT%\logs\public-link.txt"
  echo.
  echo Public link: %LINK%
  start "" "%LINK%"
) else (
  echo No public link yet. Is the application running? Try again in a minute.
)
pause
