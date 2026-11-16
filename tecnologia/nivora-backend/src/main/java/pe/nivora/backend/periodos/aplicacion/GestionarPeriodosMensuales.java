package pe.nivora.backend.periodos.aplicacion;

import java.time.Clock;

import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.periodos.dominio.CalendarioMensual;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Cambio;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Publicacion;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

public class GestionarPeriodosMensuales {
    private final RepositorioPeriodosMensuales repositorio;
    private final Clock reloj;

    public GestionarPeriodosMensuales(RepositorioPeriodosMensuales repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public Vista consultar() {
        var calendario = repositorio.consultar();
        return new Vista(calendario, calendario.actual(reloj.instant()));
    }

    public Publicacion publicar(long revisionEsperada, Cambio cambio, UsuarioAdministrador actor) {
        if (revisionEsperada < 0) throw new IllegalArgumentException("Revision invalida.");
        return repositorio.publicar(revisionEsperada, cambio, actor);
    }

    public record Vista(CalendarioMensual calendario, MesConsumo actual) {}
}
