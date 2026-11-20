# Compatible con Windows PowerShell 5.1. Los datos sensibles permanecen en memoria.
param([switch]$SoloComprobar)
$ErrorActionPreference = 'Stop'

function Leer-Secreto([string]$Mensaje) {
    $seguro = Read-Host $Mensaje -AsSecureString
    try { return [System.Net.NetworkCredential]::new('', $seguro).Password }
    finally { $seguro.Dispose() }
}

function Mostrar-CausaProveedor($Datos) {
    # Se clasifica el mensaje, sin imprimir contenido recibido del proveedor.
    if ($Datos.message -isnot [string]) { return }
    $mensaje = $Datos.message
    if ($mensaje -match 'expir|caduc|vencid') {
        Write-Host 'El mensaje del proveedor indica una credencial vencida.'
    } elseif ($mensaje -match 'limit|cuota|quota') {
        Write-Host 'El mensaje del proveedor indica un limite de consultas.'
    } elseif ($mensaje -match 'token.*(not found|no.*encontr)') {
        Write-Host 'El mensaje del proveedor indica que no encontro el token.'
    } elseif ($mensaje -match 'token.*(invalid|inv.lid|incorrect|rechaz)') {
        Write-Host 'El mensaje del proveedor indica una credencial invalida.'
    }
}

$codigoSalida = 1
$cliente = $null
$respuesta = $null
$token = $null
$dni = $null
try {
    $modulo = Split-Path $PSScriptRoot -Parent
    $jar = Join-Path $modulo 'target\nivora-backend-0.0.1-SNAPSHOT.jar'
    if (-not $SoloComprobar) {
        if (-not (Test-Path $jar)) {
            Write-Host 'Primero genera el JAR con mvnw.cmd clean verify.'
            exit 1
        }
        $java = (Get-Command java -ErrorAction Stop).Source
    }
    Add-Type -AssemblyName System.Net.Http
    Write-Host 'Abre el panel de APIsPERU y entra al servicio Consulta DNI/RUC.'
    $pausa = Read-Host 'AHORA pulsa Copiar junto a tu token, vuelve aqui y presiona Enter' -AsSecureString
    $pausa.Dispose()
    $token = ([string](Get-Clipboard -Raw)).Trim()
    Write-Host ('Caracteres copiados del token: ' + $token.Length)
    if ($token.Length -le 1) {
        Write-Host 'No se copio un token completo. Usa el boton Copiar del panel.'
        exit 1
    }
    if ($token -notmatch '^[A-Za-z0-9._~+/\-]+=*$') {
        Write-Host 'El texto copiado no tiene el formato del token de Consulta DNI/RUC.'
        Write-Host 'Copia solo su valor, sin Bearer, comillas ni comandos.'
        exit 1
    }
    $dni = (Leer-Secreto 'Escribe el DNI de 8 digitos que usas en la aplicacion').Trim()
    if ($dni -notmatch '^[0-9]{8}$') {
        Write-Host 'El DNI debe tener exactamente 8 digitos.'
        exit 1
    }

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $cliente = [System.Net.Http.HttpClient]::new($handler)
    $cliente.Timeout = [TimeSpan]::FromSeconds(25)
    $cliente.MaxResponseContentBufferSize = 16384
    $cliente.DefaultRequestHeaders.Add('Accept', 'application/json')
    # Contrato oficial: token en la consulta. No imprimir esta URI.
    $uri = 'https://dniruc.apisperu.com/api/v1/dni/' + $dni + '?token=' + [Uri]::EscapeDataString($token)
    $respuesta = $cliente.GetAsync($uri).GetAwaiter().GetResult()
    $estado = [int]$respuesta.StatusCode
    Write-Host ('HTTP del proveedor: ' + $estado)
    if ($estado -ne 200) {
        try {
            $errorProveedor = $respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult() |
                ConvertFrom-Json -ErrorAction Stop
            Mostrar-CausaProveedor $errorProveedor
        } catch { }
        switch ($estado) {
            401 { Write-Host 'El proveedor rechazo la credencial con el formato documentado.' }
            403 { Write-Host 'El proveedor denego el acceso a esta consulta.' }
            429 { Write-Host 'El proveedor limito las consultas; espera antes de repetir.' }
            404 { Write-Host 'El proveedor no confirmo el documento solicitado.' }
            default { Write-Host 'El proveedor no devolvio una respuesta utilizable.' }
        }
        exit 1
    }
    if ($respuesta.Content.Headers.ContentType.MediaType -ne 'application/json') {
        Write-Host 'La respuesta del proveedor no tiene el formato JSON esperado.'
        exit 1
    }
    $datos = $respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json -ErrorAction Stop
    if (($null -ne $datos.success -and ($datos.success -isnot [bool] -or -not $datos.success)) -or
        $datos.dni -isnot [string] -or $datos.dni -cne $dni -or
        $datos.nombres -isnot [string] -or [string]::IsNullOrWhiteSpace($datos.nombres)) {
        Mostrar-CausaProveedor $datos
        Write-Host 'La respuesta no confirma el DNI en el formato esperado por Nivora.'
        exit 1
    }
    Write-Host 'Consulta DNI confirmada.'
    $codigoSalida = 0
    if (-not $SoloComprobar) {
        $clave = Leer-Secreto 'Contrasena de nivora_app'
        # La JVM recibe el mismo token que acaba de superar la comprobacion.
        $env:NIVORA_IDENTIDAD_API_TOKEN = $token
        $env:NIVORA_DB_CLAVE = $clave
        Write-Host 'Iniciando Nivora en el puerto 8082. Deja esta ventana abierta.'
        Push-Location $modulo
        try {
            & $java '-Xms64m' '-Xmx512m' '-jar' $jar
            $codigoSalida = $LASTEXITCODE
        } finally { Pop-Location }
    }
} catch {
    # Las excepciones de HTTP pueden incluir la URI con token y DNI.
    Write-Host 'No se pudo completar la comprobacion o el arranque. No se mostraron datos sensibles.'
    $codigoSalida = 1
} finally {
    if ($null -ne $respuesta) { $respuesta.Dispose() }
    if ($null -ne $cliente) { $cliente.Dispose() }
    Remove-Item Env:NIVORA_IDENTIDAD_API_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:NIVORA_DB_CLAVE -ErrorAction SilentlyContinue
    $token = $null
    $dni = $null
    $clave = $null
    $uri = $null
    $datos = $null
}
exit $codigoSalida
