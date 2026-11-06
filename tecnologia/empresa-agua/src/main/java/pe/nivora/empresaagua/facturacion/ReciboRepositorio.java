package pe.nivora.empresaagua.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReciboRepositorio {

    private final JdbcTemplate jdbc;

    public ReciboRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RecibosSuministro> buscar(
            String codigo,
            LocalDate hoy,
            int limite) {

        return jdbc.query("""
            SELECT s.codigo,
                   t.nombre_completo,
                   r.id_recibo,
                   r.numero_recibo,
                   r.periodo,
                   r.importe_total,
                   r.fecha_disponibilidad,
                   r.fecha_vencimiento,
                   d.id_recibo AS id_detalle,
                   d.consumo_litros,
                   d.cargo_fijo,
                   d.importe_consumo,
                   tarifa.codigo AS codigo_tarifa,
                   tarifa.version
            FROM suministros s
            JOIN titulares t
                ON t.id_titular = s.id_titular
            LEFT JOIN recibos r
                ON r.id_suministro = s.id_suministro
                AND r.fecha_disponibilidad <= ?
            LEFT JOIN detalle_recibos d
                ON d.id_recibo = r.id_recibo
            LEFT JOIN tarifas tarifa
                ON tarifa.id_tarifa = d.id_tarifa
            WHERE s.codigo = ?
            ORDER BY r.periodo DESC
            LIMIT ?
            """, filas -> {

                if (!filas.next()) {
                    return Optional.empty();
                }

                String codigoEncontrado =
                    filas.getString("codigo");

                String titular =
                    filas.getString("nombre_completo");

                List<RecibosSuministro.Recibo> recibos =
                    new ArrayList<>();

                do {
                    if (filas.getObject(
                            "id_recibo", Long.class) != null) {

                        if (filas.getObject(
                                "id_detalle", Long.class) == null
                                || filas.getString(
                                    "codigo_tarifa") == null) {

                            throw new IllegalStateException(
                                "El recibo no tiene un detalle completo.");
                        }

                        BigDecimal fijo =
                            filas.getBigDecimal("cargo_fijo");

                        BigDecimal consumo =
                            filas.getBigDecimal("importe_consumo");

                        BigDecimal total =
                            filas.getBigDecimal("importe_total");

                        if (total.compareTo(
                                fijo.add(consumo)) != 0) {

                            throw new IllegalStateException(
                                "El importe del recibo no coincide "
                                    + "con sus cargos.");
                        }

                        recibos.add(
                            new RecibosSuministro.Recibo(
                                filas.getString("numero_recibo"),
                                filas.getObject(
                                    "periodo", LocalDate.class),
                                filas.getBigDecimal(
                                    "consumo_litros"),
                                fijo,
                                consumo,
                                total,
                                filas.getObject(
                                    "fecha_disponibilidad",
                                    LocalDate.class),
                                filas.getObject(
                                    "fecha_vencimiento",
                                    LocalDate.class),
                                filas.getString("codigo_tarifa"),
                                filas.getInt("version")
                            )
                        );
                    }
                } while (filas.next());

                return Optional.of(new RecibosSuministro(
                    codigoEncontrado,
                    titular,
                    "PEN",
                    List.copyOf(recibos)
                ));
            }, hoy, codigo, limite);
    }
}