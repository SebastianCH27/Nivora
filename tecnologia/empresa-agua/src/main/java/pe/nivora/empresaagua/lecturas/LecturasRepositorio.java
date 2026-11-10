package pe.nivora.empresaagua.lecturas;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LecturasRepositorio {

    private final JdbcTemplate jdbc;

    public LecturasRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> buscarIdSuministro(String codigo) {
        return jdbc.queryForList("""
            SELECT id_suministro FROM suministros WHERE codigo = ?
            """, Long.class, codigo).stream().findFirst();
    }

    public List<PaginaLecturas.Lectura> buscar(long suministro, long despuesDe, int cantidad) {
        return buscar(suministro, despuesDe, cantidad, null);
    }

    public List<PaginaLecturas.Lectura> buscar(long suministro, long despuesDe,
            int cantidad, Long hastaId) {
        String sql = """
            SELECT id_lectura, id_medidor, fecha_medicion, lectura_acumulada_m3
            FROM lecturas
            WHERE id_suministro = ? AND id_lectura > ?
            """ + (hastaId == null ? "" : " AND id_lectura <= ? ") + """
            ORDER BY id_lectura ASC
            LIMIT ?
            """;
        Object[] parametros = hastaId == null
            ? new Object[] { suministro, despuesDe, cantidad }
            : new Object[] { suministro, despuesDe, hastaId, cantidad };
        return jdbc.query(sql, (fila, numero) -> {
                long id = fila.getLong("id_lectura");
                long medidor = fila.getLong("id_medidor");
                LocalDateTime fecha = fila.getObject("fecha_medicion", LocalDateTime.class);
                BigDecimal acumulada = fila.getBigDecimal("lectura_acumulada_m3");
                if (id <= 0 || medidor <= 0 || fecha == null || acumulada == null
                        || acumulada.signum() < 0) {
                    throw new IllegalStateException("Una lectura almacenada contiene datos invalidos.");
                }

                long litros;
                try {
                    litros = acumulada.movePointRight(3).longValueExact();
                } catch (ArithmeticException ex) {
                    throw new IllegalStateException(
                        "Una lectura no se puede representar en litros enteros.");
                }

                return new PaginaLecturas.Lectura(Long.toString(id), Long.toString(medidor),
                    fecha.toInstant(ZoneOffset.UTC), litros);
            }, parametros);
    }
}
