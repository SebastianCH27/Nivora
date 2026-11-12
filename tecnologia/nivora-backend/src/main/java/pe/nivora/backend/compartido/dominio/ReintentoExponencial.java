package pe.nivora.backend.compartido.dominio;

import java.time.Duration;
import java.time.Instant;

public record ReintentoExponencial(int fallos, Instant proximoIntento) {
    public static ReintentoExponencial calcular(int fallosPrevios, Instant ahora, Duration inicial, Duration maximo) {
        int fallos = (int) Math.min(Integer.MAX_VALUE, (long) fallosPrevios + 1);
        long segundos = inicial.getSeconds();
        long tope = maximo.getSeconds();
        for (int n = 1; n < fallos && segundos < tope; n++) {
            segundos = segundos > tope / 2 ? tope : Math.min(tope, segundos * 2);
        }
        return new ReintentoExponencial(fallos, ahora.plusSeconds(segundos));
    }
}
