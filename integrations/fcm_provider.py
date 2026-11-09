"""Adaptador FCM para conectar al worker de avisos del backend compartido.

No inicia procesos, no registra cuentas y no envía nada al importarse.
El backend debe validar suscripciones, quorum, silencio, vigencia y deduplicación
antes de invocar send_route_change. Ver docs/FIREBASE_ANDROID.md.
"""
from datetime import timedelta
import math
import time
from uuid import UUID


def send_route_change(*, token, event_id, route_id, expires_at, package_name,
                      firebase_app=None, now=None):
    """Devuelve el ID aceptado por FCM; no demuestra entrega al teléfono.

    Requiere firebase-admin en EL SERVIDOR y credenciales fuera del repositorio.
    Las excepciones se devuelven al worker para su política de reintentos.
    """
    from firebase_admin import messaging

    current = time.time() if now is None else now
    UUID(event_id)
    if not isinstance(token, str) or not token or len(token) > 4096:
        raise ValueError("Token FCM inválido")
    if not isinstance(route_id, str) or not route_id or len(route_id) > 128:
        raise ValueError("Ruta inválida")
    if not math.isfinite(expires_at) or not current < expires_at <= current + 910:
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
