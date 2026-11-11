package pe.nivora.backend.acceso.dominio.administradores;

/** Derivado de la sesion autenticada, nunca del cuerpo enviado por el cliente. */
public record ActorAdministrativo(TokenAdministrativo token, UsuarioAdministrador usuario) {}
