package pe.nivora.empresaagua.consumo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsumoRepositorio {

    private final JdbcTemplate jdbc;

    public ConsumoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ConsumoActual> buscar(
            String codigo, LocalDate periodo) {

        return jdbc.query("""
            SELECT s.codigo,
                   t.nombre_completo,
                   c.periodo,
                   c.consumo_litros,
                   control.actualizado_hasta
            FROM suministros s
            JOIN titulares t
                ON t.id_titular = s.id_titular
            LEFT JOIN consumos_mensuales c
                ON c.id_suministro = s.id_suministro
                AND c.periodo = ?
                AND c.cerrado_en IS NULL
            LEFT JOIN control_consumo control
                ON control.id_control = 1
            WHERE s.codigo = ?
            """, (fila, numero) -> {

                LocalDateTime actualizado = fila.getObject(
                    "actualizado_hasta", LocalDateTime.class);

                return new ConsumoActual(
                    fila.getString("codigo"),
                    fila.getString("nombre_completo"),
                    fila.getObject("periodo", LocalDate.class),
                    fila.getBigDecimal("consumo_litros"),
                    actualizado == null
                        ? null
                        : actualizado.toInstant(ZoneOffset.UTC)
                );
            }, periodo, codigo)
            .stream()
            .findFirst();
    }

    public Optional<HistorialConsumo> buscarHistorial(
            String codigo,
            LocalDate desde,
            LocalDate hasta) {

        return jdbc.query("""
            SELECT s.codigo,
                   t.nombre_completo,
                   c.periodo,
                   c.consumo_litros,
                   c.fecha_actualizacion,
                   c.cerrado_en
            FROM suministros s
            JOIN titulares t
                ON t.id_titular = s.id_titular
            LEFT JOIN consumos_mensuales c
                ON c.id_suministro = s.id_suministro
                AND c.periodo BETWEEN ? AND ?
            WHERE s.codigo = ?
            ORDER BY c.periodo DESC
            """, filas -> {

                if (!filas.next()) {
                    return Optional.empty();
                }

                String codigoEncontrado =
                    filas.getString("codigo");

                String titular =
                    filas.getString("nombre_completo");

                List<HistorialConsumo.Periodo> periodos =
                    new ArrayList<>();

                do {
                    LocalDate periodo = filas.getObject(
                        "periodo", LocalDate.class);

                    if (periodo != null) {
                        LocalDateTime actualizacion =
                            filas.getObject(
                                "fecha_actualizacion",
                                LocalDateTime.class);

                        LocalDateTime cierre = filas.getObject(
                            "cerrado_en", LocalDateTime.class);

                        periodos.add(
                            new HistorialConsumo.Periodo(
                                periodo,
                                filas.getBigDecimal(
                                    "consumo_litros"),
                                cierre == null
                                    ? "EN_CURSO"
                                    : "CERRADO",
                                actualizacion.toInstant(
                                    ZoneOffset.UTC),
                                cierre == null
                                    ? null
                                    : cierre.toInstant(
                                        ZoneOffset.UTC)
                            )
                        );
                    }
                } while (filas.next());

                return Optional.of(new HistorialConsumo(
                    codigoEncontrado,
                    titular,
                    List.copyOf(periodos)
                ));
            }, desde, hasta, codigo);
    }
}