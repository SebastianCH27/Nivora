#include "notificaciones_dispositivo.h"
#include "permiso_notificaciones_windows.h"

#include <windows.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <roapi.h>
#include <propkey.h>
#include <propvarutil.h>
#include <wrl/client.h>
#include <winrt/Windows.UI.Notifications.h>
#include <winrt/Windows.Data.Xml.Dom.h>
#include <winrt/Windows.Foundation.h>
#include <chrono>
#include <flutter/method_channel.h>
#include <flutter/standard_method_codec.h>
#include <algorithm>
#include <cstdio>
#include <cwctype>
#include <cwchar>
#include <string>

namespace {
using Microsoft::WRL::ComPtr;
constexpr wchar_t kAppId[] = L"pe.nivora.Consulta";
constexpr wchar_t kRegistro[] = L"Software\\Nivora\\Consulta";
constexpr wchar_t kRegistroEmisor[] = L"Software\\Classes\\AppUserModelId\\pe.nivora.Consulta";
HRESULT estadoIdentidadProceso = E_PENDING;

void ComprobarWin32(LSTATUS estado) {
  if (estado != ERROR_SUCCESS) winrt::throw_hresult(HRESULT_FROM_WIN32(estado));
}

// El callback puede llegar a un hilo que todavía no tiene COM, o que ya usa MTA.
// Se prepara ANTES de crear IShellLink, respetando el modelo que ya tenga el hilo.
class ApartamentoCom {
 public:
  ApartamentoCom() {
    HRESULT estado = RoInitialize(RO_INIT_SINGLETHREADED);
    if (estado == RPC_E_CHANGED_MODE) estado = RoInitialize(RO_INIT_MULTITHREADED);
    winrt::check_hresult(estado);
    propio_ = true;  // S_OK y S_FALSE requieren una llamada correspondiente.
  }
  ~ApartamentoCom() { if (propio_) RoUninitialize(); }
  ApartamentoCom(const ApartamentoCom&) = delete;
  ApartamentoCom& operator=(const ApartamentoCom&) = delete;
 private:
  bool propio_ = false;
};

class ClaveRegistro {
 public:
  ~ClaveRegistro() { if (valor != nullptr) RegCloseKey(valor); }
  HKEY valor = nullptr;
};

class ValorPropiedad {
 public:
  ValorPropiedad() { PropVariantInit(&valor); }
  ~ValorPropiedad() { PropVariantClear(&valor); }
  PROPVARIANT valor;
};

std::wstring RutaAcceso() {
  PWSTR programas = nullptr;
  winrt::check_hresult(SHGetKnownFolderPath(FOLDERID_Programs, 0, nullptr, &programas));
  const std::wstring ruta = std::wstring(programas) + L"\\Nivora - Consulta.lnk";
  CoTaskMemFree(programas);
  return ruta;
}

class BloqueoIdentidad {
 public:
  BloqueoIdentidad() {
    mutex_ = CreateMutexW(nullptr, FALSE, L"Local\\Nivora.Consulta.Identidad");
    if (mutex_ == nullptr) winrt::throw_last_error();
    const DWORD estado = WaitForSingleObject(mutex_, 5000);
    if (estado != WAIT_OBJECT_0 && estado != WAIT_ABANDONED) {
      const DWORD error = estado == WAIT_FAILED ? GetLastError() : ERROR_TIMEOUT;
      CloseHandle(mutex_);
      winrt::throw_hresult(HRESULT_FROM_WIN32(error));
    }
  }
  ~BloqueoIdentidad() { ReleaseMutex(mutex_); CloseHandle(mutex_); }
  BloqueoIdentidad(const BloqueoIdentidad&) = delete;
  BloqueoIdentidad& operator=(const BloqueoIdentidad&) = delete;
 private:
  HANDLE mutex_ = nullptr;
};

std::string Identificar() {
  BloqueoIdentidad bloqueo;
  ClaveRegistro clave;
  ComprobarWin32(RegCreateKeyExW(HKEY_CURRENT_USER, kRegistro, 0, nullptr, 0,
      KEY_READ | KEY_WRITE, nullptr, &clave.valor, nullptr));
  wchar_t buffer[64] = {};
  DWORD bytes = sizeof(buffer), tipo = 0;
  const LSTATUS lectura = RegQueryValueExW(clave.valor, L"IdInstalacion", nullptr, &tipo,
      reinterpret_cast<BYTE*>(buffer), &bytes);
  std::wstring id;
  if (lectura == ERROR_SUCCESS && tipo == REG_SZ && bytes < sizeof(buffer)) {
    id = buffer;
  } else if (lectura == ERROR_FILE_NOT_FOUND) {
    GUID guid;
    winrt::check_hresult(CoCreateGuid(&guid));
    if (StringFromGUID2(guid, buffer, 64) == 0) winrt::throw_hresult(E_FAIL);
    id = std::wstring(buffer).substr(1, 36);
    std::transform(id.begin(), id.end(), id.begin(),
        [](wchar_t c) { return static_cast<wchar_t>(std::towlower(c)); });
    ComprobarWin32(RegSetValueExW(clave.valor, L"IdInstalacion", 0, REG_SZ,
        reinterpret_cast<const BYTE*>(id.c_str()),
        static_cast<DWORD>((id.size() + 1) * sizeof(wchar_t))));
  } else {
    ComprobarWin32(lectura == ERROR_SUCCESS ? ERROR_INVALID_DATA : lectura);
  }
  // Una identidad inválida no se sustituye: Dart la rechaza sin perder la anterior.
  return winrt::to_string(id);
}

void RegistrarEmisor(const char*& etapa) {
  etapa = "REGISTRAR_EMISOR";
  ClaveRegistro clave;
  ComprobarWin32(RegCreateKeyExW(HKEY_CURRENT_USER, kRegistroEmisor, 0, nullptr,
      REG_OPTION_NON_VOLATILE, KEY_QUERY_VALUE | KEY_SET_VALUE, nullptr,
      &clave.valor, nullptr));
  constexpr wchar_t nombre[] = L"Nivora";
  // Metadatos de nuestra aplicación. No se escriben permisos ni políticas.
  ComprobarWin32(RegSetValueExW(clave.valor, L"DisplayName", 0, REG_SZ,
      reinterpret_cast<const BYTE*>(nombre), sizeof(nombre)));
}

bool EmisorRegistrado(const char*& etapa) {
  etapa = "LEER_EMISOR";
  ClaveRegistro clave;
  const LSTATUS abierto = RegOpenKeyExW(HKEY_CURRENT_USER, kRegistroEmisor, 0,
      KEY_QUERY_VALUE, &clave.valor);
  if (abierto == ERROR_FILE_NOT_FOUND || abierto == ERROR_PATH_NOT_FOUND) return false;
  ComprobarWin32(abierto);
  wchar_t nombre[128] = {};
  DWORD bytes = sizeof(nombre), tipo = 0;
  const LSTATUS leido = RegQueryValueExW(clave.valor, L"DisplayName", nullptr,
      &tipo, reinterpret_cast<BYTE*>(nombre), &bytes);
  if (leido == ERROR_FILE_NOT_FOUND || leido == ERROR_MORE_DATA) return false;
  ComprobarWin32(leido);
  return (tipo == REG_SZ || tipo == REG_EXPAND_SZ) && bytes < sizeof(nombre) && nombre[0] != 0;
}

void PrepararAcceso(const char*& etapa) {
  etapa = "RUTA_ACCESO";
  const std::wstring ruta = RutaAcceso();
  wchar_t exe[32768] = {};
  const DWORD longitud = GetModuleFileNameW(nullptr, exe, 32768);
  if (longitud == 0) winrt::throw_last_error();
  if (longitud >= 32768) winrt::throw_hresult(HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER));
  const std::wstring ejecutable(exe, longitud);
  const std::wstring carpeta = ejecutable.substr(0, ejecutable.find_last_of(L"\\/"));

  etapa = "CREAR_ACCESO";
  ComPtr<IShellLinkW> enlace;
  winrt::check_hresult(CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&enlace)));
  winrt::check_hresult(enlace->SetPath(exe));
  winrt::check_hresult(enlace->SetArguments(L""));
  winrt::check_hresult(enlace->SetWorkingDirectory(carpeta.c_str()));
  winrt::check_hresult(enlace->SetDescription(L"Consulta de consumo de agua - Nivora"));
  winrt::check_hresult(enlace->SetIconLocation(exe, 0));

  etapa = "IDENTIDAD_ACCESO";
  ComPtr<IPropertyStore> propiedades;
  winrt::check_hresult(enlace.As(&propiedades));
  ValorPropiedad app_id;
  winrt::check_hresult(InitPropVariantFromString(kAppId, &app_id.valor));
  winrt::check_hresult(propiedades->SetValue(PKEY_AppUserModel_ID, app_id.valor));
  winrt::check_hresult(propiedades->Commit());

  etapa = "GUARDAR_ACCESO";
  ComPtr<IPersistFile> archivo;
  winrt::check_hresult(enlace.As(&archivo));
  winrt::check_hresult(archivo->Save(ruta.c_str(), TRUE));
  // Informa al shell del acceso nuevo o actualizado, sin cambiar permisos.
  SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATHW, ruta.c_str(), nullptr);
}

bool AccesoPreparado(const char*& etapa) {
  etapa = "LEER_ACCESO";
  const std::wstring ruta = RutaAcceso();
  const DWORD atributos = GetFileAttributesW(ruta.c_str());
  if (atributos == INVALID_FILE_ATTRIBUTES) {
    const DWORD error = GetLastError();
    if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND) return false;
    winrt::throw_hresult(HRESULT_FROM_WIN32(error));
  }
  if ((atributos & FILE_ATTRIBUTE_DIRECTORY) != 0) return false;
  ComPtr<IShellLinkW> enlace;
  winrt::check_hresult(CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&enlace)));
  ComPtr<IPersistFile> archivo;
  winrt::check_hresult(enlace.As(&archivo));
  winrt::check_hresult(archivo->Load(ruta.c_str(), STGM_READ));
  ComPtr<IPropertyStore> propiedades;
  winrt::check_hresult(enlace.As(&propiedades));
  ValorPropiedad app_id;
  winrt::check_hresult(propiedades->GetValue(PKEY_AppUserModel_ID, &app_id.valor));
  return app_id.valor.vt == VT_LPWSTR && app_id.valor.pwszVal != nullptr &&
      wcscmp(app_id.valor.pwszVal, kAppId) == 0;
}

std::string Permiso(const char*& etapa, bool solicitado) {
  if (!AccesoPreparado(etapa) || !EmisorRegistrado(etapa)) return "PENDIENTE";
  etapa = "CREAR_NOTIFICADOR";
  using namespace winrt::Windows::UI::Notifications;
  const auto notificador = ToastNotificationManager::CreateToastNotifier(kAppId);
  const auto consultar = [&] {
    etapa = "CONSULTAR_PERMISO";
    try {
      switch (notificador.Setting()) {
        case NotificationSetting::Enabled: return nivora::PermisoWindows::concedido;
        case NotificationSetting::DisabledForApplication:
        case NotificationSetting::DisabledForUser:
        case NotificationSetting::DisabledByGroupPolicy:
        case NotificationSetting::DisabledByManifest: return nivora::PermisoWindows::denegado;
        default: winrt::throw_hresult(HRESULT_FROM_WIN32(ERROR_NOT_SUPPORTED));
      }
    } catch (const winrt::hresult_error& error) {
      // Puede faltar el estado del emisor recién preparado. No es una concesión.
      if (static_cast<HRESULT>(error.code()) == HRESULT_FROM_WIN32(ERROR_NOT_FOUND)) {
        return nivora::PermisoWindows::pendiente;
      }
      throw;
    }
  };
  const auto preparar = [&] {
    etapa = "AVISO_PRUEBA";
    winrt::Windows::Data::Xml::Dom::XmlDocument xml;
    xml.LoadXml(L"<toast duration=\"short\"><visual><binding template=\"ToastGeneric\">"
        L"<text>Nivora</text><text>Aviso de prueba de notificaciones.</text>"
        L"<text>Vuelve a Nivora para comprobar el permiso y activar tu suministro.</text>"
        L"</binding></visual><audio silent=\"true\"/></toast>");
    ToastNotification aviso(xml);
    aviso.Tag(L"permiso");
    aviso.Group(L"configuracion");
    const auto expiracion = winrt::clock::now() + std::chrono::minutes(1);
    aviso.ExpirationTime(winrt::box_value(expiracion).as<
        winrt::Windows::Foundation::IReference<winrt::Windows::Foundation::DateTime>>());
    // Solo una solicitud explícita con estado desconocido llega a este punto.
    // El SO aplica sus bloqueos al aviso. No se considera entregado por llamar Show.
    notificador.Show(aviso);
  };
  switch (nivora::ResolverPermisoWindows(solicitado, consultar, preparar)) {
    case nivora::PermisoWindows::concedido: return "CONCEDIDO";
    case nivora::PermisoWindows::denegado: return "DENEGADO";
    case nivora::PermisoWindows::pendiente: return "PENDIENTE";
  }
  winrt::throw_hresult(E_UNEXPECTED);
}

void InformarError(flutter::MethodResult<flutter::EncodableValue>* resultado,
    const char* etapa, HRESULT codigo) {
  char hexadecimal[11] = {};
  std::snprintf(hexadecimal, sizeof(hexadecimal), "0x%08lX", static_cast<unsigned long>(codigo));
  const std::string referencia = std::string(etapa) + "/" + hexadecimal;
  const std::string registro = "[Nivora notificaciones] " + referencia + "\n";
  std::fputs(registro.c_str(), stderr);
  OutputDebugStringA(registro.c_str());
  resultado->Error("WINDOWS_NOTIFICACIONES", "Windows no pudo completar la operación.",
      flutter::EncodableValue(flutter::EncodableMap{
        {flutter::EncodableValue(std::string("etapa")), flutter::EncodableValue(std::string(etapa))},
        {flutter::EncodableValue(std::string("hresult")), flutter::EncodableValue(std::string(hexadecimal))},
      }));
}
}  // namespace

void PrepararIdentidadNotificaciones() {
  // Se llama al iniciar el proceso, antes de crear la ventana o el motor Flutter.
  estadoIdentidadProceso = SetCurrentProcessExplicitAppUserModelID(kAppId);
}

void RegistrarNotificacionesDispositivo(flutter::BinaryMessenger* messenger) {
  flutter::MethodChannel<flutter::EncodableValue> canal(
      messenger, "pe.nivora/notificaciones", &flutter::StandardMethodCodec::GetInstance());
  canal.SetMethodCallHandler([](const auto& llamada, auto resultado) {
    const char* etapa = "INICIAR_COM";
    try {
      ApartamentoCom apartamento;
      const auto& metodo = llamada.method_name();
      if (metodo == "identificar") {
        etapa = "IDENTIFICAR";
        resultado->Success(flutter::EncodableValue(Identificar()));
      } else if (metodo == "consultarPermiso" || metodo == "solicitarPermiso") {
        etapa = "IDENTIDAD_APLICACION";
        winrt::check_hresult(estadoIdentidadProceso);
        const bool solicitado = metodo == "solicitarPermiso";
        if (solicitado) {
          PrepararAcceso(etapa);
          RegistrarEmisor(etapa);
        }
        resultado->Success(flutter::EncodableValue(Permiso(etapa, solicitado)));
      } else if (metodo == "abrirAjustes") {
        etapa = "ABRIR_AJUSTES";
        const auto abierto = ShellExecuteW(nullptr, L"open", L"ms-settings:notifications", nullptr, nullptr, SW_SHOWNORMAL);
        if (reinterpret_cast<INT_PTR>(abierto) <= 32) winrt::throw_hresult(E_FAIL);
        resultado->Success();
      } else {
        resultado->NotImplemented();
      }
    } catch (const winrt::hresult_error& error) {
      InformarError(resultado.get(), etapa, static_cast<HRESULT>(error.code()));
    } catch (...) {
      InformarError(resultado.get(), etapa, E_FAIL);
    }
  });
}
