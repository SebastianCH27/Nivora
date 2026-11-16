package pe.nivora.backend.periodos.dominio;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import pe.nivora.backend.lecturas.dominio.MesConsumo;

public final class CalendarioMensual {
    public static final int DIA_PREDETERMINADO = 1;

    private final NavigableMap<YearMonth, Publicacion> porVigencia;
    private final long revision;

    public CalendarioMensual(List<Publicacion> publicaciones) {
        porVigencia = new TreeMap<>();
        long ultimaRevision = 0;
        for (var publicacion : publicaciones) {
            porVigencia.merge(publicacion.cambio().desde(), publicacion,
                (anterior, nueva) -> anterior.revision() > nueva.revision() ? anterior : nueva);
            ultimaRevision = Math.max(ultimaRevision, publicacion.revision());
        }
        revision = ultimaRevision;
    }

    public long revision() {
        return revision;
    }

    public List<Publicacion> reglas() {
        return List.copyOf(porVigencia.values());
    }

    public MesConsumo actual(Instant momento) {
        var civil = YearMonth.from(momento.atZone(MesConsumo.ZONA));
        return obtener(momento.isBefore(inicio(civil)) ? civil.minusMonths(1) : civil);
    }

    public MesConsumo obtener(YearMonth periodo) {
        return new MesConsumo(periodo, inicio(periodo), inicio(periodo.plusMonths(1)));
    }

    private Instant inicio(YearMonth periodo) {
        var regla = porVigencia.floorEntry(periodo);
        int dia = regla == null ? DIA_PREDETERMINADO : regla.getValue().cambio().diaInicio();
        return periodo.atDay(Math.min(dia, periodo.lengthOfMonth())).atStartOfDay(MesConsumo.ZONA).toInstant();
    }

    public record Cambio(YearMonth desde, int diaInicio) {
        public Cambio {
            if (desde == null || desde.getYear() < 1000 || desde.getYear() > 9999
                    || diaInicio < 1 || diaInicio > 31) {
                throw new IllegalArgumentException("Vigencia o dia de inicio invalido.");
            }
        }
    }

    public record Publicacion(long revision, Cambio cambio, Instant publicadaEn, String publicadaPor) {}
}
