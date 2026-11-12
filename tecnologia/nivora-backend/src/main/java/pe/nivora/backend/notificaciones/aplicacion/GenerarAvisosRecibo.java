package pe.nivora.backend.notificaciones.aplicacion;

import java.time.Clock;
import java.time.LocalDate;

import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAvisosRecibo;

public class GenerarAvisosRecibo {
    private final RepositorioAvisosRecibo repositorio;
    private final Clock reloj;

    public GenerarAvisosRecibo(RepositorioAvisosRecibo repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public int ejecutar() {
        var ahora = reloj.instant();
        return repositorio.registrarDisponibles(LocalDate.ofInstant(ahora, MesConsumo.ZONA), ahora);
    }
}
