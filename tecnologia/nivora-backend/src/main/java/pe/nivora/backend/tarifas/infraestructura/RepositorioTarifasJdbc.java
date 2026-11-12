package pe.nivora.backend.tarifas.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;

@Repository
public class RepositorioTarifasJdbc implements RepositorioTarifas {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final JsonMapper json;

    public RepositorioTarifasJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor, JsonMapper json) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.json = json;
    }

    @Override
    public Guardado guardar(HistorialTarifario historial, Instant consultadoEn) {
        var instante = consultadoEn.truncatedTo(ChronoUnit.MICROS);
        return transaccion.execute(estado -> guardarEnTransaccion(historial, instante));
    }

    private Guardado guardarEnTransaccion(HistorialTarifario historial, Instant consultadoEn) {
        var suministro = historial.suministro();
        String codigo = suministro.valor();
        String fuente = historial.fuente();
        if (jdbc.queryForList("SELECT codigo FROM suministros WHERE codigo = ? FOR UPDATE",
                String.class, codigo).isEmpty()) {
            throw new IllegalStateException("Verifica el suministro en Nivora antes de incorporar tarifas.");
        }

        var ultimo = buscar(fuente, suministro).orElse(null);
        if (ultimo != null) {
            if (consultadoEn.isBefore(ultimo.consultadoEn())) {
                return ultimo;
            }
            if (ultimo.historial().equals(historial)) {
                jdbc.update("""
                    UPDATE tarifas_historiales SET consultado_en_utc = ?
                    WHERE fuente = ? AND codigo_suministro = ? AND revision = ?
                    """, utc(consultadoEn), fuente, codigo, ultimo.revision());
                return new Guardado(ultimo.revision(), consultadoEn, historial);
            }
        }

        // Bloquear versiones compartidas en el mismo orden para todos los suministros.
        var versiones = historial.asignaciones().stream().map(HistorialTarifario.Asignacion::tarifa)
            .distinct().sorted(Comparator.comparing(VersionTarifaria::codigo)
                .thenComparingInt(VersionTarifaria::version)).toList();
        for (VersionTarifaria tarifa : versiones) {
            conservarVersion(fuente, tarifa);
        }

        long revision = ultimo == null ? 1 : Math.incrementExact(ultimo.revision());
        jdbc.update("""
            INSERT INTO tarifas_historiales (fuente, codigo_suministro, revision, consultado_en_utc)
            VALUES (?, ?, ?, ?)
            """, fuente, codigo, revision, utc(consultadoEn));
        if (!historial.asignaciones().isEmpty()) {
            var filas = historial.asignaciones().stream().map(a -> new Object[] {
                fuente, codigo, revision, a.aplicableDesde(), a.vigenteHasta(), a.asignadaDesde(),
                a.asignadaHasta(), a.tarifa().codigo(), a.tarifa().version()
            }).toList();
            jdbc.batchUpdate("""
                INSERT INTO tarifas_asignaciones (fuente, codigo_suministro, revision, aplicable_desde,
                    vigente_hasta, asignada_desde, asignada_hasta, codigo_tarifa, version_tarifa)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, filas);
        }
        return new Guardado(revision, consultadoEn, historial);
    }

    private void conservarVersion(String fuente, VersionTarifaria tarifa) {
        jdbc.update("""
            INSERT INTO tarifas_versiones (fuente, codigo_tarifa, version_tarifa, nombre,
                vigente_desde, cargo_fijo, igv_porcentaje, tramos_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE version_tarifa = version_tarifa
            """, fuente, tarifa.codigo(), tarifa.version(), tarifa.nombre(), tarifa.vigenteDesde(),
            tarifa.cargoFijo(), tarifa.igvPorcentaje(), json.writeValueAsString(tarifa.tramos()));
        var guardada = jdbc.queryForObject("""
            SELECT codigo_tarifa, version_tarifa, nombre, vigente_desde, cargo_fijo, igv_porcentaje, tramos_json
            FROM tarifas_versiones WHERE fuente = ? AND codigo_tarifa = ? AND version_tarifa = ?
            FOR UPDATE
            """, (fila, indice) -> leerVersion(fila), fuente, tarifa.codigo(), tarifa.version());
        if (!tarifa.equals(guardada)) {
            throw new IllegalStateException("La fuente cambio las condiciones de una version tarifaria guardada.");
        }
    }

    @Override
    public Optional<Guardado> buscar(String fuente, CodigoSuministro suministro) {
        return jdbc.query("""
            SELECT h.revision, h.consultado_en_utc, a.aplicable_desde, a.vigente_hasta,
                   a.asignada_desde, a.asignada_hasta,
                   v.codigo_tarifa, v.version_tarifa, v.nombre, v.vigente_desde,
                   v.cargo_fijo, v.igv_porcentaje, v.tramos_json
            FROM tarifas_historiales h
            LEFT JOIN tarifas_asignaciones a ON a.fuente = h.fuente
                AND a.codigo_suministro = h.codigo_suministro AND a.revision = h.revision
            LEFT JOIN tarifas_versiones v ON v.fuente = a.fuente
                AND v.codigo_tarifa = a.codigo_tarifa AND v.version_tarifa = a.version_tarifa
            WHERE h.fuente = ? AND h.codigo_suministro = ?
                AND h.revision = (
                    SELECT MAX(revision) FROM tarifas_historiales
                    WHERE fuente = h.fuente AND codigo_suministro = h.codigo_suministro
                )
            ORDER BY a.aplicable_desde
            """, filas -> {
                return leerHistorial(filas, fuente, suministro);
            }, fuente, suministro.valor());
    }

    private Optional<Guardado> leerHistorial(ResultSet filas, String fuente, CodigoSuministro suministro)
            throws SQLException {
        if (!filas.next()) return Optional.empty();
        long revision = filas.getLong("revision");
        Instant consultadoEn = filas.getObject("consultado_en_utc", LocalDateTime.class)
            .toInstant(ZoneOffset.UTC);
        var asignaciones = new ArrayList<HistorialTarifario.Asignacion>();
        do {
            var inicio = fecha(filas, "aplicable_desde");
            if (inicio != null) {
                var asignacion = new HistorialTarifario.Asignacion(leerVersion(filas),
                    fecha(filas, "vigente_hasta"), fecha(filas, "asignada_desde"), fecha(filas, "asignada_hasta"));
                if (!inicio.equals(asignacion.aplicableDesde())) {
                    throw new IllegalStateException("Las fechas tarifarias guardadas son inconsistentes.");
                }
                asignaciones.add(asignacion);
            }
        } while (filas.next());
        return Optional.of(new Guardado(revision, consultadoEn,
            new HistorialTarifario(fuente, suministro, asignaciones)));
    }

    private VersionTarifaria leerVersion(ResultSet fila) throws SQLException {
        var tramos = json.readValue(fila.getString("tramos_json"), VersionTarifaria.Tramo[].class);
        return new VersionTarifaria(fila.getString("codigo_tarifa"), fila.getInt("version_tarifa"),
            fila.getString("nombre"), fecha(fila, "vigente_desde"), fila.getBigDecimal("cargo_fijo"),
            fila.getBigDecimal("igv_porcentaje"), List.of(tramos));
    }

    private static LocalDate fecha(ResultSet fila, String campo) throws SQLException {
        return fila.getObject(campo, LocalDate.class);
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
