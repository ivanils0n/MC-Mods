"""Gera texturas, modelo 3D, ícone de inventário, fundo da tela (menu "Cobre Polido") e definições de item.

Rode da pasta coppergolem-1.0.0:  python3 arte/gerar.py
Saída: src/main/resources/assets/coppergolem/... (vai no .jar e no zip do resource pack) e src/pack/pack.png.
O cajado é um item vanilla (cogumelo distorcido no palito) com componente item_model = coppergolem:wand.
Na mão: modelo 3D (mini golem num cabo fino). No inventário, chão e moldura: o ícone 2D wand_icon.png.
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
CG = os.path.join(ROOT, "src/main/resources/assets/coppergolem")


def hx(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


CU = ["#f6a468", "#ee8644", "#e0702f", "#c15a24", "#8a3a17"]  # claro -> escuro
OX = ["#8fe0c6", "#4fa58c", "#3d8a74"]
DK = "#2a120b"


def noisy(palette, weights, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), hx(rnd.choices(palette, weights)[0]))
    return im


def copper_tex(seed=1, ox=0.0):
    if ox:
        return noisy(OX, [3, 4, 2], seed)
    return noisy(CU[:4], [2, 5, 5, 2], seed)


def wood_tex():
    return noisy(["#5b3620", "#4a2a18", "#3a1f10", "#6a4228"], [3, 4, 2, 1], 9)


def light_tex():
    return noisy(["#fbc08a", "#f6a468", "#ffd2a3"], [3, 4, 2], 11)


def face_tex():
    im = copper_tex(5)
    for x in (4, 10):  # olhos pequenos nas pontas da área mapeada no rosto (uv 3,2 -> 13,6.5)
        for y in (3, 4):
            im.putpixel((x, y), hx("#3b1214"))
    return im


def icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = lambda x, y, c: im.putpixel((x, y), hx(c))
    for k in range(8):  # cabo fino em diagonal
        p(2 + k, 13 - k, CU[2])
        p(3 + k, 13 - k, CU[3])
    for x, y in ((1, 14), (2, 14), (1, 13), (2, 13), (1, 15), (2, 15), (0, 14), (0, 13)):
        p(x, y, CU[1] if (x + y) % 2 else CU[0])  # pomo em relevo
    for y in range(6, 9):  # corpo
        for x in range(9, 14):
            p(x, y, CU[3] if (x + y) % 2 else CU[2])
    for y in range(1, 6):  # cabeça
        for x in range(8, 15):
            p(x, y, CU[1] if (x + y) % 3 else CU[0])
    for x in range(8, 15):
        p(x, 1, CU[0])
    for x in (9, 12):
        p(x, 3, DK); p(x + 1, 3, DK)
    p(11, 4, CU[4]); p(11, 5, CU[4])
    p(11, 0, OX[0]); p(11, 1, OX[1])  # para-raios oxidado
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):  # contorno escuro
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3]:
                        out.putpixel((x, y), hx(CU[4], 255)); break
    out.alpha_composite(im)
    return out


def model(tilt=True):
    DY = -5.8 if tilt else 2  # pegada no cabo, perto do pomo: o cabo fica onde o cabo de uma espada do jogo fica na mão

    def box(a, b, tex, **over):
        faces = {f: {"texture": "#" + over.get(f, tex)} for f in ("north", "south", "east", "west", "up", "down")}
        if "uv_south" in over:
            faces["south"]["uv"] = over["uv_south"]
        el = {"from": [a[0], a[1] + DY, a[2]], "to": [b[0], b[1] + DY, b[2]], "faces": faces}
        if tilt:
            el["rotation"] = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}
        return el
    els = [
        box([6.5, 0, 6.5], [9.5, 3, 9.5], "cu"),             # pomo
        box([7, 3, 7], [9, 4, 9], "cu"),                     # pescoço do pomo
        box([7.25, 4, 7.25], [8.75, 10, 8.75], "wood"),      # cabo fino escuro
        box([6.5, 10, 6.5], [9.5, 11, 9.5], "cu"),           # colar
        box([6, 11, 6.5], [10, 15, 9.5], "cu"),              # corpo
        box([10, 12, 7], [11.5, 15, 9], "cu"),               # braço
        box([5, 15, 5.5], [11, 19.5, 10.5], "cu", up="light", south="face", uv_south=[3, 2, 13, 6.5]),  # cabeça com rosto
        box([7.25, 15.75, 10.5], [8.75, 17.5, 12], "cu"),    # nariz
        box([7.25, 19.5, 7.25], [8.75, 21.5, 8.75], "cu"),   # haste da cabeça
        box([7.5, 21.5, 7.5], [8.5, 22.5, 8.5], "ox"),       # ponta oxidada
    ]
    # Mesmos valores das ferramentas do jogo (item/handheld): com o cajado inclinado como o sprite de uma espada, ele
    # fica na mão do mesmo jeito: cabo na mão, cabeça à frente, rosto para o jogador.
    disp = {
        "gui": {"rotation": [10, -20, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    }
    if not tilt:
        # 1ª pessoa: a pose [0,-90,160] vira o item de cabeça para baixo (rosto para a câmera). Em vez de mexer na pose
        # (o rosto viraria para trás), gira a geometria 180° no plano x-y: cabeça embaixo, que a pose desvira.
        for e in els:
            (fx, fy, fz), (tx, ty, tz) = e["from"], e["to"]
            e["from"], e["to"] = [16 - tx, 16 - ty, fz], [16 - fx, 16 - fy, tz]
            f = e["faces"]
            f["up"], f["down"] = f["down"], f["up"]
            for face in f.values():
                face["rotation"] = 180

        disp = {
            # conferido no print do CI (src/gametest): reto, inteiro na tela, pomo junto da mão
            "firstperson_righthand": {"rotation": [0, -90, 175], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "firstperson_lefthand": {"rotation": [0, 90, -175], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
        }
    return {"gui_light": "front", "textures": {"particle": "coppergolem:item/wand", "cu": "coppergolem:item/wand",
            "face": "coppergolem:item/wand_face", "ox": "coppergolem:item/wand_ox", "wood": "coppergolem:item/wand_wood", "light": "coppergolem:item/wand_light"}, "elements": els, "display": disp}


def gui(rows=3):
    """176x(114+18*rows): baú de N fileiras. Slots em (7+18c, 17+18r), inventário em 30+18*rows e hotbar 58 abaixo."""
    H = 114 + 18 * rows
    inv = 30 + 18 * rows - 1 + 1
    im = Image.new("RGBA", (176, H))
    rnd = random.Random(7)
    for y in range(H):
        for x in range(176):
            im.putpixel((x, y), hx(rnd.choices(["#b8603f", "#a95537", "#c06a47"], [3, 3, 2])[0]))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 175, H - 1], outline=hx(CU[4]))
    d.line([1, 1, 174, 1], fill=hx(CU[0])); d.line([1, 1, 1, H - 2], fill=hx(CU[0]))
    d.line([1, H - 2, 174, H - 2], fill=hx(CU[4])); d.line([174, 1, 174, H - 2], fill=hx(CU[4]))
    for p in ((0, 0), (175, 0), (0, H - 1), (175, H - 1)):
        im.putpixel(p, (0, 0, 0, 0))

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hx("#4a2216"))
        d.line([x, y, x + 16, y], fill=hx("#2a120b")); d.line([x, y, x, y + 16], fill=hx("#2a120b"))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(CU[1])); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(CU[1]))
    for r in range(rows):
        for c in range(9):
            slot(7 + 18 * c, 17 + 18 * r)
    for r in range(3):
        for c in range(9):
            slot(7 + 18 * c, inv + 18 * r)
    for c in range(9):
        slot(7 + 18 * c, inv + 58)
    d.rectangle([5, inv - 12, 75, inv - 1], fill=hx("#d98a62"), outline=hx(CU[3]))  # placa atrás de "Inventário"
    d.line([6, 15, 169, 15], fill=hx(CU[3]))
    return im


OUT = "#2a120b"


def outlined(im):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3]:
                        out.putpixel((x, y), hx(OUT)); break
    out.alpha_composite(im)
    return out


def paint(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), hx(pal[ch]))
    return im


PAL = {"a": CU[0], "b": CU[1], "c": CU[2], "d": CU[3], "e": CU[4], "t": OX[0], "u": OX[1], "w": "#fff1d6", "r": "#e0452d", "q": "#9c2b1b", "g": "#ffe08a", "h": "#d9a36f"}
CHEST = ["..............", "..............", "..............", "..............", "..............", "..............",
         "...aaaaaaaaaa.", "...bbbbbbbbbb.", "...bbbbhhbbbb.", "...cccchhcccc.", "...cccccccccc.", "...dddddddddd.", "...dddddddddd.", ".............."]


def icons():
    chest = [r.ljust(16, ".") for r in CHEST] + ["." * 16, "." * 16]
    out = {}
    # recarga: barra de cobre com brilho turquesa
    ing = ["................", "................", "................", "......aaaaaaa...", ".....abbbbbbc...", "....abbbbbbcc...", "...abbbbbbccd...", "...aaaaaaaccd...",
           "...cccccccddd...", "...ccccccddd....", "...dddddddd.....", "................", "................", "................", "................", "................"]
    im = paint(ing, PAL)
    for x, y in ((11, 1), (11, 2), (11, 3), (10, 2), (12, 2)):
        im.putpixel((x, y), hx(OX[0]))
    out["btn_refill"] = outlined(im)
    # origem: baú com seta turquesa saindo para cima
    im = paint(chest, PAL)
    for x, y in ((7, 1), (6, 2), (7, 2), (8, 2), (5, 3), (7, 3), (9, 3), (7, 4), (7, 5)):
        im.putpixel((x, y), hx(OX[0] if (x, y) != (7, 1) else "#ffffff"))
    out["btn_source"] = outlined(im)
    # guardar: baú com seta dourada entrando de cima
    im = paint(chest, PAL)
    for x, y in ((7, 1), (7, 2), (7, 3), (5, 3), (9, 3), (6, 4), (8, 4), (7, 5)):
        im.putpixel((x, y), hx("#ffe08a"))
    out["btn_dest"] = outlined(im)
    # ver: olho
    eye = ["................", "................", "................", "......aaaa......", "....aabbbbaa....", "...abbwwwwbba...", "..abbwwttuwbba..", "..abbwwtuuwbba..",
           "...abbwwwwbba...", "....aabbbbaa....", "......aaaa......", "................", "................", "................", "................", "................"]
    out["btn_see"] = outlined(paint(eye, PAL))
    # desfazer: X vermelho
    x = ["................", "................", "..rr........rr..", "..rrr......rrr..", "...rrr....rrr...", "....rrr..rrr....", ".....rrrrrr.....", "......rrrr......",
         ".....rrrrrr.....", "....rrr..rrr....", "...rrr....rrr...", "..rrr......rrr..", "..rr........rr..", "................", "................", "................"]
    out["btn_clear"] = outlined(paint(x, {"r": "#e0452d"}))
    # vazio: mais apagado
    plus = ["................"] * 5 + ["......dd........".replace("dd", "dd")] 
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for yy in range(4, 12):
        for xx in (7, 8):
            im.putpixel((xx, yy), hx(CU[3]))
    for xx in range(4, 12):
        for yy in (7, 8):
            im.putpixel((xx, yy), hx(CU[3]))
    out["btn_add"] = im
    return out


def save(obj, rel):
    path = os.path.join(CG, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(obj, Image.Image):
        obj.save(path)
    else:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=1); f.write("\n")


def main():
    save(copper_tex(1), "textures/item/wand.png")
    save(face_tex(), "textures/item/wand_face.png")
    save(copper_tex(2, 0.9), "textures/item/wand_ox.png")
    save(wood_tex(), "textures/item/wand_wood.png")
    save(light_tex(), "textures/item/wand_light.png")
    save(gui(3), "textures/gui/menu.png")
    save(gui(1), "textures/gui/menu_small.png")
    for name, img in icons().items():
        save(img, f"textures/item/{name}.png")
        save({"parent": "minecraft:item/generated", "textures": {"layer0": f"coppergolem:item/{name}"}}, f"models/item/{name}.json")
        save({"model": {"type": "minecraft:model", "model": f"coppergolem:item/{name}"}}, f"items/{name}.json")
    save(model(), "models/item/wand.json")
    save(model(False), "models/item/wand_fp.json")
    # 1ª pessoa: modelo reto; o resto (3ª pessoa, inventário, chão, moldura): o modelo inclinado
    fp = {"type": "minecraft:model", "model": "coppergolem:item/wand_fp"}
    save({"model": {"type": "minecraft:select", "property": "minecraft:display_context",
          "cases": [{"when": ["firstperson_righthand", "firstperson_lefthand"], "model": fp}],
          "fallback": {"type": "minecraft:model", "model": "coppergolem:item/wand"}}}, "items/wand.json")
    save({"providers": [
        {"type": "space", "advances": {"\uf000": -8, "\uf001": -177, "\uf002": 8}},
        {"type": "bitmap", "file": "coppergolem:gui/menu.png", "ascent": 13, "height": 168, "chars": ["\ue000"]},
        {"type": "bitmap", "file": "coppergolem:gui/menu_small.png", "ascent": 13, "height": 132, "chars": ["\ue001"]}]}, "font/gui.json")
    pack = os.path.join(ROOT, "src/pack")
    os.makedirs(pack, exist_ok=True)
    copper_tex(1).resize((64, 64), Image.NEAREST).save(os.path.join(pack, "pack.png"))


main()
