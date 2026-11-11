package pe.nivora.backend.lecturas.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual.Periodo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo.Estado;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

public class ConsultarEstadisticasConsumo {
    private static final String FUENTE = "empresa-agua";

    private final ConsultaLecturasPeriodo consulta;
    private final Clock reloj;

    public ConsultarEstadisticasConsumo(ConsultaLecturasPeriodo consulta, Clock reloj) {
        this.consulta = consulta;
        this.reloj = reloj;
    }

    public Resultado ejecutar(CodigoSuministro suministro, int meses) {
        ConsultarHistorialMensual.validarMeses(meses);
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        var actual = MesConsumo.de(ahora);
        var primero = new MesConsumo(actual.valor().minusMonths(meses));

        var lecturas = consulta.consultar(FUENTE, suministro, primero.inicio(), ahora)
            .orElseThrow(SuministroNoRegistradoException::new)
            .lecturas();
        var historial = ConsultarHistorialMensual.calcularPeriodos(primero.valor(), meses, lecturas);

        Instant inicioActual = actual.inicio();
        int inicioLecturas = lecturas.size();
        while (inicioLecturas > 0
                && !lecturas.get(inicioLecturas - 1).fechaMedicion().isBefore(inicioActual)) {
            inicioLecturas--;
        }
        var lecturasActuales = lecturas.subList(inicioLecturas, lecturas.size());
        Instant corte = lecturasActuales.isEmpty() ? ahora : lecturasActuales.getLast().fechaMedicion();
        var consumo = ConsumoPeriodo.calcular(inicioActual, corte, lecturasActuales);
        var periodoActual = new Periodo(actual.valor().toString(), inicioActual, actual.fin(), consumo);
        var anterior = historial.getFirst();
        Long diferencia = consumo.litros() == null || anterior.consumo().litros() == null
            ? null : consumo.litros() - anterior.consumo().litros();

        return new Resultado(FUENTE, suministro.valor(), MesConsumo.ZONA.getId(), ahora,
            new Comparacion(periodoActual, anterior, diferencia),
            List.copyOf(historial.reversed()), resumir(historial));
    }

    private static Resumen resumir(List<Periodo> periodos) {
        var estados = new EnumMap<Estado, Integer>(Estado.class);
        for (var periodo : periodos) {
            estados.merge(periodo.consumo().estado(), 1, Integer::sum);
        }
        var completos = periodos.stream().map(Periodo::consumo)
            .filter(consumo -> consumo.estado() == Estado.COMPLETO)
            .map(ConsumoPeriodo::litros).toList();
        BigDecimal total = null;
        BigDecimal promedio = null;
        Long minimo = null;
        Long maximo = null;
        if (!completos.isEmpty()) {
            total = completos.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            promedio = total.divide(BigDecimal.valueOf(completos.size()), 3, RoundingMode.HALF_UP);
            minimo = Collections.min(completos);
            maximo = Collections.max(completos);
        }
        return new Resumen(completos.size(), estados.getOrDefault(Estado.PARCIAL, 0),
            estados.getOrDefault(Estado.SIN_DATOS, 0), estados.getOrDefault(Estado.INCOMPATIBLE, 0),
            total, promedio, minimo, maximo);
    }

    public record Comparacion(Periodo actual, Periodo anterior, Long diferenciaLitros) {}

    public record Resumen(int mesesCompletos, int mesesParciales, int mesesSinDatos,
            int mesesIncompatibles, BigDecimal totalLitros, BigDecimal promedioLitros,
            Long minimoLitros, Long maximoLitros) {}

    public record Resultado(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, Comparacion comparacion, List<Periodo> serieMensual, Resumen resumen) {}
}
