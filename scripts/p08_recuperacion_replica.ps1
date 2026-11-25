Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P08 - Recuperacion y Reincorporacion de Replica PostgreSQL" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$inspectUrl = "http://localhost:8080/api/pedidos/db/inspect"
$healthUrl = "http://localhost:8080/api/pedidos/db/health"
$pedidoUrl = "http://localhost:8080/api/pedidos"

Write-Host "1. Escribiendo un pedido mientras 'postgres-replica-1' esta detenida..." -ForegroundColor Yellow
$body = @{ cliente = "Jean Stalin"; productoCodigo = "PROD-004"; cantidad = 1 } | ConvertTo-Json
$nuevo = Invoke-RestMethod -Uri $pedidoUrl -Method Post -Body $body -Headers @{ "Content-Type" = "application/json" }
Write-Host "   Pedido creado en PRIMARY: $($nuevo.numeroPedido)" -ForegroundColor White
$filasPrimario = docker exec postgres-primary psql -U postgres -d pedidos_db -tAc "SELECT count(*) FROM pedidos"
Write-Host "   Filas en primario: $filasPrimario" -ForegroundColor White

Write-Host "`n2. Reiniciando contenedor 'postgres-replica-1'..." -ForegroundColor Cyan
$sw = [System.Diagnostics.Stopwatch]::StartNew()
docker start postgres-replica-1 | Out-Null

Write-Host "   Esperando a que la aplicacion la considere apta (standby + WAL en streaming)..." -ForegroundColor Gray
$apta = $false
while ($sw.Elapsed.TotalSeconds -lt 60) {
    try {
        # Ambas instancias de servicio-pedidos tienen su propio selector: se consulta varias veces
        $h1 = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 3
        $h2 = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 3
        if ($h1.replicas.REPLICA_1 -and $h2.replicas.REPLICA_1) { $apta = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
if ($apta) {
    Write-Host ("   REPLICA_1 reincorporada {0:N1} s despues del 'docker start'" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
} else {
    Write-Host "   REPLICA_1 no fue considerada apta en 60 s" -ForegroundColor Red
}

Write-Host "`n3. Estado de replicacion visto desde el primario (pg_stat_replication):" -ForegroundColor Yellow
docker exec postgres-primary psql -U postgres -c "SELECT client_addr, state, sync_state, replay_lag FROM pg_stat_replication"

Write-Host "4. Datos escritos durante la caida presentes en la replica recuperada:" -ForegroundColor Yellow
$filasReplica = docker exec postgres-replica-1 psql -U postgres -d pedidos_db -tAc "SELECT count(*) FROM pedidos"
$enRecovery = docker exec postgres-replica-1 psql -U postgres -tAc "SELECT pg_is_in_recovery()"
Write-Host "   Filas en replica-1: $filasReplica (primario: $filasPrimario) | pg_is_in_recovery: $enRecovery" -ForegroundColor White

Write-Host "`n5. Ejecutando 10 lecturas para comprobar reincorporacion en el balanceo..." -ForegroundColor Yellow
$nodosVistos = @{}
for ($i = 1; $i -le 10; $i++) {
    $res = Invoke-RestMethod -Uri $inspectUrl -Method Get
    $node = $res.routingSelection
    Write-Host "   Lectura $($i) -> Atendida por: $($node) (IP: $($res.ip))" -ForegroundColor White
    $nodosVistos[$node] = $true
}

if ($nodosVistos.ContainsKey("REPLICA_1") -and $nodosVistos.ContainsKey("REPLICA_2")) {
    Write-Host "`n[P08 EXITOSO] Replica 1 recuperada, sincronizada e integrada nuevamente a la rotacion." -ForegroundColor Green
} else {
    Write-Host "`n[P08 ATENCION] Replica 1 aun no participa. Nodos activos: $($nodosVistos.Keys -join ', ')" -ForegroundColor Yellow
}
