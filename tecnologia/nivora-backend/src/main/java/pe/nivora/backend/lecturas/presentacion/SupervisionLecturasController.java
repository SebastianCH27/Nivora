package pe.nivora.backend.lecturas.presentacion;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.lecturas.aplicacion.ConsultarSupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.SupervisionLecturas;

@RestController
@RequestMapping("/api/v1/supervision/suministros/{codigo}/lecturas")
public class SupervisionLecturasController {
    private final ConsultarSupervisionLecturas consultar;
    private final boolean automaticaHabilitada;

    public SupervisionLecturasController(ConsultarSupervisionLecturas consultar,
            @Value("${nivora.sincronizacion.habilitada:false}") boolean automaticaHabilitada) {
        this.consultar = consultar;
        this.automaticaHabilitada = automaticaHabilitada;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> consultar(@PathVariable("codigo") String codigo) {
        var estado = consultar.ejecutar(codigo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Respuesta(estado.fuente(), estado.suministro().valor(),
                estado.consultadoEn(), automaticaHabilitada, estado.almacenamiento(),
                estado.recuperacion(), estado.programacion()));
    }

    public record Respuesta(String fuente, String codigoSuministro, Instant consultadoEn,
            boolean sincronizacionAutomaticaHabilitada,
            SupervisionLecturas.Almacenamiento almacenamiento,
            SupervisionLecturas.Recuperacion recuperacion,
            SupervisionLecturas.Programacion programacion) {}
}
