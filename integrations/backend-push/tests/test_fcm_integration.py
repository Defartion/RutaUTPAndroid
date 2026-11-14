"""Contrato Android/Python y regresiones del worker, sin broker ni llamadas a FCM."""
import json
from contextlib import closing
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import pytest
from rutautp_backend import push_service, fcm_provider
from rutautp_backend.push_config import PushConfig
from rutautp_backend.config import ConfigError
from rutautp_backend.route_changes import RouteChanges

CONTRACT = json.loads(Path(__file__).with_name("push-contract.json").read_text(encoding="utf-8"))
NOW = CONTRACT["now"]
TOPIC = "rutautp/notificaciones/cuenta-prueba/registro"


class Provider:
    def __init__(self):
        self.calls = []
        self.result = "accepted"
        self.during_send = lambda: None
    def send(self, token, event, settings):
        self.calls.append((token, event, settings))
        self.during_send()
        return self.result
    def close(self):
        pass


@pytest.fixture
def service(tmp_path, monkeypatch, feed):
    apns, fcm = Provider(), Provider()
    monkeypatch.setattr(push_service, "APNsProvider", lambda _: apns)
    monkeypatch.setattr(push_service, "FCMProvider", lambda _: fcm)
    monkeypatch.setattr(push_service, "time", SimpleNamespace(time=lambda: NOW + 20))
    config = PushConfig(enabled=True, fcm_enabled=True, fcm_project_id="proyecto-prueba",
                        database_path=str(tmp_path / "push.sqlite"))
    with closing(push_service.PushService(config, feed)) as instance:
        yield instance, apns, fcm


def register(service, body=None, **updates):
    body = dict(body or CONTRACT["fcmRegistration"])
    body.update(updates)
    return service.register(TOPIC, json.dumps(body).encode(), NOW)[1]


def event():
    return {"id": CONTRACT["dataMessage"]["eventID"], "routeId": "17350695",
            "confirmedAt": NOW + 1, "expiresAt": NOW + 300}


def state(service):
    return service.db.execute("SELECT state FROM deliveries ORDER BY rowid").fetchall()


def test_android_registration_and_receipt_match_shared_contract(service):
    s, apns, fcm = service
    assert register(s) == CONTRACT["fcmReceipt"]
    saved = json.loads(s.db.execute("SELECT body FROM devices").fetchone()[0])
    assert saved["provider"] == "fcm" and "environment" not in saved
    assert not apns.calls and not fcm.calls


@pytest.mark.parametrize("updates", [
    {"schemaVersion": True}, {"schemaVersion": 2.0}, {"schemaVersion": 1},
    {"provider": "apns"}, {"provider": "otro"}, {"platform": "ios"},
    {"packageName": "otra.aplicacion"}, {"token": "t con espacios"},
    {"token": "ñ"}, {"token": "x" * 4097}, {"token": ""},
    {"routes": ["ruta-inexistente"]}, {"revision": True},
    {"timestamp": float("inf")}, {"pauseUntil": 10 ** 400},
])
def test_rejects_invalid_fcm_envelopes_without_storing_or_sending(service, updates):
    s, apns, fcm = service
    assert register(s, **updates)["accepted"] is False
    assert s.db.execute("SELECT COUNT(*) FROM devices").fetchone()[0] == 0
    assert not apns.calls and not fcm.calls


def test_disabled_fcm_does_not_claim_apns_readiness(service):
    s, apns, _ = service
    s.fcm_provider = None
    receipt = register(s)
    assert receipt["accepted"] and not receipt["providerReady"] and s.ready
    s.observe([event()], NOW + 5)
    assert state(s) == [] and not apns.calls


def test_fcm_can_run_without_apns(service):
    s, _, fcm = service
    s.provider = None
    assert s.ready and register(s)["providerReady"]
    s.observe([event()], NOW + 5)
    assert s._send_one()
    assert len(fcm.calls) == 1 and state(s) == [("accepted",)]


def test_old_apns_registration_keeps_its_schema_and_provider(service):
    s, apns, fcm = service
    assert register(s, CONTRACT["apnsRegistration"])["accepted"]
    saved = json.loads(s.db.execute("SELECT body FROM devices").fetchone()[0])
    assert "provider" not in saved and saved["environment"] == "development"
    assert register(s, CONTRACT["apnsRegistration"])["accepted"]
    s.observe([event()], NOW + 5)
    assert s._send_one()
    assert len(apns.calls) == 1 and not fcm.calls


def test_same_device_cannot_change_provider_or_replay_old_revision(service):
    s, _, _ = service
    assert register(s)["accepted"]
    assert register(s, CONTRACT["apnsRegistration"],
                    deviceID=CONTRACT["fcmRegistration"]["deviceID"], revision=2)["code"] == "provider"
    assert register(s, revision=2, token="nuevo")["accepted"]
    assert register(s, revision=1)["code"] == "stale"
    assert register(s, revision=2)["code"] == "stale"


def test_revocation_invalidates_pending_delivery_and_survives_restart(service):
    s, _, fcm = service
    register(s); s.observe([event()], NOW + 5)
    assert state(s) == [("pending",)]
    assert register(s, revision=2, enabled=False, token="")["accepted"]
    assert state(s) == [("skipped",)] and not s._send_one() and not fcm.calls
    # Una segunda instancia lee la inscripción y su revisión de la misma base.
    with closing(push_service.PushService(s.config, s.feed)) as reopened:
        assert register(reopened, revision=1)["code"] == "stale"
        saved = json.loads(reopened.db.execute("SELECT body FROM devices").fetchone()[0])
        assert not saved["enabled"]


def test_provider_failure_does_not_stop_other_platform(service):
    s, apns, fcm = service
    register(s); register(s, CONTRACT["apnsRegistration"])
    fcm.result = "provider_error"
    s.observe([event()], NOW + 5)
    while s._send_one():
        pass
    assert len(fcm.calls) == len(apns.calls) == 1
    assert s.fcm_failed and not s.provider_failed and s.ready
    assert not register(s, revision=2)["providerReady"]
    assert register(s, CONTRACT["apnsRegistration"], revision=2)["providerReady"]


@pytest.mark.parametrize("result,expected", [("retry", "pending"), ("uncertain", "uncertain"), ("invalid_token", "invalid_token")])
def test_delivery_results_preserve_retry_and_uncertainty_policies(service, result, expected):
    s, _, fcm = service
    register(s); s.observe([event()], NOW + 5); fcm.result = result
    assert s._send_one() and state(s) == [(expected,)]
    if result == "invalid_token":
        saved = json.loads(s.db.execute("SELECT body FROM devices").fetchone()[0])
        assert not saved["enabled"] and saved["token"] == ""


def test_rotation_back_to_original_token_does_not_restore_old_retry(service):
    s, _, fcm = service
    register(s); s.observe([event()], NOW + 5)
    def rotate():
        register(s, revision=2, token="segundo")
        register(s, revision=3)
    fcm.during_send = rotate; fcm.result = "retry"
    assert s._send_one() and state(s) == [("retry",)]
    assert not s._send_one() and len(fcm.calls) == 1


def test_quorum_subscription_expiry_and_dedup_are_applied_before_fcm(service, sample_route):
    s, _, fcm = service
    register(s)
    changes = RouteChanges(s.feed)
    lat, lon = sample_route.shape[len(sample_route.shape) // 2]
    body = dict(schemaVersion=1, requestId="reporte", routeId=sample_route.route_id,
                reason="works", lat=lat, lon=lon, timestamp=NOW + 10, place="Referencia")
    for index in range(2):
        changes.handle("rutautp/cambios/cuenta-" + str(index) + "/reporte", json.dumps(body), NOW + 10)
        s.observe(changes.confirmed_for_push(NOW + 10 + index * 5), NOW + 10 + index * 5)
        if index == 0:
            assert state(s) == []
    assert s._send_one() and len(fcm.calls) == 1
    s.observe(changes.confirmed_for_push(NOW + 25), NOW + 25)
    assert not s._send_one() and len(fcm.calls) == 1


@pytest.mark.parametrize("kind", ["expired", "removed-quorum", "unsubscribed", "lease"])
def test_no_send_after_delivery_becomes_invalid(service, kind):
    s, _, fcm = service
    register(s); s.observe([event()], NOW + 5)
    if kind == "expired":
        s.db.execute("UPDATE deliveries SET expires=?", (NOW,))
    elif kind == "removed-quorum":
        s.observe([], NOW + 10)
    elif kind == "unsubscribed":
        register(s, revision=2, routes=[])
    else:
        s.db.execute("UPDATE devices SET lease=?", (NOW,))
    s.db.commit()
    s._send_one()
    assert not fcm.calls and state(s) == [("skipped",)]


@pytest.mark.parametrize("case", CONTRACT["policies"], ids=lambda x: x["id"])
def test_shared_android_python_quiet_time_policy(case):
    settings = dict(CONTRACT["fcmRegistration"], timeZone=case["timeZone"])
    settings.update(case["updates"])
    now = datetime.fromisoformat(case["instant"].replace("Z", "+00:00")).timestamp()
    assert push_service.permits(settings, now) == case["allowed"]


def test_environment_requires_tls_and_explicit_project(tmp_path, monkeypatch):
    with patch.dict("os.environ", {"FCM_ENABLED": "1", "FCM_PROJECT_ID": "proyecto-prueba"}, clear=True):
        with pytest.raises(ConfigError):
            PushConfig.from_env(str(tmp_path / "base"), False)
        assert PushConfig.from_env(str(tmp_path / "base"), True).fcm_enabled
    with patch.dict("os.environ", {"FCM_ENABLED": "1"}, clear=True):
        with pytest.raises(ConfigError):
            PushConfig.from_env(str(tmp_path / "base"), True)


def test_real_admin_sdk_encodes_data_only_payload_and_ttl(monkeypatch):
    from firebase_admin import messaging
    encoded = []
    def send(message, app):
        encoded.append(messaging._MessagingService.encode_message(message))
        return "projects/prueba/messages/sintetico"
    monkeypatch.setattr(messaging, "send", send)
    monkeypatch.setattr(fcm_provider, "time", SimpleNamespace(time=lambda: NOW))
    config = SimpleNamespace(fcm_project_id="proyecto-prueba", fcm_package="com.example.rutautpnative")
    provider = fcm_provider.FCMProvider(config, firebase_app=SimpleNamespace(project_id=config.fcm_project_id))
    assert provider.send(CONTRACT["fcmRegistration"]["token"], event(), CONTRACT["fcmRegistration"]) == "accepted"
    payload = encoded[0]
    assert payload["data"] == CONTRACT["dataMessage"]
    assert payload["android"]["ttl"] == "300s"
    assert payload["android"]["restricted_package_name"] == config.fcm_package
    assert "notification" not in payload and "notification" not in payload["android"]


@pytest.mark.parametrize("code,status,expected", [
    ("unregistered", 404, "invalid_token"), ("quota", 429, "retry"),
    ("unavailable", 503, "retry"), ("unavailable", None, "uncertain"),
    ("internal", 500, "retry"), ("deadline", None, "uncertain"),
    ("permission", 403, "provider_error"), ("sender", 403, "provider_error")])
def test_fcm_errors_are_classified_without_logging_tokens(monkeypatch, code, status, expected, caplog):
    from firebase_admin import messaging, exceptions
    classes = {"unregistered": messaging.UnregisteredError, "quota": messaging.QuotaExceededError,
               "unavailable": exceptions.UnavailableError, "internal": exceptions.InternalError,
               "deadline": exceptions.DeadlineExceededError, "permission": exceptions.PermissionDeniedError,
               "sender": messaging.SenderIdMismatchError}
    error = classes[code]("SYNTHETIC_SECRET_NOT_FOR_LOGS",
                          http_response=SimpleNamespace(status_code=status) if status else None)
    def fail(*args, **kwargs):
        raise error
    monkeypatch.setattr(messaging, "send", fail)
    monkeypatch.setattr(fcm_provider, "time", SimpleNamespace(time=lambda: NOW))
    config = SimpleNamespace(fcm_project_id="proyecto-prueba", fcm_package="com.example.rutautpnative")
    provider = fcm_provider.FCMProvider(config, firebase_app=SimpleNamespace(project_id=config.fcm_project_id))
    assert provider.send(CONTRACT["fcmRegistration"]["token"], event(), CONTRACT["fcmRegistration"]) == expected
    assert "SYNTHETIC_SECRET" not in caplog.text and CONTRACT["fcmRegistration"]["token"] not in caplog.text


def test_original_apns_http_payload_remains_compatible(monkeypatch):
    import httpx
    sent = []
    provider = object.__new__(push_service.APNsProvider)
    provider.config = SimpleNamespace(environment="development", topic="com.prueba.ios", team_id="PRUEBA12345", key_id="PRUEBA12345")
    provider.httpx = httpx
    provider.jwt = SimpleNamespace(encode=lambda *args, **kwargs: "firma-sintetica")
    provider.key = object(); provider.authorization = ""; provider.issued_at = 0
    def post(url, headers, json):
        sent.append((url, headers, json))
        return SimpleNamespace(http_version="HTTP/2", status_code=200)
    provider.client = SimpleNamespace(post=post)
    data = dict(event(), routeName="C-01 B", deliveryID="00000000-0000-4000-8000-000000000401")
    assert provider.send("aa", data, CONTRACT["apnsRegistration"]) == "accepted"
    assert sent[0][2]["rutaUTP"]["schemaVersion"] == 1
    assert sent[0][2]["rutaUTP"]["routeID"] == "17350695"
    assert sent[0][2]["aps"]["sound"] == "default"
    assert sent[0][1]["apns-topic"] == "com.prueba.ios"


@pytest.mark.parametrize("expiry", [True, float("nan"), float("inf"), 10 ** 400, NOW, NOW + 911])
def test_invalid_expiry_never_calls_admin_transport(monkeypatch, expiry):
    from firebase_admin import messaging
    def forbidden(*args, **kwargs):
        pytest.fail("No debe intentarse un envío")
    monkeypatch.setattr(messaging, "send", forbidden)
    with pytest.raises(ValueError):
        fcm_provider.send_route_change(token="sintetico", event_id=CONTRACT["dataMessage"]["eventID"],
            route_id="17350695", expires_at=expiry, package_name="com.example.rutautpnative", now=NOW)
