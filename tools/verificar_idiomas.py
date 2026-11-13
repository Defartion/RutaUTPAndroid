"""Comprueba llamadas de traducción Kotlin, plantillas y los seis idiomas, sin red."""
import argparse
import json
from pathlib import Path
import re

LANGUAGES = ("en", "fr", "pt-BR", "de", "zh-Hans")
CONTENT_CALLS = {
    "app/src/main/java/com/example/rutautpnative/model/Models.kt": "L.t(cuerpo, cuerpoIngles ?: cuerpo)",
    "app/src/main/java/com/example/rutautpnative/model/Negocio.kt": "L.t(es, en.ifBlank { es })",
    "app/src/main/java/com/example/rutautpnative/ui/components/Signable.kt": "L.t(es, en)",
    "app/src/main/java/com/example/rutautpnative/ui/screens/ReferenciaTransporte.kt": "L.t(color.nombre, color.ingles)",
}


def comment_end(source, start):
    if source.startswith("//", start):
        end = source.find("\n", start)
        return len(source) if end < 0 else end
    depth, pos = 1, start + 2
    while depth and pos < len(source):
        if source.startswith("/*", pos):
            depth += 1
            pos += 2
        elif source.startswith("*/", pos):
            depth -= 1
            pos += 2
        else:
            pos += 1
    return pos


def expression_end(source, start):
    depth, pos = 1, start
    while pos < len(source):
        if source.startswith(("//", "/*"), pos):
            pos = comment_end(source, pos)
        elif source[pos] == '"':
            pos = literal(source, pos)[0]
        elif source[pos] == "'":
            pos += 2 if source[pos + 1:pos + 2] != "\\" else 3
            pos += 1
        else:
            if source[pos] == "{":
                depth += 1
            elif source[pos] == "}":
                depth -= 1
                if not depth:
                    return pos
            pos += 1
    raise ValueError("Interpolación sin cerrar")


def literal(source, start):
    raw = source.startswith('"""', start)
    quote = '"""' if raw else '"'
    pos = start + len(quote)
    parts, text = [], ""
    while pos < len(source):
        if source.startswith(quote, pos):
            parts.append((False, text))
            return pos + len(quote), parts
        if not raw and source[pos] == "\\":
            escapes = {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "'": "'", '"': '"', "\\": "\\", "$": "$"}
            key = source[pos + 1]
            if key == "u":
                text += chr(int(source[pos + 2:pos + 6], 16))
                pos += 6
            else:
                text += escapes[key]
                pos += 2
        elif source.startswith("${", pos):
            end = expression_end(source, pos + 2)
            expression = source[pos + 2:end]
            # ${'$'} is a literal dollar, not a runtime parameter.
            if expression.strip() == "'$'":
                text += "$"
            else:
                parts.extend(((False, text), (True, expression)))
                text = ""
            pos = end + 1
        elif source[pos] == "$" and re.match(r"[A-Za-z_]", source[pos + 1:pos + 2]):
            match = re.match(r"\$([A-Za-z_][A-Za-z_0-9]*)", source[pos:])
            parts.extend(((False, text), (True, match[1])))
            text = ""
            pos += len(match[0])
        else:
            text += source[pos]
            pos += 1
    raise ValueError("Texto Kotlin sin cerrar")


def calls(source):
    """Recorre código real, saltando comentarios/textos y entendiendo ${expresiones}."""
    pos = 0
    while pos < len(source):
        if source.startswith(("//", "/*"), pos):
            pos = comment_end(source, pos)
        elif source[pos] == '"':
            pos, _ = literal(source, pos)
        elif source[pos] == "'":
            pos += 4 if source[pos + 1:pos + 2] == "\\" else 3
        else:
            match = re.match(r"\bL\.(t|f)\s*\(", source[pos:])
            if match:
                cursor = pos + len(match[0])
                strings = []
                for _ in range(2):
                    while cursor < len(source) and source[cursor].isspace():
                        cursor += 1
                    if source[cursor:cursor + 1] != '"':
                        break
                    start = cursor
                    cursor, parts = literal(source, cursor)
                    strings.append((start, cursor, parts))
                    while cursor < len(source) and source[cursor].isspace():
                        cursor += 1
                    if source[cursor:cursor + 1] != ",":
                        break
                    cursor += 1
                yield {"start": pos, "end": cursor, "argStart": pos + len(match[0]), "method": match[1], "strings": strings,
                       "expression": source[pos:pos + 100],
                       "line": source.count("\n", 0, pos) + 1}
                pos += len(match[0])
            else:
                pos += 1


def inventory(root):
    result = []
    for path in sorted((root / "app/src/main/java").rglob("*.kt")):
        if path.name == "L.kt":
            continue
        source = path.read_text(encoding="utf-8")
        for call in calls(source):
            call["file"] = str(path.relative_to(root)).replace("\\", "/")
            result.append(call)
    return result


def plain(parts):
    return "".join(value for _, value in parts)


def arguments(source, start):
    """Argumentos Kotlin completos; las comas dentro de llamadas o textos no separan."""
    parts, stack, pos, begin = [], [], start, start
    while pos < len(source):
        if source.startswith(("//", "/*"), pos):
            pos = comment_end(source, pos)
        elif source[pos] == '"':
            pos = literal(source, pos)[0]
        elif source[pos] == "'":
            pos += 4 if source[pos + 1:pos + 2] == "\\" else 3
        elif source[pos] in "([{":
            stack.append(source[pos])
            pos += 1
        elif source[pos] in ")]}":
            if not stack and source[pos] == ")":
                return parts + [source[begin:pos].strip()]
            stack.pop()
            pos += 1
        elif source[pos] == "," and not stack:
            parts.append(source[begin:pos].strip())
            pos += 1
            begin = pos
        else:
            pos += 1
    raise ValueError("Llamada sin cerrar")


def comunidad_pairs(source):
    def text(value):
        end, parts = literal(value, 0)
        if end != len(value) or any(dynamic for dynamic, _ in parts):
            raise ValueError("Contenido demo no literal")
        return plain(parts)
    bodies = []
    for name in ("cuerpos", "cuerposIngles"):
        match = re.search(r"private val " + name + r"\s*=\s*listOf\(", source)
        if not match:
            return
        values = arguments(source, match.end())
        # El listado español añade el tipo de reporte después de cada texto.
        bodies.append([plain(literal(v, 0)[1]) for v in values if v])
    if len(bodies[0]) != len(bodies[1]):
        raise ValueError("Contenido demo bilingüe desparejado")
    yield from zip(*bodies)
    for match in re.finditer(r"\b(?:Ilustrado|Vial)\(", source):
        args = arguments(source, match.end())
        if args[-1].startswith('"') and args[-2].startswith('"'):
            yield text(args[-2]), text(args[-1])


def load(path):
    def unique(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise ValueError("Clave duplicada: " + key)
            obj[key] = value
        return obj
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def resource_pairs(value):
    if isinstance(value, dict):
        if {"es", "en"} <= value.keys():
            yield value["es"], value["en"]
        if {"nombre", "ingles"} <= value.keys():
            yield value["nombre"], value["ingles"]
        for item in value.values():
            yield from resource_pairs(item)
    elif isinstance(value, list):
        for item in value:
            yield from resource_pairs(item)


def verify(root):
    shared = load(root / "app/src/main/assets/traducciones.json")
    android = load(root / "app/src/main/assets/traducciones-android.json")
    aliases = android["aliases"]
    entries = shared | android["entries"]
    errors, dynamic = [], []
    keys = set()
    for alias, target in aliases.items():
        if alias in entries or target not in shared:
            errors.append("Alias inválido: " + alias)
    for key, entry in android["entries"].items():
        parameters = set(re.findall(r"\{\d+\}", key))
        for language in LANGUAGES:
            value = entry.get(language, "")
            if not value.strip() or set(re.findall(r"\{\d+\}", value)) != parameters:
                errors.append("Complemento inválido en " + language + ": " + key)

    def check_pair(es, en, location):
        keys.add(es)
        entry = entries.get(aliases.get(es, es))
        if entry is None:
            errors.append(location + ": falta clave " + es)
            return
        parameters = set(re.findall(r"\{\d+\}", es))
        for language, value in {"en": en, **{c: entry.get(c, "") for c in LANGUAGES if c != "en"}}.items():
            if not value.strip() or set(re.findall(r"\{\d+\}", value)) != parameters:
                errors.append(location + ": texto o parámetros inválidos en " + language + ": " + es)

    for call in inventory(root):
        location = f'{call["file"]}:{call["line"]}'
        if len(call["strings"]) != 2:
            dynamic.append(location)
            permitted = CONTENT_CALLS.get(call["file"])
            if not permitted or not call["expression"].startswith(permitted):
                errors.append(location + ": llamada dinámica sin cobertura explícita")
            continue
        if any(is_expression for item in call["strings"] for is_expression, _ in item[2]):
            errors.append(location + ": interpolación directa; usar L.f y parámetros")
            continue
        es, en = (plain(item[2]) for item in call["strings"])
        args = arguments((root / call["file"]).read_text(encoding="utf-8"), call["argStart"])
        parameters = [int(n) for n in re.findall(r"\{(\d+)\}", es)]
        if parameters and (call["method"] != "f" or max(parameters) >= len(args) - 2):
            errors.append(location + ": faltan argumentos de plantilla")
        check_pair(es, en, location)
    literal_keys = len(keys)
    pairs = 0
    for name in ("negocios_promocionados.json", "referencias_transporte.json"):
        for es, en in resource_pairs(load(root / "app/src/main/assets" / name)):
            check_pair(es, en, name)
            pairs += 1
    community = root / "app/src/main/java/com/example/rutautpnative/data/ComunidadDatos.kt"
    community_pairs = list(comunidad_pairs(community.read_text(encoding="utf-8"))) if community.exists() else []
    for es, en in community_pairs:
        check_pair(es, en, "ComunidadDatos.kt")
    # El wrapper señable usa argumentos con nombre, no llamadas L.t literales.
    for path in (root / "app/src/main/java").rglob("*.kt"):
        for match in re.finditer(r'es\s*=\s*"([^"\\]*)",\s*en\s*=\s*"([^"\\]*)"', path.read_text(encoding="utf-8")):
            check_pair(match[1], match[2], str(path.relative_to(root)))
    return {"keysVerified": len(keys), "literalKeys": literal_keys, "resourcePairs": pairs, "communityPairs": len(community_pairs),
            "dynamicCalls": dynamic, "errors": sorted(set(errors))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        report = verify(args.root)
        target = args.root / "app/build/reports/parity/language-check.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f'Claves verificadas: {report["keysVerified"]}; llamadas dinámicas: {len(report["dynamicCalls"])}; errores: {len(report["errors"])}')
        for error in report["errors"]:
            print(error)
        return bool(report["errors"])
    except (ValueError, OSError) as error:
        print(str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
