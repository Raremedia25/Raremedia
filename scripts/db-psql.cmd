@echo off
rem Opens psql against theo_tech on the local cluster. Extra arguments are passed through,
rem e.g.  db-psql.cmd -c "\l"        or        db-psql.cmd -d theo_tech_test
call "%~dp0_env.cmd"
if not defined PGPASSWORD set "PGPASSWORD=theotech"
"%PG_BIN%\psql.exe" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d %PGDATABASE% %*
