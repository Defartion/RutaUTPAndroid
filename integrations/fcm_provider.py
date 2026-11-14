"""Adaptador FCM para conectar al worker de avisos del backend compartido.

No inicia procesos, no registra cuentas y no envía nada al importarse.
El backend debe validar suscripciones, quorum, silencio, vigencia y deduplicación
antes de invocar send_route_change. Ver docs/FIREBASE_ANDROID.md.
"""
from datetime import timedelta
import math
import time
from uuid import UUID
import uuid


def _finite(value):
    try:
        return not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value)
    except OverflowError:
        return False


def send_route_change(*, token, event_id, route_id, expires_at, package_name,
                      firebase_app=None, now=None):
    """Devuelve el ID aceptado por FCM; no demuestra entrega al teléfono.

    Requiere firebase-admin en EL SERVIDOR y credenciales fuera del repositorio.
    Las excepciones se devuelven al worker para su política de reintentos.
    """
    from firebase_admin import messaging

    current = time.time() if now is None else now
    if not isinstance(event_id, str) or str(UUID(event_id)) != event_id.lower():
        raise ValueError("Evento inválido")
    if not isinstance(token, str) or not token or len(token) > 4096 or any(not 33 <= ord(c) <= 126 for c in token):
        raise ValueError("Token FCM inválido")
    if not isinstance(route_id, str) or not route_id.strip() or len(route_id) > 128 or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in route_id):
        raise ValueError("Ruta inválida")
    if not _finite(current) or not _finite(expires_at) or not current < expires_at <= current + 910:
        raise ValueError("Evento vencido o vigencia inválida")
    if not package_name or not isinstance(package_name, str):
        raise ValueError("Falta el identificador Android permitido por el servidor")
    message = messaging.Message(
        token=token,
        data={"schemaVersion": "1", "kind": "routeChange", "eventID": event_id,
              "routeID": route_id, "expiresAt": str(expires_at)},
        android=messaging.AndroidConfig(
            priority="high", ttl=timedelta(seconds=expires_at - current),
            restricted_package_name=package_name),
    )
    return messaging.send(message, app=firebase_app)


class FCMProvider:
    """Mismo contrato de resultados que APNs; no registra aceptación como entrega."""
    def __init__(self, config, firebase_app=None):
        import firebase_admin
        self.config = config
        self.owns_app = firebase_app is None
        self.app = firebase_app or firebase_admin.initialize_app(
            options={"projectId": config.fcm_project_id, "httpTimeout": 5},
            name="rutautp-fcm-" + uuid.uuid4().hex)
        if self.app.project_id != config.fcm_project_id:
            if self.owns_app:
                firebase_admin.delete_app(self.app)
            raise ValueError("Proyecto Firebase distinto del autorizado")

    def send(self, token, event, settings):
        from firebase_admin import messaging, exceptions
        if settings.get("provider") != "fcm" or settings.get("packageName") != self.config.fcm_package:
            return "provider_error"
        try:
            send_route_change(token=token, event_id=event["id"], route_id=event["routeId"],
                              expires_at=event["expiresAt"], package_name=self.config.fcm_package,
                              firebase_app=self.app)
            return "accepted"
        except messaging.UnregisteredError:
            return "invalid_token"
        except (messaging.SenderIdMismatchError, messaging.ThirdPartyAuthError,
                exceptions.UnauthenticatedError, exceptions.PermissionDeniedError, ValueError):
            return "provider_error"
        except (exceptions.ResourceExhaustedError, exceptions.UnavailableError, exceptions.InternalError) as error:
            # Sin respuesta HTTP no sabemos si el proveedor llegó a aceptar el envío.
            response = getattr(error, "http_response", None)
            return "retry" if response is not None and response.status_code in (429, 500, 503) else "uncertain"
        except exceptions.FirebaseError:
            return "uncertain"

    def close(self):
        if self.owns_app:
            import firebase_admin
            firebase_admin.delete_app(self.app)
