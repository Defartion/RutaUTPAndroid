import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("config", Path(__file__).resolve().parents[1] / "verificar_configuracion.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class ConfigurationApkTest(unittest.TestCase):
    def manifest(self, value):
        return 'E: manifest\n  E: application\n    E: meta-data\n      A: android:name(0x01010003)="com.google.android.geo.API_KEY"\n      A: android:value(0x01010024)="' + value + '"\n    E: activity\n      A: android:name(0x01010003)="MainActivity"'

    def test_extrae_la_clave_del_metadato_correcto(self):
        self.assertEqual("SENTINELA", check.clave_manifest(self.manifest("SENTINELA")))

    def test_acepta_el_namespace_completo_emitido_por_aapt2(self):
        dump = self.manifest("SENTINELA").replace("A: android:", "A: http://schemas.android.com/apk/res/android:")
        self.assertEqual("SENTINELA", check.clave_manifest(dump))

    def test_configuracion_ausente_es_un_valor_distinto_a_placeholder(self):
        self.assertEqual("", check.clave_manifest(self.manifest("")))

    def test_rechaza_manifiesto_sin_metadato(self):
        with self.assertRaises(ValueError):
            check.clave_manifest("E: manifest\n  E: application")

    def test_detecta_campos_de_credenciales_aunque_las_pantallas_existan(self):
        with self.assertRaisesRegex(ValueError, "credenciales MQTT"):
            check.verificar_dex(b"MQTT_PASSWORD ConexionInstalacionStore")

    def test_detecta_credencial_sintetica_incrustada(self):
        with self.assertRaisesRegex(ValueError, "sintética"):
            check.verificar_dex(b"SECRETO_DE_ENSAYO", ["SECRETO_DE_ENSAYO"])

    def test_exige_el_almacen_protegido_y_la_pantalla_de_provision(self):
        with self.assertRaisesRegex(ValueError, "componente"):
            check.verificar_dex(b"SinComponentes")


if __name__ == "__main__":
    unittest.main()
