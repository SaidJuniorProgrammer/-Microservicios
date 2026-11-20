# Informe de Pruebas Experimentales (P01 – P08)
## Validación Experimental de Arquitectura Distribuida y Persistencia Replicada

**Universidad Estatal Península de Santa Elena (UPSE)**  
**Asignatura:** Aplicaciones Distribuidas  
**Integrantes:**
* **Said Pinto**
* **Oscar Gonabay**
* **Jean Stalin**

---

### Introducción y Metodología de Validación
Para verificar el cumplimiento de los requerimientos no funcionales de alta disponibilidad, descubrimiento dinámico, balanceo de carga y continuidad operativa ante fallos parciales, se diseñaron y ejecutaron 8 pruebas experimentales obligatorias conforme a la guía de actividades.

---

### P01 · Descubrimiento Dinámico de Servicios

* **Objetivo:** Verificar que las instancias de los microservicios se registren automáticamente en Eureka Server y puedan ser resueltas mediante su nombre lógico sin requerir configuración estática de direcciones IP.
* **Procedimiento:**
  1. Levantar la infraestructura mediante `docker compose up -d`.
  2. Consultar la API REST de Eureka en `http://localhost:8761/eureka/apps` con cabecera `Accept: application/json`.
  3. Ejecutar el script `.\scripts\p01_descubrimiento.ps1`.
* **Resultado Esperado:** Registro exitoso de `API-GATEWAY`, `SERVICIO-PRODUCTOS` y 2 instancias activas de `SERVICIO-PEDIDOS` en estado `UP`.
* **Resultado Obtenido:**
  ```text
  Microservicios registrados en Eureka Server:
  * Aplicacion: API-GATEWAY
    - Instancia ID : api-gateway:8080 | Estado: UP
  * Aplicacion: SERVICIO-PRODUCTOS
    - Instancia ID : servicio-productos:8082 | Estado: UP
  * Aplicacion: SERVICIO-PEDIDOS
    - Instancia ID : servicio-pedidos-instancia-1 | Estado: UP
    - Instancia ID : servicio-pedidos-instancia-2 | Estado: UP
  ```
* **Análisis Técnico:** El protocolo de latidos (*heartbeat*) de Eureka valida la presencia y salud de las instancias. La configuración de `eureka.instance.prefer-ip-address=true` y un ciclo de renovación ágil (3s) garantiza que el registro se mantenga sincronizado.

---

### P02 · Balanceo de Carga HTTP (100 Peticiones)

* **Objetivo:** Demostrar que Spring Cloud Gateway, en conjunto con Spring Cloud LoadBalancer, distribuye las peticiones HTTP entrantes de forma equitativa entre las dos instancias de `servicio-pedidos`.
* **Procedimiento:**
  1. Ejecutar el script `.\scripts\p02_balanceo_http.ps1`.
  2. Enviar 100 solicitudes consecutivas al endpoint `http://localhost:8080/api/pedidos/info/instance`.
  3. Contabilizar la cabecera `X-Instance-Id` y el campo `instanceId` en el cuerpo JSON de cada respuesta.
* **Resultado Esperado:** 100 respuestas exitosas (código HTTP 200) con una distribución aproximada del 50% entre `servicio-pedidos-instancia-1` y `servicio-pedidos-instancia-2`.
* **Resultado Obtenido:**
  ```text
  Total peticiones enviadas: 100
  Total peticiones exitosas: 100 (HTTP 200)
  Total peticiones con error: 0
  Distribución observada:
  * servicio-pedidos-instancia-1 : 50 peticiones (50.0 %)
  * servicio-pedidos-instancia-2 : 50 peticiones (50.0 %)
  ```
* **Análisis Técnico:** El algoritmo Round-Robin predeterminado de Spring Cloud LoadBalancer alterna perfectamente las peticiones en capa 7 (HTTP), desacoplando al cliente de la topología interna de la red de contenedores.

---

### P03 · Tolerancia a Fallos de Instancia de Microservicio

* **Objetivo:** Comprobar la resiliencia del sistema simulando la detención inesperada de una de las dos instancias del microservicio de pedidos, verificando la continuidad de servicio a través de la instancia superviviente.
* **Procedimiento:**
  1. Ejecutar `.\scripts\p03_fallo_instancia.ps1`.
  2. Detener deliberadamente el contenedor `servicio-pedidos-1` mediante `docker stop servicio-pedidos-1`.
  3. Enviar ráfaga de solicitudes a través del Gateway.
  4. Reiniciar el contenedor con `docker start servicio-pedidos-1` y verificar el reintegro.
* **Resultado Esperado:** Durante la interrupción, el Gateway redirige el 100% de las solicitudes a `servicio-pedidos-instancia-2` sin errores 503 permanentes. Al reiniciar, la instancia 1 vuelve a recibir tráfico.
* **Resultado Obtenido:**
  ```text
  1. Ambas instancias respondiendo alternadamente.
  2. Deteniendo 'servicio-pedidos-1'...
  3. Solicitudes durante el fallo:
     - Solicitud 1 -> servicio-pedidos-instancia-2 [HTTP 200]
     - Solicitud 2 -> servicio-pedidos-instancia-2 [HTTP 200]
     - Solicitud 3 -> servicio-pedidos-instancia-2 [HTTP 200]
  4. Iniciando 'servicio-pedidos-1'...
  5. Ambas instancias vuelven a alternar solicitudes tras la reincorporación en Eureka.
  ```
* **Análisis Técnico:** Gracias a que se desactivó el modo de autopreservación de Eureka (`enable-self-preservation: false`) y se redujo el `eviction-interval-timer-in-ms` a 3 segundos, el registro deslistó la instancia caída en pocos segundos, evitando que el balanceador continuara enviándole solicitudes.

---

### P04 · Comunicación Síncrona entre Microservicios (OpenFeign)

* **Objetivo:** Ejecutar una operación de negocio real que involucre la interacción entre `servicio-pedidos` y `servicio-productos` mediante resolución de nombre lógico en Eureka.
* **Procedimiento:**
  1. Consultar el stock actual de `PROD-001` en `servicio-productos`.
  2. Realizar un `POST /api/pedidos` para comprar 2 unidades de `PROD-001`.
  3. `servicio-pedidos` invoca mediante `@FeignClient(name = "servicio-productos")` los endpoints de verificación y descuento de inventario.
  4. Verificar la actualización del stock y la creación del pedido con estado `CONFIRMADO`.
  5. Probar un intento de compra con cantidad excesiva (9999 unidades) para comprobar el manejo de excepciones de negocio.
* **Resultado Esperado:** Pedido creado exitosamente, stock descontado de 50 a 48. La solicitud excesiva debe ser rechazada con código HTTP 400 (Bad Request).
* **Resultado Obtenido:**
  ```json
  POST http://localhost:8080/api/pedidos
  {
    "cliente": "Oscar Gonabay",
    "productoCodigo": "PROD-001",
    "cantidad": 2
  }

  Respuesta HTTP 201 Created:
  {
    "id": 1,
    "numeroPedido": "PED-A3B91C2D",
    "cliente": "Oscar Gonabay",
    "productoCodigo": "PROD-001",
    "productoNombre": "Laptop Dell Latitude 5420",
    "cantidad": 2,
    "precioUnitario": 850.00,
    "precioTotal": 1700.00,
    "estado": "CONFIRMADO",
    "instanciaProcesamiento": "servicio-pedidos-instancia-1"
  }
  ```
  *Consulta posterior de stock:* `stock: 48`.
  *Prueba de error:* Solicitud con 9999 unidades retornó `400 Bad Request: "Stock insuficiente..."`.
* **Análisis Técnico:** Se demostró la comunicación inter-servicio síncrona sin acoplamiento de direcciones IP físicas, respetando el principio de bases de datos independientes por servicio.

---

### P05 · Enrutamiento de Persistencia (Primary vs Replicas)

* **Objetivo:** Demostrar que las operaciones de modificación (INSERT/UPDATE) se ejecutan exclusivamente en el nodo `postgres-primary`, mientras que las consultas de lectura ordinarias se distribuyen entre `postgres-replica-1` y `postgres-replica-2`.
* **Procedimiento:**
  1. Ejecutar `.\scripts\p05_enrutamiento_db.ps1`.
  2. Enviar una petición de creación de pedido (escritura) y verificar la cabecera `X-DB-Node`.
  3. Enviar 8 peticiones al endpoint `GET /api/pedidos/db/inspect` que ejecuta la función SQL nativa `SELECT inet_server_addr(), inet_server_port(), pg_is_in_recovery()`.
* **Resultado Esperado:** 
  * Escritura: `X-DB-Node = PRIMARY`, `pg_is_in_recovery = false`.
  * Lecturas: Alternancia equitativa entre `REPLICA_1` y `REPLICA_2`, con `pg_is_in_recovery = true`.
* **Resultado Obtenido:**
  ```text
  1. POST /api/pedidos -> HTTP 201 | Cabecera X-DB-Node: PRIMARY
  2. Consultas Ordinarias (GET /api/pedidos/db/inspect):
     * Lectura 1 -> Nodo: REPLICA_1 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.3
     * Lectura 2 -> Nodo: REPLICA_2 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.4
     * Lectura 3 -> Nodo: REPLICA_1 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.3
     * Lectura 4 -> Nodo: REPLICA_2 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.4
     * Lectura 5 -> Nodo: REPLICA_1 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.3
     * Lectura 6 -> Nodo: REPLICA_2 | pg_is_in_recovery: true  | IP Contenedor: 172.28.0.4
  ```
* **Análisis Técnico:** La clase `DynamicRoutingDataSource` evalúa si la transacción activa es de solo lectura (`isCurrentTransactionReadOnly()`). Gracias al uso de `LazyConnectionDataSourceProxy`, la conexión física a la base de datos se adquiere únicamente al momento de ejecutar la sentencia SQL, permitiendo seleccionar dinámicamente el DataSource correspondiente.

---

### P06 · Tolerancia a Fallos de Réplica PostgreSQL

* **Objetivo:** Comprobar que ante la indisponibilidad súbita de una réplica de base de datos, las consultas de lectura no se interrumpen y son absorbidas por la réplica restante.
* **Procedimiento:**
  1. Ejecutar `.\scripts\p06_fallo_replica.ps1`.
  2. Detener el contenedor `postgres-replica-1` mediante `docker stop postgres-replica-1`.
  3. Ejecutar 6 solicitudes consecutivas de lectura ordinaria.
* **Resultado Esperado:** Cero errores de conexión percibidos por el cliente; el 100% de las consultas se atienden a través de `REPLICA_2`.
* **Resultado Obtenido:**
  ```text
  Deteniendo 'postgres-replica-1'...
  * Consulta 1 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  * Consulta 2 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  * Consulta 3 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  * Consulta 4 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  * Consulta 5 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  * Consulta 6 -> Nodo: REPLICA_2 | Estado: EXITOSO (HTTP 200) | IP: 172.28.0.4
  ```
* **Análisis Técnico:** El método `selectHealthyReplica()` valida la conectividad del pool de HikariCP con un timeout corto (1 segundo). Al detectar que la Réplica 1 no responde, la excluye dinámicamente del balanceo y redirige el flujo a la Réplica 2. En caso extremo de que ambas réplicas fallen, el sistema incluye un mecanismo de failover hacia el Primario para evitar la indisponibilidad total.

---

### P07 · Consistencia de Lectura Crítica (Read-After-Write)

* **Objetivo:** Demostrar el mecanismo de consistencia inmediata para operaciones de lectura críticas, forzando la consulta al nodo Primario para evitar inconsistencias por retrasos de replicación física WAL (*replication lag*).
* **Procedimiento:**
  1. Ejecutar `.\scripts\p07_consistencia_lectura.ps1`.
  2. Crear un nuevo pedido de alta prioridad.
  3. Realizar una lectura inmediata utilizando el endpoint crítico `GET /api/pedidos/{numeroPedido}/critico` o la cabecera `X-Critical-Read: true`.
  4. Consultar el estado del nodo con `GET /api/pedidos/db/inspect-critico`.
* **Resultado Esperado:** La consulta se ejecuta en el `PRIMARY`, retornando `pg_is_in_recovery = false`.
* **Resultado Obtenido:**
  ```text
  1. Pedido de alta prioridad creado: PED-7E2A91D0
  2. Solicitud de LECTURA CRITICA (GET /api/pedidos/PED-7E2A91D0/critico):
     - Codigo HTTP: 200 OK
     - Cabecera X-DB-Node: PRIMARY
  3. Inspección del nodo bajo contexto crítico:
     - Nodo Reportado: PRIMARY
     - pg_is_in_recovery: false (Nodo Maestro)
     - IP Servidor DB: 172.28.0.2 (postgres-primary)
  ```
* **Análisis Técnico:** Se implementó el aspecto AOP `CriticalReadAspect` y el contexto `RoutingContext` basado en `ThreadLocal`. Cuando un método posee la anotación `@CriticalRead` o la petición incluye la cabecera correspondiente, el enrutador anula la directiva de réplica y canaliza la consulta directamente al nodo Primario, garantizando consistencia fuerte en lecturas sensibles.

---

### P08 · Recuperación y Reincorporación de Réplica

* **Objetivo:** Reiniciar la réplica previamente detenida (`postgres-replica-1`), verificar la sincronización automática de los registros WAL y confirmar su reincorporación activa al esquema de balanceo de consultas.
* **Procedimiento:**
  1. Ejecutar `.\scripts\p08_recuperacion_replica.ps1`.
  2. Iniciar el contenedor con `docker start postgres-replica-1`.
  3. Esperar 5 segundos para que el proceso de *Streaming Replication* se enlace con el Primario.
  4. Realizar 10 consultas de lectura ordinaria y verificar la reaparición de `REPLICA_1` en la rotación.
* **Resultado Esperado:** La réplica recuperada vuelve a responder solicitudes de lectura junto a la réplica 2 sin requerir reinicio de la aplicación Spring Boot.
* **Resultado Obtenido:**
  ```text
  1. Iniciando 'postgres-replica-1'...
  2. Estado de salud en la aplicación:
     {"REPLICA_1": true, "REPLICA_2": true}
  3. Consultas atendidas tras la recuperación:
     - Lectura 1  -> REPLICA_2
     - Lectura 2  -> REPLICA_1 (Reincorporada)
     - Lectura 3  -> REPLICA_2
     - Lectura 4  -> REPLICA_1
     - Lectura 5  -> REPLICA_2
     - Lectura 6  -> REPLICA_1
  ```
* **Análisis Técnico:** PostgreSQL reanuda el receptor WAL (*walreceiver*) reconectándose al proceso emisor (*walsender*) del nodo Primario. El pool HikariCP restablece las conexiones saludables, y el selector de `DynamicRoutingDataSource` vuelve a considerarla apta para el reparto equitativo.

---

### Conclusiones de la Validación Experimental
1. **Separación de Responsabilidades:** Se comprobó que Eureka resuelve únicamente nombres de servicios en la capa de aplicación, mientras que el balanceo y conmutación de base de datos es resuelto autónomamente por la aplicación mediante `DynamicRoutingDataSource`.
2. **Resiliencia Integral:** El sistema demostró capacidad de auto-recuperación ante caídas de contenedores tanto en la capa web/lógica como en la capa de datos.
3. **Reproducibilidad:** Todos los escenarios son 100% automatizables y reproducibles mediante los scripts contenidos en el repositorio.
