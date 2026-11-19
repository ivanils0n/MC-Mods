"""Texturas da Fornalha Industrial (tema Aço Industrial). python3 arte/gerar.py [previa.png]
Grava em src/main/resources/assets: blocos (casca e controlador por nível do núcleo), modelos, itens, fundo da tela,
fonte e ícones dos botões; e src/pack/pack.png. Com um argumento, salva também uma prévia ampliada."""
import json
import os
import random
import sys

from PIL import Image, ImageDraw

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(BASE, "src/main/resources/assets")

# Níveis: 0 = solta (sem estrutura), 1 ferro, 2 ouro, 3 diamante, 4 netherite.
# (placa clara, placa média, placa escura, borda clara, borda escura, detalhe claro, detalhe escuro)
NIVEIS = {
    0: ("#8f949a", "#868b91", "#7d8288", "#aeb2b7", "#4f5357", "#6a6e73", "#4f5357"),
    1: ("#8f949a", "#868b91", "#7d8288", "#aeb2b7", "#4f5357", "#e4e8ec", "#9aa0a6"),
    2: ("#8f949a", "#868b91", "#7d8288", "#aeb2b7", "#4f5357", "#f6d55c", "#b8862a"),
    3: ("#8f949a", "#868b91", "#7d8288", "#aeb2b7", "#4f5357", "#6ff0e6", "#2a9d96"),
    4: ("#4a464c", "#433f45", "#3b383d", "#6a6570", "#211f23", "#9b8ea3", "#5a4f62"),
}


def hx(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def casca(n):
    """Chapa de aço rebitada; a faixa do meio e os rebites ganham a cor do núcleo."""
    claro, medio, escuro, bc, be, dc, de = NIVEIS[n]
    rnd = random.Random(7)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = rnd.choice((claro, medio, escuro))
            if x == 0 or y == 0:
                c = bc
            if x == 15 or y == 15:
                c = be
            px[x, y] = hx(c)
    for x in range(1, 15):  # faixa no meio
        px[x, 7] = hx(dc)
        px[x, 8] = hx(de)
    for x, y in ((2, 2), (13, 2), (2, 12), (13, 12)):
        px[x, y] = hx(dc)
        px[x + 1, y + 1] = hx(be)
    return im


def brilho(t, y, y0, y1):
    """Brasa: amarelo em cima, laranja, vermelho embaixo; t muda a cada quadro da animação."""
    f = (y - y0) / max(1, y1 - y0)
    cores = ("#ffe066", "#ffb12e", "#ff8a1f", "#e5481c")
    i = min(3, int(f * 4 + (t % 2) * 0.5 * ((y + t) % 2)))
    return cores[i]


def controlador(n, ligado, t=0):
    im = casca(n)
    px = im.load()
    _, _, _, bc, be, dc, de = NIVEIS[n]
    rnd = random.Random(100 + t)
    for y in range(4, 13):
        for x in range(3, 13):
            borda = x in (3, 12) or y in (4, 12)
            if borda:
                c = dc if (y == 4 or x == 3) else de
            elif x in (6, 9):
                c = "#2a2c2f"  # grade
            elif ligado:
                c = brilho(t, y, 5, 11)
                if rnd.random() < 0.12:
                    c = "#fff3b0"
            else:
                c = "#1e1e1e" if (x + y) % 3 else "#2a2626"
            px[x, y] = hx(c)
    return im


def animado(n):
    frames = [controlador(n, True, t) for t in range(4)]
    out = Image.new("RGBA", (16, 64))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * 16))
    return out


def js(data, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, ensure_ascii=True)
        f.write("\n")


def blocos():
    """Texturas e modelos (cubo com a mesma textura nos 6 lados: só a frente do controlador aparece de fora)."""
    tex = os.path.join(A, "fornalha/textures/block")
    os.makedirs(tex, exist_ok=True)
    casca_modelos, ctrl_modelos = [], []
    for n in range(5):
        nome = "casca" if n == 0 else "casca_%d" % n
        casca(n).save(os.path.join(tex, nome + ".png"))
        casca_modelos.append(nome)
        nome = "ctrl" if n == 0 else "ctrl_%d" % n
        controlador(n, False).save(os.path.join(tex, nome + ".png"))
        ctrl_modelos.append(nome)
        if n > 0:
            nome = "ctrl_%d_on" % n
            animado(n).save(os.path.join(tex, nome + ".png"))
            js({"animation": {"frametime": 3, "interpolate": False}}, os.path.join(tex, nome + ".png.mcmeta"))
            ctrl_modelos.append(nome)
    for nome in casca_modelos + ctrl_modelos:
        js({"parent": "minecraft:block/cube_all", "textures": {"all": "fornalha:block/" + nome}},
           os.path.join(A, "fornalha/models/block", nome + ".json"))

    # O item do jogo (pedra lisa / alto-forno) escolhe o modelo pelo custom_model_data "fornalha_<nome>";
    # sem ele (ou sem o pack) fica o modelo vanilla.
    def itens(vanilla, nomes):
        js({"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": "fornalha_" + n, "model": {"type": "minecraft:model", "model": "fornalha:block/" + n}} for n in nomes],
            "fallback": {"type": "minecraft:model", "model": "minecraft:block/" + vanilla}}},
            os.path.join(A, "minecraft/items", vanilla + ".json"))

    itens("smooth_stone", casca_modelos)
    itens("blast_furnace", ctrl_modelos)


# ---------- tela (baú de 6 fileiras: 176x222) ----------

W, H = 176, 222


def slot(img, x, y, fill="#2b2e31"):
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=hx(fill))
    d.line([x - 1, y - 1, x + 16, y - 1], fill=hx("#1a1c1e"))
    d.line([x - 1, y - 1, x - 1, y + 16], fill=hx("#1a1c1e"))
    d.line([x, y + 16, x + 16, y + 16], fill=hx("#b4b8bd"))
    d.line([x + 16, y, x + 16, y + 16], fill=hx("#b4b8bd"))


def placa(img, x0, y0, x1, y1):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=hx("#c9cdd1"), outline=hx("#4f5357"))
    d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=hx("#eef0f2"))
    for x in (x0 + 2, x1 - 2):
        img.putpixel((x, y0 + (y1 - y0) // 2), hx("#7d8288"))


def cel(r, c):
    return 8 + c * 18, 18 + r * 18


def fundo():
    img = Image.new("RGBA", (W, H))
    t = casca(0)
    for y in range(0, H, 16):
        for x in range(0, W, 16):
            img.paste(t, (x, y))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], outline=hx("#1e1e1e"))
    d.rectangle([1, 1, W - 2, H - 2], outline=hx("#dcdcdc"))
    d.rectangle([2, 2, W - 3, H - 3], outline=hx("#6a6e73"))
    for x, y in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        img.putpixel((x, y), (0, 0, 0, 0))
    placa(img, 4, 4, W - 5, 15)
    # entrada 3x3 e saída 3x3
    for r in range(3):
        for c in (0, 1, 2, 6, 7, 8):
            slot(img, *cel(r, c))
    # trilho da barra de progresso (3 espaços no meio da fileira 2) e soquetes de chama e info
    x0, y0 = cel(1, 3)
    d.rectangle([x0 - 1, y0 + 4, x0 + 53, y0 + 11], fill=hx("#1a1c1e"), outline=hx("#b4b8bd"))
    d.polygon([(x0 + 54, y0 + 1), (x0 + 54, y0 + 14), (x0 + 59, y0 + 7)], fill=hx("#1a1c1e"))
    for r, c in ((0, 4), (2, 4)):
        slot(img, *cel(r, c), fill="#3a3d40")
    # tanque: 3 espaços em coluna, com moldura
    tx, ty = cel(3, 0)
    d.rectangle([tx - 2, ty - 2, tx + 17, ty + 53], fill=hx("#1a1c1e"), outline=hx("#b4b8bd"))
    for k in (17, 35):
        d.line([tx - 2, ty + k, tx + 1, ty + k], fill=hx("#b4b8bd"))
    slot(img, *cel(5, 1))  # combustível
    for r, c in ((3, 4), (5, 6), (5, 7)):  # XP, modo, ligar
        slot(img, *cel(r, c), fill="#3a3d40")
    # inventário do jogador
    placa(img, 4, 126, W - 5, 137)
    for r in range(3):
        for c in range(9):
            slot(img, 8 + c * 18, 140 + r * 18)
    for c in range(9):
        slot(img, 8 + c * 18, 198)
    return img


def icone():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def progresso(k):
    """Pedaço da barra (3 espaços lado a lado): k de 0 a 8 = 0 a 16 px preenchidos."""
    im = icone()
    d = ImageDraw.Draw(im)
    if k:
        d.rectangle([0, 5, 2 * k - 1, 10], fill=hx("#ff9a1f"))
        d.line([0, 5, 2 * k - 1, 5], fill=hx("#ffd36a"))
        d.line([0, 10, 2 * k - 1, 10], fill=hx("#c2410c"))
    return im


def tanque(k):
    """Pedaço do tanque de lava: k de 0 a 8 = 0 a 16 px de baixo para cima."""
    im = icone()
    d = ImageDraw.Draw(im)
    if k:
        top = 16 - 2 * k
        d.rectangle([1, top, 14, 15], fill=hx("#e8752a"))
        d.line([1, top, 14, top], fill=hx("#ffc23a"))
        for x in (3, 8, 12):
            if top + 3 <= 15:
                im.putpixel((x, min(15, top + 3)), hx("#ffb12e"))
    return im


def chama(cor1, cor2, apagada=False):
    im = icone()
    d = ImageDraw.Draw(im)
    if apagada:
        d.polygon([(4, 14), (8, 6), (12, 14)], fill=hx("#3a3a3a"), outline=hx("#5a5a5a"))
        return im
    d.polygon([(3, 14), (5, 7), (7, 10), (8, 2), (10, 9), (11, 6), (13, 14)], fill=hx(cor1))
    d.polygon([(6, 14), (8, 8), (10, 14)], fill=hx(cor2))
    return im


def marca(im, cor):
    d = ImageDraw.Draw(im)
    for i in range(5):
        im.putpixel((10 + i, 10 + i), hx(cor))
        im.putpixel((14 - i, 10 + i), hx(cor))
    return im


def energia(ligado):
    im = icone()
    d = ImageDraw.Draw(im)
    cor = "#5fe03a" if ligado else "#e03a3a"
    d.ellipse([2, 2, 13, 13], outline=hx(cor), width=2)
    d.rectangle([7, 0, 8, 7], fill=hx("#1e1e1e"))
    d.rectangle([7, 1, 8, 7], fill=hx(cor))
    return im


def icones():
    out = {}
    for k in range(9):
        out["prog_%d" % k] = progresso(k)
        out["tanque_%d" % k] = tanque(k)
    out["st_trabalhando"] = chama("#ff9a1f", "#ffe066")
    out["st_parado"] = chama(None, None, True)
    out["st_pausado"] = marca(chama(None, None, True), "#e03a3a")
    out["st_sem_combustivel"] = marca(chama(None, None, True), "#ffb12e")
    cheio = icone()
    ImageDraw.Draw(cheio).rectangle([2, 3, 13, 14], fill=hx("#7d8288"), outline=hx("#2b2e31"))
    out["st_cheio"] = marca(cheio, "#e03a3a")
    out["ligado"] = energia(True)
    out["desligado"] = energia(False)
    return out


def tela():
    a = os.path.join(A, "fornalha")
    bg = fundo()
    os.makedirs(os.path.join(a, "textures/gui"), exist_ok=True)
    bg.save(os.path.join(a, "textures/gui/fornalha.png"))
    # U+F000 volta 8 px, U+E000 é o fundo inteiro, U+F001 desfaz a largura, U+F002 volta ao x 8
    js({"providers": [
        {"type": "space", "advances": {chr(0xF000): -8, chr(0xF001): -(W + 1), chr(0xF002): 8}},
        {"type": "bitmap", "file": "fornalha:gui/fornalha.png", "ascent": 13, "height": H, "chars": [chr(0xE000)]},
    ]}, os.path.join(a, "font/gui.json"))
    os.makedirs(os.path.join(a, "textures/item"), exist_ok=True)
    for nome, im in icones().items():
        im.save(os.path.join(a, "textures/item", nome + ".png"))
        js({"parent": "minecraft:item/generated", "textures": {"layer0": "fornalha:item/" + nome}},
           os.path.join(a, "models/item", nome + ".json"))
        js({"model": {"type": "minecraft:model", "model": "fornalha:item/" + nome}}, os.path.join(a, "items", nome + ".json"))
    return bg


def pack_png():
    im = controlador(1, True, 1).resize((64, 64), Image.NEAREST)
    im.save(os.path.join(BASE, "src/pack/pack.png"))


def previa(bg, caminho):
    """Prévia: os 5 níveis da casca e do controlador lado a lado e a tela com itens de exemplo."""
    s = 8
    lin = Image.new("RGBA", (5 * 18 * s, 2 * 18 * s), (0, 0, 0, 0))
    for n in range(5):
        lin.paste(casca(n).resize((16 * s, 16 * s), Image.NEAREST), (n * 18 * s, 0))
        lin.paste(controlador(n, n > 0).resize((16 * s, 16 * s), Image.NEAREST), (n * 18 * s, 18 * s))
    img = bg.copy()
    ic = icones()
    for i, k in enumerate((8, 8, 3)):
        img.alpha_composite(ic["prog_%d" % k], cel(1, 3 + i))
    for i, k in enumerate((4, 8, 8)):
        img.alpha_composite(ic["tanque_%d" % k], cel(3 + i, 0))
    img.alpha_composite(ic["st_trabalhando"], cel(0, 4))
    img.alpha_composite(ic["ligado"], cel(5, 7))
    big = img.resize((W * 3, H * 3), Image.NEAREST)
    out = Image.new("RGBA", (max(big.width, lin.width), big.height + lin.height + 24), (40, 40, 40, 255))
    out.paste(lin, (0, 0))
    out.alpha_composite(big, (0, lin.height + 24))
    out.save(caminho)


if __name__ == "__main__":
    blocos()
    bg = tela()
    pack_png()
    if len(sys.argv) > 1:
        previa(bg, sys.argv[1])
    print("ok")
