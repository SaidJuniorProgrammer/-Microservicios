Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P02 - Balanceo de Carga HTTP (100 Solicitudes via Gateway)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$gatewayUrl = "http://localhost:8080/api/pedidos/info/instance"
$totalRequests = 100
$results = @{}
$successCount = 0
$errorCount = 0

Write-Host "Enviando $totalRequests peticiones a $gatewayUrl..." -ForegroundColor Yellow

for ($i = 1; $i -le $totalRequests; $i++) {
    try {
        $response = Invoke-RestMethod -Uri $gatewayUrl -Method Get -TimeoutSec 3
        $instance = $response.instanceId
        if (-not $instance) { $instance = "Desconocida" }
        
        if ($results.ContainsKey($instance)) {
            $results[$instance]++
        } else {
            $results[$instance] = 1
        }
        $successCount++
    } catch {
        $errorCount++
        Write-Host "Error en peticion $($i) - Detalle: $_" -ForegroundColor Red
    }
}

Write-Host "`nResultados del balanceo de carga HTTP:" -ForegroundColor Green
Write-Host "----------------------------------------------------------"
Write-Host "Total peticiones exitosas: $successCount" -ForegroundColor Green
Write-Host "Total peticiones con error: $errorCount" -ForegroundColor Red
Write-Host "Distribucion por instancia:" -ForegroundColor Yellow

foreach ($key in $results.Keys) {
    $count = $results[$key]
    $percentage = [math]::Round(($count / $totalRequests) * 100, 2)
    Write-Host "  * $key : $count peticiones ($percentage %)" -ForegroundColor Cyan
}

Write-Host "`n[P02 EXITOSO] Demostracion de balanceo Round-Robin completada." -ForegroundColor Green
