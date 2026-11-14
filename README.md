# RutaUTP Android

Aplicación Kotlin y Jetpack Compose adaptada de RutaUTP para iOS.
La referencia de esta actualización es main, commit 8145276 del 09/11/2026.

- [Comparación y límites de la adaptación](docs/PARIDAD_IOS.md)
- [Plan de actualización por fases](docs/PLAN_PARIDAD_IOS_ANDROID.md)
- [Fase 1: referencia y comprobaciones automáticas](docs/FASE_1_REFERENCIA_Y_VALIDACION.md)
- [Fase 2: reglas de rutas y llegadas](docs/FASE_2_RUTAS_Y_LLEGADAS.md)
- [Fase 3: continuidad del viaje y contexto](docs/FASE_3_CONTINUIDAD_Y_CONTEXTO.md)
- [Fase 4: mapas, búsqueda y conexión por teléfono](docs/FASE_4_MAPAS_BUSQUEDA_Y_CONEXION.md)
- [Fase 5: idiomas, accesibilidad y errores](docs/FASE_5_IDIOMAS_ACCESIBILIDAD_Y_ERRORES.md)
- [Fase 6: presentación y transportes 3D](docs/FASE_6_PRESENTACION_Y_TRANSPORTES_3D.md)
- [Fase 7: integración de notificaciones remotas preparada](docs/FASE_7_NOTIFICACIONES_REMOTAS.md)
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
`python tools/verificar_idiomas.py` revisa traducciones y plantillas usadas,
incluidos los textos propios de Android y los recursos bilingües.
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

El SDK de mapas usa `MAPS_API_KEY`, resuelta desde una propiedad Gradle,
una variable de entorno o `local.properties`, en ese orden. Places y Directions
usan una clave web separada, guardada desde Perfil → Conexión de transporte.
Las restricciones y los servicios habilitados en Google Cloud deben comprobarse
con las identidades de firma de cada distribución; ver la guía de fase 4.
Las rutas GTFS, clips de señas y mallas 3D son recursos locales.
El feed institucional gtfs-utp aún no contiene rutas, igual que el origen iOS.

Las cuentas MQTT se introducen por teléfono en Perfil → Conexión de transporte
y se guardan cifradas con Android Keystore. El equipo debe asignar cuentas
distintas a cada instalación y roles distintos para lectura y contribución.
El APK solo admite valores públicos de canal (`MQTT_HOST`, `MQTT_PORT`,
`MQTT_TLS` y `MQTT_CA_ASSET`); usuario y contraseña de compilación ya no se usan.
Sin cuenta contribuyente ni consentimiento no se publica ubicación. El backend
y sus permisos de broker se administran en el repositorio compartido iOS.

No hace falta Firebase para compilar ni para programar recordatorios locales.
Para avisos remotos se necesita un proyecto Firebase, su google-services.json
y aplicar la integración FCM preparada al backend conforme a la guía de fase 7.
La configuración y la entrega real siguen pendientes. Pagos, autenticación y comunidad
siguen teniendo los límites de demostración del proyecto original.
