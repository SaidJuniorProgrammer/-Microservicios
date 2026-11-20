package com.upse.pedidos.client;

import com.upse.pedidos.client.dto.ProductoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "servicio-productos")
public interface ProductoClient {

    @GetMapping("/api/productos/codigo/{codigo}")
    ProductoDto obtenerPorCodigo(@PathVariable("codigo") String codigo);

    @PutMapping("/api/productos/{codigo}/descontar-stock")
    ProductoDto descontarStock(@PathVariable("codigo") String codigo, @RequestParam("cantidad") int cantidad);
}
