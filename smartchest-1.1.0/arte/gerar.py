"""Gera texturas, modelos, fonte da tela e definições de item do Baú Inteligente.

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
TABS = ["todos", "construcao", "natureza", "funcionais", "redstone", "ferramentas", "comida", "ingredientes"]
MODELS = ["chest", "linker", "btn_prev", "btn_next", "btn_search", "btn_sort", "btn_info", "btn_deposit", "btn_links",
          "btn_confirm", "btn_more", "tab_filter"] + [f"tab_{k}{on}" for k in TABS for on in ("", "_on")]


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


def buttons():
    prev = icon(["................", "................", "................", "........#.......", ".......##.......",
                 "......##........", ".....##.........", "....##########..", "....##########..", ".....##.........",
                 "......##........", ".......##.......", "........#.......", "................", "................", "................"])
    search = icon(["................", "................", "....#####.......", "...#h++++#......", "..#h++++++#.....",
                   "..#++++++++#....", "..#++++++++#....", "..#++++++++#....", "...#++++++#.....", "....######......",
                   "..........##....", "...........##...", "............##..", ".............#..", "................", "................"],
                  c="#e3d8ff", acc="#1b8c74", hl="#a6f5d0")
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
    links = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ring(links, 5.5, 8, 3.6, 2.6, "#c77dff", 1.3)
    ring(links, 10.5, 8, 3.6, 2.6, "#e3d8ff", 1.3)
    return {"btn_prev": prev, "btn_next": prev.transpose(Image.FLIP_LEFT_RIGHT), "btn_search": search, "btn_sort": sort,
            "btn_deposit": deposit, "btn_info": info, "btn_links": links, "btn_confirm": confirm, "btn_more": more}


# ------------------------------------------------------------------ abas
def tab_icons():
    I = lambda rows, pal: grid(rows, pal)
    icons = {
        "todos": I(["................", "................", "..############..", "..#hhhhhhhhhh#..", "..#h########h#..",
                    "..#h#......#h#..", "..############..", "..#hhhhggghhh#..", "..#hhhhgeghhh#..", "..#hhhhggghhh#..",
                    "..#hhhhhhhhhh#..", "..#hhhhhhhhhh#..", "..############..", "................", "................", "................"],
                   {"#": "#0b0812", "h": "#4b2f7a", "g": "#2fa37a", "e": "#0a2a20"}),
        "construcao": I(["................", "................", "..############..", "..#rrr#rrrrr#...", "..#rrr#rrrrr#...",
                         "..############..", "..#rrrrr#rrr#...", "..#rrrrr#rrr#...", "..############..", "..#rrr#rrrrr#...",
                         "..#rrr#rrrrr#...", "..############..", "................", "................", "................", "................"],
                        {"#": "#d9ccc0", "r": "#a0503a"}),
        "natureza": I(["................", "................", "..gggggggggggg..", "..gGgggGggggGg..", "..gggGgggGgggg..",
                       "..dgdddgdddgdd..", "..dddddddddddd..", "..ddDdddddDddd..", "..dddddDdddddd..", "..dDdddddddDdd..",
                       "..dddddddddddd..", "..ddddDddddddd..", "................", "................", "................", "................"],
                      {"g": "#5da83a", "G": "#86d05a", "d": "#7a5634", "D": "#5c3f24"}),
        "funcionais": I(["................", "................", "..############..", "..#wwwwwwwwww#..", "..#w#w#ww#w#w#..",
                         "..############..", "..#pppppppppp#..", "..#p#pppppp#p#..", "..#p#pppppp#p#..", "..#pppppppppp#..",
                         "..#p#pppppp#p#..", "..############..", "................", "................", "................", "................"],
                        {"#": "#3d2a17", "w": "#b8915a", "p": "#8a6634"}),
        "comida": I(["................", "................", ".......b........", "......b.ll......", "....rrbrrl......",
                     "...rrrrrrrr.....", "..rRrrrrrrrr....", "..rRrrrrrrrr....", "..rrrrrrrrrr....", "..rrrrrrrrrr....",
                     "...rrrrrrrr.....", "....rr..rr......", "................", "................", "................", "................"],
                    {"b": "#5c3f24", "l": "#5da83a", "r": "#d8262a", "R": "#ff8a7a"}),
    }
    red = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px(red, [(6, 6), (7, 6), (8, 7), (9, 8), (7, 9), (6, 8), (8, 9), (10, 7), (5, 7), (9, 10), (4, 9)], "#e52b1a")
    px(red, [(7, 7), (8, 8)], "#ff7a6a")
    icons["redstone"] = red
    pick = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(10):
        pick.putpixel((2 + i, 13 - i), hx("#7a5530"))
    for t in range(-5, 6):
        x, y = 10 + t + (t * t) // 9, 5 + t - (t * t) // 9
        for a, b, col in ((x, y, "#4ee6d8"), (x + 1, y, "#1fb5a8")):
            if 0 <= a < 16 and 0 <= b < 16:
                pick.putpixel((a, b), hx(col))
    outline(pick, "#0d1f1d")
    icons["ferramentas"] = pick
    ingot = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(ingot, 3, 7, 12, 10, "#d8d8d8"); rect(ingot, 4, 6, 13, 6, "#ffffff"); rect(ingot, 3, 10, 12, 10, "#9a9a9a")
    icons["ingredientes"] = ingot
    out = {}
    for k in TABS:
        out["tab_" + k] = icons[k]
        on = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # aba ativa: fundo claro e moldura na própria textura
        rect(on, 0, 0, 15, 15, "#5a3f94")
        rect(on, 0, 0, 15, 0, "#c9b8ff"); rect(on, 0, 0, 0, 15, "#c9b8ff")
        rect(on, 0, 15, 15, 15, "#2e2348"); rect(on, 15, 0, 15, 15, "#2e2348")
        on.alpha_composite(icons[k].resize((12, 12), Image.NEAREST), (2, 2))
        out["tab_" + k + "_on"] = on
    out["tab_filter"] = grid(["................", "................", "..##..##..##....", "..#..........#..", "..............#.",
                              "..#.............", "..#......h...#..", "........hhh.....", ".......hhhhh..#.", "..#.....hhh.....",
                              "..#......h......", "..............#.", "..#..........#..", "....##..##..##..", "................", "................"],
                             {"#": "#9d8cc9", "h": "#5fd4b8"})
    return out


# ------------------------------------------------------------------ fundo da tela (176x222, baú duplo)
T = dict(panel=["#1a1526", "#1d1729", "#18131f"], hi="#3d2e60", lo="#09070e", edge="#050308",
         well="#110d19", slot="#0e0b15", s_sh="#060409", s_hl="#2b2141",
         btn="#2e2348", btn_hi="#5a3f94", btn_lo="#150f22", group="#241b38", group_line="#4b2f7a",
         title="#e3d8ff", plate="#a593d8", plate_lo="#6a4aa8")

# Letras no estilo da fonte padrão do Minecraft (5 px de largura, 7 de altura + descendente) para o título fixo.
GLYPHS = {
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "a": [".....", ".....", ".###.", "....#", ".####", "#...#", ".####"],
    "ú": ["...#.", "..#..", "#...#", "#...#", "#...#", "#...#", ".####"],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "#...#"],
    "t": [".#.", ".#.", "###", ".#.", ".#.", ".#.", "..#"],
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

    # cabeçalho: olho + "Baú Inteligente" (fixo); o texto do título da tela (o filtro da pesquisa) vai em x=100
    rect(img, 8, 6, 13, 9, "#2fa37a"); rect(img, 9, 5, 12, 10, "#2fa37a")
    px(img, [(9, 6)], "#a6f5d0"); rect(img, 11, 6, 11, 9, "#0a2a20")
    end = text(img, 16, 5, "Baú Inteligente", T["title"])
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
    # controles em 3 grupos: [◀ página ▶] [pesquisar ordenar] [guardar vínculos]
    for x0, x1 in ((6, 61), (78, 115), (132, 169)):
        d.rectangle([x0, 108, x1, 125], fill=hx(T["group"]), outline=hx(T["group_line"]))
    for c in (0, 2, 4, 5, 7, 8):
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


# ------------------------------------------------------------------ escrita
def search_gui(icons, front):
    """
    Tela da pesquisa (a bigorna do jogo, 176x166), tema "Céu do End". Em cima: o desenho do Baú Inteligente, a caixa
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
    img.alpha_composite(front.resize((16, 16), Image.NEAREST), (38, 20))  # o Baú Inteligente ao lado da busca
    d.rectangle([56, 17, 171, 38], outline=hx("#5fd4b8"))
    for x in range(59, 169):  # caixa de texto vazada (o jogo desenha a caixa e o texto)
        for y in range(20, 36):
            img.putpixel((x, y), (0, 0, 0, 0))
    # botão de pegar (espaço de resultado da bigorna): moldura de portal com o olho
    d.rectangle([127, 40, 157, 70], fill=hx("#3e6b5a"), outline=hx("#2a4a3e"))
    rect(img, 140, 40, 144, 42, "#2fa37a")
    slot(133, 46, "#0b0816", "#5fd4b8")
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
    icons = buttons()
    icons.update(tab_icons())
    for name, im in icons.items():
        save_png(im, "item/" + name)
    save_png(gui(icons), "gui/storage")
    save_png(search_gui(icons, f), "gui/search")

    models = os.path.join(SC, "models/item")
    save_json(cube_model(), os.path.join(models, "chest.json"))
    save_json({"parent": "minecraft:item/handheld", "textures": {"layer0": "smartchest:item/linker"}}, os.path.join(models, "linker.json"))
    for name in icons:
        save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "smartchest:item/" + name}}, os.path.join(models, name + ".json"))

    for m in MODELS:
        save_json({"model": {"type": "minecraft:model", "model": "smartchest:item/" + m}}, os.path.join(SC, "items", m + ".json"))
    # Item invisível: o livro da caixa de texto da pesquisa (o jogo só deixa digitar com um item ali).
    save_json({"model": {"type": "minecraft:empty"}}, os.path.join(SC, "items", "blank.json"))

    # Fonte da tela: U+E000 é o fundo inteiro; U+F000 volta ao canto da tela, U+F001 desfaz a largura do fundo e
    # U+F002 leva o texto do título (o filtro da pesquisa) para x=100. U+E001 é a tela da pesquisa (bigorna, título em
    # x=60): U+F003 volta ao canto e U+F004 volta ao x=60. Ver SmartChest.title() e SearchMenu.open().
    save_json({"providers": [
        {"type": "space", "advances": {chr(0xF000): -8, chr(0xF001): -177, chr(0xF002): 100,
                                           chr(0xF003): -60, chr(0xF004): 60}},
        {"type": "bitmap", "file": "smartchest:gui/storage.png", "ascent": 13, "height": 222, "chars": [chr(0xE000)]},
        {"type": "bitmap", "file": "smartchest:gui/search.png", "ascent": 13, "height": 166, "chars": [chr(0xE001)]},
    ]}, os.path.join(SC, "font/gui.json"))

    pack = os.path.join(ROOT, "src/pack")
    os.makedirs(pack, exist_ok=True)
    iso(f, s, t, 4).resize((64, 64), Image.LANCZOS).save(os.path.join(pack, "pack.png"))


if __name__ == "__main__":
    main()
