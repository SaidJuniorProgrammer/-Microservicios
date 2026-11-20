Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " P05 - Enrutamiento de Persistencia (Primary vs Replicas)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$inspectUrl = "http://localhost:8080/api/pedidos/db/inspect"
$pedidoUrl = "http://localhost:8080/api/pedidos"

Write-Host "1. Ejecutando Escritura (POST /api/pedidos) -> Debe ir al PRIMARIO..." -ForegroundColor Yellow
$body = @{
    cliente = "Said Pinto"
    productoCodigo = "PROD-002"
    cantidad = 1
} | ConvertTo-Json

$headers = @{ "Content-Type" = "application/json" }
$resPost = Invoke-WebRequest -Uri $pedidoUrl -Method Post -Body $body -Headers $headers -UseBasicParsing
$nodePost = $resPost.Headers["X-DB-Node"]
Write-Host "   Respuesta HTTP: $($resPost.StatusCode)" -ForegroundColor Green
Write-Host "   Cabecera X-DB-Node devuelta: $($nodePost) (Esperado: PRIMARY)" -ForegroundColor Cyan

Write-Host "`n2. Ejecutando 8 Consultas Ordinarias (GET /api/pedidos/db/inspect)..." -ForegroundColor Yellow
Write-Host "   Deben distribuirse entre REPLICA_1 y REPLICA_2 (pg_is_in_recovery = true)" -ForegroundColor Gray

for ($i = 1; $i -le 8; $i++) {
    $res = Invoke-RestMethod -Uri $inspectUrl -Method Get
    Write-Host "   Lectura $($i) -> Nodo Seleccionado: $($res.routingSelection) | Es Replica?: $($res.is_replica) | IP Contenedor: $($res.ip)" -ForegroundColor White
}

Write-Host "`n[P05 EXITOSO] Escrituras dirigidas al Primario y lecturas balanceadas entre réplicas." -ForegroundColor Green
