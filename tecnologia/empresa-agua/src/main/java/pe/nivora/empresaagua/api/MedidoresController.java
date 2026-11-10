package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.lecturas.ConsultarInventarioMedidores;
import pe.nivora.empresaagua.lecturas.InventarioMedidores;

@RestController
public class MedidoresController {

    private final ConsultarInventarioMedidores consulta;

    public MedidoresController(ConsultarInventarioMedidores consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/v1/suministros/{codigo}/medidores")
    public ResponseEntity<InventarioMedidores> consultar(@PathVariable("codigo") String codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }
}
