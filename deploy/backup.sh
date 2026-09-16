#!/bin/sh
# Daily PostgreSQL backup for THEO TECH LTD. Runs inside the "backup" container.
# Writes /backups/theo_tech-YYYY-MM-DD.sql.gz once a day (shortly after start-up, then every 24 hours)
# and deletes files older than BACKUP_KEEP_DAYS (default 30).
set -eu
KEEP="${BACKUP_KEEP_DAYS:-30}"
mkdir -p /backups
while true; do
  STAMP="$(date +%F)"
  FILE="/backups/theo_tech-${STAMP}.sql.gz"
  if [ ! -f "$FILE" ]; then
    if pg_dump --no-owner --no-privileges | gzip > "${FILE}.tmp"; then
      mv "${FILE}.tmp" "$FILE"
      echo "$(date -Is) backup written: $FILE ($(du -h "$FILE" | cut -f1))"
    else
      rm -f "${FILE}.tmp"
      echo "$(date -Is) backup FAILED" >&2
    fi
    find /backups -name 'theo_tech-*.sql.gz' -mtime +"$KEEP" -delete
  fi
  sleep 86400
done
