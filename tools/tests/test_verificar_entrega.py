import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("entrega", Path(__file__).resolve().parents[1] / "verificar_entrega.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "source.kt").write_bytes(b"source\n")
        output = self.root / "app/build/reports/parity"
        output.mkdir(parents=True)
        self.path = output / "performance-check.json"
        self.contract = {"baselineCommit": "baseline", "minimumVehicles": 1, "minimumSamples": 1,
                         "sourcePaths": ["source.kt"], "oracles": [], "timings": ["snapshot"]}
        self.report = {"environment": "host-jvm", "androidDeviceMeasured": False, "networkUsed": False,
                       "equivalentResults": True, "baselineCommit": "baseline", "vehicles": 1, "samples": 1,
                       "sourceHashes": {"source.kt": hashlib.sha256(b"source\n").hexdigest()},
                       "snapshot": {"medianMs": 1, "p95Ms": 2}, "routes": 1, "geometryPreparationsWarm": 1,
                       "targetProjectionsWarm": 1, "nearbyCandidates": 0, "nearbyFullCandidates": 1}
        self.save()
    def save(self):
        self.path.write_text(json.dumps(self.report), encoding="utf-8")
    def test_measurement_never_becomes_device_or_upgrade_proof(self):
        self.assertFalse(check.performance(self.root, self.contract)["androidDeviceMeasured"])
        self.assertFalse(check.storage(self.root, {"storageFiles": []})["installationUpgradeVerified"])
        self.report["androidDeviceMeasured"] = True; self.save()
        with self.assertRaisesRegex(ValueError, "host y dispositivo"):
            check.performance(self.root, self.contract)
    def test_changed_source_invalidates_previous_measurement(self):
        (self.root / "source.kt").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "código actual"):
            check.performance(self.root, self.contract)
    def test_invalid_timings_and_storage_changes_are_rejected(self):
        valid = {"storageFiles": [{"path": "source.kt", "sha256": hashlib.sha256(b"source").hexdigest()}]}
        self.assertEqual(1, check.storage(self.root, valid)["sourceContractsVerified"])
        self.report["snapshot"]["medianMs"] = float("nan"); self.save()
        with self.assertRaisesRegex(ValueError, "Tiempo inválido"):
            check.performance(self.root, self.contract)
        with self.assertRaisesRegex(ValueError, "persistente"):
            check.storage(self.root, {"storageFiles": [{"path": "source.kt", "sha256": "changed"}]})
