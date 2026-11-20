#!/bin/bash
set -e

echo "=== Esperando que el primario este listo en postgres-primary:5432 ==="
until PGPASSWORD=replicator_pass psql -h postgres-primary -p 5432 -U replicator -d postgres -c '\q' 2>/dev/null; do
  echo "Esperando nodo primario..."
  sleep 2
done
echo "=== Primario disponible! ==="

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "=== Clonando base de datos desde postgres-primary ==="
  rm -rf "${PGDATA:?}"/*
  PGPASSWORD=replicator_pass pg_basebackup -h postgres-primary -p 5432 -U replicator -D "$PGDATA" -Fp -Xs -P -R
  chown -R postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
  echo "=== Base de datos clonada exitosamente ==="
fi

echo "=== Iniciando PostgreSQL Standby en replica ==="
exec su-exec postgres postgres -c config_file=/etc/postgresql/postgresql.conf
