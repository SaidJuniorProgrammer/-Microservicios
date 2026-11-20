package com.upse.pedidos.service;

import com.upse.pedidos.client.ProductoClient;
import com.upse.pedidos.client.dto.ProductoDto;
import com.upse.pedidos.config.CriticalRead;
import com.upse.pedidos.config.RoutingContext;
import com.upse.pedidos.model.Pedido;
import com.upse.pedidos.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository repository;
    private final ProductoClient productoClient;

    @Value("${server.port:8081}")
    private String serverPort;

    public PedidoService(PedidoRepository repository, ProductoClient productoClient) {
        this.repository = repository;
        this.productoClient = productoClient;
    }

    private String getHostIdentifier() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + serverPort;
        } catch (Exception e) {
            return "servicio-pedidos:" + serverPort;
        }
    }

    @Transactional
    public Pedido crearPedido(String cliente, String productoCodigo, int cantidad) {
        log.info("[PEDIDO] Procesando creacion de pedido: cliente={}, producto={}, cantidad={}", cliente, productoCodigo, cantidad);

        // 1. Comunicacion entre microservicios (Feign) mediante descubrimiento Eureka
        ProductoDto producto = productoClient.obtenerPorCodigo(productoCodigo);
        if (producto == null) {
            throw new IllegalArgumentException("El producto con codigo " + productoCodigo + " no existe.");
        }

        if (producto.getStock() < cantidad) {
            throw new IllegalStateException("Stock insuficiente para el producto: " + producto.getNombre() + 
                    " (Disponible: " + producto.getStock() + ", Solicitado: " + cantidad + ")");
        }

        // 2. Descontar stock en servicio-productos mediante Feign
        productoClient.descontarStock(productoCodigo, cantidad);

        // 3. Crear y persistir el pedido en postgres-primary
        BigDecimal precioTotal = producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        Pedido pedido = new Pedido();
        pedido.setNumeroPedido("PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pedido.setCliente(cliente);
        pedido.setProductoCodigo(productoCodigo);
        pedido.setProductoNombre(producto.getNombre());
        pedido.setCantidad(cantidad);
        pedido.setPrecioUnitario(producto.getPrecio());
        pedido.setPrecioTotal(precioTotal);
        pedido.setEstado("CONFIRMADO");
        pedido.setFechaCreacion(LocalDateTime.now());
        pedido.setInstanciaProcesamiento(getHostIdentifier());

        Pedido guardado = repository.save(pedido);
        log.info("[PEDIDO] Pedido guardado exitosamente con ID {} en nodo PRIMARY", guardado.getId());
        return guardado;
    }

    // Consulta ordinaria (balanceada entre réplicas de lectura)
    @Transactional(readOnly = true)
    public List<Pedido> listarPedidosOrdinario() {
        log.info("[PEDIDO] Listando pedidos mediante consulta ordinaria (balanceada entre réplicas)");
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Pedido obtenerPorNumeroOrdinario(String numeroPedido) {
        log.info("[PEDIDO] Consultando pedido {} mediante réplica ordinaria", numeroPedido);
        return repository.findByNumeroPedido(numeroPedido)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + numeroPedido));
    }

    // Consulta crítica: Forzada al Primario (evita inconsistencias por retraso de replicación WAL)
    @CriticalRead
    @Transactional(readOnly = true)
    public Pedido obtenerPorNumeroCritico(String numeroPedido) {
        log.info("[PEDIDO] Consultando pedido {} mediante LECTURA CRITICA (forzada a PRIMARY)", numeroPedido);
        return repository.findByNumeroPedido(numeroPedido)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + numeroPedido));
    }

    // Inspección de nodo PostgreSQL ejecutado
    @Transactional(readOnly = true)
    public Map<String, Object> inspeccionarNodoPostgres() {
        Map<String, Object> nodeInfo = new HashMap<>();
        try {
            Map<String, Object> raw = repository.getPostgresNodeInfo();
            nodeInfo.putAll(raw);
            nodeInfo.put("routingSelection", RoutingContext.getLastSelected());
            nodeInfo.put("instanceProcessing", getHostIdentifier());
        } catch (Exception e) {
            nodeInfo.put("error", e.getMessage());
        }
        return nodeInfo;
    }

    @CriticalRead
    @Transactional(readOnly = true)
    public Map<String, Object> inspeccionarNodoPostgresCritico() {
        Map<String, Object> nodeInfo = new HashMap<>();
        try {
            Map<String, Object> raw = repository.getPostgresNodeInfo();
            nodeInfo.putAll(raw);
            nodeInfo.put("routingSelection", RoutingContext.getLastSelected());
            nodeInfo.put("instanceProcessing", getHostIdentifier());
        } catch (Exception e) {
            nodeInfo.put("error", e.getMessage());
        }
        return nodeInfo;
    }
}
