package com.upse.pedidos.repository;

import com.upse.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByNumeroPedido(String numeroPedido);
    List<Pedido> findByCliente(String cliente);

    @Query(value = "SELECT inet_server_addr() AS ip, inet_server_port() AS port, pg_is_in_recovery() AS is_replica, current_database() AS db_name", nativeQuery = true)
    Map<String, Object> getPostgresNodeInfo();
}
