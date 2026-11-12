package pe.nivora.backend.recibos.infraestructura;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos;

@Repository
public class RepositorioSincronizacionRecibosJdbc implements RepositorioSincronizacionRecibos {
    private static final String FUENTE = "empresa-agua";
    private final ProgramacionSincronizacionJdbc programacion;

    public RepositorioSincronizacionRecibosJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.programacion = new ProgramacionSincronizacionJdbc(jdbc, gestor,
            ProgramacionSincronizacionJdbc.Tabla.RECIBOS);
    }

    @Override
    public void prepararSuministros(Instant ahora) {
        acceder(() -> {
            programacion.prepararSuministros(FUENTE, ahora);
            return null;
        });
    }

    @Override
    public Optional<Reserva> reservar(Instant elegiblesHasta, Instant ahora, Duration duracion) {
        return acceder(() -> programacion.reservar(FUENTE, elegiblesHasta, ahora, duracion)
            .map(reserva -> new Reserva(new CodigoSuministro(reserva.codigo()), reserva.token(),
                reserva.vence(), reserva.fallos())));
    }

    @Override
    public boolean finalizar(Reserva reserva, Estado estado, int fallos, Instant proximoIntento, Instant ahora) {
        return acceder(() -> programacion.finalizar(FUENTE, reserva.suministro().valor(), reserva.token(),
            estado.name(), estado == Estado.ACTUALIZADO, fallos, proximoIntento, ahora));
    }

    private static <T> T acceder(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }
}
