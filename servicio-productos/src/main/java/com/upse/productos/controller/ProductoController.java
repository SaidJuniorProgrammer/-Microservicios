package com.upse.productos.controller;

import com.upse.productos.model.Producto;
import com.upse.productos.service.ProductoService;
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
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService service;

    @Value("${server.port:8082}")
    private String serverPort;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    private HttpHeaders createInstanceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            headers.add("X-Instance-Id", InetAddress.getLocalHost().getHostName() + ":" + serverPort);
        } catch (Exception e) {
            headers.add("X-Instance-Id", "servicio-productos:" + serverPort);
        }
        return headers;
    }

    @GetMapping
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok()
                .headers(createInstanceHeaders())
                .body(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerPorId(@PathVariable Long id) {
        return service.findById(id)
                .map(p -> ResponseEntity.ok().headers(createInstanceHeaders()).body(p))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<Producto> obtenerPorCodigo(@PathVariable String codigo) {
        return service.findByCodigo(codigo)
                .map(p -> ResponseEntity.ok().headers(createInstanceHeaders()).body(p))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(createInstanceHeaders())
                .body(service.create(producto));
    }

    @PutMapping("/{codigo}/descontar-stock")
    public ResponseEntity<?> descontarStock(@PathVariable String codigo, @RequestParam int cantidad) {
        try {
            Producto actualizado = service.descontarStock(codigo, cantidad);
            return ResponseEntity.ok().headers(createInstanceHeaders()).body(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(createInstanceHeaders())
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/info/instance")
    public ResponseEntity<Map<String, Object>> infoInstancia() {
        Map<String, Object> info = new HashMap<>();
        try {
            info.put("service", "servicio-productos");
            info.put("hostname", InetAddress.getLocalHost().getHostName());
            info.put("port", serverPort);
            info.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return ResponseEntity.ok().headers(createInstanceHeaders()).body(info);
    }
}
