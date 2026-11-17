"""Gera as texturas do mod ChunkMap (ícones, seta, alfinete, aba do N, ícone do mod) e duas prévias (HUD e tela cheia).

Uso: python3 gerar.py  (as prévias vão para arte/previas/, que não entra no repositório)."""
import os, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "../src/main/resources/assets/chunkmap/textures/gui")
PREV = os.path.join(BASE, "previas")
os.makedirs(OUT + "/icons", exist_ok=True); os.makedirs(PREV, exist_ok=True)
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
def font(s, b=True): return ImageFont.truetype(FONT_B if b else FONT, s)

def hx(c):
    c = c.lstrip("#"); return tuple(int(c[i:i+2], 16) for i in (0, 2, 4)) + (255,)

# ---------------- ícones 16x16 (estilo pixel art) ----------------
PAL = dict(R="#8a3b2a", W="#e8d9b0", D="#5a3a1e", B="#5aa0e0", Y="#e8c86a", O="#c88a3a",
           G="#4f8a2e", S="#8c8c84", K="#2a2a30", P="#4a2a6a", L="#e0e8f0", C="#4fc8b8",
           E="#3aa070", N="#6a4a2a", M="#7ac850", A="#3a5ad0", Q="#e0c8e8", I="#c8a0e0",
           T="#b06a3a", V="#f0e0a0", H="#1a3a5a", U="#35d0e0")
ICONS = {
 "village": """.....RR.....|....RRRR....|...RRRRRR...|..RRRRRRRR..|.RRRRRRRRRR.|..WWWWWWWW..|..WWDDWWWW..|..WWDDWBBW..|..WWDDWBBW..|..WWDDWWWW..|..WWDDWWWW..|............""",
 "desert_pyramid": """............|.....YY.....|....YYYY....|....YOOY....|...YYYYYY...|..YYYYYYYY..|..YOYYYYOY..|.YYYYYYYYYY.|YYYYYYYYYYYY|YOYYYOOYYYOY|YYYYYOOYYYYY|............""",
 "jungle_temple": """............|....SSSS....|....SGGS....|...SSSSSS...|...SGSSGS...|..SSSSSSSS..|..SGSKKSGS..|.SSSSKKSSSS.|.SGGSKKSGGS.|SSSSSSSSSSSS|SGSSGSSGSSGS|............""",
 "swamp_hut": """......PP....|.....PPP....|.....PPP....|....PPPP....|....PPPP....|...PPPPPP...|...PMMMMP...|..PPPPPPPP..|PPPPPPPPPPPP|.PPPPPPPPPP.|............|............""",
 "igloo": """............|............|....LLLL....|..LLLLLLLL..|.LLSLLLLSLL.|.LLLLLLLLLL.|LLSLLKKLLSLL|LLLLLKKLLLLL|LLLLLKKLLLLL|SSSSSSSSSSSS|............|............""",
 "ocean_monument": """............|.....CC.....|....CHHC....|....CCCC....|...CCHHCC...|..CCCCCCCC..|..CHCCCCHC..|.CCCCHHCCCC.|.CHCCHHCCHC.|CCCCCCCCCCCC|CHHCCCCCCHHC|............""",
 "mansion": """............|.KKKK..KKKK.|KKKKKKKKKKKK|NNNNNNNNNNNN|NVNNVNNVNNVN|NNNNNNNNNNNN|NVNNVNNVNNVN|NNNNNNNNNNNN|NVNNNDDNNNVN|NNNNNDDNNNNN|NNNNNDDNNNNN|............""",
 "stronghold": """............|....GGGG....|..GGEEEEGG..|.GEEEEEEEEG.|.GEEEKKEEEG.|GEEEKKKKEEEG|GEEEKKKKEEEG|.GEEEKKEEEG.|.GEEEEEEEEG.|..GGEEEEGG..|....GGGG....|............""",
 "ancient_city": """............|...HHHHHH...|..HUUHHUUH..|..HUUHHUUH..|..HHHHHHHH..|..HHHUUHHH..|...HHHHHH...|...HUHUHU...|...HHHHHH...|..UU....UU..|.UU......UU.|............""",
 "trial_chambers": """............|.TTTTTTTTTT.|.TKKTTTTKKT.|.TKKTTTTKKT.|.TTTTCCTTTT.|.TTTCCCCTTT.|.TTTCCCCTTT.|.TTTTCCTTTT.|.TKKTTTTKKT.|.TKKTTTTKKT.|.TTTTTTTTTT.|............""",
 "shipwreck": """.....L......|.....LL.....|.....LLL....|.....LLLL...|.....L......|.....N......|NNNNNNNNNNNN|.NNNNNNNNNN.|..NNNNNNNN..|AAAAAAAAAAAA|.AAAAAAAAAA.|............""",
 "ruined_portal": """............|..KKKKKK....|..K....K....|..KIIIIK....|..KIPPIKK...|..KIPPI.K...|..KIPPIK....|..KIIIIK....|..KIIII.....|..KKKK.KK...|............|............""",
 "pillager_outpost": """...SSSSSS...|...S.SS.S...|...SSSSSS...|....SNNS....|....SNNS....|....SSSS....|....SNNS....|....SSSS....|....SNNS....|...SSSSSS...|..SSSSSSSS..|............""",
 "buried_treasure": """............|............|..DDDDDDDD..|.DOOOOOOOOD.|.DOOOOOOOOD.|.DDDDYYDDDD.|.DOOOYYOOOD.|.DOOOOOOOOD.|.DOOOOOOOOD.|.DDDDDDDDDD.|............|............""",
 "mineshaft": """.N........N.|.NSSSSSSSSN.|.N........N.|.N........N.|.NSSSSSSSSN.|.N........N.|.N........N.|.NSSSSSSSSN.|.N........N.|.N........N.|.NSSSSSSSSN.|.N........N.""",
 "slime_chunk": """............|.EEEEEEEEEE.|.EMMMMMMMME.|.EMKKMMKKME.|.EMKKMMKKME.|.EMMMMMMMME.|.EMMMMMMMME.|.EMMMKKMMME.|.EMMMMMMMME.|.EMMMMMMMME.|.EEEEEEEEEE.|............""",
 "spawn": """.....YY.....|.....YY.....|....YYYY....|YYYYYYYYYYYY|.YYYYYYYYYY.|..YYYYYYYY..|...YYYYYY...|..YYYYYYYY..|..YYY..YYY..|.YYY....YYY.|.YY......YY.|............""",
 "nether_fortress": """............|.R.R....R.R.|.RRR....RRR.|.RRRRRRRRRR.|.RKRRKKRRKR.|.RRRRRRRRRR.|.RR.RRRR.RR.|.RR.RRRR.RR.|.RR..RR..RR.|.RR..RR..RR.|.RR......RR.|............""",
 "bastion": """............|.K.KK..KK.K.|.KKKKKKKKKK.|.KYKKKKKKYK.|.KKKKYYKKKK.|.KKKYYYYKKK.|.KKKKYYKKKK.|.KYKKKKKKYK.|.KKKKKKKKKK.|.KKKK..KKKK.|.KKKK..KKKK.|............""",
 "end_city": """.....II.....|....IQQI....|.....II.....|....IIII....|....IQQI....|...IIIIII...|...IQIIQI...|..IIIIIIII..|..IQIIIIQI..|..IIIIIIII..|..IIIIIIII..|............""",
}
def icon(name):
    rows = ICONS[name].split("|")
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 15, 15], 3, fill=hx("#24303f"), outline=hx("#e8eef6"))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".": im.putpixel((x + 2, y + 2), hx(PAL[ch]))
    return im

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)

def icon_hd(name):
    """Versão 32x32 (o jogo desenha em 16 pixels de interface = 32 pixels de tela na escala 2): ladrilho com degradê,
    borda clara dupla, desenho 2x com contorno escuro e relevo (luz em cima, sombra embaixo)."""
    rows = ICONS[name].split("|")
    S = 32
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, S - 1, S - 1], 6, fill=hx("#0d1520"))
    d.rounded_rectangle([1, 1, S - 2, S - 2], 5, fill=hx("#f2f6fb"))
    bg = Image.new("RGBA", (S, S), (0, 0, 0, 0)); bd = ImageDraw.Draw(bg)
    for y in range(3, S - 3):
        t = (y - 3) / (S - 6)
        bd.line([(3, y), (S - 4, y)], fill=(int(46 - 16 * t), int(60 - 20 * t), int(80 - 26 * t), 255))
    mask = Image.new("L", (S, S), 0); ImageDraw.Draw(mask).rounded_rectangle([3, 3, S - 4, S - 4], 4, fill=255)
    im.paste(bg, (0, 0), mask)
    g = [[PAL[ch] if ch != "." else None for ch in r] for r in rows]
    n = len(g)
    glyph = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            if not g[y][x]: continue
            c = hx(g[y][x])
            up_empty = y == 0 or not g[y - 1][x]
            down_empty = y == n - 1 or not g[y + 1][x]
            for sy in range(2):
                for sx in range(2):
                    cc = c
                    if up_empty and sy == 0: cc = shade(c, 1.25)
                    elif down_empty and sy == 1: cc = shade(c, 0.72)
                    glyph.putpixel((4 + x * 2 + sx, 4 + y * 2 + sy), cc)
    a = np.array(glyph)[..., 3] > 0
    ring = np.zeros_like(a)
    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(1,-1),(-1,1)):
        ring |= np.roll(np.roll(a, dy, 0), dx, 1)
    ol = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y, x in zip(*np.nonzero(ring & ~a)): ol.putpixel((int(x), int(y)), (8, 10, 14, 200))
    im.alpha_composite(ol); im.alpha_composite(glyph)
    return im

for n in ICONS: icon_hd(n).save(f"{OUT}/icons/{n}.png")

def outline(im, col="#1a1a1a"):
    a = np.array(im)[..., 3] > 0
    g = np.zeros_like(a)
    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
        g |= np.roll(np.roll(a, dy, 0), dx, 1)
    out = Image.new("RGBA", im.size, (0,0,0,0)); px = out.load()
    for y, x in zip(*np.nonzero(g & ~a)): px[int(x), int(y)] = hx(col)
    out.alpha_composite(im); return out

# seta do jogador (aponta para cima; o mod gira conforme o yaw)
arrow = Image.new("RGBA", (16, 16)); ImageDraw.Draw(arrow).polygon([(8,1),(14,14),(8,10),(2,14)], fill=hx("#ffffff"))
arrow = outline(arrow)
# Seta 32x32: metade esquerda branca, direita cinza-azulada (dá volume), contorno escuro de 2 px.
a32 = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); ad = ImageDraw.Draw(a32)
ad.polygon([(16, 2), (28, 29), (16, 22), (4, 29)], fill=hx("#14181e"))
ad.polygon([(16, 6), (25, 26), (16, 20)], fill=hx("#c9d6e6"))
ad.polygon([(16, 6), (16, 20), (7, 26)], fill=hx("#ffffff"))
a32.save(f"{OUT}/player_arrow.png")
# outros jogadores
dot = Image.new("RGBA", (8, 8)); ImageDraw.Draw(dot).ellipse([1,1,6,6], fill=hx("#ffffff"))
# waypoint (tingido em runtime)
pin = Image.new("RGBA", (16, 16)); dp = ImageDraw.Draw(pin)
dp.ellipse([3,1,12,10], fill=hx("#ffffff")); dp.polygon([(4,8),(11,8),(7.5,15)], fill=hx("#ffffff"))
dp.ellipse([6,4,9,7], fill=hx("#9a9a9a")); pin = outline(pin)
# Alfinete em pixel art 16x16 (ampliado 2x). Duas texturas: o corpo (branco e cinza, tingido pela cor do waypoint no
# jogo) e por cima, sem tingir, o contorno, o miolo branco e o brilho.
PIN16 = [".....OOOOOO.....", "....OWWWWWWO....", "...OWHHWWWWSO...", "..OWHWWCCWWWSO..", "..OWHWCCCCWWSO..",
         "..OWWWCCCCWWSO..", "..OWWWWCCWWWSO..", "..OWWWWWWWWWSO..", "...OWWWWWWWSO...", "...OWWWWWWWSO...",
         "....OWWWWWSO....", "....OWWWWWSO....", ".....OWWWSO.....", "......OWSO......", "......OWSO......",
         ".......OO......."]
pin_body = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); pin_top = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, row in enumerate(PIN16):
    for x, ch in enumerate(row):
        if ch in "WHC": pin_body.putpixel((x, y), (255, 255, 255, 255))
        if ch == "S": pin_body.putpixel((x, y), (174, 174, 174, 255))
        if ch == "O": pin_top.putpixel((x, y), (20, 22, 28, 255))
        if ch == "C": pin_top.putpixel((x, y), (250, 250, 250, 255))
        if ch == "H": pin_top.putpixel((x, y), (255, 255, 255, 115))
pin_body.resize((32, 32), Image.NEAREST).save(f"{OUT}/waypoint.png")
pin_top.resize((32, 32), Image.NEAREST).save(f"{OUT}/waypoint_top.png")
# Caveira 32x32 do ponto de morte (não é tingida): osso claro com relevo, olhos e nariz escuros, contorno.
SKULL = ["....XXXXXXXX....","..XXXXXXXXXXXX..",".XXXXXXXXXXXXXX.",".XXXXXXXXXXXXXX.","XXXXXXXXXXXXXXXX",
         "XXX....XX....XXX","XXX....XX....XXX","XXX....XX....XXX","XXXXXXX..XXXXXXX",".XXXXXX..XXXXXX.",
         "..XXXXXXXXXXXX..","...XXXXXXXXXX...","...X.X.XX.X.X...","...XXXXXXXXXX...","....XXXXXXXX....","................"]
sk = Image.new("RGBA", (34, 34), (0, 0, 0, 0))  # 1 px de folga para o contorno
for y, row in enumerate(SKULL):
    for x, ch in enumerate(row):
        inside = 1 <= y <= 14 and 0 <= x <= 15 and (row[x] == "." and any(r[x] == "X" for r in SKULL[:y]) and any(r[x] == "X" for r in SKULL[y + 1:]) and "X" in row[:x] and "X" in row[x + 1:])
        if ch == "X":
            light = y < 3 or (y < 5 and x < 6)
            dark = y >= 11 or x >= 13
            c = (250, 246, 236, 255) if light else (176, 168, 152, 255) if dark else (232, 226, 210, 255)
        elif inside:
            c = (34, 22, 22, 255)
        else:
            continue
        for sy in range(2):
            for sx in range(2): sk.putpixel((1 + x * 2 + sx, 1 + y * 2 + sy), c)
sk = outline(sk, "#0e0a0a")
sk.save(f"{OUT}/skull.png")

# moldura do minimapa: 136x136, centro transparente 128x128
S = 96; F = S + 8
frame = Image.new("RGBA", (F, F)); fd = ImageDraw.Draw(frame)
fd.rounded_rectangle([0,0,F-1,F-1], 5, fill=hx("#3d6fa8"))
fd.rounded_rectangle([1,1,F-2,F-2], 4, fill=hx("#cfe0f2"))
fd.rectangle([3,3,F-4,F-4], fill=hx("#8fb3d9"))
fd.rectangle([4,4,F-5,F-5], fill=(0,0,0,0))
# aba da bússola (N)
tab = Image.new("RGBA", (12, 10)); td = ImageDraw.Draw(tab)
td.rounded_rectangle([0,0,11,9], 2, fill=hx("#cfe0f2"), outline=hx("#3d6fa8"))
# Aba do N em 24x20: degradê claro e borda azul de 2 px.
t24 = Image.new("RGBA", (24, 20), (0, 0, 0, 0)); tdd = ImageDraw.Draw(t24)
tdd.rounded_rectangle([0, 0, 23, 19], 4, fill=hx("#3d6fa8"))
for y in range(2, 18):
    t = (y - 2) / 15
    tdd.line([(2, y), (21, y)], fill=(int(236 - 30 * t), int(244 - 24 * t), 255, 255))
t24.save(f"{OUT}/compass_tab.png")

# ---------------- mapa de biomas falso para a prévia ----------------
rng = np.random.default_rng(7)
def noise(w, h, scale, oct=4):
    out = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(oct):
        s = max(2, int(scale / 2**o)); gw, gh = w//s + 3, h//s + 3
        g = rng.uniform(-1, 1, (gh, gw))
        im = Image.fromarray(((g+1)*127.5).astype(np.uint8)).resize((gw*s, gh*s), Image.BICUBIC)
        out += (np.asarray(im, float)[:h, :w]/127.5 - 1) * amp; tot += amp; amp *= .5
    return out / tot

BIO = dict(deep="#262670", ocean="#5656c8", cold="#7676dc", frozen="#a4a4ee", beach="#fade55",
           snowbeach="#faf0c0", snowy="#ffffff", snowtaiga="#31554a", taiga="#0b6659", plains="#8db360",
           forest="#056621", birch="#307444", dark="#40511a", savanna="#bdb25f", jungle="#537b09",
           desert="#fa9418", badlands="#d94515", swamp="#2f7a5e", meadow="#60a445", grove="#acd3c4",
           peaks="#dcdcc8", river="#2020ff", frozenriver="#a0a0ff")
def biome_map(w, h, seed=7):
    global rng; rng = np.random.default_rng(seed)
    C = noise(w, h, 90)*1.6; T = noise(w, h, 120)*1.5; Hm = noise(w, h, 80)*1.5; R = noise(w, h, 70, 3)
    names = np.empty((h, w), object)
    for y in range(h):
        for x in range(w):
            c, t, m = C[y,x], T[y,x], Hm[y,x]
            if c < -.32: n = "deep"
            elif c < -.12: n = "frozen" if t < -.35 else "cold" if t < -.05 else "ocean"
            elif c < -.08: n = "snowbeach" if t < -.35 else "beach"
            elif c > .42: n = "peaks" if c > .55 else ("grove" if t < 0 else "meadow")
            elif t < -.35: n = "snowtaiga" if m > 0 else "snowy"
            elif t < -.08: n = "taiga"
            elif t < .3: n = "dark" if m > .35 else "forest" if m > .1 else "birch" if m > 0 else "plains"
            elif t < .5: n = "jungle" if m > .2 else "swamp" if m > .05 else "savanna"
            else: n = "badlands" if m > .1 else "desert"
            if c > -.08 and abs(R[y,x]) < .018 and n != "peaks": n = "frozenriver" if t < -.35 else "river"
            names[y,x] = n
    img = Image.new("RGBA", (w, h)); px = img.load()
    for y in range(h):
        for x in range(w): px[x, y] = hx(BIO[names[y,x]])
    return img, names

STRUCT_BIOME = {"village": {"plains","savanna","desert","taiga","snowy","meadow"}, "desert_pyramid": {"desert"},
    "jungle_temple": {"jungle"}, "swamp_hut": {"swamp"}, "igloo": {"snowy","snowtaiga"},
    "ocean_monument": {"deep"}, "mansion": {"dark"}, "ancient_city": {"forest","birch","plains","taiga","meadow"},
    "trial_chambers": {"plains","forest","taiga","birch","savanna"}, "shipwreck": {"ocean","cold","beach"},
    "ruined_portal": set(BIO), "pillager_outpost": {"plains","savanna","desert","taiga","meadow","grove"},
    "buried_treasure": {"beach","snowbeach"}}
def place(names, step, seed=3):
    r = np.random.default_rng(seed); h, w = names.shape; out = []
    for st, bs in STRUCT_BIOME.items():
        sp = step * (3 if st in ("mansion","ancient_city","ocean_monument") else 1)
        for gy in range(0, h, sp):
            for gx in range(0, w, sp):
                if r.random() < (.85 if st in ('pillager_outpost','ruined_portal') else .55): continue
                x, y = gx + r.integers(0, sp), gy + r.integers(0, sp)
                if x < w and y < h and names[y, x] in bs: out.append((st, x, y))
    return out

def up(im, k): return im.resize((im.width*k, im.height*k), Image.NEAREST)

def text(d, xy, s, f, fill="#ffffff", shadow=True, anchor="la"):
    if shadow: d.text((xy[0]+2, xy[1]+2), s, font=f, fill="#3a3a3a", anchor=anchor)
    d.text(xy, s, font=f, fill=fill, anchor=anchor)

def game_bg(W, H):
    bg = Image.new("RGBA", (W, H)); d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / H; d.line([(0,y),(W,y)], fill=(int(120+60*t), int(170+40*t), 255, 255))
    hill = noise(W//8, 1, 20, 3)[0]
    for x in range(W):
        top = int(H*.55 + hill[x//8]*60)
        d.line([(x, top), (x, H)], fill=hx("#5d8a3a")); d.line([(x, top), (x, top+10)], fill=hx("#79b04a"))
    return bg.filter(ImageFilter.GaussianBlur(3))

# ---------------- prévia 1: HUD (canto superior esquerdo) ----------------
G = 2  # escala da GUI
W, H = 960, 540
hud = game_bg(W, H)
bm, names = biome_map(S, S, seed=23)
mp = up(bm, G)
mx, my = 6*G, 6*G
fr = up(frame, G); hud.alpha_composite(fr, (mx, my))
hud.alpha_composite(mp, (mx + 4*G, my + 4*G))
d = ImageDraw.Draw(hud)
cx, cy = mx + 4*G + S//2*G, my + 4*G + S//2*G
ox, oy = mx + 4*G, my + 4*G
for st, x, y in [("village", 22, 30), ("pillager_outpost", 78, 80), ("desert_pyramid", 44, 76), ("trial_chambers", 78, 36)]:
    if True:
        hud.alpha_composite(up(icon(st), G), (ox + x*G - 16, oy + y*G - 16))
# waypoints
for (wx, wy, col, lab) in [(16, 66, "#e04848", "Base"), (60, 16, "#48a0e0", "Mina")]:
    p = Image.new("RGBA", pin.size); p.paste(Image.new("RGBA", pin.size, hx(col)), mask=pin.split()[3])
    p = Image.composite(pin, p, Image.eval(pin.convert("L"), lambda v: 255 if v < 60 else 0))
    hud.alpha_composite(up(p, 1), (ox + wx*G - 8, oy + wy*G - 16))
# outro jogador
d.ellipse([ox+62*G-5, oy+60*G-5, ox+62*G+5, oy+60*G+5], fill="#ffffff"); d.ellipse([ox+62*G-3, oy+60*G-3, ox+62*G+3, oy+60*G+3], fill="#3cc85a")
text(d, (ox+62*G, oy+60*G-8), "Amigo", font(11), anchor="ms")
# jogador (centro, girado)
hud.alpha_composite(up(arrow.rotate(-35, Image.NEAREST), G), (cx - 16, cy - 16))
# bússola
hud.alpha_composite(up(tab, G), (mx + F*G//2 - 12, my - 6))
text(d, (mx + F*G//2, my + 4), "N", font(12), fill="#3d6fa8", shadow=False, anchor="mm")
# painel de info
iy = my + F*G + 4
d.rounded_rectangle([mx, iy, mx + F*G - 1, iy + 58], 6, fill=(20, 28, 40, 170))
text(d, (mx + 8, iy + 6), "X: 132 Y: 71 Z: -458", font(12))
text(d, (mx + 8, iy + 23), "Planícies", font(12), fill="#8de07a")
text(d, (mx + 8, iy + 40), "Dia 14 · 08:42", font(12, False), fill="#d0d8e0")
# hotbar fake
d.rectangle([W//2-182, H-46, W//2+182, H-4], fill=(30,30,30,180), outline="#9a9a9a", width=2)
for i in range(9): d.rectangle([W//2-180+i*40, H-44, W//2-142+i*40, H-6], outline="#6a6a6a", width=2)
hud.convert("RGB").save(f"{PREV}/previa_hud.png")

# ---------------- prévia 2: tela cheia (tecla M) ----------------
UI_BG, UI_LINE, UI_PANEL = "#cfe0f2", "#5b8fc7", "#e6eff9"
sc = Image.new("RGBA", (W, H), hx(UI_BG)); d = ImageDraw.Draw(sc)
# barra de filtros
d.rounded_rectangle([8, 8, W-9, 62], 6, fill=hx(UI_PANEL), outline=UI_LINE, width=2)
text(d, (18, 14), "Filtros", font(11), fill="#2a4a70", shadow=False)
order = ["village","desert_pyramid","jungle_temple","swamp_hut","igloo","ocean_monument","mansion","stronghold",
         "ancient_city","trial_chambers","shipwreck","ruined_portal","pillager_outpost","buried_treasure","mineshaft",
         "slime_chunk","spawn","nether_fortress","bastion","end_city"]
on = {"village","desert_pyramid","jungle_temple","ocean_monument","stronghold","ancient_city","trial_chambers","spawn","pillager_outpost"}
for i, n in enumerate(order):
    x = 18 + i*38; y = 28
    d.rounded_rectangle([x-2, y-2, x+32+1, y+32-1], 4, fill="#ffffff", outline=UI_LINE if n in on else "#b8c8d8", width=2 if n in on else 1)
    ic = up(icon(n), 2)
    if n not in on: ic = Image.blend(ic, Image.new("RGBA", ic.size, (255,255,255,0)), .55)
    sc.alpha_composite(ic, (x, y-1))
text(d, (W-110, 34), "Biomas ▾", font(12), fill="#2a4a70", shadow=False)
# mapa
MX0, MY0, MX1, MY1 = 8, 70, W-9, H-62
mw, mh = (MX1-MX0)//2, (MY1-MY0)//2
bm2, names2 = biome_map(mw, mh, seed=11)
sc.alpha_composite(up(bm2, 2), (MX0, MY0))
d.rectangle([MX0, MY0, MX1, MY1], outline=UI_LINE, width=2)
for st, x, y in place(names2, 40, seed=5):
    if st in on and 8 < x < mw-8 and 8 < y < mh-8:
        sc.alpha_composite(up(icon(st), 2), (MX0 + x*2 - 16, MY0 + y*2 - 16))
# eixos
for i, v in enumerate(range(-2048, 2049, 512)):
    x = MX0 + 30 + i * (MX1-MX0-60)//8
    d.rounded_rectangle([x-24, MY0+4, x+24, MY0+20], 7, fill=(40,40,50,200)); text(d, (x, MY0+12), f"{v:,}".replace(",", "."), font(10), anchor="mm", shadow=False)
for i, v in enumerate(range(-1024, 1025, 512)):
    y = MY0 + 50 + i * (MY1-MY0-80)//4
    d.rounded_rectangle([MX0+4, y-8, MX0+50, y+8], 7, fill=(40,40,50,200)); text(d, (MX0+27, y), f"{v:,}".replace(",", "."), font(10), anchor="mm", shadow=False)
# jogador, amigo, waypoints
px_, py_ = MX0 + (MX1-MX0)//2, MY0 + (MY1-MY0)//2
for (wx, wy, col, lab) in [(-140, 90, "#e04848", "Base"), (210, -70, "#48a0e0", "Mina")]:
    p = Image.new("RGBA", pin.size); p.paste(Image.new("RGBA", pin.size, hx(col)), mask=pin.split()[3])
    p = Image.composite(pin, p, Image.eval(pin.convert("L"), lambda v: 255 if v < 60 else 0))
    sc.alpha_composite(up(p, 2), (px_+wx-16, py_+wy-32)); text(d, (px_+wx, py_+wy-36), lab, font(12), anchor="ms")
d.ellipse([px_+120-7, py_+60-7, px_+120+7, py_+60+7], fill="#ffffff"); d.ellipse([px_+120-5, py_+60-5, px_+120+5, py_+60+5], fill="#3cc85a")
text(d, (px_+120, py_+60-10), "Amigo", font(12), anchor="ms")
sc.alpha_composite(up(arrow.rotate(-35, Image.NEAREST), 2), (px_-16, py_-16))
text(d, (px_, py_+34), "Você", font(12), anchor="ms")
# escala + botões de zoom
d.rounded_rectangle([MX1-110, MY1-34, MX1-44, MY1-8], 5, fill=(255,255,255,220)); text(d, (MX1-77, MY1-21), "1px = 4 bl", font(10), fill="#2a2a2a", shadow=False, anchor="mm")
for i, s in enumerate("+-"):
    d.rounded_rectangle([MX1-36, MY1-62+i*28, MX1-10, MY1-38+i*28], 4, fill="#ffffff", outline=UI_LINE); text(d, (MX1-23, MY1-50+i*28), s, font(14), fill="#2a4a70", shadow=False, anchor="mm")
# barra inferior
d.rounded_rectangle([8, H-56, W-9, H-8], 6, fill=hx(UI_PANEL), outline=UI_LINE, width=2)
def field(x, w, label, val):
    text(d, (x, H-32), label, font(12), fill="#2a4a70", shadow=False, anchor="lm")
    lw = d.textlength(label, font=font(12)) + 6
    d.rounded_rectangle([x+lw, H-45, x+lw+w, H-19], 4, fill="#ffffff", outline="#9ab4d0")
    text(d, (x+lw+6, H-32), val, font(11, False), fill="#202020", shadow=False, anchor="lm"); return x + lw + w
def button(x, w, s, primary=False):
    d.rounded_rectangle([x, H-45, x+w, H-19], 4, fill=UI_LINE if primary else "#ffffff", outline=UI_LINE)
    text(d, (x+w/2, H-32), s, font(11), fill="#ffffff" if primary else "#2a4a70", shadow=False, anchor="mm"); return x + w
e = field(18, 190, "Seed:", "-5496617415416764573"); e = button(e+6, 62, "Aplicar", True)
e = field(e+18, 60, "X:", "132"); e = field(e+8, 60, "Z:", "-458"); e = button(e+6, 36, "Ir")
e = button(e+18, 96, "Overworld ▾")
for lab in ("Grade", "Relevo"):
    d.rectangle([e+16, H-38, e+28, H-26], fill="#ffffff", outline="#5b8fc7"); text(d, (e+33, H-32), lab, font(11), fill="#2a4a70", shadow=False, anchor="lm"); e += 33 + d.textlength(lab, font=font(11)) + 6
button(W-110, 92, "+ Waypoint (B)")
sc.convert("RGB").save(f"{PREV}/previa_tela_cheia.png")

# folha de ícones
sheet = Image.new("RGBA", (len(order)*36 + 8, 44), hx(UI_BG))
for i, n in enumerate(order): sheet.alpha_composite(up(icon(n), 2), (8 + i*36, 6))
sheet.convert("RGB").save(f"{PREV}/icones.png")
print("ok")

# ícone do mod (64x64): um pedaço de mapa com a moldura azul e a seta
ic_map, _ = biome_map(56, 56, seed=23)
ic = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(ic)
d.rounded_rectangle([0, 0, 63, 63], 5, fill=hx("#3d6fa8")); d.rounded_rectangle([1, 1, 62, 62], 4, fill=hx("#cfe0f2"))
d.rectangle([3, 3, 60, 60], fill=hx("#8fb3d9")); ic.paste(ic_map, (4, 4))
ic.alpha_composite(up(arrow, 1), (24, 24))
ic.alpha_composite(icon("village"), (10, 8)); ic.alpha_composite(icon("desert_pyramid"), (38, 40))
ic.save(os.path.join(BASE, "../src/main/resources/assets/chunkmap/icon.png"))

# folha de comparação das texturas finais (vai para previas/)
final = [Image.open(f"{OUT}/icons/{n}.png") for n in order]
sh = Image.new("RGBA", (len(final) * 36 + 8, 120), hx("#cfe0f2"))
for i, im in enumerate(final): sh.alpha_composite(im, (8 + i * 36, 6))
for i, f in enumerate(["player_arrow", "waypoint"]):
    sh.alpha_composite(Image.open(f"{OUT}/{f}.png"), (8 + i * 40, 46))
tinted = Image.open(f"{OUT}/waypoint.png"); arr = np.array(tinted).astype(float); arr[..., :3] *= np.array([0.88, 0.28, 0.28])
tinted = Image.fromarray(arr.astype(np.uint8)); tinted.alpha_composite(Image.open(f"{OUT}/waypoint_top.png")); sh.alpha_composite(tinted, (88, 46))
sh.alpha_composite(Image.open(f"{OUT}/compass_tab.png"), (128, 52))
sh.alpha_composite(Image.open(f"{OUT}/skull.png"), (168, 46))
up(sh, 3).convert("RGB").save(f"{PREV}/texturas_finais.png")
