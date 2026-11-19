package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.Lectura;
import pe.nivora.backend.lecturas.dominio.LecturaIntervalo;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;

@Repository
public class ConsultaLecturasPeriodoJdbc implements ConsultaLecturasPeriodo {
    private final JdbcTemplate jdbc;

    public ConsultaLecturasPeriodoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Datos> consultar(String fuente, CodigoSuministro suministro, Instant desde,
            Instant hasta) {
        return consultar(fuente, suministro, desde, hasta, hasta);
    }

    @Override
    public Optional<Datos> consultar(String fuente, CodigoSuministro suministro, Instant desde,
            Instant hasta, Instant observadoHasta) {
        // Una sola consulta sin bloqueos mantiene coherentes lecturas y estado.
        try {
            return jdbc.query("""
                SELECT l.id_lectura, l.id_medidor, l.fecha_medicion_utc, l.valor_litros,
                    l.tipo_medicion, l.inicio_intervalo_utc,
                    CASE WHEN l.id_lectura IS NULL THEN (
                        SELECT MAX(anterior.fecha_medicion_utc) FROM lecturas anterior
                        WHERE anterior.fuente = ? AND anterior.codigo_suministro = s.codigo
                            AND anterior.fecha_medicion_utc <= ?
                    ) END AS ultima_anterior,
                    p.estado, c.confirmado_hasta_utc, a.ultimo_resultado
                FROM suministros s
                LEFT JOIN lecturas l ON l.fuente = ? AND l.codigo_suministro = s.codigo
                    AND ((l.tipo_medicion = 'ACUMULADA' AND l.fecha_medicion_utc BETWEEN ? AND ?)
                        OR (l.tipo_medicion = 'INTERVALO' AND l.inicio_intervalo_utc < ?
                            AND l.fecha_medicion_utc > ? AND l.fecha_medicion_utc <= ?))
                LEFT JOIN progreso_recuperacion_lecturas p ON p.fuente = ? AND p.codigo_suministro = s.codigo
                LEFT JOIN contexto_recuperacion_lecturas c
                    ON c.fuente = p.fuente AND c.codigo_suministro = p.codigo_suministro
                LEFT JOIN programacion_sincronizacion_lecturas a
                    ON a.fuente = ? AND a.codigo_suministro = s.codigo
                WHERE s.codigo = ?
                ORDER BY l.fecha_medicion_utc, l.id_medidor
                """, (ResultSetExtractor<Optional<Datos>>) filas -> mapear(filas, fuente, suministro),
                fuente, utc(desde), fuente, utc(desde), utc(hasta), utc(hasta), utc(desde),
                utc(observadoHasta), fuente, fuente, suministro.valor());
        } catch (DataAccessException | IllegalArgumentException error) {
            throw new RepositorioLecturasNoDisponibleException(
                "No se pudieron consultar las lecturas guardadas del periodo.", error);
        }
    }

    private static Optional<Datos> mapear(ResultSet filas, String fuente,
            CodigoSuministro suministro) throws SQLException {
        if (!filas.next()) {
            return Optional.empty();
        }
        Instant ultimaAnterior = fecha(filas, "ultima_anterior");
        Instant corte = fecha(filas, "confirmado_hasta_utc");
        String estado = filas.getString("estado");
        String resultado = filas.getString("ultimo_resultado");
        var lecturas = new ArrayList<Lectura>();
        do {
            String id = filas.getString("id_lectura");
            if (id != null) {
                if ("INTERVALO".equals(filas.getString("tipo_medicion"))) {
                    lecturas.add(new LecturaIntervalo(fuente, id, suministro, filas.getString("id_medidor"),
                        fecha(filas, "inicio_intervalo_utc"), fecha(filas, "fecha_medicion_utc"),
                        filas.getLong("valor_litros")));
                } else {
                    lecturas.add(new LecturaAcumulada(fuente, id, suministro, filas.getString("id_medidor"),
                        fecha(filas, "fecha_medicion_utc"), filas.getLong("valor_litros")));
                }
            }
        } while (filas.next());

        return Optional.of(new Datos(lecturas, ultimaAnterior,
            estado == null ? null : EstadoRecuperacion.valueOf(estado), corte,
            resultado == null ? null : EstadoSincronizacion.valueOf(resultado)));
    }

    private static LocalDateTime utc(Instant fecha) {
        return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC);
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        var fecha = fila.getObject(columna, LocalDateTime.class);
        return fecha == null ? null : fecha.toInstant(ZoneOffset.UTC);
    }
}
