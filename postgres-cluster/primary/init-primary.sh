#!/bin/bash
set -e

echo "=== Configurando usuario de replicacion y bases de datos en Primario ==="

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator_pass';
    CREATE DATABASE pedidos_db;
    CREATE DATABASE productos_db;
    GRANT ALL PRIVILEGES ON DATABASE pedidos_db TO postgres;
    GRANT ALL PRIVILEGES ON DATABASE productos_db TO postgres;
EOSQL

echo "host replication replicator 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
echo "host all all 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"

pg_ctl reload -D "$PGDATA"
echo "=== Primario configurado exitosamente ==="
