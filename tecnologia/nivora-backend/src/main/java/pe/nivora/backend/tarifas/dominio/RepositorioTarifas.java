package pe.nivora.backend.tarifas.dominio;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioTarifas {
    String FUENTE_ADMINISTRATIVA = "nivora";

    Guardado guardar(HistorialTarifario historial, Instant consultadoEn);

    Optional<Guardado> buscar(String fuente, CodigoSuministro suministro);

    TarifasParaCalculo buscarParaCalculo(String fuente, CodigoSuministro suministro);

    VistaAdministrativa consultarGestion(CodigoSuministro suministro);

    Publicacion consultarPublicacion(CodigoSuministro suministro, long revision);

    Publicacion publicar(CodigoSuministro suministro, long revisionEsperada,
        HistorialTarifario.Asignacion asignacion, String actor, Instant instante);

    record Guardado(long revision, Instant consultadoEn, HistorialTarifario historial) {}

    record VistaAdministrativa(Guardado importado, Publicacion publicada) {}

    record Publicacion(Guardado guardado, String publicadoPor) {}

    class Conflicto extends IllegalStateException {
        public Conflicto(String mensaje) {
            super(mensaje);
        }
    }

    class RevisionNoEncontrada extends RuntimeException {}

    class SuministroNoEncontrado extends RuntimeException {}
}
