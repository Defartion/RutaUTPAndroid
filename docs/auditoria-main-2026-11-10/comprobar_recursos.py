"""Comprobaciones de recursos y textos para la comparativa de main.

Uso: python comprobar_recursos.py --ios RUTA_IOS --android RUTA_ANDROID
Solo lee los repositorios; escribe evidencia.json junto a este archivo.
No consulta servicios externos ni abre credenciales locales.
"""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table_rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def literals(root, pattern, catalog):
    # Barrido de primeros argumentos literales simples de L.t.
    # Excluye escapes, interpolación y expresiones; no es cobertura de UI.
    found = collections.defaultdict(list)
    for path in sorted(root.rglob(pattern)):
        source = path.read_text(encoding="utf-8-sig")
        for match in re.finditer(r'L\.t\(\s*"([^"\\\n]*)"\s*,', source):
            value = match.group(1)
            if "$" in value:
                continue
            found[value].append({
                "file": path.relative_to(root).as_posix(),
                "line": source.count("\n", 0, match.start()) + 1,
            })
    missing = [
        {"text": value, "locations": locations}
        for value, locations in sorted(found.items()) if value not in catalog
    ]
    return {"simple_literal_count": len(found),
            "missing_catalog_key_count": len(missing),
            "missing_catalog_keys": missing}


def collect(ios, android):
    assets = android / "app/src/main/assets"
    evidence = {
        "audit_date": "2026-11-10",
        "scope": "main de ambos repositorios; estado publicado, no archivos heredados sin seguimiento",
        "ios_commit": git(ios, "rev-parse", "HEAD"),
        "android_commit": git(android, "rev-parse", "HEAD"),
        "ios_branch": git(ios, "branch", "--show-current"),
        "android_branch": git(android, "branch", "--show-current"),
        "tracked_android_changes": git(android, "diff", "--name-only", "HEAD"),
    }
    evidence["gtfs"] = [
        {"name": path.name, "rows": len(table_rows(path)),
         "sha256": digest(path),
         "identical_to_ios": path.read_bytes() == (ios / "gtfs" / path.name).read_bytes()}
        for path in sorted((assets / "gtfs").glob("*.txt"))
    ]
    pairs = [
        ("negocios_promocionados.json", "RutaUTP/Services/Negocios/negocios_promocionados.json"),
        ("sedes-empresas-trujillo.json", "RutaUTP/Data/sedes-empresas-trujillo.json"),
        ("traducciones.json", "RutaUTP/Localization/traducciones.json"),
        ("senias/manifest.json", "senias/manifest.json"),
    ]
    evidence["catalogs"] = [
        {"android": name, "ios": relative, "sha256": digest(assets / name),
         "identical": (assets / name).read_bytes() == (ios / relative).read_bytes()}
        for name, relative in pairs
    ]
    clips = list((assets / "senias/clips").rglob("*.mp4"))
    manifest = json.loads((assets / "senias/manifest.json").read_text(encoding="utf-8-sig"))
    evidence["signs"] = {
        "clip_files": len(clips),
        "identical_files": sum(
            path.read_bytes() == (ios / "senias/clips" /
                                 path.relative_to(assets / "senias/clips")).read_bytes()
            for path in clips
        ),
        "manifest_entries": len(manifest["señas"]),
        "available_entries_per_language": {
            language: sum((assets / "senias/clips" / language / item["archivo"]).exists()
                          for item in manifest["señas"])
            for language in ["es", "en", "fr", "pt-BR", "de", "zh-Hans"]
        },
    }
    catalog = json.loads((assets / "traducciones.json").read_text(encoding="utf-8-sig"))
    evidence["translations"] = {
        "catalog_entries": len(catalog),
        "method": "L.t con primer argumento literal simple, sin escapes ni interpolación; barrido estático, no cobertura de pantallas",
        "android": literals(android / "app/src/main/java", "*.kt", catalog),
        "ios": literals(ios / "RutaUTP", "*.swift", catalog),
    }
    stops = table_rows(assets / "gtfs/stops.txt")
    evidence["places_search_area"] = {
        "android_rectangle": {"lat_min": -8.145, "lat_max": -8.050,
                              "lon_min": -79.075, "lon_max": -78.990},
        "stop_count": len(stops),
        "stops_outside_rectangle": sum(
            not (-8.145 <= float(stop["stop_lat"]) <= -8.050
                 and -79.075 <= float(stop["stop_lon"]) <= -78.990)
            for stop in stops
        ),
    }
    tests = []
    for path in sorted((android / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        tests.append({"suite": suite.get("name"),
                      **{key: int(suite.get(key, "0"))
                         for key in ["tests", "failures", "errors", "skipped"]}})
    evidence["android_unit_tests"] = {
        "suites": tests,
        "totals": {key: sum(suite[key] for suite in tests)
                   for key in ["tests", "failures", "errors", "skipped"]},
    }
    lint = android / "app/build/reports/lint-results-debug.xml"
    if lint.exists():
        issues = ET.parse(lint).getroot().findall("issue")
        evidence["android_lint"] = {
            "severity": dict(collections.Counter(item.get("severity") for item in issues)),
            "types": dict(collections.Counter(item.get("id") for item in issues)),
        }
    evidence["ci_files"] = {
        label: [path for path in git(root, "ls-files").splitlines()
                if path.startswith(".github/workflows/")]
        for label, root in [("ios", ios), ("android", android)]
    }
    apk = android / "app/build/outputs/apk/debug/app-debug.apk"
    if apk.exists():
        evidence["android_debug_apk_sha256"] = digest(apk)
    return evidence


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ios", required=True, type=Path)
    parser.add_argument("--android", required=True, type=Path)
    arguments = parser.parse_args()
    data = collect(arguments.ios.resolve(), arguments.android.resolve())
    output = Path(__file__).with_name("evidencia.json")
    output.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Evidencia guardada: {output}")
    print("GTFS iguales:", sum(item["identical_to_ios"] for item in data["gtfs"]))
    print("Pruebas Android:", data["android_unit_tests"]["totals"])
    print("Textos sin clave:", {name: data["translations"][name]["missing_catalog_key_count"]
                               for name in ["android", "ios"]})
