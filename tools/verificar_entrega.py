"""Crea evidencia de una candidata local; no declara probados teléfonos o servicios."""
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import zipfile


def text_hash(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def performance(root, contract):
    report = json.loads((root / "app/build/reports/parity/performance-check.json").read_text(encoding="utf-8"))
    if (report["environment"] != "host-jvm" or report["androidDeviceMeasured"] is not False
            or report["networkUsed"] is not False or report["equivalentResults"] is not True
            or report["baselineCommit"] != contract["baselineCommit"]):
        raise ValueError("La medición local no acredita equivalencia o confunde host y dispositivo")
    if report["vehicles"] < contract["minimumVehicles"] or report["samples"] < contract["minimumSamples"]:
        raise ValueError("Medición de rendimiento incompleta")
    if set(report["sourceHashes"]) != set(contract["sourcePaths"]):
        raise ValueError("Faltan fuentes de la medición")
    for path, digest in report["sourceHashes"].items():
        if text_hash(root / path) != digest:
            raise ValueError("La medición no corresponde al código actual: " + path)
    for item in contract["oracles"]:
        if text_hash(root / item["path"]) != item["sha256"]:
            raise ValueError("Cambió el cálculo anterior utilizado como referencia")
    for name in contract["timings"]:
        row = report[name]
        if any(type(row[k]) not in (int, float) or not math.isfinite(row[k]) or row[k] < 0 for k in ("medianMs", "p95Ms")):
            raise ValueError("Tiempo inválido en " + name)
        if row["medianMs"] > row["p95Ms"]:
            raise ValueError("Percentiles inconsistentes")
    if (report["geometryPreparationsWarm"] > report["routes"] or report["targetProjectionsWarm"] > report["routes"]
            or not 0 <= report["nearbyCandidates"] <= report["nearbyFullCandidates"]):
        raise ValueError("El trabajo medido no respeta los límites de caché")
    return report


def storage(root, contract):
    for item in contract["storageFiles"]:
        source = (root / item["path"]).read_text(encoding="utf-8").replace("\r\n", "\n")
        if item.get("decoderVisibilityOnly"):
            source = source.replace("internal fun deserializar", "private fun deserializar")
        if hashlib.sha256(source.rstrip("\n").encode("utf-8")).hexdigest() != item["sha256"]:
            raise ValueError("Cambió un formato o almacén persistente; revisar compatibilidad: " + item["path"])
    return {"sourceContractsVerified": len(contract["storageFiles"]), "installationUpgradeVerified": False}


def checker(name):
    path = Path(__file__).with_name(name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def verify(root):
    contract = json.loads((root / "parity/performance-contract.json").read_text(encoding="utf-8"))
    reference = json.loads((root / "parity/reference.json").read_text(encoding="utf-8"))
    if contract["referenceIosCommit"] != reference["repositories"]["ios"]["commit"]:
        raise ValueError("La candidata no usa la referencia iOS fijada")
    measurements = performance(root, contract)
    persistent = storage(root, contract)
    parity = checker("verificar_paridad").verify(root, require_results=True)
    languages = checker("verificar_idiomas").verify(root)
    if languages["errors"]:
        raise ValueError("Hay errores en idiomas")
    previews = checker("verificar_capturas").verify(root)
    backend = checker("verificar_backend_push").verify(root, require_results=True)
    apk = root / "app/build/outputs/apk/debug/app-debug.apk"
    checker("verificar_apk").verificar(apk, root / "app/src/main/assets")
    config = checker("verificar_configuracion")
    configuration = config.verificar(apk, config.aapt_disponible(), prohibidos=["SYNTHETIC_FCM_TOKEN:phase7"])
    instrumentation = root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    with zipfile.ZipFile(instrumentation) as archive:
        dex = b"".join(archive.read(name) for name in archive.namelist() if name.endswith(".dex"))
        if b"EntregaFase8InstrumentedTest" not in dex:
            raise ValueError("Faltan las pruebas instrumentales nuevas en su APK")
    metadata = json.loads((apk.parent / "output-metadata.json").read_text(encoding="utf-8"))
    version = metadata["elements"][0]
    return {
        "status": "local_candidate_device_validation_pending",
        "applicationId": metadata["applicationId"], "versionName": version["versionName"], "versionCode": version["versionCode"],
        "referenceIosCommit": contract["referenceIosCommit"], "baselineCommit": contract["baselineCommit"],
        "apk": {"path": apk.relative_to(root).as_posix(), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "bytes": apk.stat().st_size},
        "instrumentationApk": {"sha256": hashlib.sha256(instrumentation.read_bytes()).hexdigest(), "executed": False},
        "checks": parity["checks"], "languagesVerified": True, "previewReferences": previews["referencesVerified"],
        "backendTests": backend["tests"], "configurationVerified": bool(configuration),
        "storage": persistent, "performance": measurements,
        "iosDeviceComparison": "pending", "realMapsAndGPS": "pending", "realOpenGL": "pending",
        "talkBackOnDevice": "pending", "remoteDelivery": "pending"
    }


def main():
    root = Path(__file__).resolve().parents[1]
    try:
        report = verify(root)
        output = root / "app/build/reports/parity/release-candidate.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("Candidata local verificada: " + report["apk"]["sha256"])
        print("Medición JVM y contratos locales comprobados; dispositivo y servicios reales pendientes")
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print("No se pudo verificar la candidata: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
