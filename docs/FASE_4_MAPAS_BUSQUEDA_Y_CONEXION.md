# Fase 4: mapas, búsqueda y conexión por instalación

Validación local: **13 de noviembre de 2026**. Implementación local terminada;
el cierre integral necesita servicios configurados y dos teléfonos.
Hallazgos: F06 y F08 en progreso por su validación externa; F09 verificado
localmente. Solo se ha utilizado la rama **main** de ambos repositorios.

La referencia iOS sigue siendo `8145276bb6fc72a168cc78119def2727578ec0bb`,
sin cambios en main al iniciar esta fase. Se mantienen los 71 recursos
compartidos y las 12 fuentes iOS fijadas por el comprobador.

La fase anterior se publicó como
[e388fd9](https://github.com/Defartion/RutaUTPAndroid/commit/e388fd9a8ccfab165b3f2b32f635fe1299e1c160).
La corrección de preparación del SDK en GitHub quedó en
[1ab4e38](https://github.com/Defartion/RutaUTPAndroid/commit/1ab4e38238d6025f643bfa882672affa5d351d07):
runner Ubuntu 24.04 y ruta explícita a sdkmanager. Su
[ejecución de GitHub](https://github.com/Defartion/RutaUTPAndroid/actions/runs/43460750207)
terminó correctamente. Ese resultado corresponde a la fase 3 publicada;
la fase 4 se entrega en un commit independiente, sin publicar todavía en GitHub.

## Cuenta de transporte por teléfono

En Perfil → Conexión de transporte se introducen servidor, puerto, usuario,
contraseña y, si el administrador lo requiere, certificado CA. La pantalla
separa la cuenta de lectura de la cuenta contribuyente. El mismo APK permite
configurar cuentas distintas en teléfonos diferentes.

El administrador debe crear y asignar las cuentas en el broker y aplicar sus
ACL. La referencia iOS tiene aprovisionamiento explícito y Keychain; no ofrece
un servicio de alta automática de cuentas que Android pueda reutilizar.
Guardar una cuenta valida y persiste sus datos, sin acreditar que el broker
la haya aceptado. No se ha añadido un registro remoto ficticio.

La identidad de configuración incluye host canónico, puerto y transporte.
Las parejas usuario/contraseña permanecen unidas al canal y al rol; no se
reutilizan para otro servidor. Lectura y contribución exigen usuarios distintos
en un mismo canal. Si existe cuenta lectora se utiliza para recibir la flota;
si no existe, la contribuyente puede recibirla con los permisos de su ACL.
Las cuentas lectoras no pueden publicar aportes o reportes desde la aplicación.
Se rechazan los nombres reservados para backend, simulador y lectores conocidos
cuando corresponda al rol.

Reemplazar, quitar o restablecer cuentas retira los modelos y conexiones del
contexto anterior. Ante un viaje activo se solicita terminarlo; los aportes
y los avisos remotos quedan desactivados hasta que el usuario los active otra
vez. Quitar una cuenta local no revoca su acceso en el servidor: el administrador
debe revocarla allí. Los rechazos de autenticación se muestran sin reproducir
credenciales; guardar nuevamente permite reintentar. Los errores de una
conexión retirada no invalidan una cuenta nueva.

La flota puede reintentar una conexión inicial fallida al volver al primer
plano, conservando la invalidación del control de simulación. Una reconexión
necesita volver a recibir control vigente antes de mostrar datos simulados.

## Protección Android y TLS

Las cuentas y la clave web se guardan en un archivo atómico versionado,
cifrado con AES-256/GCM, nonce aleatorio y autenticación del contenido.
La clave se genera en Android Keystore; el archivo se aloja en
`noBackupFilesDir/conexion-v1/credenciales.bin`. La pantalla protege las
capturas y no conserva contraseñas en el estado restaurable de Compose.
Si el archivo o la clave no se pueden abrir, se bloquea la escritura normal
y se conserva el contenido hasta un restablecimiento explícito.

Android Keystore mantiene el material de clave no exportable; el respaldo
por hardware depende del dispositivo y no se da por probado aquí. La ubicación
elegida queda excluida de las copias del sistema, conforme a la documentación
de [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
y [Auto Backup](https://developer.android.com/identity/data/autobackup).

Se eliminaron de BuildConfig `MQTT_USERNAME`, `MQTT_PASSWORD` y `MQTT_READ_ONLY`.
Sus valores de propiedades antiguos dejan de utilizarse. Solo permanecen
valores públicos del canal: host, puerto, TLS y nombre de CA empaquetada.
Los secretos de un APK antiguo distribuido deben rotarse administrativamente;
este cambio no puede retirarlos de instalaciones anteriores.

Producción exige TLS. La variante de desarrollo permite transporte plano
únicamente para direcciones locales o privadas expresamente configuradas.
Los cinco clientes MQTT usan la misma política de confianza y verifican el
nombre del servidor. Se admite la confianza del sistema o una CA suministrada;
una CA configurada inválida provoca fallo, sin continuar con otra confianza.

## Configuración efectiva de Google

El metadato del SDK usa ahora `${MAPS_API_KEY}`. La precedencia es propiedad
Gradle → variable de entorno → `local.properties`. La clave literal anterior
se trasladó al archivo local ignorado para conservar la configuración de este
equipo; no se incluye su valor en informes.

Places y Directions usan una clave web separada que se introduce en Perfil
y se guarda cifrada. No recuperan automáticamente la clave del SDK. Las
peticiones incluyen paquete y SHA-1 de la firma del APK mediante
`X-Android-Package` y `X-Android-Cert`; la huella lleva 40 dígitos hexadecimales
sin separadores. Las llamadas se cancelan al cancelar su búsqueda, tienen
tiempo y respuesta limitados, y no siguen redirecciones con la credencial.
Cambiar la clave web invalida la identidad de caché de caminatas sin reiniciar
un viaje de transporte.

En Google Cloud se deben configurar las restricciones de aplicación y API
correspondientes al SDK y a los servicios web, y comprobar las firmas debug,
release o de Play que se vayan a distribuir. Para los endpoints web usados,
incluido Directions heredado, hay que demostrar que rechazan paquete o huella
incorrectos. Si no admiten esa protección, se necesita un proxy autenticado
antes de habilitarlos en producción. El código de cabeceras no acredita esa
restricción; estas condiciones siguen la
[guía oficial de Google](https://developers.google.com/maps/api-security-best-practices).

## Cobertura y errores de búsqueda

Se sustituyó el rectángulo fijo que excluía 999 paraderos. La cobertura se
calcula desde los **4067 paraderos urbanos**, todas las sedes del catálogo y
un margen peatonal de **1600 metros**. Se conserva aunque el modo institucional
todavía no tenga feed. El GTFS actual abarca latitudes -8.2240322 a -7.9920813
y longitudes -79.1232817 a -78.946644, antes del margen.

La consulta restringe su rectángulo y filtra también las coordenadas recibidas;
un resultado fuera del área no se convierte en destino local. El texto se
serializa como JSON para conservar comillas y saltos de línea correctamente.
La cobertura se amplía al cambiar los recursos del catálogo en una nueva versión.

Se distinguen ausencia de configuración, autorización rechazada, fallo de red
y búsqueda sin resultados. Un error antiguo no modifica una selección nueva.
Si no hay proveedor peatonal disponible, el planificador conserva un itinerario
válido con caminatas identificadas como aproximadas, conforme a las fases
anteriores. El idioma de consultas y la ampliación a seis idiomas siguen
en la fase 5.

## Resultados locales

| Comprobación | Resultado |
|---|---|
| Pruebas Android unitarias | **212**, cero fallos, errores u omisiones |
| Regresiones añadidas en esta fase | 32: cuentas/canales/TLS/modelos, cobertura/configuración y errores de búsqueda |
| Pruebas Python de herramientas | **25** aprobadas |
| Referencias de comportamiento iOS | 27 ejecutadas: 11 ETA, 8 rutas y 8 continuidad |
| Recursos y fuentes iOS fijados | 71 recursos y 12 fuentes cotejados |
| Compilación | APK debug y APK de pruebas para dispositivo generados |
| Análisis Android | Cero errores, 81 advertencias y 16 sugerencias |
| APK | Seis mallas 3D, catálogos y pantallas comprobados |
| Configuración efectiva | Metadato inspeccionado; campos MQTT y marcadores de credenciales excluidos |

Se compiló además una variante con `RUTAUTP_CI_MAPS_SENTINEL` y propiedades
MQTT sintéticas. El verificador confirmó que el marcador Google era el valor
efectivo y que los dos secretos de prueba no aparecían en los DEX. Después se
regeneró el APK con la configuración habitual. Los informes solo muestran
indicadores y hashes; no imprimen claves.

APK final: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `2e217cf79b5fafdf1c85c55705b8cbd23cef94babb0c98d342939cc807ce2280`.
Informes: `app/build/reports/parity/configuration-check.json`,
`configuration-sentinel-check.json` y `reference-check.json`.

Las tres pruebas instrumentales nuevas verifican cifrado/reapertura y clave
no exportable, manipulación de contenido y copia sin la clave correspondiente.
Usan archivos y alias de prueba aislados y no abren actividades ni conexiones.
**Están compiladas, no ejecutadas**: `adb devices` no encontró teléfonos ni
emuladores el 13/11/2026. Las simulaciones de repositorios independientes en
JVM tampoco sustituyen dos instalaciones físicas.

Reproducción con Java 21 y Android SDK 36:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --continue --max-workers=1 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
python -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verificar_paridad.py --require-results
python tools/verificar_apk.py
python tools/verificar_configuracion.py
```

El modo offline necesita dependencias en caché. El comprobador de paridad
admite además `--ios RUTA_CHECKOUT_IOS` para cotejar la referencia local.
El workflow amplía estas comprobaciones con el marcador sintético y compila
las pruebas instrumentales; no las ejecuta en un dispositivo.

## Validación externa pendiente para cerrar la fase

1. Configurar Cloud y comprobar mapa, búsqueda y caminatas con las firmas y
   restricciones reales; demostrar rechazo de identidades incorrectas.
2. Instalar el mismo APK en dos teléfonos y asignarles cuentas contribuyentes
   distintas, con cuentas lectoras separadas cuando se utilicen ambos roles.
3. Probar TLS válido, CA inválida, nombre de servidor incorrecto, contraseña
   rechazada, pérdida de conexión y reconexión con los permisos reales del broker.
4. Revocar una cuenta en el servidor y comprobar que la otra instalación sigue
   operativa. Verificar quorum y deduplicación con dos contribuyentes distintos.
5. Ejecutar las tres pruebas de almacenamiento y recorrer el formulario,
   cambio de cuenta durante viaje, segundo plano y reactivación de aportes.

No se realizaron consultas reales de pago a Google ni conexiones/publicaciones
al broker compartido. El feed institucional y la entrega FCM continúan en sus
fases previstas. La revisión visual con iPhone y Android sigue pendiente.
