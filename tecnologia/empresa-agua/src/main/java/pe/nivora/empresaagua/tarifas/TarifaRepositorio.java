package pe.nivora.empresaagua.tarifas;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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

                    tramos.add(leerTramo(filas));
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

    public Optional<HistorialTarifas> buscarHistorial(String codigo) {
        return jdbc.query("""
            SELECT s.codigo, a.id_asignacion, a.fecha_desde, a.fecha_hasta,
                   t.id_tarifa, t.codigo AS codigo_tarifa, t.version, t.nombre, t.cargo_fijo,
                   t.vigente_desde, t.vigente_hasta,
                   tramo.id_tramo, tramo.desde_m3, tramo.hasta_m3, tramo.precio_por_m3
            FROM suministros s
            LEFT JOIN suministro_tarifas a ON a.id_suministro = s.id_suministro
            LEFT JOIN tarifas t ON t.id_tarifa = a.id_tarifa
            LEFT JOIN tramos_tarifa tramo ON tramo.id_tarifa = t.id_tarifa
            WHERE s.codigo = ?
            ORDER BY GREATEST(a.fecha_desde, t.vigente_desde), a.id_asignacion, tramo.desde_m3
            """, this::leerHistorial, codigo);
    }

    private Optional<HistorialTarifas> leerHistorial(ResultSet filas) throws SQLException {
        if (!filas.next()) {
            return Optional.empty();
        }
        String suministro = filas.getString("codigo");
        var asignaciones = new ArrayList<HistorialTarifas.Asignacion>();
        Long id = filas.getObject("id_asignacion", Long.class);
        while (id != null) {
            LocalDate vigenteDesde = filas.getObject("vigente_desde", LocalDate.class);
            if (vigenteDesde == null || filas.getObject("id_tarifa", Long.class) == null) {
                throw new IllegalStateException("Hay una asignacion sin version tarifaria.");
            }
            LocalDate vigenteHasta = filas.getObject("vigente_hasta", LocalDate.class);
            LocalDate asignadaDesde = filas.getObject("fecha_desde", LocalDate.class);
            LocalDate asignadaHasta = filas.getObject("fecha_hasta", LocalDate.class);
            LocalDate inicio = vigenteDesde.isAfter(asignadaDesde) ? vigenteDesde : asignadaDesde;
            LocalDate fin = vigenteHasta;
            if (asignadaHasta != null && (fin == null || asignadaHasta.isBefore(fin))) {
                fin = asignadaHasta;
            }
            if (fin != null && !fin.isAfter(inicio)) {
                throw new IllegalStateException("Hay una asignacion fuera de la vigencia de su tarifa.");
            }
            if (!asignaciones.isEmpty()) {
                LocalDate finAnterior = asignaciones.getLast().aplicableHasta();
                if (finAnterior == null || inicio.isBefore(finAnterior)) {
                    throw new IllegalStateException("El suministro tiene tarifas aplicables superpuestas.");
                }
            }

            String tarifa = filas.getString("codigo_tarifa");
            int version = filas.getInt("version");
            String nombre = filas.getString("nombre");
            BigDecimal fijo = filas.getBigDecimal("cargo_fijo");
            var tramos = new ArrayList<TarifaActual.Tramo>();
            Long siguiente;
            do {
                tramos.add(leerTramo(filas));
                siguiente = filas.next() ? filas.getObject("id_asignacion", Long.class) : null;
            } while (id.equals(siguiente));
            validarTramos(tramos);

            asignaciones.add(new HistorialTarifas.Asignacion(tarifa, version, nombre, fijo,
                vigenteDesde, vigenteHasta, asignadaDesde, asignadaHasta, inicio, fin, List.copyOf(tramos)));
            id = siguiente;
        }
        return Optional.of(new HistorialTarifas("empresa-agua", suministro, "PEN", "America/Lima",
            List.copyOf(asignaciones)));
    }

    private static TarifaActual.Tramo leerTramo(ResultSet filas) throws SQLException {
        if (filas.getObject("id_tramo", Long.class) == null) {
            throw new IllegalStateException("La tarifa no tiene tramos configurados.");
        }
        return new TarifaActual.Tramo(filas.getBigDecimal("desde_m3"),
            filas.getBigDecimal("hasta_m3"), filas.getBigDecimal("precio_por_m3"));
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