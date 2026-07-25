#!/bin/sh
set -e

if [ -n "$DATABASE_URL" ]; then
  URL="$DATABASE_URL"
  case "$URL" in
    jdbc:*) ;;
    postgres://*) URL="jdbc:postgresql://${URL#postgres://}" ;;
    postgresql://*) URL="jdbc:postgresql://${URL#postgresql://}" ;;
  esac
  # Strip credentials from URL; Spring uses PGUSER/PGPASSWORD
  STRIPPED=$(printf '%s' "$URL" | sed -E 's#jdbc:postgresql://([^:/@]+):([^@]+)@#jdbc:postgresql://#')
  export SPRING_DATASOURCE_URL="${STRIPPED}"
  echo "[start] SPRING_DATASOURCE_URL set (host from DATABASE_URL)"
fi

exec java -XX:+UseContainerSupport -Xmx512m -jar /app/app.jar
