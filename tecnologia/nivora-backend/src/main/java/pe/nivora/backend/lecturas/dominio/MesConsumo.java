package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

/** Calendario de los periodos mensuales de consumo. */
public record MesConsumo(YearMonth valor) {
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    public static MesConsumo de(Instant instante) {
        return new MesConsumo(YearMonth.from(instante.atZone(ZONA)));
    }

    public Instant inicio() {
        return inicioDe(valor);
    }

    public Instant fin() {
        return inicioDe(valor.plusMonths(1));
    }

    private static Instant inicioDe(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(ZONA).toInstant();
    }
}
