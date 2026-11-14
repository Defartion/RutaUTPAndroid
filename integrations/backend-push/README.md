# Integración FCM para el backend compartido

Paquete preparado contra main iOS 8145276bb6fc72a168cc78119def2727578ec0bb.
No está incorporado al repositorio iOS ni desplegado.

Extiende el registro y el worker existentes conservando APNs, sus reglas de
quorum, silencio, vigencia y deduplicación. Firebase se habilita por separado,
con proyecto y paquete Android autorizados por la configuración del servidor.

Ver [el resultado, las pruebas y la activación pendiente](../../docs/FASE_7_NOTIFICACIONES_REMOTAS.md).
Para preparar una copia aislada y comprobar los hashes, usar
tools/preparar_backend_push.py y tools/verificar_backend_push.py desde la raíz Android.

Los archivos Python de esta carpeta se superponen a backend/ del checkout
fijado. integrations/fcm_provider.py se incorpora como
backend/rutautp_backend/fcm_provider.py y requirements-fcm.txt queda en backend/.
El resto del servidor se conserva desde la referencia original. Las pruebas
utilizan únicamente proveedores simulados; no iniciar el Bridge para ejecutarlas.
