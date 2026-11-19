import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;

/** Datos ficticios y fuente HTTP para el entorno de carga aislado. */
public class PrepararDatosCarga {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("Usa modo, URL MySQL, base, usuarios, carpeta de salida y puerto fuente.");
        }
        String modo = args[0];
        String url = args[1];
        String base = args[2];
        int usuarios = Integer.parseInt(args[3]);
        Path salida = Path.of(args[4]);
        int puerto = Integer.parseInt(args[5]);
        if (!base.matches("nivora_carga_[a-z0-9_]+") || usuarios < 1 || usuarios > 100000) {
            throw new IllegalArgumentException("Base de carga o cantidad de usuarios invalida.");
        }
        switch (modo) {
            case "preparar" -> preparar(url, base, usuarios, salida);
            case "fuente" -> servir(usuarios, salida, puerto);
            case "estado" -> estado(url, base, salida);
            case "limpiar" -> limpiar(url, base, salida);
            default -> throw new IllegalArgumentException("Modo de carga invalido.");
        }
    }

    private static void preparar(String url, String base, int usuarios, Path salida) throws Exception {
        Files.createDirectories(salida);
        String cuenta = variable("NIVORA_CARGA_DB_USUARIO");
        if (!cuenta.matches("ncarga_[a-z0-9]+")) throw new IllegalArgumentException("Cuenta de carga invalida.");
        try (Connection db = DriverManager.getConnection(url,
                variable("NIVORA_CARGA_DB_ADMIN"), variable("NIVORA_CARGA_DB_ADMIN_CLAVE"))) {
            try (Statement sql = db.createStatement()) {
                sql.execute("CREATE DATABASE " + base + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                Files.writeString(salida.resolve("base-creada.txt"), base);
            }
            db.setCatalog(base);
            try (Statement sql = db.createStatement()) {
                try (var scripts = Files.list(Path.of("base-datos"))) {
                    for (Path script : scripts.filter(p -> p.getFileName().toString()
                            .matches("(?:0[2-9]|1[0-9]|2[0-2])-.*\\.sql")).sorted().toList()) {
                        String texto = Files.readString(script).replaceAll("(?m)^\\s*--[^\\n]*$", "");
                        for (String sentencia : texto.split(";")) {
                            String limpia = sentencia.strip();
                            if (!limpia.isEmpty() && !limpia.toUpperCase(java.util.Locale.ROOT).startsWith("USE ")) {
                                sql.execute(limpia);
                            }
                        }
                    }
                }
            }
            try (PreparedStatement crear = db.prepareStatement("CREATE USER ?@'127.0.0.1' IDENTIFIED BY ?")) {
                crear.setString(1, cuenta);
                crear.setString(2, variable("NIVORA_CARGA_DB_CLAVE"));
                crear.execute();
                Files.writeString(salida.resolve("cuenta-creada.txt"), cuenta);
            }
            try (Statement sql = db.createStatement()) {
                sql.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + base + ".* TO '" + cuenta + "'@'127.0.0.1'");
            }
            try (Statement sql = db.createStatement();
                    var fila = sql.executeQuery("SELECT VERSION(), @@max_connections, @@innodb_buffer_pool_size")) {
                fila.next();
                Files.writeString(salida.resolve("mysql.json"), "{\"version\":\"" + fila.getString(1)
                    + "\",\"maxConnections\":" + fila.getInt(2)
                    + ",\"innodbBufferPoolBytes\":" + fila.getLong(3) + "}");
            }
            sembrar(db, usuarios, salida);
        }
        System.out.println("Entorno ficticio preparado. Usuarios: " + usuarios + ". Base: " + base + ".");
    }

    private static void sembrar(Connection db, int usuarios, Path salida) throws Exception {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        YearMonth mes = YearMonth.from(ahora.atZone(LIMA));
        List<Instant> fechas = List.of(inicio(mes.minusMonths(2)), inicio(mes.minusMonths(1)), inicio(mes),
            ahora.minusSeconds(1));
        if (!fechas.getLast().isAfter(fechas.get(2))) {
            throw new IllegalStateException("Repite la preparacion despues del primer segundo del mes.");
        }
        Files.write(salida.resolve("fechas.txt"), fechas.stream().map(Instant::toString).toList());
        db.setAutoCommit(false);
        String tramos = "[{\"desdeM3\":0,\"hastaM3\":null,\"precioAguaPorM3\":1.50,\"precioAlcantarilladoPorM3\":0.50}]";
        try (PreparedStatement tarifa = db.prepareStatement("INSERT INTO tarifas_versiones VALUES "
                + "('empresa-agua','CARGA',1,'Tarifa ficticia',?,5.00,0,?)")) {
            tarifa.setObject(1, mes.minusMonths(2).atDay(1));
            tarifa.setString(2, tramos);
            tarifa.executeUpdate();
        }
        var azar = new SecureRandom();
        var filas = new java.util.ArrayList<String>(usuarios + 1);
        filas.add("codigoSuministro,token");
        try (PreparedStatement suministro = db.prepareStatement("INSERT INTO suministros VALUES (?, 'Titular ficticio de carga', ?)");
                PreparedStatement lectura = db.prepareStatement("INSERT INTO lecturas "
                    + "(fuente,id_lectura,codigo_suministro,id_medidor,fecha_medicion_utc,valor_litros,recibida_en_utc) "
                    + "VALUES ('empresa-agua',?,?,'1',?,?,?)");
                PreparedStatement historial = db.prepareStatement("INSERT INTO tarifas_historiales VALUES ('empresa-agua',?,1,?)");
                PreparedStatement asignacion = db.prepareStatement("INSERT INTO tarifas_asignaciones VALUES "
                    + "('empresa-agua',?,1,?,NULL,?,NULL,'CARGA',1)");
                PreparedStatement recibo = db.prepareStatement("INSERT INTO recibos VALUES "
                    + "(?,?,?,2000,5.00,4.00,9.00,?,?,'CARGA',1,?)");
                PreparedStatement aviso = db.prepareStatement("INSERT INTO avisos_recibo VALUES (?,?,?)");
                PreparedStatement sesion = db.prepareStatement("INSERT INTO sesiones_consulta VALUES "
                    + "(?,?,?,'00000001','Persona ficticia de carga',?,?,NULL)")) {
            for (int i = 1; i <= usuarios; i++) {
                String codigo = codigo(i);
                suministro.setString(1, codigo);
                suministro.setObject(2, utc(ahora));
                suministro.addBatch();
                for (int j = 0; j < 4; j++) {
                    lectura.setString(1, Long.toString((i - 1L) * 5 + j + 1));
                    lectura.setString(2, codigo);
                    lectura.setObject(3, utc(fechas.get(j)));
                    lectura.setLong(4, j == 3 ? 6300 : 100 + j * 2000L);
                    lectura.setObject(5, utc(ahora));
                    lectura.addBatch();
                }
                historial.setString(1, codigo);
                historial.setObject(2, utc(ahora));
                historial.addBatch();
                asignacion.setString(1, codigo);
                asignacion.setObject(2, mes.minusMonths(2).atDay(1));
                asignacion.setObject(3, mes.minusMonths(2).atDay(1));
                asignacion.addBatch();
                recibo.setString(1, codigo);
                recibo.setObject(2, mes.minusMonths(1).atDay(1));
                recibo.setString(3, "CARGA-" + mes.minusMonths(1) + "-" + codigo);
                recibo.setObject(4, mes.atDay(1));
                recibo.setObject(5, mes.atDay(8));
                recibo.setObject(6, utc(ahora));
                recibo.addBatch();
                aviso.setString(1, codigo);
                aviso.setObject(2, mes.minusMonths(1).atDay(1));
                aviso.setObject(3, utc(ahora));
                aviso.addBatch();
                byte[] secreto = new byte[32];
                azar.nextBytes(secreto);
                String token = "nc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
                String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
                sesion.setString(1, java.util.UUID.randomUUID().toString());
                sesion.setString(2, huella);
                sesion.setString(3, codigo);
                sesion.setObject(4, utc(ahora));
                sesion.setObject(5, utc(ahora.plusSeconds(14400)));
                sesion.addBatch();
                filas.add(codigo + "," + token);
            }
            suministro.executeBatch();
            lectura.executeBatch();
            historial.executeBatch();
            asignacion.executeBatch();
            recibo.executeBatch();
            aviso.executeBatch();
            sesion.executeBatch();
            db.commit();
        }
        Files.write(salida.resolve("usuarios.csv"), filas, StandardCharsets.UTF_8);
    }

    private static void servir(int usuarios, Path salida, int puerto) throws Exception {
        List<Instant> fechas = Files.readAllLines(salida.resolve("fechas.txt")).stream().map(Instant::parse).toList();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 128);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            String cuerpo;
            int estado = 200;
            if (ruta.equals("/api/v1/lecturas/corte")) {
                cuerpo = "{\"fuente\":\"empresa-agua\",\"hastaIdLectura\":\"" + usuarios * 5L
                    + "\",\"confirmadoHasta\":\"" + Instant.now().truncatedTo(ChronoUnit.MICROS) + "\"}";
            } else if (ruta.matches("/api/v1/suministros/[0-9]+/(medidores|lecturas|recibos)")) {
                String codigo = ruta.split("/")[4];
                int indice = Integer.parseInt(codigo) - 90000000;
                if (indice < 1 || indice > usuarios) {
                    estado = 404;
                    cuerpo = "{}";
                } else if (ruta.endsWith("/medidores")) {
                    cuerpo = "{\"fuente\":\"empresa-agua\",\"codigoSuministro\":\"" + codigo
                        + "\",\"zonaFechas\":\"America/Lima\",\"medidores\":[{\"idMedidor\":\"1\","
                        + "\"fechaInstalacion\":\"" + fechas.getFirst().atZone(LIMA).toLocalDate()
                        + "\",\"fechaRetiro\":null}]}";
                } else if (ruta.endsWith("/recibos")) {
                    cuerpo = "{\"codigoSuministro\":\"" + codigo + "\",\"moneda\":\"PEN\",\"recibos\":[]}";
                } else {
                    long cursor = 0;
                    for (String parametro : java.util.Objects.toString(intercambio.getRequestURI().getRawQuery(), "").split("&")) {
                        if (parametro.startsWith("despuesDe=")) cursor = Long.parseLong(parametro.substring(10));
                    }
                    var lecturas = new java.util.ArrayList<String>();
                    long ultimo = cursor;
                    for (int j = 0; j < 5; j++) {
                        long id = (indice - 1L) * 5 + j + 1;
                        if (id > cursor) {
                            lecturas.add("{\"idLectura\":\"" + id + "\",\"idMedidor\":\"1\",\"fechaMedicion\":\""
                                + (j == 4 ? fechas.getLast().plusSeconds(1) : fechas.get(j))
                                + "\",\"valorLitros\":" + (j >= 3 ? 6300 + j - 3 : 100 + j * 2000L) + "}");
                            ultimo = id;
                        }
                    }
                    cuerpo = "{\"fuente\":\"empresa-agua\",\"codigoSuministro\":\"" + codigo
                        + "\",\"tipoMedicion\":\"ACUMULADA\",\"unidad\":\"L\",\"lecturas\":["
                        + String.join(",", lecturas) + "],\"hayMas\":false,\"siguienteDespuesDe\":\"" + ultimo + "\"}";
                }
            } else {
                estado = 404;
                cuerpo = "{}";
            }
            try (intercambio) {
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(estado, bytes.length);
                intercambio.getResponseBody().write(bytes);
            }
        });
        servidor.start();
        System.out.println("Fuente ficticia de carga en " + puerto + ".");
    }

    private static void estado(String url, String base, Path salida) throws Exception {
        Path ventana = salida.resolve("ventana.txt");
        boolean medida = Files.exists(ventana);
        List<Instant> limites = medida ? Files.readAllLines(ventana).stream().map(Instant::parse).toList()
            : List.of(Instant.EPOCH, Instant.now().plusSeconds(86400));
        try (Connection db = DriverManager.getConnection(url, variable("NIVORA_CARGA_DB_USUARIO"),
                variable("NIVORA_CARGA_DB_CLAVE"))) {
            db.setCatalog(base);
            try (PreparedStatement sql = db.prepareStatement("""
                    SELECT COUNT(*) AS completados, MAX(ultimo_exito_utc) AS ultimo_exito,
                        COALESCE(SUM(ultimo_exito_utc >= ? AND ultimo_exito_utc < ?),0) AS exitos_durante,
                        (SELECT COUNT(*) FROM alertas_consumo WHERE detectada_en_utc >= ?
                            AND detectada_en_utc < ?) AS alertas_durante,
                        (SELECT COUNT(*) FROM lecturas WHERE recibida_en_utc >= ?
                            AND recibida_en_utc < ?) AS lecturas_durante,
                        (SELECT COUNT(*) FROM alertas_consumo) AS alertas,
                        (SELECT COUNT(*) FROM lecturas) AS lecturas,
                        (SELECT MAX(detectada_en_utc) FROM alertas_consumo) AS ultima_alerta
                    FROM programacion_sincronizacion_lecturas WHERE ultimo_exito_utc IS NOT NULL
                    """)) {
                for (int i = 1; i <= 6; i++) sql.setObject(i, utc(limites.get((i - 1) % 2)));
                try (var fila = sql.executeQuery()) {
                    fila.next();
                    String ultimo = fila.getString("ultimo_exito");
                    String alerta = fila.getString("ultima_alerta");
                    System.out.println("{\"suministrosActualizados\":" + fila.getLong("completados")
                        + ",\"ultimoExitoUTC\":" + (ultimo == null ? "null" : "\"" + ultimo + "\"")
                        + ",\"alertasRegistradas\":" + fila.getLong("alertas")
                        + ",\"lecturasGuardadas\":" + fila.getLong("lecturas")
                        + ",\"suministrosConExitoDuranteMedicion\":" + (medida ? fila.getLong("exitos_durante") : "null")
                        + ",\"alertasDuranteMedicion\":" + (medida ? fila.getLong("alertas_durante") : "null")
                        + ",\"lecturasDuranteMedicion\":" + (medida ? fila.getLong("lecturas_durante") : "null")
                        + ",\"ultimaAlertaUTC\":" + (alerta == null ? "null" : "\"" + alerta + "\"") + "}");
                }
            }
        }
    }

    private static void limpiar(String url, String base, Path salida) throws Exception {
        String cuenta = variable("NIVORA_CARGA_DB_USUARIO");
        if (!cuenta.matches("ncarga_[a-z0-9]+")) throw new IllegalArgumentException("Cuenta invalida.");
        try (Connection db = DriverManager.getConnection(url,
                variable("NIVORA_CARGA_DB_ADMIN"), variable("NIVORA_CARGA_DB_ADMIN_CLAVE"));
                Statement sql = db.createStatement()) {
            Path baseCreada = salida.resolve("base-creada.txt");
            Path cuentaCreada = salida.resolve("cuenta-creada.txt");
            if (Files.exists(baseCreada) && Files.readString(baseCreada).equals(base)) {
                sql.execute("DROP DATABASE IF EXISTS " + base);
            }
            if (Files.exists(cuentaCreada) && Files.readString(cuentaCreada).equals(cuenta)) {
                sql.execute("DROP USER IF EXISTS '" + cuenta + "'@'127.0.0.1'");
            }
        }
        System.out.println("Base y cuenta temporales retiradas.");
    }

    private static String codigo(int indice) {
        return Integer.toString(90000000 + indice);
    }

    private static Instant inicio(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(LIMA).toInstant();
    }

    private static LocalDateTime utc(Instant fecha) {
        return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC);
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta " + nombre + ".");
        return valor;
    }
}
