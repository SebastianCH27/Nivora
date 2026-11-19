package pe.nivora.backend.lecturas.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Consumo exacto según el tipo de medición, con cobertura explícita del periodo. */
public record ConsumoPeriodo(Estado estado, Motivo motivo, Long litros, Instant desde, Instant hasta,
        String idMedidor, String idLecturaInicial, String idLecturaFinal, Lectura.Tipo tipoMedicion) {

    public ConsumoPeriodo(Estado estado, Motivo motivo, Long litros, Instant desde, Instant hasta,
            String idMedidor, String idLecturaInicial, String idLecturaFinal) {
        this(estado, motivo, litros, desde, hasta, idMedidor, idLecturaInicial, idLecturaFinal,
            litros == null ? null : Lectura.Tipo.ACUMULADA);
    }

    public enum Estado { SIN_DATOS, PARCIAL, COMPLETO, INCOMPATIBLE }

    public enum Motivo {
        LECTURAS_INSUFICIENTES, FALTA_LECTURA_INICIAL, FALTA_LECTURA_FINAL,
        CAMBIO_DE_MEDIDOR, RETROCESO_DEL_CONTADOR, TIPOS_DE_MEDICION_MEZCLADOS,
        INTERVALOS_SUPERPUESTOS, HUECOS_ENTRE_INTERVALOS, INTERVALO_CRUZA_LIMITE,
        SUMA_FUERA_DE_RANGO
    }

    /**
     * Recibe las lecturas de una fuente y suministro, ordenadas por fecha y limitadas
     * al intervalo cerrado. El extremo final del mes actual es la ultima medicion.
     */
    public static ConsumoPeriodo calcular(Instant inicio, Instant fin, List<? extends Lectura> lecturas) {
        if (lecturas.stream().noneMatch(l -> l instanceof LecturaIntervalo)) {
            return acumuladas(inicio, fin, lecturas);
        }
        var delPeriodo = seleccionar(inicio, fin, lecturas);
        if (delPeriodo.isEmpty()) {
            return sinImporte(Estado.SIN_DATOS, Motivo.LECTURAS_INSUFICIENTES);
        }
        if (delPeriodo.stream().anyMatch(l -> l instanceof LecturaAcumulada)) {
            if (delPeriodo.stream().noneMatch(l -> l instanceof LecturaIntervalo)) {
                return acumuladas(inicio, fin, delPeriodo);
            }
            return sinImporte(Estado.INCOMPATIBLE, Motivo.TIPOS_DE_MEDICION_MEZCLADOS);
        }
        return intervalos(inicio, fin, delPeriodo.stream().map(LecturaIntervalo.class::cast)
            .sorted(Comparator.comparing(LecturaIntervalo::inicioIntervalo)
                .thenComparing(LecturaIntervalo::fechaMedicion)).toList());
    }

    /** Los acumulados incluyen ambos cortes; los intervalos usan intersección de tramos semiabiertos. */
    public static List<? extends Lectura> seleccionar(Instant inicio, Instant fin,
            List<? extends Lectura> lecturas) {
        if (fin == null || !inicio.isBefore(fin)) {
            return List.of();
        }
        return lecturas.stream().filter(l -> l instanceof LecturaIntervalo i
            ? i.inicioIntervalo().isBefore(fin) && i.fechaMedicion().isAfter(inicio)
            : !l.fechaMedicion().isBefore(inicio) && !l.fechaMedicion().isAfter(fin)).toList();
    }

    private static ConsumoPeriodo acumuladas(Instant inicio, Instant fin, List<? extends Lectura> lecturas) {
        if (lecturas.size() < 2) {
            return sinImporte(Estado.SIN_DATOS, Motivo.LECTURAS_INSUFICIENTES,
                lecturas.isEmpty() ? null : Lectura.Tipo.ACUMULADA);
        }
        var primera = lecturas.getFirst();
        var anterior = primera;
        for (int i = 1; i < lecturas.size(); i++) {
            var actual = lecturas.get(i);
            if (!actual.idMedidor().equals(primera.idMedidor())) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.CAMBIO_DE_MEDIDOR, Lectura.Tipo.ACUMULADA);
            }
            if (actual.valorLitros() < anterior.valorLitros()) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.RETROCESO_DEL_CONTADOR, Lectura.Tipo.ACUMULADA);
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

    private static ConsumoPeriodo intervalos(Instant inicio, Instant fin, List<LecturaIntervalo> lecturas) {
        var primera = lecturas.getFirst();
        LecturaIntervalo anterior = null;
        for (var actual : lecturas) {
            if (!actual.idMedidor().equals(primera.idMedidor())) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.CAMBIO_DE_MEDIDOR, Lectura.Tipo.INTERVALO);
            }
            if (anterior != null && actual.inicioIntervalo().isBefore(anterior.fechaMedicion())) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.INTERVALOS_SUPERPUESTOS, Lectura.Tipo.INTERVALO);
            }
            anterior = actual;
        }
        var completos = lecturas.stream().filter(l -> !l.inicioIntervalo().isBefore(inicio)
            && !l.fechaMedicion().isAfter(fin)).toList();
        if (completos.isEmpty()) {
            return sinImporte(Estado.SIN_DATOS, Motivo.INTERVALO_CRUZA_LIMITE, Lectura.Tipo.INTERVALO);
        }
        long litros = 0;
        boolean hueco = false;
        anterior = null;
        for (var lectura : completos) {
            try {
                litros = Math.addExact(litros, lectura.valorLitros());
            } catch (ArithmeticException error) {
                return sinImporte(Estado.INCOMPATIBLE, Motivo.SUMA_FUERA_DE_RANGO, Lectura.Tipo.INTERVALO);
            }
            hueco |= anterior != null && !anterior.fechaMedicion().equals(lectura.inicioIntervalo());
            anterior = lectura;
        }
        primera = completos.getFirst();
        var ultima = completos.getLast();
        Motivo motivo = hueco ? Motivo.HUECOS_ENTRE_INTERVALOS
            : completos.size() != lecturas.size() ? Motivo.INTERVALO_CRUZA_LIMITE
            : !primera.inicioIntervalo().equals(inicio) ? Motivo.FALTA_LECTURA_INICIAL
            : !ultima.fechaMedicion().equals(fin) ? Motivo.FALTA_LECTURA_FINAL : null;
        return new ConsumoPeriodo(motivo == null ? Estado.COMPLETO : Estado.PARCIAL, motivo, litros,
            primera.inicioIntervalo(), ultima.fechaMedicion(), primera.idMedidor(),
            primera.idLectura(), ultima.idLectura(), Lectura.Tipo.INTERVALO);
    }

    public BigDecimal metrosCubicos() {
        return litros == null ? null : BigDecimal.valueOf(litros, 3);
    }

    private static ConsumoPeriodo sinImporte(Estado estado, Motivo motivo) {
        return sinImporte(estado, motivo, null);
    }

    private static ConsumoPeriodo sinImporte(Estado estado, Motivo motivo, Lectura.Tipo tipo) {
        return new ConsumoPeriodo(estado, motivo, null, null, null, null, null, null, tipo);
    }
}
