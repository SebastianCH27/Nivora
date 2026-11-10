package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GeneradorLecturasDiarias {

    private static final Logger log =
        LoggerFactory.getLogger(GeneradorLecturasDiarias.class);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final String ACTIVOS = """
        m.fecha_instalacion <= ?
        AND (m.fecha_retiro IS NULL OR m.fecha_retiro > ?)
        """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public GeneradorLecturasDiarias(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones) {

        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    public int generarHasta(LocalDate fechaObjetivo) {
        Objects.requireNonNull(
            fechaObjetivo, "Debes indicar la fecha objetivo.");

        int diasGenerados = 0;

        while (true) {
            DiaGenerado resultado = transaccion.execute(
                estado -> generarSiguienteDia(fechaObjetivo));

            if (resultado == null) {
                return diasGenerados;
            }

            diasGenerados++;

            log.info("Dia {} completado: {} lecturas",
                resultado.fecha(), resultado.cantidad());
        }
    }

    private DiaGenerado generarSiguienteDia(LocalDate objetivo) {
        List<LocalDateTime> emisiones = jdbc.query("""
            SELECT ultima_emision_utc
            FROM control_emision_lecturas
            WHERE id_control = 1
            FOR UPDATE
            """, (fila, numero) -> fila.getObject(1, LocalDateTime.class));

        if (emisiones.size() != 1) {
            throw new IllegalStateException(
                "Falta ejecutar 08-control-emision-lecturas.sql.");
        }
        if (emisiones.get(0) != null) {
            throw new IllegalStateException(
                "La generacion diaria prepara el historial inicial; "
                    + "la emision continua ya comenzo.");
        }

        Estado estado = jdbc.queryForObject("""
            SELECT fecha_actual, semilla
            FROM estado_simulacion
            WHERE id_estado = 1
            FOR UPDATE
            """, (fila, numero) -> new Estado(
                fila.getObject("fecha_actual", LocalDate.class),
                fila.getLong("semilla")));

        if (estado == null) {
            throw new IllegalStateException(
                "Falta el estado de la simulacion.");
        }

        LocalDate actual = estado.fecha();

        if (actual == null) {
            actual = reconocerFechaInicial();

            jdbc.update("""
                UPDATE estado_simulacion
                SET fecha_actual = ?
                WHERE id_estado = 1
                """, actual);
        }

        if (!actual.isBefore(objetivo)) {
            return null;
        }

        comprobarMedidores(actual);

        Long esperadas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM medidores m WHERE " + ACTIVOS,
            Long.class, actual, actual);

        if (esperadas == null || esperadas == 0) {
            throw new IllegalStateException(
                "No hay medidores activos para generar lecturas.");
        }

        LocalDate siguiente = actual.plusDays(1);
        LocalDateTime anteriorUtc = aUtc(actual);
        LocalDateTime siguienteUtc = aUtc(siguiente);

        long ultimoMedidor = 0;
        long preparadas = 0;

        String consulta = """
            SELECT m.id_medidor, m.id_suministro,
                   l.lectura_acumulada_m3
            FROM medidores m
            LEFT JOIN lecturas l
                ON l.id_medidor = m.id_medidor
                AND l.id_suministro = m.id_suministro
                AND l.fecha_medicion = ?
            WHERE
            """ + ACTIVOS + """
            AND m.id_medidor > ?
            ORDER BY m.id_medidor
            LIMIT 1000
            """;

        while (true) {
            List<LecturaBase> bases = jdbc.query(
                consulta,
                (fila, numero) -> new LecturaBase(
                    fila.getLong("id_medidor"),
                    fila.getLong("id_suministro"),
                    fila.getBigDecimal("lectura_acumulada_m3")),
                anteriorUtc, actual, actual, ultimoMedidor);

            if (bases.isEmpty()) {
                break;
            }

            List<Object[]> nuevas = new ArrayList<>();

            for (LecturaBase base : bases) {
                if (base.acumulada() == null) {
                    throw new IllegalStateException(
                        "Falta la lectura anterior del medidor "
                            + base.medidor());
                }

                BigDecimal incremento = consumoDelDia(
                    base.suministro(), actual, estado.semilla());

                nuevas.add(new Object[] {
                    base.suministro(),
                    base.medidor(),
                    siguienteUtc,
                    base.acumulada().add(incremento)
                });
            }

            jdbc.batchUpdate("""
                INSERT INTO lecturas (
                    id_suministro,
                    id_medidor,
                    fecha_medicion,
                    lectura_acumulada_m3
                )
                VALUES (?, ?, ?, ?)
                """, nuevas);

            preparadas += nuevas.size();
            ultimoMedidor = bases.get(bases.size() - 1).medidor();

            if (preparadas % 10000 == 0) {
                log.info("Preparadas {} de {} lecturas para {}",
                    preparadas, esperadas, siguiente);
            }
        }

        if (preparadas != esperadas) {
            throw new IllegalStateException(
                "La generacion del dia quedo incompleta.");
        }

        jdbc.update("""
            UPDATE estado_simulacion
            SET fecha_actual = ?
            WHERE id_estado = 1
            """, siguiente);

        return new DiaGenerado(siguiente, preparadas);
    }

    private LocalDate reconocerFechaInicial() {
        return jdbc.queryForObject("""
            SELECT MIN(fecha_medicion) AS primera,
                   MAX(fecha_medicion) AS ultima
            FROM lecturas
            """, (fila, numero) -> {

                LocalDateTime primera =
                    fila.getObject("primera", LocalDateTime.class);

                LocalDateTime ultima =
                    fila.getObject("ultima", LocalDateTime.class);

                if (primera == null || !primera.equals(ultima)) {
                    throw new IllegalStateException(
                        "Se necesita una lectura inicial comun "
                            + "para iniciar el control.");
                }

                LocalDate fecha = primera.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(LIMA)
                    .toLocalDate();

                if (!aUtc(fecha).equals(primera)) {
                    throw new IllegalStateException(
                        "Las lecturas iniciales deben corresponder "
                            + "a medianoche en Lima.");
                }

                return fecha;
            });
    }

    private void comprobarMedidores(LocalDate fecha) {
        List<Long> repetidos = jdbc.queryForList(
            "SELECT m.id_suministro FROM medidores m WHERE "
                + ACTIVOS
                + " GROUP BY m.id_suministro"
                + " HAVING COUNT(*) > 1 LIMIT 1",
            Long.class, fecha, fecha);

        if (!repetidos.isEmpty()) {
            throw new IllegalStateException(
                "Hay varios medidores activos para el suministro "
                    + repetidos.get(0));
        }
    }

    private BigDecimal consumoDelDia(
            long suministro, LocalDate fecha, long semilla) {

        // Perfil ficticio propio de cada vivienda.
        SplittableRandom perfil =
            new SplittableRandom(semilla + suministro);

        int habitantes = 1 + perfil.nextInt(6);
        int litrosPorPersona = 90 + perfil.nextInt(61);

        // Variacion reproducible para cada vivienda y fecha.
        SplittableRandom dia = new SplittableRandom(
            semilla + suministro * 1000000L + fecha.toEpochDay());

        int porcentaje = 80 + dia.nextInt(41);

        long litros =
            (long) habitantes * litrosPorPersona * porcentaje / 100;

        return BigDecimal.valueOf(litros, 3);
    }

    private LocalDateTime aUtc(LocalDate fecha) {
        return fecha.atStartOfDay(LIMA)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    }

    private record Estado(LocalDate fecha, long semilla) {}

    private record LecturaBase(
        long medidor, long suministro, BigDecimal acumulada) {}

    private record DiaGenerado(LocalDate fecha, long cantidad) {}
}
