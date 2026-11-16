package pe.nivora.backend.periodos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.nivora.backend.periodos.dominio.CalendarioMensual.Cambio;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Publicacion;

class CalendarioMensualTests {
    @Test
    void ajustaElDiaAlUltimoDeFebreroYConservaUnLimiteComun() {
        var enero = YearMonth.of(2028, 1);
        var febrero = enero.plusMonths(1);
        var publicada = new Publicacion(1, new Cambio(enero, 31),
            Instant.parse("2027-12-10T12:00:00Z"), "admin");
        var calendario = new CalendarioMensual(List.of(publicada));

        var corte = Instant.parse("2028-02-29T05:00:00Z");
        assertEquals(corte, calendario.obtener(enero).fin());
        assertEquals(corte, calendario.obtener(febrero).inicio());
        assertEquals(enero, calendario.actual(corte.minusNanos(1000)).valor());
        assertEquals(febrero, calendario.actual(corte).valor());
        assertEquals(Instant.parse("2028-03-31T05:00:00Z"), calendario.obtener(febrero).fin());
    }
}
