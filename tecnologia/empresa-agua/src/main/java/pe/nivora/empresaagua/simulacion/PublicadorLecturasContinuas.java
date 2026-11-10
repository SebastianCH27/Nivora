package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PublicadorLecturasContinuas {

    private static final Logger log =
        LoggerFactory.getLogger(PublicadorLecturasContinuas.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int TAMANO_LOTE = 1000;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;
    private final int intervaloSegundos;

    public PublicadorLecturasContinuas(
            JdbcTemplate jdbc,
            @Value("${empresa.lecturas-continuas.intervalo-segundos:300}")
            int intervaloSegundos,
            @Value("${empresa.lecturas-continuas.consulta-maxima-segundos:30}")
            int consultaMaximaSegundos) {

        if (intervaloSegundos < 1 || consultaMaximaSegundos < 1) {
            throw new IllegalArgumentException(
                "Los tiempos de emision de lecturas deben ser mayores que cero.");
        }
        this.jdbc = jdbc;
        this.intervaloSegundos = intervaloSegundos;
        JdbcTemplate lecturas = new JdbcTemplate(
            Objects.requireNonNull(jdbc.getDataSource()));
        lecturas.setQueryTimeout(consultaMaximaSegundos);
        this.consultas = new NamedParameterJdbcTemplate(lecturas);
    }

    public void publicar(LocalDate mes, Instant hasta, boolean cierreMes) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "La emision debe compartir la transaccion del consumo.");
        }
        List<LocalDateTime> controles = consultas.query("""
            SELECT ultima_emision_utc
            FROM control_emision_lecturas
            WHERE id_control = 1
            FOR UPDATE
            """, Map.of(), (fila, numero) ->
                fila.getObject(1, LocalDateTime.class));
        if (controles.size() != 1) {
            throw new IllegalStateException(
                "Falta ejecutar 08-control-emision-lecturas.sql.");
        }
        LocalDateTime ultima = controles.get(0);
        if (ultima != null) {
            Instant anterior = ultima.toInstant(ZoneOffset.UTC);
            if (hasta.isBefore(anterior)) {
                throw new IllegalStateException(
                    "La emision de lecturas no puede retroceder.");
            }
            if (!cierreMes && hasta.isBefore(
                    anterior.plusSeconds(intervaloSegundos))) {
                return;
            }
        }

        LocalDate dia = hasta.minusNanos(1).atZone(LIMA).toLocalDate();
        LocalDateTime inicioUtc = LocalDateTime.ofInstant(
            mes.atStartOfDay(LIMA).toInstant(), ZoneOffset.UTC);
        LocalDateTime finUtc = LocalDateTime.ofInstant(hasta, ZoneOffset.UTC);
        long ultimoSuministro = 0;
        long revisadas = 0;
        long insertadas = 0;
        long inicio = System.nanoTime();
        log.info("Emision de lecturas iniciada. Corte UTC: {}", hasta);

        while (true) {
            // Primero se limita el conjunto de suministros, antes de los JOIN.
            List<Long> ids = consultas.queryForList("""
                SELECT id_suministro FROM suministros
                WHERE id_suministro > :ultimo
                ORDER BY id_suministro LIMIT :limite
                """, Map.of("ultimo", ultimoSuministro,
                    "limite", TAMANO_LOTE), Long.class);
            if (ids.isEmpty()) {
                break;
            }
            List<BaseLectura> bases = consultas.query("""
                SELECT s.id_suministro, m.id_medidor,
                       b.lectura_acumulada_m3 AS base_m3,
                       c.consumo_litros, c.cerrado_en
                FROM suministros s
                LEFT JOIN medidores m ON m.id_suministro = s.id_suministro
                    AND m.fecha_instalacion <= :dia
                    AND (m.fecha_retiro IS NULL OR m.fecha_retiro > :dia)
                LEFT JOIN lecturas b ON b.id_medidor = m.id_medidor
                    AND b.id_suministro = s.id_suministro
                    AND b.fecha_medicion = :inicio
                LEFT JOIN consumos_mensuales c
                    ON c.id_suministro = s.id_suministro AND c.periodo = :mes
                WHERE s.id_suministro IN (:ids)
                ORDER BY s.id_suministro, m.id_medidor
                """, Map.of("ids", ids, "dia", dia,
                    "inicio", inicioUtc, "mes", mes),
                (fila, numero) -> new BaseLectura(
                    fila.getLong("id_suministro"),
                    fila.getObject("id_medidor", Long.class),
                    fila.getBigDecimal("base_m3"),
                    fila.getBigDecimal("consumo_litros"),
                    fila.getObject("cerrado_en", LocalDateTime.class)));

            Map<Long, BaseLectura> porSuministro = new HashMap<>();
            for (BaseLectura base : bases) {
                if (base.medidor() == null
                        || porSuministro.put(base.suministro(), base) != null) {
                    throw new IllegalStateException(
                        "Se requiere un medidor activo por suministro: "
                            + base.suministro());
                }
                if (base.inicioM3() == null || base.consumoLitros() == null
                        || base.cerradoEn() != null) {
                    throw new IllegalStateException(
                        "Falta la lectura de inicio de mes o el periodo abierto: "
                            + base.suministro());
                }
            }
            if (porSuministro.size() != ids.size()) {
                throw new IllegalStateException("El lote de suministros esta incompleto.");
            }

            // Una sola consulta por lote; no hay subconsultas correlacionadas.
            List<Long> medidores = bases.stream().map(BaseLectura::medidor).toList();
            Map<Long, UltimaLectura> ultimas = new HashMap<>();
            consultas.query("""
                SELECT l.id_medidor, l.id_suministro,
                       l.fecha_medicion, l.lectura_acumulada_m3
                FROM (
                    SELECT id_medidor, id_suministro,
                           MAX(fecha_medicion) AS ultima_fecha
                    FROM lecturas
                    WHERE id_medidor IN (:medidores)
                    GROUP BY id_medidor, id_suministro
                ) u
                JOIN lecturas l ON l.id_medidor = u.id_medidor
                    AND l.id_suministro = u.id_suministro
                    AND l.fecha_medicion = u.ultima_fecha
                """, Map.of("medidores", medidores),
                (org.springframework.jdbc.core.RowCallbackHandler) fila ->
                    ultimas.put(fila.getLong("id_medidor"), new UltimaLectura(
                        fila.getLong("id_suministro"),
                        fila.getObject("fecha_medicion", LocalDateTime.class),
                        fila.getBigDecimal("lectura_acumulada_m3"))));

            List<Object[]> nuevas = new ArrayList<>();
            for (BaseLectura base : bases) {
                UltimaLectura previa = ultimas.get(base.medidor());
                if (previa == null || previa.suministro() != base.suministro()) {
                    throw new IllegalStateException(
                        "Falta el historial del medidor " + base.medidor());
                }
                // Resolucion de un litro: los totales mensuales conservan sus decimales.
                BigDecimal acumulada = base.inicioM3()
                    .add(base.consumoLitros().movePointLeft(3))
                    .setScale(3, RoundingMode.DOWN);
                int comparacion = acumulada.compareTo(previa.acumuladaM3());
                if (previa.fecha().isAfter(finUtc) || comparacion < 0
                        || (previa.fecha().equals(finUtc) && comparacion != 0)) {
                    throw new IllegalStateException(
                        "La lectura continua contradice el historial: "
                            + base.suministro());
                }
                if (!previa.fecha().equals(finUtc)
                        && (comparacion > 0 || cierreMes)) {
                    nuevas.add(new Object[] {
                        base.suministro(), base.medidor(), finUtc, acumulada
                    });
                }
            }
            if (!nuevas.isEmpty()) {
                int[] resultados = jdbc.batchUpdate("""
                    INSERT INTO lecturas (
                        id_suministro, id_medidor,
                        fecha_medicion, lectura_acumulada_m3
                    ) VALUES (?, ?, ?, ?)
                    """, nuevas);
                for (int resultado : resultados) {
                    if (resultado == 0 || resultado == Statement.EXECUTE_FAILED) {
                        throw new IllegalStateException(
                            "No se pudo confirmar una lectura continua.");
                    }
                }
            }
            revisadas += ids.size();
            insertadas += nuevas.size();
            ultimoSuministro = ids.get(ids.size() - 1);
            if (revisadas % 10000 == 0) {
                log.info("Emision en transaccion: {} suministros revisados; {} lecturas nuevas.",
                    revisadas, insertadas);
            }
        }
        jdbc.update("""
            UPDATE control_emision_lecturas
            SET ultima_emision_utc = ? WHERE id_control = 1
            """, finUtc);
        log.info("Emision preparada: {} suministros; {} lecturas; {} ms. Pendiente del commit del ciclo.",
            revisadas, insertadas, (System.nanoTime() - inicio) / 1000000);
    }

    private record BaseLectura(
        long suministro, Long medidor, BigDecimal inicioM3,
        BigDecimal consumoLitros, LocalDateTime cerradoEn) {
    }

    private record UltimaLectura(
        long suministro, LocalDateTime fecha, BigDecimal acumuladaM3) {
    }
}
