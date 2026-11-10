"""Gera texturas, modelos e definições de item do Altar de Teleporte (visual "Obelisco rúnico").

Rode da pasta tpaltar-1.0.0:  python3 arte/gerar.py
Saída: src/main/resources/assets/... (vai no .jar e no zip do resource pack) e src/pack/pack.png.

As decorações são item_display com escala própria (o visual sem resource pack continua o antigo), então as
coordenadas dos modelos compensam essa escala: veja to_a()/to_b() e spawnVisuals() em TpAltar.java.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src/main/resources/assets")
TEX = os.path.join(RES, "tpaltar/textures/block")
MOD = os.path.join(RES, "tpaltar/models/item")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def lerp(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def save(img, name, frametime=None):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, name + ".png"))
    meta = os.path.join(TEX, name + ".png.mcmeta")
    if frametime:
        with open(meta, "w") as f:
            json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f, indent=1)
    elif os.path.exists(meta):
        os.remove(meta)


def stone(seed, base="#2b2238", dark="#221a2e", light="#3a2f4c", crack="#16101f"):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    pal = [hexc(dark), hexc(base), hexc(base), hexc(base), hexc(light)]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rnd.choice(pal))
    x, y = rnd.randrange(16), rnd.randrange(16)
    for _ in range(9):  # uma rachadura fina
        img.putpixel((x % 16, y % 16), hexc(crack))
        x += rnd.choice((-1, 0, 1))
        y += 1
    return img


GLYPHS = [
    ["..#..", ".###.", "#.#.#", "..#.."],
    ["#...#", ".#.#.", ".#.#.", "#...#"],
    [".###.", "#.#.#", "#...#", ".###."],
]


def runes(frames=8):
    """Runas que acendem em onda, de baixo para cima. Fundo transparente (sobreposição)."""
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    dim, bright = hexc("#4a2378"), hexc("#f0c4ff")
    for k in range(frames):
        for i, g in enumerate(GLYPHS):
            phase = (k - 2 * (len(GLYPHS) - 1 - i)) / frames  # glifo de baixo acende primeiro
            t = 0.5 + 0.5 * math.sin(2 * math.pi * phase)
            col = lerp(dim, bright, t ** 1.5)
            y0 = 1 + 5 * i
            for dy, row in enumerate(g):
                for dx, ch in enumerate(row):
                    if ch == "#":
                        img.putpixel((5 + dx, 16 * k + y0 + dy), col)
    return img


def plinth_side():
    img = stone(11, base="#2e2440")
    trim, hi = hexc("#4e3f6e"), hexc("#7a5fa8")
    for x in range(16):
        img.putpixel((x, 0), hi)
        img.putpixel((x, 1), trim)
        img.putpixel((x, 14), trim)
        img.putpixel((x, 15), hexc("#1a1424"))
    for y in range(5, 11):  # moldura do encaixe central
        for x in range(4, 12):
            if x in (4, 11) or y in (5, 10):
                img.putpixel((x, y), hexc("#16101f"))
    for dy, row in enumerate(GLYPHS[2]):
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((5 + dx + 1, 6 + dy), hexc("#9b6bd6"))
    return img


def plinth_top():
    img = stone(12, base="#2e2440")
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 5.6 < r < 6.6:
                img.putpixel((x, y), hexc("#6e4fa0"))
            elif r > 7.6:
                img.putpixel((x, y), hexc("#4e3f6e"))
    return img


def crystal(frames=8, base="#5b34b0", facet="#8e5be0", shine="#efe0ff"):
    img = Image.new("RGBA", (16, 16 * frames))
    for k in range(frames):
        band = (k / frames) * 28 - 6  # faixa de brilho que atravessa a face
        for y in range(16):
            for x in range(16):
                c = hexc(facet) if (x + y) % 8 < 3 else hexc(base)
                d = abs((x + (15 - y)) - band)
                if d < 2.5:
                    c = lerp(c, hexc(shine), 1 - d / 2.5)
                img.putpixel((x, 16 * k + y), c)
    return img


def eye_shell():
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16), hexc("#0b1714"))
    for _ in range(4):  # veias
        x, y = rnd.randrange(16), rnd.randrange(16)
        for _ in range(10):
            img.putpixel((x % 16, y % 16), hexc("#1b5a48"))
            x += rnd.choice((-1, 1))
            y += rnd.choice((-1, 0, 1))
    for _ in range(10):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), hexc("#2c8a6c"))
    return img


def iris(outer, inner, rim, frames=8, speed=1):
    """Íris com raios que giram."""
    img = Image.new("RGBA", (16, 16 * frames))
    for k in range(frames):
        ph = 2 * math.pi * k / frames * speed
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                if r > 7.2:
                    c = hexc(rim)
                else:
                    c = lerp(hexc(inner), hexc(outer), min(r / 7.2, 1))
                    s = 0.5 + 0.5 * math.sin(6 * a + ph)
                    c = lerp(c, hexc(inner), 0.35 * s)
                img.putpixel((x, 16 * k + y), c)
    return img


def pupil(glow, core, slit, widths, flicker=None):
    """Pupila em fenda que abre e fecha."""
    frames = len(widths)
    img = Image.new("RGBA", (16, 16 * frames))
    for k, w in enumerate(widths):
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                c = lerp(hexc(core), hexc(glow), min(r / 8, 1))
                img.putpixel((x, 16 * k + y), c)
        for y in range(2, 14):
            half = w / 2 * (1 - abs(y - 7.5) / 7)  # fenda afinando nas pontas
            for x in range(16):
                if abs(x - 7.5) <= max(half, 0.5):
                    img.putpixel((x, 16 * k + y), hexc(slit))
        if flicker and k % 2 == 0:
            img.putpixel((7, 16 * k + 7), hexc(flicker))
            img.putpixel((8, 16 * k + 8), hexc(flicker))
    return img


def node(c1, c2, frames=6):
    img = Image.new("RGBA", (16, 16 * frames))
    for k in range(frames):
        t = 0.5 + 0.5 * math.sin(2 * math.pi * k / frames)
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5) / 10.6
                img.putpixel((x, 16 * k + y), lerp(hexc(c2), hexc(c1), min(1, r + 0.4 * (1 - t))))
    return img


ICON = [
    "......kkkk......",
    ".....kGgbGk.....",
    ".....kgGbgk.....",
    "......kkkk......",
    ".......tt.......",
    "......kttk......",
    "......kSsk......",
    ".....kSrsSk.....",
    ".....kSssSk.....",
    ".....kSrrSk.....",
    "....kSSssSSk....",
    "....kSsrsrSk....",
    "...kPPPPPPPPk...",
    "...kpprpprppk...",
    "...kppppppppk...",
    "...kkkkkkkkkk...",
]
ICON_COLORS = {
    "k": "#120c22", "s": "#2b2238", "S": "#3d3150", "r": "#d68cff", "t": "#b98cf0",
    "p": "#3a2e52", "P": "#7a5fa8", "g": "#1d9e75", "G": "#5dcaa5", "b": "#04342c",
}


def icon(scale=1):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ICON):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), hexc(ICON_COLORS[ch]))
    return img.resize((16 * scale, 16 * scale), Image.NEAREST)


# ---------- modelos ----------

def to_a(x, y, z):
    """Mundo (relativo ao centro da base, y a partir do chão) -> modelo da peça "obelisco".
    Entidade em y+1,5 com escala (0,5; 1; 0,5)."""
    return [round(8 + 32 * x, 3), round(8 + 16 * (y - 1.5), 3), round(8 + 32 * z, 3)]


def to_b(x, y, z):
    """Peça "cristais": entidade em y+2,125 com escala (0,8; 0,25; 0,8)."""
    return [round(8 + 20 * x, 3), round(8 + 64 * (y - 2.125), 3), round(8 + 20 * z, 3)]


def box(conv, half_x, y0, y1, tex_side, tex_top=None, light=None, sides_only=False, cx=0.0, cz=0.0, half_z=None):
    half_z = half_x if half_z is None else half_z
    a = conv(cx - half_x, y0, cz - half_z)
    b = conv(cx + half_x, y1, cz + half_z)
    w, d, h = 2 * half_x, 2 * half_z, y1 - y0
    uv = lambda u, v: [0, 0, min(16, round(u * 16, 2)) or 1, min(16, round(v * 16, 2)) or 1]
    faces = {
        "north": {"texture": tex_side, "uv": uv(w, h)},
        "south": {"texture": tex_side, "uv": uv(w, h)},
        "east": {"texture": tex_side, "uv": uv(d, h)},
        "west": {"texture": tex_side, "uv": uv(d, h)},
    }
    if not sides_only:
        faces["up"] = {"texture": tex_top or tex_side, "uv": uv(w, d)}
        faces["down"] = {"texture": tex_top or tex_side, "uv": uv(w, d)}
    e = {"from": a, "to": b, "faces": faces}
    if light:
        e["light_emission"] = light
    return e


def full_uv(e):
    for f in e["faces"].values():
        f["uv"] = [0, 0, 16, 16]
    return e


def obelisk_model():
    tiers = [(0.20, 1.08, 1.60), (0.165, 1.60, 2.10), (0.13, 2.10, 2.45)]
    els = [
        full_uv(box(to_a, 0.51, 0.0, 1.01, "#plinth", "#plinth_top")),  # cobre a obsidiana
        box(to_a, 0.30, 1.0, 1.08, "#stone"),
    ]
    for half, y0, y1 in tiers:
        els.append(box(to_a, half, y0, y1, "#stone"))
        els.append(full_uv(box(to_a, half + 0.006, y0 + 0.03, y1 - 0.03, "#runes", light=15, sides_only=True)))
    els.append(full_uv(box(to_a, 0.085, 2.45, 2.55, "#tip", light=10)))
    els.append(full_uv(box(to_a, 0.04, 2.55, 2.62, "#tip", light=13)))
    return {
        "textures": {
            "particle": "tpaltar:block/obelisk_stone", "stone": "tpaltar:block/obelisk_stone",
            "plinth": "tpaltar:block/plinth_side", "plinth_top": "tpaltar:block/plinth_top",
            "runes": "tpaltar:block/obelisk_runes", "tip": "tpaltar:block/crystal",
        },
        "elements": els,
    }


def shards_model():
    els = []
    r = 0.46
    for cx, cz in ((r, 0), (-r, 0), (0, r), (0, -r)):
        els.append(full_uv(box(to_b, 0.035, 1.95, 2.17, "#c", light=12, cx=cx, cz=cz)))
        els.append(full_uv(box(to_b, 0.018, 2.17, 2.22, "#c", light=12, cx=cx, cz=cz)))
        els.append(full_uv(box(to_b, 0.018, 1.90, 1.95, "#c", light=12, cx=cx, cz=cz)))
    return {"textures": {"particle": "tpaltar:block/crystal", "c": "tpaltar:block/crystal"}, "elements": els}


def cube(a, b, faces):
    return {"from": a, "to": b, "faces": {k: {"texture": v, "uv": [0, 0, 16, 16]} for k, v in faces.items()}}


def eye_model(shell, iris_tex, pupil_tex, node_tex):
    all6 = ("north", "south", "east", "west", "up", "down")
    s = {f: "#shell" for f in all6}
    core = cube([4, 4, 4], [12, 12, 12], {**s, "north": "#iris", "south": "#iris"})
    core["light_emission"] = 15
    lens = cube([5, 5, 3], [11, 11, 13], {**s, "north": "#pupil", "south": "#pupil"})
    lens["light_emission"] = 15
    els = [
        core,
        cube([3, 5, 5], [13, 11, 11], s),
        cube([5, 3, 5], [11, 13, 11], s),
        lens,
    ]
    for i in range(8):  # auréola de 8 pontos de luz ao redor do olho
        a = 2 * math.pi * i / 8
        x, y = 8 + 9 * math.cos(a), 8 + 9 * math.sin(a)
        n = 0.6 if i % 2 else 0.8
        e = cube([round(x - n, 2), round(y - n, 2), 8 - n], [round(x + n, 2), round(y + n, 2), 8 + n], {f: "#node" for f in all6})
        e["light_emission"] = 15
        els.append(e)
    return {
        "textures": {"particle": shell, "shell": shell, "iris": iris_tex, "pupil": pupil_tex, "node": node_tex},
        "elements": els,
    }


def altar_item_model():
    """Item do altar: o obelisco em miniatura (inventário, mão e chão usam as poses de bloco do block/block)."""
    p, st, ru, cr = "#plinth", "#stone", "#runes", "#tip"
    all6 = ("north", "south", "east", "west", "up", "down")

    def el(a, b, tex, top=None, light=None, sides_only=False):
        faces = {f: tex for f in all6 if not (sides_only and f in ("up", "down"))}
        if top:
            faces["up"] = top
        e = cube(a, b, faces)
        if light:
            e["light_emission"] = light
        return e

    els = [
        el([2, 0, 2], [14, 4, 14], p, top="#plinth_top"),
        el([4, 4, 4], [12, 5, 12], st),
        el([5, 5, 5], [11, 8.5, 11], st), el([4.9, 5.3, 4.9], [11.1, 8.2, 11.1], ru, light=15, sides_only=True),
        el([5.5, 8.5, 5.5], [10.5, 11, 10.5], st), el([5.4, 8.8, 5.4], [10.6, 10.7, 10.6], ru, light=15, sides_only=True),
        el([6, 11, 6], [10, 12.5, 10], st),
        el([7, 12.5, 7], [9, 13.5, 9], cr, light=13),
        el([6.5, 14.2, 6.5], [9.5, 17.2, 9.5], "#iris", top="#shell", light=15),
    ]
    for x, z in ((2.2, 7.5), (12.8, 7.5), (7.5, 2.2), (7.5, 12.8)):  # cristais em volta
        els.append(el([x - 0.6, 7, z - 0.6], [x + 0.6, 10.5, z + 0.6], cr, light=12))
    t = "tpaltar:block/"
    # Poses próprias: o obelisco é alto e fino, então fica maior no inventário e menor na mão para caber inteiro.
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.78, 0.78, 0.78]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0.5, 0], "scale": [0.28, 0.28, 0.28]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0.5, 0], "scale": [0.28, 0.28, 0.28]},
    }
    return {
        "parent": "minecraft:block/block",
        "display": display,
        "textures": {
            "particle": t + "obelisk_stone", "stone": t + "obelisk_stone", "plinth": t + "plinth_side",
            "plinth_top": t + "plinth_top", "runes": t + "obelisk_runes", "tip": t + "crystal",
            "iris": t + "eye_iris_idle", "shell": t + "eye_shell",
        },
        "elements": els,
    }


# ---------- telas (tema "Pedra rúnica") ----------
# Fundos desenhados como um caractere de fonte no título da tela (font tpaltar:gui). As posições seguem as telas
# vanilla: baú 176 x (114 + 18*fileiras), bigorna 176 x 166. Ver TpAltar.guiTitle().

GUI = os.path.join(RES, "tpaltar/textures/gui")
UI = {
    "bg": "#241c30", "bg2": "#2b2238", "bg3": "#1f1829", "edge": "#120c22", "hi": "#6b5a8c", "lo": "#3a2e52",
    "inner": "#4e3f6e", "slot": "#16101f", "slot_dark": "#0d0914", "slot_light": "#7a5fa8",
    "socket": "#c77dff", "socket_in": "#1b1030", "rune": "#5e4a86", "plate": "#b9add0", "plate_edge": "#7a6f94",
}
HOLE = (0, 0, 0, 0)


def gui_panel(w, h, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    pal = [hexc(UI["bg"])] * 3 + [hexc(UI["bg2"]), hexc(UI["bg3"])]
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), rnd.choice(pal))
    for x in range(w):
        img.putpixel((x, 0), hexc(UI["edge"])); img.putpixel((x, h - 1), hexc(UI["edge"]))
        img.putpixel((x, 1), hexc(UI["hi"])); img.putpixel((x, h - 2), hexc(UI["lo"]))
    for y in range(h):
        img.putpixel((0, y), hexc(UI["edge"])); img.putpixel((w - 1, y), hexc(UI["edge"]))
        img.putpixel((1, y), hexc(UI["hi"])); img.putpixel((w - 2, y), hexc(UI["lo"]))
    for x in range(4, w - 4):  # filete interno
        img.putpixel((x, 3), hexc(UI["inner"])); img.putpixel((x, h - 4), hexc(UI["inner"]))
    for y in range(4, h - 4):
        img.putpixel((3, y), hexc(UI["inner"])); img.putpixel((w - 4, y), hexc(UI["inner"]))
    return img


def gui_slot(img, x, y, hole):
    for i in range(18):
        img.putpixel((x + i, y), hexc(UI["slot_dark"])); img.putpixel((x, y + i), hexc(UI["slot_dark"]))
        img.putpixel((x + i, y + 17), hexc(UI["slot_light"])); img.putpixel((x + 17, y + i), hexc(UI["slot_light"]))
    for j in range(1, 17):
        for i in range(1, 17):
            img.putpixel((x + i, y + j), HOLE if hole else hexc(UI["slot"]))


def gui_socket(img, x, y, hole):
    for i in range(18):
        for px, py in ((x + i, y), (x + i, y + 17), (x, y + i), (x + 17, y + i)):
            img.putpixel((px, py), hexc(UI["socket"]))
    for j in range(1, 17):
        for i in range(1, 17):
            edge = i in (1, 16) or j in (1, 16)
            img.putpixel((x + i, y + j), HOLE if hole and not edge else hexc(UI["edge"] if edge else UI["socket_in"]))


def gui_rune(img, x, y, glyph):
    for dy, row in enumerate(GLYPHS[glyph % len(GLYPHS)]):
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x + 6 + dx, y + 7 + dy), hexc(UI["rune"]))


def gui_plate(img, x, y, w=58, h=11):
    for j in range(h):
        for i in range(w):
            edge = i in (0, w - 1) or j in (0, h - 1)
            img.putpixel((x + i, y + j), hexc(UI["plate_edge" if edge else "plate"]))


def gui_player_inv(img, top, hole):
    for r in range(3):
        for c in range(9):
            gui_slot(img, 7 + 18 * c, top + 18 * r, hole)
    for c in range(9):
        gui_slot(img, 7 + 18 * c, top + 58, hole)


def chest_bg(rows, hole):
    h = 114 + 18 * rows
    img = gui_panel(176, h, rows)
    for r in range(rows - 1):  # destinos
        for c in range(9):
            gui_slot(img, 7 + 18 * c, 17 + 18 * r, hole)
    y = 17 + 18 * (rows - 1)  # estoque de pérolas: 4 encaixes no meio, runas nos lados (os vidros ficam invisíveis)
    for c in range(9):
        if 2 <= c <= 5:
            gui_socket(img, 7 + 18 * c, y, hole)
        else:
            gui_rune(img, 7 + 18 * c, y, c)
    gui_plate(img, 6, h - 96)  # placa clara atrás do texto "Inventário" (o jogo o escreve em cinza escuro)
    gui_player_inv(img, 18 * rows + 30, hole)
    return img


# Tela de nome: tema "Céu do End" (fundo estrelado, constelação, encaixes redondos verde-água).
SKY = {"bg": "#0b0618", "bg2": "#0e081e", "bg3": "#120a26", "edge": "#3c3489", "inner": "#261d55",
       "star": "#e0c8ff", "star2": "#5dcaa5", "line": "#534ab7", "teal": "#5dcaa5", "teal_dim": "#1d9e75",
       "socket_in": "#0b1f1a", "field": "#140a28", "slot": "#140a28", "slot_edge": "#7f77dd",
       "plate": "#afa9ec", "plate_edge": "#534ab7"}
# O jogo desenha o nome digitado antes do fundo da tela (que é um texto): cobrir o campo esconderia o nome.
COVER_NAME_FIELD = False


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), hexc(c) if isinstance(c, str) else c)


def sky_line(img, a, b, c):
    n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
    for k in range(n + 1):
        put(img, round(a[0] + (b[0] - a[0]) * k / n), round(a[1] + (b[1] - a[1]) * k / n), c)


def sky_star(img, x, y, c, big=False):
    put(img, x, y, c)
    if big:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, lerp(hexc(c), hexc(SKY["bg"]), 0.45))


def sky_ring(img, cx, cy, r, c, fill=None):
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if fill and d < r - 0.5:
                put(img, x, y, fill)
            elif abs(d - r) < 0.6:
                put(img, x, y, c)


def anvil_bg():
    rnd = random.Random(26)
    w, h = 176, 166
    img = Image.new("RGBA", (w, h))
    pal = [hexc(SKY["bg"])] * 4 + [hexc(SKY["bg2"]), hexc(SKY["bg3"])]
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), rnd.choice(pal))
    for _ in range(46):  # estrelas soltas
        sky_star(img, rnd.randrange(4, w - 4), rnd.randrange(4, 78), rnd.choice([SKY["star"]] * 3 + [SKY["star2"]]), rnd.random() < 0.2)
    for x in range(w):
        put(img, x, 0, SKY["edge"]); put(img, x, h - 1, SKY["edge"]); put(img, x, 2, SKY["inner"]); put(img, x, h - 3, SKY["inner"])
    for y in range(h):
        put(img, 0, y, SKY["edge"]); put(img, w - 1, y, SKY["edge"]); put(img, 2, y, SKY["inner"]); put(img, w - 3, y, SKY["inner"])
    # constelação no lugar do martelo
    pts = [(10, 34), (20, 16), (34, 22), (46, 8), (40, 38)]
    for a, b in zip(pts, pts[1:4]):
        sky_line(img, a, b, SKY["line"])
    sky_line(img, pts[2], pts[4], SKY["line"])
    for x, y in pts:
        sky_star(img, x, y, SKY["star"], big=True)
    # campo do nome: escuro (o jogo escreve o nome em branco) com borda verde-água arredondada
    for y in range(19, 38):
        for x in range(58, 171):
            corner = (x in (58, 170)) and (y in (19, 37))
            border = x in (58, 170) or y in (19, 37)
            if corner:
                continue
            put(img, x, y, SKY["teal"] if border else (SKY["field"] if COVER_NAME_FIELD else (0, 0, 0, 0)))
    # pérola: encaixe redondo
    sky_ring(img, 35, 55, 9, SKY["teal"], fill=SKY["socket_in"])
    # segundo espaço da bigorna (não usado): mini constelação
    tri = [(80, 50), (89, 53), (84, 60)]
    for a, b in zip(tri, tri[1:] + tri[:1]):
        sky_line(img, a, b, SKY["line"])
    for x, y in tri:
        sky_star(img, x, y, "#afa9ec", big=True)
    # seta tracejada até o resultado
    for x in range(46, 127):
        if (x // 3) % 2 == 0:
            put(img, x, 55, SKY["teal"])
    for i in range(4):
        put(img, 127 - i, 55 - i, SKY["teal"]); put(img, 127 - i, 55 + i, SKY["teal"]); put(img, 128, 55, SKY["teal"])
    # resultado: portal redondo
    sky_ring(img, 142, 55, 13, SKY["teal_dim"])
    sky_ring(img, 142, 55, 11, SKY["teal"], fill=SKY["socket_in"])
    # placa clara atrás de "Inventário" e o inventário do jogador
    for j in range(11):
        for i in range(58):
            edge = i in (0, 57) or j in (0, 10)
            put(img, 6 + i, 70 + j, SKY["plate_edge" if edge else "plate"])
    for r in range(4):
        y0 = 83 + 18 * r + (4 if r == 3 else 0)
        for c in range(9):
            x0 = 7 + 18 * c
            for j in range(18):
                for i in range(18):
                    edge = i in (0, 17) or j in (0, 17)
                    if edge and (i in (0, 17)) and (j in (0, 17)):
                        continue
                    put(img, x0 + i, y0 + j, SKY["slot_edge"] if edge else SKY["slot"])
    return img


def gui_font(slot_holes):
    os.makedirs(GUI, exist_ok=True)
    providers = [{"type": "space", "advances": {
        "\uf000": -8, "\uf001": -177, "\uf002": 8, "\uf003": -60, "\uf004": 60}}]
    for rows in range(1, 7):
        img = chest_bg(rows, slot_holes)
        img.save(os.path.join(GUI, f"altar_{rows}.png"))
        # ascent 13: o título fica em y=6 da tela e o topo do glifo em y + 7 - ascent = 0
        providers.append({"type": "bitmap", "file": f"tpaltar:gui/altar_{rows}.png", "ascent": 13,
                          "height": img.height, "chars": [chr(0xE000 + rows - 1)]})
    img = anvil_bg()
    img.save(os.path.join(GUI, "nome.png"))
    providers.append({"type": "bitmap", "file": "tpaltar:gui/nome.png", "ascent": 13, "height": img.height, "chars": ["\ue006"]})
    write_json(os.path.join(RES, "tpaltar/font/gui.json"), {"providers": providers})


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def select(cases, fallback):
    return {"model": {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "cases": [{"when": w, "model": {"type": "minecraft:model", "model": m}} for w, m in cases],
        "fallback": {"type": "minecraft:model", "model": fallback},
    }}


def main():
    save(stone(3), "obelisk_stone")
    save(runes(), "obelisk_runes", frametime=4)
    save(plinth_side(), "plinth_side")
    save(plinth_top(), "plinth_top")
    save(crystal(), "crystal", frametime=3)
    save(eye_shell(), "eye_shell")
    save(iris("#0f6e56", "#7fe6c2", "#04342c"), "eye_iris_idle", frametime=4)
    save(pupil("#2fbf8f", "#b8ffe4", "#020806", [4, 4, 3, 2, 2, 3]), "eye_pupil_idle", frametime=6)
    save(node("#1d9e75", "#c8fff0"), "eye_node_idle", frametime=4)
    save(iris("#5a1490", "#ff9cff", "#22053a", speed=2), "eye_iris_active", frametime=2)
    save(pupil("#e050ff", "#ffe0ff", "#1a0020", [2, 2, 1, 1, 2, 2], flicker="#ffffff"), "eye_pupil_active", frametime=2)
    save(node("#9b30ff", "#ffe0ff"), "eye_node_active", frametime=2)
    os.makedirs(os.path.join(ROOT, "src/pack"), exist_ok=True)
    icon(8).save(os.path.join(ROOT, "src/pack/pack.png"))

    t = "tpaltar:block/"
    write_json(os.path.join(MOD, "obelisk.json"), obelisk_model())
    write_json(os.path.join(MOD, "shards.json"), shards_model())
    write_json(os.path.join(MOD, "eye_idle.json"), eye_model(t + "eye_shell", t + "eye_iris_idle", t + "eye_pupil_idle", t + "eye_node_idle"))
    write_json(os.path.join(MOD, "eye_active.json"), {
        "parent": "tpaltar:item/eye_idle",
        "textures": {"iris": t + "eye_iris_active", "pupil": t + "eye_pupil_active", "node": t + "eye_node_active"},
    })
    write_json(os.path.join(MOD, "altar_item.json"), altar_item_model())
    write_json(os.path.join(MOD, "blank.json"), {"textures": {"particle": t + "obelisk_stone"}, "elements": []})
    # shortcut: se o jogo desenhar o título por cima dos itens, os espaços precisam ser buracos (slot_holes=True)
    gui_font(slot_holes=False)

    # Só as peças marcadas com custom_model_data mudam; sem a marca (ou sem o pack) fica o visual vanilla.
    items = os.path.join(RES, "minecraft/items")
    write_json(os.path.join(items, "smooth_stone.json"), select(
        [("tpaltar_obelisk", "tpaltar:item/obelisk"), ("tpaltar_ring", "tpaltar:item/shards")], "minecraft:block/smooth_stone"))
    write_json(os.path.join(items, "ender_pearl.json"), select([("tpaltar_eye", "tpaltar:item/eye_idle")], "minecraft:item/ender_pearl"))
    # O item do altar é um olho do fim com custom_data (inclusive os craftados em versões antigas, sem a marca):
    # olho do fim comum não tem custom_data, então ele vira o obelisco em miniatura.
    eye = select([("tpaltar_eye", "tpaltar:item/eye_active")], "minecraft:item/ender_eye")["model"]
    write_json(os.path.join(items, "ender_eye.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:custom_data",
        "on_true": {"type": "minecraft:model", "model": "tpaltar:item/altar_item"},
        "on_false": eye,
    }})

    # Modelos próprios (item_model tpaltar:*), usados no singleplayer: nenhum outro resource pack os substitui.
    for name in ("obelisk", "shards", "eye_idle", "eye_active", "blank"):
        write_json(os.path.join(RES, "tpaltar/items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tpaltar:item/" + name}})
    write_json(os.path.join(RES, "tpaltar/items/altar.json"), {"model": {"type": "minecraft:model", "model": "tpaltar:item/altar_item"}})

    write_json(os.path.join(items, "glass_pane.json"), select([("tpaltar_blank", "tpaltar:item/blank")], "minecraft:item/glass_pane"))

    write_json(os.path.join(ROOT, "src/pack/pack.mcmeta"), {"pack": {
        "description": "Texturas do Altar de Teleporte",
        "min_format": 88,  # 26.2
        "max_format": 999,
    }})


def check():
    for root, _, files in os.walk(MOD):
        for f in files:
            for e in json.load(open(os.path.join(root, f))).get("elements", []):
                for v in e["from"] + e["to"]:
                    assert -16 <= v <= 32, (f, e)


if __name__ == "__main__":
    main()
    check()
    print("ok")
