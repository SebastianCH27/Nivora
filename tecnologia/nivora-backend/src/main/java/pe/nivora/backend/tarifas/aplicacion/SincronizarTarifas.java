package pe.nivora.backend.tarifas.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import pe.nivora.backend.compartido.dominio.ReintentoExponencial;
import pe.nivora.backend.tarifas.dominio.FuenteTarifasNoDisponibleException;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas.Estado;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas.Reserva;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

/** Importacion inicial y refresco con calendario persistido; HTTP fuera de las reservas SQL. */
public final class SincronizarTarifas {
    private final RepositorioSincronizacionTarifas programacion;
    private final IncorporarTarifas incorporar;
    private final Politica politica;
    private final Clock reloj;

    public SincronizarTarifas(RepositorioSincronizacionTarifas programacion, IncorporarTarifas incorporar,
            Politica politica, Clock reloj) {
        this.programacion = programacion;
        this.incorporar = incorporar;
        this.politica = politica;
        this.reloj = reloj;
    }

    public List<Intento> ejecutarCiclo() {
        Instant inicio = reloj.instant();
        programacion.prepararSuministros(inicio);
        var intentos = new ArrayList<Intento>();
        for (int n = 0; n < politica.maximoIntentos() && !Thread.currentThread().isInterrupted(); n++) {
            var reserva = programacion.reservar(inicio, reloj.instant(), politica.reserva());
            if (reserva.isEmpty()) break;
            intentos.add(ejecutar(reserva.orElseThrow()));
        }
        return List.copyOf(intentos);
    }

    private Intento ejecutar(Reserva reserva) {
        var resultado = incorporar(reserva);
        Instant fin = reloj.instant();
        var siguiente = resultado.estado() == Estado.ACTUALIZADO
            ? new ReintentoExponencial(0, fin.plus(politica.intervaloCompleto()))
            : ReintentoExponencial.calcular(reserva.fallosConsecutivos(), fin,
                politica.reintentoInicial(), politica.reintentoMaximo());
        try {
            boolean confirmada = programacion.finalizar(reserva, resultado.estado(), siguiente.fallos(),
                siguiente.proximoIntento(), fin);
            return new Intento(reserva, resultado.estado(), resultado.guardado(), confirmada,
                confirmada ? siguiente.proximoIntento() : null, resultado.error());
        } catch (RepositorioSincronizacionTarifas.NoDisponible error) {
            return new Intento(reserva, Estado.ERROR_ALMACENAMIENTO, resultado.guardado(), false, null, error);
        }
    }

    private Resultado incorporar(Reserva reserva) {
        try {
            var datos = incorporar.ejecutar(reserva.suministro());
            return datos.isEmpty() ? new Resultado(Estado.SUMINISTRO_NO_ENCONTRADO, null, null)
                : new Resultado(Estado.ACTUALIZADO, datos.orElseThrow(), null);
        } catch (FuenteTarifasNoDisponibleException error) {
            return new Resultado(Estado.ERROR_FUENTE, null, error);
        } catch (RepositorioTarifas.Conflicto error) {
            return new Resultado(Estado.CONFLICTO, null, error);
        } catch (RepositorioTarifas.NoDisponible error) {
            return new Resultado(Estado.ERROR_ALMACENAMIENTO, null, error);
        } catch (RuntimeException error) {
            return new Resultado(Estado.ERROR_INTERNO, null, error);
        }
    }

    public record Politica(int maximoIntentos, Duration reserva, Duration intervaloCompleto,
            Duration reintentoInicial, Duration reintentoMaximo) {
        public Politica {
            if (maximoIntentos < 1 || maximoIntentos > 1000) {
                throw new IllegalArgumentException("Se admiten de 1 a 1000 intentos de tarifas por ciclo.");
            }
            for (var tiempo : List.of(reserva, intervaloCompleto, reintentoInicial, reintentoMaximo)) {
                if (tiempo.getSeconds() < 1 || tiempo.getSeconds() > 86400 || tiempo.getNano() != 0) {
                    throw new IllegalArgumentException("Los tiempos deben ser segundos enteros de 1 a 86400.");
                }
            }
            if (reintentoInicial.compareTo(reintentoMaximo) > 0) {
                throw new IllegalArgumentException("El reintento inicial supera el maximo.");
            }
        }
    }

    public record Intento(Reserva reserva, Estado estado, RepositorioTarifas.Guardado guardado,
            boolean programacionConfirmada, Instant proximoIntento, RuntimeException error) {}

    private record Resultado(Estado estado, RepositorioTarifas.Guardado guardado, RuntimeException error) {}
}
