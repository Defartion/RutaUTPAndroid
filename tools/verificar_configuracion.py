"""Verifica la configuración efectiva del APK sin imprimir claves ni credenciales."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile


def clave_manifest(dump):
    lines = dump.splitlines()
    for index, line in enumerate(lines):
        if "E: meta-data" not in line:
            continue
        indent = len(line) - len(line.lstrip())
        attrs = {}
        for child in lines[index + 1:]:
            if child.strip().startswith("E:") and len(child) - len(child.lstrip()) <= indent:
                break
            match = re.search(r'A: (?:android:|http://schemas.android.com/apk/res/android:)(name|value)[^=]*="([^"\n]*)"', child)
            if match:
                attrs[match[1]] = match[2]
        if attrs.get("name") == "com.google.android.geo.API_KEY":
            if "value" not in attrs:
                raise ValueError("El metadato de Google no contiene un valor de texto")
            return attrs["value"]
    raise ValueError("Falta el metadato del SDK de mapas")


def verificar_dex(dex, prohibidos=()):
    for name in (b"MQTT_USERNAME", b"MQTT_PASSWORD", b"MQTT_READ_ONLY"):
        if name in dex:
            raise ValueError("El APK conserva un campo de credenciales MQTT de compilación")
    for marker in prohibidos:
        if marker.encode() in dex:
            raise ValueError("El APK contiene una credencial sintética que debía excluirse")
    for name in (b"ConexionInstalacionStore", b"ArchivoConexionCifrado", b"ConexionTransporteScreenKt", b"AndroidKeyStore", b"getNoBackupFilesDir"):
        if name not in dex:
            raise ValueError("Falta un componente de conexión protegida")


def aapt_disponible(explicito=None):
    if explicito:
        return explicito
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    if os.environ.get("LOCALAPPDATA"):
        roots.append(str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    roots.append(str(Path.home() / "AppData/Local/Android/Sdk"))
    local = Path(__file__).resolve().parents[1] / "local.properties"
    if local.is_file():
        # Solo se utiliza sdk.dir; no se muestran ni trasladan otras propiedades.
        match = re.search(r"(?m)^sdk\.dir\s*[=:]\s*(.+)$", local.read_text(encoding="utf-8-sig"))
        if match:
            roots.append(re.sub(r"\\(.)", r"\1", match[1].strip()))
    name = "aapt2.exe" if os.name == "nt" else "aapt2"
    for sdk in roots:
        if not sdk:
            continue
        files = list((Path(sdk) / "build-tools").glob("*/" + name))
        if files:
            return sorted(files, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))[-1]
    raise ValueError("No se encontró aapt2; indicar --aapt2 o configurar ANDROID_HOME")


def verificar(apk, aapt, expected=None, prohibidos=()):
    # Se captura la salida: el valor real del manifiesto nunca llega al registro.
    result = subprocess.run([str(aapt), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise ValueError("aapt2 no pudo inspeccionar el manifiesto")
    key = clave_manifest(result.stdout)
    if "${" in key:
        raise ValueError("No se sustituyó el placeholder de mapas")
    if expected is not None and key != expected:
        raise ValueError("La clave efectiva no coincide con la propiedad de prueba")
    with ZipFile(apk) as z:
        dex = b"".join(z.read(n) for n in z.namelist() if n.endswith(".dex"))
    verificar_dex(dex, prohibidos)
    return {"mapSdkConfigured": bool(key.strip()), "mapPropertyVerified": expected is not None,
            "mqttCredentialsBundled": False, "protectedConnectionComponents": True,
            "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest()}


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=root / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--aapt2", type=Path)
    parser.add_argument("--expected-map-key")
    parser.add_argument("--forbid", action="append", default=[])
    args = parser.parse_args()
    try:
        report = verificar(args.apk, aapt_disponible(args.aapt2), args.expected_map_key, args.forbid)
        output = root / "app/build/reports/parity/configuration-check.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("Configuración efectiva verificada; credenciales MQTT fuera del APK; almacenamiento protegido incluido")
        print("Clave SDK presente: " + str(report["mapSdkConfigured"]) + "; propiedad de prueba cotejada: " + str(report["mapPropertyVerified"]))
        return 0
    except (ValueError, OSError) as error:
        print("Error: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
