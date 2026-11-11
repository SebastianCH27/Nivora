package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.dominio.contratos.*;

public final class GestionarAdministradores {
    private final RepositorioGestionAdministradores gestion;
    private final RepositorioSesionesAdministrativas sesiones;
    private final ProtectorClavesAdministrativas claves;
    private final Clock reloj;
    private final PoliticaSesionAdministrativa limites;

    public GestionarAdministradores(RepositorioGestionAdministradores gestion,
            RepositorioSesionesAdministrativas sesiones, ProtectorClavesAdministrativas claves,
            Clock reloj, PoliticaSesionAdministrativa limites) {
        this.gestion = gestion;
        this.sesiones = sesiones;
        this.claves = claves;
        this.reloj = reloj;
        this.limites = limites;
    }

    public PaginaAdministradores listar(ActorAdministrativo actor, String cursor, int limite) {
        validarLimite(limite);
        String desde = cursor == null || cursor.isEmpty() ? "" : new UsuarioAdministrador(cursor).valor();
        return gestion.listar(actor, desde, limite);
    }

    public Administrador consultar(ActorAdministrativo actor, String usuario) {
        return gestion.consultar(actor, new UsuarioAdministrador(usuario));
    }

    public PaginaAuditoriaAdministrativa auditoria(ActorAdministrativo actor, String usuario,
            String cursor, int limite) {
        validarLimite(limite);
        if (cursor == null || !cursor.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Cursor de auditoria invalido.");
        return gestion.auditoria(actor, new UsuarioAdministrador(usuario), Long.parseLong(cursor), limite);
    }

    public ResultadoGestionAdministrativa crear(ActorAdministrativo actor, char[] actual,
            String usuario, String nombre, char[] inicial, boolean gestionar, String origen) {
        var destinatario = new UsuarioAdministrador(usuario);
        String nombreValido = Administrador.normalizarNombre(nombre);
        var confirmacion = confirmar(actor, actual, origen);
        return gestion.crear(confirmacion, destinatario, nombreValido, claves.proteger(inicial), gestionar);
    }

    public ResultadoGestionAdministrativa estado(ActorAdministrativo actor, char[] actual,
            String usuario, boolean activa, String origen) {
        var destinatario = new UsuarioAdministrador(usuario);
        return gestion.cambiarEstado(confirmar(actor, actual, origen), destinatario, activa);
    }

    public ResultadoGestionAdministrativa permiso(ActorAdministrativo actor, char[] actual,
            String usuario, boolean gestionar, String origen) {
        var destinatario = new UsuarioAdministrador(usuario);
        return gestion.cambiarPermiso(confirmar(actor, actual, origen), destinatario, gestionar);
    }

    public ResultadoGestionAdministrativa restablecer(ActorAdministrativo actor, char[] actual,
            String usuario, char[] temporal, String origen) {
        var destinatario = new UsuarioAdministrador(usuario);
        var confirmacion = confirmar(actor, actual, origen);
        return gestion.restablecerClave(confirmacion, destinatario, claves.proteger(temporal));
    }

    public ResultadoGestionAdministrativa cambiarClave(ActorAdministrativo actor, char[] actual,
            char[] nueva, String origen) {
        var confirmacion = confirmar(actor, actual, origen);
        String hashNuevo = claves.proteger(nueva);
        if (claves.coincide(nueva, confirmacion.credencial().hashClave())) {
            throw new IllegalArgumentException("La nueva clave debe ser diferente de la actual.");
        }
        return gestion.cambiarClavePropia(confirmacion, hashNuevo);
    }

    private ConfirmacionAdministrativa confirmar(ActorAdministrativo actor, char[] actual, String origen) {
        sesiones.consumirConfirmacion(actor.usuario(), origen,
            reloj.instant().truncatedTo(ChronoUnit.MICROS), limites);
        var credencial = sesiones.buscarCredencial(actor.usuario())
            .orElseThrow(CredencialesAdministrativasInvalidasException::new);
        if (!claves.coincide(actual, credencial.hashClave()) || !credencial.cuenta().activa()) {
            throw new CredencialesAdministrativasInvalidasException();
        }
        return new ConfirmacionAdministrativa(actor, credencial);
    }

    private static void validarLimite(int limite) {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("El limite debe estar entre 1 y 100.");
    }
}
