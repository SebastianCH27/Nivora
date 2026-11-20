import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_prueba.dart';

void main() {
  testWidgets('valida ambos campos antes de consultar el backend', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await tester.pumpWidget(NivoraApp(api: api));
    await tester.ensureVisible(find.text('Ingresar'));
    await tester.tap(find.text('Ingresar'));
    await tester.pumpAndSettle();
    expect(find.text('Ingresa entre 1 y 20 dígitos.'), findsOneWidget);
    expect(
      find.text('Ingresa un DNI de exactamente 8 dígitos.'),
      findsOneWidget,
    );
    expect(api.ingresos, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'acceso real del formulario, consumo parcial y cierre de sesión',
    (tester) async {
      final api = ServicioPrueba();
      await tester.pumpWidget(NivoraApp(api: api));
      await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
      await tester.enterText(find.byType(TextFormField).at(1), '01234567');
      await tester.ensureVisible(find.text('Ingresar'));
      await tester.tap(find.text('Ingresar'));
      await tester.pumpAndSettle();
      expect(find.text('Hola, Persona de prueba'), findsOneWidget);
      expect(find.text('1,250 m³'), findsOneWidget);
      expect(find.text('Lecturas parciales'), findsOneWidget);
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      expect(find.text('Cerraste tu sesión.'), findsOneWidget);
      expect(find.byType(TextFormField), findsNWidgets(2));
      expect(
        (tester.widget<TextFormField>(find.byType(TextFormField).at(1)))
            .controller!
            .text,
        isEmpty,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets('se adapta a ${tamano.width.toInt()} px sin desbordamientos', (
      tester,
    ) async {
      tester.view.physicalSize = tamano;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(NivoraApp(api: ServicioPrueba()));
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Ingresar'));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
