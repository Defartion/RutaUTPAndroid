# Fase 5: idiomas, accesibilidad y errores

Fecha de trabajo: **13 de noviembre de 2026**. Hallazgos F11 y F12.
La implementación y las comprobaciones locales se entregan separadas de la
validación pendiente con TalkBack, teléfonos y hablantes de cada idioma.

Antes de iniciar esta fase se guardó la fase 4 en el commit **21cc604** de
main. No se realizó push en esta entrega. La referencia iOS continúa siendo
main, `8145276bb6fc72a168cc78119def2727578ec0bb`, verificada sin cambios.
No se revisaron otras ramas.

## Idiomas fieles al contexto de iOS

Se conserva el catálogo compartido de 1958 entradas y se añade
`assets/traducciones-android.json`. El complemento contiene **376 entradas**
propias de Android y **49 alias** hacia claves existentes, con traducciones
en inglés, francés, portugués de Brasil, alemán y chino simplificado.
El español sigue siendo la clave y el texto de origen. Los alias permiten
reutilizar frases compartidas sin modificar el recurso fijado de iOS.

Se convirtieron 27 llamadas con interpolación directa a plantillas con
parámetros; se corrigieron además una frase concatenada del planificador
y la distancia respecto del usuario. Los valores se insertan después de
traducir la plantilla, sin convertir cantidades o nombres concretos en claves.
El comprobador rechaza claves ausentes, idiomas vacíos, parámetros diferentes,
argumentos faltantes e interpolaciones directas.

Las categorías de lugares, reportes y negocios, las pestañas y las descripciones
de zonas se resuelven al mostrarlas. Comunidad utiliza plantillas para la
antigüedad de las publicaciones. Las fechas de cupones utilizan la configuración
regional del idioma seleccionado. Places y Directions envían el idioma activo;
las caminatas separan su caché también por idioma.

La selección se aplica sin reconstruir el router ni los modelos del viaje.
Las escrituras de preferencia se serializan y una lectura antigua no revierte
una selección más reciente. Cambiar de idioma cancela una seña pendiente.
Una clave inesperada puede usar su respaldo inglés; las claves conocidas
revisadas deben tener traducción, por lo que ese respaldo no sirve para aprobar
una carencia en la comprobación automática.

El inventario revisa **1035 claves literales distintas** y **1299 claves distintas
en total**, incluyendo **757 pares bilingües de recursos** y **44 publicaciones
de demostración**. Son conteos de claves y usos comprobados, no porcentajes de
pantallas. Cuatro llamadas dinámicas están identificadas explícitamente:
contenido comunitario, texto bilingüe de recursos, envoltorio de señas y colores
de referencia. Los datos conocidos se comprueban en sus fuentes; el contenido
introducido por una persona conserva su idioma original.

No se ha realizado una revisión lingüística por hablantes nativos. Las
traducciones añadidas necesitan esa revisión editorial antes de distribuir
una versión final.

## Catálogo vacío, pendiente o ilegible

La carga devuelve un resultado explícito: catálogo disponible, feed institucional
pendiente o error. Un catálogo disponible puede estar vacío de forma válida.
Cada carpeta mantiene su estado, y un error no se memoriza como éxito vacío.
Las llamadas concurrentes comparten la lectura; cerrar una pantalla no cancela
la carga utilizada por otros consumidores.

Modo offline consume ese resultado y ofrece **Reintentar** ante un fallo de
lectura o parseo. Conserva los mensajes separados para rutas UTP pendientes
y catálogo vacío. Cambiar de modo invalida la comprobación de la hoja anterior.
Se mantiene el aviso de que los datos GTFS son locales y Google Maps requiere
red para búsquedas e indicaciones; no se promete navegación completa offline.

## TalkBack, texto grande y permisos

La barra inferior conserva roles y selección de pestaña, permite varias líneas,
usa texto de 12 sp y mantiene una altura mínima de 64 dp por elemento. Se
eliminó la reducción de opacidad del texto inactivo. Las opciones de idioma y
parentesco permiten desplazamiento, y varios selectores y controles de 3D
se distribuyen en filas flexibles para admitir etiquetas largas.

El selector de punto del mapa ofrece acciones de accesibilidad para moverlo
al norte, sur, este y oeste, además de confirmarlo. El cierre de señas tiene
un área de 48 dp y el aviso de clip pendiente reserva espacio para ese control.
Los iconos decorativos siguen sin descripción redundante cuando su tarjeta
o botón ya aporta el texto. Las áreas táctiles y acciones siguen las guías
de [Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
y [semántica de accesibilidad](https://developer.android.com/develop/ui/compose/accessibility/semantics).

La revisión estática de los tokens sólidos de navegación y texto registra
60 combinaciones: diez temáticas, claro/oscuro y tres roles de texto.
El contraste mínimo calculado es **4,991:1**, superior a 4,5:1 para esos pares.
El informe local es `app/build/reports/parity/contrast-check.json`.
Esta revisión no evalúa todos los fondos, transparencias, mapas, fotografías
o estados de la app, ni constituye una certificación de conformidad.
Se usa como referencia el criterio de
[contraste de W3C](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

La contribución pide juntas ubicación precisa y aproximada, además de actividad
cuando corresponda. Rechazar ubicación deja la contribución desactivada y
muestra cómo revisar el permiso. La ubicación aproximada y la actividad
denegada explican las limitaciones de detección. Cámara y notificaciones
conservan sus acciones de ajustes y ahora cuentan con sus textos traducidos.
La matriz completa de permisos sigue pendiente en Android instalado.

## Señas y acciones pendientes

El presentador usa una generación por presentación. Una resolución tardía de
la seña A no habilita la acción de B. Salir, pasar a segundo plano, desactivar
el modo o cambiar de idioma cancela la acción sin ejecutarla. El cierre de
una tarjeta ya resuelta y el temporizador ejecutan la acción una sola vez;
pulsar dos veces no reinicia la espera.

La resolución del clip utiliza el idioma elegido y los archivos realmente
disponibles. Francés, portugués, alemán y chino siguen sin clips, igual que
la referencia compartida: muestran el aviso de contenido pendiente. No se
sustituyen silenciosamente por español. La ventana de señas conserva su
presentación sobre hojas y diálogos; hay que verificar el apilamiento y la
reproducción física en dispositivo.

## Comprobaciones pendientes en dispositivo

1. Recorrer las cinco pestañas, formularios, mapa y visor 3D con TalkBack:
   orden del foco, agrupación, selección, acciones y ausencia de anuncios duplicados.
2. Ejecutar las dos pruebas nuevas de navegación con texto al 150 % y 200 %
   y revisar visualmente formularios, hojas y pantallas estrechas en los seis idiomas.
3. Comparar claro/oscuro, estados desactivados, fondos de mapa y fotografías;
   completar el contraste de todos los controles que no cubren los tokens revisados.
4. Denegar cámara, ubicación precisa/aproximada, actividad y notificaciones;
   probar también denegación permanente y regreso desde Ajustes.
5. Abrir señas sobre hojas y diálogos, cambiar de idioma y pasar a segundo plano;
   comprobar reproducción y aviso de clip ausente.
6. Revisar lingüísticamente las traducciones y comparar las mismas pantallas con iOS.

La fidelidad visual transversal continúa en fase 6. Permanecen abiertos los
pendientes de Google Cloud, broker y dos teléfonos de fase 4, y la integración
remota prevista para fase 7.

## Resultados y reproducción

| Comprobación local | Resultado |
|---|---|
| Pruebas unitarias Android | **235**, sin fallos, errores ni omisiones |
| Regresiones nuevas | 23: ocho de idioma/continuidad, siete de carga y ocho de señas |
| Pruebas de herramientas | **35** aprobadas |
| Referencias iOS ejecutadas | 11 ETA, ocho rutas y ocho continuidad |
| Recursos y fuentes de referencia | 71 recursos y 12 fuentes iOS cotejados |
| Idiomas | 1299 claves distintas revisadas; cero carencias detectadas |
| Compilación | APK debug y APK de pruebas instrumentales generados |
| Análisis Android | Cero errores, 82 advertencias y 16 sugerencias |
| APK instalable | Seis mallas, catálogos exactos —incluido el complemento Android— y componentes de esta fase comprobados |
| Configuración heredada de fase 4 | Clave del SDK presente y campos MQTT de credenciales excluidos |

Las dos pruebas instrumentales nuevas montan únicamente la barra inferior a
320 dp, con texto al 150 % y 200 %, y comprueban visibilidad, acciones, área
táctil y selección. **Se compilaron, pero no se ejecutaron**: `adb devices`
no encontró teléfonos ni emuladores en la validación final del 13/11/2026.
El resultado de las pruebas unitarias no acredita comportamiento físico de
TalkBack, cámara, GPS, OpenGL o video.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `f02bdb99f9b7f6d635d1d1248a00518d77c014844e5771500838b6126d655898`.
Los informes quedan en `app/build/reports/parity/` y los resultados JUnit en
`app/build/test-results/testDebugUnitTest/`.

Con Java 21 y Android SDK 36 disponibles:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --continue --max-workers=1 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
python -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verificar_idiomas.py
python tools/verificar_paridad.py --require-results
python tools/verificar_apk.py
python tools/verificar_configuracion.py
```

Offline requiere dependencias ya descargadas. Para cotejar el checkout iOS,
añadir `--ios RUTA_CHECKOUT_IOS` al comprobador de paridad.
El workflow incluye ahora la revisión de idiomas; sus cambios de fase 4 y 5
siguen sin publicarse, por lo que no se atribuye una ejecución remota a esta
entrega. La fase 5 se guarda en un commit independiente, todavía sin publicar.
