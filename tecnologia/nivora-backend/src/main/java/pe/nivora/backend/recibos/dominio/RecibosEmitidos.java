package pe.nivora.backend.recibos.dominio;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public record RecibosEmitidos(CodigoSuministro suministro, List<ReciboEmitido> recibos) {
    public RecibosEmitidos {
        Objects.requireNonNull(suministro);
        recibos = List.copyOf(recibos);
        var numeros = new HashSet<String>();
        YearMonth anterior = null;
        for (var recibo : recibos) {
            if (!numeros.add(recibo.numeroRecibo())
                    || (anterior != null && !anterior.isAfter(recibo.periodo()))) {
                throw new IllegalArgumentException("Los recibos deben venir ordenados por mes y sin duplicados.");
            }
            anterior = recibo.periodo();
        }
    }
}
