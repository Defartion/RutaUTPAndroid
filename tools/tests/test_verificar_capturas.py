import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("capturas", Path(__file__).resolve().parents[1] / "verificar_capturas.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class PreviewGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "parity").mkdir()
        self.images = self.root / "references"
        self.images.mkdir()
        self.png = b"\x89PNG\r\n\x1a\n" + b"\0\0\0\rIHDR" + struct.pack(">II", 100, 200)
        (self.images / "preview_demo.png").write_bytes(self.png)
        self.manifest = {"referenceIosCommit": "abc", "referenceRoot": "references", "minimumImages": 1,
                         "screens": [{"id": s, "previewMethods": ["preview"]} for s in ("map", "routes", "saved", "safety", "profile")],
                         "images": [{"path": "preview_demo.png", "width": 100, "height": 200, "sha256": hashlib.sha256(self.png).hexdigest()}]}
        self.save()
        (self.root / "parity/reference.json").write_text(json.dumps({"repositories": {"ios": {"commit": "abc"}}}), encoding="utf-8")

    def save(self):
        (self.root / "parity/android-previews.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def test_referencias_no_acreditan_comparacion_de_dispositivos(self):
        report = check.verify(self.root)
        self.assertFalse(report["resultsVerified"])
        self.assertEqual("pending", report["iosDeviceComparison"])

    def test_rechaza_cambio_de_imagen_sin_registrarlo(self):
        (self.images / "preview_demo.png").write_bytes(self.png + b"change")
        with self.assertRaisesRegex(ValueError, "Cambió"):
            check.verify(self.root)

    def test_rechaza_ruta_que_sale_de_las_referencias(self):
        with self.assertRaisesRegex(ValueError, "fuera"):
            check.contained(self.images, "../otro.png")

    def test_resultados_ausentes_o_fallidos_no_se_aceptan(self):
        with self.assertRaisesRegex(ValueError, "Faltan resultados"):
            check.verify(self.root, True)
        target = self.root / "app/build/test-results/validateDebugScreenshotTest"
        target.mkdir(parents=True)
        (target / "TEST-preview.xml").write_text('<testsuite tests="1" failures="1"><testcase><failure/></testcase></testsuite>', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "fallidas"):
            check.verify(self.root, True)
        (target / "TEST-preview.xml").write_text('<testsuite tests="1" failures="0"><testcase/></testsuite>', encoding="utf-8")
        rendered = self.root / "app/build/outputs/screenshotTest-results/preview/debug/rendered"
        rendered.mkdir(parents=True)
        (rendered / "preview_demo.png").write_bytes(self.png)
        self.assertTrue(check.verify(self.root, True)["resultsVerified"])
        (rendered / "preview_demo.png").write_bytes(self.png + b"stale")
        with self.assertRaisesRegex(ValueError, "no coincide"):
            check.verify(self.root, True)
