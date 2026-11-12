package pe.nivora.backend.notificaciones.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.ControlAlertas;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Permiso;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Preferencia;

public class ConfigurarNotificaciones {
    private final RepositorioConfiguracionNotificaciones repositorio;
    private final Clock reloj;
    private final boolean habilitadaPorServidor;

    public ConfigurarNotificaciones(RepositorioConfiguracionNotificaciones repositorio, Clock reloj,
            boolean habilitadaPorServidor) {
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.habilitadaPorServidor = habilitadaPorServidor;
    }

    public Optional<Preferencia> consultarDispositivo(CodigoSuministro suministro, UUID dispositivo) {
        return repositorio.buscarPreferencia(suministro, dispositivo);
    }

    public Preferencia configurarDispositivo(CodigoSuministro suministro, UUID dispositivo,
            Permiso permiso, boolean habilitadas) {
        return repositorio.guardarPreferencia(suministro, new Preferencia(dispositivo, permiso, habilitadas, reloj.instant()));
    }

    public EstadoAlertas consultarAlertas() {
        return estadoAlertas(repositorio.buscarControlAlertas().orElse(new ControlAlertas(true, null, null)));
    }

    public EstadoAlertas configurarAlertas(boolean habilitada, UsuarioAdministrador actor) {
        return estadoAlertas(repositorio.guardarControlAlertas(habilitada, actor, reloj.instant()));
    }

    public boolean evaluarAlertas() {
        return habilitadaPorServidor && consultarAlertas().habilitada();
    }

    private EstadoAlertas estadoAlertas(ControlAlertas control) {
        return new EstadoAlertas(control.habilitada(), habilitadaPorServidor && control.habilitada(),
            control.actualizadaPor(), control.actualizadaEn());
    }

    public record EstadoAlertas(boolean habilitada, boolean efectiva, String actualizadaPor, Instant actualizadaEn) {}
}
