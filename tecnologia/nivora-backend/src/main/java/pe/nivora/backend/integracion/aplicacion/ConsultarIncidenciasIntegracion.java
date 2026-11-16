package pe.nivora.backend.integracion.aplicacion;

import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.dominio.ConsultaIncidenciasIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Cursor;

public class ConsultarIncidenciasIntegracion {
    private final ConsultaIncidenciasIntegracion consulta;

    public ConsultarIncidenciasIntegracion(ConsultaIncidenciasIntegracion consulta) {
        this.consulta = consulta;
    }

    public Optional<Pagina> ejecutar(CodigoSuministro suministro, Cursor antesDe, int limite) {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("Limite invalido.");
        return consulta.consultar(suministro, antesDe, limite + 1).map(incidencias -> {
            boolean hayMas = incidencias.size() > limite;
            var visibles = hayMas ? List.copyOf(incidencias.subList(0, limite)) : incidencias;
            return new Pagina(visibles, hayMas ? visibles.getLast().cursor() : null);
        });
    }

    public record Pagina(List<IncidenciaIntegracion> incidencias, Cursor siguienteAntesDe) {}
}
