package pe.nivora.backend.acceso.dominio.consulta;

import java.time.Instant;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Sesion de consulta limitada al suministro autorizado en su creacion. */
public record SesionConsulta(String id, CodigoSuministro codigoSuministro, String nombre,
        Instant creadaEn, Instant expiraEn) {

    @Override
    public String toString() {
        return "SesionConsulta[datos ocultos]";
    }
}
