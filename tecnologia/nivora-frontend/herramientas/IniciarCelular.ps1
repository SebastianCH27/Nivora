param(
    [Parameter(Mandatory = $true)]
    [string]$Dispositivo,
    [string]$SdkAndroid = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $SdkAndroid 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "No se encontro adb.exe en $SdkAndroid" }
$flutter = (Get-Command flutter -ErrorAction Stop).Source
Push-Location (Join-Path $PSScriptRoot '..')
try {
    if ($Dispositivo -match '^\d{1,3}(\.\d{1,3}){3}:\d+$') {
        & $adb connect $Dispositivo
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar al celular.' }
    }
    $estado = & $adb -s $Dispositivo get-state 2>&1
    if ($LASTEXITCODE -ne 0 -or "$estado".Trim() -ne 'device') {
        throw 'El celular debe estar conectado y autorizado. Revisa Depuracion inalambrica.'
    }
    & $adb -s $Dispositivo reverse tcp:8082 tcp:8082
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo habilitar la conexion con el backend.' }
    & $flutter run -d $Dispositivo --dart-define=NIVORA_API_URL=http://127.0.0.1:8082
    if ($LASTEXITCODE -ne 0) { throw 'Flutter no pudo iniciar la aplicacion.' }
} finally {
    Pop-Location
}
