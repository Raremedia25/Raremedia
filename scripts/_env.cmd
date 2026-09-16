@echo off
rem Shared environment for every script. Nothing here depends on user-level env vars.
rem Everything (Maven cache, Maven distribution, PostgreSQL cluster) lives under D:\theo-tech-system\.tooling

set "THEO_ROOT=%~dp0.."
for %%I in ("%THEO_ROOT%") do set "THEO_ROOT=%%~fI"

set "MAVEN_USER_HOME=%THEO_ROOT%\.tooling\m2"
rem MAVEN_USER_HOME only relocates the wrapper's Maven distribution; the dependency cache is pinned separately.
set "MAVEN_OPTS=-Dmaven.repo.local=%THEO_ROOT%\.tooling\m2\repository %MAVEN_OPTS%"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\java\jdk-25.0.2"

set "PG_HOME=%THEO_ROOT%\.tooling\pgsql"
set "PG_BIN=%PG_HOME%\bin"
set "PGDATA=%PG_HOME%\data"
set "PG_LOG=%PG_HOME%\logfile.txt"
set "PGPORT=5433"
set "PGHOST=127.0.0.1"
set "PGUSER=postgres"
set "PGDATABASE=theo_tech"
