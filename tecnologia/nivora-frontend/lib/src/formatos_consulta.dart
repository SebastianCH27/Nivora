String fechaLima(DateTime utc) {
  final fecha = utc.toUtc().subtract(const Duration(hours: 5));
  String dos(int n) => n.toString().padLeft(2, '0');
  return '${dos(fecha.day)}/${dos(fecha.month)}/${fecha.year} '
      '${dos(fecha.hour)}:${dos(fecha.minute)} (Lima)';
}

String nombreMes(String periodo) {
  const meses = [
    'Enero',
    'Febrero',
    'Marzo',
    'Abril',
    'Mayo',
    'Junio',
    'Julio',
    'Agosto',
    'Septiembre',
    'Octubre',
    'Noviembre',
    'Diciembre',
  ];
  return '${meses[int.parse(periodo.substring(5)) - 1]} ${periodo.substring(0, 4)}';
}

String soles(String importe) {
  final partes = importe.split('.');
  return 'S/ ${partes[0]}.${partes.length == 1 ? '00' : partes[1].padRight(2, '0')}';
}

String fechaCalendario(DateTime fecha) =>
    '${fecha.day.toString().padLeft(2, '0')}/'
    '${fecha.month.toString().padLeft(2, '0')}/${fecha.year}';
