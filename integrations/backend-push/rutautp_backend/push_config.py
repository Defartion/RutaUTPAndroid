"""APNs es optativo: la clave de Apple solo se carga en el servidor."""
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path

from .config import ConfigError, DEFAULT_DATABASE_PATH


@dataclass(frozen=True)
class PushConfig:
    enabled: bool = False
    environment: str = "development"
    team_id: str = ""
    key_id: str = ""
    topic: str = ""
    key_path: str = ""
    database_path: str = ""
    fcm_enabled: bool = False
    fcm_package: str = "com.example.rutautpnative"
    fcm_project_id: str = ""

    @classmethod
    def from_env(cls, database_path: str, tls: bool) -> PushConfig:
        flag = os.environ.get("APNS_ENABLED", "0").lower()
        if flag not in ("0", "1", "false", "true"):
            raise ConfigError("APNS_ENABLED debe ser 0 o 1")
        enabled = flag in ("1", "true")
        fcm_flag = os.environ.get("FCM_ENABLED", "0").lower()
        if fcm_flag not in ("0", "1", "false", "true"):
            raise ConfigError("FCM_ENABLED debe ser 0 o 1")
        values = dict(
            enabled=enabled,
            fcm_enabled=fcm_flag in ("1", "true"),
            fcm_package=os.environ.get("FCM_ANDROID_PACKAGE", "com.example.rutautpnative"),
            fcm_project_id=os.environ.get("FCM_PROJECT_ID", ""),
            environment=os.environ.get("APNS_ENVIRONMENT", "development"),
            team_id=os.environ.get("APNS_TEAM_ID", ""),
            key_id=os.environ.get("APNS_KEY_ID", ""),
            topic=os.environ.get("APNS_TOPIC", ""),
            key_path=os.environ.get("APNS_KEY_PATH", ""),
            database_path=os.environ.get("PUSH_DB_PATH", (database_path or str(DEFAULT_DATABASE_PATH)) + ".push.sqlite"),
        )
        if (enabled or values["fcm_enabled"]) and not tls:
            raise ConfigError("Los avisos remotos requieren MQTT_TLS=1")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", values["fcm_package"]):
            raise ConfigError("FCM_ANDROID_PACKAGE debe identificar la aplicación autorizada")
        if values["fcm_enabled"] and not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", values["fcm_project_id"]):
            raise ConfigError("FCM_PROJECT_ID debe identificar el proyecto Firebase autorizado")
        if enabled:
            if not tls:
                raise ConfigError("APNs requiere MQTT_TLS=1 para registrar dispositivos")
            if values["environment"] not in ("development", "production"):
                raise ConfigError("APNS_ENVIRONMENT debe ser development o production")
            if any(not re.fullmatch(r"[A-Z0-9]{10}", values[k]) for k in ("team_id", "key_id")):
                raise ConfigError("APNS_TEAM_ID y APNS_KEY_ID deben tener 10 caracteres")
            if not re.fullmatch(r"[A-Za-z0-9.-]{1,255}", values["topic"]):
                raise ConfigError("APNS_TOPIC debe ser el bundle ID autorizado por Apple")
            if not Path(values["key_path"]).is_file():
                raise ConfigError("No se encuentra el archivo indicado en APNS_KEY_PATH")
        return cls(**values)
