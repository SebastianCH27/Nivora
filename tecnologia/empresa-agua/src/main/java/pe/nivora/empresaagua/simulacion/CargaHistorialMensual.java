package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
    name = "empresa.carga-mensual.habilitada",
    havingValue = "true"
)
public class CargaHistorialMensual implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(CargaHistorialMensual.class);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public CargaHistorialMensual(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones) {

        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        Long cantidad = transaccion.execute(estado -> cargar());

        log.info(
            "Carga mensual terminada: {} registros nuevos",
            cantidad);
    }

    private long cargar() {
        LocalDate ultimaFecha = jdbc.queryForObject("""
            SELECT fecha_actual
            FROM estado_simulacion
            WHERE id_estado = 1
            FOR UPDATE
            """, LocalDate.class);

        Boolean existeHistorial = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM consumos_mensuales LIMIT 1
            )
            """, Boolean.class);

        if (Boolean.TRUE.equals(existeHistorial)) {
            log.info(
                "Ya existe historial mensual. Se conservan sus datos.");
            return 0;
        }

        Limites limites = jdbc.queryForObject("""
            SELECT MIN(fecha_medicion) AS primera,
                   MAX(fecha_medicion) AS ultima
            FROM lecturas
            """, (fila, numero) -> new Limites(
                fila.getObject("primera", LocalDateTime.class),
                fila.getObject("ultima", LocalDateTime.class)));

        if (ultimaFecha == null
                || limites == null
                || limites.primera() == null) {

            throw new IllegalStateException(
                "Faltan las lecturas o su fecha de control.");
        }

        LocalDate primerMes = limites.primera()
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(LIMA)
            .toLocalDate()
            .withDayOfMonth(1);

        if (!aUtc(primerMes).equals(limites.primera())
                || !aUtc(ultimaFecha).equals(limites.ultima())) {

            throw new IllegalStateException(
                "Las fechas de las lecturas no coinciden "
                    + "con el historial esperado.");
        }

        long total = 0;

        for (LocalDate mes = primerMes;
                !mes.isAfter(ultimaFecha.withDayOfMonth(1));
                mes = mes.plusMonths(1)) {

            LocalDate proximoMes = mes.plusMonths(1);
            boolean cerrado = !proximoMes.isAfter(ultimaFecha);
            LocalDate corte = cerrado ? proximoMes : ultimaFecha;

            LocalDateTime inicioUtc = aUtc(mes);
            LocalDateTime finUtc = aUtc(corte);

            long ultimoId = 0;
            long preparadas = 0;

            while (true) {
                List<Long> ids = jdbc.queryForList("""
                    SELECT id_suministro
                    FROM suministros
                    WHERE id_suministro > ?
                    ORDER BY id_suministro
                    LIMIT 1000
                    """, Long.class, ultimoId);

                if (ids.isEmpty()) {
                    break;
                }

                long limite = ids.get(ids.size() - 1);

                List<Consumo> consumos = jdbc.query("""
                    SELECT s.id_suministro,
                           (fin.lectura_acumulada_m3
                               - inicio.lectura_acumulada_m3)
                               * 1000 AS litros
                    FROM suministros s
                    JOIN lecturas inicio
                        ON inicio.id_suministro = s.id_suministro
                        AND inicio.fecha_medicion = ?
                    JOIN lecturas fin
                        ON fin.id_suministro = s.id_suministro
                        AND fin.id_medidor = inicio.id_medidor
                        AND fin.fecha_medicion = ?
                    WHERE s.id_suministro > ?
                        AND s.id_suministro <= ?
                    ORDER BY s.id_suministro
                    """, (fila, numero) -> new Consumo(
                        fila.getLong("id_suministro"),
                        fila.getBigDecimal("litros")),
                    inicioUtc, finUtc, ultimoId, limite);

                if (consumos.size() != ids.size()) {
                    throw new IllegalStateException(
                        "Faltan lecturas de inicio o cierre "
                            + "para el periodo " + mes);
                }

                List<Object[]> registros = new ArrayList<>();

                for (Consumo consumo : consumos) {
                    if (consumo.litros().signum() < 0) {
                        throw new IllegalStateException(
                            "Consumo negativo en el suministro "
                                + consumo.suministro());
                    }

                    registros.add(new Object[] {
                        consumo.suministro(),
                        mes,
                        consumo.litros(),
                        finUtc,
                        cerrado ? finUtc : null
                    });
                }

                jdbc.batchUpdate("""
                    INSERT INTO consumos_mensuales (
                        id_suministro,
                        periodo,
                        consumo_litros,
                        fecha_actualizacion,
                        cerrado_en
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """, registros);

                preparadas += registros.size();
                ultimoId = limite;
            }

            total += preparadas;

            log.info(
                "Periodo {} preparado: {} suministros",
                mes, preparadas);
        }

        return total;
    }

    private LocalDateTime aUtc(LocalDate fecha) {
        return fecha.atStartOfDay(LIMA)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    }

    private record Limites(
        LocalDateTime primera,
        LocalDateTime ultima
    ) {}

    private record Consumo(
        long suministro,
        BigDecimal litros
    ) {}
}