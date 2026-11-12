package pe.nivora.backend.tarifas.dominio;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;

public record ProyeccionMensual(Estado estado, Datos datos) {
    public static final Duration MINIMO_OBSERVADO = Duration.ofDays(1);

    public static ProyeccionMensual calcular(Instant inicio, Instant fin, ConsumoPeriodo observado) {
        if (observado.litros() == null) {
            return new ProyeccionMensual(Estado.SIN_CONSUMO_CALCULABLE, null);
        }
        if (observado.estado() != ConsumoPeriodo.Estado.COMPLETO || !inicio.equals(observado.desde())) {
            return new ProyeccionMensual(Estado.COBERTURA_INCOMPLETA, null);
        }
        if (!observado.hasta().isBefore(fin)) {
            return new ProyeccionMensual(Estado.PERIODO_CERRADO, null);
        }

        var duracionObservada = Duration.between(inicio, observado.hasta());
        if (duracionObservada.compareTo(MINIMO_OBSERVADO) < 0) {
            return new ProyeccionMensual(Estado.TIEMPO_INSUFICIENTE, null);
        }

        var duracionMes = Duration.between(inicio, fin);
        var litros = BigDecimal.valueOf(observado.litros());
        var nanosObservados = BigDecimal.valueOf(duracionObservada.toNanos());
        var proyectados = litros.multiply(BigDecimal.valueOf(duracionMes.toNanos()))
            .divide(nanosObservados, 0, RoundingMode.HALF_UP).toBigIntegerExact();
        var promedio = litros.multiply(BigDecimal.valueOf(MINIMO_OBSERVADO.toNanos()))
            .divide(nanosObservados, 3, RoundingMode.HALF_UP);
        return new ProyeccionMensual(Estado.PROYECTADO,
            new Datos(proyectados, promedio, duracionObservada, duracionMes));
    }

    public enum Estado {
        PROYECTADO, SIN_CONSUMO_CALCULABLE, COBERTURA_INCOMPLETA, TIEMPO_INSUFICIENTE, PERIODO_CERRADO
    }

    public record Datos(BigInteger litros, BigDecimal promedioLitrosPorDia, Duration duracionObservada,
            Duration duracionMes) {
        public BigDecimal metrosCubicos() {
            return new BigDecimal(litros, 3);
        }
    }
}
