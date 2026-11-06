package pe.nivora.empresaagua.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TarifaRepositorio {

    private final JdbcTemplate jdbc;

    public TarifaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TarifaActual> buscar(String codigo, LocalDate hoy) {
        return jdbc.query("""
            SELECT s.codigo, titular.nombre_completo,
                   a.id_asignacion, a.fecha_desde, a.fecha_hasta,
                   t.id_tarifa, t.codigo AS codigo_tarifa,
                   t.version, t.nombre, t.cargo_fijo,
                   t.vigente_desde, t.vigente_hasta,
                   tramo.id_tramo, tramo.desde_m3,
                   tramo.hasta_m3, tramo.precio_por_m3
            FROM suministros s
            JOIN titulares titular
                ON titular.id_titular = s.id_titular
            LEFT JOIN suministro_tarifas a
                ON a.id_suministro = s.id_suministro
                AND a.fecha_desde <= ?
                AND (a.fecha_hasta IS NULL OR a.fecha_hasta > ?)
            LEFT JOIN tarifas t
                ON t.id_tarifa = a.id_tarifa
                AND t.vigente_desde <= ?
                AND (t.vigente_hasta IS NULL OR t.vigente_hasta > ?)
            LEFT JOIN tramos_tarifa tramo
                ON tramo.id_tarifa = t.id_tarifa
            WHERE s.codigo = ?
            ORDER BY a.id_asignacion, tramo.desde_m3
            """, filas -> {
                if (!filas.next()) {
                    return Optional.empty();
                }

                Long asignacion = filas.getObject(
                    "id_asignacion", Long.class);

                if (asignacion == null
                        || filas.getObject(
                            "id_tarifa", Long.class) == null) {

                    throw new IllegalStateException(
                        "El suministro no tiene una tarifa vigente disponible.");
                }

                String suministro = filas.getString("codigo");
                String titular = filas.getString("nombre_completo");
                String tarifa = filas.getString("codigo_tarifa");
                int version = filas.getInt("version");
                String nombre = filas.getString("nombre");
                BigDecimal fijo = filas.getBigDecimal("cargo_fijo");

                LocalDate vigenteDesde = filas.getObject(
                    "vigente_desde", LocalDate.class);

                LocalDate vigenteHasta = filas.getObject(
                    "vigente_hasta", LocalDate.class);

                LocalDate asignadaDesde = filas.getObject(
                    "fecha_desde", LocalDate.class);

                LocalDate asignadaHasta = filas.getObject(
                    "fecha_hasta", LocalDate.class);

                List<TarifaActual.Tramo> tramos = new ArrayList<>();

                do {
                    if (!asignacion.equals(
                            filas.getObject(
                                "id_asignacion", Long.class))) {

                        throw new IllegalStateException(
                            "El suministro tiene mas de una tarifa asignada para la fecha.");
                    }

                    if (filas.getObject(
                            "id_tramo", Long.class) == null) {

                        throw new IllegalStateException(
                            "La tarifa no tiene tramos configurados.");
                    }

                    tramos.add(new TarifaActual.Tramo(
                        filas.getBigDecimal("desde_m3"),
                        filas.getBigDecimal("hasta_m3"),
                        filas.getBigDecimal("precio_por_m3")
                    ));
                } while (filas.next());

                validarTramos(tramos);

                return Optional.of(new TarifaActual(
                    suministro, titular, hoy, "PEN",
                    tarifa, version, nombre, fijo,
                    vigenteDesde, vigenteHasta,
                    asignadaDesde, asignadaHasta,
                    List.copyOf(tramos)
                ));
            }, hoy, hoy, hoy, hoy, codigo);
    }

    private void validarTramos(List<TarifaActual.Tramo> tramos) {
        BigDecimal siguiente = BigDecimal.ZERO;

        for (TarifaActual.Tramo tramo : tramos) {
            if (siguiente == null
                    || tramo.desdeM3().compareTo(siguiente) != 0) {

                throw new IllegalStateException(
                    "Los tramos de la tarifa deben ser continuos desde cero.");
            }

            siguiente = tramo.hastaM3();
        }

        if (siguiente != null) {
            throw new IllegalStateException(
                "El ultimo tramo de la tarifa debe quedar sin limite superior.");
        }
    }
}