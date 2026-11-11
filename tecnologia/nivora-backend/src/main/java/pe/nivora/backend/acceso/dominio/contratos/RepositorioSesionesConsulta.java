package pe.nivora.backend.acceso.dominio.contratos;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.IdentidadConsultada;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.consulta.SesionConsulta;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;

public interface RepositorioSesionesConsulta {

    /** Registra el suministro, el acceso y la sesion dentro de una transaccion. */
    SesionConsulta crear(TokenConsulta token, SuministroVerificado suministro,
        IdentidadConsultada identidad, Instant ahora, Instant expiraEn);

    Optional<SesionConsulta> autenticar(TokenConsulta token, Instant ahora);

    void cerrar(TokenConsulta token, Instant ahora);

    /** Devuelve cero si se consume el intento, o los segundos de espera. */
    long consumirIntento(String origen, Instant ahora, Duration ventana, int maximo);
}
