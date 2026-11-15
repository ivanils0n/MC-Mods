"""Texturas do Coletor (16x16). python3 arte/gerar.py [A|B|C] [pasta]
Sem argumentos: gera a opção escolhida (ESCOLHIDA) em src/main/resources/assets/coletor/textures/block/."""
import os
import random
import sys

from PIL import Image

ESCOLHIDA = "B"


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def jitter(c, rnd, amt=10):
    d = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


def img():
    return Image.new("RGBA", (16, 16))


def planks(rnd, light, dark, seam, vertical=False):
    """Tábuas de 4 px com emenda escura e veio leve."""
    im = img()
    px = im.load()
    for y in range(16):
        for x in range(16):
            a, b = (x, y) if vertical else (y, x)
            c = light if (a // 4) % 2 == 0 else dark
            if a % 4 == 3:
                c = seam
            elif rnd.random() < 0.18:
                c = jitter(c, rnd, 8)
            px[x, y] = c
    # pontas das tábuas em lugares diferentes
    for row in range(4):
        cut = rnd.randint(3, 12)
        for k in range(3):
            a = row * 4 + k
            x, y = (a, cut) if vertical else (cut, a)
            px[x, y] = seam
    return im


def frame(im, outer, inner, w=2):
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 0:
                px[x, y] = outer
            elif d < w:
                px[x, y] = inner
    return im


def soil_with_sprouts(rnd, soil, soil2, leaf, leaf2, crop=None):
    im = img()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = soil if (y % 4) < 2 else soil2
            if rnd.random() < 0.15:
                px[x, y] = jitter(px[x, y], rnd, 6)
    for row in (3, 7, 11):
        for col in (3, 7, 11):
            # brotinho em V
            px[col, row + 1] = leaf2
            px[col, row] = leaf
            px[col - 1, row - 1] = leaf
            px[col + 1, row - 1] = leaf2
            if crop:
                px[col, row - 1] = crop
    return im


# ---------- A: Celeiro (carvalho, feixe de trigo) ----------

def opcao_a():
    rnd = random.Random(7)
    oak_l, oak_d, seam = hexc("a8814f"), hexc("9a7444"), hexc("6b4a2b")
    frame_o, frame_i = hexc("4a3220"), hexc("6b4a2b")
    wheat, wheat_d, wheat_s = hexc("e8cc62"), hexc("c9a43a"), hexc("8a6a1e")

    side = frame(planks(rnd, oak_l, oak_d, seam), frame_o, frame_i)
    px = side.load()
    # janelinha escura no meio com o feixe de trigo
    for y in range(4, 13):
        for x in range(4, 12):
            px[x, y] = hexc("3a2716")
    bundle = [
        "..W.W.W.",
        ".WwW.WwW",
        "..WwWwW.",
        "...wWw..",
        "...rrr..",
        "...wWw..",
        "..w.W.w.",
        ".w..W..w",
        "........",
    ]
    for j, line in enumerate(bundle):
        for i, ch in enumerate(line):
            c = {"W": wheat, "w": wheat_d, "r": hexc("b5302a")}.get(ch)
            if c:
                px[4 + i, 4 + j] = c
    # cravos de ferro nos cantos
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = hexc("9a9a9a")

    top = frame(soil_with_sprouts(rnd, hexc("5a3c22"), hexc("4a3020"), hexc("6fbf3a"), hexc("4e8f28"), wheat), frame_o, frame_i)
    bottom = frame(planks(rnd, oak_l, oak_d, seam, vertical=True), frame_o, frame_i)
    return {"side": side, "top": top, "bottom": bottom}


# ---------- B: Ferro e enxada (pedra, faixas de ferro, lâmina) ----------

def opcao_b():
    rnd = random.Random(11)
    st, st2, st3 = hexc("7d7d7d"), hexc("6a6a6a"), hexc("575757")
    iron, iron_d, iron_l = hexc("b8b8b8"), hexc("8c8c8c"), hexc("dcdcdc")
    side = img()
    px = side.load()
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            px[x, y] = st if r < 0.5 else st2 if r < 0.85 else st3
    # tijolinhos de pedra
    for y in range(16):
        for x in range(16):
            if y % 5 == 4 or (x + (4 if (y // 5) % 2 else 0)) % 8 == 7:
                px[x, y] = hexc("4a4a4a")
    # faixas de ferro em cima e embaixo, com rebites
    for x in range(16):
        for y in (1, 2, 13, 14):
            px[x, y] = iron if y in (1, 13) else iron_d
        px[x, 0] = px[x, 15] = hexc("3a3a3a")
    for x in (2, 7, 12):
        px[x, 1] = px[x, 13] = iron_l
    # enxada atravessada: cabo de madeira na diagonal e cabeça de ferro no alto
    for i in range(9):
        px[4 + i, 12 - i] = hexc("7a5530")
        px[4 + i, 13 - i] = hexc("5c3e22")
    for x in range(6, 14):
        px[x, 4] = iron_l if x == 6 else iron
        px[x, 5] = iron_d
    px[6, 6] = px[7, 6] = iron
    px[6, 7] = iron_d
    top = img()
    tp = top.load()
    soil = soil_with_sprouts(rnd, hexc("5a3c22"), hexc("4a3020"), hexc("6fbf3a"), hexc("4e8f28")).load()
    for y in range(16):
        for x in range(16):
            tp[x, y] = soil[x, y]
            if x % 5 == 0 or y % 5 == 0:  # grade de ferro
                tp[x, y] = iron_d if (x + y) % 2 else iron
            if min(x, y, 15 - x, 15 - y) == 0:
                tp[x, y] = hexc("3a3a3a")
    bottom = img()
    bp = bottom.load()
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            bp[x, y] = st2 if r < 0.6 else st3
    return {"side": side, "top": top, "bottom": bottom}


# ---------- C: Cesto de vime (trançado, colheita saindo pelo topo) ----------

def opcao_c():
    rnd = random.Random(5)
    w1, w2, w3 = hexc("d9b27a"), hexc("bf9358"), hexc("8f6838")
    side = img()
    px = side.load()
    for y in range(16):
        for x in range(16):
            # trama: tiras horizontais de 2 px passando por cima/baixo das varetas verticais
            over = ((x // 2) + (y // 2)) % 2 == 0
            c = w1 if over else w2
            if x % 4 == 0:
                c = w3  # vareta
            if y % 2 == 1 and not over:
                c = w3
            if rnd.random() < 0.12:
                c = jitter(c, rnd, 8)
            px[x, y] = c
    # borda superior grossa (aro do cesto) e base
    for x in range(16):
        px[x, 0] = hexc("6b4a2b")
        px[x, 1] = hexc("a8814f") if x % 3 else hexc("8a6a3a")
        px[x, 2] = hexc("8a6a3a")
        px[x, 15] = hexc("6b4a2b")
    # alça de corda no meio
    for x in range(6, 10):
        px[x, 5] = hexc("e8dcc0")
    px[5, 6] = px[10, 6] = hexc("e8dcc0")
    top = img()
    tp = top.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            tp[x, y] = hexc("6b4a2b") if d == 0 else hexc("a8814f") if d == 1 else hexc("4a3020")
    # colheita amontoada: trigo, cenoura, beterraba
    crops = [(hexc("e8cc62"), hexc("c9a43a")), (hexc("ff8e1a"), hexc("d06a0a")), (hexc("a8203a"), hexc("7a1428"))]
    for _ in range(46):
        x, y = rnd.randint(3, 12), rnd.randint(3, 12)
        a, b = crops[rnd.randrange(3)]
        tp[x, y] = a
        tp[min(14, x + 1), y] = b
    for x, y in ((4, 4), (11, 5), (6, 11), (12, 12), (8, 7)):
        tp[x, y] = hexc("6fbf3a")
        tp[x, y - 1] = hexc("4e8f28")
    bottom = img()
    bp = bottom.load()
    for y in range(16):
        for x in range(16):
            bp[x, y] = w2 if (x + y) % 3 else w3
    return {"side": side, "top": top, "bottom": bottom}


OPCOES = {"A": opcao_a, "B": opcao_b, "C": opcao_c}


def salvar(op, pasta):
    os.makedirs(pasta, exist_ok=True)
    for nome, im in OPCOES[op]().items():
        im.save(os.path.join(pasta, nome + ".png"))


if __name__ == "__main__":
    op = sys.argv[1] if len(sys.argv) > 1 else ESCOLHIDA
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    pasta = sys.argv[2] if len(sys.argv) > 2 else os.path.join(base, "src/main/resources/assets/coletor/textures/block")
    salvar(op, pasta)
    print("ok", op, pasta)
