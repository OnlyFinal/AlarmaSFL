# Alarma SFL para Android

Proyecto Android Studio (Kotlin) que convierte la lógica de Alarma SFL en una app y un widget de pantalla de inicio.

## Incluye
- Widget con Pepper y Blueberry, precios, gráficas de historial y estado de límites.
- Configuración de mercado Plaza (P2P) o Sequence.
- Límites de compra y venta editables desde la app.
- Notificaciones locales al alcanzar los límites.
- Historial local de hasta 3000 registros por activo.
- Consultas periódicas con WorkManager cuando hay conexión, más actualización manual desde la aplicación.

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
- Confirmar el formato JSON actual de la API en una ejecución real; el lector ya contempla búsquedas recursivas y valores numéricos o cadenas numéricas.
- Añadir botón de actualización manual en el widget mediante un receiver.
- Añadir un servicio foreground opcional si necesitas consultas más frecuentes, teniendo en cuenta consumo de batería y restricciones de Android.

## Cambios de esta revisión (guiada por `alarma-sfl-1.html`)
- La consulta prueba primero la API directa y después las mismas tres rutas proxy alternativas del HTML, recordando la última ruta que funcionó.
- El análisis de mercado/producto busca claves sin distinguir mayúsculas y minúsculas, admite valores numéricos o cadenas numéricas y busca dentro de objetos y arreglos.
- `updated` solo se modifica cuando se ha obtenido al menos un precio válido; los fallos quedan en `last_error`.
- El historial se amplió a 3000 puntos por producto, con el mismo criterio temporal de muestreo aproximado de 14 minutos.
- Se añadió actualización manual inmediata desde la aplicación.
- La aplicación muestra los precios almacenados, la variación desde el primer registro y el estado de la última consulta.
- Las gráficas del widget incluyen las líneas de los límites de compra y venta.

## Verificación pendiente
Esta revisión se realizó sobre el código fuente. En este entorno no está instalado Gradle ni se dispone de una compilación Android para ejecutar pruebas de integración. Por tanto, abre el proyecto en Android Studio, sincroniza Gradle y compila antes de instalarlo. La respuesta actual de la API debe verificarse desde el dispositivo, ya que puede variar por disponibilidad de red o cambios del servidor.
