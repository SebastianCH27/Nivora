package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.ReservaSincronizacion;
import pe.nivora.backend.lecturas.dominio.contratos.*;

/** Reserva y programa intentos; la recuperacion conserva sus transacciones por pagina. */
public final class SincronizarLecturas {
    private final String fuente;
    private final RepositorioSincronizacion programacion;
    private final Function<CodigoSuministro, RecuperarLecturas.Resultado> recuperar;
    private final PoliticaSincronizacion politica;
    private final Clock reloj;

    public SincronizarLecturas(String fuente, RepositorioSincronizacion programacion,
            Function<CodigoSuministro, RecuperarLecturas.Resultado> recuperar,
            PoliticaSincronizacion politica, Clock reloj) {
        this.fuente = Objects.requireNonNull(fuente);
        this.programacion = Objects.requireNonNull(programacion);
        this.recuperar = Objects.requireNonNull(recuperar);
        this.politica = Objects.requireNonNull(politica);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public List<Intento> ejecutarCiclo() {
        Instant inicio = reloj.instant();
        programacion.prepararSuministros(fuente, inicio);
        var intentos = new ArrayList<Intento>();
        for (int n = 0; n < politica.maximoIntentos() && !Thread.currentThread().isInterrupted(); n++) {
            var candidata = programacion.reservar(fuente, inicio, reloj.instant(), politica.reserva());
            if (candidata.isEmpty()) break;
            var reserva = candidata.orElseThrow();
            EstadoSincronizacion estado;
            RecuperarLecturas.Resultado resultado = null;
            RuntimeException error = null;
            try {
                resultado = Objects.requireNonNull(recuperar.apply(reserva.suministro()));
                if (!fuente.equals(resultado.progreso().fuente())
                        || !reserva.suministro().equals(resultado.progreso().suministro())) {
                    throw new IllegalStateException("La recuperacion no corresponde a la reserva.");
                }
                estado = switch (resultado.progreso().estado()) {
                    case RECORRIDO_COMPLETO -> EstadoSincronizacion.RECORRIDO_COMPLETO;
                    case EN_CURSO, PENDIENTE -> EstadoSincronizacion.EN_CURSO;
                    case ERROR_FUENTE -> EstadoSincronizacion.ERROR_FUENTE;
                    case SUMINISTRO_NO_ENCONTRADO -> EstadoSincronizacion.SUMINISTRO_NO_ENCONTRADO;
                    case BLOQUEADO -> EstadoSincronizacion.BLOQUEADO;
                };
            } catch (FuenteLecturasNoDisponibleException fallo) {
                estado = EstadoSincronizacion.ERROR_FUENTE; error = fallo;
            } catch (ProgresoRecuperacionModificadoException fallo) {
                estado = EstadoSincronizacion.CONFLICTO; error = fallo;
            } catch (RepositorioLecturasNoDisponibleException fallo) {
                estado = EstadoSincronizacion.ERROR_ALMACENAMIENTO; error = fallo;
            } catch (RuntimeException fallo) {
                estado = EstadoSincronizacion.ERROR_INTERNO; error = fallo; resultado = null;
            }
            Instant fin = reloj.instant();
            var siguiente = politica.siguiente(estado, reserva.fallosConsecutivos(), fin);
            boolean actualizada = false;
            try {
                actualizada = programacion.finalizar(reserva, estado,
                    siguiente.fallos(), siguiente.proximoIntento(), fin);
            } catch (RepositorioSincronizacionNoDisponibleException fallo) {
                estado = EstadoSincronizacion.ERROR_ALMACENAMIENTO; error = fallo;
            }
            intentos.add(new Intento(reserva, estado, resultado, actualizada,
                actualizada ? siguiente.proximoIntento() : null, error));
        }
        return List.copyOf(intentos);
    }

    public record Intento(ReservaSincronizacion reserva, EstadoSincronizacion estado,
        RecuperarLecturas.Resultado recuperacion, boolean programacionConfirmada,
        Instant proximoIntento, RuntimeException error) {}
}
