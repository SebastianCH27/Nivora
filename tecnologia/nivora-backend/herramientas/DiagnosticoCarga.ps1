# Funciones del diagnostico local; utilizadas por EjecutarPruebaCarga.ps1.
function Crear-AgenteCarga([string]$clases, [string]$carpeta) {
    $manifiesto = Join-Path $carpeta 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifiesto, "Manifest-Version: 1.0`r`nPremain-Class: DiagnosticoCarga`r`n`r`n", [Text.UTF8Encoding]::new($false))
    $ruta = Join-Path $carpeta 'diagnostico-agente.jar'
    $zip = [IO.Compression.ZipFile]::Open($ruta, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $manifiesto, 'META-INF/MANIFEST.MF')
        $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,
            (Join-Path $clases 'DiagnosticoCarga.class'), 'DiagnosticoCarga.class')
    } finally { $zip.Dispose() }
    return $ruta
}

function Medir-MySqlCarga([double]$milisegundos, $anterior) {
    $cpu = 0.0; $memoria = 0L; $identificadores = @()
    foreach ($p in @(Get-Process -Name mysqld -ErrorAction SilentlyContinue)) {
        try {
            $p.Refresh()
            $cpu += $p.TotalProcessorTime.TotalMilliseconds
            $memoria += $p.WorkingSet64
            $identificadores += $p.Id
        } catch { }
    }
    if ($identificadores.Count -eq 0) { return $null }
    $ids = ($identificadores | Sort-Object) -join ';'
    $porcentaje = $null
    if ($null -ne $anterior -and $anterior.pids -eq $ids -and $milisegundos -gt $anterior.ms) {
        $porcentaje = [Math]::Round(100 * ($cpu - $anterior.cpuMs) / ($milisegundos - $anterior.ms), 2)
    }
    return [pscustomobject]@{utc=[DateTime]::UtcNow.ToString('o'); pids=$ids; ms=$milisegundos;
        cpuMs=$cpu; cpuPorcentajeDeUnNucleo=$porcentaje; memoriaProcesoBytes=$memoria}
}

function Leer-MuestrasCarga([string]$ruta, $inicio, $fin) {
    if (-not (Test-Path -LiteralPath $ruta)) { return }
    Import-Csv -LiteralPath $ruta | Where-Object {
        if ([string]::IsNullOrWhiteSpace($_.utc)) { return $false }
        $fecha = [DateTimeOffset]::MinValue
        if (-not [DateTimeOffset]::TryParse($_.utc, [ref]$fecha)) { return $false }
        $fecha -ge $inicio -and $fecha -lt $fin
    }
}

function Resumir-ValorCarga($filas, [string]$propiedad) {
    $valores = @($filas | ForEach-Object {
        $valor = $_.$propiedad
        if (-not [string]::IsNullOrWhiteSpace([string]$valor) -and [double]$valor -ge 0) { [double]$valor }
    })
    if ($valores.Count -eq 0) { return $null }
    $resumen = $valores | Measure-Object -Average -Minimum -Maximum
    return [ordered]@{muestras=$valores.Count; promedio=[Math]::Round($resumen.Average,3);
        minimo=$resumen.Minimum; maximo=$resumen.Maximum}
}

function Resumir-JvmCarga([string]$ruta, $inicio, $fin) {
    $filas = @(Leer-MuestrasCarga $ruta $inicio $fin | Where-Object { $_.muestraCompleta -eq 'OK' })
    $gcMs = $null; $gcCantidad = $null; $cpu = $null
    if ($filas.Count -gt 1) {
        $gcMs = [Math]::Max(0, [long]$filas[-1].gcTiempoMs - [long]$filas[0].gcTiempoMs)
        $gcCantidad = [Math]::Max(0, [long]$filas[-1].gcCantidad - [long]$filas[0].gcCantidad)
        $segundos = ([DateTimeOffset]::Parse($filas[-1].utc) - [DateTimeOffset]::Parse($filas[0].utc)).TotalSeconds
        if ($segundos -gt 0 -and [long]$filas[0].cpuProcesoNanos -ge 0 -and [long]$filas[-1].cpuProcesoNanos -ge [long]$filas[0].cpuProcesoNanos) {
            $cpu = [Math]::Round(100 * ([long]$filas[-1].cpuProcesoNanos - [long]$filas[0].cpuProcesoNanos) / ($segundos * 1000000000), 3)
        }
    }
    return [ordered]@{
        muestras=$filas.Count
        cpuProcesoPorcentajeDeUnNucleo=$cpu
        cpuSistemaPorcentaje=(Resumir-ValorCarga $filas 'cpuSistemaPorcentaje')
        memoriaFisicaLibreBytes=(Resumir-ValorCarga $filas 'memoriaFisicaLibreBytes')
        heapUsadoBytes=(Resumir-ValorCarga $filas 'heapUsadoBytes')
        conexionesActivas=(Resumir-ValorCarga $filas 'conexionesActivas')
        conexionesInactivas=(Resumir-ValorCarga $filas 'conexionesInactivas')
        conexionesTotales=(Resumir-ValorCarga $filas 'conexionesTotales')
        hilosEsperandoConexion=(Resumir-ValorCarga $filas 'hilosEsperandoConexion')
        hilosHttpOcupados=(Resumir-ValorCarga $filas 'hilosHttpOcupados')
        hilosHttpMaximos=(Resumir-ValorCarga $filas 'hilosHttpMaximos')
        gcTiempoMsEntrePrimeraYUltimaMuestra=$gcMs
        gcCantidadEntrePrimeraYUltimaMuestra=$gcCantidad
    }
}

function Guardar-DiagnosticoCarga([string]$prefijo, [string]$carpeta, $informe) {
    $inicio = [DateTimeOffset]::Parse($informe.inicioMedicionUTC)
    $fin = [DateTimeOffset]::Parse($informe.finMedicionUTC)
    $mysql = @(Leer-MuestrasCarga ($prefijo + '-mysql.csv') $inicio $fin)
    Guardar-Json ([ordered]@{
        inicioMedicionUTC=$informe.inicioMedicionUTC; finMedicionUTC=$informe.finMedicionUTC
        usuarios=$informe.usuarios; usuariosMinimosEnMedicion=$informe.usuariosMinimosEnMedicion
        fallosGenerador=$informe.fallosGenerador; cumpleUmbralesDeEstaEtapa=$informe.cumpleUmbralesDeEstaEtapa
        operaciones=@($informe.operaciones | Select-Object nombre,solicitudes,p95MsHasta,internosOTransporte,otrosErrores)
        alcance='Diagnostico instrumentado; requiere interpretar muestras, logs GC y capturas de hilos.'
        backend=(Resumir-JvmCarga (Join-Path $carpeta 'diagnostico-backend/jvm.csv') $inicio $fin)
        generador=(Resumir-JvmCarga (Join-Path $carpeta 'diagnostico-generador/jvm.csv') $inicio $fin)
        mysql=[ordered]@{muestras=$mysql.Count;
            cpuPorcentajeDeUnNucleo=(Resumir-ValorCarga $mysql 'cpuPorcentajeDeUnNucleo');
            memoriaProcesoBytes=(Resumir-ValorCarga $mysql 'memoriaProcesoBytes')}
    }) ($prefijo + '-diagnostico.json')
}
