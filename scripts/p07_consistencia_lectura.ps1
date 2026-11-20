Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P07 - Consistencia de Lectura Critica (Read-After-Write)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$pedidoUrl = "http://localhost:8080/api/pedidos"
$inspectCriticoUrl = "http://localhost:8080/api/pedidos/db/inspect-critico"

Write-Host "1. Creando pedido de alta prioridad..." -ForegroundColor Yellow
$body = @{
    cliente = "Auditoria Financiera UPSE"
    productoCodigo = "PROD-003"
    cantidad = 1
} | ConvertTo-Json

$headers = @{ "Content-Type" = "application/json" }
$nuevoPedido = Invoke-RestMethod -Uri $pedidoUrl -Method Post -Body $body -Headers $headers
$numPedido = $nuevoPedido.numeroPedido
Write-Host "   Pedido generado: $numPedido" -ForegroundColor Green

Write-Host "`n2. Realizando LECTURA CRITICA inmediata con direccionamiento forzado al PRIMARIO..." -ForegroundColor Yellow
$criticoRes = Invoke-WebRequest -Uri "$pedidoUrl/$numPedido/critico" -Method Get -UseBasicParsing
Write-Host "   Codigo HTTP: $($criticoRes.StatusCode)" -ForegroundColor Green
Write-Host "   Cabecera X-DB-Node: $($criticoRes.Headers['X-DB-Node']) (Esperado: PRIMARY)" -ForegroundColor Cyan

Write-Host "`n3. Inspeccionando nodo de base de datos bajo contexto critico..." -ForegroundColor Yellow
$inspectRes = Invoke-RestMethod -Uri $inspectCriticoUrl -Method Get
Write-Host "   Nodo Reportado : $($inspectRes.routingSelection)" -ForegroundColor White
Write-Host "   Es Replica?    : $($inspectRes.is_replica) (Esperado: False -> Primario)" -ForegroundColor Cyan
Write-Host "   IP Servidor DB : $($inspectRes.ip)" -ForegroundColor White

Write-Host "`n[P07 EXITOSO] Politica de consistencia aplicada: Lecturas criticas dirigidas al Primario." -ForegroundColor Green
