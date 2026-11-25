package com.upse.pedidos.controller;

import com.upse.pedidos.config.DynamicRoutingDataSource;
import com.upse.pedidos.config.RoutingContext;
import com.upse.pedidos.model.Pedido;
import com.upse.pedidos.service.PedidoService;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;
    private final DynamicRoutingDataSource routingDataSource;

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${eureka.instance.instance-id:servicio-pedidos}")
    private String instanceId;

    public PedidoController(PedidoService service, DynamicRoutingDataSource routingDataSource) {
        this.service = service;
        this.routingDataSource = routingDataSource;
    }

    private HttpHeaders createInstanceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Instance-Id", instanceId);
        if (RoutingContext.getLastSelected() != null) {
            headers.add("X-DB-Node", RoutingContext.getLastSelected().name());
        }
        return headers;
    }

    public static class PedidoRequest {
        public String cliente;
        public String productoCodigo;
        public int cantidad;
    }

    @PostMapping
    public ResponseEntity<?> crearPedido(@RequestBody PedidoRequest req) {
        try {
            Pedido creado = service.crearPedido(req.cliente, req.productoCodigo, req.cantidad);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .headers(createInstanceHeaders())
                    .body(creado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage(), "instanceId", instanceId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage(), "instanceId", instanceId));
        } catch (FeignException e) {
            // servicio-productos caido o sin instancias registradas en Eureka
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", "servicio-productos no disponible: " + e.getMessage(), "instanceId", instanceId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", "Error interno: " + e.getMessage(), "instanceId", instanceId));
        }
    }

    @GetMapping
    public ResponseEntity<List<Pedido>> listarOrdinario() {
        // Se consulta primero para que la cabecera X-DB-Node refleje el nodo usado
        List<Pedido> pedidos = service.listarPedidosOrdinario();
        return ResponseEntity.ok()
                .headers(createInstanceHeaders())
                .body(pedidos);
    }

    @GetMapping("/{numeroPedido}")
    public ResponseEntity<?> obtenerOrdinario(@PathVariable String numeroPedido) {
        try {
            Pedido p = service.obtenerPorNumeroOrdinario(numeroPedido);
            return ResponseEntity.ok().headers(createInstanceHeaders()).body(p);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage(), "instanceId", instanceId));
        }
    }

    @GetMapping("/{numeroPedido}/critico")
    public ResponseEntity<?> obtenerCritico(@PathVariable String numeroPedido) {
        try {
            Pedido p = service.obtenerPorNumeroCritico(numeroPedido);
            return ResponseEntity.ok().headers(createInstanceHeaders()).body(p);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage(), "instanceId", instanceId));
        }
    }

    @GetMapping("/db/inspect")
    public ResponseEntity<Map<String, Object>> inspeccionarNodoDb() {
        Map<String, Object> info = service.inspeccionarNodoPostgres();
        info.put("instanceId", instanceId);
        return ResponseEntity.ok().headers(createInstanceHeaders()).body(info);
    }

    @GetMapping("/db/inspect-critico")
    public ResponseEntity<Map<String, Object>> inspeccionarNodoDbCritico() {
        Map<String, Object> info = service.inspeccionarNodoPostgresCritico();
        info.put("instanceId", instanceId);
        return ResponseEntity.ok().headers(createInstanceHeaders()).body(info);
    }

    @GetMapping("/db/health")
    public ResponseEntity<Map<String, Object>> healthReplicas() {
        Map<String, Object> health = new HashMap<>();
        health.put("replicas", routingDataSource.getReplicaHealthStatus());
        health.put("instanceId", instanceId);
        return ResponseEntity.ok().headers(createInstanceHeaders()).body(health);
    }

    @GetMapping("/info/instance")
    public ResponseEntity<Map<String, Object>> infoInstancia() {
        Map<String, Object> info = new HashMap<>();
        try {
            info.put("service", "servicio-pedidos");
            info.put("instanceId", instanceId);
            info.put("hostname", InetAddress.getLocalHost().getHostName());
            info.put("port", serverPort);
            info.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return ResponseEntity.ok().headers(createInstanceHeaders()).body(info);
    }
}
