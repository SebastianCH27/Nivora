#include "../runner/permiso_notificaciones_windows.h"

#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

using nivora::PermisoWindows;
using nivora::ResolverPermisoWindows;

void exigir(bool condicion, const char* mensaje) {
  if (!condicion) throw std::runtime_error(mensaje);
}

int main() {
  int pruebas = 0;
  // Un ingreso o una actualización no muestran avisos ni conceden lo desconocido.
  for (const auto estado : {PermisoWindows::concedido, PermisoWindows::denegado,
                            PermisoWindows::pendiente}) {
    int consultas = 0, avisos = 0;
    const auto resultado = ResolverPermisoWindows(false,
        [&] { ++consultas; return estado; }, [&] { ++avisos; });
    exigir(resultado == estado && consultas == 1 && avisos == 0,
           "Una consulta ordinaria produjo un aviso o cambió el permiso.");
    ++pruebas;
  }
  // Con permiso ya conocido, solicitar no produce un aviso, y respeta denegado.
  for (const auto estado : {PermisoWindows::concedido, PermisoWindows::denegado}) {
    int avisos = 0;
    const auto resultado = ResolverPermisoWindows(true,
        [&] { return estado; }, [&] { ++avisos; });
    exigir(resultado == estado && avisos == 0,
           "Se mostró un aviso sin necesitar preparar el registro.");
    ++pruebas;
  }
  // Mostrar el aviso no prueba el permiso: hay que consultar de nuevo al SO.
  for (const auto final : {PermisoWindows::concedido, PermisoWindows::denegado,
                           PermisoWindows::pendiente}) {
    std::vector<std::string> eventos;
    int consultas = 0;
    const auto resultado = ResolverPermisoWindows(true,
        [&] { eventos.push_back("consulta"); return ++consultas == 1
            ? PermisoWindows::pendiente : final; },
        [&] { eventos.push_back("aviso"); });
    exigir(eventos == std::vector<std::string>{"consulta", "aviso", "consulta"},
           "La preparación no volvió a consultar el estado real.");
    exigir(resultado == final, "Se inventó un permiso tras mostrar el aviso.");
    ++pruebas;
  }
  // Los fallos distintos del estado pendiente se conservan como errores.
  for (const std::string fallo : {"consulta_inicial", "aviso", "consulta_final"}) {
    int consultas = 0;
    bool propagado = false;
    try {
      ResolverPermisoWindows(true, [&] {
        ++consultas;
        if (fallo == "consulta_inicial" ||
            (fallo == "consulta_final" && consultas == 2)) throw std::runtime_error(fallo);
        return PermisoWindows::pendiente;
      }, [&] { if (fallo == "aviso") throw std::runtime_error(fallo); });
    } catch (const std::runtime_error& e) { propagado = e.what() == fallo; }
    exigir(propagado, "Un error se convirtió en permiso o se perdió.");
    ++pruebas;
  }
  std::cout << pruebas << " comprobaciones C++ correctas.\n";
}
