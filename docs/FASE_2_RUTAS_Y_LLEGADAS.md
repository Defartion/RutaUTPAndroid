# Fase 2 — Rutas y llegadas fieles a iOS

Referencia: main iOS 8145276bb6fc72a168cc78119def2727578ec0bb.
Fecha: 10 de noviembre de 2026.
Estado: **implementada y verificada localmente**; GPS y comparación visual
en dispositivo pendientes.

La fase 1 se conservó en el commit local **e42b069** antes de empezar.
Se volvieron a consultar ambas ramas main: la referencia remota de iOS
seguía en 8145276 y la de Android en 7ba57cb. No se inspeccionaron otras ramas.

## Llegadas de vehículos — F01

El estimador Android ahora:

- Conserva todos los puntos del recorrido para proyectar la posición y calcular
  la distancia restante. La geometría simplificada se mantiene para dibujar.
- Permite un objetivo a un máximo de 1600 m del corredor, con el mismo criterio
  de distancia usado por el planificador.
- Calcula en ambos sentidos cuando el vehículo se acerca al objetivo.
- Resuelve la vuelta de recorridos circulares según el rumbo.
- No muestra ETA para una velocidad observada entre 0 y menos de 0,5 m/s,
  incluso si el vehículo está en el destino.
- Usa la velocidad observada desde 0,5 m/s, limitada a 15 m/s.
- Solo utiliza el horario cuando la velocidad es desconocida. Su velocidad
  programada procede de la longitud original y duración, con límites 0,5–15 m/s.
- Rechaza velocidades no finitas, falta de rumbo y estimaciones superiores
  a dos horas.

El mapa usa el destino seleccionado o la referencia de inicio vigente
(campus, sede o respaldo de ubicación), en lugar de recurrir siempre a UTP.
Al cambiar destino, limpiar o cambiar sede recalcula las llegadas sobre las
posiciones disponibles. Antes de reutilizarlas, el proveedor vuelve a comprobar
su caducidad y autorización de simulación.

La corrección de la elección del ancla y su conexión al mapa se revisó en
código; el recorrido real con GPS y las interacciones sobre el mapa necesitan
la validación en dispositivo prevista por el plan.

## Comparación de rutas — F02

Cuando existe una línea directa, un transbordo se muestra como alternativa
solo si:

1. La mejor directa tarda al menos 45 minutos.
2. El transbordo ahorra al menos 15 minutos.
3. El ahorro representa al menos el 25 % del viaje directo.

Se incluyen las esperas iniciales en los tiempos comparados. La penalización
de tres minutos que ordena candidatos no se presenta como tiempo de viaje
ni se usa para falsear el ahorro.

Se conservan todas las líneas directas válidas, hasta 32 combinaciones con
transbordo y el destacado de subida más cercana. Cada tramo de bus de un
transbordo del comparador debe superar 800 m, conforme al barrido de iOS.
La elección de subidas/bajadas aplica ese mínimo antes de seleccionar las
mejores conexiones, evitando descartar una conexión útil por otra más corta.

El filtro de ahorro se aplica al comparador del mapa. La búsqueda de candidatos
de tracking mantiene transbordos cuando corresponde; no se le impone la
exclusión de alternativas frente a un directo corto.

La verificación de caminatas y el estado de estimación cuando falla el
proveedor continúan activos.

## Casos de prueba

Se activaron las expectativas iOS originales de la fase 1:

| Grupo | Casos ejecutados |
|---|---:|
| Referencia ETA | 11 |
| Límites de comparación de transbordos | 8 |
| Regresiones adicionales de fase 2 | 8 |
| Pruebas Android totales, incluidas las anteriores | 146 |

Las regresiones adicionales cubren circular en ambos sentidos, una curva
que la reducción a 250 puntos perdería, detenido en el destino, velocidades
no finitas, directo corto, espera inicial de un directo largo, transbordo
sin directo con caminatas verificadas/estimadas, conservación de 40 directas
y tramos demasiado cortos para el comparador.

Los 27 casos de referencia iniciales quedan así: **19 comprobados y ocho
pendientes de la fase 3**. Los ocho pendientes son escenarios de estado;
no se cuentan como pruebas aprobadas ni se omiten mediante JUnit.

El verificador exige cada caso en su ejecutor correspondiente y una ejecución
de al menos 146 pruebas sin fallos, errores ni omisiones. Sus propias 17 pruebas
comprueban también que los casos de rutas no puedan declararse aprobados
sin ejecutarlos.

## Validación realizada

| Comprobación | Resultado |
|---|---|
| Pruebas Android | 146; cero fallos, errores y omisiones. |
| Pruebas del verificador | 17; todas aprobadas. |
| Compilación del APK | Correcta. |
| Análisis Android | Cero errores; 76 advertencias y 16 sugerencias. |
| Referencia compartida | 71 recursos y fuentes iOS fijadas comprobados. |
| APK instalable | Mallas y catálogos empaquetados comprobados. |

## Reproducción

~~~powershell
python -m unittest discover -s tools/tests -p "test_*.py"
.\gradlew.bat :app:testDebugUnitTest --offline
.\gradlew.bat :app:assembleDebug :app:lintDebug --offline
python tools/verificar_paridad.py --require-results
python tools/verificar_apk.py
~~~

Offline presupone dependencias en caché. La comprobación adicional del checkout
iOS se realiza con la opción --ios del verificador.

Los resultados de la ejecución de esta fase se conservan en
app/build/test-results/testDebugUnitTest y app/build/reports/parity.

## Límites de validación

No se ejecutó Xcode, no se publicaron observaciones MQTT y no se modificó
el backend. Las pruebas usan datos sintéticos y los recursos locales fijados.
La comparación GPS en un teléfono y la experiencia visual en ambas plataformas
siguen pendientes de las fases de validación en dispositivo.

Las correcciones de conservación de pestañas, notificaciones durante un viaje
y cambio de catálogo siguen en fase 3. La preparación/caché de geometría ETA y
las mediciones de rendimiento permanecen en fase 8.
