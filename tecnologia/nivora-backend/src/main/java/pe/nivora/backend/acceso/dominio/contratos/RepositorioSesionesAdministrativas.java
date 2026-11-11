package pe.nivora.backend.acceso.dominio.contratos;

import java.time.Instant;
import java.util.Optional;
import pe.nivora.backend.acceso.dominio.administradores.*;

public interface RepositorioSesionesAdministrativas {
    Optional<CredencialAdministrativa> buscarCredencial(UsuarioAdministrador usuario);
    void consumirIntento(UsuarioAdministrador usuario, String origen, Instant ahora,
            PoliticaSesionAdministrativa politica);
    SesionAdministrativa crear(TokenAdministrativo token, CredencialAdministrativa esperada,
            Instant ahora, PoliticaSesionAdministrativa politica);
    Optional<SesionAdministrativa> autenticar(TokenAdministrativo token, Instant ahora,
            PoliticaSesionAdministrativa politica);
    boolean cerrar(TokenAdministrativo token, Instant ahora);
}
