# Fase 8: rendimiento y candidata local

Trabajo y comprobaciones locales: **14 de noviembre de 2026**.
Al iniciar esta fase, la fase 7 estaba guardada en **08ba24a96017cc135511c689652977cde80a39f7**, sin push.
Esta fase queda implementada y comprobada dentro del alcance local descrito;
la validación de entrega en teléfonos continúa pendiente.

Se mantiene main iOS 8145276bb6fc72a168cc78119def2727578ec0bb, comprobada sin
cambios al iniciar el trabajo. Se cotejan **36 fuentes y 71 recursos**.
No se revisaron otras ramas ni se modificó el repositorio iOS.

## Optimización fiel a la referencia

| Área | Cambio |
|---|---|
| Geometría ETA | Se prepara una vez por ruta del catálogo instalado: shape original, longitud, circularidad y velocidad programada. |
| Destino | Se memoriza una proyección por ruta y por la única ancla vigente, incluidas las proyecciones fuera de ruta. |
| Invalidación | Las coordenadas se comparan por sus bits exactos. Instalar un catálogo siempre invalida la caché, aunque conserve IDs o referencias. |
| Posición del vehículo | Se sigue proyectando cada posición nueva. No se reutiliza el ETA de un bus para otro ni se alteran velocidad, sentido, radios o límites. |
| Memoria | La caché pertenece al mapa y solo retiene su catálogo y destino actual. La geometría preparada copia una vez la lista de coordenadas. |
| Proyección | Se conserva el resultado numérico calculando el tramo ganador y sus acumulados en una pasada, sin el array auxiliar por consulta. |
| Cercanía | Se añade el prefiltro conservador de main iOS. La selección final conserva distancia, radios 400/800 m, orden y duplicados. |
| Datos extremos | Ante polos, antimeridiano, datos inválidos o un radio no utilizable, el prefiltro conserva candidatos para el cálculo original. |
| Catálogos | El índice se reutiliza por carpeta y referencia de lista; una carga nueva lo sustituye. La consulta se calcula en Dispatchers.Default. |
| Trabajo oculto | Los mensajes de flota no reconstruyen el mapa oculto; al volver se aplican posiciones vigentes. El bucle simulado de 20 Hz solo corre con mapa visible y fuente simulada. |

La longitud haversine y la fracción de progreso de la proyección conservan sus
métricas distintas. Las pruebas comparan con copias del estimador y del matching
anteriores a esta fase, tomadas de 08ba24a y congeladas por hash. No se comparan
dos llamadas al mismo camino optimizado.

El mapa libera su consumidor de GPS al ocultarse. Un viaje activo puede mantener
su propio consumidor de ubicación; esta fase no desactiva ese seguimiento.
También se invalida la carga del panel al pausar para que una respuesta tardía
no reinicie trabajos ni deje el estado Cargando activo.

## Mediciones observadas en la computadora

Entorno: **Windows 11, JVM 21.0.10**, feed real de **102 rutas y 53 616 puntos**.
Las posiciones de los **1000 vehículos** son sintéticas; no se usa el broker.
Cada escenario tiene dos vueltas de preparación y cinco muestras. Se informa
mediana y percentil 95 por rango más próximo, que con cinco muestras es el máximo.
No se impone un tiempo máximo a CI, ya que sus equipos y carga varían.

| Escenario | Mediana | p95 |
|---|---:|---:|
| Parseo del catálogo | 55.73 ms | 64.24 ms |
| Construcción del índice y primera selección de candidatos | 3.27 ms | 3.74 ms |
| ETA de una instantánea, código anterior | 44.72 ms | 48.52 ms |
| ETA de una instantánea, caché recién instalada | 12.51 ms | 12.76 ms |
| ETA de una instantánea, caché preparada | 7.90 ms | 8.45 ms |
| Cambio de destino con geometría preparada | 8.79 ms | 9.23 ms |
| 32 consultas cercanas, recorrido completo | 25.03 ms | 25.90 ms |
| Las mismas consultas con prefiltro | 9.05 ms | 9.32 ms |

En esta ejecución, el cálculo ETA preparado tardó aproximadamente **un 82.3 %
menos** que el anterior. El prefiltro redujo las rutas sometidas a distancia
exacta de **3264 a 996**, un **69.5 %**. Todas las comparaciones produjeron
resultados iguales; se verifican además los límites de preparación por ruta.

Estos tiempos miden cálculo Kotlin en la JVM del equipo. No representan FPS,
consumo de batería, inicio completo de la aplicación, renderizado del mapa,
recepción MQTT ni latencia de un teléfono. El parseo se mide con el entorno
ya preparado, sin atribuirlo a un arranque frío del sistema.

Los resultados incluyen hashes de los diez archivos fuente asociados a la
medición. Si cambia alguno, el verificador rechaza usar esa medición anterior.
La evidencia original está en parity/phase8-local-evidence.json; nuevas
ejecuciones generan sus propios informes en app/build/reports/parity/.

## Continuidad y conservación de datos

Pasan pruebas de pausa/reanudación sin ticks ocultos y de cargas tardías al
ocultar el mapa. La caché se prueba con destinos casi idénticos, cambios de
catálogo que mantienen IDs, rutas inválidas y muchas anclas consecutivas.

Se comprueba la lectura histórica de lugares y líneas, la compatibilidad UTP
sin esFijo, sus IDs, los importes Long en céntimos y la configuración de
notificaciones versión 1. Un JSON ilegible mantiene el bloqueo existente.
Los decodificadores de lugares y líneas pasan de privados a internos para
probar el camino de producción; su comportamiento se conserva.

El contrato de doce archivos de almacenamiento se coteja con 08ba24a.
Solo se admiten esa visibilidad de los dos métodos y diferencias de saltos de
línea o nueva línea final. Los nombres, claves, esquemas y rutas de fotos
permanecen iguales. Esto no se presenta como prueba de actualización instalada.

Se compilan **tres pruebas instrumentales nuevas**, sin ejecutarlas:

- Conservación e idempotencia de preferencias versión 1.
- Conservación de ruta, lectura y bytes de fotos después de la migración.
- Medición ETA en Android con geometría real y posiciones sintéticas.

Utilizan preferencias y archivos aislados del APK de pruebas o datos en memoria.
No abren la Activity principal, piden GPS ni inicializan Firebase o MQTT.
No se encontró teléfono ni emulador conectado en la comprobación ADB.

## Resultado y artefactos

| Comprobación | Resultado local |
|---|---|
| Unitarias Android | **285 aprobadas**, 21 nuevas, sin fallos ni omisiones; los 27 casos de referencia iOS siguen pasando. |
| Herramientas Python | **45 aprobadas**. |
| Backend preparado | Se verifica el mismo paquete y sus **88 resultados aprobados de fase 7**, sin repetir ni activar servicios. |
| Idiomas | 1301 claves y cuatro puntos dinámicos conocidos, cero errores. |
| Análisis estático | Cero errores; 82 advertencias y 16 sugerencias. |
| Capturas | 39 referencias Android conservadas y verificadas; no se modificó la interfaz ni se regeneraron. |
| APK de aplicación y pruebas | Ambos compilados; se comprueba la presencia de las nuevas clases. |
| Comparación iPhone/Android | Pendiente en dispositivos equivalentes. |
| Firebase y servidor | Pendientes, conforme a lo confirmado por el equipo en fase 7. |

APK debug: versión **1.2 (3)**, applicationId com.example.rutautpnative.
SHA-256 comprobado:
a02bcf6635ddf642c9439533c7997dae20d71421a07ae82761161753c331dff1.
Ese hash identifica el archivo local; otra firma debug o una recompilación
en otro entorno puede producir bytes diferentes.

tools/verificar_entrega.py genera release-candidate.json vinculando APK,
versión, resultados, mediciones, fuentes y contratos persistentes. Su estado es
local_candidate_device_validation_pending. Los campos de actualización
instalada, prueba instrumental ejecutada, comparación iOS y servicios reales
siguen pendientes expresamente.

El workflow prepara un trabajo adicional que reúne artefactos Android y backend,
vuelve a verificarlos y conserva el informe de candidata. La sintaxis YAML y la
verificación local están comprobadas. Esta entrega publica juntas las fases 7
y 8. La ejecución remota aprobada durante su preparación corresponde a 6a2297c;
los resultados de la nueva publicación se consultan en GitHub Actions.

## Reproducir y terminar la validación

~~~powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --continue --max-workers=1 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
python -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verificar_paridad.py --require-results
python tools/verificar_entrega.py
~~~

La verificación integral requiere conservar o generar también los resultados
del backend conforme a fase 7. El modificador offline requiere dependencias
ya disponibles. Las mediciones solo se regeneran al ejecutar su prueba; no se
actualizan sus valores manualmente para aprobar una candidata.

Para cerrar F13/F14 y la entrega en dispositivo falta:

1. Ejecutar connectedDebugAndroidTest con un Android intermedio y registrar sus
   características, tiempos, temperaturas y condiciones de prueba.
2. Instalar la candidata sobre una versión anterior con el mismo identificador
   y firma; comprobar favoritos, preferencias, fotos y saldo de demostración
   antes/después. Los casos de formato y migración aislada no sustituyen esta prueba.
3. Revisar las cinco pestañas, teclado, Atrás, rotación, recreación, TalkBack,
   texto grande y permisos; comparar con iPhone usando datos equivalentes.
4. Medir inicio completo, búsquedas, instantáneas, mapa y batería con la app
   visible, oculta y al bloquear/desbloquear, incluyendo un viaje activo.
5. Probar Google Maps y OpenGL reales. Activar y validar FCM cuando existan
   Firebase y servidor; esa dependencia no impide preparar esta candidata local.

F13 y F14 permanecen en curso por esos requisitos. No se declara lista para
publicación en una tienda. La fase 8 se guarda como entrega independiente.
