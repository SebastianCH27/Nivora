package pe.nivora.empresaagua.suministros;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SuministroRepositorio {

    private final JdbcTemplate jdbc;

    public SuministroRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Suministro> buscarPorCodigo(String codigo) {
        return jdbc.query("""
            SELECT s.codigo, t.nombre_completo
            FROM suministros s
            JOIN titulares t ON t.id_titular = s.id_titular
            WHERE s.codigo = ?
            """, (fila, numero) -> new Suministro(
                fila.getString("codigo"),
                fila.getString("nombre_completo")), codigo)
            .stream()
            .findFirst();
    }
}
