@echo off
rem Runs the Maven wrapper with the D:-local repository.
rem Integration tests (*IT) run only when the local PostgreSQL cluster is up.
rem Usage: build.cmd [maven goals...]      default: clean verify
call "%~dp0_env.cmd"
setlocal
set "GOALS=%*"
if "%GOALS%"=="" set "GOALS=clean verify"

set "IT_FLAG=-DskipITs=true"
"%PG_BIN%\pg_isready.exe" -h %PGHOST% -p %PGPORT% >nul 2>&1
if %errorlevel%==0 (
  set "IT_FLAG=-Pit-db"
  echo [build] PostgreSQL reachable on %PGPORT% - integration tests enabled.
) else (
  echo [build] PostgreSQL not reachable on %PGPORT% - integration tests skipped.
)

call "%THEO_ROOT%\mvnw.cmd" -B -f "%THEO_ROOT%\pom.xml" %IT_FLAG% %GOALS%
exit /b %errorlevel%
