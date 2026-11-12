package pe.nivora.backend.tarifas.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual.Periodo;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.tarifas.dominio.CostoEstimado;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

public class ConsultarCostosConsumo {
    private final ConsultarEstadisticasConsumo consumos;
    private final RepositorioTarifas tarifas;

    public ConsultarCostosConsumo(ConsultarEstadisticasConsumo consumos, RepositorioTarifas tarifas) {
        this.consumos = consumos;
        this.tarifas = tarifas;
    }

    public Resultado ejecutar(CodigoSuministro suministro, int meses) {
        var mediciones = consumos.ejecutar(suministro, meses);
        var guardado = tarifas.buscar(mediciones.fuente(), suministro);
        var historial = guardado.map(RepositorioTarifas.Guardado::historial);
        var actual = estimar(mediciones.comparacion().actual(), historial);
        var anteriores = mediciones.serieMensual().reversed().stream()
            .map(periodo -> estimar(periodo, historial)).toList();
        return new Resultado(mediciones.fuente(), mediciones.codigoSuministro(), mediciones.zonaHoraria(),
            mediciones.consultadoEn(), guardado.map(RepositorioTarifas.Guardado::revision).orElse(null),
            actual, anteriores);
    }

    private static Estimacion estimar(Periodo periodo, Optional<HistorialTarifario> historial) {
        Long litros = periodo.consumo().litros();
        if (litros == null) {
            return new Estimacion(periodo, Estado.SIN_CONSUMO_CALCULABLE, null, null);
        }

        var inicio = periodo.inicioMes().atZone(MesConsumo.ZONA).toLocalDate();
        var fin = periodo.finMesExclusivo().atZone(MesConsumo.ZONA).toLocalDate();
        var asignacion = historial.flatMap(h -> h.aplicable(inicio, fin)).orElse(null);
        if (asignacion == null) {
            return new Estimacion(periodo, Estado.SIN_TARIFA_COMPATIBLE, null, null);
        }

        var costo = CostoEstimado.calcular(litros, asignacion.tarifa());
        var estado = costo.totalEstimado() == null ? Estado.CONCEPTOS_INCOMPLETOS : Estado.ESTIMADO;
        return new Estimacion(periodo, estado, asignacion, costo);
    }

    public enum Estado { SIN_CONSUMO_CALCULABLE, SIN_TARIFA_COMPATIBLE, CONCEPTOS_INCOMPLETOS, ESTIMADO }

    public record Estimacion(Periodo periodo, Estado estado, HistorialTarifario.Asignacion tarifa,
            CostoEstimado costo) {}

    public record Resultado(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, Long revisionTarifaria, Estimacion actual, List<Estimacion> anteriores) {}
}
