package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public record AlertaConsumo(CodigoSuministro suministro, YearMonth periodo,
        long consumoActualLitros, long consumoAnteriorLitros, Instant medidoHasta, Instant detectadaEn) {
    public AlertaConsumo {
        Objects.requireNonNull(suministro);
        Objects.requireNonNull(periodo);
        Objects.requireNonNull(medidoHasta);
        Objects.requireNonNull(detectadaEn);
        if (consumoAnteriorLitros < 0 || consumoActualLitros <= consumoAnteriorLitros) {
            throw new IllegalArgumentException("El consumo actual debe superar al anterior.");
        }
        if (medidoHasta.isAfter(detectadaEn)) {
            throw new IllegalArgumentException("La lectura debe estar disponible al detectar la alerta.");
        }
    }
}
