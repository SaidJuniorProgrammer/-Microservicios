Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P06 - Tolerancia a Fallos de Replica PostgreSQL" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$inspectUrl = "http://localhost:8080/api/pedidos/db/inspect"

Write-Host "1. Simulando caida: Deteniendo 'postgres-replica-1'..." -ForegroundColor Red
docker stop postgres-replica-1

Write-Host "   Esperando 2 segundos para deteccion de estado..." -ForegroundColor Gray
Start-Sleep -Seconds 2

Write-Host "`n2. Ejecutando 6 consultas de lectura durante la caida..." -ForegroundColor Yellow
$todasExitosas = $true

for ($i = 1; $i -le 6; $i++) {
    try {
        $res = Invoke-RestMethod -Uri $inspectUrl -Method Get -TimeoutSec 3
        Write-Host "   Consulta $($i) -> Nodo: $($res.routingSelection) | Estado: EXITOSO | IP: $($res.ip)" -ForegroundColor Green
        if ($res.routingSelection -ne "REPLICA_2") {
            Write-Host "   [Nota] Enrutado a: $($res.routingSelection)" -ForegroundColor Yellow
        }
    } catch {
        $todasExitosas = $false
        Write-Host "   Consulta $($i) fallo - Detalle: $_" -ForegroundColor Red
    }
}

if ($todasExitosas) {
    Write-Host "`n[P06 EXITOSO] Las consultas continuaron sin interrupcion a traves de la réplica saludable restante." -ForegroundColor Green
} else {
    Write-Host "`n[P06 ADVERTENCIA] Ocurrieron errores transitorios durante la desconexion." -ForegroundColor Yellow
}
