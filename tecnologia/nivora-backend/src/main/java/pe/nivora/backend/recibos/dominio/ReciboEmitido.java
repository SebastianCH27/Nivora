package pe.nivora.backend.recibos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public record ReciboEmitido(String numeroRecibo, YearMonth periodo, BigDecimal consumoLitros,
        BigDecimal cargoFijo, BigDecimal importeConsumo, BigDecimal importeTotal,
        LocalDate fechaDisponibilidad, LocalDate fechaVencimiento, String codigoTarifa, int versionTarifa) {

    public ReciboEmitido {
        validarTexto(numeroRecibo, 40);
        validarTexto(codigoTarifa, 30);
        if (versionTarifa < 1) {
            throw new IllegalArgumentException("La version tarifaria debe ser positiva.");
        }
        if (periodo == null || periodo.getYear() < 1000 || periodo.getYear() > 9999
                || fechaDisponibilidad == null || fechaVencimiento == null
                || !fechaDisponibilidad.isAfter(periodo.atEndOfMonth())
                || fechaVencimiento.isBefore(fechaDisponibilidad) || fechaVencimiento.getYear() > 9999) {
            throw new IllegalArgumentException("El periodo y las fechas del recibo no son validos.");
        }
        consumoLitros = decimal(consumoLitros, 15, 3);
        cargoFijo = decimal(cargoFijo, 12, 2);
        importeConsumo = decimal(importeConsumo, 12, 2);
        importeTotal = decimal(importeTotal, 12, 2);
        if (cargoFijo.add(importeConsumo).compareTo(importeTotal) != 0) {
            throw new IllegalArgumentException("El importe total del recibo no coincide con sus cargos.");
        }
    }

    private static BigDecimal decimal(BigDecimal valor, int precision, int escala) {
        if (valor == null || valor.signum() < 0
                || (long) valor.precision() - valor.scale() > precision - escala
                || valor.stripTrailingZeros().scale() > escala) {
            throw new IllegalArgumentException("El valor excede la precision del recibo.");
        }
        return valor.setScale(escala, RoundingMode.UNNECESSARY);
    }

    private static void validarTexto(String valor, int longitud) {
        if (valor == null || valor.isBlank() || !valor.equals(valor.strip())
                || valor.length() > longitud || valor.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("La referencia del recibo no es valida.");
        }
    }
}
