package pe.nivora.backend.notificaciones.dominio;

public interface RepositorioAlertasConsumo {
    /** Devuelve true solo al insertar la primera alerta del suministro y periodo. */
    boolean registrar(AlertaConsumo alerta);
}
