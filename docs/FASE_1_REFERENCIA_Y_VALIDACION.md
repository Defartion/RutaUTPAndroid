# Fase 1 — Referencia y comprobaciones de regresión

Estado: **implementada y verificada localmente el 10/11/2026**.
Este documento conserva los resultados al cerrar la fase 1. La
[fase 2](FASE_2_RUTAS_Y_LLEGADAS.md) amplía los casos activos y las comprobaciones.
La primera ejecución del workflow en GitHub queda pendiente de publicar estos
archivos en el repositorio. No se ha realizado un push ni configurado protección
de rama.

## Referencia fijada

Se volvió a consultar exclusivamente main de ambos repositorios antes de empezar:

| Repositorio | Commit |
|---|---|
| iOS y backend compartido | 8145276bb6fc72a168cc78119def2727578ec0bb |
| Android al comenzar la fase 1 | 7ba57cbe73f97e83fd112584a9e8ff89623ac4c1 |

Ambos conservaban las referencias de la auditoría. El commit Android anterior
identifica el punto de partida; los cambios futuros no deben modificarlo para
simular que siempre formaron parte de la base.

[reference.json](../parity/reference.json) fija los commits, 71 recursos
compartidos y 12 fuentes del comportamiento iOS/backend. También identifica
las versiones de contratos de vehículos, simulación, ocupación, cambios y push.

Los recursos incluyen los once archivos GTFS, cuatro JSON y 56 clips de señas.
Se conservaron los recursos existentes. Para los textos, la huella se calcula
con saltos LF, evitando que la conversión automática CRLF de Git en Windows
produzca una diferencia artificial frente a Linux. Los vídeos se comparan
byte a byte.

El contrato push queda registrado con su incompatibilidad actual:
backend APNs schema 1 frente a cliente FCM schema 2. La fase 1 no declara que
esa integración esté resuelta.

## Casos de comportamiento preparados

Los archivos están en recursos de pruebas, fuera de los recursos de la aplicación:

| Archivo | Casos | Estado |
|---|---:|---|
| [eta.json](../app/src/test/resources/parity/eta.json) | 11 | 6 ejecutados como base y 5 pendientes de F01. |
| [route-comparison.json](../app/src/test/resources/parity/route-comparison.json) | 8 | Pendientes de adaptar a la corrección F02. |
| [travel-state.json](../app/src/test/resources/parity/travel-state.json) | 8 | Pendientes del adaptador de estado o prueba instrumental de fase 3. |

Son **27 casos de referencia: seis comprobados en Android y 21 pendientes**.
Los resultados esperados proceden de pruebas o reglas del commit iOS indicado.
No se ejecutó Xcode en Windows.

IosEtaReferenceTest lee el mismo JSON y ejecuta únicamente los casos marcados
como baseline: llegada hacia el destino, tráfico lento, falta de rumbo,
vehículo que se aleja, destino fuera del corredor y estimación superior a dos horas.

Los otros cinco casos ETA conservan el resultado esperado iOS para detenido,
velocidad baja, velocidad desconocida, sentido inverso y destino entre 800 y
1600 m. No se sustituyen sus expectativas por el comportamiento defectuoso actual.

Los casos de rutas fijan los límites de 45 minutos, ahorro de 15 minutos y
25 %, y la situación sin directo. Los de estado incluyen respuestas antiguas,
limpieza, cambio a Buses UTP, notificaciones durante un viaje y conservación
de pestañas/tracking.

### Cómo cerrar un caso en las siguientes fases

1. Usar su entrada y resultado esperado al corregir la función.
2. Vincularlo a una prueba del comportamiento real de Android.
3. Para ETA, promoverlo a baseline cuando la implementación satisfaga el caso.
   El test parametrizado lo ejecutará y el control exigirá que figure en JUnit.
4. Para rutas/estado, añadir su ejecutor antes de promover casos: el control
   actual rechaza activarlos sin ejecutor.
5. Actualizar hallazgo y evidencia de cierre, incluyendo dispositivo si procede.

El número de pruebas aprobadas no incluye los 21 escenarios pendientes.

## Seguimiento y comparación visual

[findings.json](../parity/findings.json) conserva F01–F15 con sus fases, estado,
criterio de aceptación y validación. Todos siguen pendientes, salvo F14, que
está en curso: esta fase incorpora la comprobación inicial y la fase 8 completa
la cobertura de flujos y dispositivos.

[visual-states.json](../parity/visual-states.json) fija la comparación de Mapa,
Rutas, Guardado, Seguridad y Perfil con estados cargados, vacíos, errores,
formularios y estados específicos de cada pantalla.

Se contemplan tema claro/oscuro, texto al 150 %/200 % y TalkBack cuando proceda.
La comparación con iPhone y las capturas sobre dispositivo permanecen pendientes.
Una vista previa Compose no se registra como prueba de GPS, cámara u OpenGL.

## Comprobaciones automáticas

El workflow [android.yml](../.github/workflows/android.yml) se ejecutará en:

- Push a main.
- Pull requests dirigidos a main.
- Ejecución manual desde GitHub Actions.

Prepara Java 21, Python 3.13 y Android SDK 36, usa el wrapper del proyecto y:

1. Ejecuta las pruebas del verificador.
2. Comprueba recursos, referencia y seguimiento.
3. Ejecuta pruebas unitarias, compilación del APK y análisis Android.
4. Exige al menos 125 pruebas sin fallos, errores ni omisiones, incluidos
   los seis casos de referencia ETA.
5. Verifica que lint no tenga errores y que los recursos estén realmente
   incluidos en el APK.
6. Conserva informes y, si todo pasa, el APK de desarrollo.

El runner no recibe cuentas MQTT, configuración Firebase ni firma de producción.
La comprobación funciona sin conectar con el backend ni clonar iOS.
Se puede comprobar adicionalmente el checkout iOS exacto en local.

La definición del flujo sigue el uso del wrapper y la configuración Gradle
documentados por [GitHub](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle).
La sintaxis YAML y la selección de eventos se comprobaron localmente; todavía
no hay una ejecución remota de estos cambios.

## Ejecutar en local

Desde la raíz del repositorio, con JDK 21 y SDK 36:

~~~powershell
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/verificar_paridad.py
.\gradlew.bat :app:testDebugUnitTest --rerun --offline
.\gradlew.bat :app:assembleDebug :app:lintDebug --offline
python tools/verificar_paridad.py --require-results
python tools/verificar_apk.py
~~~

Offline requiere tener las dependencias en caché. En una instalación nueva,
omitir esa opción. En Linux se usa bash ./gradlew en lugar del wrapper .bat.

Para contrastar además un checkout local iOS en el commit fijado:

~~~powershell
python tools/verificar_paridad.py --ios RUTA_CHECKOUT_IOS --require-results
~~~

El informe generado queda en
app/build/reports/parity/reference-check.json. Ese archivo distingue recursos
verificados, casos pendientes y resultados de pruebas.

## Validación realizada

| Comprobación | Resultado |
|---|---|
| Pruebas unitarias Android | 125; cero fallos, errores u omitidas. |
| Base anterior | 119 pruebas conservadas. |
| Casos ETA nuevos ejecutados | 6, obtenidos del JSON compartido. |
| Pruebas del verificador | 15; todas aprobadas. |
| Compilación de desarrollo | Correcta. |
| Análisis Android | Cero errores; 76 advertencias y 16 sugerencias. |
| Recursos de referencia | 71 correctos; fuentes contrastadas con checkout iOS fijado. |
| APK | Comprobación correcta de mallas y catálogos instalables. |
| Workflow | YAML y eventos verificados en local; ejecución GitHub pendiente de publicación. |

Las pruebas del verificador comprueban, entre otros casos, recursos alterados,
rutas fuera del checkout, hallazgos omitidos, referencias mezcladas, resultados
JUnit ausentes o inflados, casos ETA no ejecutados, pruebas omitidas, errores
lint y conversión LF/CRLF.

El comportamiento de la aplicación permanece igual en esta fase. Las correcciones
de cálculo, navegación e integración se ejecutan en las fases siguientes.
