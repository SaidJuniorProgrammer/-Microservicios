Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P08 - Recuperacion y Reincorporacion de Replica PostgreSQL" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$inspectUrl = "http://localhost:8080/api/pedidos/db/inspect"
$healthUrl = "http://localhost:8080/api/pedidos/db/health"

Write-Host "1. Reiniciando contenedor 'postgres-replica-1'..." -ForegroundColor Cyan
docker start postgres-replica-1

Write-Host "   Esperando 5 segundos para recuperacion de replicacion WAL..." -ForegroundColor Gray
Start-Sleep -Seconds 5

Write-Host "`n2. Verificando estado de salud de réplicas en la aplicacion..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri $healthUrl -Method Get
Write-Host "   Estado reportado: $($health.replicas | ConvertTo-Json -Compress)" -ForegroundColor White

Write-Host "`n3. Ejecutando 10 lecturas para comprobar reincorporacion en el balanceo..." -ForegroundColor Yellow
$nodosVistos = @{}

for ($i = 1; $i -le 10; $i++) {
    $res = Invoke-RestMethod -Uri $inspectUrl -Method Get
    $node = $res.routingSelection
    Write-Host "   Lectura $($i) -> Atendida por: $($node) (IP: $($res.ip))" -ForegroundColor White
    $nodosVistos[$node] = $true
}

if ($nodosVistos.ContainsKey("REPLICA_1") -and $nodosVistos.ContainsKey("REPLICA_2")) {
    Write-Host "`n[P08 EXITOSO] Réplica 1 recuperada e integrada nuevamente a la rotacion activa." -ForegroundColor Green
} else {
    Write-Host "`n[P08 ATENCION] Aun completando sincronizacion. Nodos activos: $($nodosVistos.Keys -join ', ')" -ForegroundColor Yellow
}
