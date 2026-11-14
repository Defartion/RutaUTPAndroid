# Herramientas de desarrollo

## Referencia iOS y comprobaciones automáticas

`python tools/verificar_paridad.py` comprueba los recursos fijados en
`parity/reference.json`, los casos de comparación y el seguimiento F01–F15.
`--require-results` exige además los resultados JUnit y lint de la compilación
actual: al menos 252 pruebas, sin fallos ni omisiones, incluidos los once casos
ETA, ocho de comparación de rutas y ocho de continuidad. Los casos pendientes
se informan por separado.

`python -m unittest discover -s tools/tests -p "test_*.py"` ejecuta las pruebas
del verificador. El informe queda en
`app/build/reports/parity/reference-check.json`.
Para comprobar también un checkout iOS exacto, añadir `--ios RUTA_CHECKOUT_IOS`.
No necesita acceso al broker ni credenciales.

Ver [el resultado y reproducción de fase 1](../docs/FASE_1_REFERENCIA_Y_VALIDACION.md).
La [fase 2](../docs/FASE_2_RUTAS_Y_LLEGADAS.md) activa los casos de ETA y transbordos.
La [fase 4](../docs/FASE_4_MAPAS_BUSQUEDA_Y_CONEXION.md) documenta las cuentas
por instalación y la comprobación efectiva de la configuración de mapas.

`python tools/verificar_idiomas.py` comprueba llamadas Kotlin con comentarios,
escapes y plantillas, además del contenido bilingüe de recursos y Comunidad.
Rechaza traducciones o parámetros ausentes, argumentos faltantes y llamadas
dinámicas nuevas sin cobertura explícita. El informe queda en
`app/build/reports/parity/language-check.json`. Los cuatro puntos dinámicos
conocidos se documentan en la [fase 5](../docs/FASE_5_IDIOMAS_ACCESIBILIDAD_Y_ERRORES.md).
El catálogo compartido permanece intacto; el complemento propio es
`app/src/main/assets/traducciones-android.json`. El verificador del APK coteja
ambos catálogos contra los archivos realmente empaquetados.

## Comprobar el APK generado

Después de assembleDebug, ejecutar `python tools/verificar_apk.py`. Verifica las
mallas y catálogos dentro del APK y la presencia de las pantallas nuevas. AAPT
descomprime los assets con sufijo `.gz`: los modelos se abren como `.mesh` en
el dispositivo. Las pruebas sobre los archivos fuente no detectan por sí solas
una diferencia entre esas rutas de empaquetado y las que abre el visor.

`python tools/verificar_configuracion.py` inspecciona el manifiesto y los DEX
sin mostrar claves: comprueba el placeholder resuelto, la ausencia de campos
MQTT de credenciales y la presencia del almacén protegido. Guarda el informe
en `app/build/reports/parity/configuration-check.json`. La opción
`--expected-map-key` se reserva a marcadores sintéticos de CI; no pasar claves
reales por la línea de comandos. `--forbid MARCADOR` comprueba que una
credencial sintética de compilación no se haya empaquetado.

## Vistas previas Compose

`python tools/verificar_capturas.py` coteja el inventario de 39 PNG revisados,
sus hashes y dimensiones, y la cobertura de las cinco pestañas. El inventario
está en `parity/android-previews.json`. `--require-results` exige además una
ejecución satisfactoria de `validateDebugScreenshotTest` para todas las imágenes.
El informe queda en `app/build/reports/parity/preview-check.json`.

Son referencias locales de Android para detectar regresiones. No sustituyen
la comparación iPhone/Android ni las pruebas de Google Maps, OpenGL y TalkBack
en dispositivo. El workflow comprueba el inventario; el renderizado Layoutlib
se ejecutó localmente en Windows y todavía no se certificó en Linux.
Ver comandos, alcance y adaptaciones en la [fase 6](../docs/FASE_6_PRESENTACION_Y_TRANSPORTES_3D.md).

## Recursos 3D

convertir_transportes_ios.py convierte los seis modelos USDZ de la referencia
iOS a las mallas locales usadas por el visor OpenGL ES de Android. No requiere
USD en la aplicación. La herramienta necesita Python, NumPy y usd-core en el
equipo que realice la conversión; ver su ayuda y ThirdPartyNotices/Transportes3D.md.
También genera `.partes.json`: rangos de vértices de Carroceria/ContornoMarca
y transformación del espacio local al normalizado. Permiten pintar solo las
zonas originales de carrocería y situar las bandas/cartel de cada línea en
las mismas coordenadas de iOS. Deben regenerarse junto con las seis mallas.

## Scripts de demostración anteriores

simular_vehiculos.py y agregador_vehiculos.py son herramientas heredadas que ya
estaban en este checkout. Publican posiciones con el formato antiguo, sin
procedencia y sin autorización de simulación. La app actual no muestra esas
posiciones como flota observada. Sus comentarios originales sobre anonimato y
vehículos reales no describen el contrato actual.

Para probar la flota compartida se debe usar el backend y el simulador actuales
[del repositorio iOS fijado para la paridad](https://github.com/JDiazD33/RutaUTP/tree/8145276bb6fc72a168cc78119def2727578ec0bb/backend),
con MQTT v2/v3, sus cuentas y ACL, y el control vigente de simulación. No ejecutar
los scripts heredados contra un servicio compartido para comprobar paridad.
La demostración local dentro de la app continúa separada y rotulada como simulación.
