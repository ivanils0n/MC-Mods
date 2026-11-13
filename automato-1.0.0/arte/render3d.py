"""Imagem 3D (estática) do modelo do Autômato: python3 arte/render3d.py saida.png"""
import math

import numpy as np
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import gerar  # noqa: E402

SHADE = {"up": 1.0, "down": 0.5, "north": 0.85, "south": 0.85, "east": 0.68, "west": 0.68}
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def face_world(face, a, b, u, v):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {"north": (16 - u, 16 - v, z1), "south": (u, 16 - v, z2), "east": (x2, 16 - v, 16 - u),
            "west": (x1, 16 - v, u), "up": (u, y2, v), "down": (u, y1, 16 - v)}[face]


def auto_uv(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {"north": (16 - x2, 16 - y2, 16 - x1, 16 - y1), "south": (x1, 16 - y2, x2, 16 - y1),
            "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1), "west": (z1, 16 - y2, z2, 16 - y1),
            "up": (x1, z1, x2, z2), "down": (x1, 16 - z2, x2, 16 - z1)}[face]


def rotator(r):
    """Rotação de elemento do Minecraft (um eixo, ângulo em graus, em volta de origin)."""
    if not r:
        return lambda p, vec=False: p
    t = math.radians(r["angle"])
    c, sn = math.cos(t), math.sin(t)
    o = r["origin"]
    ax = r["axis"]

    def f(p, vec=False):
        q = list(p) if vec else [p[i] - o[i] for i in range(3)]
        x, y, z = q
        if ax == "x":
            y, z = y * c - z * sn, y * sn + z * c
        elif ax == "y":
            x, z = x * c + z * sn, -x * sn + z * c
        else:
            x, y = x * c - y * sn, x * sn + y * c
        return (x, y, z) if vec else (x + o[0], y + o[1], z + o[2])
    return f


def render(elements, tex, view=(-1.0, 1.1, -1.4), scale=22, size=640):
    V = norm(view)
    f = tuple(-c for c in V)
    right = norm(cross(f, (0, 1, 0)))
    up = cross(right, f)
    c = (8, 8, 8)
    proj = lambda p: (size / 2 + scale * dot([p[i] - c[i] for i in range(3)], right),
                      size / 2 - scale * dot([p[i] - c[i] for i in range(3)], up))
    faces = []
    for e in elements:
        a, b = e["from"], e["to"]
        rot = rotator(e.get("rotation"))
        for name, fd in e["faces"].items():
            if dot(rot(NORMAL[name], True), V) <= 0:
                continue
            u0, v0, u1, v1 = auto_uv(name, a, b)
            if u1 == u0 or v1 == v0:
                continue
            ctr = face_world(name, a, b, (u0 + u1) / 2, (v0 + v1) / 2)
            faces.append((dot(ctr, V), name, a, b, (u0, v0, u1, v1), fd["texture"][1:], rot))
    out = np.zeros((size, size, 4), np.uint8)
    zbuf = np.full((size, size), -1e9)
    ys, xs = np.mgrid[0:size, 0:size] + 0.5
    for _, name, a, b, (u0, v0, u1, v1), tk, rot in faces:
        fw = lambda u, v: rot(face_world(name, a, b, u, v))
        A = proj(fw(u0, v0))
        B = proj(fw(u1, v0))
        C = proj(fw(u0, v1))
        # tela = A + (u-u0)*eu + (v-v0)*ev  ->  inversa
        eu = ((B[0] - A[0]) / (u1 - u0), (B[1] - A[1]) / (u1 - u0))
        ev = ((C[0] - A[0]) / (v1 - v0), (C[1] - A[1]) / (v1 - v0))
        det = eu[0] * ev[1] - eu[1] * ev[0]
        ia, ib, ic, id_ = ev[1] / det, -ev[0] / det, -eu[1] / det, eu[0] / det
        coef = (ia, ib, u0 - ia * A[0] - ib * A[1], ic, id_, v0 - ic * A[0] - id_ * A[1])
        src = tex[tk].convert("RGBA")
        k = SHADE[name]
        src = Image.merge("RGBA", [ch.point(lambda x: int(x * k)) for ch in src.split()[:3]] + [src.split()[3]])
        layer = src.transform((size, size), Image.AFFINE, coef, Image.NEAREST)
        # profundidade de cada pixel (no plano da face) e máscara do paralelogramo
        uu = coef[0] * xs + coef[1] * ys + coef[2]
        vv = coef[3] * xs + coef[4] * ys + coef[5]
        inside = (uu >= min(u0, u1)) & (uu <= max(u0, u1)) & (vv >= min(v0, v1)) & (vv <= max(v0, v1))
        p0 = np.array(fw(u0, v0))
        du = (np.array(fw(u1, v0)) - p0) / (u1 - u0)
        dv = (np.array(fw(u0, v1)) - p0) / (v1 - v0)
        depth = dot(p0, V) + (uu - u0) * dot(du, V) + (vv - v0) * dot(dv, V)
        arr = np.asarray(layer)
        win = inside & (depth > zbuf + 1e-6) & (arr[..., 3] > 0)
        out[win] = arr[win]
        zbuf[win] = depth[win]
    return Image.fromarray(out, "RGBA")


if __name__ == "__main__":
    t = gerar.g3d_textures()
    els = gerar.g3d_elements("#wall")
    off = dict(t)
    on = dict(t, wall=t["wall_on"].crop((0, 0, 16, 16)))
    views = [("desligado", off, (-1.0, 1.1, -1.4)), ("ligado", on, (-1.0, 1.1, -1.4)), ("de frente, ligado", on, (-0.25, 0.3, -1.5))]
    W = 640
    img = Image.new("RGBA", (W * 3, W + 60), gerar.hx("#1E1F24"))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)
    for i, (label, tx, v) in enumerate(views):
        img.alpha_composite(render(els, tx, v), (i * W, 0))
        d.text((i * W + 220, W + 5), label, fill=gerar.hx("#FFB060" if "ligado" == label.split(",")[0].split()[-1] else "#BBBBBB"), font=font)
    img.save(sys.argv[1])
