package com.upse.pedidos.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

/**
 * Reintenta UNA vez una lectura ordinaria (readOnly, no critica) cuando la replica
 * que la atendia se cae a mitad de la consulta (conexion cerrada o terminada por el
 * servidor). La replica se marca como no disponible y el reintento, en una
 * transaccion nueva, se enruta a la otra replica sana o al PRIMARY.
 *
 * Solo aplica a lecturas: repetir una consulta de solo lectura no tiene efectos.
 * Se ejecuta fuera de la transaccion (antes del TransactionInterceptor) para que el
 * reintento obtenga una conexion nueva.
 */
@Aspect
@Component
public class ReplicaReadRetryAspect implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReplicaReadRetryAspect.class);

    private final DynamicRoutingDataSource routingDataSource;

    public ReplicaReadRetryAspect(DynamicRoutingDataSource routingDataSource) {
        this.routingDataSource = routingDataSource;
    }

    @Around("@annotation(transactional) && !@annotation(com.upse.pedidos.config.CriticalRead)")
    public Object retryOnReplicaFailure(ProceedingJoinPoint joinPoint, Transactional transactional) throws Throwable {
        if (!transactional.readOnly()) {
            return joinPoint.proceed();
        }
        // Como maximo: intento original + otra replica + PRIMARY
        for (int attempt = 1; ; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (RuntimeException e) {
                DataSourceType failed = RoutingContext.getLastSelected();
                if (attempt >= MAX_ATTEMPTS || failed == null || failed == DataSourceType.PRIMARY
                        || !isConnectionFailure(e)) {
                    throw e;
                }
                routingDataSource.markUnavailable(failed);
                log.warn("[ROUTING-DB] Conexion con {} perdida durante la consulta ({}). Reintentando la lectura en otro nodo",
                        failed, rootMessage(e));
            }
        }
    }

    private static final int MAX_ATTEMPTS = 3;

    /**
     * Detecta perdida de conexion: SQLState clase 08 (error de conexion) o 57P0x
     * (servidor apagandose / conexion terminada). Si la conexion muere a mitad de la
     * consulta, el error que llega aqui suele ser el del cierre de la transaccion
     * ("connection is closed", sin SQLState), por eso tambien se revisan los mensajes.
     */
    private boolean isConnectionFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P0"))) {
                return true;
            }
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (msg.contains("connection is closed") || msg.contains("connection has been closed")
                    || msg.contains("terminating connection") || msg.contains("i/o error")) {
                return true;
            }
        }
        return false;
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    @Override
    public int getOrder() {
        // Despues de CriticalReadAspect (HIGHEST_PRECEDENCE) y antes del TransactionInterceptor
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
