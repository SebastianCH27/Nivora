#!/usr/bin/env python3
"""Comparacion controlada Linux: mismo JAR/datos, CPU de servidor 1 -> 4, generador separado.

Utiliza un MySQL desechable en loopback y elimina su cuenta, base y sesiones al terminar.
Los informes publicos no contienen credenciales; el estado privado vive solo durante la ejecucion.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import zipfile


def puerto():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def escribir(ruta, datos):
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2) + '\n')


def parar(proceso):
    if proceso is not None and proceso.poll() is None:
        proceso.terminate()
        try:
            proceso.wait(timeout=15)
        except subprocess.TimeoutExpired:
            proceso.kill()
            proceso.wait(timeout=10)


def esperar_http(url, proceso):
    import urllib.request
    for _ in range(180):
        if proceso.poll() is not None:
            raise RuntimeError('El servicio termino antes de responder salud; revisar su log.')
        try:
            with urllib.request.urlopen(url, timeout=1) as r:
                if r.status == 200:
                    return
        except OSError:
            pass
        time.sleep(.25)
    raise RuntimeError('El servidor no respondio salud dentro del plazo de arranque.')


def muestra(pid):
    try:
        campos = Path(f'/proc/{pid}/stat').read_text().split(') ', 1)[1].split()
        estado = dict(line.split(':', 1) for line in Path(f'/proc/{pid}/status').read_text().splitlines() if ':' in line)
        return {'cpu_segundos': (int(campos[11]) + int(campos[12])) / os.sysconf('SC_CLK_TCK'),
                'rss_bytes': int(estado['VmRSS'].split()[0]) * 1024,
                'rss_maximo_bytes': int(estado['VmHWM'].split()[0]) * 1024,
                'hilos': int(estado['Threads']), 'cpu_admitidas': estado['Cpus_allowed_list'].strip()}
    except (OSError, KeyError):
        return None


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mysqld', default=shutil.which('mysqld'))
    p.add_argument('--mysql-basedir')
    p.add_argument('--mysql-libdir')
    p.add_argument('--java', default=shutil.which('java'))
    p.add_argument('--javac', default=shutil.which('javac'))
    p.add_argument('--rapida', action='store_true', help='Solo comprueba el montaje; no acredita EQ-01/EQ-02.')
    p.add_argument('--salida', type=Path)
    a = p.parse_args()
    if not all((a.mysqld, a.java, a.javac)) or not hasattr(os, 'sched_getaffinity'):
        p.error('Se requieren Linux, JDK 21, MySQL 8 y al menos siete CPU disponibles.')
    cpus = sorted(os.sched_getaffinity(0))
    if len(cpus) < 7:
        p.error('Se requieren siete CPU para separar servidor ampliado, generador y fuente.')
    modulo = Path(__file__).resolve().parents[1]
    jar = modulo / 'target/nivora-backend-0.0.1-SNAPSHOT.jar'
    if not jar.is_file():
        p.error('Construye primero el JAR con clean verify.')
    salida = (a.salida or modulo / 'target/escalabilidad' / secrets.token_hex(6)).resolve()
    salida.mkdir(parents=True, exist_ok=False)
    entorno = dict(os.environ)
    if a.mysql_libdir:
        entorno['LD_LIBRARY_PATH'] = a.mysql_libdir
    entorno.update(NIVORA_CARGA_DB_ADMIN='carga_admin', NIVORA_CARGA_DB_ADMIN_CLAVE=secrets.token_urlsafe(24),
                   NIVORA_CARGA_DB_USUARIO='ncarga_' + secrets.token_hex(6),
                   NIVORA_CARGA_DB_CLAVE=secrets.token_urlsafe(24), NIVORA_CARGA_INTERVALOS='true')
    entorno['NIVORA_DB_CLAVE'] = entorno['NIVORA_CARGA_DB_CLAVE']
    java = str(Path(a.java).resolve())
    mysql = str(Path(a.mysqld).resolve())
    base_mysql = a.mysql_basedir or str(Path(mysql).parent.parent)
    cantidad = 100 if a.rapida else 5000
    etapas = [(100, 15, 2, 2)] if a.rapida else [(500, 60, 5, 10), (1000, 60, 5, 10), (2500, 60, 5, 10), (5000, 120, 60, 15)]
    db_port, api_port, fuente_port = puerto(), puerto(), puerto()
    base = 'nivora_carga_' + secrets.token_hex(6)
    url = f'jdbc:mysql://127.0.0.1:{db_port}/?connectionTimeZone=UTC&rewriteBatchedStatements=true'
    resumen = {'rapida': a.rapida, 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
               'generador_cpu': cpus[4:6], 'fuente_cpu': [cpus[6]], 'pool': 20,
               'backend_heap_mb': 512, 'generador_heap_mb': 768, 'mysql_buffer_mb': 128,
               'datos': {'suministros': cantidad, 'acumuladas': cantidad // 2, 'intervalos': cantidad // 2,
                         'lecturas_iniciales_por_suministro': 4, 'consultas': 8, 'tarifas_automaticas': True},
               'perfiles': []}
    escribir(salida / 'configuracion.json', resumen)
    logs = []
    def iniciar(cmd, log, cpu, env=entorno):
        f = log.open('w'); logs.append(f)
        return subprocess.Popen(['taskset', '-c', ','.join(map(str, cpu)), *map(str, cmd)],
                                cwd=modulo, env=env, stdout=f, stderr=subprocess.STDOUT)
    with tempfile.TemporaryDirectory(prefix='nivora-escala-') as temporal:
        privado = Path(temporal)
        privado.chmod(0o700)
        copia_jar = privado / 'backend-verificado.jar'
        shutil.copyfile(jar, copia_jar)
        assert hashlib.sha256(copia_jar.read_bytes()).hexdigest() == resumen['jar_sha256']
        jar = copia_jar
        clases = privado / 'clases'; clases.mkdir()
        with zipfile.ZipFile(jar) as z:
            nombre = next(n for n in z.namelist() if n.startswith('BOOT-INF/lib/mysql-connector-j-'))
            driver = privado / 'mysql.jar'; driver.write_bytes(z.read(nombre))
        subprocess.run([a.javac, '--release', '21', '-d', str(clases),
                        str(modulo / 'herramientas/PrepararDatosCarga.java'),
                        str(modulo / 'herramientas/PruebaCargaConsultas.java')], check=True, cwd=modulo)
        cp = str(clases) + os.pathsep + str(driver)
        def datos(modo):
            return json.loads(subprocess.check_output([java, '-Xmx256m', '-cp', cp, 'PrepararDatosCarga',
                modo, url, base, str(cantidad), str(privado), str(fuente_port)], cwd=modulo, env=entorno, text=True)) if modo == 'estado' else subprocess.run([
                java, '-Xmx256m', '-cp', cp, 'PrepararDatosCarga', modo, url, base, str(cantidad),
                str(privado), str(fuente_port)], cwd=modulo, env=entorno, check=True, stdout=subprocess.DEVNULL)
        data = privado / 'mysql'; data.mkdir()
        archivos_mysql = privado / 'mysql-files'; archivos_mysql.mkdir()
        inicial = privado / 'init.sql'
        inicial.write_text("CREATE USER 'carga_admin'@'127.0.0.1' IDENTIFIED BY '" + entorno['NIVORA_CARGA_DB_ADMIN_CLAVE']
            + "';\nGRANT ALL PRIVILEGES ON *.* TO 'carga_admin'@'127.0.0.1' WITH GRANT OPTION;\n")
        inicial.chmod(0o600)
        args_mysql = [mysql, '--no-defaults', '--basedir=' + base_mysql, '--datadir=' + str(data)]
        subprocess.run(args_mysql + ['--initialize-insecure'], env=entorno, check=True, capture_output=True, timeout=40)
        preparado = False
        db = fuente = backend = generador = None
        try:
            for nombre, server_cpu in [('inicial', cpus[:1]), ('ampliado', cpus[:4])]:
                print('Iniciando perfil', nombre, 'CPU de backend/MySQL', server_cpu, flush=True)
                perfil = salida / nombre; perfil.mkdir()
                db = iniciar(args_mysql + ['--bind-address=127.0.0.1', '--skip-name-resolve', '--mysqlx=0',
                    '--port=' + str(db_port), '--socket=', '--pid-file=' + str(privado / 'mysql.pid'),
                    '--secure-file-priv=' + str(archivos_mysql),
                    '--innodb-buffer-pool-size=128M', '--default-time-zone=+00:00',
                    '--log-error=' + str(privado / 'mysql-interno.log')] + ([] if preparado else ['--init-file=' + str(inicial)]),
                    perfil / 'mysql.log', server_cpu)
                for _ in range(180):
                    if db.poll() is not None:
                        raise RuntimeError('MySQL no arranco: ' + (privado / 'mysql-interno.log').read_text()[-1800:])
                    try:
                        with socket.create_connection(('127.0.0.1', db_port), .2): break
                    except OSError: time.sleep(.2)
                else: raise RuntimeError('MySQL no abrio su puerto.')
                if not preparado:
                    datos('preparar'); preparado = True
                    resumen['fechas_iniciales'] = (privado / 'fechas.txt').read_text().splitlines()
                    resumen['sesiones_sha256'] = hashlib.sha256((privado / 'usuarios.csv').read_bytes()).hexdigest()
                else:
                    datos('restablecer')
                escribir(perfil / 'estado-inicial.json', datos('estado'))
                fuente = iniciar([java, '-Xmx128m', '-XX:ActiveProcessorCount=1', '-cp', cp, 'PrepararDatosCarga',
                    'fuente', url, base, str(cantidad), str(privado), str(fuente_port)], perfil / 'fuente.log', [cpus[6]])
                backend = iniciar([java, '-Xmx512m', '-XX:ActiveProcessorCount=4', '-jar', str(jar),
                    f'--server.port={api_port}', '--spring.datasource.url=' + url.replace('/?', '/' + base + '?'),
                    '--spring.datasource.username=' + entorno['NIVORA_CARGA_DB_USUARIO'],
                    '--spring.datasource.hikari.maximum-pool-size=20', '--server.tomcat.threads.max=200',
                    f'--nivora.empresa-agua.url-base=http://127.0.0.1:{fuente_port}',
                    '--nivora.sincronizacion.intervalo-ms=1000', '--nivora.sincronizacion.maximo-intentos-por-ciclo=1',
                    '--nivora.sincronizacion.intervalo-completo-segundos=30', '--nivora.web-push.habilitado=false',
                    '--nivora.tarifas.sincronizacion.habilitada=true', '--nivora.tarifas.sincronizacion.intervalo-ms=1000',
                    '--nivora.tarifas.sincronizacion.maximo-intentos-por-ciclo=1',
                    '--nivora.tarifas.sincronizacion.intervalo-completo-segundos=30'], perfil / 'backend.log', server_cpu)
                esperar_http(f'http://127.0.0.1:{api_port}/actuator/health', backend)
                resultados = []
                for usuarios, segundos, rampa, calentamiento in etapas:
                    if nombre == 'ampliado' and usuarios == 5000: segundos = 1800
                    informe = perfil / f'usuarios-{usuarios}.json'
                    print(nombre, usuarios, 'usuarios;', segundos, 'segundos de medicion', flush=True)
                    (privado / 'ventana.txt').unlink(missing_ok=True)
                    antes = datos('estado')
                    generador = iniciar([java, '-Xmx768m', '-XX:ActiveProcessorCount=2', '-cp', str(clases),
                        'PruebaCargaConsultas', f'http://127.0.0.1:{api_port}', str(privado / 'usuarios.csv'),
                        str(usuarios), str(rampa), str(calentamiento), str(segundos), '1000', str(informe)],
                        perfil / f'usuarios-{usuarios}.log', cpus[4:6])
                    muestras = []
                    while generador.poll() is None:
                        muestras.append({'utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                            'monotono': time.monotonic(), 'backend': muestra(backend.pid),
                            'mysql': muestra(db.pid), 'generador': muestra(generador.pid), 'fuente': muestra(fuente.pid)})
                        time.sleep(5)
                    escribir(perfil / f'recursos-{usuarios}.json', muestras)
                    if generador.returncode != 0:
                        raise RuntimeError('Fallo el generador; revisar su log.')
                    resultado = json.loads(informe.read_text())
                    (privado / 'ventana.txt').write_text(resultado['inicioMedicionUTC'] + '\n' + resultado['finMedicionUTC'] + '\n')
                    despues = datos('estado')
                    continuidad = despues['suministrosConExitoDuranteMedicion'] > 0 and despues['lecturasDuranteMedicion'] > 0
                    continuidad &= despues['alertasDuranteMedicion'] > 0 and despues['tarifasConExitoDuranteMedicion'] > 0
                    evaluacion = {'usuarios': usuarios, 'antes': antes, 'despues': despues,
                                  'continuidad': continuidad, 'cumple': resultado['cumpleUmbralesDeEstaEtapa'] and continuidad}
                    escribir(perfil / f'evaluacion-{usuarios}.json', evaluacion)
                    resultados.append(evaluacion)
                    print('Resultado', nombre, usuarios, 'cumple:', evaluacion['cumple'],
                          'p95 maximo:', max(x['p95MsHasta'] for x in resultado['operaciones']), flush=True)
                resumen['perfiles'].append({'nombre': nombre, 'cpu_servidor': server_cpu, 'resultados': resultados})
                escribir(salida / 'resumen.json', resumen)
                parar(backend); backend = None
                parar(fuente); fuente = None
                if nombre == 'ampliado': datos('limpiar'); preparado = False
                parar(db); db = None
            resumen['limpieza_completada'] = True
            escribir(salida / 'resumen.json', resumen)
            print('Evaluacion terminada; informes:', salida, flush=True)
        finally:
            parar(generador); parar(backend); parar(fuente)
            if preparado and db is not None and db.poll() is None:
                try: datos('limpiar')
                except Exception: pass
            parar(db)
            for log in logs: log.close()


if __name__ == '__main__':
    main()
