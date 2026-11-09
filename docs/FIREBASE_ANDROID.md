# Avisos remotos en Android

La contraparte de APNs es **Firebase Cloud Messaging (FCM)**. El proyecto incluye
su SDK, `RutaFirebaseMessagingService`, registro del token, recepción de cambios
de ruta, deduplicación y navegación al aviso. No necesita una conexión MQTT en
segundo plano para recibir un mensaje FCM.

## Estado de esta actualización

El equipo todavía no tiene un proyecto Firebase. Por eso la aplicación compila
sin `google-services.json`, mantiene FCM desactivado y muestra que falta esa
configuración. Los recordatorios locales funcionan con NotificationManager y
AlarmManager y no dependen de Firebase. No se han creado cuentas, desplegado
servicios, registrado dispositivos ni enviado notificaciones a usuarios.

## Configuración de la aplicación

1. Crear el proyecto Firebase del equipo y registrar la aplicación Android con
   el identificador **com.example.rutautpnative**. Si se cambia este identificador,
   actualizarlo también en Android, Firebase y el backend.
2. Descargar su configuración de aplicación y colocarla en
   `app/google-services.json`. Gradle aplica automáticamente el complemento
   Google Services cuando el archivo existe. No usar configuraciones de ejemplo.
3. Compilar e instalar la app. En Notificaciones, activar el permiso de Android,
   guardar las líneas que interesan y activar **Cambios en mis líneas guardadas**.
4. Configurar una cuenta MQTT propia por instalación con TLS verificado. Una
   cuenta de lectura de presentación no puede registrar aportes ni avisos.

La inicialización automática de FCM está desactivada por defecto. El permiso
nativo, la obtención del token FCM, el recibo del backend y la entrega real son
estados distintos. La pantalla solo confirma el registro al recibir el recibo
correspondiente, con revisión, proveedor disponible y vigencia válidos.
La identidad de esta instalación, las revisiones y la deduplicación se guardan
en `rutautp.push`, excluido de copias y transferencias del sistema.

## Cambio necesario en el backend compartido

La main iOS `138900d06bf57a8835ad6c4261f3b608cf6b66b5` solo implementa registros
APNs de esquema 1 y tokens hexadecimales. **Un token FCM no debe enviarse al
proveedor APNs ni presentarse como si el backend ya lo aceptase.**

Android publica por MQTT/TLS en `rutautp/notificaciones/{principal}/registro`
y escucha `rutautp/notificaciones/{principal}/recibo`, con las mismas ACL
privadas por cuenta documentadas en el backend iOS. El registro Android usa:

```json
{
  "schemaVersion": 2,
  "provider": "fcm",
  "platform": "android",
  "packageName": "com.example.rutautpnative",
  "requestID": "UUID de la solicitud",
  "deviceID": "UUID estable de la instalación",
  "revision": 1,
  "timestamp": 0,
  "token": "token proporcionado por FCM",
  "enabled": true,
  "routes": ["route_id del GTFS"],
  "language": "es",
  "timeZone": "America/Lima",
  "pauseUntil": null,
  "mutedDays": [],
  "quietHours": false,
  "quietStart": 1320,
  "quietEnd": 420,
  "sound": true
}
```

El ejemplo describe tipos; sus UUID y token deben ser valores reales. El servidor
debe añadir validación de este esquema y almacenar el proveedor por registro,
conservar la revisión creciente, la caducidad de 30 días y todas sus verificaciones
de quorum, suscripción, silencios, permiso de cuenta y deduplicación. Su recibo
conserva `requestID`, `revision`, `accepted`, `providerReady`, `leaseUntil` y `code`.
Hasta adaptar y desplegar ese backend, la app no anunciará registro confirmado.

Se incluye `integrations/fcm_provider.py` como adaptador del worker de entrega.
Requiere `firebase-admin` **en el servidor**, autenticado mediante credenciales
de servicio del proyecto Firebase, guardadas fuera del repositorio. No introduce
credenciales de servicio en el APK. Debe conectarse al worker existente después
de las comprobaciones anteriores; importarlo no inicia un servicio ni envía nada.

El backend enviará **mensajes de datos**, sin un bloque `notification` que el
sistema pudiera mostrar saltándose la política local:

```json
{
  "schemaVersion": "1",
  "kind": "routeChange",
  "eventID": "UUID del evento",
  "routeID": "route_id del GTFS",
  "expiresAt": "segundos UNIX de vencimiento"
}
```

La app comprueba ruta guardada, permiso, activación, pausas, silencio y evento
no vencido antes de mostrarlo. Tocar el aviso consulta los cambios actuales;
no convierte el mensaje en prueba de un cierre ni interrumpe un viaje activo.
La aceptación por FCM no demuestra entrega al teléfono. La verificación real
requiere Firebase configurado, el backend adaptado y un dispositivo con Google
Play Services. No se ha verificado una entrega real en esta actualización.

Referencias oficiales: [configurar FCM en Android](https://firebase.google.com/docs/cloud-messaging/android/get-started),
[recibir mensajes](https://firebase.google.com/docs/cloud-messaging/android/receive-messages).
