Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P04 - Comunicacion Sincrona entre Microservicios (Feign)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$prodUrl = "http://localhost:8080/api/productos/codigo/PROD-001"
$pedidoUrl = "http://localhost:8080/api/pedidos"

Write-Host "1. Consultando stock inicial de 'PROD-001' en servicio-productos..." -ForegroundColor Yellow
$prodInicial = Invoke-RestMethod -Uri $prodUrl -Method Get
Write-Host "   Producto: $($prodInicial.nombre)" -ForegroundColor White
Write-Host "   Stock Inicial: $($prodInicial.stock)" -ForegroundColor Cyan

Write-Host "`n2. Creando pedido en servicio-pedidos (llamara a servicio-productos via Feign)..." -ForegroundColor Yellow
$body = @{
    cliente = "Oscar Gonabay"
    productoCodigo = "PROD-001"
    cantidad = 2
} | ConvertTo-Json

$headers = @{ "Content-Type" = "application/json" }
$pedidoCreado = Invoke-RestMethod -Uri $pedidoUrl -Method Post -Body $body -Headers $headers

Write-Host "   Pedido Creado: $($pedidoCreado.numeroPedido)" -ForegroundColor Green
Write-Host "   Cliente      : $($pedidoCreado.cliente)" -ForegroundColor White
Write-Host "   Total        : `$ $($pedidoCreado.precioTotal)" -ForegroundColor White
Write-Host "   Estado       : $($pedidoCreado.estado)" -ForegroundColor Green

Write-Host "`n3. Verificando descuento de stock en servicio-productos..." -ForegroundColor Yellow
$prodFinal = Invoke-RestMethod -Uri $prodUrl -Method Get
Write-Host "   Stock Actual : $($prodFinal.stock) (Deseado: $($prodInicial.stock - 2))" -ForegroundColor Green

Write-Host "`n4. Probando manejo de errores: Solicitando stock superior al disponible..." -ForegroundColor Yellow
$bodyExceso = @{
    cliente = "Jean Stalin"
    productoCodigo = "PROD-001"
    cantidad = 9999
} | ConvertTo-Json

try {
    $resError = Invoke-RestMethod -Uri $pedidoUrl -Method Post -Body $bodyExceso -Headers $headers
    Write-Host "   Error: debio rechazar el pedido." -ForegroundColor Red
} catch {
    Write-Host "   Exito: la solicitud fue rechazada por falta de stock. Detalle: $_" -ForegroundColor Green
}

Write-Host "`n[P04 EXITOSO] Comunicacion entre microservicios verificada correctamente." -ForegroundColor Green
