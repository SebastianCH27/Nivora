import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Carga cerrada: cada usuario tiene una sesion y espera entre consultas. Requiere JDK 21. */
public class PruebaCargaConsultas {
    private static final String[] RUTAS = {"consumo-actual", "historial?meses=2", "estadisticas?meses=2",
        "costos?meses=2", "proyeccion", "recibos?limite=12", "notificaciones?limite=20", "comparacion"};
    private static final String[] NOMBRES = {"consumo", "historial", "estadisticas", "costos",
        "proyeccion", "recibos", "notificaciones", "comparacion"};

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException("Usa URL base, CSV, usuarios, rampa, calentamiento, duracion, pausa-ms, informe.json.");
        }
        URI base = URI.create(args[0]);
        if (!List.of("127.0.0.1", "localhost").contains(base.getHost()) || !"http".equals(base.getScheme())) {
            throw new IllegalArgumentException("La herramienta solo acepta el servidor local de carga.");
        }
        int usuarios = entero(args[2], 1, 10000);
        int rampa = entero(args[3], 0, 600);
        int calentamiento = entero(args[4], 0, 600);
        int segundos = entero(args[5], 5, 3600);
        int pausa = entero(args[6], 10, 60000);
        List<String> filas = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        if (filas.size() < usuarios + 1 || !filas.getFirst().equals("codigoSuministro,token")) {
            throw new IllegalArgumentException("El CSV no contiene las sesiones requeridas.");
        }
        List<String[]> sesiones = new ArrayList<>();
        var tokens = new HashSet<String>();
        var suministros = new HashSet<String>();
        for (int i = 1; i <= usuarios; i++) {
            String[] fila = filas.get(i).split(",", -1);
            if (fila.length != 2 || !fila[0].matches("9[0-9]{7}") || !fila[1].matches("nc_[A-Za-z0-9_-]{43}")
                    || !tokens.add(fila[1]) || !suministros.add(fila[0])) {
                throw new IllegalArgumentException("Las sesiones y suministros deben ser ficticios e independientes.");
            }
            sesiones.add(fila);
        }
        Medida[] medidas = new Medida[RUTAS.length];
        for (int i = 0; i < medidas.length; i++) medidas[i] = new Medida();
        AtomicInteger activos = new AtomicInteger();
        AtomicInteger fallosGenerador = new AtomicInteger();
        CountDownLatch preparados = new CountDownLatch(usuarios);
        CountDownLatch iniciar = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(usuarios);
        long[] tiempos = new long[3];
        Instant inicioUTC = Instant.now();
        Instant medicionUTC;
        int minimoActivos = usuarios;
        int muestras = 0;
        long memoriaMaxima = 0;
        var sistema = (com.sun.management.OperatingSystemMXBean)
            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        long cpuInicial = sistema.getProcessCpuTime();
        long relojInicial = System.nanoTime();
        try (HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5)).build();
                var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < usuarios; i++) {
                int indice = i;
                ejecutor.submit(() -> {
                    preparados.countDown();
                    boolean incorporado = false;
                    try {
                        iniciar.await();
                        esperarHasta(tiempos[0] + Duration.ofSeconds(rampa).toNanos() * indice / usuarios);
                        String[] sesion = sesiones.get(indice);
                        Pattern codigo = Pattern.compile("\"codigoSuministro\"\\s*:\\s*\"" + sesion[0] + "\"");
                        HttpRequest[] consultas = new HttpRequest[RUTAS.length];
                        for (int j = 0; j < RUTAS.length; j++) {
                            consultas[j] = HttpRequest.newBuilder(URI.create(base + "/api/v1/acceso/consulta/" + RUTAS[j]))
                                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + sesion[1])
                                .header("Accept", "application/json").build();
                        }
                        activos.incrementAndGet();
                        incorporado = true;
                        int ruta = indice % RUTAS.length;
                        while (System.nanoTime() < tiempos[2]) {
                            long antes = System.nanoTime();
                            boolean medir = antes >= tiempos[1];
                            int estado = 0;
                            boolean valida = false;
                            try {
                                var pendiente = cliente.sendAsync(consultas[ruta], HttpResponse.BodyHandlers.ofString());
                                try {
                                    // El plazo cubre tambien un cuerpo que se detiene despues de las cabeceras.
                                    var respuesta = pendiente.get(10, TimeUnit.SECONDS);
                                    estado = respuesta.statusCode();
                                    valida = estado == 200 && respuesta.headers().firstValue("content-type").orElse("")
                                        .startsWith("application/json") && codigo.matcher(respuesta.body()).find();
                                } finally {
                                    if (!pendiente.isDone()) pendiente.cancel(true);
                                }
                            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
                                // Incluye timeout, desconexion y fallo de conexion: todos se cuentan como transporte.
                            }
                            if (medir) medidas[ruta].registrar(System.nanoTime() - antes, estado, valida);
                            ruta = (ruta + 1) % RUTAS.length;
                            Thread.sleep(pausa);
                        }
                    } catch (Exception error) {
                        fallosGenerador.incrementAndGet();
                        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    } finally {
                        if (incorporado) activos.decrementAndGet();
                        terminados.countDown();
                    }
                });
            }
            if (!preparados.await(60, TimeUnit.SECONDS)) {
                iniciar.countDown();
                throw new IllegalStateException("El generador no preparo todos los usuarios.");
            }
            medicionUTC = Instant.now().plusSeconds(rampa + calentamiento);
            tiempos[0] = System.nanoTime();
            tiempos[1] = tiempos[0] + Duration.ofSeconds(rampa + calentamiento).toNanos();
            tiempos[2] = tiempos[1] + Duration.ofSeconds(segundos).toNanos();
            iniciar.countDown();
            long ultimoProgreso = 0;
            while (System.nanoTime() < tiempos[2]) {
                long ahora = System.nanoTime();
                if (ahora >= tiempos[1]) {
                    minimoActivos = Math.min(minimoActivos, activos.get());
                    muestras++;
                }
                memoriaMaxima = Math.max(memoriaMaxima, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                if (ahora - ultimoProgreso >= Duration.ofSeconds(5).toNanos()) {
                    System.out.println("Usuarios iniciados: " + activos.get() + "/" + usuarios
                        + "; fase: " + (ahora < tiempos[1] ? "preparacion" : "medicion") + ".");
                    ultimoProgreso = ahora;
                }
                Thread.sleep(250);
            }
            if (!terminados.await(75, TimeUnit.SECONDS)) throw new IllegalStateException("Hay usuarios que no finalizaron.");
        }
        double cpu = 100.0 * (sistema.getProcessCpuTime() - cpuInicial) / (System.nanoTime() - relojInicial);
        boolean objetivo = muestras > 0 && minimoActivos == usuarios && fallosGenerador.get() == 0;
        List<String> operaciones = new ArrayList<>();
        for (int i = 0; i < medidas.length; i++) {
            Medida m = medidas[i];
            boolean cumple = m.total >= 100 && m.percentil(.95) <= 2000
                && 100.0 * m.internos / Math.max(1, m.total) < 1 && m.otros == 0;
            objetivo &= cumple;
            operaciones.add(m.json(NOMBRES[i], segundos, cumple));
            System.out.printf(Locale.ROOT, "%s: solicitudes=%d p95<=%d ms internos/transporte=%d otros=%d%n",
                NOMBRES[i], m.total, m.percentil(.95), m.internos, m.otros);
        }
        String informe = "{\n\"inicioUTC\":\"" + inicioUTC + "\",\"finUTC\":\"" + Instant.now()
            + "\",\"inicioMedicionUTC\":\"" + medicionUTC
            + "\",\"finMedicionUTC\":\"" + medicionUTC.plusSeconds(segundos) + "\",\"usuarios\":" + usuarios + ",\"usuariosMinimosEnMedicion\":" + minimoActivos
            + ",\"rampaSegundos\":" + rampa + ",\"calentamientoSegundos\":" + calentamiento
            + ",\"medicionSegundos\":" + segundos + ",\"pausaMs\":" + pausa
            + ",\"fallosGenerador\":" + fallosGenerador.get() + ",\"cumpleUmbralesDeEstaEtapa\":" + objetivo
            + ",\"cubre5000Durante30Minutos\":" + (usuarios >= 5000 && segundos >= 1800)
            + ",\"generador\":{\"procesadores\":" + Runtime.getRuntime().availableProcessors()
            + ",\"heapMaximoBytes\":" + Runtime.getRuntime().maxMemory() + ",\"heapObservadoMaximoBytes\":" + memoriaMaxima
            + ",\"cpuMediaPorcentajeDeUnNucleo\":" + String.format(Locale.ROOT, "%.2f", cpu)
            + "},\"operaciones\":[\n" + String.join(",\n", operaciones) + "\n]}\n";
        Path salida = Path.of(args[7]);
        Files.createDirectories(salida.toAbsolutePath().getParent());
        Files.writeString(salida, informe);
        System.out.println("Informe: " + salida + ". Cumple umbrales de esta etapa: " + objetivo + ".");
        if (fallosGenerador.get() > 0 || muestras == 0 || minimoActivos != usuarios) System.exit(2);
    }

    private static int entero(String texto, int minimo, int maximo) {
        int valor = Integer.parseInt(texto);
        if (valor < minimo || valor > maximo) throw new IllegalArgumentException("Parametro fuera de rango.");
        return valor;
    }

    private static void esperarHasta(long instante) throws InterruptedException {
        long espera = instante - System.nanoTime();
        if (espera > 0) TimeUnit.NANOSECONDS.sleep(espera);
    }

    private static final class Medida {
        private long total, internos, otros, sumaNanos, maximoNanos;
        private final long[] histograma = new long[10002];

        synchronized void registrar(long nanos, int estado, boolean valida) {
            total++;
            sumaNanos += nanos;
            maximoNanos = Math.max(maximoNanos, nanos);
            histograma[(int) Math.min(10001, (nanos + 999999) / 1000000)]++;
            if (estado == 0 || estado >= 500) internos++;
            else if (!valida) otros++;
        }

        long percentil(double fraccion) {
            long requerido = (long) Math.ceil(total * fraccion), suma = 0;
            if (total == 0) return 10001;
            for (int i = 0; i < histograma.length; i++) {
                suma += histograma[i];
                if (suma >= requerido) return i == 10001 ? (maximoNanos + 999999) / 1000000 : i;
            }
            return 10001;
        }

        String json(String nombre, int segundos, boolean cumple) {
            return String.format(Locale.ROOT,
                "{\"nombre\":\"%s\",\"solicitudes\":%d,\"internosOTransporte\":%d,\"otrosErrores\":%d,"
                + "\"erroresPorcentaje\":%.4f,\"solicitudesPorSegundo\":%.3f,\"mediaMs\":%.3f,"
                + "\"p50MsHasta\":%d,\"p95MsHasta\":%d,\"p99MsHasta\":%d,\"maximoMs\":%.3f,\"cumple\":%s}",
                nombre, total, internos, otros, 100.0 * internos / Math.max(1, total), total / (double) segundos,
                total == 0 ? 0 : sumaNanos / 1e6 / total, percentil(.5), percentil(.95), percentil(.99), maximoNanos / 1e6, cumple);
        }
    }
}
