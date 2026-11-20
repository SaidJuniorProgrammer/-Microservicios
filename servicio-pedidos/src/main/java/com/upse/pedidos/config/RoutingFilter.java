package com.upse.pedidos.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoutingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String criticalHeader = httpRequest.getHeader("X-Critical-Read");
                if ("true".equalsIgnoreCase(criticalHeader)) {
                    RoutingContext.setCriticalRead(true);
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (response instanceof HttpServletResponse httpResponse) {
                DataSourceType selected = RoutingContext.getLastSelected();
                if (selected != null) {
                    httpResponse.setHeader("X-DB-Node", selected.name());
                }
            }
            RoutingContext.clear();
        }
    }
}
