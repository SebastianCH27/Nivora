package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.facturacion.ConsultarRecibos;
import pe.nivora.empresaagua.facturacion.RecibosSuministro;

@RestController
@RequestMapping("/api/v1/suministros")
public class RecibosController {

    private final ConsultarRecibos consulta;

    public RecibosController(ConsultarRecibos consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{codigo}/recibos")
    public ResponseEntity<RecibosSuministro> consultar(
            @PathVariable("codigo") String codigo,
            @RequestParam(
                value = "limite",
                defaultValue = "12"
            ) int limite) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo, limite));
    }
}