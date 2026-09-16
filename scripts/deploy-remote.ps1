<#
.SYNOPSIS
  Installs and starts THEO TECH LTD on a fresh Ubuntu server (Oracle Cloud Always Free) over SSH.

.DESCRIPTION
  Run from Windows PowerShell. Does everything after the server exists:
    1. packs the project (scripts\pack-for-deploy.cmd) and uploads it
    2. installs Docker if missing, opens ports 80/443 in the server firewall
    3. writes .env with freshly generated secrets (kept only on the server) unless one already exists
    4. docker compose up -d --build, waits for the app to report healthy
  Re-running is safe: it re-uploads the code and rebuilds the app; database, .env and backups are kept.

.EXAMPLE
  .\deploy-remote.ps1 -ServerIp 129.151.1.2 -KeyPath $HOME\.ssh\theotech_oci
  .\deploy-remote.ps1 -ServerIp 129.151.1.2 -KeyPath $HOME\.ssh\theotech_oci -Domain theotech.duckdns.org
#>
param(
  [Parameter(Mandatory = $true)] [string] $ServerIp,
  [Parameter(Mandatory = $true)] [string] $KeyPath,
  [string] $User = "ubuntu",
  [string] $Domain = ""          # leave empty to serve plain http://<ip>; set a domain for automatic HTTPS
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$sshOpts = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=20")
$target = "$User@$ServerIp"

function Remote([string] $script) {
  # runs a bash script on the server; stdin carries the script so quoting stays simple
  $script | & ssh @sshOpts $target "bash -s"
  if ($LASTEXITCODE -ne 0) { throw "remote step failed (exit $LASTEXITCODE)" }
}

Write-Host "==> 1/5 packing project" -ForegroundColor Cyan
& "$root\scripts\pack-for-deploy.cmd" | Out-Null
$archive = Join-Path $HOME "theo-tech.tgz"
if (-not (Test-Path $archive)) { throw "pack failed: $archive missing" }

Write-Host "==> 2/5 checking SSH access to $target" -ForegroundColor Cyan
Remote "echo connected to \$(hostname) as \$(whoami); uname -m; . /etc/os-release && echo \$PRETTY_NAME"

Write-Host "==> 3/5 uploading $([math]::Round((Get-Item $archive).Length / 1KB)) KB" -ForegroundColor Cyan
& scp @sshOpts $archive "${target}:~/theo-tech.tgz"
if ($LASTEXITCODE -ne 0) { throw "upload failed" }

Write-Host "==> 4/5 installing Docker, opening ports, writing .env" -ForegroundColor Cyan
$siteAddress = if ($Domain) { $Domain } else { ":80" }
$cookieSecure = if ($Domain) { "true" } else { "false" }
Remote @"
set -eu
if ! command -v docker >/dev/null 2>&1; then
  echo "installing docker..."
  curl -fsSL https://get.docker.com | sudo sh >/dev/null
  sudo usermod -aG docker \$USER
fi
# Oracle Ubuntu images ship iptables rules that only allow SSH; open the web ports and keep them after reboot
for port in 80 443; do
  if ! sudo iptables -C INPUT -m state --state NEW -p tcp --dport \$port -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport \$port -j ACCEPT
  fi
done
sudo netfilter-persistent save >/dev/null 2>&1 || true
# unpack over the previous version (keeps .env and backups, which are not in the archive)
tar -xzf ~/theo-tech.tgz -C ~
cd ~/theo-tech-system
if [ ! -f .env ]; then
  cat > .env <<EOF
APP_DB_PASSWORD=\$(openssl rand -hex 24)
APP_REMEMBER_ME_KEY=\$(openssl rand -hex 32)
SITE_ADDRESS=$siteAddress
APP_COOKIE_SECURE=$cookieSecure
BACKUP_KEEP_DAYS=30
TZ=Africa/Kigali
EOF
  chmod 600 .env
  echo ".env created with generated secrets"
else
  sed -i "s|^SITE_ADDRESS=.*|SITE_ADDRESS=$siteAddress|; s|^APP_COOKIE_SECURE=.*|APP_COOKIE_SECURE=$cookieSecure|" .env
  echo ".env kept (site address updated)"
fi
"@

Write-Host "==> 5/5 building and starting (first build takes 5-10 minutes)" -ForegroundColor Cyan
Remote @"
set -eu
cd ~/theo-tech-system
sudo docker compose up -d --build
echo "waiting for the application..."
for i in \$(seq 1 90); do
  if sudo docker compose exec -T app wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo "application healthy after \$((i*5))s"; exit 0
  fi
  sleep 5
done
echo "application not healthy yet; last log lines:"; sudo docker compose logs --tail=40 app; exit 1
"@

$url = if ($Domain) { "https://$Domain" } else { "http://$ServerIp" }
Write-Host ""
Write-Host "Deployed. Open $url and sign in with admin / Admin@123 (you will be asked to set a new password)." -ForegroundColor Green
Write-Host "Backups: ~/theo-tech-system/backups on the server. Logs: ssh ... 'cd theo-tech-system && sudo docker compose logs -f app'"
