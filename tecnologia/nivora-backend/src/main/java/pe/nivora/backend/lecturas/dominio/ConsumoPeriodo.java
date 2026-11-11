package pe.nivora.backend.lecturas.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Diferencia exacta de un contador acumulado dentro de un periodo. */
public record ConsumoPeriodo(Estado estado, Motivo motivo, Long litros, Instant desde, Instant hasta,
        String idMedidor, String idLecturaInicial, String idLecturaFinal) {

    public enum Estado { SIN_DATOS, PARCIAL, COMPLETO, INCOMPATIBLE }

    public enum Motivo {
        LECTURAS_INSUFICIENTES, FALTA_LECTURA_INICIAL, FALTA_LECTURA_FINAL,
        CAMBIO_DE_MEDIDOR, RETROCESO_DEL_CONTADOR
    }

    /**
     * Recibe las lecturas de una fuente y suministro, ordenadas por fecha y limitadas
     * al intervalo cerrado. El extremo final del mes actual es la ultima medicion.
     */
    public static ConsumoPeriodo calcular(Instant inicio, Instant fin, List<LecturaAcumulada> lecturas) {
        if (lecturas.size() < 2) {
            return sinImporte(Estado.SIN_DATOS, Motivo.LECTURAS_INSUFICIENTES);
        }
        var primera = lecturas.getFirst();
        var anterior = primera;
        for (int i = 1; i < lecturas.size(); i++) {
            var actual = lecturas.get(i);
            if (!actual.idMedidor().equals(primera.idMedidor())) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.CAMBIO_DE_MEDIDOR);
            }
            if (actual.valorLitros() < anterior.valorLitros()) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.RETROCESO_DEL_CONTADOR);
            }
            anterior = actual;
        }

        Motivo motivo = null;
        if (!primera.fechaMedicion().equals(inicio)) {
            motivo = Motivo.FALTA_LECTURA_INICIAL;
        } else if (!anterior.fechaMedicion().equals(fin)) {
            motivo = Motivo.FALTA_LECTURA_FINAL;
        }
        return new ConsumoPeriodo(motivo == null ? Estado.COMPLETO : Estado.PARCIAL, motivo,
            anterior.valorLitros() - primera.valorLitros(), primera.fechaMedicion(),
            anterior.fechaMedicion(), primera.idMedidor(), primera.idLectura(), anterior.idLectura());
    }

    public BigDecimal metrosCubicos() {
        return litros == null ? null : BigDecimal.valueOf(litros, 3);
    }

    private static ConsumoPeriodo sinImporte(Estado estado, Motivo motivo) {
        return new ConsumoPeriodo(estado, motivo, null, null, null, null, null, null);
    }
}
