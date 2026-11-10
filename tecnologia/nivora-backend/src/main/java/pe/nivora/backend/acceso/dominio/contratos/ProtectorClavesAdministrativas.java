package pe.nivora.backend.acceso.dominio.contratos;

public interface ProtectorClavesAdministrativas {
    /** Valida y protege la clave nueva; no modifica el arreglo del llamador. */
    String proteger(char[] clave);
    boolean coincide(char[] clave, String hashGuardado);
}
