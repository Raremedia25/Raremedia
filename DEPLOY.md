# Deploying THEO TECH LTD on an Oracle Cloud Always Free server

One small Linux server runs everything: PostgreSQL, the application, an HTTPS front door (Caddy) and a
daily database backup, all as Docker containers described in `docker-compose.yml`. Nothing is paid for.

Time needed: about one hour the first time. You need a credit card for Oracle's identity check (it is not
charged for Always Free resources) and, ideally, a domain name so the site gets HTTPS.

---

## 1. Create the server

1. Sign up at <https://cloud.oracle.com/> (Free Tier). Choose the home region closest to Rwanda that offers
   Always Free capacity (for example Johannesburg or Frankfurt).
2. Menu → **Compute → Instances → Create instance**.
   - Name: `theotech`
   - Image: **Ubuntu 24.04** (Canonical Ubuntu).
   - Shape: click *Change shape* → **Ampere** → `VM.Standard.A1.Flex`, **2 OCPUs, 12 GB memory**
     (Always Free allows up to 4 OCPUs / 24 GB in total; 2/12 is plenty).
     If the region says "Out of capacity", try again later or pick 1 OCPU / 6 GB.
   - Networking: keep the defaults (a new virtual cloud network, **assign a public IPv4 address**).
   - SSH keys: **Generate a key pair** and download the private key (`ssh-key-….key`). Keep it safe.
   - Boot volume: default (about 47 GB).
3. Click **Create** and wait until the state is *Running*. Note the **Public IP address**.

## 2. Open ports 80 and 443

Oracle blocks everything except SSH by default, in two places. Both must be opened.

**a) Cloud firewall.** Instance page → *Primary VNIC* → *Subnet* → *Default Security List* →
**Add Ingress Rules**: Source `0.0.0.0/0`, protocol TCP, destination port `80`. Add a second rule for port `443`.

**b) Firewall inside Ubuntu.** After connecting in step 3, run:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Connect and install Docker

From PowerShell on your Windows PC (replace the key path and IP):

```powershell
ssh -i C:\Users\ISAIE\Downloads\ssh-key-2026-09-16.key ubuntu@YOUR_SERVER_IP
```

On the server:

```bash
sudo apt-get update && sudo apt-get -y upgrade
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```

Connect again (so the `docker` group applies) and check: `docker compose version`.

## 4. Upload the project

On Windows:

```powershell
D:\theo-tech-system\scripts\pack-for-deploy.cmd
scp -i C:\Users\ISAIE\Downloads\ssh-key-2026-09-16.key $env:USERPROFILE\theo-tech.tgz ubuntu@YOUR_SERVER_IP:~
```

On the server:

```bash
tar -xzf theo-tech.tgz && cd theo-tech-system
cp .env.example .env
nano .env
```

Fill in `.env`:

| Variable | Value |
|---|---|
| `APP_DB_PASSWORD` | a long random password (run `openssl rand -hex 24` to make one) |
| `APP_REMEMBER_ME_KEY` | another long random string |
| `SITE_ADDRESS` | your domain (see step 6) for HTTPS, or `:80` to use the bare IP for now |
| `APP_COOKIE_SECURE` | `true` with a domain, **`false`** with the bare IP |

Save with Ctrl+O, Enter, Ctrl+X.

## 5. Start

```bash
docker compose up -d --build
```

The first build downloads Java and Maven dependencies and takes 5–10 minutes. Follow it with
`docker compose logs -f app` until you see `Started TheoTechApplication`. Then open
`http://YOUR_SERVER_IP` (or `https://your-domain`) and sign in with **admin / Admin@123**. You are asked
to set a new password immediately; do it. The shop starts empty (sample products are only inserted on a
developer machine): add your products, then add workers under **Workers**.

## 6. A domain and HTTPS (recommended)

Without HTTPS, passwords travel unencrypted and phones show "not secure". Two free ways to get a name:

- **DuckDNS** (<https://www.duckdns.org>): pick a name such as `theotech.duckdns.org` and point it at the server IP.
- Any domain you own: add an **A record** pointing at the server IP.

Then in `.env` set `SITE_ADDRESS=theotech.duckdns.org` and `APP_COOKIE_SECURE=true`, and run
`docker compose up -d`. Caddy obtains a Let's Encrypt certificate within a minute and renews it forever.

## 7. Backups

The `backup` container writes `backups/theo_tech-YYYY-MM-DD.sql.gz` every day and keeps 30 days.
Copy them off the server now and then (from Windows):

```powershell
scp -i C:\path\to\key ubuntu@YOUR_SERVER_IP:~/theo-tech-system/backups/*.gz D:\theo-tech-backups\
```

Restore one (this replaces the current data):

```bash
docker compose stop app
gunzip -c backups/theo_tech-2026-09-16.sql.gz | docker compose exec -T db psql -U theotech -d theo_tech
docker compose start app
```

## 8. Updating the application later

Upload a new `theo-tech.tgz` as in step 4, then on the server:

```bash
cd ~ && tar -xzf theo-tech.tgz && cd theo-tech-system && docker compose up -d --build
```

Your `.env`, the database and the backups are kept; only the application image is rebuilt. Database changes
ship as Flyway migrations and are applied automatically on start.

## 9. Everyday commands

| Task | Command (in `~/theo-tech-system`) |
|---|---|
| See what is running | `docker compose ps` |
| Application log | `docker compose logs -f app` |
| Restart everything | `docker compose restart` |
| Stop / start | `docker compose down` / `docker compose up -d` |
| Free disk space from old images | `docker image prune -f` |

## Troubleshooting

- **Site does not open at all** → step 2: both firewalls (Oracle security list *and* iptables) must allow 80/443.
- **Sign-in "works" but you land on the login page again** → `APP_COOKIE_SECURE=true` while using plain `http://IP`.
  Set it to `false` (or move to a domain with HTTPS) and `docker compose up -d`.
- **"Out of host capacity" when creating the VM** → Always Free ARM capacity is popular; retry later, at other hours,
  or with 1 OCPU / 6 GB.
- **Build fails with memory errors** → the 1 OCPU / 6 GB shape can be tight for Maven; retry with `docker compose build --no-cache`.
- **Is the database exposed?** No: it has no published port and is only reachable from the app and backup containers.
