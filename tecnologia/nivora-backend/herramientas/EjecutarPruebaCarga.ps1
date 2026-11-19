[CmdletBinding()]
param(
    [switch]$Completa,
    [switch]$Diagnostico,
    [switch]$MedirRecursos,
    [switch]$SinInteraccion,
    [switch]$DatosMixtos,
    [ValidateRange(0,100000)][int]$SuministrosDatos = 0,
    [ValidateRange(1,10000)][int[]]$Etapas = @(20),
    [ValidateRange(5,3600)][int]$DuracionSegundos = 45,
    [ValidateRange(0,600)][int]$RampaSegundos = 5,
    [ValidateRange(0,600)][int]$CalentamientoSegundos = 5,
    [ValidateRange(10,60000)][int]$PausaMs = 1000,
    [ValidateRange(128,32768)][int]$MemoriaBackendMb = 512,
    [ValidateRange(128,32768)][int]$MemoriaGeneradorMb = 512,
    [ValidateRange(2,200)][int]$PoolConexiones = 10,
    [ValidateRange(10,1000)][int]$HilosHttp = 200,
    [ValidateRange(1,65535)][int]$PuertoMysql = 3306,
    [ValidateRange(20000,60000)][int]$PuertoBackend = 28082,
    [ValidateRange(20000,60000)][int]$PuertoFuente = 28081,
    [ValidatePattern('^[a-zA-Z0-9_]+$')][string]$AdministradorMysql = 'root'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
if ($SinInteraccion -and [string]::IsNullOrWhiteSpace($env:NIVORA_CARGA_DB_ADMIN_CLAVE)) {
    throw 'SinInteraccion requiere NIVORA_CARGA_DB_ADMIN_CLAVE en el entorno del proceso.'
}
$instrumentada = $Diagnostico -or $MedirRecursos
$raiz = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $raiz 'target/nivora-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw 'Primero ejecuta mvnw.cmd clean verify desde el backend.' }
$java = (Get-Command java -ErrorAction Stop).Source
$javac = (Get-Command javac -ErrorAction Stop).Source
if ($PuertoBackend -eq $PuertoFuente) { throw 'Los dos puertos de carga deben ser diferentes.' }
foreach ($puerto in @($PuertoBackend, $PuertoFuente)) {
    $prueba = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $puerto)
    try { $prueba.Start() } finally { $prueba.Stop() }
}
if ($Completa) {
    $Etapas = @(500,1000,2500,5000)
    $RampaSegundos = 60
    $CalentamientoSegundos = 15
}
if ($Diagnostico -and $Etapas.Count -ne 1) {
    throw 'El diagnostico acepta una sola etapa. Usa, por ejemplo, -Etapas 2500 -SuministrosDatos 5000 -Diagnostico.'
}
$usuarios = [int]($Etapas | Measure-Object -Maximum).Maximum
if ($SuministrosDatos -gt 0) {
    if ($SuministrosDatos -lt $usuarios) { throw 'SuministrosDatos debe cubrir todos los usuarios de las etapas.' }
    $usuarios = $SuministrosDatos
}
$id = [Guid]::NewGuid().ToString('N').Substring(0,12)
$base = 'nivora_carga_' + $id
$cuenta = 'ncarga_' + $id
$salida = Join-Path $raiz ('target/carga/' + $id)
$clases = Join-Path $salida 'clases'
$null = New-Item -ItemType Directory -Force -Path $clases
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archivo = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entrada = @($archivo.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/mysql-connector-j-[^/]+\.jar$' })
    if ($entrada.Count -ne 1) { throw 'El JAR no contiene un unico controlador MySQL.' }
    $controlador = Join-Path $salida 'mysql-connector-j.jar'
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entrada[0], $controlador, $true)
} finally { $archivo.Dispose() }
& $javac --release 21 -encoding UTF-8 -d $clases (Join-Path $PSScriptRoot 'PrepararDatosCarga.java') (Join-Path $PSScriptRoot 'PruebaCargaConsultas.java') (Join-Path $PSScriptRoot 'DiagnosticoCarga.java')
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron compilar las herramientas. Requieren JDK 21.' }
$argumentosDiagnosticoBackend = @()
$argumentosDiagnosticoGenerador = @()
if ($instrumentada) {
    . (Join-Path $PSScriptRoot 'DiagnosticoCarga.ps1')
    $agente = Crear-AgenteCarga $clases $salida
    $diagBackend = Join-Path $salida 'diagnostico-backend'
    $diagGenerador = Join-Path $salida 'diagnostico-generador'
    $rutaRelativa = 'target/carga/' + $id
    $argumentosDiagnosticoBackend = @("-javaagent:$agente=$diagBackend",
        "-Xlog:gc*,safepoint:file=$rutaRelativa/backend-gc.log:time,uptime,level,tags:filecount=2,filesize=8m")
    $argumentosDiagnosticoGenerador = @("-javaagent:$agente=$diagGenerador",
        "-Xlog:gc*,safepoint:file=$rutaRelativa/generador-gc.log:time,uptime,level,tags:filecount=2,filesize=8m")
}
$classpath = $clases + [IO.Path]::PathSeparator + $controlador
$urlMysql = "jdbc:mysql://127.0.0.1:$PuertoMysql/?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true"
$urlBackend = "http://127.0.0.1:$PuertoBackend"
$nombres = @('NIVORA_CARGA_DB_USUARIO','NIVORA_CARGA_DB_CLAVE','NIVORA_CARGA_DB_ADMIN',
    'NIVORA_CARGA_DB_ADMIN_CLAVE','NIVORA_DB_CLAVE','NIVORA_WEB_PUSH_HABILITADO','NIVORA_CARGA_INTERVALOS')
$anteriores = @{}
foreach ($nombre in $nombres) { $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$procesos = New-Object System.Collections.Generic.List[System.Diagnostics.Process]
$preparacionIntentada = $false
$carpetaAnterior = Get-Location

function Invocar-Datos([string]$modo) {
    $resultado = & $java -cp $classpath PrepararDatosCarga $modo $urlMysql $base $usuarios $salida $PuertoFuente
    if ($LASTEXITCODE -ne 0) { throw "Fallo la operacion de carga: $modo. Revisa su salida." }
    return $resultado
}
function Iniciar-Java([string[]]$argumentos, [string]$nombre) {
    # Start-Process une los argumentos en Windows; entrecomillar conserva rutas con espacios.
    $citados = @($argumentos | ForEach-Object { '"' + $_ + '"' })
    $p = Start-Process -FilePath $java -ArgumentList $citados -WorkingDirectory $raiz -PassThru `
        -RedirectStandardOutput (Join-Path $salida ($nombre + '.log')) `
        -RedirectStandardError (Join-Path $salida ($nombre + '-error.log'))
    $procesos.Add($p)
    # Windows PowerShell necesita conservar el handle antes de terminar el proceso
    # para informar ExitCode cuando se redirigen las salidas (PowerShell #5421).
    $null = $p.Handle
    return $p
}
function Confirmar-SalidaGenerador($proceso, [string]$rutaInforme) {
    $codigoSalida = $proceso.ExitCode
    if ($null -eq $codigoSalida) {
        throw "No se pudo obtener el codigo de salida del generador (ExitCode nulo). Consulta los logs y $rutaInforme."
    }
    if ($codigoSalida -ne 0) {
        throw "El generador termino con codigo $codigoSalida. Consulta los logs y $rutaInforme."
    }
    if (-not (Test-Path -LiteralPath $rutaInforme -PathType Leaf)) {
        throw "El generador termino con codigo 0 pero falta el informe: $rutaInforme."
    }
}
function Guardar-Json($valor, [string]$ruta) {
    $valor | ConvertTo-Json -Depth 12 | Set-Content -Path $ruta -Encoding UTF8
}
try {
    Set-Location $raiz
    if (-not $SinInteraccion) {
        $clave = Read-Host 'Contrasena del administrador de MySQL para crear el entorno temporal' -AsSecureString
        $env:NIVORA_CARGA_DB_ADMIN_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password
    }
    $env:NIVORA_CARGA_DB_ADMIN = $AdministradorMysql
    $env:NIVORA_CARGA_DB_USUARIO = $cuenta
    $env:NIVORA_CARGA_DB_CLAVE = [Guid]::NewGuid().ToString('N') + 'Carga9!'
    $env:NIVORA_CARGA_INTERVALOS = $DatosMixtos.IsPresent.ToString().ToLowerInvariant()
    $preparacionIntentada = $true
    Invocar-Datos 'preparar' | Write-Host
    # Los hijos reciben solo la cuenta de carga; la clave administrativa se conserva en esta sesion.
    $claveAdmin = $env:NIVORA_CARGA_DB_ADMIN_CLAVE
    Remove-Item Env:NIVORA_CARGA_DB_ADMIN_CLAVE
    $env:NIVORA_DB_CLAVE = $env:NIVORA_CARGA_DB_CLAVE
    $env:NIVORA_WEB_PUSH_HABILITADO = 'false'
    $fuente = Iniciar-Java @('-Xmx128m','-cp',$classpath,'PrepararDatosCarga','fuente',
        $urlMysql,$base,[string]$usuarios,$salida,[string]$PuertoFuente) 'fuente'
    $argumentos = @("-Xmx${MemoriaBackendMb}m") + $argumentosDiagnosticoBackend + @('-jar',$jar,"--server.port=$PuertoBackend",
        "--spring.datasource.url=$($urlMysql.Replace('/?', '/' + $base + '?'))",
        "--spring.datasource.username=$cuenta","--spring.datasource.hikari.maximum-pool-size=$PoolConexiones",
        "--server.tomcat.threads.max=$HilosHttp","--nivora.empresa-agua.url-base=http://127.0.0.1:$PuertoFuente",
        '--nivora.sincronizacion.intervalo-ms=1000','--nivora.sincronizacion.maximo-intentos-por-ciclo=1',
        '--nivora.sincronizacion.intervalo-completo-segundos=30','--nivora.web-push.habilitado=false',
        '--nivora.tarifas.sincronizacion.habilitada=true',
        '--nivora.tarifas.sincronizacion.intervalo-ms=1000','--nivora.tarifas.sincronizacion.maximo-intentos-por-ciclo=1',
        '--nivora.tarifas.sincronizacion.intervalo-completo-segundos=30')
    if ($instrumentada) {
        $argumentos += @('--spring.datasource.hikari.register-mbeans=true',
            '--spring.datasource.hikari.pool-name=NivoraCarga','--server.tomcat.mbeanregistry.enabled=true')
    }
    $backend = Iniciar-Java $argumentos 'backend'
    $listo = $false
    for ($i = 0; $i -lt 90; $i++) {
        if ($backend.HasExited -or $fuente.HasExited) { throw 'Un proceso termino durante el arranque. Revisa los logs de carga.' }
        try {
            $salud = Invoke-RestMethod -UseBasicParsing -Uri ($urlBackend + '/actuator/health') -TimeoutSec 2
            if ($salud.status -eq 'UP') { $listo = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $listo) { throw 'El entorno de carga no alcanzo el estado UP.' }
    if ($instrumentada -and -not (Test-Path (Join-Path $diagBackend 'iniciado.txt'))) {
        throw 'No se inicio el diagnostico del backend. Revisa backend-error.log.'
    }
    $versionJava = (& $java --version | Out-String).Trim()
    $ram = $null
    if (Get-Command Get-CimInstance -ErrorAction SilentlyContinue) {
        $ram = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
    } elseif (Test-Path '/proc/meminfo') {
        $lineaRam = Get-Content '/proc/meminfo' | Where-Object { $_ -match '^MemTotal:\s+(\d+)' } | Select-Object -First 1
        if ($lineaRam -match '^MemTotal:\s+(\d+)') { $ram = [long]$Matches[1] * 1024 }
    }
    Guardar-Json ([ordered]@{
        creadoUTC = [DateTime]::UtcNow.ToString('o'); equipo = [Environment]::MachineName
        sistema = [Environment]::OSVersion.VersionString; procesadores = [Environment]::ProcessorCount
        ramFisicaBytes = $ram; mysql = (Get-Content (Join-Path $salida 'mysql.json') -Raw | ConvertFrom-Json)
        java = $versionJava; jarSHA256 = (Get-FileHash $jar -Algorithm SHA256).Hash
        baseTemporal = $base; usuariosFicticios = $usuarios; lecturasPorSuministro = 4
        recibosPorSuministro = 1; periodosConConsumo = 3; sesionesIndependientes = $usuarios
        memoriaBackendMb = $MemoriaBackendMb; memoriaGeneradorMb = $MemoriaGeneradorMb
        poolConexiones = $PoolConexiones; hilosHttp = $HilosHttp; pausaMs = $PausaMs
        diagnostico = [bool]$Diagnostico; medirRecursos = [bool]$instrumentada; intervaloDiagnosticoSegundos = 5
        sinInteraccion = [bool]$SinInteraccion; powershell = $PSVersionTable.PSVersion.ToString()
        etapas = $Etapas; completa = [bool]$Completa; generadorYServidorEnMismoEquipo = $true
        sincronizacionLecturasIntervaloMs = 1000; intentosPorCiclo = 1; intervaloCompletoSegundos = 30
        sincronizacionTarifasHabilitada = $true; datosMixtos = $DatosMixtos.IsPresent
    }) (Join-Path $salida 'entorno.json')
    $numero = 0
    foreach ($cantidad in $Etapas) {
        $numero++
        $prefijo = Join-Path $salida ("etapa-$numero-$cantidad")
        $duracion = $DuracionSegundos
        if ($Completa) { if ($cantidad -eq 5000) { $duracion = 1800 } else { $duracion = 120 } }
        Remove-Item (Join-Path $salida 'ventana.txt') -ErrorAction SilentlyContinue
        $antes = Invocar-Datos 'estado' | ConvertFrom-Json
        Guardar-Json $antes ($prefijo + '-antes.json')
        Write-Host "Etapa $numero : $cantidad usuarios; $duracion segundos de medicion."
        $reloj = [Diagnostics.Stopwatch]::StartNew()
        $cpuAnterior = $backend.TotalProcessorTime.TotalMilliseconds
        $msAnterior = 0
        $muestras = New-Object System.Collections.Generic.List[object]
        $muestrasMysql = New-Object System.Collections.Generic.List[object]
        $ultimaMysql = $null
        $proximaMysql = 0.0
        $capturaSolicitada = $false
        $argumentosGenerador = @("-Xmx${MemoriaGeneradorMb}m") + $argumentosDiagnosticoGenerador + @('-cp',$clases,'PruebaCargaConsultas',
            $urlBackend,(Join-Path $salida 'usuarios.csv'),[string]$cantidad,[string]$RampaSegundos,
            [string]$CalentamientoSegundos,[string]$duracion,[string]$PausaMs,($prefijo + '.json'))
        $generador = Iniciar-Java $argumentosGenerador "etapa-$numero"
        while (-not $generador.HasExited) {
            Start-Sleep -Seconds 1
            $backend.Refresh()
            if ($backend.HasExited -or $fuente.HasExited) { throw 'El entorno se detuvo durante la carga.' }
            $ms = $reloj.Elapsed.TotalMilliseconds
            $cpu = $backend.TotalProcessorTime.TotalMilliseconds
            $muestras.Add([pscustomobject]@{
                utc = [DateTime]::UtcNow.ToString('o'); memoriaProcesoBytes = $backend.WorkingSet64
                cpuPorcentajeDeUnNucleo = [Math]::Round(100 * ($cpu - $cpuAnterior) / [Math]::Max(1, $ms - $msAnterior), 2)
            })
            if ($instrumentada -and $ms -ge $proximaMysql) {
                $medidaMysql = Medir-MySqlCarga $ms $ultimaMysql
                if ($null -ne $medidaMysql) { $muestrasMysql.Add($medidaMysql); $ultimaMysql = $medidaMysql }
                $proximaMysql = $ms + 5000
            }
            if ($Diagnostico -and -not $capturaSolicitada -and
                    $reloj.Elapsed.TotalSeconds -ge ($RampaSegundos + $CalentamientoSegundos + 15)) {
                Set-Content (Join-Path $diagBackend 'capturar-hilos.flag') 'capturar'
                $capturaSolicitada = $true
            }
            $cpuAnterior = $cpu
            $msAnterior = $ms
            if ($muestras.Count % 5 -eq 0) { Write-Host "Carga en curso: $([int]$reloj.Elapsed.TotalSeconds) segundos." }
        }
        $generador.WaitForExit()
        $muestras | Export-Csv -Path ($prefijo + '-recursos.csv') -NoTypeInformation -Encoding UTF8
        Confirmar-SalidaGenerador $generador ($prefijo + '.json')
        Write-Host "Generador finalizado. Codigo de salida: $($generador.ExitCode)."
        $informe = Get-Content ($prefijo + '.json') -Raw | ConvertFrom-Json
        if ($instrumentada) {
            $muestrasMysql | Export-Csv -Path ($prefijo + '-mysql.csv') -NoTypeInformation -Encoding UTF8
            Guardar-DiagnosticoCarga $prefijo $salida $informe
            Copy-Item (Join-Path $diagGenerador 'jvm.csv') ($prefijo + '-generador.csv')
            Write-Host ('Resumen diagnostico: ' + $prefijo + '-diagnostico.json')
            Get-Content ($prefijo + '-diagnostico.json') -Raw | Write-Host
        }
        @($informe.inicioMedicionUTC, $informe.finMedicionUTC) | Set-Content (Join-Path $salida 'ventana.txt') -Encoding ASCII
        $despues = Invocar-Datos 'estado' | ConvertFrom-Json
        Guardar-Json $despues ($prefijo + '-despues.json')
        $progreso = $despues.suministrosConExitoDuranteMedicion -gt 0
        $alertaDuranteCarga = $despues.alertasDuranteMedicion -gt 0
        $tarifasDuranteCarga = $despues.tarifasConExitoDuranteMedicion -gt 0
        $continuidad = $progreso -and $alertaDuranteCarga -and $tarifasDuranteCarga -and ($despues.lecturasDuranteMedicion -gt 0)
        Guardar-Json ([ordered]@{
            sincronizacionDuranteMedicion = $progreso; alertasGeneradasDuranteMedicion = $alertaDuranteCarga
            tarifasActualizadasDuranteMedicion = $despues.tarifasConExitoDuranteMedicion
            cumpleContinuidad = $continuidad
            cumpleEtapaCompleta = $informe.cumpleUmbralesDeEstaEtapa -and $continuidad
            alertasAlFinal = $despues.alertasRegistradas
            lecturasIncorporadasDuranteMedicion = $despues.lecturasDuranteMedicion
            cumpleUmbralesDeEstaEtapa = $informe.cumpleUmbralesDeEstaEtapa
            cubre5000Durante30Minutos = $informe.cubre5000Durante30Minutos
            requiereComparacionDeRecursos = $true
        }) ($prefijo + '-evaluacion.json')
        Write-Host "p95 y errores dentro de los limites: $($informe.cumpleUmbralesDeEstaEtapa). Sincronizacion durante medicion: $progreso."
        if (-not ($informe.cumpleUmbralesDeEstaEtapa -and $continuidad)) {
            Write-Host 'La etapa no satisface los umbrales. Conserva los informes y revisa el cuello de botella antes de ampliar la carga.'
            break
        }
    }
    Write-Host "Informes disponibles en: $salida"
} finally {
    foreach ($p in $procesos) {
        if (-not $p.HasExited) { $p.Kill(); $p.WaitForExit() }
    }
    if ($preparacionIntentada) {
        if (Get-Variable claveAdmin -ErrorAction SilentlyContinue) { $env:NIVORA_CARGA_DB_ADMIN_CLAVE = $claveAdmin }
        try { Invocar-Datos 'limpiar' | Write-Host }
        catch { Write-Warning "No se retiro el entorno temporal $base / $cuenta. Ejecuta la limpieza indicada en la documentacion." }
    }
    foreach ($nombre in $nombres) { [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process') }
    Set-Location $carpetaAnterior
}
