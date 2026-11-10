package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GeneradorConsumoContinuo {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final PublicadorLecturasContinuas publicador;

    public GeneradorConsumoContinuo(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones,
            PublicadorLecturasContinuas publicador) {

        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
        this.publicador = publicador;
    }

    public Instant actualizarHasta(Instant objetivo) {
        Instant hasta = Objects.requireNonNull(objetivo)
            .truncatedTo(ChronoUnit.SECONDS);

        while (true) {
            Instant punto = transaccion.execute(
                estado -> avanzar(hasta));

            if (!punto.isBefore(hasta)
                    || Thread.currentThread().isInterrupted()) {
                return punto;
            }
        }
    }

    private Instant avanzar(Instant objetivo) {
        Control control = jdbc.queryForObject("""
            SELECT actualizado_hasta, semilla
            FROM control_consumo
            WHERE id_control = 1
            FOR UPDATE
            """, (fila, numero) -> new Control(
                fila.getObject(
                    "actualizado_hasta", LocalDateTime.class),
                fila.getLong("semilla")));

        if (control == null) {
            throw new IllegalStateException(
                "Falta el control del consumo continuo.");
        }

        Instant anterior = control.fecha() == null
            ? reconocerInicio()
            : control.fecha().toInstant(ZoneOffset.UTC);

        if (!anterior.isBefore(objetivo)) {
            guardarControl(anterior);
            return anterior;
        }

        LocalDate dia = anterior.atZone(LIMA).toLocalDate();
        LocalDate mes = dia.withDayOfMonth(1);

        Instant inicioDia = dia.atStartOfDay(LIMA).toInstant();
        Instant finDia = dia.plusDays(1)
            .atStartOfDay(LIMA).toInstant();

        Instant fin = objetivo.isBefore(finDia)
            ? objetivo : finDia;

        LocalDateTime finUtc =
            LocalDateTime.ofInstant(fin, ZoneOffset.UTC);

        int desde = (int) Duration.between(
            inicioDia, anterior).getSeconds();

        int hasta = (int) Duration.between(
            inicioDia, fin).getSeconds();

        List<Long> suministros = jdbc.query("""
            SELECT s.id_suministro, c.periodo, c.cerrado_en
            FROM suministros s
            LEFT JOIN consumos_mensuales c
                ON c.id_suministro = s.id_suministro
                AND c.periodo = ?
            ORDER BY s.id_suministro
            """, (fila, numero) -> {

                if (fila.getDate("periodo") == null
                        || fila.getObject("cerrado_en") != null) {

                    throw new IllegalStateException(
                        "Falta un periodo abierto para el suministro "
                            + fila.getLong("id_suministro"));
                }

                return fila.getLong("id_suministro");
            }, mes);

        if (suministros.isEmpty()) {
            throw new IllegalStateException(
                "No hay suministros para simular.");
        }

        List<Object[]> cambios = new ArrayList<>();

        for (long suministro : suministros) {
            Perfil perfil = new Perfil(
                suministro, dia, control.semilla());

            long mililitros =
                perfil.hasta(hasta) - perfil.hasta(desde);

            if (mililitros > 0) {
                cambios.add(new Object[] {
                    BigDecimal.valueOf(mililitros, 3),
                    finUtc,
                    suministro,
                    mes
                });
            }

            if (cambios.size() == 1000) {
                guardarCambios(cambios);
                cambios.clear();
            }
        }

        guardarCambios(cambios);

        LocalDate siguienteDia = dia.plusDays(1);
        boolean cierreMes = fin.equals(finDia)
            && siguienteDia.getDayOfMonth() == 1;

        publicador.publicar(mes, fin, cierreMes);

        if (cierreMes) {

            jdbc.update("""
                UPDATE consumos_mensuales
                SET cerrado_en = ?, fecha_actualizacion = ?
                WHERE periodo = ?
                    AND cerrado_en IS NULL
                """, finUtc, finUtc, mes);

            jdbc.update("""
                INSERT INTO consumos_mensuales (
                    id_suministro,
                    periodo,
                    consumo_litros,
                    fecha_actualizacion,
                    cerrado_en
                )
                SELECT id_suministro, ?, 0, ?, NULL
                FROM suministros
                """, siguienteDia, finUtc);
        }

        guardarControl(fin);
        return fin;
    }

    private void guardarCambios(List<Object[]> cambios) {
        if (cambios.isEmpty()) {
            return;
        }

        int[] resultados = jdbc.batchUpdate("""
            UPDATE consumos_mensuales
            SET consumo_litros = consumo_litros + ?,
                fecha_actualizacion = ?
            WHERE id_suministro = ?
                AND periodo = ?
                AND cerrado_en IS NULL
            """, cambios);

        for (int resultado : resultados) {
            if (resultado == 0
                    || resultado == Statement.EXECUTE_FAILED) {

                throw new IllegalStateException(
                    "No se pudo actualizar un consumo.");
            }
        }
    }

    private Instant reconocerInicio() {
        Inicio inicio = jdbc.queryForObject("""
            SELECT MIN(periodo) AS primer_mes,
                   MAX(periodo) AS ultimo_mes,
                   MIN(fecha_actualizacion) AS primera,
                   MAX(fecha_actualizacion) AS ultima,
                   COUNT(*) AS cantidad
            FROM consumos_mensuales
            WHERE cerrado_en IS NULL
            """, (fila, numero) -> new Inicio(
                fila.getObject("primer_mes", LocalDate.class),
                fila.getObject("ultimo_mes", LocalDate.class),
                fila.getObject("primera", LocalDateTime.class),
                fila.getObject("ultima", LocalDateTime.class),
                fila.getLong("cantidad")));

        Long cantidad = jdbc.queryForObject(
            "SELECT COUNT(*) FROM suministros", Long.class);

        if (inicio == null
                || inicio.cantidad() == 0
                || cantidad == null
                || inicio.cantidad() != cantidad
                || !inicio.primerMes().equals(inicio.ultimoMes())
                || !inicio.primera().equals(inicio.ultima())) {

            throw new IllegalStateException(
                "Se necesita el historial mensual inicial completo.");
        }

        Instant punto =
            inicio.ultima().toInstant(ZoneOffset.UTC);

        if (!punto.atZone(LIMA)
                .toLocalDate()
                .withDayOfMonth(1)
                .equals(inicio.primerMes())) {

            throw new IllegalStateException(
                "El periodo abierto tiene una fecha incorrecta.");
        }

        return punto;
    }

    private void guardarControl(Instant punto) {
        jdbc.update("""
            UPDATE control_consumo
            SET actualizado_hasta = ?
            WHERE id_control = 1
            """, LocalDateTime.ofInstant(
                punto, ZoneOffset.UTC));
    }

    private record Control(
        LocalDateTime fecha,
        long semilla
    ) {}

    private record Inicio(
        LocalDate primerMes,
        LocalDate ultimoMes,
        LocalDateTime primera,
        LocalDateTime ultima,
        long cantidad
    ) {}

    // Episodios ficticios de consumo por vivienda y dia.
    static class Perfil {

        private static final int[] PESOS = {
            1, 1, 3, 12, 7, 9, 8, 7, 12, 14, 6, 2
        };

        private final int[] inicios = new int[12];
        private final int[] duraciones = new int[12];
        private final long[] cantidades = new long[12];

        Perfil(long suministro, LocalDate dia, long semilla) {
            SplittableRandom vivienda =
                new SplittableRandom(semilla + suministro);

            int habitantes = 1 + vivienda.nextInt(6);
            int litrosPorPersona = 90 + vivienda.nextInt(61);

            SplittableRandom variacion = new SplittableRandom(
                semilla + suministro * 1000000L
                    + dia.toEpochDay());

            long litros = (long) habitantes * litrosPorPersona
                * (80 + variacion.nextInt(41)) / 100;

            SplittableRandom episodios = new SplittableRandom(
                semilla + suministro * 1000003L
                    + dia.toEpochDay() * 97L);

            int[] pesos = new int[12];
            long pesoTotal = 0;

            for (int i = 0; i < 12; i++) {
                duraciones[i] = 120 + episodios.nextInt(481);

                inicios[i] = i * 7200
                    + episodios.nextInt(7201 - duraciones[i]);

                pesos[i] =
                    PESOS[i] * (80 + episodios.nextInt(41));

                pesoTotal += pesos[i];
            }

            long acumulado = 0;

            for (int i = 0; i < 12; i++) {
                long anterior =
                    litros * 1000 * acumulado / pesoTotal;

                acumulado += pesos[i];

                cantidades[i] =
                    litros * 1000 * acumulado / pesoTotal
                        - anterior;
            }
        }

        long hasta(int segundo) {
            long total = 0;

            for (int i = 0; i < 12; i++) {
                int transcurrido = Math.min(
                    duraciones[i], segundo - inicios[i]);

                if (transcurrido <= 0) {
                    break;
                }

                total += cantidades[i]
                    * transcurrido / duraciones[i];
            }

            return total;
        }
    }
}
