# Modelos y referencias importados de RutaUTP iOS

Fuente: repositorio del equipo `JDiazD33/RutaUTP`, main
`138900d06bf57a8835ad6c4261f3b608cf6b66b5` (30 de octubre de 2026).

Los seis archivos `RutaUTP/Resources/Transportes3D/**/*.usdz` se convirtieron a
`app/src/main/assets/transportes3d/*.mesh.gz` mediante
`tools/convertir_transportes_ios.py`. Se conservan sus triángulos, normales,
transformaciones y colores de materiales; solo se normalizan eje y escala
para el visor Android. No se generan vehículos nuevos ni se sustituyen por sprites.

La conversión requiere usd-core y NumPy en el equipo de desarrollo. Ninguna de
esas dependencias se empaqueta en Android. El visor utiliza OpenGL ES del sistema.

`referencias_transporte.json` conserva por route_id el código, letra, empresa,
colores, categoría de modelo, carácter orientativo y enlace de procedencia de
`IdentificacionRuta3D.swift`. Las fuentes se presentan en la ficha; no se
descargan fotografías. Los colores no certifican toda la flota de una empresa.

Los avisos de procedencia y marcas del origen están copiados en `ios-main/`.
Los logotipos importados se usan bajo las condiciones que allí documenta el
proyecto del equipo; esta copia no otorga permisos adicionales sobre las marcas.
