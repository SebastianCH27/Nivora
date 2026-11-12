package pe.nivora.backend.tarifas.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Condiciones economicas inmutables de una version en soles. */
public record VersionTarifaria(String codigo, int version, String nombre, LocalDate vigenteDesde,
        BigDecimal cargoFijo, BigDecimal igvPorcentaje, List<Tramo> tramos) {

    public VersionTarifaria {
        texto(codigo, 30);
        texto(nombre, 120);
        fecha(vigenteDesde);
        if (version < 1) {
            throw new IllegalArgumentException("El numero de version debe ser positivo.");
        }
        cargoFijo = decimal(cargoFijo, 12, 2);
        if (igvPorcentaje != null) {
            igvPorcentaje = decimal(igvPorcentaje, 7, 4);
            if (igvPorcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("La tasa de IGV debe ser de 0 a 100.");
            }
        }

        tramos = List.copyOf(tramos);
        BigDecimal esperado = BigDecimal.ZERO;
        for (Tramo tramo : tramos) {
            if (esperado == null || tramo.desdeM3().compareTo(esperado) != 0) {
                throw new IllegalArgumentException("Los tramos deben ser continuos desde cero.");
            }
            esperado = tramo.hastaM3();
        }
        if (esperado != null) {
            throw new IllegalArgumentException("La tarifa requiere un tramo final sin tope.");
        }
    }

    static void texto(String valor, int maximo) {
        if (valor == null || valor.isBlank() || !valor.equals(valor.strip()) || valor.length() > maximo) {
            throw new IllegalArgumentException("El texto de tarifa no es valido.");
        }
    }

    static void fecha(LocalDate fecha) {
        Objects.requireNonNull(fecha, "La fecha inicial es obligatoria.");
        if (fecha.getYear() < 1000 || fecha.getYear() > 9999) {
            throw new IllegalArgumentException("La fecha tarifaria esta fuera del rango admitido.");
        }
    }

    private static BigDecimal decimal(BigDecimal valor, int precision, int escala) {
        if (valor == null || valor.signum() < 0
                || (long) valor.precision() - valor.scale() > precision - escala
                || valor.stripTrailingZeros().scale() > escala) {
            throw new IllegalArgumentException("El valor tarifario excede la precision admitida.");
        }
        return valor.setScale(escala, RoundingMode.UNNECESSARY);
    }

    public record Tramo(BigDecimal desdeM3, BigDecimal hastaM3, BigDecimal precioAguaPorM3,
            BigDecimal precioAlcantarilladoPorM3) {
        public Tramo {
            desdeM3 = decimal(desdeM3, 15, 3);
            if (hastaM3 != null) {
                hastaM3 = decimal(hastaM3, 15, 3);
                if (hastaM3.compareTo(desdeM3) <= 0) {
                    throw new IllegalArgumentException("El tramo debe tener un rango positivo.");
                }
            }
            precioAguaPorM3 = decimal(precioAguaPorM3, 12, 4);
            if (precioAlcantarilladoPorM3 != null) {
                precioAlcantarilladoPorM3 = decimal(precioAlcantarilladoPorM3, 12, 4);
            }
        }
    }
}
