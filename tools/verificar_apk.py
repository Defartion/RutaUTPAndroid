"""Comprueba los recursos realmente instalables, después de assembleDebug.

Uso: python tools/verificar_apk.py [--apk ruta/app-debug.apk]
No necesita dependencias, dispositivo ni conexión de red.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct
from zipfile import ZipFile


def verificar(apk: Path, assets: Path):
    with ZipFile(apk) as archive:
        models = sorted((assets / "transportes3d").glob("*.mesh.gz"))
        if len(models) != 6:
            raise ValueError("Se esperaban los seis modelos originales")
        for source in models:
            # AAPT convierte .mesh.gz en .mesh al empaquetar los assets.
            name = f"assets/transportes3d/{source.name.removesuffix('.gz')}"
            data = archive.read(name)
            if data != gzip.decompress(source.read_bytes()):
                raise ValueError(f"La malla empaquetada no coincide: {name}")
            magic, count = struct.unpack_from(">II", data)
            if magic != 0x52555433 or not 3 <= count <= 300_000 or count % 3 or len(data) != 8 + count * 40:
                raise ValueError(f"Malla empaquetada inválida: {name}")
            parts_source = source.with_name(source.name.replace(".mesh.gz", ".partes.json"))
            parts_data = archive.read(f"assets/transportes3d/{parts_source.name}")
            if parts_data != parts_source.read_bytes():
                raise ValueError(f"Partes de identificación distintas: {parts_source.name}")
            parts = json.loads(parts_data)
            if not parts["carrocerias"] or any(start < 0 or size <= 0 or start + size > count for start, size in parts["carrocerias"] + parts["contornos"]):
                raise ValueError(f"Partes de identificación inválidas: {parts_source.name}")
        for source in [assets / "referencias_transporte.json", assets / "traducciones.json", assets / "traducciones-android.json"]:
            if archive.read(f"assets/{source.name}") != source.read_bytes():
                raise ValueError(f"Catálogo empaquetado distinto: {source.name}")
        dex = b"".join(archive.read(name) for name in archive.namelist() if name.endswith(".dex"))
        if b".mesh.gz" in dex:
            raise ValueError("El visor aún busca .mesh.gz, pero AAPT instala las mallas como .mesh")
        for screen in [b"Transportes3DScreenKt", b"NotificacionesScreenKt", b"AlternativasRutaSheetKt", b"RespaldosRecuperacionSheetKt", b"GuardadoPantallaKt", b"SeguridadAyudaKt", b"RutaOpcionCardKt", b"VisorFotoCarnetKt", b"PinturaTransporte3DKt"]:
            if screen not in dex:
                raise ValueError(f"Pantalla ausente del APK: {screen.decode()}")
        for component in [b"CatalogoIdiomas", b"CargaCatalogo", b"PresentadorSenias",
                          b"CamaraTransporte3D", b"PanelModeloTransporteKt", b"PresentacionSeccionesKt",
                          b"RegistroPush"]:
            if component not in dex:
                raise ValueError("Falta un componente del APK: " + component.decode())
    print("APK comprobado: seis mallas accesibles, catálogos exactos y pantallas nuevas incluidas")
    print(f"SHA-256: {hashlib.sha256(apk.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=root / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    verificar(args.apk, root / "app/src/main/assets")
