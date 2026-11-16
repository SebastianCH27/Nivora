import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

class GenerarClavesVapid {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Indica la ruta de un archivo nuevo, fuera del repositorio.");
        }
        var generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        var par = generador.generateKeyPair();
        var publica = ((ECPublicKey) par.getPublic()).getW();
        var privada = ((ECPrivateKey) par.getPrivate()).getS();
        var hex = HexFormat.of();
        var base64 = Base64.getUrlEncoder().withoutPadding();
        var punto = hex.parseHex("04%064x%064x".formatted(publica.getAffineX(), publica.getAffineY()));
        var escalar = hex.parseHex("%064x".formatted(privada));
        Files.writeString(Path.of(args[0]), """
            {"publica":"%s","privada":"%s"}
            """.formatted(base64.encodeToString(punto), base64.encodeToString(escalar)), CREATE_NEW, WRITE);
        System.out.println("Par VAPID guardado en el archivo indicado.");
    }
}
