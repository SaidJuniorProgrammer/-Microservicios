Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "     SUITE COMPLETA DE PRUEBAS EXPERIMENTALES (P01-P08)    " -ForegroundColor Magenta
Write-Host "   ARQUITECTURA DE MICROSERVICIOS Y PERSISTENCIA DISTRIBUIDA" -ForegroundColor Magenta
Write-Host "   Integrantes: Oscar Gonabay, Jean Stalin, Said Pinto     " -ForegroundColor Magenta
Write-Host "==========================================================`n" -ForegroundColor Magenta

$scripts = @(
    "p01_descubrimiento.ps1",
    "p02_balanceo_http.ps1",
    "p03_fallo_instancia.ps1",
    "p04_comunicacion_servicios.ps1",
    "p05_enrutamiento_db.ps1",
    "p06_fallo_replica.ps1",
    "p07_consistencia_lectura.ps1",
    "p08_recuperacion_replica.ps1"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($s in $scripts) {
    $path = Join-Path $scriptDir $s
    Write-Host "`n>>> EJECUTANDO ESCENARIO: $s <<<" -ForegroundColor Yellow
    & $path
    Start-Sleep -Seconds 2
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  SUITE FINALIZADA - REVISE EL RESULTADO DE CADA ESCENARIO  " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
