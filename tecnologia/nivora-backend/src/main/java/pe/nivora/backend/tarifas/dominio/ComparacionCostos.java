package pe.nivora.backend.tarifas.dominio;

import java.math.BigDecimal;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;

/** Diferencias comparado menos base; una ausencia no equivale a un importe cero. */
public record ComparacionCostos(Estado estado, Long diferenciaLitros, BigDecimal diferenciaCostoEstimado) {
    public enum Estado { COMPLETA, PARCIAL, NO_CALCULABLE }

    public static ComparacionCostos calcular(ConsumoPeriodo base, BigDecimal costoBase,
            boolean periodoBaseCompleto, ConsumoPeriodo comparado, BigDecimal costoComparado,
            boolean periodoComparadoCompleto) {
        Long litros = base.litros() == null || comparado.litros() == null
            ? null : comparado.litros() - base.litros();
        if (litros == null || costoBase == null || costoComparado == null) {
            return new ComparacionCostos(Estado.NO_CALCULABLE, litros, null);
        }
        boolean completos = periodoBaseCompleto && periodoComparadoCompleto
            && base.estado() == ConsumoPeriodo.Estado.COMPLETO
            && comparado.estado() == ConsumoPeriodo.Estado.COMPLETO;
        return new ComparacionCostos(completos ? Estado.COMPLETA : Estado.PARCIAL,
            litros, costoComparado.subtract(costoBase));
    }

    public BigDecimal diferenciaMetrosCubicos() {
        return diferenciaLitros == null ? null : BigDecimal.valueOf(diferenciaLitros, 3);
    }
}
