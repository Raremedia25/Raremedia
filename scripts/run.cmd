@echo off
rem Starts the application with the dev profile against the local cluster (starts it if needed).
call "%~dp0_env.cmd"
call "%~dp0db-start.cmd" || exit /b 1
call "%THEO_ROOT%\mvnw.cmd" -f "%THEO_ROOT%\pom.xml" -DskipTests -DskipITs spring-boot:run -Dspring-boot.run.profiles=dev
exit /b %errorlevel%
