package com.upse.pedidos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicRoutingDataSource.class);

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<DataSourceType, Boolean> replicaHealth = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DataSourceType, DataSource> rawDataSources = new ConcurrentHashMap<>();

    public DynamicRoutingDataSource() {
        replicaHealth.put(DataSourceType.REPLICA_1, true);
        replicaHealth.put(DataSourceType.REPLICA_2, true);
    }

    public void registerRawDataSource(DataSourceType type, DataSource dataSource) {
        rawDataSources.put(type, dataSource);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        boolean isCritical = RoutingContext.isCriticalRead();

        // Si es escritura o lectura critica -> NODO PRIMARIO
        if (!isReadOnly || isCritical) {
            log.info("[ROUTING-DB] Dirigiendo a PRIMARY (ReadOnly={}, Critical={})", isReadOnly, isCritical);
            RoutingContext.setLastSelected(DataSourceType.PRIMARY);
            return DataSourceType.PRIMARY;
        }

        // Consultas ordinarias: balanceo entre replicas saludables
        DataSourceType selected = selectHealthyReplica();
        RoutingContext.setLastSelected(selected);
        log.info("[ROUTING-DB] Dirigiendo lectura ordinaria a: {}", selected);
        return selected;
    }

    private synchronized DataSourceType selectHealthyReplica() {
        boolean r1Healthy = Boolean.TRUE.equals(replicaHealth.get(DataSourceType.REPLICA_1));
        boolean r2Healthy = Boolean.TRUE.equals(replicaHealth.get(DataSourceType.REPLICA_2));

        // Ambas réplicas saludables: Round-Robin
        if (r1Healthy && r2Healthy) {
            int count = roundRobinCounter.getAndIncrement();
            if (count % 2 == 0) {
                return DataSourceType.REPLICA_1;
            } else {
                return DataSourceType.REPLICA_2;
            }
        }

        // Solo Réplica 1 saludable
        if (r1Healthy) {
            return DataSourceType.REPLICA_1;
        }

        // Solo Réplica 2 saludable
        if (r2Healthy) {
            return DataSourceType.REPLICA_2;
        }

        // Si ambas réplicas cayeron, failover a Primario para no interrumpir el servicio
        log.warn("[ROUTING-DB] Ninguna réplica disponible en cache. Failover temporal a PRIMARY.");
        return DataSourceType.PRIMARY;
    }

    /**
     * Fallback inmediato: si la replica elegida no entrega conexion (caida aun no
     * detectada por el chequeo periodico), se marca como no saludable y se reintenta
     * en la otra replica sana o, en ultimo caso, en el PRIMARY. Asi la consulta en
     * curso no falla durante la ventana de deteccion.
     */
    @Override
    public Connection getConnection() throws SQLException {
        DataSourceType key = (DataSourceType) determineCurrentLookupKey();
        try {
            return rawDataSources.get(key).getConnection();
        } catch (SQLException e) {
            if (key == DataSourceType.PRIMARY) {
                throw e;
            }
            replicaHealth.put(key, false);
            DataSourceType fallback = selectHealthyReplica();
            log.warn("[ROUTING-DB] {} no disponible ({}). Reintentando la consulta en {}", key, e.getMessage(), fallback);
            RoutingContext.setLastSelected(fallback);
            return rawDataSources.get(fallback).getConnection();
        }
    }

    /** Excluye una replica de inmediato; el chequeo periodico la reincorporara cuando vuelva a ser apta. */
    public void markUnavailable(DataSourceType type) {
        if (type != DataSourceType.PRIMARY) {
            replicaHealth.put(type, false);
        }
    }

    @Scheduled(fixedRate = 2000)
    public void backgroundHealthCheck() {
        checkNodeHealth(DataSourceType.REPLICA_1);
        checkNodeHealth(DataSourceType.REPLICA_2);
    }

    /**
     * Criterio de aptitud de una replica:
     *  - Para PERMANECER en la rotacion: responde y esta en modo standby (pg_is_in_recovery).
     *  - Para REINCORPORARSE tras una caida: ademas su receptor WAL debe estar en estado
     *    'streaming' con el primario, es decir, conectada y aplicando cambios.
     * Asi una replica recien levantada no recibe consultas antes de sincronizarse, y una
     * caida del primario no expulsa a las replicas que siguen atendiendo lecturas.
     */
    public boolean checkNodeHealth(DataSourceType type) {
        DataSource ds = rawDataSources.get(type);
        if (ds == null) {
            replicaHealth.put(type, false);
            return false;
        }
        boolean previous = Boolean.TRUE.equals(replicaHealth.get(type));
        boolean healthy;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(1);
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT pg_is_in_recovery(), EXISTS (SELECT 1 FROM pg_stat_wal_receiver WHERE status = 'streaming')")) {
                rs.next();
                boolean inRecovery = rs.getBoolean(1);
                boolean streaming = rs.getBoolean(2);
                healthy = inRecovery && (previous || streaming);
            }
        } catch (Exception e) {
            healthy = false;
        }
        replicaHealth.put(type, healthy);
        if (healthy != previous) {
            log.warn("[ROUTING-DB] {} cambia de estado: {} -> {}", type,
                    previous ? "SALUDABLE" : "NO DISPONIBLE", healthy ? "SALUDABLE (reincorporada)" : "NO DISPONIBLE (excluida)");
        }
        return healthy;
    }

    public ConcurrentHashMap<DataSourceType, Boolean> getReplicaHealthStatus() {
        return replicaHealth;
    }
}
