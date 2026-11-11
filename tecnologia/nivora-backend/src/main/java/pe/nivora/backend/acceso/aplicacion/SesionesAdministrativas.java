package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.dominio.contratos.*;

public final class SesionesAdministrativas {
    private final RepositorioSesionesAdministrativas repositorio;
    private final ProtectorClavesAdministrativas claves;
    private final Clock reloj;
    private final PoliticaSesionAdministrativa politica;
    private final String hashAusente;

    public SesionesAdministrativas(RepositorioSesionesAdministrativas repositorio,
            ProtectorClavesAdministrativas claves, Clock reloj, PoliticaSesionAdministrativa politica) {
        this.repositorio = repositorio;
        this.claves = claves;
        this.reloj = reloj;
        this.politica = politica;
        hashAusente = claves.proteger("Referencia interna sin cuenta administrativa".toCharArray());
    }

    public Inicio iniciar(String usuario, char[] clave, String origen) {
        var identificador = new UsuarioAdministrador(usuario);
        if (clave == null || clave.length == 0 || clave.length > 256
                || Character.codePointCount(clave, 0, clave.length) > 128) {
            throw new IllegalArgumentException("Credenciales con formato invalido.");
        }
        Instant ahora = ahora();
        repositorio.consumirIntento(identificador, origen, ahora, politica);
        var encontrada = repositorio.buscarCredencial(identificador);
        boolean coincide = claves.coincide(clave, encontrada.map(CredencialAdministrativa::hashClave).orElse(hashAusente));
        if (!coincide || encontrada.isEmpty() || !encontrada.get().cuenta().activa()) {
            throw new CredencialesAdministrativasInvalidasException();
        }
        var token = TokenAdministrativo.crear();
        // La escritura vuelve a comprobar estado, version y hash tras la verificacion.
        var sesion = repositorio.crear(token, encontrada.get(), ahora(), politica);
        return new Inicio(token, sesion);
    }

    public Optional<SesionAdministrativa> autenticar(TokenAdministrativo token) {
        return repositorio.autenticar(token, ahora(), politica);
    }

    public void cerrar(TokenAdministrativo token) { repositorio.cerrar(token, ahora()); }

    private Instant ahora() { return reloj.instant().truncatedTo(ChronoUnit.MICROS); }
    public record Inicio(TokenAdministrativo token, SesionAdministrativa sesion) {}
}
