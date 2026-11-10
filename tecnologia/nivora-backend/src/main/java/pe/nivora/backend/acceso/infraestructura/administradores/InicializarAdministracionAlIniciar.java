package pe.nivora.backend.acceso.infraestructura.administradores;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.contratos.AdministracionYaInicializadaException;

@Component
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name = "nivora.administradores.inicializar-al-iniciar", havingValue = "true")
public class InicializarAdministracionAlIniciar implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(InicializarAdministracionAlIniciar.class);
    private final InicializarAdministracion inicializar;

    public InicializarAdministracionAlIniciar(InicializarAdministracion inicializar) {
        this.inicializar = inicializar;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        // Las credenciales no se admiten mediante argumentos ni archivos de configuracion.
        String usuario = variable("NIVORA_ADMIN_USUARIO");
        String nombre = variable("NIVORA_ADMIN_NOMBRE");
        char[] clave = variable("NIVORA_ADMIN_CLAVE").toCharArray();
        try {
            var cuenta = inicializar.ejecutar(usuario, nombre, clave);
            LOG.info("Administracion inicial preparada: usuario {}; cuenta activa {}; permiso gestionar administradores {}.",
                cuenta.usuario().valor(), cuenta.activa(), cuenta.gestionarAdministradores());
        } catch (AdministracionYaInicializadaException existente) {
            LOG.warn("La administracion ya fue inicializada. No se modifico ninguna cuenta.");
        } finally {
            Arrays.fill(clave, '\0');
        }
    }

    private String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalArgumentException("Falta la variable de proceso " + nombre + ".");
        }
        return valor;
    }
}
