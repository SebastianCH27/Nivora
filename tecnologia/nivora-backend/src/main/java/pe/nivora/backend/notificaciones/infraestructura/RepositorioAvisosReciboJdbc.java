package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.notificaciones.dominio.RepositorioAvisosRecibo;

@Repository
public class RepositorioAvisosReciboJdbc implements RepositorioAvisosRecibo {
    private static final int LIMITE_POR_CICLO = 100;
    private final JdbcTemplate jdbc;

    public RepositorioAvisosReciboJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int registrarDisponibles(LocalDate fechaLocal, Instant registradoEn) {
        var pendientes = jdbc.query("""
            SELECT r.codigo_suministro, r.periodo
            FROM recibos r
            WHERE r.fecha_disponibilidad <= ?
              AND NOT EXISTS (
                SELECT 1 FROM avisos_recibo a
                WHERE a.codigo_suministro = r.codigo_suministro AND a.periodo = r.periodo
              )
            ORDER BY r.fecha_disponibilidad, r.codigo_suministro, r.periodo
            LIMIT ?
            """, (fila, indice) -> new ReciboPendiente(fila.getString("codigo_suministro"),
                fila.getObject("periodo", LocalDate.class)), fechaLocal, LIMITE_POR_CICLO);
        var utc = LocalDateTime.ofInstant(registradoEn.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        int nuevos = 0;
        for (var recibo : pendientes) {
            try {
                nuevos += jdbc.update("""
                    INSERT INTO avisos_recibo (codigo_suministro, periodo, registrado_en_utc)
                    VALUES (?, ?, ?)
                    """, recibo.suministro(), recibo.periodo(), utc);
            } catch (DuplicateKeyException error) {
                // Otro ciclo registro el mismo recibo. Se conserva la primera fecha.
            }
        }
        return nuevos;
    }

    private record ReciboPendiente(String suministro, LocalDate periodo) {}
}
