package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ProgresoRecuperacion;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;

/** Recorre un numero acotado de paginas, reanudando el progreso propio. */
public final class RecuperarLecturas {
    private final String nombreFuente;
    private final FuenteLecturas fuente;
    private final RepositorioRecuperacionLecturas repositorio;
    private final Clock reloj;

    public RecuperarLecturas(String nombreFuente, FuenteLecturas fuente,
                            RepositorioRecuperacionLecturas repositorio, Clock reloj) {
        this.nombreFuente = Objects.requireNonNull(nombreFuente);
        this.fuente = Objects.requireNonNull(fuente);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public Resultado ejecutar(CodigoSuministro codigo, int limite, int maximoPaginas) {
        if (limite < 1 || limite > 500 || maximoPaginas < 1 || maximoPaginas > 100) {
            throw new IllegalArgumentException("El limite debe ser 1-500 y las paginas por ejecucion 1-100.");
        }
        var progreso = repositorio.consultar(nombreFuente, codigo);
        if (progreso.estado() == EstadoRecuperacion.RECORRIDO_COMPLETO) {
            // Un nuevo recorrido revisa tambien filas antiguas que hayan llegado o cambiado despues.
            progreso = repositorio.reiniciar(progreso, reloj.instant());
        }
        int paginas = 0;
        int nuevas = 0;
        int repetidas = 0;
        int rechazadas = 0;
        while (paginas < maximoPaginas && progreso.estado() != EstadoRecuperacion.BLOQUEADO) {
            Optional<LoteLecturas> respuesta;
            try {
                respuesta = fuente.consultar(codigo, progreso.cursor(), limite);
            } catch (FuenteLecturasNoDisponibleException ex) {
                try {
                    repositorio.registrarFallo(progreso, EstadoRecuperacion.ERROR_FUENTE, reloj.instant());
                } catch (RuntimeException falloRegistro) {
                    falloRegistro.addSuppressed(ex);
                    throw falloRegistro;
                }
                throw ex;
            }
            if (respuesta.isEmpty()) {
                progreso = repositorio.registrarFallo(progreso,
                    EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO, reloj.instant());
                break;
            }
            var paso = repositorio.confirmar(progreso, respuesta.orElseThrow(), reloj.instant());
            progreso = paso.progreso();
            nuevas += paso.incorporacion().nuevas();
            repetidas += paso.incorporacion().repetidas();
            rechazadas += paso.incorporacion().rechazadas();
            paginas++;
            if (progreso.estado() == EstadoRecuperacion.RECORRIDO_COMPLETO) { break; }
        }
        return new Resultado(paginas, new ResultadoIncorporacion(nuevas, repetidas, rechazadas), progreso);
    }

    public record Resultado(int paginas, ResultadoIncorporacion incorporacion, ProgresoRecuperacion progreso) {}
}
