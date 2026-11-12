package pe.nivora.backend.recibos.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import pe.nivora.backend.compartido.dominio.ReintentoExponencial;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos.Estado;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos.Reserva;

public class SincronizarRecibos {
    private final RepositorioSincronizacionRecibos programacion;
    private final IncorporarRecibos incorporar;
    private final Politica politica;
    private final Clock reloj;

    public SincronizarRecibos(RepositorioSincronizacionRecibos programacion,
            IncorporarRecibos incorporar, Politica politica, Clock reloj) {
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
        var estado = resultado.estado();
        var siguiente = estado == Estado.ACTUALIZADO
            ? new ReintentoExponencial(0, fin.plus(politica.intervaloCompleto()))
            : ReintentoExponencial.calcular(reserva.fallosConsecutivos(), fin,
                politica.reintentoInicial(), politica.reintentoMaximo());
        try {
            boolean confirmada = programacion.finalizar(reserva, estado, siguiente.fallos(),
                siguiente.proximoIntento(), fin);
            return new Intento(reserva, estado, resultado.incorporacion(), confirmada,
                confirmada ? siguiente.proximoIntento() : null, resultado.error());
        } catch (RepositorioSincronizacionRecibos.NoDisponible error) {
            return new Intento(reserva, Estado.ERROR_ALMACENAMIENTO, resultado.incorporacion(), false, null, error);
        }
    }

    private Resultado incorporar(Reserva reserva) {
        try {
            var datos = incorporar.ejecutar(reserva.suministro(), politica.limiteRecibos());
            return datos.isEmpty() ? new Resultado(Estado.SUMINISTRO_NO_ENCONTRADO, null, null)
                : new Resultado(Estado.ACTUALIZADO, datos.orElseThrow(), null);
        } catch (FuenteRecibosNoDisponibleException error) {
            return new Resultado(Estado.ERROR_FUENTE, null, error);
        } catch (RepositorioRecibos.Conflicto error) {
            return new Resultado(Estado.CONFLICTO, null, error);
        } catch (RepositorioRecibos.NoDisponible | RepositorioRecibos.SuministroNoRegistrado error) {
            return new Resultado(Estado.ERROR_ALMACENAMIENTO, null, error);
        } catch (RuntimeException error) {
            return new Resultado(Estado.ERROR_INTERNO, null, error);
        }
    }

    public record Politica(int maximoIntentos, int limiteRecibos, Duration reserva,
            Duration intervaloCompleto, Duration reintentoInicial, Duration reintentoMaximo) {
        public Politica {
            if (maximoIntentos < 1 || limiteRecibos < 1 || limiteRecibos > 60) {
                throw new IllegalArgumentException("Los limites de sincronizacion de recibos no son validos.");
            }
            for (var tiempo : List.of(reserva, intervaloCompleto, reintentoInicial, reintentoMaximo)) {
                if (tiempo.getSeconds() < 1 || tiempo.getNano() != 0) {
                    throw new IllegalArgumentException("Los tiempos de sincronizacion deben ser segundos enteros positivos.");
                }
            }
            if (reintentoInicial.compareTo(reintentoMaximo) > 0) {
                throw new IllegalArgumentException("El reintento inicial supera el maximo.");
            }
        }
    }

    public record Intento(Reserva reserva, Estado estado, RepositorioRecibos.Incorporacion incorporacion,
            boolean programacionConfirmada, Instant proximoIntento, RuntimeException error) {}

    private record Resultado(Estado estado, RepositorioRecibos.Incorporacion incorporacion, RuntimeException error) {}
}
