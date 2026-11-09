"""Convierte los USDZ originales a mallas locales para OpenGL ES de Android.

Uso: PXR_WORK_THREAD_LIMIT=1 python convertir_transportes_ios.py --source <Transportes3D>
Requiere usd-core y numpy solo para la conversión; la app no necesita esas librerías.
"""
import argparse
import gzip
import os
from pathlib import Path
import struct

os.environ.setdefault("PXR_WORK_THREAD_LIMIT", "1")


def convert(source: Path, output: Path):
    import numpy as np
    from pxr import Usd, UsdGeom, UsdShade, Gf

    for path in sorted(source.rglob("*.usdz")):
        stage = Usd.Stage.Open(str(path))
        cache = UsdGeom.XformCache()
        z_up = UsdGeom.GetStageUpAxis(stage) == UsdGeom.Tokens.z
        triangles = []
        for prim in stage.Traverse():
            if not prim.IsA(UsdGeom.Mesh):
                continue
            mesh = UsdGeom.Mesh(prim)
            if UsdGeom.Imageable(prim).ComputeVisibility() == UsdGeom.Tokens.invisible:
                continue
            world = cache.GetLocalToWorldTransform(prim)
            points = np.array([tuple(world.Transform(Gf.Vec3d(p))) for p in mesh.GetPointsAttr().Get()], dtype=np.float64)
            normals_raw = mesh.GetNormalsAttr().Get()
            normals = np.array([tuple(world.TransformDir(Gf.Vec3d(n))) for n in normals_raw], dtype=np.float64) if normals_raw else None
            counts = mesh.GetFaceVertexCountsAttr().Get()
            indices = mesh.GetFaceVertexIndicesAttr().Get()
            color = [0.7, 0.7, 0.7, 1.0]
            material = UsdShade.MaterialBindingAPI(prim).ComputeBoundMaterial()[0]
            if material:
                for shader_prim in Usd.PrimRange(material.GetPrim()):
                    if shader_prim.IsA(UsdShade.Shader):
                        value = UsdShade.Shader(shader_prim).GetInput("diffuseColor").Get()
                        if value is not None:
                            color[:3] = list(value)
            cursor = 0
            interpolation = mesh.GetNormalsInterpolation()
            for count in counts:
                for k in range(1, count - 1):
                    corners = [cursor, cursor + k, cursor + k + 1]
                    tri = [points[indices[c]] for c in corners]
                    flat = np.cross(tri[1] - tri[0], tri[2] - tri[0])
                    flat /= max(1e-12, np.linalg.norm(flat))
                    for corner, point in zip(corners, tri):
                        ni = corner if interpolation == "faceVarying" else indices[corner]
                        normal = normals[ni].copy() if normals is not None and ni < len(normals) else flat.copy()
                        normal /= max(1e-12, np.linalg.norm(normal))
                        if z_up:
                            point = np.array([point[0], point[2], -point[1]])
                            normal = np.array([normal[0], normal[2], -normal[1]])
                        triangles.append([*point, *normal, *color])
                cursor += count
        data = np.asarray(triangles, dtype=np.float32)
        if not len(data) or not np.isfinite(data).all():
            raise ValueError(f"Malla inválida: {path}")
        low, high = data[:, :3].min(axis=0), data[:, :3].max(axis=0)
        data[:, :3] = (data[:, :3] - (low + high) / 2) * (4 / max(high - low))
        name = ("detallado_" if "Detallado" in path.parts else "original_") + path.stem + ".mesh.gz"
        output.mkdir(parents=True, exist_ok=True)
        with gzip.GzipFile(filename=str(output / name), mode="wb", mtime=0) as stream:
            stream.write(b"RUT3" + struct.pack(">i", len(data)))
            stream.write(data.astype(">f4").tobytes())
        print(name, "vértices", len(data), "bytes", (output / name).stat().st_size, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/transportes3d"))
    args = parser.parse_args()
    convert(args.source, args.output)
