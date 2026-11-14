"""Comprueba el paquete de integración y sus pruebas; no acredita entrega remota."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def verify(root, require_results=False):
    root = Path(root).resolve()
    manifest = json.loads((root / "integrations/backend-push/manifest.json").read_text(encoding="utf-8"))
    reference = json.loads((root / "parity/reference.json").read_text(encoding="utf-8"))
    if manifest["referenceIosCommit"] != reference["repositories"]["ios"]["commit"]:
        raise ValueError("La referencia del backend no corresponde a main iOS fijada")
    for item in manifest["packageFiles"]:
        path = (root / item["path"]).resolve()
        if not path.is_relative_to(root):
            raise ValueError("Archivo fuera del repositorio")
        data = path.read_bytes()
        if item.get("hashMode") == "lf":
            data = data.replace(b"\r\n", b"\n")
        if hashlib.sha256(data).hexdigest() != item["sha256"]:
            raise ValueError("Cambió el paquete de integración: " + item["path"])
    report = {"iosCommit": manifest["referenceIosCommit"], "packageVerified": True,
              "testsVerified": False, "deployed": False, "realDeliveryVerified": False}
    if require_results:
        path = root / "app/build/reports/parity/backend-push-tests.xml"
        xml = ET.parse(path).getroot()
        suites = [xml] if xml.tag == "testsuite" else list(xml.iter("testsuite"))
        cases = [case for suite in suites for case in suite.findall("testcase")]
        if not suites or sum(int(s.get("tests", "0")) for s in suites) != len(cases):
            raise ValueError("Conteo de pruebas del backend inconsistente")
        if any(int(s.get(k, "0")) for s in suites for k in ("errors", "failures", "skipped")) or any(
            case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
            raise ValueError("Hay pruebas fallidas u omitidas del backend")
        if len(cases) < manifest["minimumBackendTests"]:
            raise ValueError("Faltan pruebas de integración del backend")
        classes = {case.get("classname") for case in cases}
        if not set(manifest["requiredTestClasses"]).issubset(classes):
            raise ValueError("Faltan pruebas de APNs, quorum o FCM")
        report.update(testsVerified=True, tests=len(cases))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-results", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        report = verify(root, args.require_results)
        target = root / "app/build/reports/parity/backend-push-check.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("Paquete backend verificado; pruebas comprobadas: " + str(report["testsVerified"]) +
              "; despliegue y entrega real pendientes")
        return 0
    except (ValueError, OSError, ET.ParseError) as error:
        print("Error al comprobar la integración: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
