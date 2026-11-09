package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.lecturas.ConsultarLecturas;
import pe.nivora.empresaagua.lecturas.PaginaLecturas;

@RestController
@RequestMapping("/api/v1/suministros")
public class LecturasController {

    private final ConsultarLecturas consulta;

    public LecturasController(ConsultarLecturas consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{codigo}/lecturas")
    public ResponseEntity<PaginaLecturas> consultar(
            @PathVariable("codigo") String codigo,
            @RequestParam(value = "despuesDe", defaultValue = "0") long despuesDe,
            @RequestParam(value = "limite", defaultValue = "100") int limite) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo, despuesDe, limite));
    }
}
