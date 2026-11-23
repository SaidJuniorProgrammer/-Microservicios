Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P06 - Tolerancia a Fallos de Replica PostgreSQL" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Lecturas ordinarias continuas ANTES y DURANTE la caida de la replica 1.
# No se espera tras el 'docker stop': se registra tambien la ventana de deteccion.

$inspectUrl = "http://localhost:8080/api/pedidos/db/inspect"
$healthUrl = "http://localhost:8080/api/pedidos/db/health"
$log = New-Object System.Collections.Generic.List[object]
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Send-Read($fase) {
    $t0 = $sw.Elapsed.TotalSeconds
    try {
        $r = Invoke-RestMethod -Uri $inspectUrl -Method Get -TimeoutSec 5
        if ($r.error) { $estado = "ERROR-BD"; $nodo = "-" } else { $estado = "200"; $nodo = $r.routingSelection }
        $ip = $r.ip
    } catch {
        $estado = "ERROR-HTTP"; $nodo = "-"; $ip = "-"
    }
    $dur = [math]::Round(($sw.Elapsed.TotalSeconds - $t0) * 1000)
    $log.Add([pscustomobject]@{ t = [math]::Round($t0, 1); fase = $fase; estado = $estado; ms = $dur; nodo = $nodo; ip = $ip })
    Start-Sleep -Milliseconds 200
}

Write-Host "1. Estado de contenedores:" -ForegroundColor Yellow
docker compose ps -a postgres-primary postgres-replica-1 postgres-replica-2 --format "   {{.Name}}  {{.Status}}"

Write-Host "`n2. Lecturas con ambas replicas (4 s)..." -ForegroundColor Yellow
$fin = $sw.Elapsed.TotalSeconds + 4
while ($sw.Elapsed.TotalSeconds -lt $fin) { Send-Read "ANTES" }

Write-Host "3. Deteniendo 'postgres-replica-1' (el trafico continua)..." -ForegroundColor Red
$tStop = $sw.Elapsed.TotalSeconds
docker stop postgres-replica-1 | Out-Null
$fin = $sw.Elapsed.TotalSeconds + 12
while ($sw.Elapsed.TotalSeconds -lt $fin) { Send-Read "DURANTE" }

Write-Host "`nResultados por fase:" -ForegroundColor Green
foreach ($fase in "ANTES", "DURANTE") {
    $f = $log | Where-Object fase -eq $fase
    $ok = ($f | Where-Object estado -eq "200").Count
    $err = ($f | Where-Object estado -ne "200").Count
    $r1 = ($f | Where-Object nodo -eq "REPLICA_1").Count
    $r2 = ($f | Where-Object nodo -eq "REPLICA_2").Count
    $max = ($f | Measure-Object ms -Maximum).Maximum
    Write-Host ("  {0,-8} total={1,3} ok={2,3} errores={3,2} REPLICA_1={4,3} REPLICA_2={5,3} latencia_max={6} ms" -f $fase, $f.Count, $ok, $err, $r1, $r2, $max)
}
$ultimaR1 = ($log | Where-Object nodo -eq "REPLICA_1" | Select-Object -Last 1).t
Write-Host ("`n  Ultima lectura atendida por REPLICA_1: {0:N1} s respecto al 'docker stop'" -f ($ultimaR1 - $tStop))

Write-Host "`n4. Estado del selector de replicas en la aplicacion:" -ForegroundColor Yellow
$h = Invoke-RestMethod -Uri $healthUrl -Method Get
Write-Host "   $($h.replicas | ConvertTo-Json -Compress)"
docker compose ps -a postgres-primary postgres-replica-1 postgres-replica-2 --format "   {{.Name}}  {{.Status}}"

$errores = ($log | Where-Object estado -ne "200").Count
if ($errores -eq 0) {
    Write-Host "`n[P06 EXITOSO] Las consultas continuaron a traves de la replica saludable restante." -ForegroundColor Green
} else {
    Write-Host "`n[P06 CON ERRORES TRANSITORIOS] $errores lecturas fallaron durante la ventana de deteccion." -ForegroundColor Yellow
}
