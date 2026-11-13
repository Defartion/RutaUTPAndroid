import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("idiomas", Path(__file__).resolve().parents[1] / "verificar_idiomas.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class LanguageGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "app/src/main/java/Test.kt"
        self.source.parent.mkdir(parents=True)
        self.assets = self.root / "app/src/main/assets"
        self.assets.mkdir(parents=True)
        self.entry = {c: "Value {0}" for c in check.LANGUAGES}
        self.save("traducciones.json", {"Valor {0}": self.entry})
        self.save("traducciones-android.json", {"aliases": {}, "entries": {}})
        for name in ("negocios_promocionados.json", "referencias_transporte.json"):
            self.save(name, [])
        self.source.write_text('L.f("Valor {0}", "Value {0}", "A")', encoding="utf-8")

    def save(self, name, value):
        (self.assets / name).write_text(json.dumps(value), encoding="utf-8")

    def test_catalogo_y_plantilla_validos(self):
        self.assertEqual([], check.verify(self.root)["errors"])

    def test_comentarios_y_literales_no_son_llamadas(self):
        source = '// L.t("Falso", "False")\n/* /* L.t("Otro", "Other") */ */\nval t="L.t(\\"X\\", \\"Y\\")"\nL.t("Real", "Real")'
        self.assertEqual(1, len(list(check.calls(source))))

    def test_escapes_y_dolar_literal_se_decodifican_sin_interpolar(self):
        _, parts = check.literal(r'"A\n\"B\"\$5"', 0)
        self.assertEqual('A\n"B"$5', check.plain(parts))
        self.assertFalse(any(expr for expr, _ in parts))

    def test_interpolacion_con_expresion_anidada_no_se_da_por_traducida(self):
        source = 'L.t("Hola $' + '{nombre ?: "otro"}", "Hi $nombre")'
        self.source.write_text(source, encoding="utf-8")
        self.assertTrue(any("interpolación" in error for error in check.verify(self.root)["errors"]))

    def test_rechaza_clave_ausente_y_llamada_dinamica_nueva(self):
        self.source.write_text('L.t("Ausente", "Missing"); L.t(variable, english)', encoding="utf-8")
        errors = check.verify(self.root)["errors"]
        self.assertTrue(any("falta clave" in e for e in errors))
        self.assertTrue(any("dinámica" in e for e in errors))

    def test_rechaza_idioma_o_parametro_que_falta(self):
        self.entry["de"] = "Wert"
        self.save("traducciones.json", {"Valor {0}": self.entry})
        self.assertTrue(any("en de" in e for e in check.verify(self.root)["errors"]))

    def test_rechaza_alias_que_no_resuelve_y_claves_duplicadas(self):
        self.save("traducciones-android.json", {"aliases": {"A": "Ausente"}, "entries": {}})
        self.assertTrue(any("Alias inválido" in e for e in check.verify(self.root)["errors"]))
        path = self.assets / "duplicado.json"
        path.write_text('{"a":1,"a":2}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicada"):
            check.load(path)

    def test_comprueba_el_texto_bilingue_de_los_recursos(self):
        self.save("negocios_promocionados.json", [{"promo": {"es": "Ausente", "en": "Missing"}}])
        self.assertTrue(any("negocios_promocionados.json" in e for e in check.verify(self.root)["errors"]))

    def test_rechaza_plantilla_sin_argumento_aunque_tenga_traduccion(self):
        self.source.write_text('L.f("Valor {0}", "Value {0}")', encoding="utf-8")
        self.assertTrue(any("faltan argumentos" in e for e in check.verify(self.root)["errors"]))

    def test_comas_anidadas_escapes_y_espacios_no_rompen_el_conteo(self):
        self.source.write_text('L.f ( "Valor {0}", "Value {0}", llamada("a,b", lista[0]) )', encoding="utf-8")
        self.assertEqual([], check.verify(self.root)["errors"])


if __name__ == "__main__":
    unittest.main()
