package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;

/** Verifica un suministro en la fuente y conserva los datos encontrados. */
public final class ActualizarSuministro {

    private final FuenteSuministros fuente;
    private final RepositorioSuministros repositorio;
    private final Clock reloj;

    public ActualizarSuministro(FuenteSuministros fuente,
                               RepositorioSuministros repositorio, Clock reloj) {
        this.fuente = Objects.requireNonNull(fuente);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Devuelve la verificacion obtenida; no elimina datos si la fuente confirma ausencia. */
    public Optional<SuministroVerificado> ejecutar(CodigoSuministro codigo) {
        Objects.requireNonNull(codigo);
        return fuente.consultar(codigo).map(suministro -> {
            if (!codigo.equals(suministro.codigo())) {
                throw new FuenteSuministrosNoDisponibleException(
                    "La fuente devolvio un suministro diferente al solicitado.");
            }
            var verificado = new SuministroVerificado(suministro, reloj.instant());
            repositorio.guardar(verificado);
            return verificado;
        });
    }
}
