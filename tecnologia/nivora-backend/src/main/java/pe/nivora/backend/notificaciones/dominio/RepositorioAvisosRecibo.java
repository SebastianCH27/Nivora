package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.time.LocalDate;

public interface RepositorioAvisosRecibo {
    int registrarDisponibles(LocalDate fechaLocal, Instant registradoEn);
}
