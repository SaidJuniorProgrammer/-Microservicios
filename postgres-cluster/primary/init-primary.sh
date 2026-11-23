#!/bin/bash
set -e

REPL_USER="${POSTGRES_REPLICATION_USER:-replicator}"
REPL_PASSWORD="${POSTGRES_REPLICATION_PASSWORD:-replicator_pass}"

echo "=== Configurando usuario de replicacion y bases de datos en Primario ==="

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER "$REPL_USER" WITH REPLICATION ENCRYPTED PASSWORD '$REPL_PASSWORD';
    CREATE DATABASE pedidos_db;
    CREATE DATABASE productos_db;
    GRANT ALL PRIVILEGES ON DATABASE pedidos_db TO postgres;
    GRANT ALL PRIVILEGES ON DATABASE productos_db TO postgres;
EOSQL

echo "host replication $REPL_USER 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
echo "host all all 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"

pg_ctl reload -D "$PGDATA"
echo "=== Primario configurado exitosamente ==="
