import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

import 'src/api_nivora.dart';
import 'src/aplicacion.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  const direccion = String.fromEnvironment(
    'NIVORA_API_URL',
    defaultValue: 'http://127.0.0.1:8082',
  );
  runApp(
    NivoraApp(
      api: ApiNivora(Uri.tryParse(direccion) ?? Uri(), desarrollo: kDebugMode),
    ),
  );
}
