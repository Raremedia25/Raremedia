@echo off
call "%~dp0_env.cmd"
if not exist "%PG_BIN%\pg_ctl.exe" (
  echo [db-status] PostgreSQL binaries not found at %PG_BIN%
  exit /b 2
)
"%PG_BIN%\pg_ctl.exe" -D "%PGDATA%" status
if %errorlevel% neq 0 (
  echo [db-status] NOT RUNNING  (start with scripts\db-start.cmd)
  exit /b 1
)
"%PG_BIN%\pg_isready.exe" -h %PGHOST% -p %PGPORT%
exit /b %errorlevel%
