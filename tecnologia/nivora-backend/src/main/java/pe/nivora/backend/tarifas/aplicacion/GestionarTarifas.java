package pe.nivora.backend.tarifas.aplicacion;

import java.time.Clock;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario.Asignacion;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

public class GestionarTarifas {
    private final RepositorioTarifas tarifas;
    private final Clock reloj;

    public GestionarTarifas(RepositorioTarifas tarifas, Clock reloj) {
        this.tarifas = tarifas;
        this.reloj = reloj;
    }

    public RepositorioTarifas.VistaAdministrativa consultar(CodigoSuministro suministro) {
        return tarifas.consultarGestion(suministro);
    }

    public RepositorioTarifas.Publicacion consultar(CodigoSuministro suministro, long revision) {
        return tarifas.consultarPublicacion(suministro, revision);
    }

    public RepositorioTarifas.Publicacion publicar(CodigoSuministro suministro, long revisionEsperada,
            Asignacion asignacion, UsuarioAdministrador actor) {
        return tarifas.publicar(suministro, revisionEsperada, asignacion, actor.valor(), reloj.instant());
    }
}
