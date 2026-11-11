package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.consulta.SesionConsulta;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;
import pe.nivora.backend.acceso.dominio.contratos.AccesoConsultaLimitadoException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSesionesConsulta;

public final class SesionesConsulta {

    private final FuenteSuministros suministros;
    private final FuenteIdentidad identidades;
    private final RepositorioSesionesConsulta repositorio;
    private final Clock reloj;
    private final Duration duracion;
    private final Duration ventanaIntentos;
    private final int intentosPorOrigen;

    public SesionesConsulta(FuenteSuministros suministros, FuenteIdentidad identidades,
            RepositorioSesionesConsulta repositorio, Clock reloj, Duration duracion,
            Duration ventanaIntentos, int intentosPorOrigen) {
        if (duracion.isNegative() || duracion.isZero() || ventanaIntentos.isNegative()
                || ventanaIntentos.isZero() || intentosPorOrigen < 1) {
            throw new IllegalArgumentException("La politica de sesiones de consulta debe ser positiva.");
        }
        this.suministros = suministros;
        this.identidades = identidades;
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.duracion = duracion;
        this.ventanaIntentos = ventanaIntentos;
        this.intentosPorOrigen = intentosPorOrigen;
    }

    public Optional<Inicio> iniciar(CodigoSuministro codigo, Dni dni, String origen) {
        long espera = repositorio.consumirIntento(origen, ahora(), ventanaIntentos, intentosPorOrigen);
        if (espera > 0) {
            throw new AccesoConsultaLimitadoException(espera);
        }

        var encontrado = suministros.consultar(codigo);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        var suministro = encontrado.get();
        if (!codigo.equals(suministro.codigo())) {
            throw new FuenteSuministrosNoDisponibleException("Respuesta de suministro incompatible.");
        }
        var verificado = new SuministroVerificado(suministro, ahora());

        var identidad = identidades.consultar(dni);
        if (identidad == null || !dni.equals(identidad.dni())) {
            throw new FuenteIdentidadNoDisponibleException(
                FuenteIdentidadNoDisponibleException.Motivo.RESPUESTA_INCOMPATIBLE);
        }
        var token = TokenConsulta.crear();
        Instant inicio = ahora();
        var sesion = repositorio.crear(token, verificado, identidad, inicio, inicio.plus(duracion));
        return Optional.of(new Inicio(token, sesion));
    }

    public Optional<SesionConsulta> autenticar(TokenConsulta token) {
        return repositorio.autenticar(token, ahora());
    }

    public void cerrar(TokenConsulta token) {
        repositorio.cerrar(token, ahora());
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record Inicio(TokenConsulta token, SesionConsulta sesion) {}
}
