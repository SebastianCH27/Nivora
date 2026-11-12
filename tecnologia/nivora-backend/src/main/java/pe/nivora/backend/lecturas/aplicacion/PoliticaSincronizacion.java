package pe.nivora.backend.lecturas.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import pe.nivora.backend.compartido.dominio.ReintentoExponencial;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;

public record PoliticaSincronizacion(int maximoIntentos, int limitePagina,
        int maximoPaginas, Duration reserva, Duration intervaloCompleto,
        Duration continuacion, Duration reintentoInicial, Duration reintentoMaximo) {
    public PoliticaSincronizacion {
        if (maximoIntentos < 1 || maximoIntentos > 100 || limitePagina < 1 || limitePagina > 500
                || maximoPaginas < 1 || maximoPaginas > 10) {
            throw new IllegalArgumentException("Los limites de sincronizacion no son validos.");
        }
        for (Duration tiempo : new Duration[]{reserva, intervaloCompleto, continuacion,
                reintentoInicial, reintentoMaximo}) {
            Objects.requireNonNull(tiempo);
            if (tiempo.getSeconds() < 1 || tiempo.getSeconds() > 86400 || tiempo.getNano() != 0) {
                throw new IllegalArgumentException("Los tiempos deben ser de 1 a 86400 segundos enteros.");
            }
        }
        if (reintentoInicial.compareTo(reintentoMaximo) > 0) {
            throw new IllegalArgumentException("El reintento inicial supera el maximo.");
        }
    }

    public Siguiente siguiente(EstadoSincronizacion estado, int fallosPrevios, Instant ahora) {
        if (fallosPrevios < 0) throw new IllegalArgumentException("Fallos previos invalidos.");
        return switch (estado) {
            case RECORRIDO_COMPLETO -> new Siguiente(0, ahora.plus(intervaloCompleto));
            case EN_CURSO -> new Siguiente(0, ahora.plus(continuacion));
            case BLOQUEADO -> new Siguiente(0, null);
            case CONFLICTO -> new Siguiente(fallosPrevios, ahora.plus(continuacion));
            default -> {
                var reintento = ReintentoExponencial.calcular(fallosPrevios, ahora, reintentoInicial, reintentoMaximo);
                yield new Siguiente(reintento.fallos(), reintento.proximoIntento());
            }
        };
    }

    public record Siguiente(int fallos, Instant proximoIntento) {}
}
