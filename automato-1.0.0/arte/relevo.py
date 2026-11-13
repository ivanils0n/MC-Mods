"""Modelo com relevo da opção G: tijolos saltados (rejunte 0,5 px para dentro) e duas frentes.

Frente 1 "Arco com grade": boca em arco, contorno de pedra lisa e grade de ferro, brasa no fundo.
Frente 2 "Braço de madeira": abertura quadrada com um braço de carvalho e ponta de ferro saindo dela.
"""
import random

from gerar import new, noise, rect, px, hx, bevel, g_plate, g_back, g_oak, g_inner, g_wall
from PIL import Image

FACES = ["north", "south", "east", "west", "up", "down"]


def mortar(u, v):
    """Rejunte do desenho de tijolos de g_plate (linhas 0,4,8,12 e juntas verticais alternadas)."""
    if v % 4 == 0:
        return True
    band = v // 4
    return u in ((5, 13), (1, 9), (5, 13), (1, 9))[band]


def box(face, u0, v0, u1, v1, d0, d1):
    """Caixa no espaço do modelo para um retângulo (u, v) da textura de uma face, de d0 a d1 de profundidade."""
    if face == "north":
        return [16 - u1, 16 - v1, d0], [16 - u0, 16 - v0, d1]
    if face == "south":
        return [u0, 16 - v1, 16 - d1], [u1, 16 - v0, 16 - d0]
    if face == "east":
        return [16 - d1, 16 - v1, 16 - u1], [16 - d0, 16 - v0, 16 - u0]
    if face == "west":
        return [d0, 16 - v1, u0], [d1, 16 - v0, u1]
    if face == "up":
        return [u0, 16 - d1, v0], [u1, 16 - d0, v1]
    return [u0, d0, 16 - v1], [u1, d1, 16 - v0]  # down


def element(a, b, tex, inner=None):
    faces = {f: {"texture": tex} for f in FACES}
    if inner:
        for f in FACES:
            faces[f] = {"texture": inner}
        faces[inner_face_skip(a, b)] = {"texture": tex}
    return {"from": a, "to": b, "faces": faces}


def inner_face_skip(a, b):
    return "north"


def rects(grid):
    """Junta células iguais em retângulos (linhas, depois colunas iguais). grid[v][u] = chave ou None."""
    runs = []
    for v in range(16):
        u = 0
        while u < 16:
            k = grid[v][u]
            e = u
            while e + 1 < 16 and grid[v][e + 1] == k:
                e += 1
            if k is not None:
                runs.append([u, v, e + 1, v + 1, k])
            u = e + 1
    merged = []
    for r in runs:
        for m in merged:
            if m[0] == r[0] and m[2] == r[2] and m[3] == r[1] and m[4] == r[4]:
                m[3] = r[3]
                break
        else:
            merged.append(r)
    return merged


def clip(a, b, lo=0.5, hi=15.5):
    return [max(lo, c) for c in a], [min(hi, c) for c in b]


def side_bricks(face, tex):
    """Tijolos de uma face lateral: 0 a 0,5 px; o rejunte é o núcleo, 0,5 px para dentro."""
    grid = [[None if mortar(u, v) else "b" for u in range(16)] for v in range(16)]
    return [{"from": a, "to": b, "faces": {f: {"texture": tex} for f in FACES}}
            for u0, v0, u1, v1, _ in rects(grid) for a, b in [box(face, u0, v0, u1, v1, 0, 0.5)]]


def front(design):
    """Mapa da frente: profundidade de cada célula (0 tijolo, 0,5 rejunte, 3 buraco) e textura."""
    depth = [[0.5 if mortar(u, v) else 0 for u in range(16)] for v in range(16)]
    tex = [["#side"] * 16 for _ in range(16)]
    extra = []
    if design in (1, 4, 5):
        hole = set()
        for v in range(4, 13):
            span = (7, 8) if v == 4 else (6, 9) if v == 5 else (5, 10)
            for u in range(span[0], span[1] + 1):
                hole.add((u, v))
        for v in range(3, 14):  # contorno de pedra lisa, um pouco saltado
            for u in range(4, 12):
                if (u, v) in hole:
                    continue
                if any((u + du, v + dv) in hole for du in (-1, 0, 1) for dv in (-1, 0, 1)):
                    depth[v][u] = -0.5
                    tex[v][u] = "#smooth"
        for u, v in hole:
            depth[v][u] = 3
        if design == 5:  # a ferramenta de verdade aparece por cima (um item_display), então o arco fica vazio
            return finish(depth, tex, extra)
        if design == 4:
            def add(u0, v0, u1, v1, d0, d1, t, rot=None):
                a, b = box("north", u0, v0, u1, v1, d0, d1)
                e = {"from": a, "to": b, "faces": {f: {"texture": t} for f in FACES}}
                if rot:
                    e["rotation"] = {"angle": rot[0], "axis": "x", "origin": [8, rot[1], -4]}
                extra.append(e)
            add(7.25, 8.25, 8.75, 9.75, -3.5, 3, "#oak")               # cabo
            add(7, 7.5, 9, 10.5, -4.75, -3.25, "#pick")                # olho
            add(7.25, 3.5, 8.75, 9, -4.5, -3.5, "#pick", (22.5, 7))      # metade de cima, curvada para o bloco
            add(7.25, 9, 8.75, 14.5, -4.5, -3.5, "#pick", (-22.5, 7))    # metade de baixo
            add(7.5, 2, 8.5, 3.5, -4.25, -3.75, "#pick_dark", (22.5, 7))
            add(7.5, 14.5, 8.5, 16, -4.25, -3.75, "#pick_dark", (-22.5, 7))
            return finish(depth, tex, extra)
        for u in (6.5, 8.5):  # grade de ferro
            a, b = box("north", u, 5, u + 1, 13, 0.5, 1.5)
            extra.append({"from": a, "to": b, "faces": {f: {"texture": "#iron"} for f in FACES}})
        a, b = box("north", 5, 9, 11, 10, 0.75, 1.25)
        extra.append({"from": a, "to": b, "faces": {f: {"texture": "#iron"} for f in FACES}})
    elif design == 3:
        for v in range(4, 12):
            for u in range(4, 12):
                if 4 < u < 11 and 4 < v < 11:
                    depth[v][u] = 3
                else:
                    depth[v][u] = -0.5
                    tex[v][u] = "#smooth"
        def add(u0, v0, u1, v1, d0, d1, t):
            a, b = box("north", u0, v0, u1, v1, d0, d1)
            extra.append({"from": a, "to": b, "faces": {f: {"texture": t} for f in FACES}})
        def rot(angle, ox):
            extra[-1]["rotation"] = {"angle": angle, "axis": "z", "origin": [ox, 9, -4]}
        add(7.25, 6.25, 8.75, 7.75, -3.5, 3, "#oak")        # cabo, saindo do fundo
        add(6.75, 5.5, 9.25, 8, -4.75, -3.25, "#pick")      # olho da cabeça
        # cabeça em arco: cada metade inclinada 22,5° (as pontas descem), com a ponta mais escura
        add(3, 6, 8, 7.5, -4.5, -3.5, "#pick"); rot(-22.5, 8)
        add(8, 6, 13, 7.5, -4.5, -3.5, "#pick"); rot(22.5, 8)
        add(1.5, 6.25, 3, 7.25, -4.25, -3.75, "#pick_dark"); rot(-22.5, 8)
        add(13, 6.25, 14.5, 7.25, -4.25, -3.75, "#pick_dark"); rot(22.5, 8)
    else:
        for v in range(4, 12):
            for u in range(4, 12):
                if 4 < u < 11 and 4 < v < 11:
                    depth[v][u] = 3
                else:
                    depth[v][u] = -0.5
                    tex[v][u] = "#oak"
        a, b = box("north", 6.5, 6.5, 9.5, 9.5, -2.5, 3)  # braço
        extra.append({"from": a, "to": b, "faces": {f: {"texture": "#oak"} for f in FACES}})
        a, b = box("north", 5.5, 5.5, 10.5, 10.5, -4, -2.5)  # ponta de ferro
        extra.append({"from": a, "to": b, "faces": {f: {"texture": "#iron"} for f in FACES}})
        a, b = box("north", 7, 7, 9, 9, -4.5, -4)
        extra.append({"from": a, "to": b, "faces": {f: {"texture": "#iron_dark"} for f in FACES}})
    return finish(depth, tex, extra)


def finish(depth, tex, extra):
    els = []
    grid = [[(depth[v][u], tex[v][u]) if depth[v][u] < 3 else None for u in range(16)] for v in range(16)]
    for u0, v0, u1, v1, (d, t) in rects(grid):
        if d < 0.5:  # parte saltada, até a superfície do rejunte
            a, b = box("north", u0, v0, u1, v1, d, 0.5)
            els.append({"from": a, "to": b, "faces": {f: {"texture": t} for f in FACES}})
        a, b = box("north", u0, v0, u1, v1, 0.5, 3)
        a, b = clip(a, b)
        els.append({"from": a, "to": b, "faces": {f: {"texture": "#side" if t == "#side" else "#inner"} for f in FACES} | {"north": {"texture": t}}})
    return els + extra


def elements(design, wall="#wall"):
    core = {"from": [0.5, 0.5, 3], "to": [15.5, 15.5, 15.5],
            "faces": {"north": {"texture": wall}, "south": {"texture": "#back"}, "east": {"texture": "#side"},
                      "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}}
    out = [core] + front(design)
    for f, t in (("east", "#side"), ("west", "#side"), ("up", "#top"), ("down", "#top"), ("south", "#back")):
        out += side_bricks(f, t)
    return out


def textures():
    smooth = new()
    noise(smooth, ["#A8A8A8", "#A2A2A2", "#AEAEAE"], 9)
    bevel(smooth, "#BDBDBD", "#8A8A8A")
    iron = new()
    noise(iron, ["#5F6469", "#676C71", "#585D62"], 10)
    dark = new()
    noise(dark, ["#3A3D41", "#33363A"], 11)
    pick = new()
    noise(pick, ["#D8DCE0", "#CDD2D6", "#E4E7EA"], 12)
    pdark = new()
    noise(pdark, ["#9EA4AA", "#A9AEB3"], 13)
    back = g_back()
    t = {"side": g_plate(5), "top": g_plate(6), "back": back, "oak": g_oak(7), "inner": g_inner(8),
         "smooth": smooth, "iron": iron, "iron_dark": dark, "pick": pick, "pick_dark": pdark, "wall": g_wall(False)}
    on = Image.new("RGBA", (16, 32))
    on.paste(g_wall(True, 0), (0, 0))
    on.paste(g_wall(True, 1), (0, 16))
    t["wall_on"] = on
    return t
