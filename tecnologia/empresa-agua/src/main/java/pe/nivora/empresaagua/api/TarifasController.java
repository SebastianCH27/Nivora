package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.tarifas.ConsultarTarifaActual;
import pe.nivora.empresaagua.tarifas.TarifaActual;

@RestController
@RequestMapping("/api/v1/suministros")
public class TarifasController {

    private final ConsultarTarifaActual consulta;

    public TarifasController(ConsultarTarifaActual consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{codigo}/tarifa-actual")
    public ResponseEntity<TarifaActual> consultar(
            @PathVariable("codigo") String codigo) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }
}