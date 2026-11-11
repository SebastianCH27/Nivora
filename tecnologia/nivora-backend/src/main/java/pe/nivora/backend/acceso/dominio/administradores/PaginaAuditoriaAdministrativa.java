package pe.nivora.backend.acceso.dominio.administradores;

import java.time.Instant;
import java.util.List;

public record PaginaAuditoriaAdministrativa(List<Evento> eventos,
        String siguienteDespuesDe, boolean hayMas) {
    public PaginaAuditoriaAdministrativa { eventos = List.copyOf(eventos); }
    public record Evento(String idEvento, String actorUsuario, String afectadoUsuario,
        String operacion, Instant registradoEn) {}
}
