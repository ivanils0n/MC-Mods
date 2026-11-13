"""Gera texturas, modelos e definições de item do Autômato.

Rode da pasta automato-1.0.0:  python3 arte/gerar.py [A|B|C] [--preview arquivo.png]
Saída: src/main/resources/assets/... (vai no .jar e no zip do resource pack) e src/pack/pack.png.

Nada aqui registra bloco ou item novo: o Autômato é um ejetor. assets/minecraft/items/dispenser.json troca o modelo só
do ejetor marcado (item com custom_data, ou o visual com custom_model_data "automato_idle"/"automato_on"); qualquer
outro ejetor continua com o modelo do jogo.
"""
import json
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src/main/resources/assets")
AU = os.path.join(RES, "automato")


def hx(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


def rect(img, x0, y0, x1, y1, c):  # inclusivo
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), hx(c))


def px(img, pts, c):
    for p in pts:
        img.putpixel(p, hx(c))


def noise(img, pal, seed, box=(0, 0, 16, 16)):
    rnd = random.Random(seed)
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            img.putpixel((x, y), hx(rnd.choice(pal)))


def bevel(img, light, dark, box=(0, 0, 15, 15)):
    x0, y0, x1, y1 = box
    for i in range(x0, x1 + 1):
        img.putpixel((i, y0), hx(light))
        img.putpixel((i, y1), hx(dark))
    for i in range(y0, y1 + 1):
        img.putpixel((x0, i), hx(light))
        img.putpixel((x1, i), hx(dark))


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 255))


# ---------- A: Ferro Rebitado (chapas de ferro, janela com garra mecânica e lâmpada de redstone) ----------

def a_plate(seed):
    im = new()
    noise(im, ["#C9CDD1", "#C2C6CA", "#BBBFC3", "#CFD3D6"], seed)
    bevel(im, "#E4E7EA", "#7C8186")
    rect(im, 1, 7, 14, 7, "#8E9398")  # emenda entre as chapas
    rect(im, 1, 8, 14, 8, "#DADDE0")
    for x, y in [(2, 2), (13, 2), (2, 12), (13, 12), (2, 5), (13, 5), (2, 10), (13, 10)]:
        px(im, [(x, y)], "#F2F4F5")
        px(im, [(x + 1, y + 1)], "#6B7075")
    return im


def a_front(on, frame=0):
    im = a_plate(11)
    rect(im, 3, 4, 12, 13, "#2A2D31")  # janela
    bevel(im, "#4A4E53", "#16181B", (3, 4, 12, 13))
    # garra: braço vertical e três dedos
    rect(im, 7, 5, 8, 9, "#9FA5AB")
    rect(im, 7, 5, 7, 9, "#C7CCD1")
    rect(im, 5, 10, 10, 10, "#8A9096")
    px(im, [(5, 11), (5, 12), (7, 11), (8, 11), (10, 11), (10, 12)], "#B4BABF")
    px(im, [(7, 12), (8, 12)], "#6E747A")
    # lâmpada
    lamp = ["#5A1612", "#3F0F0C"] if not on else (["#FF4A2E", "#C21E0E"] if frame == 0 else ["#FF8A5C", "#E8301A"])
    rect(im, 7, 1, 8, 2, lamp[0])
    px(im, [(8, 2)], lamp[1])
    if on:
        px(im, [(6, 1), (9, 1), (6, 2), (9, 2)], "#B85A4A")
    return im


def a_back():
    im = a_plate(12)
    rect(im, 6, 10, 9, 13, "#3A3D41")
    rect(im, 7, 11, 8, 12, "#A0201A")  # entrada de redstone
    px(im, [(7, 11)], "#E0402C")
    return im


# ---------- B: Cobre com Engrenagem (cobre com pátina, engrenagem que gira quando liga) ----------

def b_plate(seed):
    im = new()
    noise(im, ["#C46A43", "#B85F3B", "#D07650", "#AD5735"], seed)
    bevel(im, "#E59A73", "#7A3A22")
    rnd = random.Random(seed + 7)
    for _ in range(9):
        x, y = rnd.randint(1, 14), rnd.randint(1, 14)
        px(im, [(x, y)], rnd.choice(["#4FA58A", "#62B89A", "#3E8A72"]))
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(im, [(x, y)], "#F0B48E")
    return im


GEAR = [
    "....##....",
    "..#.##.#..",
    ".########.",
    "..##..##..",
    "###....###",
    "###....###",
    "..##..##..",
    ".########.",
    "..#.##.#..",
    "....##....",
]
GEAR2 = [
    "..#....#..",
    ".###..###.",
    "..######..",
    ".###..###.",
    "..#....#..",
    "..#....#..",
    ".###..###.",
    "..######..",
    ".###..###.",
    "..#....#..",
]


def b_front(on, frame=0):
    im = b_plate(21)
    rect(im, 2, 2, 13, 13, "#5B2E1C")
    bevel(im, "#3A1C10", "#E08A60", (2, 2, 13, 13))
    g = GEAR if frame == 0 else GEAR2
    col = "#F2C35B" if on else "#9C8A6A"
    for y, row in enumerate(g):
        for x, ch in enumerate(row):
            if ch == "#":
                im.putpixel((3 + x, 3 + y), hx(col))
    rect(im, 7, 7, 8, 8, "#FF5A2A" if on else "#4A2014")
    return im


def b_back():
    im = b_plate(22)
    for y in (4, 7, 10):
        rect(im, 4, y, 11, y, "#5B2E1C")  # grade de ventilação
        rect(im, 4, y + 1, 11, y + 1, "#E08A60")
    return im


# ---------- C: Pedra-negra e Redstone (pedra-negra polida, visor que varre quando liga) ----------

def c_plate(seed, lit=False):
    im = new()
    noise(im, ["#2E2A30", "#353137", "#29252B", "#3B373D"], seed)
    bevel(im, "#4C474F", "#151216")
    line = "#C0281C" if lit else "#6A1A14"
    for p in [(1, 4), (2, 4), (3, 4), (3, 5), (3, 6), (4, 6), (5, 6), (12, 9), (13, 9), (14, 9), (12, 10), (12, 11), (11, 11), (10, 11)]:
        px(im, [p], line)
    return im


def c_front(on, frame=0):
    im = c_plate(31, on)
    rect(im, 2, 6, 13, 9, "#0E0C0F")  # visor
    bevel(im, "#151216", "#5A5560", (2, 6, 13, 9))
    if on:
        for x in range(3, 13):
            px(im, [(x, 7), (x, 8)], "#7A120C")
        c = 4 + frame * 3
        for x, col in [(c - 1, "#B0301C"), (c, "#FF5A3A"), (c + 1, "#FF5A3A"), (c + 2, "#B0301C")]:
            if 3 <= x <= 12:
                px(im, [(x, 7), (x, 8)], col)
    else:
        for x in range(3, 13):
            px(im, [(x, 7)], "#3A1210")
    rect(im, 6, 11, 9, 13, "#1A171B")  # boca/abertura da ferramenta
    rect(im, 7, 12, 8, 12, "#4A4550")
    return im


def c_back():
    im = c_plate(32)
    rect(im, 6, 6, 9, 9, "#1A171B")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


# ---------- D: Madeira e Ferro (tábuas com cintas de ferro, braço de pistão) ----------

def d_plate(seed):
    im = new()
    noise(im, ["#A0784A", "#9A7244", "#A87F50", "#93693D"], seed)
    for y in (3, 7, 11, 15):
        rect(im, 0, y, 15, y, "#6E4E2A")
    rect(im, 0, 0, 15, 1, "#8C9196"); rect(im, 0, 14, 15, 15, "#8C9196")
    rect(im, 0, 1, 15, 1, "#5F6469"); rect(im, 0, 14, 15, 14, "#C4C8CC")
    for x in (2, 13):
        px(im, [(x, 0), (x, 15)], "#E2E5E8")
    return im


def d_front(on, frame=0):
    im = d_plate(41)
    rect(im, 3, 3, 12, 12, "#3B2A18")
    bevel(im, "#2A1D10", "#B08858", (3, 3, 12, 12))
    ext = 0 if not on else (1 if frame == 0 else 2)
    rect(im, 6, 4, 9, 11, "#7A7F84")  # cabeça do pistão
    rect(im, 6 - ext, 6 - ext, 9 + ext, 9 + ext, "#B9BEC3" if on else "#9AA0A6")
    rect(im, 7, 7, 8, 8, "#E8402A" if on else "#5A1A14")
    return im


def d_back():
    im = d_plate(42)
    rect(im, 6, 6, 9, 9, "#3B2A18")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


# ---------- E: Robô de Ardósia (rosto de robô com dois olhos que acendem em ciano) ----------

def e_plate(seed):
    im = new()
    noise(im, ["#4A4A50", "#44444A", "#505056", "#3E3E44"], seed)
    for y in (0, 8):
        rect(im, 0, y, 15, y, "#2C2C31")
    for x in (0, 8):
        rect(im, x, 0, x, 15, "#2C2C31")
    bevel(im, "#66666D", "#1E1E22")
    return im


def e_front(on, frame=0):
    im = new()
    noise(im, ["#4A4A50", "#44444A", "#505056"], 51)
    bevel(im, "#66666D", "#1E1E22")
    eye = ["#2A3A40", "#1A2428"] if not on else (["#7FF6FF", "#2EC8D8"] if frame == 0 else ["#2EC8D8", "#1A8C99"])
    for x0 in (3, 9):
        rect(im, x0, 4, x0 + 3, 7, "#1A1A1E")
        rect(im, x0 + 1, 5, x0 + 2, 6, eye[0])
        px(im, [(x0 + 2, 6)], eye[1])
    rect(im, 4, 10, 11, 12, "#1A1A1E")  # grade da boca
    for x in (5, 7, 9):
        rect(im, x, 10, x, 12, "#5A5A62" if not on else "#2EC8D8")
    return im


def e_back():
    im = e_plate(52)
    rect(im, 5, 5, 10, 10, "#1A1A1E")
    for y in (6, 8):
        rect(im, 6, y, 9, y, "#5A5A62")
    return im


# ---------- F: Quartzo e Ouro (quartzo liso com moldura e lente de ouro) ----------

def f_plate(seed):
    im = new()
    noise(im, ["#ECE6DD", "#E6DFD5", "#F1EBE3", "#E0D9CE"], seed)
    bevel(im, "#E8C24A", "#9C7A1A")
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px(im, [(x, y)], "#FFE27A")
    return im


def f_front(on, frame=0):
    im = f_plate(61)
    ring = [(x, y) for y in range(3, 13) for x in range(3, 13) if 12 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 25]
    px(im, ring, "#D4A82A")
    inner = [(x, y) for y in range(3, 13) for x in range(3, 13) if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 12]
    lens = "#2A3348" if not on else ("#6FE0FF" if frame == 0 else "#B8F2FF")
    px(im, inner, lens)
    px(im, [(6, 6), (6, 7)], "#FFFFFF" if on else "#4A5570")
    return im


def f_back():
    im = f_plate(62)
    rect(im, 6, 6, 9, 9, "#D4A82A")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


# ---------- G: Pedra e Carvalho (tijolos de pedra, portinhola de madeira com brasa) ----------

def g_plate(seed):
    im = new()
    noise(im, ["#8A8A8A", "#7F7F7F", "#959595", "#777777"], seed)
    for y in (0, 4, 8, 12):
        rect(im, 0, y, 15, y, "#5E5E5E")
    for y0, xs in ((1, (5, 13)), (5, (1, 9)), (9, (5, 13)), (13, (1, 9))):
        for x in xs:
            rect(im, x, y0, x, y0 + 2, "#5E5E5E")
    return im


def ember(im, box, on, frame):
    x0, y0, x1, y1 = box
    rect(im, x0, y0, x1, y1, "#1C1612")
    if on:
        cols = ["#FF9A2A", "#E0601A"] if frame == 0 else ["#FFC04A", "#C84A12"]
        rect(im, x0 + 1, y1 - 1, x1 - 1, y1, cols[1])
        rect(im, x0 + 2, y1 - 2, x1 - 2, y1 - 1, cols[0])


def g_front(on, frame=0):
    im = g_plate(71)
    rect(im, 3, 3, 12, 13, "#6B4A2A")  # moldura de carvalho
    bevel(im, "#9C7448", "#4A3218", (3, 3, 12, 13))
    ember(im, (5, 5, 10, 11), on, frame)
    rect(im, 5, 8, 10, 8, "#6B4A2A")  # travessa da portinhola
    return im


def g_back():
    im = g_plate(72)
    rect(im, 6, 6, 9, 9, "#6B4A2A")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


# ---------- H: Tronco e Cobre (casca de carvalho, abertura com aro de cobre) ----------

def h_plate(seed):
    im = new()
    noise(im, ["#6B5232", "#5E4728", "#74593A", "#55401F"], seed)
    for x in (2, 6, 11, 14):
        rect(im, x, 0, x, 15, "#45331C")
    return im


def h_top(seed):
    im = new()
    noise(im, ["#B08A55", "#A8824D"], seed)
    for r, c in ((7, "#8E6A3A"), (5, "#B08A55"), (3, "#8E6A3A")):
        pts = [(x, y) for y in range(16) for x in range(16) if abs(max(abs(x - 7.5), abs(y - 7.5)) - r) < 0.6]
        px(im, pts, c)
    bevel(im, "#6B5232", "#55401F")
    return im


def h_front(on, frame=0):
    im = h_plate(81)
    rect(im, 3, 3, 12, 12, "#C46A43")  # aro de cobre
    bevel(im, "#E59A73", "#7A3A22", (3, 3, 12, 12))
    ember(im, (5, 5, 10, 10), on, frame)
    px(im, [(4, 4), (11, 4), (4, 11), (11, 11)], "#F0B48E")
    return im


def h_back():
    im = h_plate(82)
    rect(im, 6, 6, 9, 9, "#C46A43")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


# ---------- I: Ferraria (carvalho-escuro com ferro, martelo na frente e fornalha que acende) ----------

def i_plate(seed):
    im = new()
    noise(im, ["#4A3020", "#42291A", "#523624", "#3B2416"], seed)
    for y in (5, 10):
        rect(im, 0, y, 15, y, "#2E1C10")
    rect(im, 0, 0, 15, 0, "#5A5E62"); rect(im, 0, 15, 15, 15, "#5A5E62")
    rect(im, 0, 0, 0, 15, "#5A5E62"); rect(im, 15, 0, 15, 15, "#5A5E62")
    for x, y in [(0, 0), (15, 0), (0, 15), (15, 15)]:
        px(im, [(x, y)], "#A0A5AA")
    return im


def i_front(on, frame=0):
    im = i_plate(91)
    # martelo
    rect(im, 4, 2, 9, 4, "#8C9196")
    rect(im, 4, 2, 9, 2, "#C4C8CC")
    rect(im, 6, 5, 7, 8, "#7A5230")
    # boca da fornalha
    rect(im, 3, 10, 12, 14, "#5A5E62")
    ember(im, (4, 11, 11, 14), on, frame)
    return im


def i_back():
    im = i_plate(92)
    rect(im, 6, 6, 9, 9, "#2E1C10")
    rect(im, 7, 7, 8, 8, "#A0201A")
    return im


OPTIONS = {
    "A": ("Ferro Rebitado", a_front, a_plate, a_back, 2),
    "B": ("Cobre com Engrenagem", b_front, b_plate, b_back, 2),
    "C": ("Pedra-negra e Redstone", c_front, lambda s: c_plate(s), c_back, 3),
    "D": ("Madeira e Ferro", d_front, d_plate, d_back, 2),
    "E": ("Robô de Ardósia", e_front, e_plate, e_back, 2),
    "F": ("Quartzo e Ouro", f_front, f_plate, f_back, 2),
    "G": ("Pedra e Carvalho", g_front, g_plate, g_back, 2),
    "H": ("Tronco e Cobre", h_front, h_plate, h_back, 2),
    "I": ("Ferraria", i_front, i_plate, i_back, 2),
}
TOPS = {"H": h_top}


def textures(opt):
    name, front, plate, back, frames = OPTIONS[opt]
    on = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        on.paste(front(True, f), (0, 16 * f))
    return {"front": front(False), "front_on": on, "side": plate(5), "top": TOPS.get(opt, plate)(6), "back": back()}


def model(front):
    faces = {"north": "#front", "south": "#back", "east": "#side", "west": "#side", "up": "#top", "down": "#top"}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "automato:block/side", "front": "automato:block/" + front, "back": "automato:block/back",
                     "side": "automato:block/side", "top": "automato:block/top"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                      "faces": {k: {"texture": v, "cullface": k} for k, v in faces.items()}}],
    }


# ---------- G em 3D: portinhola funda com moldura de carvalho saltada ----------

def g_oak(seed):
    im = new()
    noise(im, ["#8F6A3E", "#86623A", "#987246", "#7C5832"], seed)
    for y in (3, 7, 11, 15):
        rect(im, 0, y, 15, y, "#5E4224")
    return im


def g_inner(seed):
    im = new()
    noise(im, ["#4A4A4A", "#444444", "#525252", "#3E3E3E"], seed)
    return im


def g_wall(on, frame=0):
    """Fundo da portinhola (a 3 pixels de profundidade). A abertura é x 5..10, linhas 5..10 da textura."""
    im = new()
    noise(im, ["#1C1612", "#211A15", "#18120E"], 73)
    if on:
        cols = ["#FFB040", "#E0601A", "#8A2A0C"] if frame == 0 else ["#FFD060", "#C84A12", "#7A220A"]
        rect(im, 5, 10, 10, 10, cols[2])
        rect(im, 6, 9, 9, 10, cols[1])
        px(im, [(7, 9), (8, 8), (7, 8)] if frame == 0 else [(8, 9), (7, 8), (8, 7)], cols[0])
    else:
        px(im, [(6, 10), (8, 10), (9, 10)], "#3A2A20")
    return im


def g3d_textures():
    on = Image.new("RGBA", (16, 32))
    on.paste(g_wall(True, 0), (0, 0))
    on.paste(g_wall(True, 1), (0, 16))
    return {"side": g_plate(5), "top": g_plate(6), "back": g_back(), "oak": g_oak(7), "inner": g_inner(8),
            "wall": g_wall(False), "wall_on": on}


def g3d_elements(wall):
    """Frente no norte (z=0). Corpo com um buraco de 6x6 (x 5..11, y 5..11) e 3 de fundo; moldura de carvalho saltada."""
    def el(a, b, faces):
        return {"from": a, "to": b, "faces": faces}

    def f(tex, cull=None):
        d = {"texture": tex}
        if cull:
            d["cullface"] = cull
        return d

    side = lambda c: f("#side", c)
    out = [el([0, 0, 3], [16, 16, 16], {"north": f(wall), "south": f("#back", "south"), "east": side("east"),
                                         "west": side("west"), "up": f("#top", "up"), "down": f("#top", "down")})]
    for a, b, inner in (([0, 0, 0], [16, 5, 3], "up"), ([0, 11, 0], [16, 16, 3], "down"),
                        ([0, 5, 0], [5, 11, 3], "east"), ([11, 5, 0], [16, 11, 3], "west")):
        faces = {"north": side("north")}
        for d in ("east", "west", "up", "down"):
            touches_edge = (d == "east" and b[0] == 16) or (d == "west" and a[0] == 0) or (d == "up" and b[1] == 16) or (d == "down" and a[1] == 0)
            if d == inner:
                faces[d] = f("#inner")
            elif touches_edge:
                faces[d] = f("#top", d) if d in ("up", "down") else side(d)
        out.append(el(a, b, faces))
    oak = lambda: {d: f("#oak") for d in ("north", "east", "west", "up", "down")}
    for a, b in (([3, 11, -1], [13, 13, 0.5]), ([3, 3, -1], [13, 5, 0.5]), ([3, 5, -1], [5, 11, 0.5]), ([11, 5, -1], [13, 11, 0.5]),
                 ([5, 7.5, -0.5], [11, 8.5, 1])):
        out.append(el(a, b, oak()))
    return out


def g3d_model(on):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "automato:block/side", "side": "automato:block/side", "top": "automato:block/top",
                         "back": "automato:block/back", "oak": "automato:block/oak", "inner": "automato:block/inner",
                         "wall": "automato:block/" + ("wall_on" if on else "wall")},
            "elements": g3d_elements("#wall")}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def generate(opt):
    if opt == "G3":
        return generate_g3d()
    tex = textures(opt)
    os.makedirs(os.path.join(AU, "textures/block"), exist_ok=True)
    for k, im in tex.items():
        im.save(os.path.join(AU, "textures/block", k + ".png"))
    write_json(os.path.join(AU, "textures/block/front_on.png.mcmeta"), {"animation": {"frametime": 6}})
    write_json(os.path.join(AU, "models/item/machine.json"), model("front"))
    write_json(os.path.join(AU, "models/item/machine_on.json"), model("front_on"))
    write_dispenser_items()
    iso(tex["front"], tex["side"], tex["top"], 8).resize((64, 64), Image.NEAREST).save(os.path.join(ROOT, "src/pack/pack.png"))


def write_dispenser_items():
    m = lambda name: {"type": "minecraft:model", "model": "automato:item/" + name}
    write_json(os.path.join(RES, "minecraft/items/dispenser.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:custom_data",
        "on_true": m("machine"),
        "on_false": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                     "cases": [{"when": "automato_idle", "model": m("machine")}, {"when": "automato_on", "model": m("machine_on")}],
                     "fallback": {"type": "minecraft:model", "model": "minecraft:block/dispenser"}}}})


def generate_g3d():
    d = os.path.join(AU, "textures/block")
    for f in os.listdir(d) if os.path.isdir(d) else []:
        os.remove(os.path.join(d, f))
    os.makedirs(d, exist_ok=True)
    tex = g3d_textures()
    for k, im in tex.items():
        im.save(os.path.join(d, k + ".png"))
    write_json(os.path.join(d, "wall_on.png.mcmeta"), {"animation": {"frametime": 6}})
    write_json(os.path.join(AU, "models/item/machine.json"), g3d_model(False))
    write_json(os.path.join(AU, "models/item/machine_on.json"), g3d_model(True))
    write_dispenser_items()
    t = textures("G")
    iso(t["front"], t["side"], t["top"], 8).resize((64, 64), Image.NEAREST).save(os.path.join(ROOT, "src/pack/pack.png"))


# ---------- prévia ----------

def iso(front, side, top, s):
    """Cubo isométrico: frente à esquerda, lado à direita, topo em cima (escala s por pixel)."""
    w = 16 * s
    h = w // 2
    big = lambda im: im.crop((0, 0, 16, 16)).resize((w, w), Image.NEAREST)

    def shade(im, k):
        r, g, b, a = im.split()
        return Image.merge("RGBA", [c.point(lambda v: int(v * k)) for c in (r, g, b)] + [a])

    out = Image.new("RGBA", (2 * w, 2 * w), (0, 0, 0, 0))
    size = (2 * w, 2 * w)
    # coeficientes = inversa: (x, y) da textura para cada (X, Y) da saída
    out.alpha_composite(big(top).transform(size, Image.AFFINE, (0.5, 1, -h, 0.5, -1, h), Image.NEAREST))
    out.alpha_composite(shade(big(front), 0.9).transform(size, Image.AFFINE, (1, 0, 0, -0.5, 1, -h), Image.NEAREST))
    out.alpha_composite(shade(big(side), 0.7).transform(size, Image.AFFINE, (1, 0, -w, 0.5, 1, -3 * h), Image.NEAREST))
    return out


def preview(path, opts="ABC"):
    s = 8
    cell = 2 * 16 * s
    W, H = 3 * (2 * cell + 40) + 40, cell + 330
    out = Image.new("RGBA", (W, H), hx("#1E1F24"))
    d = ImageDraw.Draw(out)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
    except OSError:
        font = small = ImageFont.load_default()
    for i, opt in enumerate(opts):
        x0 = 40 + i * (2 * cell + 40)
        t = textures(opt)
        d.text((x0, 20), f"{opt} — {OPTIONS[opt][0]}", fill=hx("#FFFFFF"), font=font)
        out.alpha_composite(iso(t["front"], t["side"], t["top"], s), (x0, 80))
        out.alpha_composite(iso(t["front_on"], t["side"], t["top"], s), (x0 + cell, 80))
        d.text((x0 + 80, 80 + cell + 5), "desligado", fill=hx("#BBBBBB"), font=small)
        d.text((x0 + cell + 80, 80 + cell + 5), "ligado", fill=hx("#FFB060"), font=small)
        # texturas planas
        for j, k in enumerate(["front", "front_on", "side", "back"]):
            im = t[k].crop((0, 0, 16, 16)).resize((96, 96), Image.NEAREST)
            out.alpha_composite(im, (x0 + j * 110, 80 + cell + 50))
            d.text((x0 + j * 110, 80 + cell + 150), ["frente", "ligada", "lado", "trás"][j], fill=hx("#999999"), font=small)
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--preview" in args:
        preview(args[args.index("--preview") + 1], args[args.index("--preview") + 2] if len(args) > args.index("--preview") + 2 else "ABC")
    else:
        generate(args[0] if args else "G3")
