#ifndef NIVORA_PERMISO_NOTIFICACIONES_WINDOWS_H_
#define NIVORA_PERMISO_NOTIFICACIONES_WINDOWS_H_

namespace nivora {

enum class PermisoWindows { concedido, denegado, pendiente };

// Una consulta sin estado no concede permiso. La preparación visible solo puede
// ejecutarse como consecuencia de Solicitar permiso, nunca al consultar o entrar.
template <class Consultar, class Preparar>
PermisoWindows ResolverPermisoWindows(bool solicitado, Consultar consultar,
                                     Preparar preparar) {
  const auto inicial = consultar();
  if (inicial != PermisoWindows::pendiente || !solicitado) return inicial;
  preparar();
  return consultar();
}

}  // namespace nivora
#endif
