package pe.nivora.backend.tarifas.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Importe referencial en PEN, calculado con las condiciones de una sola version. */
public record CostoEstimado(BigDecimal agua, BigDecimal alcantarillado, BigDecimal cargoFijo,
        BigDecimal subtotalConocido, BigDecimal igv, BigDecimal totalEstimado, List<TramoAplicado> tramos) {

    public static CostoEstimado calcular(long litros, VersionTarifaria tarifa) {
        if (litros < 0) {
            throw new IllegalArgumentException("El consumo en litros debe ser no negativo.");
        }

        var volumen = BigDecimal.valueOf(litros, 3);
        BigDecimal agua = BigDecimal.ZERO;
        BigDecimal alcantarillado = BigDecimal.ZERO;
        boolean alcantarilladoInformado = true;
        var tramos = new ArrayList<TramoAplicado>();
        for (var tramo : tarifa.tramos()) {
            if (volumen.compareTo(tramo.desdeM3()) <= 0) break;
            var limite = tramo.hastaM3() == null ? volumen : volumen.min(tramo.hastaM3());
            var aplicado = limite.subtract(tramo.desdeM3());
            var importeAgua = aplicado.multiply(tramo.precioAguaPorM3());
            var precioAlcantarillado = tramo.precioAlcantarilladoPorM3();
            var importeAlcantarillado = precioAlcantarillado == null ? null : aplicado.multiply(precioAlcantarillado);
            agua = agua.add(importeAgua);
            if (importeAlcantarillado == null) {
                alcantarilladoInformado = false;
            } else {
                alcantarillado = alcantarillado.add(importeAlcantarillado);
            }
            tramos.add(new TramoAplicado(tramo.desdeM3(), tramo.hastaM3(), aplicado,
                tramo.precioAguaPorM3(), precioAlcantarillado, importeAgua, importeAlcantarillado));
        }
        if (litros == 0) {
            alcantarilladoInformado = tarifa.tramos().getFirst().precioAlcantarilladoPorM3() != null;
        }

        agua = centimos(agua);
        alcantarillado = alcantarilladoInformado ? centimos(alcantarillado) : null;
        var subtotal = agua.add(tarifa.cargoFijo());
        if (alcantarillado != null) subtotal = subtotal.add(alcantarillado);

        BigDecimal igv = null;
        BigDecimal total = null;
        if (alcantarilladoInformado && tarifa.igvPorcentaje() != null) {
            igv = centimos(subtotal.multiply(tarifa.igvPorcentaje()).movePointLeft(2));
            total = subtotal.add(igv);
        }
        return new CostoEstimado(agua, alcantarillado, tarifa.cargoFijo(), subtotal, igv, total,
            List.copyOf(tramos));
    }

    private static BigDecimal centimos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public record TramoAplicado(BigDecimal desdeM3, BigDecimal hastaM3, BigDecimal volumenM3,
            BigDecimal precioAguaPorM3, BigDecimal precioAlcantarilladoPorM3,
            BigDecimal importeAgua, BigDecimal importeAlcantarillado) {}
}
