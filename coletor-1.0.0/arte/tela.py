"""Tela do Coletor no estilo B (pedra, ferro e terra). python3 arte/tela.py [previa.png]
Grava no jogo o fundo (176x186, baú de 4 fileiras), a fonte da tela e os ícones dos botões; previa.png: fundo + itens de exemplo, ampliado."""
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

W, H = 176, 186
ROW4 = 71  # y da fileira de controle


def hx(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def stone(img, box, seed):
    """Tijolinhos de pedra, como a lateral do bloco."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            ry, row = (y - y0) % 6, (y - y0) // 6
            off = 0 if row % 2 == 0 else 6
            c = "#5a5a5a" if ry == 0 or (x - x0 + off) % 12 == 0 else rnd.choice(("#858585", "#7b7b7b", "#8f8f8f", "#808080"))
            img.putpixel((x, y), hx(c))


def iron_band(img, x0, y0, x1, y1):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=hx("#b8b8b8"))
    d.line([x0, y0, x1, y0], fill=hx("#dcdcdc"))
    d.line([x0, y1, x1, y1], fill=hx("#6e6e6e"))
    for x in range(x0 + 4, x1 - 2, 12):
        img.putpixel((x, y0 + (y1 - y0) // 2), hx("#eeeeee"))
        img.putpixel((x + 1, y0 + (y1 - y0) // 2 + 1), hx("#6e6e6e"))


def soil(img, box, seed):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = "#5a3c22" if (y - y0) % 4 < 2 else "#4a3020"
            img.putpixel((x, y), hx(c) if rnd.random() > 0.15 else hx("#634329"))
    for x in range(x0 + 3, x1 - 2, 7):  # brotinhos na borda de baixo
        for dx, dy, c in ((0, -2, "#6fbf3a"), (-1, -3, "#6fbf3a"), (1, -3, "#4e8f28"), (0, -1, "#4e8f28")):
            img.putpixel((x + dx, y1 + dy), hx(c))


def label_plate(img, x0, y0, x1, y1):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=hx("#c9c9c9"), outline=hx("#5a5a5a"))
    d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=hx("#ececec"))
    for x in (x0 + 2, x1 - 2):
        img.putpixel((x, y0 + (y1 - y0) // 2), hx("#7a7a7a"))


def slot(img, x, y, fill="#3d3d3d"):
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=hx(fill))
    d.line([x - 1, y - 1, x + 16, y - 1], fill=hx("#262626"))
    d.line([x - 1, y - 1, x - 1, y + 16], fill=hx("#262626"))
    d.line([x, y + 16, x + 16, y + 16], fill=hx("#a8a8a8"))
    d.line([x + 16, y, x + 16, y + 16], fill=hx("#a8a8a8"))


def fundo():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    stone(img, (0, 0, W, H), 3)
    d = ImageDraw.Draw(img)
    # moldura de ferro com rebites, cantos arredondados como as telas do jogo
    d.rectangle([0, 0, W - 1, H - 1], outline=hx("#1e1e1e"))
    d.rectangle([1, 1, W - 2, H - 2], outline=hx("#dcdcdc"))
    d.rectangle([2, 2, W - 3, H - 3], outline=hx("#8c8c8c"))
    for x, y in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        img.putpixel((x, y), (0, 0, 0, 0))
    for x, y in ((5, 5), (W - 6, 5), (5, H - 6), (W - 6, H - 6)):
        img.putpixel((x, y), hx("#eeeeee"))
        img.putpixel((x + 1, y + 1), hx("#4a4a4a"))
    # placas claras de ferro para os títulos (o texto "Inventário" do jogo é cinza escuro e não muda de cor)
    label_plate(img, 4, 4, W - 5, 14)
    # colheita: 3 fileiras
    for r in range(3):
        for c in range(9):
            slot(img, 8 + c * 18, 18 + r * 18)
    # fileira de controle sobre terra com brotos, com uma faixa de ferro embaixo
    soil(img, (3, ROW4, W - 3, ROW4 + 19), 9)
    iron_band(img, 3, ROW4 + 19, W - 4, ROW4 + 20)
    for c in range(9):
        if c in (1, 7):
            continue  # espaços vazios: só a terra
        slot(img, 8 + c * 18, ROW4 + 1, "#2e2e2e")
    # inventário do jogador
    label_plate(img, 4, 91, W - 5, 101)
    for r in range(3):
        for c in range(9):
            slot(img, 8 + c * 18, 104 + r * 18)
    for c in range(9):
        slot(img, 8 + c * 18, 162)
    return img


# ---------- ícones 16x16 dos itens de exemplo ----------

def icon(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch in pal:
                im.putpixel((x, y), hx(pal[ch]))
    return im


WHEAT = icon(["..........yY....", ".........yYy....", "........yYyY....", ".......yYyY.....", "......yYyY......", "......YyY.......",
              ".....gYy........", "....gg..........", "...gg...........", "..gg............", ".gg.............", "................"],
             {"y": "#e8cc62", "Y": "#c9a43a", "g": "#7a8a2a"})
SEEDS = icon(["................", "......g.........", "....g...g.......", "......gG..g.....", "...g..Gg........", ".....g...gG.....",
              "...gG..g........", "......Gg..g.....", "....g....G......", "................"], {"g": "#3f9a2a", "G": "#2a6a1a"})
CARROT = icon(["............gg..", "...........gGg..", "..........gGg...", ".........ooG....", "........oOo.....", ".......oOo......",
               "......oOo.......", ".....oOo........", "....oOo.........", "...oO...........", "..o............."],
              {"o": "#ff8e1a", "O": "#d06a0a", "g": "#4e8f28", "G": "#6fbf3a"})
MEAL = icon(["................", "......ww........", ".....wWWw.......", "....wWwwWw......", "...wWwwwwWw.....", "...wwwwwwww.....",
             "...wWwwwwWw.....", "....wWWWWw......", ".....wwww.......", "................"], {"w": "#f0f0e6", "W": "#c8c8b4"})

DIG = {"0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "3": ["111", "001", "111", "001", "111"],
       "5": ["111", "100", "111", "001", "111"], "7": ["111", "001", "010", "010", "010"], "9": ["111", "101", "111", "001", "111"]}


def plate(num, on):
    """Botão de área: plaquinha de ferro com o lado da área (3, 5, ...); a escolhida acende em verde."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 2, 14, 13], fill=hx("#5fae34" if on else "#a8a8a8"), outline=hx("#2a4a1a" if on else "#4a4a4a"))
    d.line([2, 3, 13, 3], fill=hx("#9be36a" if on else "#dcdcdc"))
    for x, y in ((2, 4), (13, 4), (2, 12), (13, 12)):
        im.putpixel((x, y), hx("#e8e8e8" if not on else "#cff5b0"))
    w = len(num) * 4 - 1
    x0 = (16 - w) // 2
    for k, ch in enumerate(num):
        for j, line in enumerate(DIG[ch]):
            for i, c in enumerate(line):
                if c == "1":
                    im.putpixel((x0 + k * 4 + i, 6 + j), hx("#ffffff" if on else "#2e2e2e"))
    return im


def status_icon():
    im = WHEAT.copy()
    for x, y in ((10, 12), (11, 13), (12, 12), (13, 11), (14, 10)):  # visto verde
        im.putpixel((x, y), hx("#5fe03a"))
        im.putpixel((x, y + 1), hx("#2a6a1a"))
    return im


def toggle_icon(on):
    im = MEAL.copy()
    d = ImageDraw.Draw(im)
    d.rectangle([10, 11, 14, 14], fill=hx("#5fe03a" if on else "#e03a3a"), outline=hx("#1e1e1e"))
    return im


REDSTONE = icon(["................", "......r.........", "....r.rR..r.....", "...rRrRrr.......", "..r.RrRRr.r.....", "....rRrRr.......",
                 "...r.rRr..r.....", ".....r.r........", "................"], {"r": "#ff2a1a", "R": "#a80c00"})


def full_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 3, 13, 14], fill=hx("#8f6838"), outline=hx("#3a2716"))
    d.line([2, 6, 13, 6], fill=hx("#5c3e22"))
    d.line([2, 11, 13, 11], fill=hx("#5c3e22"))
    for i in range(5):  # X vermelho
        im.putpixel((5 + i, 5 + i), hx("#e03a3a"))
        im.putpixel((9 - i, 5 + i), hx("#e03a3a"))
    return im


def icones():
    # "esperando crescer" e a farinha de osso usam os itens do jogo (seguem o resource pack do jogador)
    out = {"status_working": status_icon(), "status_paused": REDSTONE, "status_full": full_icon()}
    for n in (3, 5, 7, 9, 11):
        out["area_%d" % n] = plate(str(n), False)
        out["area_%d_on" % n] = plate(str(n), True)
    return out


def gravar_jogo(base, bg):
    """Fundo, fonte da tela e os ícones dos botões (cada um com seu item_model coletor:<nome>)."""
    import json
    a = os.path.join(base, "src/main/resources/assets/coletor")
    for sub in ("textures/gui", "textures/item", "models/item", "items", "font"):
        os.makedirs(os.path.join(a, sub), exist_ok=True)
    bg.save(os.path.join(a, "textures/gui/coletor.png"))

    def js(data, path):
        with open(os.path.join(a, path), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=1, ensure_ascii=True)
            f.write("\n")

    # U+F000 volta 8 px (do x do título até o canto), U+E000 é o fundo inteiro, U+F001 desfaz a largura, U+F002 volta ao x 8
    js({"providers": [
        {"type": "space", "advances": {chr(0xF000): -8, chr(0xF001): -(W + 1), chr(0xF002): 8}},
        {"type": "bitmap", "file": "coletor:gui/coletor.png", "ascent": 13, "height": H, "chars": [chr(0xE000)]},
    ]}, "font/gui.json")
    for name, im in icones().items():
        im.save(os.path.join(a, "textures/item", name + ".png"))
        js({"parent": "minecraft:item/generated", "textures": {"layer0": "coletor:item/" + name}}, "models/item/" + name + ".json")
        js({"model": {"type": "minecraft:model", "model": "coletor:item/" + name}}, "items/" + name + ".json")


def fonte(n):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, n)
    return ImageFont.load_default()


def previa(bg, scale=4):
    img = bg.copy()

    def put(ic, c, y, n=None):
        img.alpha_composite(ic, (8 + c * 18, y))
        return (8 + c * 18, y, n)

    counts = []
    for c, (ic, n) in enumerate(((WHEAT, 64), (WHEAT, 64), (WHEAT, 23), (SEEDS, 41), (CARROT, 37), (MEAL, 12))):
        counts.append(put(ic, c, 18, n))
    counts.append(put(CARROT, 0, 36, 8))
    put(status_icon(), 0, ROW4 + 1)
    for i, n in enumerate(("3", "5", "7", "9", "11")):
        put(plate(n, n == "9"), 2 + i, ROW4 + 1)
    put(MEAL, 8, ROW4 + 1)
    counts.append(put(SEEDS, 2, 162, 5))
    big = img.resize((W * scale, H * scale), Image.NEAREST)
    d = ImageDraw.Draw(big)
    f = fonte(7 * scale)
    d.text((8 * scale, 5 * scale), "Coletor", font=f, fill="#303030")
    d.text((8 * scale, 92 * scale), "Inventário", font=f, fill="#404040")
    for x, y, n in counts:
        if n and n > 1:
            t = str(n)
            d.text(((x + 17) * scale, (y + 17) * scale), t, font=f, fill="#3f3f3f", anchor="rd")
            d.text(((x + 16) * scale, (y + 16) * scale), t, font=f, fill="white", anchor="rd")
    return big


if __name__ == "__main__":
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    bg = fundo()
    gravar_jogo(base, bg)
    if len(sys.argv) > 1:
        previa(bg).save(sys.argv[1])
