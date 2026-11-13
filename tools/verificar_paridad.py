"""Verifica la referencia de paridad sin red, teléfono ni credenciales.

python tools/verificar_paridad.py [--ios CHECKOUT_IOS] [--require-results]
Los JSON de referencia son especificaciones; los casos pendientes se informan
como pendientes y nunca se cuentan como pruebas de comportamiento aprobadas.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REFERENCE_EXECUTORS = {
    "travel-state": ("STATE-", "com.example.rutautpnative.IosTravelStateReferenceTest"),
    "eta": ("ETA-", "com.example.rutautpnative.IosEtaReferenceTest"),
    "route-comparison": ("ROUTE-", "com.example.rutautpnative.IosRouteComparisonReferenceTest"),
}


def load_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Clave JSON duplicada: " + key)
            result[key] = value
        return result

    def invalid(value):
        raise ValueError("Número JSON no finito: " + value)

    return json.loads(path.read_text(encoding="utf-8-sig"),
                      object_pairs_hook=unique, parse_constant=invalid)


def contained(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError("Ruta fuera del repositorio: " + relative)
    return path


def sha256(path, mode="binary"):
    data = path.read_bytes()
    if mode == "lf":
        # Git puede convertir LF a CRLF en Windows. Se fija el contenido textual
        # con LF; los binarios conservan una comparación byte a byte.
        data = data.replace(b"\r\n", b"\n")
    elif mode != "binary":
        raise ValueError("Modo de hash desconocido: " + mode)
    return hashlib.sha256(data).hexdigest()


def validate_fixtures(root, reference):
    directory = root / "app/src/test/resources/parity"
    finding_rows = load_json(root / "parity/findings.json")["findings"]
    findings = {row["id"]: row for row in finding_rows}
    expected_findings = {"F" + str(index).zfill(2) for index in range(1, 16)}
    if len(findings) != len(finding_rows) or findings.keys() != expected_findings:
        raise ValueError("El seguimiento debe incluir F01–F15 una sola vez")
    for row in finding_rows:
        if not row["acceptance"] or not row["validation"] or row["status"] not in {"pending", "in_progress", "verified"}:
            raise ValueError("Hallazgo sin criterio o estado válido: " + row["id"])
    seen = set()
    summary = {}
    baseline_ids = []
    for name, minimum in reference["minimumFixtureCases"].items():
        fixture = load_json(directory / (name + ".json"))
        if fixture["schemaVersion"] != 1 or fixture["referenceIosCommit"] != reference["repositories"]["ios"]["commit"]:
            raise ValueError("Referencia diferente en fixture: " + name)
        if len(fixture["cases"]) < minimum:
            raise ValueError("Se redujeron los casos de referencia: " + name)
        summary[name] = {"baseline": [], "pending": []}
        for case in fixture["cases"]:
            identifier = case["id"]
            if identifier in seen:
                raise ValueError("Caso duplicado: " + identifier)
            seen.add(identifier)
            status = case["status"]
            if status not in summary[name]:
                raise ValueError("Estado de caso desconocido: " + identifier)
            if status == "pending":
                if case["finding"] not in findings or case["phase"] not in findings[case["finding"]]["phases"]:
                    raise ValueError("Caso sin hallazgo/fase válida: " + identifier)
            else:
                if name not in REFERENCE_EXECUTORS:
                    raise ValueError("Falta un ejecutor para activar este caso: " + identifier)
                baseline_ids.append(identifier)
            if name == "eta":
                for coordinate in [case["position"], case["target"]] + case.get("shape", fixture["route"]["shape"]):
                    if (len(coordinate) != 2 or not all(type(value) in (int, float) and math.isfinite(value) for value in coordinate)
                            or not -90 <= coordinate[0] <= 90 or not -180 <= coordinate[1] <= 180):
                        raise ValueError("Coordenada inválida en " + identifier)
                expected = case["expectedMinutes"]
                if expected is not None and (type(expected) is not int or not 1 <= expected <= 120):
                    raise ValueError("ETA esperada inválida en " + identifier)
            summary[name][status].append(identifier)
    if not set(reference["requiredBaselineCaseIds"]).issubset(baseline_ids):
        raise ValueError("Se retiró un caso de comportamiento ya verificado")
    visuals = load_json(root / "parity/visual-states.json")
    if {screen["id"] for screen in visuals["screens"]} != {"map", "routes", "saved", "safety", "profile"}:
        raise ValueError("Falta una pestaña en la revisión visual")
    for screen in visuals["screens"]:
        if len(screen["states"]) < 5:
            raise ValueError("Faltan estados visuales en " + screen["id"])
    return summary, baseline_ids


def verify_results(root, minimum, baseline_ids):
    files = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    if not files:
        raise ValueError("Faltan los resultados JUnit; ejecutar testDebugUnitTest")
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    reference_names = {suite: [] for _, suite in REFERENCE_EXECUTORS.values()}
    for file in files:
        suite = ET.parse(file).getroot()
        if int(suite.attrib["tests"]) != len(suite.findall("testcase")):
            raise ValueError("El conteo JUnit no coincide con sus casos: " + file.name)
        for key in totals:
            totals[key] += int(suite.attrib[key])
        if suite.get("name") in reference_names:
            reference_names[suite.get("name")].extend(case.get("name", "") for case in suite.findall("testcase"))
    if totals["failures"] or totals["errors"] or totals["skipped"]:
        raise ValueError("Hay pruebas fallidas, con error u omitidas: " + str(totals))
    if totals["tests"] < minimum:
        raise ValueError("La ejecución contiene menos de " + str(minimum) + " pruebas")
    for identifier in baseline_ids:
        names = next((reference_names[suite] for prefix, suite in REFERENCE_EXECUTORS.values()
                      if identifier.startswith(prefix)), [])
        if not any(name.endswith("[" + identifier + "]") for name in names):
            raise ValueError("No se ejecutó el caso de referencia " + identifier)
    lint = root / "app/build/reports/lint-results-debug.xml"
    if not lint.is_file():
        raise ValueError("Falta el resultado lintDebug")
    issues = ET.parse(lint).getroot().findall("issue")
    if any(issue.get("severity") in {"Error", "Fatal"} for issue in issues):
        raise ValueError("El análisis de Android contiene errores")
    return {"unitTests": totals,
            "referenceEtaCasesExecuted": len(reference_names[REFERENCE_EXECUTORS["eta"][1]]),
            "referenceStateCasesExecuted": len(reference_names[REFERENCE_EXECUTORS["travel-state"][1]]),
            "referenceRouteCasesExecuted": len(reference_names[REFERENCE_EXECUTORS["route-comparison"][1]]),
            "lintWarnings": sum(issue.get("severity") == "Warning" for issue in issues),
            "lintHints": sum(issue.get("severity") == "Hint" for issue in issues)}


def verify(root, ios=None, require_results=False):
    root = root.resolve()
    reference = load_json(root / "parity/reference.json")
    if reference["schemaVersion"] != 1:
        raise ValueError("Versión de referencia desconocida")
    for repo in reference["repositories"].values():
        if repo["branch"] != "main" or not re.fullmatch("[0-9a-f]{40}", repo["commit"]):
            raise ValueError("La referencia debe fijar main y un commit completo")
    if reference["backendContracts"]["referenceCommit"] != reference["repositories"]["ios"]["commit"]:
        raise ValueError("El backend y la referencia iOS deben fijar el mismo commit")
    resources = reference["sharedResources"]
    if len(resources) != reference["sharedResourceCount"]:
        raise ValueError("Se redujo el inventario de recursos compartidos")
    paths = [item["androidPath"] for item in resources]
    if len(set(paths)) != len(paths):
        raise ValueError("Hay recursos duplicados")
    for item in resources:
        actual = contained(root, item["androidPath"])
        if sha256(actual, item.get("hashMode", "binary")) != item["sha256"]:
            raise ValueError("Cambió el recurso de referencia: " + item["androidPath"])
    if ios is not None:
        ios = ios.resolve()
        revision = subprocess.check_output(["git", "-C", str(ios), "rev-parse", "HEAD"], text=True).strip()
        if revision != reference["repositories"]["ios"]["commit"]:
            raise ValueError("El checkout iOS no coincide con el commit fijado")
        for item in resources:
            if sha256(contained(ios, item["iosPath"]), item.get("hashMode", "binary")) != item["sha256"]:
                raise ValueError("El recurso iOS difiere: " + item["iosPath"])
        for item in reference["iosSources"]:
            if sha256(contained(ios, item["path"]), item.get("hashMode", "binary")) != item["sha256"]:
                raise ValueError("Cambió la fuente de contrato iOS: " + item["path"])
    fixtures, baseline_ids = validate_fixtures(root, reference)
    report = {"referenceIosCommit": reference["repositories"]["ios"]["commit"],
              "androidBaselineCommit": reference["repositories"]["android"]["commit"],
              "sharedResourcesVerified": len(resources), "iosCheckoutVerified": ios is not None,
              "fixtureCases": fixtures,
              "pendingBehaviorCases": sum(len(group["pending"]) for group in fixtures.values()),
              "resultsVerified": require_results}
    if require_results:
        report["checks"] = verify_results(root, reference["minimumUnitTests"], baseline_ids)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--ios", type=Path)
    parser.add_argument("--require-results", action="store_true")
    arguments = parser.parse_args()
    try:
        report = verify(arguments.root, arguments.ios, arguments.require_results)
        output = arguments.root / "app/build/reports/parity"
        output.mkdir(parents=True, exist_ok=True)
        (output / "reference-check.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("Referencia verificada: " + str(report["sharedResourcesVerified"]) + " recursos")
        print("Casos pendientes de corrección: " + str(report["pendingBehaviorCases"]) + " (no se cuentan como aprobados)")
        if arguments.require_results:
            print("Pruebas y análisis: " + str(report["checks"]))
        return 0
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError, subprocess.CalledProcessError) as error:
        print("ERROR de paridad: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
