package pe.nivora.empresaagua.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CalculadoraRecibo {

    public Resultado calcular(
            BigDecimal litros,
            BigDecimal cargoFijo,
            List<Tramo> tramos) {

        if (litros == null
                || cargoFijo == null
                || tramos == null
                || litros.signum() < 0
                || cargoFijo.signum() < 0
                || cargoFijo.stripTrailingZeros().scale() > 2
                || tramos.isEmpty()) {

            throw new IllegalArgumentException(
                "El consumo y la tarifa deben ser validos.");
        }

        for (Tramo tramo : tramos) {
            if (tramo == null
                    || tramo.desde() == null
                    || tramo.precio() == null
                    || tramo.desde().signum() < 0
                    || tramo.precio().signum() < 0
                    || (tramo.hasta() != null
                        && tramo.hasta().compareTo(
                            tramo.desde()) <= 0)) {

                throw new IllegalArgumentException(
                    "Hay un tramo tarifario invalido.");
            }
        }

        List<Tramo> ordenados = new ArrayList<>(tramos);
        ordenados.sort(Comparator.comparing(Tramo::desde));

        BigDecimal esperado = BigDecimal.ZERO;

        for (int i = 0; i < ordenados.size(); i++) {
            Tramo tramo = ordenados.get(i);

            if (tramo.desde().compareTo(esperado) != 0
                    || (i < ordenados.size() - 1
                        && tramo.hasta() == null)
                    || (i == ordenados.size() - 1
                        && tramo.hasta() != null)) {

                throw new IllegalArgumentException(
                    "Los tramos deben cubrir todo el consumo "
                        + "sin huecos ni superposiciones.");
            }

            esperado = tramo.hasta();
        }

        BigDecimal metrosCubicos = litros.movePointLeft(3);
        BigDecimal importe = BigDecimal.ZERO;

        for (Tramo tramo : ordenados) {
            if (metrosCubicos.compareTo(tramo.desde()) <= 0) {
                break;
            }

            BigDecimal limite = tramo.hasta() == null
                ? metrosCubicos
                : metrosCubicos.min(tramo.hasta());

            BigDecimal cantidad =
                limite.subtract(tramo.desde());

            importe = importe.add(
                cantidad.multiply(tramo.precio()));
        }

        BigDecimal fijo =
            cargoFijo.setScale(2, RoundingMode.HALF_UP);

        BigDecimal consumo =
            importe.setScale(2, RoundingMode.HALF_UP);

        return new Resultado(
            fijo,
            consumo,
            fijo.add(consumo));
    }

    public record Tramo(
        BigDecimal desde,
        BigDecimal hasta,
        BigDecimal precio
    ) {}

    public record Resultado(
        BigDecimal cargoFijo,
        BigDecimal importeConsumo,
        BigDecimal importeTotal
    ) {}
}