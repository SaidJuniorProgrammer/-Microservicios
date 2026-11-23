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
Se ejecutaron los 8 escenarios obligatorios sobre un entorno creado desde cero (`docker compose down -v` seguido de `docker compose up --build -d`), mediante los scripts de `scripts/` (`run_all_tests.ps1`). Los resultados que se muestran corresponden a esa ejecución real; la salida completa, sin editar, está en [evidencias/ejecucion_p01_p08.txt](evidencias/ejecucion_p01_p08.txt).

En los escenarios de fallo (P03 y P06) el tráfico se envía de forma **continua (una solicitud cada ~200 ms) antes, durante y después** de detener el contenedor, sin pausas, para registrar también la ventana en la que el sistema todavía no ha detectado la caída. Adicionalmente se ejecutaron pruebas de fallo más agresivas (caída abrupta con `docker kill`, conexiones cortadas a mitad de consulta y caída del primario), cuyos resultados se resumen en cada escenario.

> Las direcciones IP de los contenedores y los identificadores de hostname dependen del equipo donde se ejecute; los tiempos pueden variar algunos segundos.

---

### P01 · Descubrimiento Dinámico de Servicios

* **Objetivo:** Verificar que las instancias se registran en Eureka y pueden localizarse por nombre lógico.
* **Procedimiento:** `.\scripts\p01_descubrimiento.ps1` consulta `http://localhost:8761/eureka/apps` (JSON).
* **Resultado Esperado:** `API-GATEWAY`, `SERVICIO-PRODUCTOS` y 2 instancias de `SERVICIO-PEDIDOS` en estado `UP`.
* **Resultado Obtenido:**
  ```text
  * Aplicacion: SERVICIO-PEDIDOS
    - Instancia ID : servicio-pedidos-instancia-1           Host / IP: 172.27.0.6:8081  Estado: UP
    - Instancia ID : servicio-pedidos-instancia-2           Host / IP: 172.27.0.7:8081  Estado: UP
  * Aplicacion: API-GATEWAY
    - Instancia ID : 56e620262720:api-gateway:8080          Host / IP: 172.27.0.8:8080  Estado: UP
  * Aplicacion: SERVICIO-PRODUCTOS
    - Instancia ID : f278deb0dc27:servicio-productos:8082   Host / IP: 172.27.0.9:8082  Estado: UP
  ```
* **Análisis:** Las dos instancias de pedidos comparten el nombre lógico `servicio-pedidos` y se distinguen por `instance-id`. El Gateway (`lb://servicio-pedidos`) y Feign (`@FeignClient(name = "servicio-productos")`) resuelven los destinos por nombre, sin IPs fijas. Los microservicios renuevan su lease cada 3 s y Eureka expulsa las instancias vencidas cada 3 s (auto-preservación desactivada). La caché de respuestas del servidor Eureka se redujo de 30 s (valor por defecto) a 2 s, y los clientes (Gateway y servicio-pedidos) refrescan el registro y la caché del balanceador cada 2 s.

---

### P02 · Balanceo de Carga HTTP (100 Peticiones)

* **Objetivo:** Demostrar que Gateway + Spring Cloud LoadBalancer reparten las solicitudes entre las dos instancias.
* **Procedimiento:** `.\scripts\p02_balanceo_http.ps1` envía 100 solicitudes a `GET /api/pedidos/info/instance` y contabiliza `instanceId`.
* **Resultado Esperado:** 100 respuestas HTTP 200 con distribución aproximada 50/50.
* **Resultado Obtenido:**
  ```text
  Total peticiones exitosas: 100
  Total peticiones con error: 0
  * servicio-pedidos-instancia-2 : 50 peticiones (50 %)
  * servicio-pedidos-instancia-1 : 50 peticiones (50 %)
  ```
* **Análisis:** La política Round-Robin de Spring Cloud LoadBalancer alterna instancia por instancia. El cliente solo conoce el Gateway (puerto 8080).

---

### P03 · Fallo de Instancia de Microservicio

* **Objetivo:** Detener una instancia y comprobar que el sistema sigue atendiendo mediante la otra; medir tiempos de recuperación.
* **Procedimiento:** `.\scripts\p03_fallo_instancia.ps1`: tráfico continuo 5 s con ambas instancias → `docker stop servicio-pedidos-1` sin pausa (20 s de tráfico) → `docker start servicio-pedidos-1` (40 s de tráfico).
* **Resultado Esperado:** Continuidad del servicio por `servicio-pedidos-instancia-2` y reincorporación de la instancia 1 tras reiniciarla.
* **Resultado Obtenido:**
  ```text
  ANTES    total= 23 ok= 23 errores= 0 inst-1= 11 inst-2= 12 latencia_max=13 ms
  DURANTE  total= 92 ok= 92 errores= 0 inst-1=  1 inst-2= 91 latencia_max=70 ms
  DESPUES  total=186 ok=186 errores= 0 inst-1= 77 inst-2=109 latencia_max=95 ms
  Ultima respuesta de instancia-1 antes del fallo: 0,2 s tras iniciar el 'docker stop'
  Reincorporacion de instancia-1: 7,1 s despues del 'docker start'
  ```
  **Prueba adicional – caída abrupta (`docker kill servicio-pedidos-1`)** con tráfico continuo de GET y POST durante 40 s: 59/59 GET con HTTP 200, 59/59 POST con HTTP 201 y 59 filas nuevas en la base de datos (ningún pedido duplicado). Durante ~14 s algunas solicitudes tardaron entre 1 y 2,2 s.
* **Análisis:**
  * Con `docker stop` la instancia se apaga de forma ordenada y se da de baja en Eureka; el Gateway deja de enrutarle en menos de 1 s.
  * Con `docker kill` la instancia no se da de baja: sigue en el registro hasta que vence su lease (5 s) y Eureka la expulsa. En esa ventana, el Gateway corta en 1 s el intento de conexión (`httpclient.connect-timeout`) y el filtro `Retry` reenvía la solicitud a la otra instancia. Como la conexión nunca se estableció, la instancia caída no procesó nada y es seguro reintentar también los `POST`.
  * El Gateway **no** reintenta ante respuestas 5xx ni ante timeouts de respuesta: en ese caso la solicitud pudo haberse procesado y repetirla duplicaría pedidos o descuentos de stock.
  * **Problemas detectados y corregidos:** en la versión inicial no había timeout de conexión y Eureka servía su registro desde una caché de 30 s; durante ~18 s tras detener la instancia, parte de las solicitudes quedaban colgadas más de 5 s. Además, el `Retry` incluía `POST` ante errores 5xx: una solicitud fallida llegó a ejecutarse 4 veces.
  * Tiempo de recuperación ≈ 7 s, dominado por el arranque de Spring Boot de la instancia más su registro en Eureka.

---

### P04 · Comunicación entre Microservicios (OpenFeign)

* **Objetivo:** Ejecutar una operación de negocio que requiere una llamada real de `servicio-pedidos` a `servicio-productos` mediante Eureka.
* **Procedimiento:** `.\scripts\p04_comunicacion_servicios.ps1`: consulta de stock → `POST /api/pedidos` (2 unidades de `PROD-001`) → verificación de stock → pedido con stock excesivo → pedido de producto inexistente → trazas de logs.
* **Resultado Esperado:** Pedido `CONFIRMADO` y stock descontado en 2; rechazo 400 por stock insuficiente; rechazo 404 por producto inexistente.
* **Resultado Obtenido:**
  ```text
  Stock Inicial: 50
  Pedido Creado: PED-E3AC0F0F | Total: $ 1700 | Estado: CONFIRMADO
  Stock Actual : 48 (Deseado: 48)
  Stock excesivo    -> HTTP 400 {"error": "Stock insuficiente para el producto: Laptop Dell Latitude 5420 (Disponible: 48, Solicitado: 9999)"}
  Producto PROD-999 -> HTTP 404 {"error": "El producto con codigo PROD-999 no existe."}

  [instancia-1] [PEDIDO] Procesando creacion de pedido: cliente=Oscar Gonabay, producto=PROD-001, cantidad=2
  [instancia-1] [PEDIDO] Pedido guardado exitosamente con ID 1 en nodo PRIMARY
  [instancia-2] [PEDIDO] Procesando creacion de pedido: cliente=Jean Stalin, producto=PROD-001, cantidad=9999
  [instancia-1] [PEDIDO] Procesando creacion de pedido: cliente=Said Pinto, producto=PROD-999, cantidad=1
  ```
* **Análisis:**
  * Cada servicio usa su propia base (`pedidos_db`, `productos_db`); pedidos solo accede a productos por su API REST.
  * Manejo de errores: 404 de productos → 404 de negocio; productos caído o sin instancias en Eureka → 503 `servicio-productos no disponible`.
  * **Problema detectado y corregido:** antes, un producto inexistente devolvía 500 (Feign lanza `FeignException.NotFound`, nunca devuelve `null`). Las trazas muestran ahora una única ejecución por solicitud.
  * **Limitación:** si el guardado del pedido falla después de descontar el stock, no hay compensación automática (Sagas fuera del alcance de la actividad).

---

### P05 · Enrutamiento de Persistencia (Primary vs Réplicas)

* **Objetivo:** Demostrar que las escrituras usan el primario y las consultas ordinarias se distribuyen entre las dos réplicas.
* **Procedimiento:** `.\scripts\p05_enrutamiento_db.ps1`: `POST /api/pedidos` y 8 consultas a `GET /api/pedidos/db/inspect`, que ejecuta `SELECT inet_server_addr(), inet_server_port(), pg_is_in_recovery(), current_database()` en el nodo seleccionado.
* **Resultado Esperado:** Escritura en `PRIMARY`; lecturas repartidas entre `REPLICA_1` y `REPLICA_2` con `pg_is_in_recovery = true`.
* **Resultado Obtenido:**
  ```text
  POST /api/pedidos -> HTTP 201 | X-DB-Node: PRIMARY
  Lectura 1 -> REPLICA_1 | Es Replica?: True | IP: 172.27.0.5
  Lectura 2 -> REPLICA_1 | Es Replica?: True | IP: 172.27.0.5
  Lectura 3 -> REPLICA_2 | Es Replica?: True | IP: 172.27.0.4
  Lectura 4 -> REPLICA_2 | Es Replica?: True | IP: 172.27.0.4
  Lectura 5 -> REPLICA_1 | Es Replica?: True | IP: 172.27.0.5
  Lectura 6 -> REPLICA_1 | Es Replica?: True | IP: 172.27.0.5
  Lectura 7 -> REPLICA_2 | Es Replica?: True | IP: 172.27.0.4
  Lectura 8 -> REPLICA_2 | Es Replica?: True | IP: 172.27.0.4
  ```
  En esa ejecución: `postgres-primary = 172.27.0.3`, `postgres-replica-1 = 172.27.0.5`, `postgres-replica-2 = 172.27.0.4`.
* **Análisis:**
  * `DynamicRoutingDataSource` (extiende `AbstractRoutingDataSource`) decide el nodo: transacción de escritura o lectura crítica → `PRIMARY`; `@Transactional(readOnly = true)` ordinaria → réplica sana por Round-Robin. `LazyConnectionDataSourceProxy` retrasa la obtención de la conexión física hasta la primera sentencia, cuando ya se conoce si la transacción es de solo lectura. Open-Session-In-View está desactivado (`spring.jpa.open-in-view: false`) para que cada transacción obtenga su propia conexión y el nodo se elija por transacción y no por petición HTTP.
  * El patrón R1,R1,R2,R2 se debe a que cada instancia de pedidos tiene su propio contador Round-Robin y el Gateway alterna entre instancias; globalmente el reparto es 50/50.
  * La prueba no depende solo de la etiqueta de la aplicación: `pg_is_in_recovery()` y la IP del servidor las devuelve el propio PostgreSQL. Las sentencias SQL quedan registradas (`log_statement = 'all'`) en `$PGDATA/log/` de cada contenedor, por ejemplo: `docker exec postgres-replica-2 sh -c 'tail $PGDATA/log/*.log'`.

---

### P06 · Fallo de Réplica PostgreSQL

* **Objetivo:** Detener una réplica y comprobar que las consultas continúan por la réplica restante.
* **Procedimiento:** `.\scripts\p06_fallo_replica.ps1`: lecturas continuas 4 s con ambas réplicas → `docker stop postgres-replica-1` sin pausa → 12 s de lecturas.
* **Resultado Esperado:** Las consultas continúan por `REPLICA_2`; el selector marca `REPLICA_1` como no disponible.
* **Resultado Obtenido:**
  ```text
  ANTES    total= 19 ok= 19 errores= 0 REPLICA_1= 10 REPLICA_2=  9 latencia_max=14 ms
  DURANTE  total= 51 ok= 51 errores= 0 REPLICA_1=  0 REPLICA_2= 51 latencia_max=1013 ms
  Estado del selector: {"REPLICA_1":false,"REPLICA_2":true}

  Logs de servicio-pedidos:
  [ROUTING-DB] REPLICA_1 cambia de estado: SALUDABLE -> NO DISPONIBLE (excluida)
  [ROUTING-DB] REPLICA_1 no disponible (HikariPool-Replica1 - Connection is not available, request timed out after 1000ms.). Reintentando la consulta en REPLICA_2
  ```
  **Pruebas adicionales:**
  * 4 rondas consecutivas deteniendo alternadamente `postgres-replica-1` y `postgres-replica-2` con tráfico continuo (`/db/inspect` y `GET /api/pedidos`): 0 errores, latencia máxima ≈ 1,04 s.
  * Conexiones cortadas a mitad de consulta (`pg_terminate_backend` repetido sobre ambas réplicas): 120/120 respuestas HTTP 200, con 12 lecturas reintentadas automáticamente en otro nodo.
  * Ambas réplicas detenidas: las lecturas pasan a `PRIMARY` (HTTP 200).
* **Análisis:** El fallo de una réplica se maneja en tres niveles:
  1. Un chequeo cada 2 s excluye la réplica que no responde.
  2. Si una consulta elige la réplica antes de que el chequeo la detecte y no obtiene conexión, el router la marca como caída y obtiene la conexión de la otra réplica en la misma solicitud (costo: ~1 s, el timeout de conexión de HikariCP).
  3. Si la réplica se cae **mientras la consulta se está ejecutando**, `ReplicaReadRetryAspect` repite la lectura en una transacción nueva sobre otro nodo. Solo se aplica a lecturas ordinarias (`readOnly`, no críticas), cuya repetición no tiene efectos.
  * Si ambas réplicas caen, las lecturas se dirigen al primario.
  * **Problema detectado y corregido:** en la versión inicial no existían los niveles 2 y 3; en una ejecución, 1 de 46 lecturas falló con 2,1 s de espera.

---

### P07 · Consistencia de Lectura Crítica (Read-After-Write)

* **Objetivo:** Crear un registro y consultarlo inmediatamente mediante una operación crítica dirigida al primario.
* **Procedimiento:** `.\scripts\p07_consistencia_lectura.ps1`: `POST /api/pedidos` → `GET /api/pedidos/{numero}/critico` inmediato → `GET /api/pedidos/db/inspect-critico`.
* **Resultado Esperado:** Lectura en `PRIMARY` con `pg_is_in_recovery = false`.
* **Resultado Obtenido:**
  ```text
  Pedido generado: PED-1B9AA362
  GET /api/pedidos/PED-1B9AA362/critico -> HTTP 200 | X-DB-Node: PRIMARY
  inspect-critico -> Nodo: PRIMARY | Es Replica?: False | IP: 172.27.0.3
  ```
* **Análisis:** La replicación es asíncrona, por lo que una réplica puede no tener todavía un registro recién escrito. Las lecturas críticas (`@CriticalRead` o cabecera `X-Critical-Read: true`) se fuerzan al primario mediante un aspecto AOP que se ejecuta antes de abrir la transacción. Las lecturas ordinarias aceptan consistencia eventual; en las pruebas el retraso de réplica (`replay_lag`) fue del orden de milisegundos.

---

### P08 · Recuperación y Reincorporación de Réplica

* **Objetivo:** Reiniciar la réplica detenida, comprobar su sincronización y que vuelve a participar en las consultas.
* **Procedimiento:** `.\scripts\p08_recuperacion_replica.ps1`: escritura mientras `postgres-replica-1` está detenida → `docker start postgres-replica-1` → espera hasta que la aplicación la considere apta → estado de replicación → comparación de filas → 10 lecturas.
* **Resultado Esperado:** La réplica recupera los datos escritos durante la caída y vuelve a la rotación sin reiniciar la aplicación.
* **Resultado Obtenido:**
  ```text
  Pedido creado en PRIMARY: PED-F6B12228 | Filas en primario: 4
  REPLICA_1 reincorporada 10,1 s despues del 'docker start'

   client_addr |   state   | sync_state |   replay_lag
  -------------+-----------+------------+-----------------
   172.27.0.4  | streaming | async      |
   172.27.0.5  | streaming | async      | 00:00:00.003079

  Filas en replica-1: 4 (primario: 4) | pg_is_in_recovery: t
  Lecturas: REPLICA_2, REPLICA_1, REPLICA_1, REPLICA_2, REPLICA_2, REPLICA_1, REPLICA_1, REPLICA_2, REPLICA_1, REPLICA_2

  Logs: [ROUTING-DB] REPLICA_1 cambia de estado: NO DISPONIBLE -> SALUDABLE (reincorporada)
  ```
* **Análisis:**
  * Al reiniciar, PostgreSQL reanuda la replicación por streaming desde el primario y aplica el WAL pendiente (el pedido escrito durante la caída aparece en la réplica).
  * Criterio de aptitud: una réplica excluida solo se reincorpora si responde, `pg_is_in_recovery()` es verdadero y `pg_stat_wal_receiver.status = 'streaming'`. Así no recibe consultas mientras arranca o antes de conectarse al primario. Una réplica que ya está en la rotación permanece en ella mientras responda en modo standby, de modo que una caída del primario no la expulsa (comprobado: con el primario detenido las lecturas siguieron atendiéndose desde las réplicas). **Problema detectado y corregido:** antes solo se comprobaba `SELECT 1`.
  * **Limitación:** no se usan *replication slots*; si una réplica estuviera detenida tanto tiempo que el primario reciclara más de `wal_keep_size` (64 MB) de WAL, habría que reconstruirla (`docker compose rm -sf postgres-replica-1 && docker compose up -d postgres-replica-1`).

---

### Conclusiones de la Validación Experimental
1. **Separación de responsabilidades:** Eureka resuelve únicamente nombres de servicios; la selección de nodos PostgreSQL la realiza la aplicación con `DynamicRoutingDataSource`.
2. **Resiliencia observada:** ni la caída de una instancia de pedidos (ordenada o abrupta) ni la de una réplica produjeron errores visibles para el cliente; el costo fue una latencia puntual de 1–2 s mientras se detecta el fallo.
3. **Problemas encontrados durante las pruebas y corregidos:** solicitudes colgadas al caer una instancia (sin timeout en el Gateway y caché de 30 s en Eureka), reintentos de `POST` que ejecutaban un pedido varias veces, producto inexistente reportado como error 500, lecturas fallidas durante la ventana de detección de una réplica o con la conexión cortada a mitad de consulta, y reincorporación de réplicas sin verificar la replicación.
4. **Limitaciones:** sin failover automático del primario (no exigido): con el primario caído las lecturas continúan desde las réplicas pero las escrituras fallan. Tampoco hay compensación si falla el guardado tras descontar stock, ni *replication slots*.
