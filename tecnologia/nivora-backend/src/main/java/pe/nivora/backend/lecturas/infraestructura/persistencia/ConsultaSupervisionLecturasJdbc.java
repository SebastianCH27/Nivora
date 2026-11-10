package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.SupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.SupervisionLecturas.*;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaSupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.SupervisionLecturasNoDisponibleException;

@Repository
public class ConsultaSupervisionLecturasJdbc implements ConsultaSupervisionLecturas {
    private final JdbcTemplate jdbc;

    public ConsultaSupervisionLecturasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SupervisionLecturas> consultar(String fuente, CodigoSuministro suministro,
            Instant ahora) {
        try {
            // Una sola lectura consistente. Sin bloqueos FOR UPDATE, escrituras ni HTTP.
            return jdbc.query("""
                SELECT s.codigo,
                    l.cantidad, l.primera_medicion, l.ultima_medicion, l.ultima_recepcion,
                    i.cantidad AS incidencias, ir.cantidad AS incidencias_abiertas,
                    p.estado, p.cursor_confirmado, p.revision,
                    p.ultima_confirmacion_utc, p.actualizado_en_utc,
                    c.hasta_id_lectura, c.confirmado_hasta_utc, c.capturado_en_utc,
                    a.codigo_suministro AS programado, a.ultimo_resultado,
                    a.fallos_consecutivos, a.ultimo_intento_utc, a.ultimo_exito_utc,
                    a.proximo_intento_utc, a.reserva_hasta_utc
                FROM suministros s
                CROSS JOIN (
                    SELECT COUNT(*) AS cantidad, MIN(fecha_medicion_utc) AS primera_medicion,
                        MAX(fecha_medicion_utc) AS ultima_medicion,
                        MAX(recibida_en_utc) AS ultima_recepcion
                    FROM lecturas WHERE fuente = ? AND codigo_suministro = ?
                ) l
                CROSS JOIN (
                    SELECT COUNT(*) AS cantidad FROM incidencias_lecturas
                    WHERE fuente = ? AND codigo_suministro = ?
                ) i
                CROSS JOIN (
                    SELECT COUNT(*) AS cantidad FROM incidencias_recuperacion_lecturas
                    WHERE fuente = ? AND codigo_suministro = ? AND abierta = TRUE
                ) ir
                LEFT JOIN progreso_recuperacion_lecturas p
                    ON p.fuente = ? AND p.codigo_suministro = s.codigo
                LEFT JOIN contexto_recuperacion_lecturas c
                    ON c.fuente = p.fuente AND c.codigo_suministro = p.codigo_suministro
                LEFT JOIN programacion_sincronizacion_lecturas a
                    ON a.fuente = ? AND a.codigo_suministro = s.codigo
                WHERE s.codigo = ?
                """, (fila, numero) -> mapear(fila, fuente, suministro, ahora),
                fuente, suministro.valor(), fuente, suministro.valor(),
                fuente, suministro.valor(), fuente, fuente, suministro.valor())
                .stream().findFirst();
        } catch (RuntimeException error) {
            // Incluye datos guardados incompatibles: no convertirlos en ausencia o cero.
            throw new SupervisionLecturasNoDisponibleException(error);
        }
    }

    private static SupervisionLecturas mapear(ResultSet fila, String fuente,
            CodigoSuministro suministro, Instant ahora) throws SQLException {
        var almacenamiento = new Almacenamiento(fila.getLong("cantidad"),
            fecha(fila, "primera_medicion"), fecha(fila, "ultima_medicion"),
            fecha(fila, "ultima_recepcion"), fila.getLong("incidencias"),
            fila.getLong("incidencias_abiertas"));
        Corte corte = fila.getString("hasta_id_lectura") == null ? null : new Corte(
            fila.getString("hasta_id_lectura"), fecha(fila, "confirmado_hasta_utc"),
            fecha(fila, "capturado_en_utc"));
        Recuperacion recuperacion = fila.getString("estado") == null ? null : new Recuperacion(
            EstadoRecuperacion.valueOf(fila.getString("estado")),
            fila.getString("cursor_confirmado"), Long.toString(fila.getLong("revision")),
            fecha(fila, "ultima_confirmacion_utc"), fecha(fila, "actualizado_en_utc"), corte);
        Programacion programacion = null;
        if (fila.getString("programado") != null) {
            String ultimo = fila.getString("ultimo_resultado");
            Instant reserva = fecha(fila, "reserva_hasta_utc");
            programacion = new Programacion(ultimo == null ? null : EstadoSincronizacion.valueOf(ultimo),
                fila.getInt("fallos_consecutivos"), fecha(fila, "ultimo_intento_utc"),
                fecha(fila, "ultimo_exito_utc"), fecha(fila, "proximo_intento_utc"), reserva,
                reserva != null && reserva.isAfter(ahora),
                reserva != null && !reserva.isAfter(ahora));
        }
        return new SupervisionLecturas(fuente, suministro, ahora, almacenamiento,
            recuperacion, programacion);
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        var fecha = fila.getObject(columna, LocalDateTime.class);
        return fecha == null ? null : fecha.toInstant(ZoneOffset.UTC);
    }
}
