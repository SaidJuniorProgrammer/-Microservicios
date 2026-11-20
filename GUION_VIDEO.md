# Guión Técnico del Video Demostrativo (3 a 5 Minutos)
## Arquitectura de Microservicios Distribuidos y Persistencia Replicada

* **Universidad Estatal Península de Santa Elena (UPSE)**
* **Asignatura:** Aplicaciones Distribuidas
* **Integrantes del Equipo:**
  * **Said Pinto**
  * **Oscar Gonabay**
  * **Jean Stalin**
* **Duración Total Estimada:** 4 minutos y 30 segundos.

---

### Estructura de Participación y Tiempos

| Minuto | Sección | Integrante Responsable | Enfoque de Pantalla / Demostración |
| :--- | :--- | :--- | :--- |
| **0:00 - 0:45** | 1. Introducción y Arquitectura General | **Said Pinto** | Diagrama de Arquitectura y `docker compose ps` |
| **0:45 - 1:30** | 2. Descubrimiento y Balanceo HTTP | **Jean Stalin** | Panel de Eureka y Script de 100 peticiones (`p02`) |
| **1:30 - 2:30** | 3. Comunicación Inter-servicio y Persistencia | **Oscar Gonabay** | Petición de compra (Feign) y logs de enrutamiento DB (`p04`, `p05`) |
| **2:30 - 3:45** | 4. Simulación Experimental de Fallos | **Said Pinto** | Detención de instancia (`p03`) y detención de réplica PostgreSQL (`p06`) |
| **3:45 - 4:30** | 5. Recuperación y Conclusiones Técnicas | **Oscar Gonabay & Jean Stalin** | Reincorporación de réplica (`p08`) y cierre técnico |

---

### Guión Detallado Paso a Paso

#### Minuto 0:00 - 0:45 | Introducción y Arquitectura (Said Pinto)
* **Pantalla:** Mostrar el diagrama de arquitectura en pantalla completa o el archivo `README.md`, y luego cambiar a la terminal con `docker compose ps`.
* **Voz de Said Pinto:**
  > *"Saludos cordiales, profesor y compañeros. Somos el equipo conformado por Oscar Gonabay, Jean Stalin y Said Pinto. A continuación presentamos la validación experimental de nuestra arquitectura de microservicios distribuidos.*
  > 
  > *Como observan en el diagrama, el sistema cuenta con un API Gateway en el puerto 8080 que centraliza todas las peticiones, un servidor Eureka para el registro dinámico en el puerto 8761, dos microservicios de negocio: Servicio de Productos y Servicio de Pedidos —este último con dos instancias concurrentes para balanceo y alta disponibilidad— y un clúster de base de datos PostgreSQL 16 con un nodo Primario para escrituras y dos Réplicas de lectura bajo replicación física WAL.*
  > 
  > *En la terminal verificamos que los 8 contenedores se encuentran levantados y saludables mediante Docker Compose."*

---

#### Minuto 0:45 - 1:30 | Descubrimiento Dinámico y Balanceo HTTP (Jean Stalin)
* **Pantalla:** Mostrar el navegador web en `http://localhost:8761` (Eureka Dashboard) y luego abrir PowerShell para ejecutar `.\scripts\p02_balanceo_http.ps1`.
* **Voz de Jean Stalin:**
  > *"Gracias, Said. Aquí en el panel de Eureka Server podemos comprobar que las aplicaciones se registran dinámicamente mediante latidos periódicos. Vemos `API-GATEWAY`, `SERVICIO-PRODUCTOS` y las dos instancias simultáneas de `SERVICIO-PEDIDOS`, identificadas como `instancia-1` e `instancia-2`.*
  > 
  > *Ahora pasamos a la terminal para ejecutar la prueba obligatoria P02: enviaremos 100 solicitudes consecutivas al Gateway dirigidas al servicio de pedidos.*
  > 
  > *(Se ejecuta el script y se ven las estadísticas).*
  > 
  > *Como pueden apreciar, las 100 peticiones resultaron exitosas con código 200, logrando un balanceo exacto del 50% para la instancia 1 y 50% para la instancia 2, gracias al algoritmo Round-Robin de Spring Cloud LoadBalancer."*

---

#### Minuto 1:30 - 2:30 | Comunicación Inter-servicio y Persistencia Replicada (Oscar Gonabay)
* **Pantalla:** Mostrar Postman o el script `.\scripts\p04_comunicacion_servicios.ps1` y `.\scripts\p05_enrutamiento_db.ps1`.
* **Voz de Oscar Gonabay:**
  > *"Continuando con el flujo, demostraremos la comunicación inter-servicio y la persistencia de datos.*
  > 
  > *Al enviar una orden de compra a `servicio-pedidos`, este no accede directamente a la tabla de productos, sino que invoca a `servicio-productos` a través de un cliente declarativo OpenFeign utilizando el nombre lógico registrado en Eureka.*
  > 
  > *Aquí vemos cómo se valida el stock disponible y se realiza el descuento automático en inventario.*
  > 
  > *Por otro lado, para cumplir el requisito de persistencia replicada implementamos en Spring Boot un `DynamicRoutingDataSource` con `LazyConnectionDataSourceProxy`. Al realizar una escritura, la cabecera `X-DB-Node` marca `PRIMARY`. Pero cuando realizamos consultas ordinarias de lectura, el sistema distribuye las solicitudes equitativamente entre la Réplica 1 y la Réplica 2, como lo evidencia la función SQL nativa que nos confirma que `pg_is_in_recovery` es verdadero."*

---

#### Minuto 2:30 - 3:45 | Simulación de Fallos Parciales (Said Pinto)
* **Pantalla:** Terminal ejecutando `.\scripts\p03_fallo_instancia.ps1` y luego `.\scripts\p06_fallo_replica.ps1`.
* **Voz de Said Pinto:**
  > *"Un aspecto fundamental de nuestra actividad es demostrar qué ocurre cuando el sistema experimenta fallos.*
  > 
  > *Primero, provocamos la caída de una instancia de microservicio ejecutando `docker stop servicio-pedidos-1`. Eureka detecta la desconexión rápidamente porque configuramos intervalos de latido reducidos. Observen que seguimos enviando peticiones y el Gateway redirige el 100% del tráfico a la instancia 2 sin caídas de servicio.*
  > 
  > *Segundo, simulamos un fallo en la capa de datos: detenemos `postgres-replica-1`. Inmediatamente ejecutamos consultas de lectura; el selector dinámico detecta que la Réplica 1 no responde y redirige transparentemente las consultas a la Réplica 2 restante, manteniendo la disponibilidad y cero errores para el cliente.*
  > 
  > *Adicionalmente, con la prueba P07 demostramos que las lecturas críticas post-escritura se fuerzan al Primario con `@CriticalRead`, previniendo inconsistencias por el retraso de replicación WAL."*

---

#### Minuto 3:45 - 4:30 | Recuperación y Conclusiones Técnicas (Oscar Gonabay y Jean Stalin)
* **Pantalla:** Ejecutar `.\scripts\p08_recuperacion_replica.ps1`, mostrando cómo la réplica reiniciada vuelve al balanceo.
* **Voz de Oscar Gonabay:**
  > *"Para concluir la demostración, reiniciamos la Réplica 1 mediante `docker start`. El proceso de replicación de PostgreSQL se reconecta al Primario para ponerse al día con el registro WAL, y el pool de conexiones de Spring Boot reincorpora la réplica al balanceo activo de lecturas de manera totalmente automática."*
* **Voz de Jean Stalin:**
  > *"Como conclusiones técnicas:*
  > *1. Logramos aislar responsabilidades: Eureka solo maneja la capa web, mientras que el balanceo de base de datos se resuelve a nivel de persistencia en la aplicación.*
  > *2. Comprobamos que una arquitectura distribuida tolerante a fallos requiere mecanismos tanto en la capa de red y gateway como en la capa transaccional de datos.*
  > *Todos los procedimientos se encuentran documentados en el informe y en el repositorio público de GitHub. Muchas gracias por su atención."*
