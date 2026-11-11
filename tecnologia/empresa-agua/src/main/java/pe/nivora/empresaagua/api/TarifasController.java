package pe.nivora.empresaagua.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.empresaagua.tarifas.ConsultarHistorialTarifas;
import pe.nivora.empresaagua.tarifas.ConsultarTarifaActual;
import pe.nivora.empresaagua.tarifas.HistorialTarifas;
import pe.nivora.empresaagua.tarifas.TarifaActual;

@RestController
@RequestMapping("/api/v1/suministros")
public class TarifasController {

    private final ConsultarTarifaActual consulta;
    private final ConsultarHistorialTarifas historial;

    public TarifasController(ConsultarTarifaActual consulta, ConsultarHistorialTarifas historial) {
        this.consulta = consulta;
        this.historial = historial;
    }

    @GetMapping("/{codigo}/tarifa-actual")
    public ResponseEntity<TarifaActual> consultar(
            @PathVariable("codigo") String codigo) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }

    @GetMapping("/{codigo}/tarifas")
    public ResponseEntity<HistorialTarifas> historial(@PathVariable("codigo") String codigo) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(historial.consultar(codigo));
    }
}
