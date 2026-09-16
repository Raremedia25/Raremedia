@echo off
rem Packs the project for upload to the server: source, Docker files and deploy folder only
rem (no local tooling, build output, logs or archived code). Result: %USERPROFILE%\theo-tech.tgz
setlocal
set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
set "OUT=%USERPROFILE%\theo-tech.tgz"
tar -czf "%OUT%" -C "%ROOT%\.." ^
  --exclude=theo-tech-system/.tooling ^
  --exclude=theo-tech-system/target ^
  --exclude=theo-tech-system/.removed-phase3 ^
  --exclude=theo-tech-system/backups ^
  --exclude=theo-tech-system/uploads ^
  --exclude=theo-tech-system/.env ^
  --exclude=*.log ^
  theo-tech-system
if errorlevel 1 (
  echo [pack] failed
  exit /b 1
)
echo [pack] wrote %OUT%
echo [pack] upload with:  scp "%OUT%" ubuntu@YOUR_SERVER_IP:~
