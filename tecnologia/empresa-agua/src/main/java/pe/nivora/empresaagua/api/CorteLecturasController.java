package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.lecturas.ConsultarCorteLecturas;
import pe.nivora.empresaagua.lecturas.CorteLecturas;

@RestController
public class CorteLecturasController {

    private final ConsultarCorteLecturas consulta;

    public CorteLecturasController(ConsultarCorteLecturas consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/v1/lecturas/corte")
    public ResponseEntity<CorteLecturas> consultar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(consulta.consultar());
    }
}
