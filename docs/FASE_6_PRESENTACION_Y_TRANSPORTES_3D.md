# Fase 6: presentación y transportes 3D

Trabajo y validación local: **14 de noviembre de 2026**. La implementación
se contrasta con el código de main iOS. La comparación visual en dos dispositivos
y el funcionamiento físico de mapas y OpenGL siguen pendientes.

La fase 5 quedó guardada en **aedfc71**, en main y todavía sin push. La
referencia iOS continúa en 8145276bb6fc72a168cc78119def2727578ec0bb, comprobada
sin cambios el 14/11/2026. Solo se utilizaron las ramas main.

## Comparación aplicada

| Área | Referencia iOS | Ajuste Android |
|---|---|---|
| Navegación común | Cinco pestañas, cabeceras neutras y acento adaptado a claro/oscuro | Cabecera compartida en Rutas, Guardado y Seguridad. Se mantienen identidad y cambio inmediato de pestaña de fase 3. Con texto al 150 % o superior, las cinco acciones se distribuyen en dos filas. |
| Selector de sección | Icono, etiqueta, selección y línea inferior en Guardado y Seguridad | Componente compartido con rol de pestaña y distribución adaptable al texto grande. |
| Mapa | Controles estables sobre una fila reservada para tarjetas, carga, error o vacío | Panel con una altura mínima de 112 dp y estados diferenciados; el fallo permite reintentar el catálogo. Las respuestas antiguas no sustituyen el estado nuevo. |
| Tarjeta de vehículo | Empresa, línea, llegada, tipo/ramal y franja de color | Se mantiene el ancho de referencia de 256 dp; las cápsulas usan pares de color de la referencia en lugar de texto blanco sobre cualquier color de línea. |
| Rutas | Superficie inferior, borde, extremos, datos y accesorios detalle/añadir/guardada | Superficie y accesorios diferenciados, tarifa destacada, etiqueta de guardada y descripción accesible completa. |
| Detalle y explorador | Identidad, estadísticas, recorrido, reconocimiento del vehículo y guía | Se conserva ese orden. El reconocimiento usa el panel 3D compartido; las vistas previas del recorrido dibujan únicamente geometría local. |
| Guardado | Lugares y paraderos separados; acción contextual de añadir; líneas con la misma tarjeta | Se conservan los datos y el estado; se unifica el selector y se permite distribuir etiquetas y acciones en varias filas. |
| Seguridad | Ayuda/Comunidad, contacto, emergencia, paraderos y avisos | Se conserva el orden; cabecera y selector comparten las reglas de presentación. Las acciones se apilan con texto grande. Se incluyen vistas previas de Ayuda y Comunidad de demostración. |
| Perfil | Identidad, billetera, carnés, cupones y grupos de ajustes; identidad apilada con texto grande | Identidad adaptable, encabezado de billetera flexible y acciones de pago/recarga apiladas con texto grande. Se conservan las acciones y preferencias existentes. |
| Galería y detalle 3D | Mismo panel, variantes original/detallado, carga, fallo y reintento | Panel y controles únicos para ambos accesos; las preferencias de variante se sincronizan entre vistas. |

Los dieciséis archivos Swift de presentación revisados se añaden a la
referencia reproducible: **28 fuentes iOS fijadas** en total, además de los
71 recursos compartidos. Se incluyen raíz/barra inferior, Mapa/buscador/panel/
tarjeta/menú, Rutas/detalle/identificación/visor y las otras tres pestañas.
Los hashes se cotejan con tools/verificar_paridad.py --ios.

## Visor 3D

El encuadre deja de usar una distancia fija. Se obtiene un radio de los
vértices realmente cargados y se ajusta a la relación ancho/alto. Se aplica
el campo visual de 36 grados, la vista inicial desde el parabrisas y el lado
del acompañante, y límites de zoom y desplazamiento relativos al modelo.
Android conserva el zoom relativo y la posición al recrear la superficie,
adaptando el encuadre al nuevo tamaño disponible.

Las mallas Android tienen su propia normalización; usar unidades numéricas
idénticas a iOS sin considerar esa escala produciría encuadres distintos.
Las pruebas proyectan las seis geometrías reales en vistas cuadradas, altas
y anchas. Las lecturas de límites no modifican el buffer que utiliza OpenGL.

Se conserva el fondo blanco del visor de iOS, también dentro de la interfaz
oscura. Rotar, acercar, alejar y restablecer usan controles comunes; el
desplazamiento tiene acciones accesibles. Los controles se desactivan durante
la carga o el fallo. Un fallo de lectura o de preparación gráfica ofrece
Reintentar. Al ocultarse el visor, perder el foco o pasar a segundo plano se
pausa la superficie. Los callbacks de una carga retirada no modifican la nueva.

La categoría se resuelve por identidad de ruta y referencia. Se conserva
la excepción de C-44/T2 de Miramar sin aplicarla a otra empresa. Una ruta
institucional o una categoría desconocida no hereda un modelo urbano:
presenta el estado de contenido pendiente de iOS.

La referencia M-15/C2VR de El Cortijo conserva C2VR como identidad, pero
representa C2 y el distintivo VR pequeño de forma separada. La regla comprueba
routeId, código, letra y empresa. Los colores, zonas de carrocería, bandas y
coordenadas del cartel siguen utilizando los recursos y las reglas portadas;
no se sustituyeron las seis geometrías ni se repintaron ventanas o ruedas.

## Alcance de las capturas

Las vistas previas usan componentes reales de producción y entradas fijas.
Cubren las cinco pestañas, texto al 150 % y 200 %, claro/oscuro y varios
estados de carga, error y vacío; añaden detalle de ruta y marco del visor.
Se alojan en PantallasIosCapturas.kt y Fase6Capturas.kt.

El fondo del mapa principal está rotulado como muestra. El detalle dibuja
una geometría local sin cartografía. Las capturas de 3D cubren el marco y sus
estados, **no una imagen producida por OpenGL**. Tampoco reproducen GPS,
permisos, notificaciones del sistema ni una conexión al broker.

Las referencias de capturas anteriores no se actualizaron inicialmente:
cinco de las ocho comparaciones detectaron cambios acumulados de fases
anteriores. La revisión de las 39 imágenes detectó palabras partidas en la
navegación, billetera y Ayuda al 200 %. Se corrigió la distribución y se
regeneraron las imágenes para una segunda revisión. Una imagen regenerada
por sí sola no acredita fidelidad a iOS.

Android conserva el tamaño de texto solicitado. Para evitar palabras partidas,
la barra inferior reparte las cinco pestañas en dos filas desde el 150 %;
el espacio de cada etiqueta se reparte según su ancho medido. El mapa reserva
la altura real de la barra y del panel para situar controles y detalle del bus,
en lugar de asumir una altura fija que se superpondría con texto grande.
Ayuda/Comunidad y Lugares/Líneas pasan a filas completas desde el 180 %.
Las acciones de billetera, contacto y reporte se apilan desde el 150 %.
Estos cambios de distribución son adaptaciones Android: no se atribuyen a
una coincidencia píxel a píxel con el tab bar nativo de iOS.

La foto local de Comunidad se decodifica de forma síncrona únicamente en las
vistas previas para poder revisar una imagen estable; en la aplicación sigue
cargándose fuera del hilo principal. El stream del asset se cierra en ambos casos.

## Comprobación local

Se incorporan 17 pruebas unitarias: 13 para encuadre, gestos, restauración e
identificación 3D y cuatro para los estados del panel de mapa. La proyección
comprueba todos los vértices de las seis mallas reales en tres relaciones de
aspecto, sin sustituir esa geometría por cubos de prueba. La resolución de
modelo y colores recorre las 102 referencias de líneas; las identidades
incorrectas, las categorías desconocidas y el feed UTP se prueban por separado.

El inventario de imágenes se registra en parity/android-previews.json con
hashes y dimensiones. tools/verificar_capturas.py comprueba ese inventario;
--require-results exige los 39 casos de comparación y que cada PNG recién
renderizado coincida con su referencia. Las pruebas del comprobador rechazan
archivos cambiados, rutas fuera del directorio, resultados ausentes o fallidos
e imágenes renderizadas que no coincidan. El informe mantiene expresamente
pendiente la comparación entre dispositivos y el renderizado nativo.

El workflow de main añade la verificación del inventario, sin declarar
ejecutadas las capturas en Linux. La última ejecución remota aprobada continúa
siendo la de 1ab4e38; las fases posteriores se han comprobado localmente.
Las referencias se produjeron en Windows, JDK 21, Layoutlib y complemento de
capturas 0.0.1-alpha16, en español y zona horaria America/Lima. Esas condiciones
se registran en el inventario; el saludo de Seguridad utiliza una fecha y zona
horaria explícitas en la vista previa. La aplicación usa la hora local normal.

La primera repetición detectó una diferencia únicamente en el halo decorativo
del banner de paraderos de Ayuda, en claro y oscuro. Su fase leía el reloj real
del sistema. Ahora el pulso usa el reloj de animación de Compose en la aplicación
y una fase fija en las vistas previas. No se aumentó la tolerancia de comparación
para aceptar ese cambio aleatorio.

Resultados finales del 14/11/2026:

| Comprobación | Resultado |
|---|---|
| Pruebas unitarias Android | 252 aprobadas, sin fallos ni omisiones; incluyen los 27 casos de referencia iOS. |
| Pruebas de herramientas | 39 aprobadas. |
| Comparación Compose | 39 imágenes aprobadas; los PNG renderizados coinciden exactamente con sus referencias revisadas. |
| Fuentes y recursos iOS | 28 fuentes y 71 recursos cotejados con el checkout fijado de main. |
| Idiomas | 1301 claves comprobadas, cuatro puntos dinámicos conocidos, cero errores. |
| Análisis estático | Cero errores; 82 advertencias y 16 sugerencias pendientes. |
| APK | Compilado; seis mallas accesibles, catálogos exactos y componentes de la fase incluidos. Configuración efectiva comprobada sin mostrar claves. |
| Pruebas instrumentales | APK de pruebas compilado; no ejecutado en teléfono ni emulador. |

SHA-256 del APK debug comprobado:
`c7a03565acb6965c51c3d206e593a42efc65dd8a180d28dd2ff77b90f125eacb`.
La fase 6 queda implementada y verificada dentro de este alcance local,
preparada como entrega independiente. No se declara cerrada la validación visual en dispositivos.

Comando utilizado para la entrega, con JDK 21 y dependencias ya disponibles:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:validateDebugScreenshotTest --offline --continue --max-workers=1 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' '-Pcapturas=true' '-Pandroid.experimental.enableScreenshotTest=true' --console=plain
python -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verificar_paridad.py --require-results
python tools/verificar_idiomas.py
python tools/verificar_apk.py
python tools/verificar_configuracion.py
python tools/verificar_capturas.py --require-results
```

Para una modificación visual deliberada, revisar los PNG renderizados y usar
updateDebugScreenshotTest en lugar de validateDebugScreenshotTest. Después
revisar las referencias, registrar sus hashes y repetir la validación. No se
debe actualizar el inventario automáticamente para ocultar una regresión.

## Diferencias y comprobaciones pendientes

- Obtener capturas iPhone/Android con los mismos datos, idioma, ancho útil y
  tamaño de texto. No se dispone de un iPhone ni de capturas iOS equivalentes
  para afirmar coincidencia visual completa.
- Revisar las cinco pestañas y sus hojas en teléfono, incluyendo texto grande,
  zonas seguras, teclado, Atrás, foco de TalkBack y estados de error.
- Probar giro, zoom, desplazamiento, restablecimiento, reintento y recreación
  del contexto OpenGL con las seis mallas en hardware Android.
- Comparar la iluminación y el cartel de cada modelo. RealityKit y OpenGL ES
  tienen materiales e iluminación distintos; las pruebas geométricas no
  acreditan igualdad de la imagen final.
- Revisar Google Maps y el explorador con la configuración real de fase 4.
  Cartografía y rutas peatonales pueden diferir respecto de MapKit.

Se mantienen iconos, fuentes y controles nativos de Android cuando corresponde.
Las áreas táctiles Android conservan su mínimo de 48 dp. La fidelidad se evalúa
en jerarquía, contenido, relaciones de tamaño y acciones, con las diferencias
de plataforma registradas. La fase 7 conserva sus dependencias de backend y FCM.
