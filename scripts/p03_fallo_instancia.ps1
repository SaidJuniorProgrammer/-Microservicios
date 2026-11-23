Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P03 - Tolerancia a Fallos de Instancia de Microservicio" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Trafico continuo (una solicitud cada ~200 ms) ANTES, DURANTE y DESPUES del fallo.
# No se espera tras el 'docker stop': se registra la ventana de fallo completa.

$gatewayUrl = "http://localhost:8080/api/pedidos/info/instance"
$log = New-Object System.Collections.Generic.List[object]
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Send-Probe($fase) {
    $t0 = $sw.Elapsed.TotalSeconds
    try {
        $r = Invoke-RestMethod -Uri $gatewayUrl -Method Get -TimeoutSec 5
        $estado = "200"; $inst = $r.instanceId
    } catch {
        $estado = "ERROR"; $inst = "-"
    }
    $dur = [math]::Round(($sw.Elapsed.TotalSeconds - $t0) * 1000)
    $log.Add([pscustomobject]@{ t = [math]::Round($t0, 1); fase = $fase; estado = $estado; ms = $dur; instancia = $inst })
    Start-Sleep -Milliseconds 200
}

Write-Host "1. Trafico inicial con ambas instancias (5 s)..." -ForegroundColor Yellow
$fin = $sw.Elapsed.TotalSeconds + 5
while ($sw.Elapsed.TotalSeconds -lt $fin) { Send-Probe "ANTES" }

Write-Host "2. Deteniendo 'servicio-pedidos-1' (sin pausa, el trafico continua)..." -ForegroundColor Red
$tStop = $sw.Elapsed.TotalSeconds
$job = Start-Job { docker stop servicio-pedidos-1 | Out-Null }
$fin = $sw.Elapsed.TotalSeconds + 20
while ($sw.Elapsed.TotalSeconds -lt $fin) { Send-Probe "DURANTE" }
Wait-Job $job | Out-Null; Remove-Job $job

Write-Host "3. Iniciando 'servicio-pedidos-1'..." -ForegroundColor Cyan
docker start servicio-pedidos-1 | Out-Null
$tStart = $sw.Elapsed.TotalSeconds
$tReinc = $null
$fin = $sw.Elapsed.TotalSeconds + 40
while ($sw.Elapsed.TotalSeconds -lt $fin) {
    Send-Probe "DESPUES"
    if (-not $tReinc -and $log[-1].instancia -eq "servicio-pedidos-instancia-1") { $tReinc = $log[-1].t }
}

Write-Host "`nResultados por fase:" -ForegroundColor Green
foreach ($fase in "ANTES", "DURANTE", "DESPUES") {
    $f = $log | Where-Object fase -eq $fase
    $ok = ($f | Where-Object estado -eq "200").Count
    $err = ($f | Where-Object estado -ne "200").Count
    $i1 = ($f | Where-Object instancia -eq "servicio-pedidos-instancia-1").Count
    $i2 = ($f | Where-Object instancia -eq "servicio-pedidos-instancia-2").Count
    $max = ($f | Measure-Object ms -Maximum).Maximum
    Write-Host ("  {0,-8} total={1,3} ok={2,3} errores={3,2} inst-1={4,3} inst-2={5,3} latencia_max={6} ms" -f $fase, $f.Count, $ok, $err, $i1, $i2, $max)
}

$ultimaI1 = ($log | Where-Object { $_.instancia -eq "servicio-pedidos-instancia-1" -and $_.t -lt $tStart } | Select-Object -Last 1).t
Write-Host "`nTiempos observados:" -ForegroundColor Yellow
Write-Host ("  Ultima respuesta de instancia-1 antes del fallo: {0:N1} s tras iniciar el 'docker stop'" -f ($ultimaI1 - $tStop))
if ($tReinc) {
    Write-Host ("  Reincorporacion de instancia-1: {0:N1} s despues del 'docker start'" -f ($tReinc - $tStart))
} else {
    Write-Host "  Instancia-1 aun no reincorporada en la ventana observada." -ForegroundColor Yellow
}

$errores = ($log | Where-Object estado -ne "200").Count
if ($errores -eq 0) {
    Write-Host "`n[P03 EXITOSO] Sin errores visibles para el cliente durante el fallo." -ForegroundColor Green
} else {
    Write-Host "`n[P03 CON ERRORES TRANSITORIOS] $errores solicitudes fallaron durante la ventana de deteccion." -ForegroundColor Yellow
}
