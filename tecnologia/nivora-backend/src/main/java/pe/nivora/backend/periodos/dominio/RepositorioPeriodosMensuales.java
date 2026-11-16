package pe.nivora.backend.periodos.dominio;

import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Cambio;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Publicacion;

public interface RepositorioPeriodosMensuales {
    CalendarioMensual consultar();

    Publicacion publicar(long revisionEsperada, Cambio cambio, UsuarioAdministrador actor);

    class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) {
            super(mensaje);
        }
    }

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo acceder al calendario mensual.", causa);
        }
    }
}
