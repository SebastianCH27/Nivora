package pe.nivora.backend.notificaciones.aplicacion;

import java.time.Clock;
import java.time.YearMonth;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.notificaciones.dominio.AlertaConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAlertasConsumo;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

public class EvaluarAlertaConsumo {
    private final ConsultarEstadisticasConsumo estadisticas;
    private final RepositorioAlertasConsumo repositorio;
    private final ConfigurarNotificaciones configuracion;
    private final RepositorioPeriodosMensuales periodos;
    private final Clock reloj;

    public EvaluarAlertaConsumo(ConsultarEstadisticasConsumo estadisticas, RepositorioAlertasConsumo repositorio,
            ConfigurarNotificaciones configuracion, RepositorioPeriodosMensuales periodos, Clock reloj) {
        this.estadisticas = estadisticas;
        this.repositorio = repositorio;
        this.configuracion = configuracion;
        this.periodos = periodos;
        this.reloj = reloj;
    }

    public Resultado ejecutar(CodigoSuministro suministro) {
        if (!configuracion.evaluarAlertas()) {
            return new Resultado(periodos.consultar().actual(reloj.instant()).valor(), Estado.PAUSADA);
        }
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

    public enum Estado { PAUSADA, SIN_DATOS_COMPARABLES, NO_SUPERA, GENERADA, YA_REGISTRADA }

    public record Resultado(YearMonth periodo, Estado estado) {}
}
