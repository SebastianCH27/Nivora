package pe.nivora.empresaagua.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GeneradorRecibos {

    private static final Logger log =
        LoggerFactory.getLogger(GeneradorRecibos.class);

    private static final ZoneId LIMA =
        ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    private final CalculadoraRecibo calculadora =
        new CalculadoraRecibo();

    public GeneradorRecibos(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones) {

        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(
            gestorTransacciones);
    }

    public int generarPendientes() {
        int total = 0;

        while (true) {
            LocalDate mesActual = LocalDate.now(LIMA)
                .withDayOfMonth(1);

            LocalDate periodo = jdbc.queryForObject("""
                SELECT MIN(c.periodo)
                FROM consumos_mensuales c
                LEFT JOIN recibos r
                    ON r.id_suministro = c.id_suministro
                    AND r.periodo = c.periodo
                WHERE c.cerrado_en IS NOT NULL
                    AND c.periodo < ?
                    AND r.id_recibo IS NULL
                """, LocalDate.class, mesActual);

            if (periodo == null) {
                return total;
            }

            long ultimoId = 0;

            while (true) {
                long desde = ultimoId;

                Lote lote = transaccion.execute(
                    estado -> generarLote(periodo, desde));

                if (lote == null || lote.cantidad() == 0) {
                    break;
                }

                ultimoId = lote.ultimoId();
                total += lote.cantidad();

                if (total % 10000 == 0) {
                    log.info(
                        "Recibos generados en esta ejecucion: {}",
                        total);
                }

                if (Thread.currentThread().isInterrupted()) {
                    return total;
                }
            }
        }
    }

    private Lote generarLote(
            LocalDate periodo, long ultimoId) {

        jdbc.queryForObject("""
            SELECT id_control
            FROM control_consumo
            WHERE id_control = 1
            FOR UPDATE
            """, Integer.class);

        LocalDate disponible = periodo.plusMonths(1);
        LocalDate vencimiento = disponible.plusDays(7);

        List<Pendiente> pendientes = jdbc.query("""
            SELECT c.id_suministro,
                   s.codigo,
                   c.consumo_litros,
                   t.id_tarifa,
                   t.cargo_fijo,
                   (
                       SELECT COUNT(*)
                       FROM suministro_tarifas a2
                       WHERE a2.id_suministro = c.id_suministro
                           AND a2.fecha_desde < ?
                           AND (
                               a2.fecha_hasta IS NULL
                               OR a2.fecha_hasta > ?
                           )
                   ) AS asignaciones
            FROM consumos_mensuales c
            JOIN suministros s
                ON s.id_suministro = c.id_suministro
            LEFT JOIN recibos r
                ON r.id_suministro = c.id_suministro
                AND r.periodo = c.periodo
            LEFT JOIN suministro_tarifas a
                ON a.id_suministro = c.id_suministro
                AND a.fecha_desde <= ?
                AND (
                    a.fecha_hasta IS NULL
                    OR a.fecha_hasta >= ?
                )
            LEFT JOIN tarifas t
                ON t.id_tarifa = a.id_tarifa
                AND t.vigente_desde <= ?
                AND (
                    t.vigente_hasta IS NULL
                    OR t.vigente_hasta >= ?
                )
            WHERE c.periodo = ?
                AND c.id_suministro > ?
                AND c.cerrado_en IS NOT NULL
                AND r.id_recibo IS NULL
            ORDER BY c.id_suministro
            LIMIT 1000
            """, (fila, numero) -> {

                Long idTarifa = fila.getObject(
                    "id_tarifa", Long.class);

                if (idTarifa == null
                        || fila.getInt("asignaciones") != 1) {

                    throw new IllegalStateException(
                        "Se necesita una sola tarifa valida "
                            + "para todo el periodo: suministro "
                            + fila.getLong("id_suministro")
                            + ", mes " + periodo);
                }

                return new Pendiente(
                    fila.getLong("id_suministro"),
                    fila.getString("codigo"),
                    fila.getBigDecimal("consumo_litros"),
                    idTarifa,
                    fila.getBigDecimal("cargo_fijo"));
            },
            disponible,
            periodo,
            periodo,
            disponible,
            periodo,
            disponible,
            periodo,
            ultimoId);

        if (pendientes.isEmpty()) {
            return new Lote(0, ultimoId);
        }

        Map<Long, List<CalculadoraRecibo.Tramo>> tarifas =
            new HashMap<>();

        List<Emision> emisiones = new ArrayList<>();
        List<Object[]> cabeceras = new ArrayList<>();

        String mes = periodo.format(
            DateTimeFormatter.ofPattern("uuuuMM"));

        for (Pendiente pendiente : pendientes) {
            List<CalculadoraRecibo.Tramo> tramos =
                tarifas.computeIfAbsent(
                    pendiente.tarifa(),
                    this::cargarTramos);

            CalculadoraRecibo.Resultado resultado =
                calculadora.calcular(
                    pendiente.litros(),
                    pendiente.cargo(),
                    tramos);

            String numero =
                "EA-" + mes + "-" + pendiente.codigo();

            emisiones.add(
                new Emision(numero, pendiente, resultado));

            cabeceras.add(new Object[] {
                numero,
                pendiente.suministro(),
                periodo,
                resultado.importeTotal(),
                disponible,
                vencimiento
            });
        }

        jdbc.batchUpdate("""
            INSERT INTO recibos (
                numero_recibo,
                id_suministro,
                periodo,
                importe_total,
                fecha_disponibilidad,
                fecha_vencimiento
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """, cabeceras);

        String marcas = String.join(
            ",",
            Collections.nCopies(emisiones.size(), "?"));

        Map<String, Long> ids = new HashMap<>();

        jdbc.query(
            "SELECT numero_recibo, id_recibo "
                + "FROM recibos WHERE numero_recibo IN ("
                + marcas + ")",
            fila -> {
                ids.put(
                    fila.getString("numero_recibo"),
                    fila.getLong("id_recibo"));
            },
            emisiones.stream()
                .map(Emision::numero)
                .toArray());

        List<Object[]> detalles = new ArrayList<>();

        for (Emision emision : emisiones) {
            Long idRecibo = ids.get(emision.numero());

            if (idRecibo == null) {
                throw new IllegalStateException(
                    "No se pudo recuperar un recibo generado.");
            }

            detalles.add(new Object[] {
                idRecibo,
                emision.pendiente().tarifa(),
                emision.pendiente().litros(),
                emision.resultado().cargoFijo(),
                emision.resultado().importeConsumo()
            });
        }

        jdbc.batchUpdate("""
            INSERT INTO detalle_recibos (
                id_recibo,
                id_tarifa,
                consumo_litros,
                cargo_fijo,
                importe_consumo
            )
            VALUES (?, ?, ?, ?, ?)
            """, detalles);

        return new Lote(
            emisiones.size(),
            pendientes.get(
                pendientes.size() - 1).suministro());
    }

    private List<CalculadoraRecibo.Tramo> cargarTramos(
            long idTarifa) {

        return jdbc.query("""
            SELECT desde_m3,
                   hasta_m3,
                   precio_por_m3
            FROM tramos_tarifa
            WHERE id_tarifa = ?
            ORDER BY desde_m3
            """, (fila, numero) ->
                new CalculadoraRecibo.Tramo(
                    fila.getBigDecimal("desde_m3"),
                    fila.getBigDecimal("hasta_m3"),
                    fila.getBigDecimal("precio_por_m3")
                ), idTarifa);
    }

    private record Pendiente(
        long suministro,
        String codigo,
        BigDecimal litros,
        long tarifa,
        BigDecimal cargo
    ) {}

    private record Emision(
        String numero,
        Pendiente pendiente,
        CalculadoraRecibo.Resultado resultado
    ) {}

    private record Lote(
        int cantidad,
        long ultimoId
    ) {}
}