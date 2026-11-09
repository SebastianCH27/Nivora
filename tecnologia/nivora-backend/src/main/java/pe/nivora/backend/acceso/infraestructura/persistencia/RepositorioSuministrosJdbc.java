package pe.nivora.backend.acceso.infraestructura.persistencia;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministrosNoDisponibleException;

@Repository
public class RepositorioSuministrosJdbc implements RepositorioSuministros {

    private final JdbcTemplate jdbc;

    public RepositorioSuministrosJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardar(SuministroVerificado verificado) {
        Objects.requireNonNull(verificado);
        var suministro = verificado.suministro();
        var fechaUtc = LocalDateTime.ofInstant(verificado.fechaVerificacion(), ZoneOffset.UTC);

        try {
            // Una sentencia atomica y una clave primaria evitan duplicados durante reintentos.
            jdbc.update("""
                INSERT INTO suministros (codigo, titular, verificado_en_utc)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    titular = CASE WHEN verificado_en_utc <= ? THEN ? ELSE titular END,
                    verificado_en_utc = GREATEST(verificado_en_utc, ?)
                """, suministro.codigo().valor(), suministro.titular(), fechaUtc,
                fechaUtc, suministro.titular(), fechaUtc);
        } catch (DataAccessException ex) {
            throw new RepositorioSuministrosNoDisponibleException(
                "No se pudo guardar el suministro en Nivora.", ex);
        }
    }

    @Override
    public Optional<SuministroVerificado> buscar(CodigoSuministro codigo) {
        Objects.requireNonNull(codigo);
        try {
            return jdbc.query("""
                SELECT codigo, titular, verificado_en_utc
                FROM suministros
                WHERE codigo = ?
                """, (fila, numero) -> new SuministroVerificado(
                    new Suministro(new CodigoSuministro(fila.getString("codigo")),
                        fila.getString("titular")),
                    fila.getObject("verificado_en_utc", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC)), codigo.valor()).stream().findFirst();
        } catch (DataAccessException ex) {
            throw new RepositorioSuministrosNoDisponibleException(
                "No se pudo consultar el suministro almacenado en Nivora.", ex);
        }
    }
}
