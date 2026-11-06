package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.consumo.ConsumoActual;
import pe.nivora.empresaagua.consumo.ConsultarConsumoActual;
import pe.nivora.empresaagua.consumo.ConsultarHistorialConsumo;
import pe.nivora.empresaagua.consumo.HistorialConsumo;

@RestController
@RequestMapping("/api/v1/suministros")
public class ConsumoController {

    private final ConsultarConsumoActual consulta;
    private final ConsultarHistorialConsumo historial;

    public ConsumoController(
            ConsultarConsumoActual consulta,
            ConsultarHistorialConsumo historial) {

        this.consulta = consulta;
        this.historial = historial;
    }

    @GetMapping("/{codigo}/consumo-actual")
    public ResponseEntity<ConsumoActual> consultar(
            @PathVariable("codigo") String codigo) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }

    @GetMapping("/{codigo}/historial")
    public ResponseEntity<HistorialConsumo> consultarHistorial(
            @PathVariable("codigo") String codigo,
            @RequestParam(
                value = "meses",
                defaultValue = "12"
            ) int meses) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(historial.consultar(codigo, meses));
    }
}