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


CU = ["#f4a77d", "#e07e5c", "#c06a47", "#a1492f", "#6b2e1c"]  # claro -> escuro
OX = ["#8fe0c6", "#4fa58c", "#3d8a74"]
DK = "#2a120b"


def copper_tex(seed=1, ox=0.0):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rnd.choices(CU[:4], [1, 4, 4, 1])[0]
            if rnd.random() < ox:
                c = rnd.choice(OX)
            im.putpixel((x, y), hx(c))
    return im


def wood_tex():
    rnd = random.Random(9)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), hx(rnd.choices(["#4a2a18", "#5b3620", "#3a1f10"], [3, 3, 2])[0]))
    return im


def light_tex():
    rnd = random.Random(11)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), hx(rnd.choices(["#f8c3a0", "#f4a77d", "#fbd3b6"], [3, 4, 2])[0]))
    return im


def face_tex():
    im = copper_tex(5)
    d = ImageDraw.Draw(im)
    for x in (3, 11):
        d.rectangle([x, 5, x + 1, 7], fill=hx(DK))  # olhos
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


def model():
    def box(a, b, tex, only=None, up=None):
        faces = {f: {"texture": "#" + tex} for f in ("north", "south", "east", "west", "up", "down")}
        if up:
            faces["up"] = {"texture": "#" + up}
        if only:
            faces = {only: {"texture": "#" + tex}}
        return {"from": a, "to": b, "faces": faces}
    els = [
        box([6.5, 0, 6.5], [9.5, 3, 9.5], "cu"),             # pomo
        box([7, 3, 7], [9, 4, 9], "cu"),                     # pescoço do pomo
        box([7.25, 4, 7.25], [8.75, 10, 8.75], "wood"),      # cabo fino escuro
        box([6.5, 10, 6.5], [9.5, 11, 9.5], "cu"),           # colar
        box([6, 11, 6.5], [10, 15, 9.5], "cu"),              # corpo
        box([10, 12, 7], [11.5, 15, 9], "cu"),               # braço
        box([5, 15, 5.5], [11, 19.5, 10.5], "cu", up="light"),   # cabeça
        box([5, 15, 5.5], [11, 19.5, 10.5], "face", "south"),    # olhos (virados para quem segura)
        box([7.25, 15.75, 10.5], [8.75, 17.5, 12], "cu"),    # nariz
        box([7.25, 19.5, 7.25], [8.75, 21.5, 8.75], "cu"),   # haste da cabeça
        box([7.5, 21.5, 7.5], [8.5, 22.5, 8.5], "ox"),       # ponta oxidada
    ]
    disp = {
        "gui": {"rotation": [20, -35, 0], "translation": [0, -2, 0], "scale": [0.56, 0.56, 0.56]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [0, -90, -170], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, 170], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 160], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -160], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    }
    return {"gui_light": "front", "textures": {"particle": "coppergolem:item/wand", "cu": "coppergolem:item/wand",
            "face": "coppergolem:item/wand_face", "ox": "coppergolem:item/wand_ox", "wood": "coppergolem:item/wand_wood", "light": "coppergolem:item/wand_light"}, "elements": els, "display": disp}


def gui():
    """176x168: baú de 3 fileiras. Slots em (7+18c, 17+18r), inventário em y=84 e hotbar y=142."""
    im = Image.new("RGBA", (176, 168))
    rnd = random.Random(7)
    for y in range(168):
        for x in range(176):
            im.putpixel((x, y), hx(rnd.choices(["#b8603f", "#a95537", "#c06a47"], [3, 3, 2])[0]))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 175, 167], outline=hx(CU[4]))
    d.line([1, 1, 174, 1], fill=hx(CU[0])); d.line([1, 1, 1, 166], fill=hx(CU[0]))
    d.line([1, 166, 174, 166], fill=hx(CU[4])); d.line([174, 1, 174, 166], fill=hx(CU[4]))
    for p in ((0, 0), (175, 0), (0, 167), (175, 167)):
        im.putpixel(p, (0, 0, 0, 0))

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hx("#4a2216"))
        d.line([x, y, x + 16, y], fill=hx("#2a120b")); d.line([x, y, x, y + 16], fill=hx("#2a120b"))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(CU[1])); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(CU[1]))
    for r in range(3):
        for c in range(9):
            slot(7 + 18 * c, 17 + 18 * r)
    for r in range(3):
        for c in range(9):
            slot(7 + 18 * c, 84 + 18 * r)
    for c in range(9):
        slot(7 + 18 * c, 142)
    d.rectangle([5, 72, 75, 83], fill=hx("#d98a62"), outline=hx(CU[3]))  # placa atrás de "Inventário"
    d.line([6, 15, 169, 15], fill=hx(CU[3]))
    return im


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
    save(gui(), "textures/gui/menu.png")
    save(model(), "models/item/wand.json")
    # o mesmo modelo 3D em todo lugar (inventário, mão, chão, moldura)
    save({"model": {"type": "minecraft:model", "model": "coppergolem:item/wand"}}, "items/wand.json")
    save({"providers": [
        {"type": "space", "advances": {"": -8, "": -177, "": 0}},
        {"type": "bitmap", "file": "coppergolem:gui/menu.png", "ascent": 13, "height": 168, "chars": [""]}]}, "font/gui.json")
    pack = os.path.join(ROOT, "src/pack")
    os.makedirs(pack, exist_ok=True)
    copper_tex(1).resize((64, 64), Image.NEAREST).save(os.path.join(pack, "pack.png"))


main()
