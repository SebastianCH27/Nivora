package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    name = "empresa.carga-tarifas.habilitada",
    havingValue = "true"
)
public class CargaTarifas implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(CargaTarifas.class);

    // Valores ficticios definidos para este proyecto.
    private static final String CODIGO = "DOMESTICA";
    private static final BigDecimal CARGO_FIJO =
        new BigDecimal("5.00");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public CargaTarifas(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones) {

        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(
            gestorTransacciones);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        Integer cantidad = transaccion.execute(
            estado -> cargar());

        log.info(
            "Tarifa domestica preparada. Nuevas asignaciones: {}",
            cantidad);
    }

    private int cargar() {
        jdbc.queryForObject("""
            SELECT id_control
            FROM control_consumo
            WHERE id_control = 1
            FOR UPDATE
            """, Integer.class);

        LocalDate inicio = jdbc.queryForObject(
            "SELECT MIN(periodo) FROM consumos_mensuales",
            LocalDate.class);

        if (inicio == null) {
            throw new IllegalStateException(
                "Primero se necesita el historial mensual.");
        }

        Long sinHistorial = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM suministros s
            WHERE NOT EXISTS (
                SELECT 1
                FROM consumos_mensuales c
                WHERE c.id_suministro = s.id_suministro
            )
            """, Long.class);

        if (sinHistorial == null || sinHistorial > 0) {
            throw new IllegalStateException(
                "Hay suministros sin historial mensual inicial.");
        }

        long idTarifa = prepararTarifa(inicio);
        prepararTramos(idTarifa);

        return jdbc.update("""
            INSERT INTO suministro_tarifas (
                id_suministro,
                id_tarifa,
                fecha_desde,
                fecha_hasta
            )
            SELECT s.id_suministro,
                   ?,
                   MIN(c.periodo),
                   NULL
            FROM suministros s
            JOIN consumos_mensuales c
                ON c.id_suministro = s.id_suministro
            LEFT JOIN suministro_tarifas asignacion
                ON asignacion.id_suministro = s.id_suministro
            WHERE asignacion.id_asignacion IS NULL
            GROUP BY s.id_suministro
            ORDER BY s.id_suministro
            """, idTarifa);
    }

    private long prepararTarifa(LocalDate inicio) {
        List<Tarifa> existentes = jdbc.query("""
            SELECT id_tarifa,
                   cargo_fijo,
                   vigente_desde,
                   vigente_hasta
            FROM tarifas
            WHERE codigo = ?
                AND version = 1
            """, (fila, numero) -> new Tarifa(
                fila.getLong("id_tarifa"),
                fila.getBigDecimal("cargo_fijo"),
                fila.getObject(
                    "vigente_desde", LocalDate.class),
                fila.getObject(
                    "vigente_hasta", LocalDate.class)
            ), CODIGO);

        if (!existentes.isEmpty()) {
            Tarifa tarifa = existentes.get(0);

            if (tarifa.cargo().compareTo(CARGO_FIJO) != 0
                    || tarifa.inicio().isAfter(inicio)
                    || tarifa.fin() != null) {

                throw new IllegalStateException(
                    "DOMESTICA version 1 ya existe con otra "
                        + "configuracion. Se conserva.");
            }

            return tarifa.id();
        }

        jdbc.update("""
            INSERT INTO tarifas (
                codigo,
                version,
                nombre,
                cargo_fijo,
                vigente_desde,
                vigente_hasta
            )
            VALUES (
                ?, 1, 'Tarifa domestica', ?, ?, NULL
            )
            """, CODIGO, CARGO_FIJO, inicio);

        return jdbc.queryForObject("""
            SELECT id_tarifa
            FROM tarifas
            WHERE codigo = ?
                AND version = 1
            """, Long.class, CODIGO);
    }

    private void prepararTramos(long idTarifa) {
        List<Tramo> esperados = List.of(
            new Tramo(
                new BigDecimal("0"),
                new BigDecimal("10"),
                new BigDecimal("1.50")),
            new Tramo(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2.20")),
            new Tramo(
                new BigDecimal("20"),
                null,
                new BigDecimal("3.00"))
        );

        List<Tramo> existentes = jdbc.query("""
            SELECT desde_m3,
                   hasta_m3,
                   precio_por_m3
            FROM tramos_tarifa
            WHERE id_tarifa = ?
            ORDER BY desde_m3
            """, (fila, numero) -> new Tramo(
                fila.getBigDecimal("desde_m3"),
                fila.getBigDecimal("hasta_m3"),
                fila.getBigDecimal("precio_por_m3")
            ), idTarifa);

        if (existentes.isEmpty()) {
            List<Object[]> registros = esperados.stream()
                .map(tramo -> new Object[] {
                    idTarifa,
                    tramo.desde(),
                    tramo.hasta(),
                    tramo.precio()
                })
                .toList();

            jdbc.batchUpdate("""
                INSERT INTO tramos_tarifa (
                    id_tarifa,
                    desde_m3,
                    hasta_m3,
                    precio_por_m3
                )
                VALUES (?, ?, ?, ?)
                """, registros);

            return;
        }

        if (existentes.size() != esperados.size()) {
            throw new IllegalStateException(
                "Los tramos existentes no coinciden. Se conservan.");
        }

        for (int i = 0; i < esperados.size(); i++) {
            Tramo actual = existentes.get(i);
            Tramo esperado = esperados.get(i);

            if (!iguales(actual.desde(), esperado.desde())
                    || !iguales(actual.hasta(), esperado.hasta())
                    || !iguales(actual.precio(), esperado.precio())) {

                throw new IllegalStateException(
                    "Los tramos existentes son distintos. Se conservan.");
            }
        }
    }

    private boolean iguales(BigDecimal uno, BigDecimal otro) {
        return uno == null
            ? otro == null
            : otro != null && uno.compareTo(otro) == 0;
    }

    private record Tarifa(
        long id,
        BigDecimal cargo,
        LocalDate inicio,
        LocalDate fin
    ) {}

    private record Tramo(
        BigDecimal desde,
        BigDecimal hasta,
        BigDecimal precio
    ) {}
}