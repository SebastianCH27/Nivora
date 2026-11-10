package pe.nivora.backend.lecturas.infraestructura.empresaagua;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasConCorte;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.infraestructura.ContextoLecturasJson;

public final class FuenteLecturasConCorteHttp implements FuenteLecturasConCorte {
    private final ClienteEmpresaAgua cliente;
    private final FuenteLecturasHttp paginas;

    public FuenteLecturasConCorteHttp(ClienteEmpresaAgua cliente) {
        this.cliente = Objects.requireNonNull(cliente);
        this.paginas = new FuenteLecturasHttp(cliente);
    }

    @Override
    public Optional<ContextoLecturas> consultarContexto(CodigoSuministro codigo) {
        Objects.requireNonNull(codigo);
        String ruta = "/api/v1/suministros/" + codigo.valor() + "/medidores";
        try {
            var inventario = cliente.consultar(ruta, Map.of(), 256 * 1024);
            if (inventario.estado() == 404 && esAusencia(inventario.cuerpo(), ruta)) {
                return Optional.empty();
            }
            if (inventario.estado() != 200) throw noDisponible();
            var corte = cliente.consultar("/api/v1/lecturas/corte", Map.of(), 64 * 1024);
            if (corte.estado() != 200) throw noDisponible();
            try {
                return Optional.of(ContextoLecturasJson.interpretar(
                    corte.cuerpo(), inventario.cuerpo(), codigo));
            } catch (RuntimeException error) {
                throw noDisponible();
            }
        } catch (ClienteEmpresaAgua.ConsultaFallidaException error) {
            throw new FuenteLecturasNoDisponibleException(
                "No se pudo obtener el corte y el inventario de lecturas.", error);
        }
    }

    @Override
    public Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor,
            int limite, ContextoLecturas contexto) {
        if (!"empresa-agua".equals(contexto.fuente()) || !codigo.equals(contexto.suministro())) {
            throw new IllegalArgumentException("El contexto no corresponde al suministro solicitado.");
        }
        return paginas.consultar(codigo, cursor, limite, contexto.hastaIdLectura());
    }

    private static boolean esAusencia(JsonNode cuerpo, String ruta) {
        try {
            JsonNode estado = cuerpo.get("status");
            return estado != null && estado.isIntegralNumber()
                && BigInteger.valueOf(404).equals(estado.bigIntegerValue())
                && "Suministro no encontrado".equals(ContextoLecturasJson.texto(cuerpo, "title"))
                && ruta.equals(ContextoLecturasJson.texto(cuerpo, "instance"));
        } catch (RuntimeException error) { return false; }
    }

    private static FuenteLecturasNoDisponibleException noDisponible() {
        return new FuenteLecturasNoDisponibleException(
            "La Empresa de Agua no devolvio un corte e inventario utilizables.");
    }
}
