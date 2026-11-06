package pe.nivora.empresaagua;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class EmpresaAguaApplicationTests {

    @Autowired
    private DataSource fuente;

    @Test
    void iniciaConUnaBaseDePruebasAislada() throws Exception {
        try (var conexion = fuente.getConnection()) {
            assertTrue(
                conexion.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:"));
        }
    }
}