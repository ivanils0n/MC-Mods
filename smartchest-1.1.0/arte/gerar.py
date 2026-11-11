"""Gera texturas, modelos, fonte da tela e definições de item do Baú Central.

Rode da pasta smartchest-1.1.0:  python3 arte/gerar.py
Saída: src/main/resources/assets/... (vai no .jar e no zip do resource pack) e src/pack/pack.png.

Visual escolhido: baú "Obsidiana do End" (opção A) e vinculador "Chave do End" (opção 4).
Nada aqui registra bloco ou item novo. Os itens do mod apontam (componente item_model) para definições próprias em
assets/smartchest/items, que nenhum outro resource pack substitui; os itens vanilla não são tocados, então os packs
do jogador continuam valendo para eles.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src/main/resources/assets")
SC = os.path.join(RES, "smartchest")

# Modelos do mod; SmartChest.java usa os mesmos nomes no componente item_model ("smartchest:<nome>").
# Abas (mesma ordem de Categories.KEYS no Java): cada uma tem o ícone normal e o "_on" (aba ativa, com moldura).
MODELS = ["chest", "chest_base", "chest_lid", "linker", "btn_prev", "btn_next", "btn_search", "btn_sort", "btn_info", "btn_deposit", "btn_links",
          "btn_confirm", "btn_more", "btn_clear", "btn_organize", "tab_filter", "crafting_table"]


def hx(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


def px(img, pts, c):
    for p in pts:
        img.putpixel(p, hx(c))


def rect(img, x0, y0, x1, y1, c):  # inclusivo
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), hx(c))


def noise(img, palette, seed, box, weights=None):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), hx(rnd.choices(palette, weights)[0]))


def grid(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), hx(pal[ch]))
    return im


def outline(im, c):
    w, h = im.size
    a = [[im.getpixel((x, y))[3] > 0 for x in range(w)] for y in range(h)]
    for y in range(h):
        for x in range(w):
            if not a[y][x] and any(0 <= y + dy < h and 0 <= x + dx < w and a[y + dy][x + dx]
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), hx(c))


def ring(im, cx, cy, rx, ry, c, th=1.0):
    for y in range(16):
        for x in range(16):
            d = math.hypot((x + .5 - cx) / rx, (y + .5 - cy) / ry)
            if abs(d - 1) * min(rx, ry) <= th / 2 + .25:
                im.putpixel((x, y), hx(c))


def pearl(im, cx, cy, r):
    for y in range(16):
        for x in range(16):
            if math.hypot(x + .5 - cx, y + .5 - cy) <= r:
                hl = math.hypot(x + .5 - (cx - r * .4), y + .5 - (cy - r * .4))
                im.putpixel((x, y), hx("#0b4d42" if hl > r * 1.2 else "#1b8c74" if hl > r * .6 else "#5fd4b8"))
    im.putpixel((int(cx - r * .45), int(cy - r * .45)), hx("#e6fff8"))


# ------------------------------------------------------------------ baú (opção A: Obsidiana do End)
OBS = ["#14101e", "#1a1428", "#1f1830", "#251c3a"]
OBS_W = [0.35, 0.3, 0.25, 0.1]


def bevel(im, light, dark):
    for i in range(16):
        im.putpixel((i, 0), hx(light)); im.putpixel((0, i), hx(light))
        im.putpixel((i, 15), hx(dark)); im.putpixel((15, i), hx(dark))


def eye(img, x, y):
    rect(img, x, y, x + 3, y + 3, "#2fa37a")
    px(img, [(x, y), (x + 3, y), (x, y + 3), (x + 3, y + 3)], "#1d6e52")
    px(img, [(x + 1, y), (x + 1, y + 1)], "#a6f5d0")
    px(img, [(x + 2, y + 1), (x + 2, y + 2), (x + 1, y + 2)], "#0a2a20")


def chest_faces():
    f, s, t = (Image.new("RGBA", (16, 16)) for _ in range(3))
    for i, im in enumerate((f, s, t)):
        noise(im, OBS, 10 + i, (0, 0, 16, 16), OBS_W)
        bevel(im, "#3a2b5c", "#0b0812")
    for im in (f, s):  # tampa / corpo
        for x in range(1, 15):
            im.putpixel((x, 5), hx("#0b0812")); im.putpixel((x, 4), hx("#3a2b5c"))
    rect(f, 5, 3, 10, 9, "#4b2f7a"); rect(f, 5, 9, 10, 9, "#2a1a48"); rect(f, 10, 3, 10, 9, "#2a1a48")
    eye(f, 6, 4)
    px(f, [(2, 11), (13, 2), (12, 13), (3, 2)], "#8d5fe0")
    px(s, [(3, 9), (11, 11), (7, 2), (12, 7)], "#6a4aa8")
    for d in range(4):  # runa em losango no topo
        px(t, [(7 - d, 4 + d), (8 + d, 4 + d), (7 - d, 11 - d), (8 + d, 11 - d)], "#6a4aa8")
    rect(t, 7, 7, 8, 8, "#2fa37a"); px(t, [(7, 7)], "#a6f5d0")
    return f, s, t


# ------------------------------------------------------------------ mesa de trabalho ligada ao Baú Central
WOOD = ["#9c7a4c", "#8a6a40", "#a8865a", "#7c5e38"]

def table_faces():
    top, front, side = (Image.new("RGBA", (16, 16)) for _ in range(3))
    # topo: grade 3x3 de obsidiana do End com espaços iguais (4x4) e linhas roxas de 1 pixel
    for gx in range(3):
        for gy in range(3):
            noise(top, OBS, 80 + gx * 3 + gy, (1 + gx * 5, 1 + gy * 5, 5 + gx * 5, 5 + gy * 5), OBS_W)
    for i in range(16):
        for k in (0, 5, 10, 15):
            top.putpixel((i, k), hx("#6a4aa8")); top.putpixel((k, i), hx("#6a4aa8"))
    rect(top, 7, 7, 8, 8, "#2fa37a"); px(top, [(7, 7)], "#a6f5d0"); px(top, [(8, 8)], "#1d6e52")
    px(top, [(0, 0), (15, 0), (0, 15), (15, 15), (5, 5), (10, 5), (5, 10), (10, 10)], "#8d5fe0")
    # laterais: tábuas de madeira, faixa de obsidiana no alto e cantos roxos
    for im, seed in ((front, 90), (side, 91)):
        noise(im, WOOD, seed, (0, 0, 16, 16), [.35, .3, .2, .15])
        for y in (5, 10):
            for x in range(16):
                im.putpixel((x, y), hx("#6b5030"))
        noise(im, OBS, seed + 5, (0, 0, 16, 4), OBS_W)
        for x in range(16):
            im.putpixel((x, 3), hx("#6a4aa8")); im.putpixel((x, 0), hx("#3a2b5c"))
        for y in range(16):
            for x in (0, 15):
                im.putpixel((x, y), hx("#251c3a"))
        for y in range(4, 16, 3):
            im.putpixel((0, y), hx("#4b2f7a")); im.putpixel((15, y), hx("#4b2f7a"))
        rect(im, 0, 15, 15, 15, "#14101e")
    # frente: olho do End no centro, como o do Baú Central
    rect(front, 5, 6, 10, 12, "#4b2f7a"); rect(front, 5, 12, 10, 12, "#2a1a48"); rect(front, 10, 6, 10, 12, "#2a1a48")
    eye(front, 6, 7)
    # lado: martelo e serrote estilizados em ferro escuro
    rect(side, 3, 6, 3, 13, "#5a4228"); rect(side, 2, 5, 5, 6, "#8c8c9c"); px(side, [(2, 5)], "#c8c8d8")
    for i in range(6):
        rect(side, 8 + i // 2, 6 + i, 12, 6 + i, "#8c8c9c")
    px(side, [(9, 7), (10, 9), (11, 11)], "#c8c8d8"); rect(side, 12, 5, 13, 6, "#5a4228")
    return front, side, top


# ------------------------------------------------------------------ vinculador (opção 4: Chave do End)
def rod(im, x0, y0, n):
    for i in range(n):
        x, y = x0 + i, y0 - i
        im.putpixel((x, y), hx("#ffd34e")); im.putpixel((x + 1, y), hx("#e07b0e"))
        if i % 3 == 1:
            im.putpixel((x, y), hx("#fff3a0"))


def linker():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rod(im, 2, 13, 7)
    ring(im, 11.5, 4.5, 3.3, 3.3, "#4b2f7a", 1.4)
    pearl(im, 11.5, 4.5, 2.0)
    px(im, [(9, 2), (10, 1)], "#8d5fe0")
    px(im, [(4, 13), (5, 13), (5, 14), (6, 12), (7, 12)], "#4b2f7a")  # dentes
    outline(im, "#14081f")
    return im


# ------------------------------------------------------------------ ícones dos botões
def icon(rows, c="#e3d8ff", acc="#5fd4b8", hl="#e6fff8"):
    return grid(rows, {"#": c, "+": acc, "h": hl})


def lupa():
    """Lupa: aro dourado, olho do End na lente e cabo roxo."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if ((x - 6.5) ** 2 + (y - 6.5) ** 2) ** .5 <= 2.6:
                im.putpixel((x, y), hx("#1b8c74" if x + y > 13 else "#2fa37a"))
    ring(im, 6.5, 6.5, 3.6, 3.6, "#ffd34e", 1.2)
    rect(im, 6, 6, 7, 7, "#0b2a22")
    px(im, [(5, 5)], "#d8fff0")
    for i in range(3):
        x = y = 9 + i
        px(im, [(x, y), (x + 1, y), (x, y + 1)], "#6a4aa8")
    px(im, [(10, 11), (11, 12), (12, 12)], "#3d2e60")
    outline(im, "#0b0816")
    return im


def organize():
    """Organizar: itens misturados entram no funil e caem separados em 3 baús, cada um com a cor de uma categoria."""
    G, R, Y = "#5fd4b8", "#e52b1a", "#f5d36a"
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px(im, [(4, 2), (8, 3), (11, 2)], R); px(im, [(6, 2), (10, 3)], G); px(im, [(5, 3), (9, 2)], Y)
    rect(im, 3, 4, 12, 4, "#c9b8ff"); rect(im, 4, 5, 11, 5, "#9d8cc9"); rect(im, 6, 6, 9, 6, "#9d8cc9"); rect(im, 7, 7, 8, 7, "#9d8cc9")
    for i, c in enumerate((G, R, Y)):
        x = 2 + i * 4
        rect(im, x, 9, x + 2, 12, "#4b2f7a"); rect(im, x, 10, x + 2, 10, "#2e2348"); px(im, [(x + 1, 11)], c)
    outline(im, "#0b0816")
    return im


def buttons():
    prev = icon(["................", "................", "................", "........#.......", ".......##.......",
                 "......##........", ".....##.........", "....##########..", "....##########..", ".....##.........",
                 "......##........", ".......##.......", "........#.......", "................", "................", "................"])
    search = lupa()
    sort = icon(["................", "................", "................", "..##########....", "..##########....",
                 "................", "..#######....+..", "..#######...+++.", "............+++.", "..####.....+++++",
                 "..####.......+..", ".............+..", "..##.........+..", "..##.........+..", "................", "................"])
    deposit = icon(["................", "................", ".......++.......", ".......++.......", ".......++.......",
                    ".......++.......", "....++++++++....", ".....++++++.....", "......++++......", ".......++.......",
                    "................", "..#..........#..", "..#..........#..", "..############..", "................", "................"])
    info = icon(["................", "................", "...#########....", "...#hhhhhhh##...", "...#h+++++h##...",
                 "...#h+...+h##...", "...#h+++++h##...", "...#hhhhhhh##...", "...#hhhhhhh##...", "...#hhhhhhh##...",
                 "...#hhhhhhh##...", "...#########h...", "....hhhhhhhh....", "................", "................", "................"],
                c="#6a4aa8", acc="#2fa37a", hl="#d9ccff")
    confirm = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in [(3, 8), (4, 9), (5, 10), (6, 11), (7, 10), (8, 9), (9, 8), (10, 7), (11, 6), (12, 5)]:
        rect(confirm, x, y, x, y + 1, "#5fd4b8")
    rect(confirm, 2, 13, 13, 13, "#e3d8ff")
    more = icon(["................", "................", "................", "................", "................",
                 "................", "................", "..##..##..##....", "..##..##..##....", "................",
                 "................", "................", "................", "................", "................", "................"])
    clear = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(4, 12):
        rect(clear, i, i, i + 1, i, "#ff6b7a"); rect(clear, 15 - i - 1, i, 15 - i, i, "#ff6b7a")
    outline(clear, "#3a0d18")
    links = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ring(links, 5.5, 8, 3.6, 2.6, "#c77dff", 1.3)
    ring(links, 10.5, 8, 3.6, 2.6, "#e3d8ff", 1.3)
    return {"btn_prev": prev, "btn_next": prev.transpose(Image.FLIP_LEFT_RIGHT), "btn_search": search, "btn_sort": sort,
            "btn_deposit": deposit, "btn_info": info, "btn_links": links, "btn_confirm": confirm, "btn_more": more, "btn_clear": clear,
            "btn_organize": organize()}


# ------------------------------------------------------------------ abas
def tab_icons():
    out = {}
    f = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # filtro pelo item: quadrado vazio só com os cantos
    for x, y, dx, dy in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        px(f, [(x, y), (x + dx, y), (x + 2 * dx, y), (x, y + dy), (x, y + 2 * dy)], "#9d8cc9")
    out["tab_filter"] = f
    return out


# ------------------------------------------------------------------ fundo da tela (176x222, baú duplo)
T = dict(panel=["#1a1526", "#1d1729", "#18131f"], hi="#3d2e60", lo="#09070e", edge="#050308",
         well="#110d19", slot="#0e0b15", s_sh="#060409", s_hl="#2b2141",
         btn="#2e2348", btn_hi="#5a3f94", btn_lo="#150f22", group="#241b38", group_line="#4b2f7a",
         title="#e3d8ff", plate="#a593d8", plate_lo="#6a4aa8")

# Letras no estilo da fonte padrão do Minecraft (5 px de largura, 7 de altura + descendente) para o título fixo.
GLYPHS = {
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "r": [".....", ".....", "#.##.", "##..#", "#....", "#....", "#...."],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "a": [".....", ".....", ".###.", "....#", ".####", "#...#", ".####"],
    "ú": ["...#.", "..#..", "#...#", "#...#", "#...#", "#...#", ".####"],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "#...#"],
    "t": [".#.", ".#.", "###", ".#.", ".#.", ".#.", "..#"],
    "c": [".....", ".....", ".####", "#....", "#....", "#....", ".####"],
    "e": [".....", ".....", ".###.", "#...#", "#####", "#....", ".####"],
    "l": ["#.", "#.", "#.", "#.", "#.", "#.", ".#"],
    "i": ["#", ".", "#", "#", "#", "#", "#"],
    "g": [".....", ".....", ".####", "#...#", "#...#", ".####", "....#", "####."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "s": [".....", ".....", ".####", "#....", ".###.", "....#", "####."],
    "u": [".....", ".....", "#...#", "#...#", "#...#", "#...#", ".####"],
    "d": ["....#", "....#", ".####", "#...#", "#...#", "#...#", ".####"],
    "o": [".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###."],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
}


def text(img, x, y, s, col, shadow=None):
    for ch in s:
        g = GLYPHS[ch]
        for dy, row in enumerate(g):
            for dx, c in enumerate(row):
                if c == "#":
                    if shadow:
                        img.putpixel((x + dx + 1, y + dy + 1), hx(shadow))
                    img.putpixel((x + dx, y + dy), hx(col))
        x += len(g[0]) + 1
    return x


def gui(icons):
    img = Image.new("RGBA", (176, 222), (0, 0, 0, 0))
    noise(img, T["panel"], 7, (0, 0, 176, 222))
    d = ImageDraw.Draw(img)

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hx(T["slot"]))
        d.line([x, y, x + 16, y], fill=hx(T["s_sh"])); d.line([x, y, x, y + 16], fill=hx(T["s_sh"]))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(T["s_hl"])); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(T["s_hl"]))

    def button(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hx(T["btn"]))
        d.line([x, y, x + 17, y], fill=hx(T["btn_hi"])); d.line([x, y, x, y + 17], fill=hx(T["btn_hi"]))
        d.line([x, y + 17, x + 17, y + 17], fill=hx(T["btn_lo"])); d.line([x + 17, y, x + 17, y + 17], fill=hx(T["btn_lo"]))

    def well(x0, y0, x1, y1):
        d.rectangle([x0, y0, x1, y1], fill=hx(T["well"]))
        d.line([x0, y0, x1, y0], fill=hx(T["lo"])); d.line([x0, y0, x0, y1], fill=hx(T["lo"]))
        d.line([x0, y1, x1, y1], fill=hx(T["hi"])); d.line([x1, y0, x1, y1], fill=hx(T["hi"]))

    d.rectangle([0, 0, 175, 221], outline=hx(T["edge"]))
    d.line([1, 1, 174, 1], fill=hx(T["hi"])); d.line([1, 1, 1, 220], fill=hx(T["hi"]))
    d.line([1, 220, 174, 220], fill=hx(T["lo"])); d.line([174, 1, 174, 220], fill=hx(T["lo"]))
    for p in [(0, 0), (175, 0), (0, 221), (175, 221)]:
        img.putpixel(p, (0, 0, 0, 0))

    # cabeçalho: olho + "Baú Central" (fixo); o texto do título da tela (o filtro da pesquisa) vai em x=100
    rect(img, 8, 6, 13, 9, "#2fa37a"); rect(img, 9, 5, 12, 10, "#2fa37a")
    px(img, [(9, 6)], "#a6f5d0"); rect(img, 11, 6, 11, 9, "#0a2a20")
    end = text(img, 16, 5, "Baú Central", T["title"])
    assert end <= 98, end  # o título fixo não pode encostar no texto do filtro

    # 1ª fileira: abas (encaixe do filtro pelo item + 8 categorias) em forma de pasta; depois 4 fileiras de itens
    # (slots em 7 + 18c, 17 + 18r, iguais aos do baú duplo vanilla)
    for c in range(9):
        x = 7 + c * 18
        d.rectangle([x, 17, x + 17, 34], fill=hx(T["btn"] if c else T["slot"]))
        d.line([x + 1, 17, x + 16, 17], fill=hx(T["btn_hi"] if c else T["group_line"]))
        d.line([x, 18, x, 34], fill=hx(T["btn_hi"] if c else T["group_line"]))
        d.line([x + 17, 18, x + 17, 34], fill=hx(T["btn_lo"] if c else T["group_line"]))
    d.line([6, 35, 169, 35], fill=hx(T["group_line"]))
    well(6, 35, 169, 107)
    for r in range(1, 5):
        for c in range(9):
            slot(7 + c * 18, 17 + r * 18)
    # controles em 2 grupos: [◀ página ▶] [pesquisar ordenar organizar guardar vínculos]
    for x0, x1 in ((6, 61), (78, 169)):
        d.rectangle([x0, 108, x1, 125], fill=hx(T["group"]), outline=hx(T["group_line"]))
    for c in (0, 2, 4, 5, 6, 7, 8):
        button(7 + c * 18, 108)
    slot(7 + 18, 108)
    # "Inventário" é escrito pelo jogo em cinza escuro (x=8, y=128): placa clara atrás para dar leitura em qualquer idioma
    d.rectangle([5, 126, 75, 137], fill=hx(T["plate"]), outline=hx(T["plate_lo"]))
    d.line([79, 131, 169, 131], fill=hx(T["group_line"]))
    well(6, 138, 169, 192)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 139 + r * 18)
    well(6, 196, 169, 215)
    for c in range(9):
        slot(7 + c * 18, 197)
    return img


def craft_gui(icons):
    """
    Tela da mesa de trabalho ligada (a da mesa vanilla, 176x166; ver TableMenu). Grade 3x3 em 30+18c, 17+18r, resultado
    em 124,35, inventário em 8,84 e hotbar em 8,142. Por cima do botão do livro de receitas (x 5-24, y 34-51; o fundo
    é desenhado depois dele) vai a seta de voltar: o clique nele volta para a pesquisa.
    """
    img = Image.new("RGBA", (176, 166), (0, 0, 0, 0))
    noise(img, T["panel"], 8, (0, 0, 176, 166))
    d = ImageDraw.Draw(img)

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hx(T["slot"]))
        d.line([x, y, x + 16, y], fill=hx(T["s_sh"])); d.line([x, y, x, y + 16], fill=hx(T["s_sh"]))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(T["s_hl"])); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(T["s_hl"]))

    def well(x0, y0, x1, y1):
        d.rectangle([x0, y0, x1, y1], fill=hx(T["well"]))
        d.line([x0, y0, x1, y0], fill=hx(T["lo"])); d.line([x0, y0, x0, y1], fill=hx(T["lo"]))
        d.line([x0, y1, x1, y1], fill=hx(T["hi"])); d.line([x1, y0, x1, y1], fill=hx(T["hi"]))

    d.rectangle([0, 0, 175, 165], outline=hx(T["edge"]))
    d.line([1, 1, 174, 1], fill=hx(T["hi"])); d.line([1, 1, 1, 164], fill=hx(T["hi"]))
    d.line([1, 164, 174, 164], fill=hx(T["lo"])); d.line([174, 1, 174, 164], fill=hx(T["lo"]))
    for p in [(0, 0), (175, 0), (0, 165), (175, 165)]:
        img.putpixel(p, (0, 0, 0, 0))
    eye(img, 8, 4)
    text(img, 16, 5, "Mesa do Baú Central", T["title"])
    # grade 3x3 numa moldura roxa, como o topo da mesa
    d.rectangle([28, 15, 83, 70], fill=hx("#14101e"), outline=hx(T["group_line"]))
    for r in range(3):
        for c in range(3):
            slot(29 + c * 18, 16 + r * 18)
    # seta do End (grade -> resultado)
    rect(img, 90, 42, 104, 46, "#6a4aa8"); rect(img, 90, 43, 104, 45, "#8d5fe0")
    for i in range(6):
        rect(img, 105 + i, 38 + i, 105 + i, 50 - i, "#8d5fe0")
    px(img, [(91, 43), (93, 43)], "#c77dff")
    # resultado: moldura de portal (26x26) com o olho, como o botão de pegar da pesquisa
    d.rectangle([119, 30, 144, 55], fill=hx("#3e6b5a"), outline=hx("#2a4a3e"))
    rect(img, 130, 30, 133, 31, "#2fa37a")
    d.rectangle([122, 33, 141, 52], fill=hx(T["slot"]))
    d.line([122, 33, 141, 33], fill=hx(T["s_sh"])); d.line([122, 33, 122, 52], fill=hx(T["s_sh"]))
    d.line([123, 52, 141, 52], fill=hx("#5fd4b8")); d.line([141, 34, 141, 52], fill=hx("#5fd4b8"))
    # "Inventário" é escrito pelo jogo em cinza escuro (x=8, y=72): placa clara atrás
    d.rectangle([5, 71, 75, 81], fill=hx(T["plate"]), outline=hx(T["plate_lo"]))
    well(6, 82, 169, 136)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 83 + r * 18)
    well(6, 140, 169, 159)
    for c in range(9):
        slot(7 + c * 18, 141)
    d.rectangle([5, 34, 24, 51], fill=hx(T["btn"]))  # seta de voltar por cima do botão do livro
    d.line([5, 34, 24, 34], fill=hx(T["btn_hi"])); d.line([5, 34, 5, 51], fill=hx(T["btn_hi"]))
    d.line([5, 51, 24, 51], fill=hx(T["btn_lo"])); d.line([24, 34, 24, 51], fill=hx(T["btn_lo"]))
    img.alpha_composite(icons["btn_prev"], (7, 35))
    return img


# ------------------------------------------------------------------ escrita
def search_gui(icons):
    """
    Tela da pesquisa (a bigorna do jogo, 176x166), tema "Céu do End". Em cima: a lupa, a caixa
    de texto (vazada: o jogo a desenha) e o botão de pegar (espaço de resultado). Embaixo, o inventário do jogador vira
    parte do baú: 27 resultados e a hotbar como fileira de separados. Ver SearchMenu.
    """
    img = Image.new("RGBA", (176, 166), (0, 0, 0, 0))
    noise(img, ["#0b0816", "#090712", "#07060c"], 9, (0, 0, 176, 166))
    d = ImageDraw.Draw(img)

    def slot(x, y, fill, hl):
        d.rectangle([x, y, x + 17, y + 17], fill=hx(fill))
        d.line([x, y, x + 16, y], fill=hx("#060409")); d.line([x, y, x, y + 16], fill=hx("#060409"))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(hl)); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(hl))

    d.rectangle([0, 0, 175, 165], outline=hx(T["edge"]))
    d.line([1, 1, 174, 1], fill=hx("#3a6fd8")); d.line([1, 1, 1, 164], fill=hx("#3a6fd8"))
    d.line([1, 164, 174, 164], fill=hx(T["lo"])); d.line([174, 1, 174, 164], fill=hx(T["lo"]))
    for p in [(0, 0), (175, 0), (0, 165), (175, 165)]:
        img.putpixel(p, (0, 0, 0, 0))
    rnd = random.Random(3)
    for _ in range(90):  # estrelas do portal do End na parte de cima
        img.putpixel((rnd.randrange(3, 173), rnd.randrange(3, 68)),
                     hx(rnd.choice(["#2fa37a", "#5fd4b8", "#8d5fe0", "#c77dff", "#e6fff8", "#3a6fd8"])))
    img.alpha_composite(icons["btn_search"], (38, 20))  # a lupa ao lado da busca
    d.rectangle([56, 17, 171, 38], outline=hx("#5fd4b8"))
    for x in range(59, 169):  # caixa de texto vazada (o jogo desenha a caixa e o texto)
        for y in range(20, 36):
            img.putpixel((x, y), (0, 0, 0, 0))
    # botão de pegar (espaço de resultado da bigorna): moldura de portal com o olho
    d.rectangle([127, 40, 157, 70], fill=hx("#3e6b5a"), outline=hx("#2a4a3e"))
    rect(img, 140, 40, 144, 42, "#2fa37a")
    slot(133, 46, "#0b0816", "#5fd4b8")
    slot(26, 46, "#0b0816", "#8d5fe0")  # botão de voltar (1º espaço da bigorna), com contorno
    d.rectangle([25, 45, 44, 64], outline=hx("#8d5fe0"))
    slot(75, 46, "#0b0816", "#b84a5a")  # X de limpar a pesquisa (2º espaço)
    d.rectangle([74, 45, 93, 64], outline=hx("#b84a5a"))
    # faixa dos resultados: o jogo escreve "Inventário" em cinza #404040 em (8,72); fundo da mesma cor = texto some
    d.rectangle([4, 69, 171, 81], fill=hx("#151026"))
    d.rectangle([5, 70, 64, 81], fill=hx("#404040"))
    end = 170 - (sum(len(GLYPHS[c][0]) + 1 for c in "Resultados") - 1)
    text(img, end, 72, "Resultados", "#dbd99a")
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 83 + r * 18, "#0b0816", "#2b2141")
    # hotbar = separados: moldura de portal e setinha fantasma em cada espaço
    d.rectangle([5, 139, 170, 160], fill=hx("#2a4a3e"))
    for c in range(9):
        x = 7 + c * 18
        slot(x, 141, "#0d2420", "#5fd4b8")
        for i in range(3):
            img.putpixel((x + 7 + i, 149 + i), hx("#2e6b5e")); img.putpixel((x + 11 - i, 149 + i), hx("#2e6b5e"))
    return img


def craft_search_gui(icons):
    """
    Pesquisa da mesa de trabalho ligada (a mesma bigorna da pesquisa, 176x166; ver CraftSearchMenu), no tema da tela
    da mesa: painel roxo, a mesa no lugar da lupa, fechar (1º espaço) e limpar (2º). O espaço de resultado da bigorna
    não é usado. Embaixo, 27 receitas; a hotbar não é usada e vira uma prateleira de madeira.
    """
    img = Image.new("RGBA", (176, 166), (0, 0, 0, 0))
    noise(img, T["panel"], 8, (0, 0, 176, 166))
    d = ImageDraw.Draw(img)

    def slot(x, y, hl=None):
        d.rectangle([x, y, x + 17, y + 17], fill=hx(T["slot"]))
        d.line([x, y, x + 16, y], fill=hx(T["s_sh"])); d.line([x, y, x, y + 16], fill=hx(T["s_sh"]))
        d.line([x + 1, y + 17, x + 17, y + 17], fill=hx(hl or T["s_hl"])); d.line([x + 17, y + 1, x + 17, y + 17], fill=hx(hl or T["s_hl"]))

    d.rectangle([0, 0, 175, 165], outline=hx(T["edge"]))
    d.line([1, 1, 174, 1], fill=hx(T["hi"])); d.line([1, 1, 1, 164], fill=hx(T["hi"]))
    d.line([1, 164, 174, 164], fill=hx(T["lo"])); d.line([174, 1, 174, 164], fill=hx(T["lo"]))
    for p in [(0, 0), (175, 0), (0, 165), (175, 165)]:
        img.putpixel(p, (0, 0, 0, 0))
    # topo: tampo da mesa (grade de obsidiana com linhas roxas) por trás da busca
    d.rectangle([4, 4, 171, 67], fill=hx("#14101e"), outline=hx(T["group_line"]))
    for x in range(5, 171, 9):
        d.line([x, 5, x, 66], fill=hx("#1d1630"))
    for y in range(5, 67, 9):
        d.line([5, y, 170, y], fill=hx("#1d1630"))
    rnd = random.Random(4)
    for _ in range(40):
        img.putpixel((rnd.randrange(6, 170), rnd.randrange(6, 66)), hx(rnd.choice(["#8d5fe0", "#6a4aa8", "#2fa37a", "#c77dff"])))
    top = table_faces()[2]
    img.alpha_composite(top, (36, 20))  # a mesa no lugar da lupa
    d.rectangle([56, 17, 171, 38], outline=hx("#8d5fe0"))
    for x in range(59, 169):  # caixa de texto vazada (o jogo desenha a caixa e o texto)
        for y in range(20, 36):
            img.putpixel((x, y), (0, 0, 0, 0))
    slot(26, 46, "#8d5fe0")  # fechar (1º espaço da bigorna)
    d.rectangle([25, 45, 44, 64], outline=hx("#8d5fe0"))
    slot(75, 46, "#b84a5a")  # limpar a pesquisa (2º espaço)
    d.rectangle([74, 45, 93, 64], outline=hx("#b84a5a"))
    # seta do End apontando para as receitas, no lugar do espaço de resultado (não usado)
    rect(img, 120, 50, 140, 54, "#6a4aa8"); rect(img, 120, 51, 140, 53, "#8d5fe0")
    for i in range(6):
        rect(img, 141 + i, 46 + i, 141 + i, 58 - i, "#8d5fe0")
    # "Inventário" (cinza #404040 em 8,72) quase some num roxo acinzentado de brilho parecido; à direita, "Receitas"
    d.rectangle([4, 69, 171, 81], fill=hx("#14101e"))
    d.rectangle([5, 70, 64, 81], fill=hx("#3f3850"), outline=hx(T["group_line"]))
    end = 170 - (sum(len(GLYPHS[c][0]) + 1 for c in "Receitas") - 1)
    text(img, end, 72, "Receitas", T["title"])
    d.rectangle([6, 82, 169, 136], fill=hx(T["well"]), outline=hx(T["group_line"]))
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 83 + r * 18)
    # hotbar não usada: prateleira de madeira com cantos de obsidiana
    side = table_faces()[1]
    for x in range(5, 171, 16):
        img.alpha_composite(side.crop((0, 4, min(16, 171 - x), 15)), (x, 141))
    noise(img, WOOD, 77, (5, 141, 171, 159), [.35, .3, .2, .15])
    for x in range(5, 171):
        img.putpixel((x, 146), hx("#6b5030")); img.putpixel((x, 153), hx("#6b5030"))
        img.putpixel((x, 140), hx("#6a4aa8")); img.putpixel((x, 159), hx("#251c3a"))
    for x in range(5, 171, 18):
        rect(img, x, 141, x + 1, 158, "#251c3a")
    return img


def save_png(img, rel):
    path = os.path.join(SC, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def save_json(obj, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


def cube_model():
    face = lambda t: {"texture": t, "uv": [0, 0, 16, 16]}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "smartchest:block/chest_side", "front": "smartchest:block/chest_front",
                     "side": "smartchest:block/chest_side", "top": "smartchest:block/chest_top"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": face("#front"), "south": face("#side"), "east": face("#side"), "west": face("#side"),
            "up": face("#top"), "down": face("#top")}}],
    }


def table_model():
    face = lambda t: {"texture": t, "uv": [0, 0, 16, 16]}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "smartchest:block/table_side", "front": "smartchest:block/table_front",
                     "side": "smartchest:block/table_side", "top": "smartchest:block/table_top"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": face("#front"), "south": face("#front"), "east": face("#side"), "west": face("#side"),
            "up": face("#top"), "down": face("#side")}}],
    }


def lid_models():
    """
    O baú no mundo são duas peças (SmartChest.spawnVisual): o corpo e a tampa. A tampa é desenhada com a dobradiça
    (aresta de trás, embaixo) no centro do modelo, então girar a peça é girar em volta da dobradiça. O item_display
    mostra o modelo virado meia-volta: a frente do baú no mundo é a face sul do modelo, e a tampa se estende para +z.
    """
    tex = {"particle": "smartchest:block/chest_side", "front": "smartchest:block/chest_front",
           "side": "smartchest:block/chest_side", "top": "smartchest:block/chest_top"}
    f = lambda t, v0, v1: {"texture": t, "uv": [0, v0, 16, v1]}
    base = {"parent": "minecraft:block/block", "textures": tex, "elements": [{"from": [0, 0, 0], "to": [16, 11, 16], "faces": {
        "south": f("#front", 5, 16), "north": f("#side", 5, 16), "east": f("#side", 5, 16), "west": f("#side", 5, 16),
        "down": f("#top", 0, 16)}}]}
    lid = {"parent": "minecraft:block/block", "textures": tex, "elements": [{"from": [0, 8, 8], "to": [16, 13, 24], "faces": {
        "south": f("#front", 0, 5), "north": f("#side", 0, 5), "east": f("#side", 0, 5), "west": f("#side", 0, 5),
        "up": f("#top", 0, 16), "down": f("#side", 0, 16)}}]}
    return base, lid


KEY = [  # Vinculador em 3D (chave em pé, como um desenho de 1 px de espessura que ganhou volume): caixa, textura
    ([7, 0, 7], [9, 10, 9], "gold"), ([9, 1, 7.5], [12, 2.5, 8.5], "frame"), ([9, 4, 7.5], [11, 5.5, 8.5], "frame"),
    ([4, 10, 7], [12, 11, 9], "frame"), ([4, 15, 7], [12, 16, 9], "frame"), ([4, 11, 7], [5, 15, 9], "frame"),
    ([11, 11, 7], [12, 15, 9], "frame"), ([6, 11.5, 6.5], [10, 14.5, 9.5], "pearl")]


def key_model():
    """As rotações são as do item/handheld com -45 graus a mais em z: lá o desenho é na diagonal, aqui a chave é reta.
    Na mão ela gira mais meia-volta: o personagem segura pelo aro, com os dentes para fora."""
    faces = lambda t: {k: {"texture": "#" + t} for k in ("north", "south", "east", "west", "up", "down")}
    return {
        "gui_light": "front",
        "textures": {"particle": "smartchest:item/linker_gold", "gold": "smartchest:item/linker_gold",
                     "frame": "smartchest:item/linker_frame", "pearl": "smartchest:item/linker_pearl"},
        "elements": [{"from": a, "to": b, "faces": faces(t)} for a, b, t in KEY],
        "display": {
            "gui": {"rotation": [0, 25, -45]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, -45]},
            "thirdperson_righthand": {"rotation": [0, -90, -170], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, 170], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 160], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -160], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        },
    }


def key_textures():
    out = {}
    for name, pal, hi, lo in (("gold", ["#ffd34e", "#f0b830", "#e07b0e", "#fff3a0"], "#fff3a0", "#b85e08"),
                              ("frame", ["#4b2f7a", "#5a3f94", "#3a2460"], "#8d5fe0", "#14081f"),
                              ("pearl", ["#2fa37a", "#1b8c74", "#5fd4b8"], "#a6f5d0", "#0a2a20")):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        noise(im, pal, 40 + len(name), (0, 0, 16, 16))
        bevel(im, hi, lo)
        out["linker_" + name] = im
    return out


def iso(front, side, top, k):
    """Miniatura isométrica (ícone do pack)."""
    c30, s30 = math.cos(math.radians(30)), math.sin(math.radians(30))
    W, H = int(32 * c30 * k) + 2, 32 * k + 2
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    P = lambda X, Y, Z: (W / 2 + (X - Z) * c30 * k, 16 * k + 1 + (X + Z) * s30 * k - Y * k)
    shade = lambda c, f: tuple(int(v * f) for v in c[:3]) + (255,)
    q = lambda pts, col: d.polygon(pts, fill=col, outline=col)
    for v in range(16):
        for u in range(16):
            q([P(u, 16, v), P(u + 1, 16, v), P(u + 1, 16, v + 1), P(u, 16, v + 1)], top.getpixel((u, v)))
            q([P(u, 16 - v, 16), P(u + 1, 16 - v, 16), P(u + 1, 15 - v, 16), P(u, 15 - v, 16)], shade(front.getpixel((u, v)), .85))
            q([P(16, 16 - v, 16 - u), P(16, 16 - v, 15 - u), P(16, 15 - v, 15 - u), P(16, 15 - v, 16 - u)], shade(side.getpixel((u, v)), .65))
    return out


def main():
    f, s, t = chest_faces()
    save_png(f, "block/chest_front"); save_png(s, "block/chest_side"); save_png(t, "block/chest_top")
    save_png(linker(), "item/linker")
    tf, ts, tt = table_faces()
    save_png(tf, "block/table_front"); save_png(ts, "block/table_side"); save_png(tt, "block/table_top")
    icons = buttons()
    icons.update(tab_icons())
    for name, im in icons.items():
        save_png(im, "item/" + name)
    save_png(gui(icons), "gui/storage")
    save_png(search_gui(icons), "gui/search")
    save_png(craft_gui(icons), "gui/crafting")
    save_png(craft_search_gui(icons), "gui/craft_search")

    models = os.path.join(SC, "models/item")
    save_json(cube_model(), os.path.join(models, "chest.json"))
    base, lid = lid_models()
    save_json(base, os.path.join(models, "chest_base.json")); save_json(lid, os.path.join(models, "chest_lid.json"))
    save_json(table_model(), os.path.join(models, "crafting_table.json"))
    for name, im in key_textures().items():
        save_png(im, "item/" + name)
    save_json(key_model(), os.path.join(models, "linker.json"))
    for name in icons:
        save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "smartchest:item/" + name}}, os.path.join(models, name + ".json"))

    for m in MODELS:
        save_json({"model": {"type": "minecraft:model", "model": "smartchest:item/" + m}}, os.path.join(SC, "items", m + ".json"))
    # Item invisível: o livro da caixa de texto da pesquisa (o jogo só deixa digitar com um item ali).
    save_json({"model": {"type": "minecraft:empty"}}, os.path.join(SC, "items", "blank.json"))

    # Fonte da tela: U+E000 é o fundo inteiro; U+F000 volta ao canto da tela, U+F001 desfaz a largura do fundo e
    # U+F002 leva o texto do título (o filtro da pesquisa) para x=100. U+E001 é a tela da pesquisa (bigorna, título em
    # x=60): U+F003 volta ao canto e U+F004 volta ao x=60. U+E002 é a mesa de trabalho ligada (título em x=29): U+F005
    # volta ao canto. Ver SmartChest.title() e SearchMenu.open().
    save_json({"providers": [
        {"type": "space", "advances": {chr(0xF000): -8, chr(0xF001): -177, chr(0xF002): 100,
                                           chr(0xF003): -60, chr(0xF004): 60, chr(0xF005): -29}},
        {"type": "bitmap", "file": "smartchest:gui/storage.png", "ascent": 13, "height": 222, "chars": [chr(0xE000)]},
        {"type": "bitmap", "file": "smartchest:gui/search.png", "ascent": 13, "height": 166, "chars": [chr(0xE001)]},
        {"type": "bitmap", "file": "smartchest:gui/crafting.png", "ascent": 13, "height": 166, "chars": [chr(0xE002)]},
        {"type": "bitmap", "file": "smartchest:gui/craft_search.png", "ascent": 13, "height": 166, "chars": [chr(0xE003)]},
    ]}, os.path.join(SC, "font/gui.json"))

    pack = os.path.join(ROOT, "src/pack")
    os.makedirs(pack, exist_ok=True)
    iso(f, s, t, 4).resize((64, 64), Image.LANCZOS).save(os.path.join(pack, "pack.png"))


if __name__ == "__main__":
    main()
