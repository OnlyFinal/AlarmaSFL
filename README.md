# Alarma SFL para Android

Proyecto Android Studio (Kotlin) que convierte la lógica de Alarma SFL en una app y un widget de pantalla de inicio.

## Incluye
- Widget con Pepper y Blueberry, precios, gráficas de historial y estado de límites.
- Configuración de mercado Plaza (P2P) o Sequence.
- Límites de compra y venta editables desde la app.
- Notificaciones locales al alcanzar los límites.
- Historial local de hasta 300 registros por activo.
- Consultas periódicas con WorkManager cuando hay conexión.

## Cómo abrir
1. Descomprime el ZIP.
2. Abre Android Studio y selecciona **Open**.
3. Selecciona la carpeta `AlarmaSFL_Android`.
4. Espera a que Gradle sincronice.
5. Ejecuta la app en tu teléfono Android.
6. Configura límites y mercado.
7. Mantén pulsada la pantalla de inicio → Widgets → Alarma SFL y coloca el widget.

## Limitaciones importantes
- Android/WorkManager no garantiza una ejecución exacta cada 15 minutos; Doze, ahorro de batería y fabricantes pueden retrasarla.
- La frecuencia mínima estándar para trabajo periódico de WorkManager es 15 minutos. No equivale a la consulta de 5 minutos del HTML cuando la página está abierta.
- Las notificaciones requieren permiso en Android 13 o posterior.
- El acceso a la API depende de que `https://sfl.world/api/v1/prices` esté disponible desde la red del teléfono y conserve el formato de datos esperado.
- Este proyecto es código fuente para compilar en Android Studio; no es un APK ya compilado.
- Antes de usarlo para decisiones financieras, verifica los precios en la fuente oficial. No se garantiza que las alertas lleguen puntualmente.

## Próximas mejoras recomendadas
- Verificar el JSON real de la API y ajustar el lector si su estructura cambió.
- Añadir botón de actualización manual en el widget mediante un receiver.
- Añadir un servicio foreground opcional si necesitas consultas más frecuentes, teniendo en cuenta consumo de batería y restricciones de Android.
