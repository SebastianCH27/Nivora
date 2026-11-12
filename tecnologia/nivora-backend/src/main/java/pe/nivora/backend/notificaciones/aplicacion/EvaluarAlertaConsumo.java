package pe.nivora.backend.notificaciones.aplicacion;

import java.time.YearMonth;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.notificaciones.dominio.AlertaConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAlertasConsumo;

public class EvaluarAlertaConsumo {
    private final ConsultarEstadisticasConsumo estadisticas;
    private final RepositorioAlertasConsumo repositorio;

    public EvaluarAlertaConsumo(ConsultarEstadisticasConsumo estadisticas, RepositorioAlertasConsumo repositorio) {
        this.estadisticas = estadisticas;
        this.repositorio = repositorio;
    }

    public Resultado ejecutar(CodigoSuministro suministro) {
        var datos = estadisticas.ejecutar(suministro, 1);
        var actual = datos.comparacion().actual();
        var anterior = datos.comparacion().anterior();
        var periodo = YearMonth.parse(actual.periodo());
        var consumoActual = actual.consumo();
        var consumoAnterior = anterior.consumo();
        if (consumoActual.estado() != ConsumoPeriodo.Estado.COMPLETO
                || consumoAnterior.estado() != ConsumoPeriodo.Estado.COMPLETO) {
            return new Resultado(periodo, Estado.SIN_DATOS_COMPARABLES);
        }
        if (consumoActual.litros() <= consumoAnterior.litros()) {
            return new Resultado(periodo, Estado.NO_SUPERA);
        }

        var alerta = new AlertaConsumo(suministro, periodo, consumoActual.litros(), consumoAnterior.litros(),
            consumoActual.hasta(), datos.consultadoEn());
        return new Resultado(periodo, repositorio.registrar(alerta) ? Estado.GENERADA : Estado.YA_REGISTRADA);
    }

    public enum Estado { SIN_DATOS_COMPARABLES, NO_SUPERA, GENERADA, YA_REGISTRADA }

    public record Resultado(YearMonth periodo, Estado estado) {}
}
