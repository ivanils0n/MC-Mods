"""Prévia da tela do Autômato (baú de 2 fileiras): python3 arte/tela.py saida.png"""
import random
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from gerar import hx  # noqa: E402

W, H = 176, 150
PICK = ["....DDDDDD......", "...DHHHHLHDD....", "....DDDDHHLHD...", "........DWHHD...", ".......DWDDHLD..", "......DWD..DHD..",
        ".....DWD...DHD..", "....DWD.....DHD.", "...DWD......DD..", "..DWD...........", ".DWD............", ".DD............."]


def bricks(img, box, seed, base=("#8A8A8A", "#7F7F7F", "#959595")):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            ry = (y - y0) % 6
            off = 0 if ((y - y0) // 6) % 2 == 0 else 6
            c = "#5E5E5E" if ry == 0 or (x - x0 + off) % 12 == 0 else rnd.choice(base)
            img.putpixel((x, y), hx(c))


def slot(img, x, y, dark="#2B2622", light="#B9B2A6", fill="#4A423B"):
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=hx(fill))
    d.line([x - 1, y - 1, x + 16, y - 1], fill=hx(dark))
    d.line([x - 1, y - 1, x - 1, y + 16], fill=hx(dark))
    d.line([x, y + 16, x + 16, y + 16], fill=hx(light))
    d.line([x + 16, y, x + 16, y + 16], fill=hx(light))


def ghost(img, x, y, rows):
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch != ".":
                img.putpixel((x + i, y + j + 2), hx("#5C534A"))


def sprite(img, x, y, rows, pal):
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch != ".":
                img.putpixel((x + i, y + j + 2), hx(pal[ch]))


def button(img, x, y, on):
    """Plaquinha de carvalho-escuro com rebites de ferro; a escolhida tem moldura de brasa e fica afundada."""
    d = ImageDraw.Draw(img)
    rnd = random.Random(x)
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=hx("#1A1410"))
    frame = "#E0601A" if on else "#4A3220"
    d.rectangle([x, y, x + 15, y + 15], fill=hx(frame))
    if on:
        d.line([x, y, x + 15, y], fill=hx("#FFB040"))
        d.line([x, y, x, y + 15], fill=hx("#FFB040"))
    oy = 0
    for yy in range(y + 1, y + 15):
        for xx in range(x + 1, x + 15):
            img.putpixel((xx, yy), hx(rnd.choice(["#4A3020", "#42291A", "#523624"] if not on else ["#3B2416", "#42291A"])))
    for yy in (y + 5 + oy, y + 10 + oy):
        d.line([x + 1, yy, x + 14, yy], fill=hx("#2E1C10"))
    if not on:
        d.line([x + 1, y + 1, x + 14, y + 1], fill=hx("#6E4E32"))
        d.line([x + 1, y + 14, x + 14, y + 14], fill=hx("#24160C"))
    for cx, cy in ((x + 2, y + 2), (x + 13, y + 2), (x + 2, y + 13), (x + 13, y + 13)):
        img.putpixel((cx, cy), hx("#C4C8CC" if not on else "#FFD27A"))


def draw_background():
    """Fundo da tela no jogo: espaços vazios (picareta apagada) e a fileira de alcance só com os rebaixos."""
    return draw(preview=False)


def draw(preview=True):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bricks(img, (0, 0, W, H), 1)
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, W - 4, H - 4], fill=hx("#6B4A2A"))  # painel de carvalho
    for y in range(3, H - 3, 4):
        d.line([3, y, W - 4, y], fill=hx("#5E4224"))
    d.rectangle([4, 14, W - 5, 56], fill=hx("#3A2E26"))  # rebaixo das fileiras do bloco
    for i in range(9):
        slot(img, 8 + i * 18, 18)
    tools = [None, None, None, None, None, None, None, None, None]
    if preview:
        sprite(img, 8, 18, PICK, {"D": "#1E3A3A", "H": "#4FE0D0", "L": "#B8FFF4", "W": "#7A5230"})
    for i in range(1 if preview else 0, 9):
        ghost(img, 8 + i * 18, 18, PICK)
    # fileira do alcance
    for i in (9, 10):
        x = 8 + (i - 9) * 18
        bricks(img, (x - 1, 35, x + 17, 53), 3 + i, ("#7A7A7A", "#727272"))
    for i in (16, 17):  # pote de XP e botão Recolher (itens por cima)
        ImageDraw.Draw(img).rectangle([8 + (i - 9) * 18 - 1, 35, 8 + (i - 9) * 18 + 16, 52], fill=hx("#1A1410"))
    for k in range(5):
        if preview:
            button(img, 8 + (2 + k) * 18, 36, k == 1)
        else:
            ImageDraw.Draw(img).rectangle([8 + (2 + k) * 18 - 1, 35, 8 + (2 + k) * 18 + 16, 52], fill=hx("#1A1410"))
    for row in range(3):
        for col in range(9):
            slot(img, 8 + col * 18, 67 + row * 18, fill="#8B8B8B", dark="#373737", light="#FFFFFF")
    for col in range(9):
        slot(img, 8 + col * 18, 125, fill="#8B8B8B", dark="#373737", light="#FFFFFF")
    d.rectangle([4, 63, W - 5, 64], fill=hx("#5E4224"))
    return img


def main(path):
    s = 5
    big = draw().resize((W * s, H * s), Image.NEAREST)
    out = Image.new("RGBA", (W * s + 520, H * s + 40), hx("#1E1F24"))
    out.alpha_composite(big, (20, 20))
    d = ImageDraw.Draw(out)
    f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
    sm = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
    note = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 24)
    d.text((20 + 8 * s, 20 + 4 * s), "Autômato", fill=hx("#FFE2B0"), font=f)
    for k in range(5):
        x = 20 + (8 + (2 + k) * 18) * s
        on = k == 1
        cx, cy = x + 8 * s, 20 + 36 * s + 8 * s
        d.text((cx + 3, cy + 3), str(k + 1), fill=hx("#1A1410"), font=sm, anchor="mm")
        d.text((cx, cy), str(k + 1), fill=hx("#FFC060" if on else "#D8C8A8"), font=sm, anchor="mm")
    d.text((20 + 8 * s, 20 + 56 * s), "Inventário", fill=hx("#FFE2B0"), font=note)
    tx = W * s + 50
    lines = [("Fileira de cima", True), ("9 espaços só para", False), ("ferramentas e armas.", False), ("Vazios mostram uma", False),
             ("picareta apagada.", False), ("", False), ("Fileira de baixo", True), ("Alcance da quebra:", False),
             ("placas de 1 a 5.", False), ("A com brasa é a", False), ("escolhida (aqui: 2).", False), ("Espada: sempre 3.", False),
             ("", False), ("Sem o resource pack:", True), ("tela de baú comum,", False), ("botões viram flechas", False), ("(1 a 5 na quantidade).", False)]
    y = 40
    for text, bold in lines:
        d.text((tx, y), text, fill=hx("#FFFFFF" if bold else "#BBBBBB"), font=sm if bold else note)
        y += 38 if bold else 32
    out.save(path)


if __name__ == "__main__":
    main(sys.argv[1])
