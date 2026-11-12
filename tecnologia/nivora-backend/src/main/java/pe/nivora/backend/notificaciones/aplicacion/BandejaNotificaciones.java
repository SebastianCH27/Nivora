package pe.nivora.backend.notificaciones.aplicacion;

import java.time.Clock;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioBandejaNotificaciones;

public class BandejaNotificaciones {
    private final RepositorioBandejaNotificaciones repositorio;
    private final Clock reloj;

    public BandejaNotificaciones(RepositorioBandejaNotificaciones repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public Pagina consultar(CodigoSuministro suministro, Notificacion.Cursor antesDe, boolean soloPendientes, int limite) {
        if (limite < 1 || limite > 60) {
            throw new IllegalArgumentException("El limite de notificaciones debe estar entre 1 y 60.");
        }
        var encontrados = repositorio.buscar(suministro, antesDe, soloPendientes, limite + 1);
        if (encontrados.size() <= limite) {
            return new Pagina(List.copyOf(encontrados), null);
        }
        var pagina = List.copyOf(encontrados.subList(0, limite));
        var ultimo = pagina.getLast();
        return new Pagina(pagina, new Notificacion.Cursor(ultimo.registradaEn(), ultimo.clave()));
    }

    public boolean marcarLeida(CodigoSuministro suministro, Notificacion.Clave clave) {
        return repositorio.marcarLeida(suministro, clave, reloj.instant());
    }

    public record Pagina(List<Notificacion> notificaciones, Notificacion.Cursor siguienteAntesDe) {}
}
