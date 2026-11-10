"""Regresiones del control que evita dar por aprobada una referencia incompleta."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("verificar_paridad", REPO / "tools/verificar_paridad.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class ReferenceGuardTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "parity").mkdir()
        for name in ("reference", "findings", "visual-states"):
            shutil.copyfile(REPO / ("parity/" + name + ".json"), self.root / ("parity/" + name + ".json"))
        shutil.copytree(REPO / "app/src/test/resources/parity", self.root / "app/src/test/resources/parity")
        self.resource = self.root / "asset.txt"
        self.resource.write_bytes(b"shared baseline")
        ref = CHECK.load_json(self.root / "parity/reference.json")
        ref["sharedResources"] = [{
            "androidPath": "asset.txt", "iosPath": "asset.txt",
            "sha256": hashlib.sha256(self.resource.read_bytes()).hexdigest(),
        }]
        ref["sharedResourceCount"] = 1
        self.save("parity/reference.json", ref)

    def save(self, relative, value):
        (self.root / relative).write_text(json.dumps(value), encoding="utf-8")

    def reports(self):
        directory = self.root / "app/build/test-results/testDebugUnitTest"
        directory.mkdir(parents=True)
        for name, cases in [
            ("OriginalSuite", ["original-" + str(index) for index in range(119)]),
            ("com.example.rutautpnative.IosEtaReferenceTest",
             ["llegadaCoincideConReferenciaIos[ETA-0" + str(index) + "]" for index in range(1, 7)]),
        ]:
            suite = ET.Element("testsuite", name=name, tests=str(len(cases)), failures="0", errors="0", skipped="0")
            for case in cases:
                ET.SubElement(suite, "testcase", name=case, classname=name)
            ET.ElementTree(suite).write(directory / ("TEST-" + name + ".xml"))
        lint = self.root / "app/build/reports"
        lint.mkdir(parents=True)
        (lint / "lint-results-debug.xml").write_text("<issues/>", encoding="utf-8")

    def test_pendientes_no_se_presentan_como_pruebas_aprobadas(self):
        report = CHECK.verify(self.root)
        self.assertEqual(21, report["pendingBehaviorCases"])
        self.assertEqual(6, len(report["fixtureCases"]["eta"]["baseline"]))
        self.assertFalse(report["resultsVerified"])

    def test_rechaza_recurso_modificado(self):
        self.resource.write_bytes(b"different feed")
        with self.assertRaisesRegex(ValueError, "Cambió el recurso"):
            CHECK.verify(self.root)

    def test_texto_compartido_admite_checkout_lf_y_crlf(self):
        ref = CHECK.load_json(self.root / "parity/reference.json")
        ref["sharedResources"][0]["hashMode"] = "lf"
        ref["sharedResources"][0]["sha256"] = hashlib.sha256(b"first\nsecond\n").hexdigest()
        self.save("parity/reference.json", ref)
        self.resource.write_bytes(b"first\r\nsecond\r\n")
        self.assertEqual(1, CHECK.verify(self.root)["sharedResourcesVerified"])

    def test_binarios_no_normalizan_saltos_de_linea(self):
        self.resource.write_bytes(b"shared\r\nbaseline")
        with self.assertRaisesRegex(ValueError, "Cambió el recurso"):
            CHECK.verify(self.root)

    def test_rechaza_ruta_fuera_del_checkout(self):
        ref = CHECK.load_json(self.root / "parity/reference.json")
        ref["sharedResources"][0]["androidPath"] = "../asset.txt"
        self.save("parity/reference.json", ref)
        with self.assertRaisesRegex(ValueError, "fuera del repositorio"):
            CHECK.verify(self.root)

    def test_rechaza_seguimiento_que_omite_un_hallazgo(self):
        data = CHECK.load_json(self.root / "parity/findings.json")
        data["findings"] = data["findings"][1:]
        self.save("parity/findings.json", data)
        with self.assertRaisesRegex(ValueError, "F01–F15"):
            CHECK.verify(self.root)

    def test_rechaza_cambiar_la_referencia_de_un_fixture(self):
        path = "app/src/test/resources/parity/eta.json"
        data = CHECK.load_json(self.root / path)
        data["referenceIosCommit"] = "0" * 40
        self.save(path, data)
        with self.assertRaisesRegex(ValueError, "Referencia diferente"):
            CHECK.verify(self.root)

    def test_rechaza_resultados_ausentes(self):
        with self.assertRaisesRegex(ValueError, "Faltan los resultados"):
            CHECK.verify(self.root, require_results=True)

    def test_resultados_exigen_los_casos_ios_y_el_minimo_original(self):
        self.reports()
        report = CHECK.verify(self.root, require_results=True)
        self.assertEqual(125, report["checks"]["unitTests"]["tests"])
        self.assertEqual(6, report["checks"]["referenceEtaCasesExecuted"])

    def test_rechaza_conteo_inflado_sin_casos(self):
        self.reports()
        file = next((self.root / "app/build/test-results/testDebugUnitTest").glob("TEST-OriginalSuite.xml"))
        suite = ET.parse(file)
        suite.getroot().set("tests", "999")
        suite.write(file)
        with self.assertRaisesRegex(ValueError, "conteo JUnit"):
            CHECK.verify(self.root, require_results=True)

    def test_no_permite_sustituir_caso_ios_por_otro_test(self):
        self.reports()
        file = self.root / "app/build/test-results/testDebugUnitTest/TEST-com.example.rutautpnative.IosEtaReferenceTest.xml"
        suite = ET.parse(file)
        suite.getroot().find("testcase").set("name", "otraPrueba")
        suite.write(file)
        with self.assertRaisesRegex(ValueError, "No se ejecutó el caso"):
            CHECK.verify(self.root, require_results=True)

    def test_rechaza_ejecucion_con_menos_pruebas_que_la_base(self):
        self.reports()
        file = self.root / "app/build/test-results/testDebugUnitTest/TEST-OriginalSuite.xml"
        suite = ET.parse(file)
        suite.getroot().remove(suite.getroot().find("testcase"))
        suite.getroot().set("tests", "118")
        suite.write(file)
        with self.assertRaisesRegex(ValueError, "menos de 125"):
            CHECK.verify(self.root, require_results=True)

    def test_rechaza_prueba_omitida(self):
        self.reports()
        file = self.root / "app/build/test-results/testDebugUnitTest/TEST-OriginalSuite.xml"
        suite = ET.parse(file)
        suite.getroot().set("skipped", "1")
        ET.SubElement(suite.getroot().find("testcase"), "skipped")
        suite.write(file)
        with self.assertRaisesRegex(ValueError, "omitidas"):
            CHECK.verify(self.root, require_results=True)

    def test_rechaza_error_lint(self):
        self.reports()
        file = self.root / "app/build/reports/lint-results-debug.xml"
        file.write_text('<issues><issue severity="Error"/></issues>', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "análisis de Android contiene errores"):
            CHECK.verify(self.root, require_results=True)

    def test_rechaza_json_con_claves_duplicadas(self):
        file = self.root / "duplicate.json"
        file.write_text('{"expectedMinutes": null, "expectedMinutes": 4}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicada"):
            CHECK.load_json(file)


if __name__ == "__main__":
    unittest.main()
