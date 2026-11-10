package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ProgresoRecuperacion;
import pe.nivora.backend.lecturas.dominio.contratos.ProgresoRecuperacionModificadoException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;
import pe.nivora.backend.lecturas.infraestructura.ContextoLecturasJson;

@Repository
public class RepositorioRecuperacionLecturasJdbc implements RepositorioRecuperacionLecturas {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final RepositorioLecturas lecturas;

    public RepositorioRecuperacionLecturasJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor,
                                              RepositorioLecturas lecturas) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.lecturas = lecturas;
    }

    @Override
    public ProgresoRecuperacion consultar(String fuente, CodigoSuministro suministro) {
        try {
            comprobarSuministro(suministro);
            return buscar(fuente, suministro, false)
                .orElseGet(() -> ProgresoRecuperacion.inicial(fuente, suministro));
        } catch (DataAccessException ex) {
            throw new RepositorioLecturasNoDisponibleException("No se pudo consultar el progreso de lecturas.", ex);
        }
    }

    @Override
    public ProgresoRecuperacion iniciarContexto(ProgresoRecuperacion esperado,
            ContextoLecturas contexto, Instant ahora) {
        return ejecutarAtomico(() -> {
            var siguiente = bloquearYComprobar(esperado).iniciarContexto(contexto, ahora);
            guardar(siguiente);
            String inventario = ContextoLecturasJson.inventario(contexto);
            jdbc.update("""
                INSERT INTO contexto_recuperacion_lecturas (fuente, codigo_suministro,
                    hasta_id_lectura, confirmado_hasta_utc, inventario_json, capturado_en_utc)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE hasta_id_lectura = ?, confirmado_hasta_utc = ?,
                    inventario_json = ?, capturado_en_utc = ?
                """, siguiente.fuente(), siguiente.suministro().valor(),
                contexto.hastaIdLectura(), aUtc(contexto.confirmadoHasta()), inventario,
                aUtc(siguiente.actualizadoEn()), contexto.hastaIdLectura(),
                aUtc(contexto.confirmadoHasta()), inventario, aUtc(siguiente.actualizadoEn()));
            return siguiente;
        });
    }

    @Override
    public Paso confirmar(ProgresoRecuperacion esperado, LoteLecturas lote, Instant ahora) {
        return ejecutarAtomico(() -> {
            var actual = bloquearYComprobar(esperado);
            actual.comprobarPagina(lote);
            // La incorporacion existente se une a esta misma transaccion JDBC.
            var resultado = lecturas.incorporar(lote, ahora, actual.contexto());
            var siguiente = actual.confirmar(lote, resultado, ahora);
            guardar(siguiente);
            jdbc.update("""
                UPDATE incidencias_recuperacion_lecturas
                SET abierta = FALSE, ultima_resolucion_utc = ?
                WHERE fuente = ? AND codigo_suministro = ? AND abierta = TRUE
                """, aUtc(siguiente.actualizadoEn()), actual.fuente(), actual.suministro().valor());
            return new Paso(siguiente, resultado);
        });
    }

    @Override
    public ProgresoRecuperacion registrarFallo(ProgresoRecuperacion esperado,
                                               EstadoRecuperacion fallo, Instant ahora) {
        return ejecutarAtomico(() -> {
            var actual = bloquearYComprobar(esperado);
            var siguiente = actual.registrarFallo(fallo, ahora);
            guardar(siguiente);
            var fecha = aUtc(siguiente.actualizadoEn());
            jdbc.update("""
                INSERT INTO incidencias_recuperacion_lecturas (fuente, codigo_suministro, tipo,
                    primera_recepcion_utc, ultima_recepcion_utc, repeticiones, abierta)
                VALUES (?, ?, ?, ?, ?, 1, TRUE)
                ON DUPLICATE KEY UPDATE
                    ultima_recepcion_utc = GREATEST(ultima_recepcion_utc, ?),
                    repeticiones = repeticiones + 1, abierta = TRUE
                """, actual.fuente(), actual.suministro().valor(), fallo.name(), fecha, fecha, fecha);
            return siguiente;
        });
    }

    @Override
    public ProgresoRecuperacion reiniciar(ProgresoRecuperacion esperado, Instant ahora) {
        return ejecutarAtomico(() -> {
            var siguiente = bloquearYComprobar(esperado).reiniciar(ahora);
            guardar(siguiente);
            jdbc.update("DELETE FROM contexto_recuperacion_lecturas WHERE fuente = ? AND codigo_suministro = ?",
                siguiente.fuente(), siguiente.suministro().valor());
            return siguiente;
        });
    }

    private ProgresoRecuperacion bloquearYComprobar(ProgresoRecuperacion esperado) {
        jdbc.update("""
            INSERT INTO control_incorporacion_lecturas (fuente) VALUES (?)
            ON DUPLICATE KEY UPDATE fuente = fuente
            """, esperado.fuente());
        jdbc.queryForObject("SELECT fuente FROM control_incorporacion_lecturas WHERE fuente = ? FOR UPDATE",
            String.class, esperado.fuente());
        comprobarSuministro(esperado.suministro());
        var actual = buscar(esperado.fuente(), esperado.suministro(), true)
            .orElseGet(() -> ProgresoRecuperacion.inicial(esperado.fuente(), esperado.suministro()));
        if (actual.revision() != esperado.revision() || !actual.cursor().equals(esperado.cursor())
                || actual.estado() != esperado.estado()) {
            throw new ProgresoRecuperacionModificadoException();
        }
        return actual;
    }

    private void comprobarSuministro(CodigoSuministro suministro) {
        Long cantidad = jdbc.queryForObject("SELECT COUNT(*) FROM suministros WHERE codigo = ?",
            Long.class, suministro.valor());
        if (cantidad == null || cantidad == 0) { throw new SuministroNoRegistradoException(); }
    }

    private Optional<ProgresoRecuperacion> buscar(String fuente, CodigoSuministro suministro, boolean bloquear) {
        String bloqueo = bloquear ? " FOR UPDATE" : "";
        return jdbc.query("""
            SELECT p.fuente, p.codigo_suministro, p.cursor_confirmado, p.revision, p.estado,
                p.ultima_confirmacion_utc, p.actualizado_en_utc,
                c.hasta_id_lectura, c.confirmado_hasta_utc, c.inventario_json
            FROM progreso_recuperacion_lecturas p
            LEFT JOIN contexto_recuperacion_lecturas c
                ON c.fuente = p.fuente AND c.codigo_suministro = p.codigo_suministro
            WHERE p.fuente = ? AND p.codigo_suministro = ?
            """ + bloqueo, RepositorioRecuperacionLecturasJdbc::mapear,
            fuente, suministro.valor()).stream().findFirst();
    }

    private void guardar(ProgresoRecuperacion progreso) {
        var confirmacion = aUtc(progreso.ultimaConfirmacion());
        var actualizacion = aUtc(progreso.actualizadoEn());
        jdbc.update("""
            INSERT INTO progreso_recuperacion_lecturas (fuente, codigo_suministro, cursor_confirmado,
                revision, estado, ultima_confirmacion_utc, actualizado_en_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE cursor_confirmado = ?, revision = ?, estado = ?,
                ultima_confirmacion_utc = ?, actualizado_en_utc = ?
            """, progreso.fuente(), progreso.suministro().valor(), progreso.cursor(), progreso.revision(),
            progreso.estado().name(), confirmacion, actualizacion, progreso.cursor(), progreso.revision(),
            progreso.estado().name(), confirmacion, actualizacion);
    }

    private <T> T ejecutarAtomico(Supplier<T> operacion) {
        try {
            return transaccion.execute(estado -> operacion.get());
        } catch (DataAccessException | TransactionException | JacksonException ex) {
            throw new RepositorioLecturasNoDisponibleException(
                "No se pudo completar el registro de recuperacion de lecturas.", ex);
        }
    }

    private static ProgresoRecuperacion mapear(ResultSet fila, int numero) throws SQLException {
        var confirmacion = fila.getObject("ultima_confirmacion_utc", LocalDateTime.class);
        ContextoLecturas contexto = null;
        String hasta = fila.getString("hasta_id_lectura");
        if (hasta != null) {
            try {
                contexto = ContextoLecturasJson.restaurar(fila.getString("fuente"),
                    new CodigoSuministro(fila.getString("codigo_suministro")), hasta,
                    fila.getObject("confirmado_hasta_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    fila.getString("inventario_json"));
            } catch (RuntimeException error) {
                throw new RepositorioLecturasNoDisponibleException(
                    "El contexto de recuperacion guardado no es utilizable.", error);
            }
        }
        return new ProgresoRecuperacion(fila.getString("fuente"),
            new CodigoSuministro(fila.getString("codigo_suministro")), fila.getString("cursor_confirmado"),
            fila.getLong("revision"), EstadoRecuperacion.valueOf(fila.getString("estado")),
            confirmacion == null ? null : confirmacion.toInstant(ZoneOffset.UTC),
            fila.getObject("actualizado_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC), contexto);
    }

    private static LocalDateTime aUtc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
