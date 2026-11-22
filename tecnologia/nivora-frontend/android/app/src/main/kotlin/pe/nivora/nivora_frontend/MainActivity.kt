package pe.nivora.nivora_frontend

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.UUID

class MainActivity : FlutterActivity() {
    private var solicitud: MethodChannel.Result? = null
    private val codigoPermiso = 7701

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "pe.nivora/notificaciones")
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "identificar" -> {
                            // noBackupFilesDir evita copiar el UUID a otra instalación al restaurar.
                            val archivo = File(noBackupFilesDir, "notificaciones-instalacion")
                            val id = if (archivo.exists()) archivo.readText() else {
                                val nuevo = UUID.randomUUID().toString()
                                val temporal = File(noBackupFilesDir, "notificaciones-instalacion.tmp")
                                temporal.writeText(nuevo)
                                check(temporal.renameTo(archivo))
                                nuevo
                            }
                            check(UUID.fromString(id).toString() == id)
                            result.success(id)
                        }
                        "consultarPermiso" -> result.success(permiso())
                        "solicitarPermiso" -> {
                            if (solicitud != null) {
                                result.error("OCUPADO", "Hay una solicitud pendiente.", null)
                            } else if (Build.VERSION.SDK_INT >= 33 && permiso() != "CONCEDIDO") {
                                solicitud = result
                                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), codigoPermiso)
                            } else {
                                result.success(permiso())
                            }
                        }
                        "abrirAjustes" -> {
                            val intent = if (Build.VERSION.SDK_INT >= 26) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:$packageName"))
                            }
                            startActivity(intent)
                            result.success(null)
                        }
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    solicitud = null
                    result.error("DISPOSITIVO", "No se pudo consultar el dispositivo.", null)
                }
            }
    }

    private fun guardarDecision(valor: String) {
        val archivo = File(noBackupFilesDir, "notificaciones-decision")
        if (!archivo.exists() || archivo.readText() != valor) {
            val temporal = File(noBackupFilesDir, "notificaciones-decision.tmp")
            temporal.writeText(valor)
            check(temporal.renameTo(archivo))
        }
    }

    private fun permiso(): String {
        val habilitadas = if (Build.VERSION.SDK_INT >= 24) {
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        } else true
        if (habilitadas) {
            guardarDecision("CONCEDIDO")
            return "CONCEDIDO"
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // Solo APIs públicas: rationale identifica una denegación previa.
            // Antes de decidir y tras cancelar el diálogo, permanece PENDIENTE.
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                guardarDecision("DENEGADO")
                return "DENEGADO"
            }
            if (!File(noBackupFilesDir, "notificaciones-decision").exists()) {
                return "PENDIENTE"
            }
        }
        return "DENEGADO"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == codigoPermiso) {
            val resultado = solicitud
            solicitud = null
            try { resultado?.success(permiso()) }
            catch (e: Exception) { resultado?.error("PERMISO", "No se pudo consultar el permiso.", null) }
        }
    }

    override fun onDestroy() {
        solicitud?.error("CANCELADA", "La solicitud se interrumpió.", null)
        solicitud = null
        super.onDestroy()
    }
}
