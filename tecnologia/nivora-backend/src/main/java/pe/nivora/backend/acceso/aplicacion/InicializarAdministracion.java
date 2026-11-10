package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import pe.nivora.backend.acceso.dominio.administradores.Administrador;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.dominio.contratos.ProtectorClavesAdministrativas;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioAdministradores;

public final class InicializarAdministracion {
    private final RepositorioAdministradores repositorio;
    private final ProtectorClavesAdministrativas claves;
    private final Clock reloj;

    public InicializarAdministracion(RepositorioAdministradores repositorio,
            ProtectorClavesAdministrativas claves, Clock reloj) {
        this.repositorio = repositorio;
        this.claves = claves;
        this.reloj = reloj;
    }

    public Administrador ejecutar(String usuario, String nombre, char[] clave) {
        var identificador = new UsuarioAdministrador(usuario);
        var nombreValido = Administrador.normalizarNombre(nombre);
        String hash = claves.proteger(clave);
        return repositorio.crearPrimero(identificador, nombreValido, hash,
            reloj.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
