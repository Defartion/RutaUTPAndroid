# Persistencia local de RutaUTP Android

Los nombres siguientes son los que utiliza el código Android. Las claves
históricas del inventario iOS no deben confundirse con los nombres de archivo
y claves de DataStore. No se renombran los almacenes existentes.

## DataStore Preferences

| Archivo lógico | Claves | Contenido |
|---|---|---|
| `lugares_guardados_v1` | `lugares_json` | Lista JSON de lugares, categorías, iconos, frecuencia e ID de paradero. |
| `lineas_guardadas_v1` | `lineas_json` | Referencias JSON a routeId del GTFS. |
| `tema_prefs_v1` | `isDarkMode` | Apariencia. |
| `idioma_prefs_v1` | `idioma_codigo` | es, en, fr, pt-BR, de o zh-Hans. |
| `senias_prefs_v1` | `senias_modo_activado` | Preferencia de señas. |
| `cupones_guardados_v1` | `cupones_ids` | IDs de cupones guardados, serializados. |
| `seguridad_tiles_v1` | `elegidos`, `orden` | Tarjetas de seguridad y orden, en JSON. |
| `monedero_v1` | `monedero.saldo.v1`, `monedero.movimientos.v1` | Saldo Long en céntimos y hasta 20 movimientos JSON. Monedero de demostración. |

Los archivos físicos de Preferences DataStore terminan en `.preferences_pb`
dentro de `files/datastore/`. Los cambios de lugares y líneas son reactivos;
las modificaciones y operaciones del monedero se serializan.

Los lugares anteriores sin `esFijo` y llamados UTP se interpretan como fijos.
Una escritura nueva incluye explícitamente esFijo, incluso cuando vale false.
Se conservan los IDs existentes; las semillas nuevas tienen IDs estables.
Esta compatibilidad se aplica al leer LugaresStore. El sello global de esquema
`persistencia.esquema.version` sigue en 1 y no ejecuta esa conversión por sí solo.

## SharedPreferences

| Archivo | Claves o patrón | Contenido |
|---|---|---|
| `rutautp.persistencia` | `persistencia.esquema.version` | Versión global. |
| mismo | `perfil_telefono`, `perfil_correoPersonal`, `perfil_notificaciones`, `perfil_compartirUbicacion` | Preferencias del perfil. |
| mismo | `emergencia_nombre`, `emergencia_parentesco`, `emergencia_numero`, `emergencia_numeroAlternativo` | Contacto de emergencia común a Perfil y Seguridad. |
| mismo | `apariencia.tematica.empresa.v1` | Temática. |
| mismo | `inicio.sede.trabajo.id.v1`, `inicio.referencia.utp.v1`, `transporte.buses.utp.v1` | Sede y modo de transporte. |
| mismo | `perfil.carnet.<foto/digital>.<empresa>.visible.v1` | Visibilidad de carnés por empresa. |
| mismo | `mapa.micros-visibles.v1` | Límite de vehículos; 0 significa todos. |
| mismo | `notificaciones.config.v1`, `notificaciones.alarmas.v1` | Agenda JSON e identificadores de alarmas locales. |
| `rutautp.tracking` | `rutautp.passive-tracking-consent` | Consentimiento para contribuir con ubicación. |
| mismo | `rutautp.boarding-place-notes.v1` | Hasta 200 referencias locales de subida. |
| mismo | `tracking.radioParadero`, `tracking.velocidadDemo` | Ajustes de demostración. |
| `rutautp.push` | `notificaciones.push.*` | Activación FCM, UUID de instalación, revisión creciente y eventos recibidos. Excluido de copia y transferencia del sistema. |

FCM conserva su token mediante su propio SDK; la aplicación no lo utiliza como
credencial MQTT. No se incluyen credenciales de servicio Firebase en Android.

## Archivos y cifrado

- `noBackupFilesDir/conexion-v1/credenciales.bin`: configuración versionada de
  cuentas MQTT por canal y rol, y clave web de búsqueda. Archivo atómico cifrado
  con AES-256/GCM y clave `rutautp.conexion.aes.v1.conexion-v1` de Android Keystore.
  No se incluye en copia ni transferencia del sistema. Si falta la clave o el
  contenido es ilegible, se conserva el archivo y se bloquea la escritura normal;
  el usuario puede borrar explícitamente esta configuración desde Perfil.
- `rutautp_tarjetas.xml`, mediante EncryptedSharedPreferences y Android Keystore:
  clave `local` con nombre, red y últimos cuatro dígitos ASCII. Nunca PAN ni CVV.
  Se excluye de copia y transferencia del sistema. Si no hay cifrado disponible,
  la app no crea una copia en texto plano.
- `files/perfil_foto.jpg` y `files/carnet-<empresa>.jpg`: imágenes finales recortadas.
- `noBackupFilesDir/tracking-control/<hash-endpoint>.json`: checkpoint atómico
  del control de simulación, revisiones y ejecuciones retiradas. Recuperar el
  checkpoint no concede una autorización de simulación: hace falta control vigente.
- `files/recuperacion/*.json`: respaldos anteriores al restablecimiento explícito
  de lugares, líneas, monedero o notificaciones. Se pueden exportar desde Perfil.

Un JSON ilegible de esos cuatro grupos se conserva y bloquea su sobrescritura
normal. El restablecimiento solicitado primero archiva el contenido y después
lo reemplaza. Esto no es recuperación de un archivo protobuf físicamente dañado
ni una función de importar/restaurar respaldos.

## Datos de sesión

El nombre de demostración del perfil, la ciudad elegida, los votos comunitarios,
la flota y el viaje activo no constituyen datos de cuenta sincronizados. La
ubicación se publica solo durante la contribución consentida en primer plano.
Cerrar el viaje no implica borrar el histórico que conserve el backend compartido.
