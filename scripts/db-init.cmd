@echo off
rem One-time cluster initialisation. Safe to re-run: refuses if the cluster already exists.
rem Usage: db-init.cmd [superuser-password]   (default: theotech)
call "%~dp0_env.cmd"
setlocal
set "PW=%~1"
if "%PW%"=="" set "PW=theotech"

if not exist "%PG_BIN%\initdb.exe" (
  echo [db-init] PostgreSQL binaries not found at %PG_BIN%
  exit /b 1
)
if exist "%PGDATA%\PG_VERSION" (
  echo [db-init] Cluster already exists at %PGDATA%. Nothing to do.
  exit /b 0
)

set "PWFILE=%TEMP%\theo-pg-pw-%RANDOM%.txt"
<nul set /p ="%PW%" > "%PWFILE%"
"%PG_BIN%\initdb.exe" -D "%PGDATA%" -U %PGUSER% --pwfile="%PWFILE%" -E UTF8 --locale=C -A scram-sha-256
set "RC=%errorlevel%"
del /q "%PWFILE%" >nul 2>&1
if not "%RC%"=="0" (
  echo [db-init] initdb failed with code %RC%
  exit /b %RC%
)

call "%~dp0db-start.cmd" || exit /b 1

set "PGPASSWORD=%PW%"
"%PG_BIN%\psql.exe" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d postgres -v ON_ERROR_STOP=1 ^
  -c "CREATE DATABASE theo_tech      ENCODING 'UTF8' TEMPLATE template0;" ^
  -c "CREATE DATABASE theo_tech_test ENCODING 'UTF8' TEMPLATE template0;"
if %errorlevel% neq 0 (
  echo [db-init] Database creation failed.
  exit /b 1
)
echo [db-init] Cluster ready on %PGHOST%:%PGPORT% with databases theo_tech and theo_tech_test.
endlocal
