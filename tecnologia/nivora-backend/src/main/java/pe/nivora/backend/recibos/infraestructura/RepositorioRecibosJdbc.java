package pe.nivora.backend.recibos.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.ReciboEmitido;
import pe.nivora.backend.recibos.dominio.RecibosEmitidos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

@Repository
public class RepositorioRecibosJdbc implements RepositorioRecibos {
    private static final String SELECCION = """
        SELECT numero_recibo, periodo, consumo_litros, cargo_fijo, importe_consumo, importe_total,
               fecha_disponibilidad, fecha_vencimiento, codigo_tarifa, version_tarifa, recibido_en_utc
        FROM recibos
        """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;
    private final TransactionTemplate transaccion;

    public RepositorioRecibosJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.consultas = new NamedParameterJdbcTemplate(jdbc);
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Incorporacion guardar(RecibosEmitidos lote, Instant recibidoEn) {
        var utc = LocalDateTime.ofInstant(recibidoEn.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        try {
            return transaccion.execute(estado -> guardarEnTransaccion(lote, utc));
        } catch (DuplicateKeyException error) {
            throw new Conflicto();
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }

    private Incorporacion guardarEnTransaccion(RecibosEmitidos lote, LocalDateTime utc) {
        String codigo = lote.suministro().valor();
        // Serializa las importaciones del mismo suministro entre instancias.
        if (jdbc.queryForList("SELECT codigo FROM suministros WHERE codigo = ? FOR UPDATE",
                String.class, codigo).isEmpty()) {
            throw new SuministroNoRegistrado();
        }
        if (lote.recibos().isEmpty()) {
            return new Incorporacion(0, 0);
        }
        var periodos = lote.recibos().stream().map(recibo -> recibo.periodo().atDay(1)).toList();
        var existentes = consultas.query(SELECCION + """
            WHERE codigo_suministro = :codigo AND periodo IN (:periodos)
            FOR UPDATE
            """, Map.of("codigo", codigo, "periodos", periodos), (fila, indice) -> leerRecibo(fila)).stream()
            .collect(Collectors.toMap(ReciboEmitido::periodo, Function.identity()));

        var nuevos = new ArrayList<Object[]>();
        for (var recibo : lote.recibos()) {
            var anterior = existentes.get(recibo.periodo());
            if (anterior == null) {
                nuevos.add(new Object[] {codigo, recibo.periodo().atDay(1), recibo.numeroRecibo(),
                    recibo.consumoLitros(), recibo.cargoFijo(), recibo.importeConsumo(), recibo.importeTotal(),
                    recibo.fechaDisponibilidad(), recibo.fechaVencimiento(), recibo.codigoTarifa(),
                    recibo.versionTarifa(), utc});
            } else if (!anterior.equals(recibo)) {
                throw new Conflicto();
            }
        }
        if (!nuevos.isEmpty()) {
            jdbc.batchUpdate("""
                INSERT INTO recibos (codigo_suministro, periodo, numero_recibo, consumo_litros, cargo_fijo,
                    importe_consumo, importe_total, fecha_disponibilidad, fecha_vencimiento, codigo_tarifa,
                    version_tarifa, recibido_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, nuevos);
        }
        return new Incorporacion(nuevos.size(), existentes.size());
    }

    @Override
    public List<Guardado> buscarRecientes(CodigoSuministro suministro, int limite) {
        if (limite < 1 || limite > 60) {
            throw new IllegalArgumentException("El limite de recibos debe estar entre 1 y 60.");
        }
        try {
            return jdbc.query(SELECCION + """
                WHERE codigo_suministro = ?
                ORDER BY periodo DESC
                LIMIT ?
                """, (fila, indice) -> new Guardado(leerRecibo(fila),
                    fila.getObject("recibido_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                suministro.valor(), limite);
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    private static ReciboEmitido leerRecibo(ResultSet fila) throws SQLException {
        return new ReciboEmitido(fila.getString("numero_recibo"),
            YearMonth.from(fila.getObject("periodo", LocalDate.class)), fila.getBigDecimal("consumo_litros"),
            fila.getBigDecimal("cargo_fijo"), fila.getBigDecimal("importe_consumo"), fila.getBigDecimal("importe_total"),
            fila.getObject("fecha_disponibilidad", LocalDate.class), fila.getObject("fecha_vencimiento", LocalDate.class),
            fila.getString("codigo_tarifa"), fila.getInt("version_tarifa"));
    }
}
