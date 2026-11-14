"""Verifica referencias Compose revisadas; --require-results exige su comparación local."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET


def contained(root, relative):
    target = (root / relative).resolve()
    if not target.is_relative_to(root.resolve()):
        raise ValueError("Ruta fuera del directorio de capturas")
    return target


def dimensions(data):
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Imagen PNG no válida")
    return struct.unpack(">II", data[16:24])


def verify(root, require_results=False):
    manifest = json.loads((root / "parity/android-previews.json").read_text(encoding="utf-8"))
    reference = json.loads((root / "parity/reference.json").read_text(encoding="utf-8"))
    if manifest["referenceIosCommit"] != reference["repositories"]["ios"]["commit"]:
        raise ValueError("La referencia iOS de las capturas es diferente")
    directory = contained(root, manifest["referenceRoot"])
    images = manifest["images"]
    paths = [item["path"] for item in images]
    if len(paths) != len(set(paths)) or len(paths) < manifest["minimumImages"]:
        raise ValueError("Inventario de imágenes incompleto o duplicado")
    if {p.relative_to(directory).as_posix() for p in directory.rglob("*.png")} != set(paths):
        raise ValueError("El inventario no corresponde a los PNG de referencia")
    if {item["id"] for item in manifest["screens"]} != {"map", "routes", "saved", "safety", "profile"}:
        raise ValueError("Falta una pestaña en las capturas")
    for screen in manifest["screens"]:
        if not screen["previewMethods"]:
            raise ValueError("Faltan composiciones de una pestaña")
        for method in screen["previewMethods"]:
            if not any(Path(path).name.startswith(method + "_") for path in paths):
                raise ValueError("Falta la composición " + method)
    for item in images:
        data = contained(directory, item["path"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != item["sha256"]:
            raise ValueError("Cambió una referencia; revisar y registrar: " + item["path"])
        if dimensions(data) != (item["width"], item["height"]):
            raise ValueError("Dimensiones diferentes en " + item["path"])
    report = {"referencesVerified": len(images), "resultsVerified": False,
              "iosDeviceComparison": "pending", "nativeMapAndOpenGL": "pending"}
    if require_results:
        files = list((root / "app/build/test-results/validateDebugScreenshotTest").glob("TEST-*.xml"))
        if not files:
            raise ValueError("Faltan resultados de validateDebugScreenshotTest")
        total = 0
        for file in files:
            suite = ET.parse(file).getroot()
            cases = suite.findall("testcase")
            if int(suite.get("tests", "0")) != len(cases):
                raise ValueError("Conteo de capturas inconsistente")
            if any(int(suite.get(key, "0")) for key in ("failures", "errors", "skipped")):
                raise ValueError("Hay capturas fallidas u omitidas")
            if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
                raise ValueError("Un caso de captura no fue aprobado")
            total += len(cases)
        if total != len(images):
            raise ValueError("No se ejecutaron todas las capturas registradas")
        rendered = root / "app/build/outputs/screenshotTest-results/preview/debug/rendered"
        for item in images:
            data = contained(rendered, item["path"]).read_bytes()
            if hashlib.sha256(data).hexdigest() != item["sha256"]:
                raise ValueError("La imagen renderizada no coincide con la referencia: " + item["path"])
        report.update(resultsVerified=True, tests=total)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-results", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        report = verify(root, args.require_results)
        output = root / "app/build/reports/parity/preview-check.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("Referencias Compose verificadas: " + str(report["referencesVerified"]) +
              "; resultados comprobados: " + str(report["resultsVerified"]) +
              "; comparación con dispositivos iOS/Android pendiente")
        return 0
    except (ValueError, OSError, ET.ParseError) as error:
        print("Error: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
