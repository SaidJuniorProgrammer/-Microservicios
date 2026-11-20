package com.upse.pedidos.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.primary.url:jdbc:postgresql://postgres-primary:5432/pedidos_db}")
    private String primaryUrl;

    @Value("${spring.datasource.replica1.url:jdbc:postgresql://postgres-replica-1:5432/pedidos_db}")
    private String replica1Url;

    @Value("${spring.datasource.replica2.url:jdbc:postgresql://postgres-replica-2:5432/pedidos_db}")
    private String replica2Url;

    @Value("${spring.datasource.username:postgres}")
    private String username;

    @Value("${spring.datasource.password:postgres_password}")
    private String password;

    private HikariDataSource buildHikariDataSource(String poolName, String url, boolean isReadOnly) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(500);
        config.setReadOnly(isReadOnly);
        return new HikariDataSource(config);
    }

    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        return buildHikariDataSource("HikariPool-Primary", primaryUrl, false);
    }

    @Bean(name = "replica1DataSource")
    public DataSource replica1DataSource() {
        return buildHikariDataSource("HikariPool-Replica1", replica1Url, true);
    }

    @Bean(name = "replica2DataSource")
    public DataSource replica2DataSource() {
        return buildHikariDataSource("HikariPool-Replica2", replica2Url, true);
    }

    @Bean(name = "routingDataSource")
    public DynamicRoutingDataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replica1DataSource") DataSource replica1,
            @Qualifier("replica2DataSource") DataSource replica2) {

        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primary);
        targetDataSources.put(DataSourceType.REPLICA_1, replica1);
        targetDataSources.put(DataSourceType.REPLICA_2, replica2);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primary);

        routingDataSource.registerRawDataSource(DataSourceType.PRIMARY, primary);
        routingDataSource.registerRawDataSource(DataSourceType.REPLICA_1, replica1);
        routingDataSource.registerRawDataSource(DataSourceType.REPLICA_2, replica2);

        return routingDataSource;
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("routingDataSource") DynamicRoutingDataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
