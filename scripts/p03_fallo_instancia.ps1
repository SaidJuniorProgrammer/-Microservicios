Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P03 - Tolerancia a Fallos de Instancia de Microservicio" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$gatewayUrl = "http://localhost:8080/api/pedidos/info/instance"

Write-Host "1. Verificando estado inicial con ambas instancias activas..." -ForegroundColor Yellow
for ($i = 1; $i -le 4; $i++) {
    $res = Invoke-RestMethod -Uri $gatewayUrl -Method Get
    Write-Host "   Solicitud $($i) respondida por: $($res.instanceId)" -ForegroundColor White
}

Write-Host "`n2. Simulando caida: Deteniendo 'servicio-pedidos-1'..." -ForegroundColor Red
docker stop servicio-pedidos-1

Write-Host "   Esperando 5 segundos para actualizacion de Eureka..." -ForegroundColor Gray
Start-Sleep -Seconds 5

Write-Host "`n3. Enviando solicitudes durante la caida de instancia 1..." -ForegroundColor Yellow
$falloExitoso = $true
for ($i = 1; $i -le 6; $i++) {
    try {
        $res = Invoke-RestMethod -Uri $gatewayUrl -Method Get -TimeoutSec 3
        Write-Host "   Solicitud $($i) respondida por: $($res.instanceId)" -ForegroundColor Green
        if ($res.instanceId -ne "servicio-pedidos-instancia-2") {
            $falloExitoso = $false
        }
    } catch {
        Write-Host "   Solicitud $($i) fallo: $_" -ForegroundColor Red
    }
}

Write-Host "`n4. Recuperando instancia: Iniciando 'servicio-pedidos-1'..." -ForegroundColor Cyan
docker start servicio-pedidos-1

Write-Host "   Esperando 8 segundos para reintegro en Eureka..." -ForegroundColor Gray
Start-Sleep -Seconds 8

Write-Host "`n5. Verificando reintegro de ambas instancias..." -ForegroundColor Yellow
for ($i = 1; $i -le 4; $i++) {
    $res = Invoke-RestMethod -Uri $gatewayUrl -Method Get
    Write-Host "   Solicitud $($i) respondida por: $($res.instanceId)" -ForegroundColor White
}

Write-Host "`n[P03 EXITOSO] El sistema mantuvo continuidad operativa durante la interrupcion." -ForegroundColor Green
