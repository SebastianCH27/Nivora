package pe.nivora.backend.periodos.infraestructura;

import static pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc.utc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.periodos.dominio.CalendarioMensual;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Cambio;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Publicacion;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

@Repository
public class RepositorioPeriodosMensualesJdbc implements RepositorioPeriodosMensuales {
    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public RepositorioPeriodosMensualesJdbc(JdbcTemplate jdbc, Clock reloj, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
    }

    @Override
    public CalendarioMensual consultar() {
        try {
            var reglas = jdbc.query("""
                SELECT p.revision, p.desde_mes, p.dia_inicio, p.publicada_en_utc, p.actor_usuario
                FROM publicaciones_periodos_mensuales p
                WHERE NOT EXISTS (
                    SELECT 1 FROM publicaciones_periodos_mensuales mas_reciente
                    WHERE mas_reciente.desde_mes = p.desde_mes AND mas_reciente.revision > p.revision
                )
                """, (fila, numero) -> new Publicacion(fila.getLong("revision"),
                    new Cambio(YearMonth.from(fila.getObject("desde_mes", LocalDate.class)), fila.getInt("dia_inicio")),
                    fila.getObject("publicada_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    fila.getString("actor_usuario")));
            return new CalendarioMensual(reglas);
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public Publicacion publicar(long revisionEsperada, Cambio cambio, UsuarioAdministrador actor) {
        try {
            return transaccion.execute(estado -> {
                jdbc.queryForObject("SELECT id_control FROM control_periodos_mensuales WHERE id_control = 1 FOR UPDATE",
                    Integer.class);
                long ultima = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM publicaciones_periodos_mensuales",
                    Long.class);
                if (ultima != revisionEsperada) {
                    throw new Conflicto("La revision del calendario cambio. Consulta el calendario antes de publicar.");
                }
                var ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
                if (!cambio.desde().isAfter(YearMonth.from(ahora.atZone(MesConsumo.ZONA)))) {
                    throw new IllegalArgumentException("La vigencia debe comenzar en un mes civil futuro.");
                }
                long siguiente = Math.incrementExact(ultima);
                jdbc.update("""
                    INSERT INTO publicaciones_periodos_mensuales
                        (revision, desde_mes, dia_inicio, publicada_en_utc, actor_usuario) VALUES (?, ?, ?, ?, ?)
                    """, siguiente, cambio.desde().atDay(1), cambio.diaInicio(), utc(ahora), actor.valor());
                return new Publicacion(siguiente, cambio, ahora, actor.valor());
            });
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }
}
