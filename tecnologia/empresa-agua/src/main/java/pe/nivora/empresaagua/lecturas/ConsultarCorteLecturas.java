package pe.nivora.empresaagua.lecturas;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConsultarCorteLecturas {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public ConsultarCorteLecturas(JdbcTemplate jdbc,
            PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaccion.setTimeout(10);
    }

    public CorteLecturas consultar() {
        return transaccion.execute(estado -> {
            // Los productores guardan las lecturas y esta marca en la misma
            // transaccion. El bloqueo impide leer una marca y un maximo de
            // publicaciones diferentes. No se ejecuta el generador desde la API.
            List<LocalDateTime> fechas = jdbc.query("""
                SELECT ultima_emision_utc FROM control_emision_lecturas
                WHERE id_control = 1 FOR UPDATE
                """, (fila, numero) -> fila.getObject(1, LocalDateTime.class));
            if (fechas.size() != 1 || fechas.get(0) == null) {
                throw new IllegalStateException(
                    "Todavia no hay una emision de lecturas confirmada.");
            }
            Long maximo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id_lectura), 0) FROM lecturas", Long.class);
            if (maximo == null || maximo < 0) {
                throw new IllegalStateException("El limite de lecturas no es valido.");
            }
            return new CorteLecturas("empresa-agua", Long.toString(maximo),
                fechas.get(0).toInstant(ZoneOffset.UTC));
        });
    }
}
