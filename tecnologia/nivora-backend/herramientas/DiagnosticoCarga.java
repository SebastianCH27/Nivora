import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/** Agente local de solo observacion para las JVM creadas por la prueba de carga. */
public class DiagnosticoCarga {
    private static final MBeanServer MBEANS = ManagementFactory.getPlatformMBeanServer();
    private static final com.sun.management.OperatingSystemMXBean SISTEMA =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static Path carpeta;
    private static int capturas;
    private static long proximaCaptura;

    public static void premain(String opciones, Instrumentation instrumentacion) throws Exception {
        carpeta = Path.of(opciones);
        Files.createDirectories(carpeta);
        Files.writeString(carpeta.resolve("jvm.csv"),
            "utc,pid,procesadores,cpuSistemaPorcentaje,cpuProcesoNanos,memoriaFisicaLibreBytes,"
            + "heapUsadoBytes,heapMaximoBytes,gcCantidad,gcTiempoMs,hilosJvm,conexionesActivas,"
            + "conexionesInactivas,conexionesTotales,hilosEsperandoConexion,hilosHttpOcupados,"
            + "hilosHttpTotales,hilosHttpMaximos,muestraCompleta\n");
        var programador = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "nivora-diagnostico-carga");
            hilo.setDaemon(true);
            return hilo;
        });
        programador.scheduleWithFixedDelay(DiagnosticoCarga::muestrear, 0, 5, TimeUnit.SECONDS);
        Files.writeString(carpeta.resolve("iniciado.txt"), "Agente local iniciado. Muestreo cada 5 segundos.\n");
    }

    private static void muestrear() {
        try {
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long cantidadGc = 0;
            long tiempoGc = 0;
            for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                cantidadGc += Math.max(0, gc.getCollectionCount());
                tiempoGc += Math.max(0, gc.getCollectionTime());
            }
            var pool = new ObjectName("com.zaxxer.hikari:type=Pool (NivoraCarga)");
            long ocupadosHttp = -1, totalesHttp = -1, maximosHttp = -1;
            for (var nombre : MBEANS.queryNames(new ObjectName("*:type=ThreadPool,*"), null)) {
                long ocupados = atributo(nombre, "currentThreadsBusy");
                if (ocupados >= 0) {
                    ocupadosHttp = Math.max(0, ocupadosHttp) + ocupados;
                    totalesHttp = Math.max(0, totalesHttp) + Math.max(0, atributo(nombre, "currentThreadCount"));
                    maximosHttp = Math.max(0, maximosHttp) + Math.max(0, atributo(nombre, "maxThreads"));
                }
            }
            double cargaSistema = SISTEMA.getCpuLoad();
            String fila = String.format(Locale.ROOT,
                "%s,%d,%d,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,OK%n",
                Instant.now(), ProcessHandle.current().pid(), Runtime.getRuntime().availableProcessors(),
                cargaSistema < 0 ? -1 : cargaSistema * 100, SISTEMA.getProcessCpuTime(),
                SISTEMA.getFreeMemorySize(), heap.getUsed(), heap.getMax(), cantidadGc, tiempoGc,
                ManagementFactory.getThreadMXBean().getThreadCount(), atributo(pool, "ActiveConnections"),
                atributo(pool, "IdleConnections"), atributo(pool, "TotalConnections"),
                atributo(pool, "ThreadsAwaitingConnection"), ocupadosHttp, totalesHttp, maximosHttp);
            Files.writeString(carpeta.resolve("jvm.csv"), fila, StandardOpenOption.APPEND);
            long ahora = System.nanoTime();
            if (capturas < 3 && ahora >= proximaCaptura && Files.exists(carpeta.resolve("capturar-hilos.flag"))) {
                capturarHilos();
                proximaCaptura = ahora + TimeUnit.SECONDS.toNanos(20);
            }
        } catch (Exception error) {
            // Registrar el tipo del fallo; nunca incluir cuerpos, propiedades del proceso ni secretos.
            try {
                Files.writeString(carpeta.resolve("errores.log"), Instant.now() + " "
                    + error.getClass().getSimpleName() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignorado) { }
        }
    }

    private static long atributo(ObjectName nombre, String atributo) {
        try {
            Object valor = MBEANS.getAttribute(nombre, atributo);
            return valor instanceof Number numero ? numero.longValue() : -1;
        } catch (Exception noDisponible) {
            return -1;
        }
    }

    private static void capturarHilos() throws IOException {
        var hilos = ManagementFactory.getThreadMXBean();
        var texto = new ArrayList<String>();
        texto.add("Captura UTC " + Instant.now() + ". Hilos de plataforma; maximo 12 marcos por hilo.");
        for (var hilo : hilos.getThreadInfo(hilos.getAllThreadIds(), 12)) {
            if (hilo == null) continue;
            texto.add("\n" + hilo.getThreadName() + " estado=" + hilo.getThreadState()
                + " bloqueo=" + hilo.getLockName());
            for (var marco : hilo.getStackTrace()) texto.add("    " + marco);
        }
        Files.write(carpeta.resolve("hilos-" + (++capturas) + ".txt"), texto);
    }
}
