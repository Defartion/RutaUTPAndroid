# Comparación Android / iOS

Referencia consultada: [RutaUTP main, commit 8145276](https://github.com/JDiazD33/RutaUTP/tree/8145276bb6fc72a168cc78119def2727578ec0bb),
del 9 de noviembre de 2026. Comparación realizada el 9 de noviembre de 2026.
Se usó el contenido actual de este checkout Android, incluidos los archivos
que ya estaban sin seguimiento por Git antes de comenzar. No se borraron esos
archivos.

## Cambios portados

| Área | Adaptación Android |
|---|---|
| Rutas y detalle | Buscador fijo, acceso a galería 3D, tarjetas con letra y extremos GTFS reales, ficha de identidad y estadísticas, visor integrado y navegación. |
| Explorador de ruta | Mapa completo con encuadre real de geometría/paraderos, marcadores, ubicación con permiso nativo, seguimiento, leyenda y lista de paraderos. |
| Perfil | Identidad, billetera, credenciales visibles por empresa y grupos Preferencias/Accesibilidad/Privacidad. Foto del carné físico independiente de la foto de perfil del carné digital de muestra. |
| Guardado | Pestañas con iconos, lugares y paraderos separados, tarjetas de líneas compartidas con Rutas y acceso al catálogo completo de paraderos. |
| Seguridad visible | Saludo y reloj, Ayuda/Comunidad, contacto y emergencias, reportes y avisos vigentes de líneas guardadas/viaje activo. |
| Mapa visible | Cabecera y panel inferior plegable con controles, reporte, contador real y tarjetas de vehículos. |
| Datos | Rutas, tarifas, sedes, negocios, manifiesto de señas y catálogo de traducciones de main. |
| GTFS | BOM, CR/LF, comillas escapadas y registros CSV multilínea; encabezados requeridos, coordenadas válidas, fallos reintentables y distancia al segmento del recorrido. |
| Búsqueda | Índice por código, letra, operador y calles de paraderos; tildes, códigos con/sin guion, variantes con corazón y calificadores multilingües. |
| Itinerarios | Otras rutas, recomendada por cercanía de subida, más rápida, espera inicial y verificación de caminatas al seleccionar; viajes directos y con un transbordo. |
| Caminatas | Caché de éxitos de 5 minutos/128 entradas, solicitudes compartidas, plazo de 4 segundos y cancelación cuando ya no quedan consumidores. |
| Lugares | Categorías ampliadas, edición, frecuencia, iconos, ID de paradero, esFijo persistido y lectura de registros anteriores sin renombrar sus claves. |
| Seguridad | Paraderos guardados compartidos con Guardado y editor común de contacto de emergencia con teléfono alternativo e importación desde el selector de contactos. |
| Notificaciones locales | Permiso Android, prueba, recordatorio por destino/días/hora, pausas, silencios, sonido, hasta tres avisos diarios y reprogramación tras reinicio/cambio horario. |
| Notificaciones remotas | Cliente FCM, registro MQTT/TLS con revisión y recibo, validación/deduplicación de eventos y navegación. Activación externa pendiente; ver FIREBASE_ANDROID.md. |
| Idiomas | Español, inglés, francés, portugués de Brasil, alemán y chino simplificado. La falta de un clip en el idioma activo se indica sin sustituirlo por otro idioma. |
| Vehículos | Contratos MQTT v2/v3; procedencia observada/simulada; control de simulación con revisión, revocación, reloj monótono y checkpoint privado; no se confunde ocupación simulada con votos comunitarios. |
| Mapa | Límite persistido Todos/5/10/15/20 manteniendo visible el vehículo seleccionado. Retirada de posiciones obsoletas y de selecciones cuya identidad cambia. |
| Transportes 3D | Seis mallas originales de main convertidas desde USDZ; giro, zoom, desplazamiento y variante persistida. Carrocería pintada sin teñir ventanas/ruedas, bandas y cartel en las coordenadas del origen. Referencias de identidad, colores y procedencia de las 102 rutas. |
| Monedero / QR | Formularios de recarga y pago actualizados: saldo previsto, importes adaptables, billeteras con selección nativa, confirmación desplazable y escáner como primera acción. |
| Contrato de pago | Lectura completa TLV, rechazo de importes/monedas ambiguos, moneda PEN/604, reserva de una operación por captura y escrituras de saldo serializadas. Sigue siendo demostración. |
| Persistencia | Copia antes del restablecimiento solicitado, listado/exportación de respaldos, conservación de JSON ilegible y referencias de tarjetas sin degradar a almacenamiento plano. |
| Consentimiento | Explica la relación entre cuenta MQTT, sesión y ubicación; pausa de publicación en segundo plano. No promete anonimato. |

## Equivalencias de plataforma

SwiftUI → Jetpack Compose; MapKit → Google Maps/Places/Directions;
CoreLocation → Fused Location Provider; CoreMotion → Activity Recognition;
AVFoundation → CameraX/Media3; Keychain → almacenamiento cifrado Android;
UNUserNotificationCenter → NotificationManager/AlarmManager; APNs → Firebase
Cloud Messaging; SceneKit/USDZ → OpenGL ES y mallas convertidas de los mismos
recursos. Los modelos 3D y los catálogos funcionan sin descargarlos en ejecución.

Las alarmas locales son inexactas conforme a las restricciones de Android;
no se pide acceso especial a alarmas exactas. El sistema puede retrasarlas.
No se han introducido autenticación, pagos reales ni publicaciones comunitarias
en servidor: tampoco existen como servicios terminados en el origen.

## Archivos de presentación contrastados

Se volvió a revisar main el 9/11/2026, incluyendo la presentación que
faltaba en la revisión inicial de servicios y los commits nuevos de ese día. La comparación se hizo contra:

| Swift en `RutaUTP/Screens` | Kotlin en `ui/screens` |
|---|---|
| `Rutas/RutasView.swift`, `RutaOpcionCard.swift`, `DetalleRutaView.swift` | `RutasScreen.kt`, `ui/components/RutaOpcionCard.kt` |
| `DetalleRuta/ExploradorRutaView.swift` | `ExploradorRutaScreen.kt` |
| `Rutas/IdentificacionRuta3DView.swift`, `VisorTransporte3D.swift` | `Transportes3DScreen.kt`, `PinturaTransporte3D.kt` y metadatos extraídos del USDZ |
| `Perfil/PerfilView.swift`, `CarneDigitalView.swift` | `PerfilScreen.kt`, `CarneDigitalScreen.kt`, `VisorFotoCarnet.kt` |
| `Guardado/GuardadoView.swift` | `GuardadoPantalla.kt`, hojas de `GuardadoScreen.kt` |
| `Seguridad/SeguridadView.swift` | `SeguridadScreen.kt`, `SeguridadAyuda.kt` |
| `Mapa/MapaView.swift`, `NearbyTransportPanel.swift`, `BusCard.swift` | `mapa/MapaScreen.kt` |

## Verificación y límites

Validación del 9 de noviembre de 2026 con JDK 21 y el wrapper del proyecto:

- `:app:testDebugUnitTest`: 119 pruebas, cero fallos/errores. Incluyen
  representación de líneas, pintura e identificación sobre los seis recursos
  reales y relación ruta/viaje GTFS.
- `:app:assembleDebug`: APK de desarrollo generado correctamente.
- `:app:lintDebug`: cero errores; permanecen advertencias sobre dependencias,
  APIs y otras recomendaciones del analizador. No se desactivaron sus errores.
- Comprobación de sintaxis Python y XML, igualdad binaria de los catálogos
  actualizados frente a main y revisión de espacios con `git diff --check`.
- Adaptador FCM comprobado con un proveedor simulado: mensaje de datos,
  vigencia y rechazo de entradas inválidas. No se enviaron mensajes reales.
- Revisión posterior del APK: corregida la ruta de apertura de las mallas.
  AAPT descomprime `.mesh.gz` como `.mesh`; el visor abre este último nombre.
  `python tools/verificar_apk.py` comprueba las mallas empaquetadas, su igualdad
  con los originales y que el código no conserve el nombre de archivo ausente.
  La comprobación reproduce el fallo en el APK anterior y valida la corrección.

Las capturas reproducibles de Compose cubren Rutas y Perfil en claro/oscuro,
Rutas con texto al 150 %, recarga normal/texto grande y pago QR. Se revisan
como vistas previas nativas con datos de muestra; no son capturas de un teléfono.
Ver COMPROBAR_ACTUALIZACION.md para reproducirlas.

Las pruebas cubren parser CSV, búsqueda, itinerarios, dinero/QR, teléfonos,
agenda, control MQTT, recibos FCM, recursos 3D y catálogo. No había un teléfono/emulador
conectado en la comprobación de dispositivos. La compilación no acredita
renderizado OpenGL, permisos, cámara, GPS real, entrega FCM ni funcionamiento
en campo. Esas comprobaciones requieren un dispositivo Android.

La main iOS incluye herramientas y diagnóstico de su backend de simulación y
pruebas específicos de UIKit/Xcode; no se copian como si fuesen funcionalidades
Android. El receptor Android implementa su contrato de datos. El backend es
compartido y no se ha desplegado ni modificado el servicio remoto.
Los scripts de demostración heredados de este checkout usan un contrato antiguo;
no sirven para validar la flota v2/v3. Ver `tools/README.md`.

La presentación 3D conserva geometrías y zonas de carrocería del USDZ, usa las
fichas de color verificadas por identidad y las coordenadas de franjas/cartel
de iOS. OpenGL ES usa iluminación y texto nativos de Android; no reproduce
exactamente los materiales físicos de RealityKit. La revisión píxel a píxel y
los gestos sobre el visor requieren ambas aplicaciones en dispositivos.
