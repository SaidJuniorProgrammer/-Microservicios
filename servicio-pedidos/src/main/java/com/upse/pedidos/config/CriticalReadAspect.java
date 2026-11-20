package com.upse.pedidos.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CriticalReadAspect implements Ordered {

    @Around("@annotation(com.upse.pedidos.config.CriticalRead) || @within(com.upse.pedidos.config.CriticalRead)")
    public Object manageCriticalRead(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean previous = RoutingContext.isCriticalRead();
        try {
            RoutingContext.setCriticalRead(true);
            return joinPoint.proceed();
        } finally {
            RoutingContext.setCriticalRead(previous);
        }
    }

    @Override
    public int getOrder() {
        // Ejecutar antes de que comience @Transactional
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
