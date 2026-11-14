import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("backendcheck", Path(__file__).resolve().parents[1] / "verificar_backend_push.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class BackendPackageGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "integrations/backend-push").mkdir(parents=True)
        (self.root / "parity").mkdir()
        (self.root / "source.py").write_bytes(b"verified")
        manifest = {"referenceIosCommit": "abc", "minimumBackendTests": 1, "requiredTestClasses": ["FCM"],
                    "packageFiles": [{"path": "source.py", "sha256": hashlib.sha256(b"verified").hexdigest()}]}
        (self.root / "integrations/backend-push/manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        (self.root / "parity/reference.json").write_text(json.dumps({"repositories": {"ios": {"commit": "abc"}}}), encoding="utf-8")
    def test_package_and_tests_never_claim_real_delivery(self):
        result = check.verify(self.root)
        self.assertFalse(result["realDeliveryVerified"])
        self.assertFalse(result["deployed"])
        report = self.root / "app/build/reports/parity/backend-push-tests.xml"
        report.parent.mkdir(parents=True)
        report.write_text('<testsuites><testsuite tests="1"><testcase classname="FCM"/></testsuite></testsuites>', encoding="utf-8")
        self.assertTrue(check.verify(self.root, True)["testsVerified"])
    def test_changed_source_does_not_pass_package_check(self):
        (self.root / "source.py").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Cambió"):
            check.verify(self.root)
    def test_missing_or_failed_results_never_pass(self):
        with self.assertRaises(OSError):
            check.verify(self.root, True)
        report = self.root / "app/build/reports/parity/backend-push-tests.xml"
        report.parent.mkdir(parents=True)
        report.write_text('<testsuite tests="1"><testcase classname="FCM"><skipped/></testcase></testsuite>', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "omitidas"):
            check.verify(self.root, True)
