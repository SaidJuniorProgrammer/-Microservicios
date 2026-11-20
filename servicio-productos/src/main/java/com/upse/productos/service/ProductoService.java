package com.upse.productos.service;

import com.upse.productos.model.Producto;
import com.upse.productos.repository.ProductoRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initData() {
        if (repository.count() == 0) {
            repository.save(new Producto("PROD-001", "Laptop Dell Latitude 5420", new BigDecimal("850.00"), 50, "Computadoras"));
            repository.save(new Producto("PROD-002", "Mouse Inalambrico Logitech MX", new BigDecimal("35.50"), 120, "Accesorios"));
            repository.save(new Producto("PROD-003", "Teclado Mecanico Redragon", new BigDecimal("55.00"), 80, "Accesorios"));
            repository.save(new Producto("PROD-004", "Monitor LG UltraWide 29", new BigDecimal("260.00"), 30, "Monitores"));
            repository.save(new Producto("PROD-005", "Audifonos Sony WH-1000XM4", new BigDecimal("299.99"), 45, "Audio"));
        }
    }

    public List<Producto> findAll() {
        return repository.findAll();
    }

    public Optional<Producto> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<Producto> findByCodigo(String codigo) {
        return repository.findByCodigo(codigo);
    }

    @Transactional
    public Producto create(Producto producto) {
        return repository.save(producto);
    }

    @Transactional
    public Producto descontarStock(String codigo, int cantidad) {
        Producto prod = repository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con codigo: " + codigo));

        if (prod.getStock() < cantidad) {
            throw new IllegalStateException("Stock insuficiente para " + prod.getNombre() + ". Disponible: " + prod.getStock() + ", Solicitado: " + cantidad);
        }

        prod.setStock(prod.getStock() - cantidad);
        return repository.save(prod);
    }
}
