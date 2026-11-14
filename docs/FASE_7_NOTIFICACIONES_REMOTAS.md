# Fase 7: integración preparada de notificaciones remotas

Trabajo local: **14 de noviembre de 2026**. El equipo confirmó que todavía no
dispone de Firebase ni de un servidor de pruebas y pidió dejar la integración
preparada. No se creó infraestructura ni se enviaron avisos reales.

Antes de esta fase se guardó la fase 6 en **6a2297c804dcf49c373bcc67e67e7ee8b1f62988**
y se publicó main, incluidos los commits pendientes de fases 4 y 5. GitHub
aprobó [su ejecución 43612152671](https://github.com/Defartion/RutaUTPAndroid/actions/runs/43612152671).
La fase 7 se guardó en 08ba24a y su publicación se agrupa con la fase 8.

La referencia continúa en main iOS
8145276bb6fc72a168cc78119def2727578ec0bb. Se cotejan **33 fuentes y 71 recursos**.
El código original iOS y su backend no se modificaron.

## Comparación y cambios

| Área | iOS de referencia | Preparación Android y backend |
|---|---|---|
| Proveedor | APNs, token hexadecimal y entorno Apple | FCM con identificador explícito de proveedor, plataforma Android y aplicación autorizada. |
| Registro | Esquema 1; UUID de instalación y solicitud; revisión creciente | Esquema 2; serializador Android probado contra el mismo JSON que acepta Python. |
| Recibo | Solicitud, revisión, aceptación, disponibilidad y cobertura | Android exige además esquema 2, proveedor FCM, plataforma y paquete correctos para confirmar. Un recibo APNs o legado no presta su confirmación a FCM. |
| Preferencias | Líneas, idioma, zona, pausas, días y horario de silencio, sonido | Mismas reglas; siete vectores compartidos se ejecutan en Kotlin y Python. |
| Vigencia | Registro de 30 días, comprobación del cliente hasta 31 días | Mismos límites; se rechazan recibos vencidos, fuera de rango o antiguos. |
| Elegibilidad | Quorum de reportes, suscripción previa y evento vigente | Se conserva el flujo de RouteChanges antes de encolar, también para FCM. |
| Entrega | Worker APNs con deduplicación y reintentos acotados | El mismo worker selecciona proveedor por inscripción. Un error de configuración FCM detiene ese canal y permite continuar APNs. |
| Baja o rotación | Revisión persistida, retirada de pendientes y protección del intento anterior | Se prueban baja, reinicio y rotación A→B→A; un intento antiguo no recupera su reintento. |
| Presentación | APNs muestra una alerta de comunidad | FCM envía únicamente datos; Android aplica permiso y política local antes de mostrar el aviso. |
| Apertura | Consultar cambios vigentes sin interrumpir el viaje | Se conserva la navegación protegida de fase 3. El mensaje no se convierte en prueba de un cierre. |

Registro aceptado, proveedor listo, aceptación del envío y entrega al teléfono
son hechos distintos. Las pruebas acreditan el contrato y el procesamiento local,
sin afirmar recepción remota.

## Paquete del backend

El repositorio Android contiene una integración aplicable al backend compartido:

- integrations/backend-push/rutautp_backend/push_config.py y push_service.py:
  extensiones de los dos archivos fijados de main.
- integrations/fcm_provider.py: adaptador FCM instalado como
  rutautp_backend/fcm_provider.py.
- integrations/backend-push/requirements-fcm.txt: dependencias de servidor,
  con Firebase Admin 7.8.0 y tzdata 2026.6.
- integrations/backend-push/tests/test_fcm_integration.py: 55 casos nuevos.
- parity/push-contract.json: registros, recibo, evento y reglas temporales
  comunes a Kotlin y Python; todos los identificadores son sintéticos.

El manifiesto registra los hashes de origen y del paquete. Las inscripciones
APNs existentes conservan su representación y su esquema; el proveedor ausente
en sus filas históricas se interpreta como APNs. El proveedor de una instalación
ya registrada no puede cambiarse accidentalmente. La configuración autorizada
del servidor decide el paquete Android y el proyecto Firebase.

El mismo servidor conserva las reglas de ambos proveedores. La herramienta
preparar_backend_push.py extrae solamente backend y gtfs versionados del commit
fijado, aplica la integración a app/build/backend-phase7 y copia las pruebas.
No copia archivos locales ni modifica el checkout iOS ni un servidor en ejecución.

Las pruebas del backend se ejecutaron en **Ubuntu mediante WSL, Python 3.14.4**.
El backend original importa fcntl para sus bloqueos de archivos, una función
de Linux que Windows no ofrece. Por eso se utiliza su entorno previsto para
esas pruebas. Android se compiló y probó en Windows con JDK 21.

## Resultado local

| Comprobación | Resultado |
|---|---|
| Android | 264 pruebas aprobadas; 12 nuevas de contrato, recibos, eventos y silencios. |
| Backend en Ubuntu | 88 aprobadas: 55 nuevas FCM, seis regresiones APNs y 27 de reportes/quorum originales. Sin omisiones. |
| SDK de servidor | El codificador real de Firebase Admin valida datos, TTL y aplicación; su transporte se sustituye por un doble. |
| Transporte APNs | Se comprueba la estructura HTTP/2 y del mensaje original con un cliente simulado. |
| Herramientas | 42 pruebas aprobadas. Los verificadores rechazan paquetes cambiados y resultados incompletos, fallidos u omitidos. |
| Idiomas | 1301 claves y cuatro puntos dinámicos conocidos; cero errores. |
| Análisis Android | Cero errores; 82 advertencias y 16 sugerencias. |
| APK | Generado, seis mallas/catálogos exactos y RegistroPush incluido. Sin el token sintético del contrato ni credenciales MQTT de compilación. |
| Capturas | Las 39 referencias de fase 6 se conservan; esta fase no modifica la interfaz ni regenera imágenes. |
| Dispositivo | APK de pruebas instrumentales compilado; no se ejecutó entrega FCM ni prueba en teléfono. |

SHA-256 del APK debug verificado:
e3034c9c0a0314a27371ad8996876cb13c7b28dfd96292f8ab003b0e99e362ed.

Firebase Admin 7.8.0 advierte que Message.token está deprecado; esta integración
mantiene el camino compatible con el registro que usa el cliente Android actual.
Las nueve advertencias de esa API no se ocultaron. La validación del proyecto
Firebase y una eventual migración conjunta del identificador requieren el entorno real.

El worker solo vuelve a encolar rechazos temporales explícitos. Un resultado de
red incierto se conserva como incierto. Esto no promete entrega exactamente una
vez por el proveedor; el evento mantiene su UUID para la deduplicación del cliente.

## Reproducción

Preparar la copia desde un checkout exacto de main iOS:

~~~powershell
python tools/verificar_backend_push.py
python tools/preparar_backend_push.py --ios C:\RUTA\RutaUTP
~~~

En Linux/Ubuntu, dentro del repositorio Android, usar un entorno Python aislado:

~~~bash
python -m pip install -r app/build/backend-phase7/backend/requirements-dev.txt -r app/build/backend-phase7/backend/requirements-fcm.txt
python -m pytest -q app/build/backend-phase7/backend/tests/test_push_register_numbers.py app/build/backend-phase7/backend/tests/test_push_retry_registration.py app/build/backend-phase7/backend/tests/test_route_changes.py app/build/backend-phase7/backend/tests/test_fcm_integration.py --junitxml=app/build/reports/parity/backend-push-tests.xml
python tools/verificar_backend_push.py --require-results
~~~

La compilación Android habitual utiliza testDebugUnitTest, assembleDebug,
assembleDebugAndroidTest y lintDebug. Después, ejecutar verificar_paridad.py
con --require-results, verificar_idiomas.py, verificar_apk.py y
verificar_configuracion.py --forbid SYNTHETIC_FCM_TOKEN:phase7.

El workflow añade un trabajo Ubuntu independiente que obtiene la misma
referencia iOS y repite esas pruebas sin secretos ni transportes reales.
Ese trabajo nuevo aún no se ha publicado ni ejecutado en GitHub.

## Activación pendiente

Cuando el equipo disponga del entorno:

1. Registrar la aplicación Android en Firebase y aportar app/google-services.json.
2. Revisar e incorporar el paquete preparado al repositorio del backend compartido.
3. Instalar requirements-fcm.txt en el servidor y aportar sus credenciales fuera
   del repositorio, mediante la identidad de servicio del entorno o ADC.
4. Configurar FCM_ENABLED=1, FCM_PROJECT_ID y FCM_ANDROID_PACKAGE. Mantener
   MQTT_TLS=1 y las ACL privadas existentes por cuenta. APNS_ENABLED conserva
   su función independiente.
5. Probar con cuentas y teléfonos del equipo: permiso, token, registro,
   proveedor disponible, envío aceptado y recepción visible. Comprobar bajas,
   expiración, silencios y coexistencia con iPhone.

FCM permanece desactivado por defecto en el servidor preparado. No se ha
aportado configuración Firebase al APK ni se ha contactado al broker.
F07 continúa en curso hasta la incorporación y la validación real.

Referencias oficiales: [recepción de mensajes Android](https://firebase.google.com/docs/cloud-messaging/android/receive-messages),
[Firebase Admin Python](https://firebase.google.com/docs/reference/admin/python/firebase_admin.messaging),
[errores del SDK](https://firebase.google.com/docs/reference/admin/python/firebase_admin.exceptions)
y [vigencia de mensajes](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan).
