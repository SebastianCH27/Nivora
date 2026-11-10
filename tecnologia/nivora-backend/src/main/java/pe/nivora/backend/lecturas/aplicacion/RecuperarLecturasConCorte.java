package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ProgresoRecuperacion;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasConCorte;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;

/** Recuperacion manual con contexto persistido; las llamadas HTTP quedan fuera de las escrituras. */
public final class RecuperarLecturasConCorte {
    private final String nombreFuente;
    private final FuenteLecturasConCorte fuente;
    private final RepositorioRecuperacionLecturas repositorio;
    private final Clock reloj;

    public RecuperarLecturasConCorte(String nombreFuente, FuenteLecturasConCorte fuente,
            RepositorioRecuperacionLecturas repositorio, Clock reloj) {
        this.nombreFuente = Objects.requireNonNull(nombreFuente);
        this.fuente = Objects.requireNonNull(fuente);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public RecuperarLecturas.Resultado ejecutar(CodigoSuministro codigo, int limite, int maximoPaginas) {
        if (limite < 1 || limite > 500 || maximoPaginas < 1 || maximoPaginas > 100) {
            throw new IllegalArgumentException("El limite debe ser 1-500 y las paginas por ejecucion 1-100.");
        }
        var progreso = repositorio.consultar(nombreFuente, codigo);
        if (progreso.estado() == EstadoRecuperacion.BLOQUEADO) return vacio(progreso);
        if (progreso.estado() == EstadoRecuperacion.RECORRIDO_COMPLETO) {
            // Se conserva la revision completa del historial de los recorridos anteriores.
            progreso = repositorio.reiniciar(progreso, reloj.instant());
        }
        if (progreso.contexto() == null) {
            Optional<ContextoLecturas> contexto;
            try {
                contexto = fuente.consultarContexto(codigo);
                if (contexto.isPresent() && (!nombreFuente.equals(contexto.orElseThrow().fuente())
                        || !codigo.equals(contexto.orElseThrow().suministro())
                        || contexto.orElseThrow().confirmadoHasta().isAfter(reloj.instant()))) {
                    throw new FuenteLecturasNoDisponibleException(
                        "El corte no corresponde al suministro o esta en el futuro.");
                }
            } catch (FuenteLecturasNoDisponibleException error) {
                registrarError(progreso, error);
                throw error;
            }
            if (contexto.isEmpty()) {
                return vacio(repositorio.registrarFallo(progreso,
                    EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO, reloj.instant()));
            }
            // Los progresos anteriores sin contexto reinician el cursor para validar todo el historial.
            progreso = repositorio.iniciarContexto(progreso, contexto.orElseThrow(), reloj.instant());
        }

        int paginas = 0, nuevas = 0, repetidas = 0, rechazadas = 0;
        while (paginas < maximoPaginas) {
            Optional<LoteLecturas> respuesta;
            try {
                respuesta = fuente.consultar(codigo, progreso.cursor(), limite, progreso.contexto());
            } catch (FuenteLecturasNoDisponibleException error) {
                registrarError(progreso, error);
                throw error;
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
            if (progreso.estado() == EstadoRecuperacion.RECORRIDO_COMPLETO
                    || progreso.estado() == EstadoRecuperacion.BLOQUEADO) break;
        }
        return new RecuperarLecturas.Resultado(paginas,
            new ResultadoIncorporacion(nuevas, repetidas, rechazadas), progreso);
    }

    private void registrarError(ProgresoRecuperacion progreso, FuenteLecturasNoDisponibleException error) {
        try {
            repositorio.registrarFallo(progreso, EstadoRecuperacion.ERROR_FUENTE, reloj.instant());
        } catch (RuntimeException falloRegistro) {
            falloRegistro.addSuppressed(error);
            throw falloRegistro;
        }
    }

    private static RecuperarLecturas.Resultado vacio(ProgresoRecuperacion progreso) {
        return new RecuperarLecturas.Resultado(0, new ResultadoIncorporacion(0, 0, 0), progreso);
    }
}
