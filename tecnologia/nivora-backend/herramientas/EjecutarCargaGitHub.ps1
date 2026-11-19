[CmdletBinding()]
param(
    [ValidateSet('breve','diagnostico-2500','completa')][string]$Perfil = 'breve',
    [ValidateSet(10,20)][int]$PoolConexiones = 10,
    [ValidateRange(1,65535)][int]$PuertoMysql = 3306,
    [ValidatePattern('^[a-zA-Z0-9_]+$')][string]$AdministradorMysql = 'root'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$raiz = Split-Path $PSScriptRoot -Parent
$carpeta = Join-Path $raiz 'target/carga'
$previas = @()
if (Test-Path $carpeta) { $previas = @(Get-ChildItem $carpeta -Directory | Select-Object -ExpandProperty Name) }
$parametros = @{
    SinInteraccion = $true; SuministrosDatos = 5000; PoolConexiones = $PoolConexiones
    PuertoMysql = $PuertoMysql; AdministradorMysql = $AdministradorMysql
}
switch ($Perfil) {
    'breve' { $parametros.MedirRecursos = $true }
    'diagnostico-2500' {
        $parametros.Diagnostico = $true; $parametros.Etapas = @(2500)
        $parametros.DuracionSegundos = 120; $parametros.RampaSegundos = 60
        $parametros.CalentamientoSegundos = 15
    }
    'completa' { $parametros.Completa = $true; $parametros.MedirRecursos = $true }
}
$fallo = $null
try { & (Join-Path $PSScriptRoot 'EjecutarPruebaCarga.ps1') @parametros }
catch { $fallo = $_ }
$nuevas = @()
if (Test-Path $carpeta) {
    $nuevas = @(Get-ChildItem $carpeta -Directory | Where-Object { $_.Name -notin $previas })
}
if ($nuevas.Count -ne 1) {
    if ($null -ne $fallo) { throw $fallo }
    throw 'No se pudo identificar una unica carpeta nueva de resultados.'
}
$salida = $nuevas[0].FullName
$evaluaciones = @()
$filas = New-Object System.Collections.Generic.List[string]
$filas.Add('## Prueba de carga de Nivora')
$filas.Add('')
$filas.Add("Perfil: $Perfil. Pool: $PoolConexiones. Ejecucion: $($nuevas[0].Name).")
$filas.Add("Commit: $env:GITHUB_SHA. Runner: $env:RUNNER_OS / $env:RUNNER_ARCH.")
$filas.Add('Servidor, MySQL y generador comparten la VM; recursos instrumentados cada 5 segundos.')
$filas.Add('')
$filas.Add('| Usuarios | Ventana medida | Consultas | p95 maximo | Latencia y errores | Sincronizacion | Alertas |')
$filas.Add('|---:|---:|---:|---:|---|---|---|')
$recursosValidos = $true
foreach ($archivo in @(Get-ChildItem $salida -File -Filter '*-evaluacion.json' | Sort-Object Name)) {
    $prefijo = $archivo.FullName -replace '-evaluacion\.json$', ''
    $e = Get-Content $archivo.FullName -Raw | ConvertFrom-Json
    $r = Get-Content ($prefijo + '.json') -Raw | ConvertFrom-Json
    $d = Get-Content ($prefijo + '-diagnostico.json') -Raw | ConvertFrom-Json
    $evaluaciones += $e
    $consultas = ($r.operaciones | Measure-Object -Property solicitudes -Sum).Sum
    $p95 = ($r.operaciones | Measure-Object -Property p95MsHasta -Maximum).Maximum
    $filas.Add("| $($r.usuarios) | $($r.medicionSegundos) s | $consultas | $p95 ms | $($e.cumpleUmbralesDeEstaEtapa) | $($e.sincronizacionDuranteMedicion) | $($e.alertasGeneradasDuranteMedicion) |")
    if ($d.backend.muestras -lt 2 -or $d.generador.muestras -lt 2 -or
            $null -eq $d.backend.cpuSistemaPorcentaje -or $null -eq $d.generador.cpuProcesoPorcentajeDeUnNucleo) {
        $recursosValidos = $false
    }
}
# Lista cerrada: nunca publicar usuarios.csv, claves ni logs completos del servidor.
foreach ($archivo in @(Get-ChildItem $salida -File | Sort-Object Name)) {
    if ($archivo.Name -match '^(entorno|mysql)\.json$|^etapa-[0-9]+-[0-9]+(?:-(?:evaluacion|diagnostico|antes|despues))?\.json$') {
        Write-Host "::group::Informe $($archivo.Name)"
        Get-Content $archivo.FullName -Raw | Write-Host
        Write-Host '::endgroup::'
    }
}
if ($evaluaciones.Count -eq 0) { $recursosValidos = $false }
$cumple = $null -eq $fallo -and $evaluaciones.Count -gt 0 -and $recursosValidos
foreach ($e in $evaluaciones) {
    $cumple = $cumple -and $e.cumpleUmbralesDeEstaEtapa -and
        $e.sincronizacionDuranteMedicion -and $e.alertasGeneradasDuranteMedicion
}
if ($Perfil -eq 'completa') {
    $cumple = $cumple -and $evaluaciones.Count -eq 4 -and $evaluaciones[-1].cubre5000Durante30Minutos
}
$filas.Add('')
$filas.Add("Resultado del perfil: $cumple. Muestras de recursos suficientes: $recursosValidos.")
$filas.Add('Una ejecucion breve o diagnostica no acredita la meta de 5000 usuarios durante 30 minutos.')
$filas.Add('EQ-02 requiere comparar recursos y demostrar una carga sostenible mayor.')
$texto = $filas -join [Environment]::NewLine
$texto | Write-Host
if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_STEP_SUMMARY)) {
    Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Value $texto -Encoding UTF8
}
if ($null -ne $fallo) { throw $fallo }
if (-not $cumple) { throw 'El perfil no cumple todos sus controles. Revisa los JSON en los grupos Informe de este paso.' }
