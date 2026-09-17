@echo off
call "%~dp0_env.cmd"
if not exist "%PG_BIN%\pg_ctl.exe" (
  echo [db-start] PostgreSQL binaries not found at %PG_BIN%
  exit /b 1
)
if not exist "%PGDATA%\PG_VERSION" (
  echo [db-start] No cluster at %PGDATA%. Run scripts\db-init.cmd first.
  exit /b 1
)
"%PG_BIN%\pg_ctl.exe" -D "%PGDATA%" status >nul 2>&1
if %errorlevel%==0 (
  echo [db-start] PostgreSQL is already running on port %PGPORT%.
  exit /b 0
)
rem stdin/stdout/stderr go to nul on purpose: the postgres process inherits these handles and keeps them for
rem as long as it runs; if they pointed at a log file (serve.cmd redirects this script), every later write to
rem that file from another process would fail with "file is being used by another process".
"%PG_BIN%\pg_ctl.exe" -D "%PGDATA%" -l "%PG_LOG%" -w -t 60 -o "-p %PGPORT% -c listen_addresses=%PGHOST%" start <nul >nul 2>&1
if %errorlevel% neq 0 (
  echo [db-start] Failed to start. See %PG_LOG%
  exit /b 1
)
echo [db-start] PostgreSQL 18 running on %PGHOST%:%PGPORT%  (data: %PGDATA%)
