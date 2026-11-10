package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.MotivoRechazoLectura;
import pe.nivora.backend.lecturas.dominio.PoliticaIncorporacionLecturas;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

@Repository
public class RepositorioLecturasJdbc implements RepositorioLecturas {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final JsonMapper json;

    public RepositorioLecturasJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor,
                                   JsonMapper json) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.json = json;
    }

    @Override
    public ResultadoIncorporacion incorporar(LoteLecturas lote, Instant recibidaEn) {
        return incorporar(lote, recibidaEn, null);
    }

    @Override
    public ResultadoIncorporacion incorporar(LoteLecturas lote, Instant recibidaEn,
            ContextoLecturas contexto) {
        Objects.requireNonNull(lote);
        Objects.requireNonNull(recibidaEn);
        if (lote.lecturas().size() > 500) {
            throw new IllegalArgumentException("El lote no puede superar 500 lecturas.");
        }
        if (contexto != null && (!contexto.fuente().equals(lote.fuente())
                || !contexto.suministro().equals(lote.suministro()))) {
            throw new IllegalArgumentException("El contexto no corresponde al lote.");
        }
        try {
            return transaccion.execute(estado -> incorporarEnTransaccion(lote, recibidaEn, contexto));
        } catch (DataAccessException | TransactionException | JacksonException ex) {
            throw new RepositorioLecturasNoDisponibleException(
                "No se pudo completar la incorporacion de lecturas en Nivora.", ex);
        }
    }

    private ResultadoIncorporacion incorporarEnTransaccion(LoteLecturas lote, Instant recibidaEn,
            ContextoLecturas contexto) {
        // Adquirir el bloqueo antes de cualquier lectura consistente del lote.
        jdbc.update("""
            INSERT INTO control_incorporacion_lecturas (fuente) VALUES (?)
            ON DUPLICATE KEY UPDATE fuente = fuente
            """, lote.fuente());
        jdbc.queryForObject("""
            SELECT fuente FROM control_incorporacion_lecturas WHERE fuente = ? FOR UPDATE
            """, String.class, lote.fuente());
        Long suministros = jdbc.queryForObject(
            "SELECT COUNT(*) FROM suministros WHERE codigo = ?", Long.class, lote.suministro().valor());
        if (suministros == null || suministros == 0) {
            throw new SuministroNoRegistradoException();
        }

        int nuevas = 0;
        int repetidas = 0;
        int rechazadas = 0;
        var recepcion = aUtc(recibidaEn.truncatedTo(ChronoUnit.MICROS));
        for (var lectura : lote.lecturas()) {
            var existente = buscarInterno(lectura.fuente(), lectura.idLectura());
            boolean identica = existente.isPresent() && existente.orElseThrow().equals(lectura);
            if (identica && contexto == null) {
                repetidas++;
                continue;
            }
            Optional<MotivoRechazoLectura> motivo = existente.isPresent() && !identica
                ? Optional.of(MotivoRechazoLectura.ID_EN_CONFLICTO)
                : PoliticaIncorporacionLecturas.validarFecha(lectura, recibidaEn);
            if (motivo.isEmpty() && contexto != null) motivo = contexto.validar(lectura);
            if (identica && motivo.isEmpty()) {
                repetidas++;
                continue;
            }
            if (motivo.isEmpty()) {
                var mismaMedicion = jdbc.queryForList("""
                    SELECT id_lectura FROM lecturas
                    WHERE fuente = ? AND codigo_suministro = ? AND id_medidor = ?
                        AND fecha_medicion_utc = ?
                    """, String.class, lectura.fuente(), lectura.suministro().valor(),
                    lectura.idMedidor(), aUtc(lectura.fechaMedicion()));
                if (!mismaMedicion.isEmpty()) {
                    motivo = Optional.of(MotivoRechazoLectura.MEDICION_CON_OTRO_ID);
                } else {
                    motivo = PoliticaIncorporacionLecturas.validarContador(lectura,
                        vecino(lectura, true), vecino(lectura, false));
                }
            }
            if (motivo.isPresent()) {
                registrarIncidencia(lectura, motivo.orElseThrow(), recepcion);
                rechazadas++;
            } else {
                jdbc.update("""
                    INSERT INTO lecturas (fuente, id_lectura, codigo_suministro, id_medidor,
                        fecha_medicion_utc, valor_litros, unidad, tipo_medicion, recibida_en_utc)
                    VALUES (?, ?, ?, ?, ?, ?, 'L', 'ACUMULADA', ?)
                    """, lectura.fuente(), lectura.idLectura(), lectura.suministro().valor(),
                    lectura.idMedidor(), aUtc(lectura.fechaMedicion()), lectura.valorLitros(), recepcion);
                nuevas++;
            }
        }
        return new ResultadoIncorporacion(nuevas, repetidas, rechazadas);
    }

    private OptionalLong vecino(LecturaAcumulada lectura, boolean anterior) {
        String condicion = anterior ? "<" : ">";
        String orden = anterior ? "DESC" : "ASC";
        // Los fragmentos son constantes internas; todos los datos se envian como parametros.
        var valores = jdbc.queryForList("""
            SELECT valor_litros FROM lecturas
            WHERE fuente = ? AND codigo_suministro = ? AND id_medidor = ?
                AND fecha_medicion_utc
            """ + condicion + " ? ORDER BY fecha_medicion_utc " + orden + " LIMIT 1",
            Long.class, lectura.fuente(), lectura.suministro().valor(), lectura.idMedidor(),
            aUtc(lectura.fechaMedicion()));
        return valores.isEmpty() ? OptionalLong.empty() : OptionalLong.of(valores.getFirst());
    }

    private void registrarIncidencia(LecturaAcumulada lectura, MotivoRechazoLectura motivo,
                                     LocalDateTime recepcion) {
        var detalle = new Detalle(lectura.fuente(), lectura.idLectura(), lectura.suministro().valor(),
            lectura.idMedidor(), lectura.fechaMedicion().toString(), lectura.valorLitros());
        String datos = json.writeValueAsString(detalle);
        String huella = huella(motivo.name(), detalle.fuente(), detalle.idLectura(),
            detalle.codigoSuministro(), detalle.idMedidor(), detalle.fechaMedicion(),
            Long.toString(detalle.valorLitros()));
        jdbc.update("""
            INSERT INTO incidencias_lecturas (fuente, id_lectura, codigo_suministro, motivo,
                datos_recibidos, huella, primera_recepcion_utc, ultima_recepcion_utc, repeticiones)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE
                ultima_recepcion_utc = GREATEST(ultima_recepcion_utc, ?),
                repeticiones = repeticiones + 1
            """, lectura.fuente(), lectura.idLectura(), lectura.suministro().valor(), motivo.name(),
            datos, huella, recepcion, recepcion, recepcion);
    }

    private static String huella(String... campos) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String campo : campos) {
                byte[] bytes = campo.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible.", ex);
        }
    }

    @Override
    public Optional<LecturaAcumulada> buscar(String fuente, String idLectura) {
        try {
            return buscarInterno(fuente, idLectura);
        } catch (DataAccessException ex) {
            throw new RepositorioLecturasNoDisponibleException("No se pudo consultar la lectura guardada.", ex);
        }
    }

    private Optional<LecturaAcumulada> buscarInterno(String fuente, String idLectura) {
        return jdbc.query("SELECT * FROM lecturas WHERE fuente = ? AND id_lectura = ?",
            RepositorioLecturasJdbc::mapear, fuente, idLectura).stream().findFirst();
    }

    @Override
    public long contar(CodigoSuministro codigo) {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM lecturas WHERE codigo_suministro = ?",
                Long.class, codigo.valor());
        } catch (DataAccessException ex) {
            throw new RepositorioLecturasNoDisponibleException("No se pudieron contar las lecturas guardadas.", ex);
        }
    }

    @Override
    public long contarIncidencias(CodigoSuministro codigo) {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM incidencias_lecturas WHERE codigo_suministro = ?",
                Long.class, codigo.valor());
        } catch (DataAccessException ex) {
            throw new RepositorioLecturasNoDisponibleException("No se pudieron consultar las incidencias.", ex);
        }
    }

    private static LecturaAcumulada mapear(ResultSet fila, int numero) throws SQLException {
        return new LecturaAcumulada(fila.getString("fuente"), fila.getString("id_lectura"),
            new CodigoSuministro(fila.getString("codigo_suministro")), fila.getString("id_medidor"),
            fila.getObject("fecha_medicion_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
            fila.getLong("valor_litros"));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private record Detalle(String fuente, String idLectura, String codigoSuministro,
                           String idMedidor, String fechaMedicion, long valorLitros) {}
}
