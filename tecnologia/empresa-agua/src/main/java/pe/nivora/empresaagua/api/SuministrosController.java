package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.suministros.ConsultarSuministro;
import pe.nivora.empresaagua.suministros.Suministro;

@RestController
@RequestMapping("/api/v1/suministros")
public class SuministrosController {

    private final ConsultarSuministro consulta;

    public SuministrosController(ConsultarSuministro consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Suministro> consultar(
            @PathVariable("codigo") String codigo) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }
}
