package pe.nivora.backend.tarifas.infraestructura;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;

@Repository
public class RepositorioSincronizacionTarifasJdbc implements RepositorioSincronizacionTarifas {
    private static final String FUENTE = "empresa-agua";
    private final ProgramacionSincronizacionJdbc programacion;

    public RepositorioSincronizacionTarifasJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        programacion = new ProgramacionSincronizacionJdbc(jdbc, gestor,
            ProgramacionSincronizacionJdbc.Tabla.TARIFAS);
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
            .map(r -> new Reserva(new CodigoSuministro(r.codigo()), r.token(), r.vence(), r.fallos())));
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
