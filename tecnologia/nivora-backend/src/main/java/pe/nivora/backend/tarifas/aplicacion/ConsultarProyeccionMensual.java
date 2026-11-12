package pe.nivora.backend.tarifas.aplicacion;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarCostosConsumo.Estado;
import pe.nivora.backend.tarifas.dominio.CostoEstimado;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.ProyeccionMensual;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

public class ConsultarProyeccionMensual {
    private final ConsultarConsumoActual consumoActual;
    private final RepositorioTarifas tarifas;

    public ConsultarProyeccionMensual(ConsultarConsumoActual consumoActual, RepositorioTarifas tarifas) {
        this.consumoActual = consumoActual;
        this.tarifas = tarifas;
    }

    public Resultado ejecutar(CodigoSuministro suministro) {
        var observado = consumoActual.ejecutar(suministro);
        var proyeccion = ProyeccionMensual.calcular(observado.inicioMes(), observado.finMesExclusivo(),
            observado.consumo());
        if (proyeccion.datos() == null) {
            return new Resultado(observado, proyeccion, null);
        }

        var guardado = tarifas.buscar(observado.fuente(), suministro);
        var revision = guardado.map(RepositorioTarifas.Guardado::revision).orElse(null);
        var inicio = observado.inicioMes().atZone(MesConsumo.ZONA).toLocalDate();
        var fin = observado.finMesExclusivo().atZone(MesConsumo.ZONA).toLocalDate();
        var asignacion = guardado.flatMap(g -> g.historial().aplicable(inicio, fin)).orElse(null);
        if (asignacion == null) {
            return new Resultado(observado, proyeccion,
                new Estimacion(Estado.SIN_TARIFA_COMPATIBLE, revision, null, null));
        }

        var costo = CostoEstimado.calcular(proyeccion.datos().litros(), asignacion.tarifa());
        var estado = costo.totalEstimado() == null ? Estado.CONCEPTOS_INCOMPLETOS : Estado.ESTIMADO;
        return new Resultado(observado, proyeccion, new Estimacion(estado, revision, asignacion, costo));
    }

    public record Resultado(ConsultarConsumoActual.Resultado observado, ProyeccionMensual proyeccion,
            Estimacion estimacion) {}

    public record Estimacion(Estado estado, Long revisionTarifaria, HistorialTarifario.Asignacion tarifa,
            CostoEstimado costo) {}
}
