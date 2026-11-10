# RutaUTP Android

Aplicación Kotlin y Jetpack Compose adaptada de RutaUTP para iOS.
La referencia de esta actualización es main, commit 8145276 del 09/11/2026.

- [Comparación y límites de la adaptación](docs/PARIDAD_IOS.md)
- [Configurar avisos remotos con Firebase](docs/FIREBASE_ANDROID.md)
- [Almacenes y claves locales](PERSISTENCIA.md)
- [Herramientas de desarrollo](tools/README.md)
- [Atribuciones](ThirdPartyNotices/README.md)

## Abrir y comprobar

Requiere Android Studio compatible con AGP 9.2.1, JDK 21 y Android SDK 36.
El mínimo de instalación es Android 8 (API 26).

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

El APK de desarrollo queda en `app/build/outputs/apk/debug/app-debug.apk`.
Después de generarlo, `python tools/verificar_apk.py` comprueba los recursos
que realmente quedaron empaquetados para instalar.
Los tests unitarios no necesitan teléfono. Cámara, permisos, GPS, avisos del
sistema y renderizado 3D deben revisarse también en un dispositivo Android.

## Probar desde Android Studio

Abrir esta carpeta, sincronizar Gradle, seleccionar la configuración `app`
y un teléfono o emulador, y pulsar Run. La versión de esta revisión es **1.2 (3)**;
se puede comprobar en el menú del mapa → Sobre nosotros.
Ver [dónde aparecen los cambios](docs/COMPROBAR_ACTUALIZACION.md).

Rutas tiene buscador fijo y tarjetas con letra, empresa y extremos reales del
recorrido. El detalle incluye mapa ampliable y modelo 3D de la línea. Perfil
se organiza en identidad, billetera y grupos de ajustes; Guardado separa
lugares y paraderos; Seguridad separa Ayuda y Comunidad.

## Servicios externos

Maps, Places y Directions usan la configuración Google Maps del manifiesto.
Las rutas GTFS, clips de señas y mallas 3D son recursos locales.
El feed institucional gtfs-utp aún no contiene rutas, igual que el origen iOS.

MQTT se configura mediante local.properties (ignorado por Git) o propiedades
Gradle: MQTT_HOST, MQTT_PORT, MQTT_USERNAME, MQTT_PASSWORD, MQTT_TLS,
MQTT_CA_ASSET y MQTT_READ_ONLY. Sin configuración no se publica ubicación.
Las credenciales de la app deben pertenecer a su propia instalación. El backend
y sus permisos de broker se administran en el repositorio compartido iOS.

No hace falta Firebase para compilar ni para programar recordatorios locales.
Para avisos remotos se necesita un proyecto Firebase, su google-services.json
y adaptar el backend a FCM conforme a la guía. Pagos, autenticación y comunidad
siguen teniendo los límites de demostración del proyecto original.
