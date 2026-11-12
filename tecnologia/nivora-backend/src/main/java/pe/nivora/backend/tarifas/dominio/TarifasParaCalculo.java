package pe.nivora.backend.tarifas.dominio;

import java.time.LocalDate;

import pe.nivora.backend.tarifas.dominio.HistorialTarifario.Asignacion;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas.Guardado;

public record TarifasParaCalculo(Guardado importado, Guardado administrativo) {
    public Seleccion aplicar(LocalDate desde, LocalDate hasta) {
        var elegido = administrativo != null && administrativo.historial().intersecta(desde, hasta)
            ? administrativo : importado;
        if (elegido == null) {
            return new Seleccion(null, null, null);
        }
        return new Seleccion(elegido.historial().fuente(), elegido.revision(),
            elegido.historial().aplicable(desde, hasta).orElse(null));
    }

    public Long revisionImportada() {
        return importado == null ? null : importado.revision();
    }

    public Long revisionAdministrativa() {
        return administrativo == null ? null : administrativo.revision();
    }

    public record Seleccion(String fuente, Long revision, Asignacion asignacion) {}
}
