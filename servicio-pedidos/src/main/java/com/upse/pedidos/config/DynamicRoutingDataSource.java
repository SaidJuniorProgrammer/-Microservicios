package com.upse.pedidos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
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

    @Scheduled(fixedRate = 2000)
    public void backgroundHealthCheck() {
        checkNodeHealth(DataSourceType.REPLICA_1);
        checkNodeHealth(DataSourceType.REPLICA_2);
    }

    public boolean checkNodeHealth(DataSourceType type) {
        DataSource ds = rawDataSources.get(type);
        if (ds == null) {
            replicaHealth.put(type, false);
            return false;
        }
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(1);
            stmt.execute("SELECT 1");
            replicaHealth.put(type, true);
            return true;
        } catch (Exception e) {
            replicaHealth.put(type, false);
            return false;
        }
    }

    public ConcurrentHashMap<DataSourceType, Boolean> getReplicaHealthStatus() {
        return replicaHealth;
    }
}
