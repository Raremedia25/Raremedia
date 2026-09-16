# Hosting THEO TECH LTD on Render (free) with a Neon database (free)

Render runs the application from the Dockerfile; Neon provides PostgreSQL. Neither needs a card.
Two things to know about the free tier: the app **sleeps after 15 minutes without visitors** and takes
about a minute to wake on the next visit, and the Neon database pauses when idle (wakes in a second).

## 1. Database on Neon (3 minutes)

1. Go to <https://neon.tech> → **Sign up** (GitHub, Google or email).
2. **Create project**: name `theotech`, region **Europe (Frankfurt)**, PostgreSQL 17 or newer. Keep the default
   database `neondb` and role `neondb_owner`.
3. On the project page click **Connect** and choose **Java (JDBC)**. You get three things; copy them somewhere:
   - the host, e.g. `ep-cool-name-123456.eu-central-1.aws.neon.tech`
   - the user, `neondb_owner`
   - the password (click the eye icon to reveal it)

   The JDBC URL to use is `jdbc:postgresql://<host>/neondb?sslmode=require`.

## 2. Code on GitHub (2 minutes)

1. Go to <https://github.com> → sign up or sign in → **New repository**: name `theo-tech-system`,
   **Private**, no README. Click **Create repository**.
2. Send the repository URL (looks like `https://github.com/yourname/theo-tech-system`) to Claude, who pushes the
   code from this PC (a browser window will ask you to sign in to GitHub once).

## 3. Application on Render (5 minutes)

1. Go to <https://render.com> → **Sign up with GitHub** (this lets Render read the repository).
2. **New → Blueprint**. Pick the `theo-tech-system` repository. Render reads `render.yaml` and shows one
   service, `theotech`, plus the values it needs:
   - `APP_DB_URL` → `jdbc:postgresql://<host>/neondb?sslmode=require` (from step 1)
   - `APP_DB_USER` → `neondb_owner`
   - `APP_DB_PASSWORD` → the Neon password
3. Click **Apply**. The first build takes 8–12 minutes (Java and Maven are downloaded). When the service shows
   **Live**, open its URL, `https://theotech-xxxx.onrender.com`, and sign in with **admin / Admin@123**.
   You are asked to set a new password immediately.

## Afterwards

- **Updating**: every push to the `main` branch redeploys automatically.
- **Waking the app faster**: the free plan sleeps when idle. Opening the page once in the morning wakes it; the
  first load takes about a minute.
- **Backups**: Neon keeps point-in-time history on the free plan (a few days). To keep your own copy, use
  Neon's dashboard **Backups / Export**, or run `pg_dump` against the Neon URL from any PC with PostgreSQL tools.
- **Custom domain**: Render supports your own domain (Settings → Custom Domains) if you ever buy one;
  `duckdns.org` names cannot be pointed at Render because DuckDNS does not support CNAME records.
