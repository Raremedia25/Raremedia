@echo off
call "%~dp0_env.cmd"
"%PG_BIN%\pg_ctl.exe" -D "%PGDATA%" status >nul 2>&1
if %errorlevel% neq 0 (
  echo [db-stop] PostgreSQL is not running.
  exit /b 0
)
"%PG_BIN%\pg_ctl.exe" -D "%PGDATA%" -m fast -w -t 60 stop
if %errorlevel% neq 0 (
  echo [db-stop] Failed to stop. See %PG_LOG%
  exit /b 1
)
echo [db-stop] PostgreSQL stopped.
