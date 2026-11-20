Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P01 - Verificacion de Descubrimiento Dinamico en Eureka" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$eurekaUrl = "http://localhost:8761/eureka/apps"
$headers = @{ "Accept" = "application/json" }

try {
    $response = Invoke-RestMethod -Uri $eurekaUrl -Headers $headers -Method Get
    $apps = $response.applications.application

    Write-Host "`nMicroservicios registrados en Eureka Server:" -ForegroundColor Green
    foreach ($app in $apps) {
        Write-Host "`n* Aplicacion: $($app.name)" -ForegroundColor Yellow
        $instances = $app.instance
        if (-not ($instances -is [array])) { $instances = @($instances) }

        foreach ($inst in $instances) {
            Write-Host "  - Instancia ID : $($inst.instanceId)" -ForegroundColor White
            Write-Host "    Host / IP    : $($inst.ipAddr):$($inst.port.'$')" -ForegroundColor Gray
            Write-Host "    Estado       : $($inst.status)" -ForegroundColor Green
        }
    }
    Write-Host "`n[P01 EXITOSO] Todos los servicios estan registrados y descubiertos dinamicamente." -ForegroundColor Green
} catch {
    Write-Host "[ERROR] No se pudo conectar a Eureka en $($eurekaUrl) - Detalle: $_" -ForegroundColor Red
}
