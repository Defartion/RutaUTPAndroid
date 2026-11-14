"""Prepara una copia de pruebas del backend fijado; no modifica ni despliega iOS."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile


def prepare(root, ios):
    root, ios = Path(root).resolve(), Path(ios).resolve()
    package = root / "integrations/backend-push"
    manifest = json.loads((package / "manifest.json").read_text(encoding="utf-8"))
    commit = manifest["referenceIosCommit"]
    reference = json.loads((root / "parity/reference.json").read_text(encoding="utf-8"))
    if commit != reference["repositories"]["ios"]["commit"]:
        raise ValueError("La integración no corresponde a la referencia iOS")
    actual = subprocess.check_output(["git", "-C", str(ios), "rev-parse", "HEAD"], text=True).strip()
    if actual != commit:
        raise ValueError("El checkout iOS debe estar en el commit fijado de main")
    for item in manifest["upstreamFiles"]:
        original = subprocess.check_output(["git", "-C", str(ios), "show", commit + ":" + item["path"]])
        if hashlib.sha256(original).hexdigest() != item["sha256"]:
            raise ValueError("Cambió el archivo iOS de origen: " + item["path"])
    # Solo datos versionados del commit; nunca copiar .env, cuentas o archivos locales.
    archive = subprocess.check_output(["git", "-C", str(ios), "archive", commit, "backend", "gtfs"])
    output = (root / "app/build/backend-phase7").resolve()
    allowed = (root / "app/build").resolve()
    if not output.is_relative_to(allowed) or output == allowed:
        raise ValueError("El destino debe quedar en app/build")
    output.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as tar:
        for member in tar:
            destination = (output / member.name).resolve()
            if not destination.is_relative_to(output) or not (member.isdir() or member.isfile()):
                raise ValueError("Entrada no permitida en el archivo del backend")
            if member.isdir():
                destination.mkdir(parents=True, exist_ok=True)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(tar.extractfile(member).read())
    for path in package.rglob("*"):
        if path.is_file() and path.suffix in (".py", ".txt"):
            destination = output / "backend" / path.relative_to(package)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(path.read_bytes())
    (output / "backend/rutautp_backend/fcm_provider.py").write_bytes((root / "integrations/fcm_provider.py").read_bytes())
    contract = output / "backend/tests/push-contract.json"
    contract.write_bytes((root / "parity/push-contract.json").read_bytes())
    (output / "phase7-source.json").write_text(json.dumps({"iosCommit": commit, "deployed": False}) + "\n", encoding="utf-8")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ios", type=Path, required=True)
    args = parser.parse_args()
    try:
        output = prepare(Path(__file__).resolve().parents[1], args.ios)
        print("Backend preparado para pruebas locales: " + str(output))
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print("No se pudo preparar el backend: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
