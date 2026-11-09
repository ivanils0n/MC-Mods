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
    os.makedirs(os.path.join(RES, "tpaltar/textures/item"), exist_ok=True)
    icon().save(os.path.join(RES, "tpaltar/textures/item/altar.png"))
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
    write_json(os.path.join(MOD, "altar_icon.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tpaltar:item/altar"}})

    # Só as peças marcadas com custom_model_data mudam; sem a marca (ou sem o pack) fica o visual vanilla.
    items = os.path.join(RES, "minecraft/items")
    write_json(os.path.join(items, "smooth_stone.json"), select(
        [("tpaltar_obelisk", "tpaltar:item/obelisk"), ("tpaltar_ring", "tpaltar:item/shards")], "minecraft:block/smooth_stone"))
    write_json(os.path.join(items, "ender_pearl.json"), select([("tpaltar_eye", "tpaltar:item/eye_idle")], "minecraft:item/ender_pearl"))
    write_json(os.path.join(items, "ender_eye.json"), select(
        [("tpaltar_eye", "tpaltar:item/eye_active"), ("tpaltar_altar", "tpaltar:item/altar_icon")], "minecraft:item/ender_eye"))

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
