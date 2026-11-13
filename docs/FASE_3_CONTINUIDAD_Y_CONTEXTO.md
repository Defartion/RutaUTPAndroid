# Fase 3 — Continuidad del viaje y contexto

Referencia: main iOS `8145276bb6fc72a168cc78119def2727578ec0bb`.
Inicio: 10 de noviembre de 2026.
Validación local completada: 13 de noviembre de 2026.
Estado: **implementada y verificada localmente**; comprobación en dispositivo pendiente.

La fase 2 se conservó en el commit local **bcab4e3** antes de empezar.
Al retomar y cerrar la validación el 13 de noviembre, las ramas main remotas
seguían en iOS `8145276` y Android `7ba57cb`.
No se consultaron otras ramas. La validación se completó antes de publicar esta entrega.

## Búsquedas y selección — F03

El mapa asigna una revisión a la selección y cancela búsquedas, sugerencias,
líneas cercanas, cámara, itinerario y caminatas de alternativas cuando cambia
el contexto. Una respuesta solo se aplica si su revisión sigue vigente.
Seleccionar B o limpiar invalida A aunque el servicio termine después.
También se invalida al cambiar sede. Una selección existente se conserva
al cambiar la referencia; sus cálculos se vuelven a resolver.

Las dependencias de consulta y cámara se pueden controlar desde pruebas.
Estas ejecutan el MapaViewModel real y verifican el destino publicado y los
movimientos solicitados a la cámara, sin red ni Google Maps inicializado.

## Cambio de catálogo — F04 y preparación F15

La raíz conserva un almacén de modelos por contexto. Volver a una pestaña o
recrear la actividad reutiliza los modelos. Cambiar entre urbano y Buses UTP
retira el almacén anterior, cancela su trabajo y crea otro. Se descartan
las selecciones y solicitudes pendientes del router y se reinicia el
contexto de contribución pasiva. No se revoca el consentimiento guardado.

El catálogo institucional ausente produce líneas y flota vacías; no abre
un proveedor real. Cuando exista ese catálogo, la flota institucional solo
aceptará vehículos cuyo route_id esté en él.

La lectura del GTFS institucional añade `utp:` a agency_id, route_id,
trip_id, shape_id, stop_id y fare_id en todas las tablas que los contienen.
Conserva las relaciones, los campos vacíos, textos, tarifas y geometría.
Las referencias urbanas existentes mantienen sus identificadores.
No se activa ni inventa un feed institucional: datos, identificadores MQTT
y contrato del backend deben adoptar el mismo espacio antes de publicarlo.

## Viajes y notificaciones — F05

Un registro común contempla viajes declarados, tracking puerta a puerta y
navegación sobre una línea. Ocultar una pantalla o pausar la app no termina
el viaje. Finalizarlo o retirar su contexto elimina el registro correspondiente.
Llegar al destino conserva la protección mientras permanezca abierto el
resumen del viaje o la navegación de línea, igual que iOS.

Las acciones de avisos se identifican, encolan y consumen una vez. Se
comprueba vigencia y existencia del destino o línea. El estado de viaje se
consulta después de las lecturas asíncronas, para detectar un viaje iniciado
mientras cargaban los datos. Un aviso inválido o un viaje activo conserva
la pantalla y el destino. Las acciones consumidas se guardan en SavedStateHandle.
Los extras del intent se retiran después de encolar la acción, evitando
repetirla al recrear la actividad. Avisos antiguos sin identidad verificable
muestran información no disponible.

Los avisos locales identifican el recordatorio o agenda que los originó.
Se comprueba que esa configuración y su destino sigan vigentes, incluso
si el lugar anterior todavía existe. No se añade una caducidad temporal
arbitraria a los locales; los remotos conservan su vencimiento del servidor.

El aviso diario presenta una hoja con el título, consejo y límite de
cupones de demostración de iOS. Durante un viaje se puede leer y cerrar,
pero no abre otra pantalla. El botón vuelve a consultar el registro al
tocarlo: también queda protegido si el viaje comenzó con la hoja abierta.
La opción de cambios de ruta abre la consulta de reportes actuales.

## Pestañas, recreación y pausa — F10

La raíz mantiene estado guardable independiente por pestaña y elimina la
composición de la pantalla oculta. Guardado conserva subpestaña y una posición
de lista independiente para Lugares y Líneas. Rutas conserva su búsqueda,
filtro, detalle y navegación; la lista mantiene su posición al abrir detalle.
Seguridad conserva Ayuda/Comunidad. Tracking y navegación de línea pertenecen
a modelos retenidos por la actividad, con progreso y sesión fuera de la
composición. Atrás vuelve al mapa desde pestañas y cierra la navegación de
línea por su acción de finalización.

Salir de UTP descarta la preferencia institucional oculta, para que volver
al campus no reactive automáticamente el modo anterior.

La demostración de navegación distingue reanudar su avance de iniciarla
de nuevo desde el comienzo.

GPS, animación y reloj de Comunidad se pausan al ocultar la pantalla o dejar
la app. Reanudar no crea un viaje nuevo. La inicialización de la contribución
pasiva evita duplicar sus colectores al recrear la actividad.

La continuidad descrita cubre pestañas, segundo plano y recreación con el
proceso vivo. No se restaura un viaje real después de que Android mate el
proceso; no se inventan posiciones ni sesiones de contribución persistidas.

## Validación

Pruebas aprobadas: **180 Android y 18 del comprobador**, sin fallos ni
pruebas omitidas. La prueba de navegación valida tanto reanudar el avance
como reiniciar la demostración desde el comienzo.

La compilación, generación del APK y análisis estático terminaron correctamente.
El análisis registra **cero errores, 79 advertencias y 16 sugerencias**.
Se verificaron los **71 recursos compartidos**, las fuentes del checkout iOS
y la ejecución de las 27 referencias. El APK conserva las seis mallas 3D,
los catálogos y las pantallas que comprueba la herramienta.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `d6786c1dbf490ca7a7db0168c94801f2a7e0a4dd76375e8acd9ef0f603de6e15`.

| Referencias y regresiones | Casos |
|---|---:|
| ETA de iOS | 11 |
| Comparación de rutas de iOS | 8 |
| Continuidad de iOS | 8 |
| Regresiones adicionales de fase 3 | 26 |
| Pruebas Android totales | 180 |

Los 27 escenarios de referencia iniciales están activados y ejecutados.
La comprobación exige su clase de prueba y su identificador JUnit, además
del total mínimo; no basta con incrementar el conteo.

Los ocho escenarios STATE-01 a STATE-08 usan los modelos y router de la app,
no un reductor paralelo creado únicamente para aprobar los ejemplos.
Se añaden regresiones de cola, caducidad, repetición, registro de viajes,
retiro de modelos y relaciones de IDs institucionales homónimos.

Reproducción en Windows con Java 21 y Android SDK disponibles:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --continue --max-workers=1 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
python -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verificar_paridad.py --require-results
python tools/verificar_apk.py
```

La última pasada usa el compilador dentro del proceso de Gradle para evitar
el servicio Kotlin que no respondió en la ejecución anterior. No cambia
las reglas de la app ni excluye pruebas o comprobaciones.

El modo offline requiere las dependencias en caché. Para cotejar además el
checkout iOS, añadir `--ios` y su carpeta al comprobador de paridad.
Los resultados quedan en app/build/test-results y app/build/reports.
El workflow de GitHub se ejecutará al publicar los commits en main.

## Comprobación pendiente en dispositivo

- Girar en Tracking y en navegación de línea; comparar destino, progreso y sesión.
- Cambiar entre pestañas y verificar posición de lista, detalle y subpestaña.
- Dejar la app y comprobar que GPS y animaciones se pausan y reanudan una vez.
- Abrir recordatorio, cambio de ruta y aviso diario durante cada tipo de viaje.
- Repetir urbano → Buses UTP → urbano con un canal configurado y posiciones recibidas.
- Comparar presentación con iOS. El trabajo visual transversal continúa en fase 6.

El 13 de noviembre tampoco había teléfonos ni emuladores conectados al
ejecutar `adb devices`. Las pruebas unitarias de modelos no sustituyen
la comprobación de interacción y rotación sobre la aplicación instalada.

La integración de notificaciones remotas y backend común sigue en fase 7.
La separación institucional necesita validación integral cuando exista el feed.

La fase 3 se entrega en un commit independiente. F15 mantiene seguimiento
abierto para coordinar el feed
y el backend; su preparación de identificadores prevista para esta fase
está implementada y probada.
