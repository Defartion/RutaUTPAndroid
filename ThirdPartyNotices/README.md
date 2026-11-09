# ThirdPartyNotices — Procedencia y licencias

Este directorio documenta la procedencia de todos los datos y recursos de
terceros empaquetados con la aplicación. Ningún recurso se incorpora sin
documentar su origen aquí.

## GTFS (feed de transporte)

- **Fuente**: feed GTFS estático del área de Trujillo, empaquetado en
  `app/src/main/assets/gtfs/` (102 rutas, 4067 paraderos, 53 616 puntos de
  recorrido).
- **Estado**: los metadatos de `feed_info.txt` aún identifican al publicador
  como «Arequipa Bus» y requieren revisión de procedencia.
- El feed describe la **estructura** del transporte (rutas, recorridos,
  paraderos, frecuencias y tarifas). **No contiene** posiciones GPS en vivo:
  las posiciones de los buses son simuladas o llegan por MQTT del backend.
- La carpeta `assets/gtfs-utp/` está reservada para el futuro feed
  institucional de buses UTP (hoy vacía, a propósito).

## Comunidad — fotos de archivo

Las publicaciones de la Comunidad son **ficticias**; las fotos son de
archivo con procedencia verificable. Los créditos, fechas y enlaces se
muestran en el detalle de cada publicación dentro de la app.

### Wikimedia Commons (con licencia declarada)
| Foto | Autor | Año | Licencia |
|---|---|---|---|
| `comunidad-pizarro` (Jirón Pizarro) | EACC | 2012 | CC BY-SA 3.0 |
| `comunidad-centro` (Centro de Trujillo) | Pitxiquin | 2017 | CC BY-SA 4.0 |
| `comunidad-papal` (Óvalo Papal) | Latimax | 2011 | CC BY-SA 3.0 |

### Municipalidad Provincial de Trujillo (archivo de noticias, gob.pe)
Las 20 fotos restantes (`comunidad-trujillo-*`) provienen de publicaciones
oficiales de la MPT en gob.pe. Cada una acredita «Municipalidad Provincial
de Trujillo» con la fecha de la publicación fuente. No se declara una
licencia expresa; se usan como referencia de noticias públicas para una
aplicación prototipo educativa. Los enlaces a cada publicación fuente están
en el modelo `FotoComunidad.kt` y se muestran en el detalle de cada post.

## Iconos

- **Categorías de negocios y cupones**: Uicons Regular Rounded de Flaticon
  (https://www.flaticon.com/uicons), usados como SVG locales convertidos a
  ImageVector. La atribución aparece en «Sobre nosotros».

## Marcas

- El selector de temáticas usa los recursos locales de `assets/marcas/`
  cuando están disponibles (Interbank, Inkafarma, Mifarma y Don Belisario);
  para las demás empresas conserva los discos con iniciales. La procedencia
  de los recursos del origen iOS está en `ios-main/Marcas/`. Los nombres de
  empresas son referencias para el selector de sede de trabajo.
- Las billeteras del monedero son parodias: **"Yapo"/"Plun"**, con cuentas
  inventadas. Ninguna app de pagos reales leerá esos QR como un cobro.

## Logo institucional

- El logo UTP en monocromo del carné digital es un asset local con fines
  de demostración educativa.
