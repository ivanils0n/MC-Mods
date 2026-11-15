"""Saco do Ender: gera texturas, modelo 3D, definição do item e ícone do resource pack.

Uso: python3 arte/gerar.py [preview.png]
Modelo: formato da trouxa do jogo (corpo largo de boca aberta, gola por cima e alça), nas cores escuras com cordão lilás.
O item é um papel do jogo marcado com custom_model_data "sacoender"; assets/minecraft/items/paper.json troca o
modelo só desse papel (qualquer outro papel continua igual ao do jogo).
"""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont


def hexc(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)

# ---------- texturas (16x16) ----------
def cloth(pal, seed, folds=True, specks=None):
    rng = np.random.default_rng(seed)
    t = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            i = 1 + (rng.random() < .25) - (rng.random() < .2)
            if folds and x % 5 == 2: i -= 1          # dobras verticais
            if folds and x % 5 == 3 and y % 3: i += 0
            if y < 2: i += 1                          # brilho no alto
            if y > 13: i -= 1                         # sombra embaixo
            t[y, x] = hexc(pal[max(0, min(len(pal) - 1, i))])
    if specks:
        for _ in range(specks[1]):
            x, y = rng.integers(0, 16, 2); t[y, x] = hexc(rng.choice(specks[0]))
    return t

def solid_noise(pal, seed):
    rng = np.random.default_rng(seed)
    t = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            t[y, x] = hexc(pal[rng.integers(0, len(pal))])
    return t

def round_icon(ring, mid, core, slit=None):
    t = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 8: t[y, x] = hexc(ring)
            if d < 6: t[y, x] = hexc(mid)
            if d < 3.2: t[y, x] = hexc(core)
            if slit and abs(x - 7.5) < 1.2 and d < 6: t[y, x] = hexc(slit)
    t[4:6, 4:6] = hexc('#ffffff') if slit is None else hexc('#f4ffd0')  # brilho
    return t

def obsidian_patch(seed):
    t = solid_noise(['#0f0a18', '#140d22', '#1b1230', '#0b0712'], seed)
    rng = np.random.default_rng(seed + 1)
    for _ in range(14):
        x, y = rng.integers(0, 16, 2); t[y, x] = hexc(rng.choice(['#5a4288', '#7a5cb0']))
    t[0, :] = hexc('#6a4f9a'); t[:, 0] = hexc('#6a4f9a')
    t[15, :] = hexc('#07040c'); t[:, 15] = hexc('#07040c')
    return t

# ---------- geometria ----------
def F(fr, to, tex, faces=None, uv=None):
    return dict(fr=fr, to=to, tex=tex, faces=faces, uv=uv or {})

BODY = [
    F([5, 0, 5], [11, 1, 11], 'cloth'),
    F([3, 1, 4], [13, 5, 12], 'cloth'),
    F([4, 1, 3], [12, 5, 13], 'cloth'),
    F([4, 5, 4], [12, 7, 12], 'cloth'),
    F([5, 7, 5], [11, 8.5, 11], 'cloth'),
    F([6, 8.5, 6], [10, 10.5, 10], 'cloth'),
    F([5.5, 8.75, 5.5], [10.5, 9.75, 10.5], 'cord'),
    F([6, 10.5, 6], [10, 11.5, 10], 'top'),
    F([5, 10.5, 5], [7, 12.5, 7], 'top'), F([9, 10.5, 5], [11, 12.5, 7], 'top'),
    F([5, 10.5, 9], [7, 12.5, 11], 'top'), F([9, 10.5, 9], [11, 12.5, 11], 'top'),
]
FULL = {d: [0, 0, 16, 16] for d in ['north', 'south', 'east', 'west', 'up', 'down']}

# ---------- auto UV (igual ao Minecraft) ----------
def face_quads(e):
    (x1, y1, z1), (x2, y2, z2) = e['fr'], e['to']
    # cantos: origem (u0,v0), eixo u, eixo v  ; normal
    return {
        'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1), (0, 0, -1), [16 - x2, 16 - y2, 16 - x1, 16 - y1]),
        'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2), (0, 0, 1), [x1, 16 - y2, x2, 16 - y1]),
        'west':  ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1), (-1, 0, 0), [z1, 16 - y2, z2, 16 - y1]),
        'east':  ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2), (1, 0, 0), [16 - z2, 16 - y2, 16 - z1, 16 - y1]),
        'up':    ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2), (0, 1, 0), [x1, z1, x2, z2]),
        'down':  ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1), (0, -1, 0), [x1, 16 - z2, x2, 16 - z1]),
    }

SHADE = {'up': 1.0, 'down': .5, 'north': .8, 'south': .8, 'east': .6, 'west': .6}

def rot(rx, ry):
    a, b = math.radians(rx), math.radians(ry)
    Ry = np.array([[math.cos(b), 0, -math.sin(b)], [0, 1, 0], [math.sin(b), 0, math.cos(b)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    return Rx @ Ry

def render(elements, tex, size=512, rx=30, ry=225, scale=0.5):
    R = rot(rx, ry)
    img = np.zeros((size, size, 4), np.float32)
    zb = np.full((size, size), -1e9)
    k = size / 16 * scale * 1.6
    def P(p):
        q = R @ (np.array(p, float) - 8)
        return np.array([size / 2 + q[0] * k, size / 2 - q[1] * k, q[2]])
    for e in elements:
        for d, (o, ou, ov, n, auto) in face_quads(e).items():
            nn = R @ np.array(n, float)
            if nn[2] <= 0: continue
            T = tex[e['tex']]
            rect = e['uv'].get(d, auto)
            po, pu, pv = P(o), P(ou), P(ov)
            eu, ev = pu - po, pv - po
            M = np.array([[eu[0], ev[0]], [eu[1], ev[1]]])
            if abs(np.linalg.det(M)) < 1e-6: continue
            Mi = np.linalg.inv(M)
            pts = np.array([po, pu, pv, pu + ev])
            x0, x1 = int(max(0, pts[:, 0].min())), int(min(size - 1, pts[:, 0].max()) + 1)
            y0, y1 = int(max(0, pts[:, 1].min())), int(min(size - 1, pts[:, 1].max()) + 1)
            if x1 <= x0 or y1 <= y0: continue
            ys, xs = np.mgrid[y0:y1, x0:x1]
            dx, dy = xs + .5 - po[0], ys + .5 - po[1]
            s = Mi[0, 0] * dx + Mi[0, 1] * dy
            t = Mi[1, 0] * dx + Mi[1, 1] * dy
            m = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            z = po[2] + s * eu[2] + t * ev[2]
            u = rect[0] + s * (rect[2] - rect[0]); v = rect[1] + t * (rect[3] - rect[1])
            tu = np.clip(np.floor(u).astype(int), 0, 15); tv = np.clip(np.floor(v).astype(int), 0, 15)
            col = T[tv, tu].astype(np.float32)
            m &= col[..., 3] > 0
            m &= z > zb[y0:y1, x0:x1]
            sh = SHADE[d]
            c = col.copy(); c[..., :3] *= sh
            img[y0:y1, x0:x1][m] = c[m]
            zb[y0:y1, x0:x1][m] = z[m]
    return Image.fromarray(img.clip(0, 255).astype(np.uint8))


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = ROOT + '/src/main/resources/assets'

TEX = dict(
    cloth=cloth(['#120d1a', '#1c1528', '#271d38', '#33274a'], 31, folds=False),
    top=cloth(['#120d1a', '#1c1528', '#271d38', '#33274a'], 32, folds=False),
    cord=solid_noise(['#6a4296', '#8456b8', '#a06fd6'], 33),
    inner=solid_noise(['#06040a', '#0b0812', '#100b19'], 35),
)

def hollow(x1, y1, z1, x2, y2, z2, t, tex, inner):
    """Moldura oca (paredes de espessura t) com fundo escuro: a boca aberta da trouxa."""
    return [F([x1, y1, z1], [x2, y2, z1 + t], tex), F([x1, y1, z2 - t], [x2, y2, z2], tex),
            F([x1, y1, z1 + t], [x1 + t, y2, z2 - t], tex), F([x2 - t, y1, z1 + t], [x2, y2, z2 - t], tex),
            F([x1 + t, y1, z1 + t], [x2 - t, y1 + 0.25, z2 - t], inner)]

BUNDLE = [
    F([3, 0, 5], [13, 1, 11], 'cloth'),                      # fundo
    F([2, 1, 4], [14, 7, 12], 'cloth'),                      # corpo
] + hollow(2, 7, 4, 14, 8.5, 12, 1, 'top', 'inner') + [   # boca do corpo
    F([4.5, 7, 6], [11.5, 9.5, 10], 'top'),                  # pescoço franzido
] + hollow(3.5, 9.5, 5, 12.5, 11.5, 11, 1, 'top', 'inner') + [  # gola
    F([5, 8.5, 7.5], [6, 13, 8.5], 'cord'),                  # alça
    F([10, 8.5, 7.5], [11, 13, 8.5], 'cord'),
    F([5, 13, 7.5], [11, 14, 8.5], 'cord'),
]

def plate_set():
    """Placa de obsidiana com 4 rebites, igual nos quatro lados."""
    out = []
    # (placa) frente z-, trás z+, oeste x-, leste x+
    out += [F([4.5, 1.5, 2.6], [11.5, 4.5, 3], 'plate', uv=FULL), F([4.5, 1.5, 13], [11.5, 4.5, 13.4], 'plate', uv=FULL),
            F([2.6, 1.5, 4.5], [3, 4.5, 11.5], 'plate', uv=FULL), F([13, 1.5, 4.5], [13.4, 4.5, 11.5], 'plate', uv=FULL)]
    for y in (1.7, 3.7):
        for a in (4.7, 10.7):
            out += [F([a, y, 2.3], [a + .6, y + .6, 2.6], 'cord'), F([a, y, 13.4], [a + .6, y + .6, 13.7], 'cord'),
                    F([2.3, y, a], [2.6, y + .6, a + .6], 'cord'), F([13.4, y, a], [13.7, y + .6, a + .6], 'cord')]
    return out

ELEMENTS = BUNDLE

def model_json():
    els = []
    for e in ELEMENTS:
        faces = {}
        for d in face_quads(e):
            f = {'texture': '#' + e['tex']}
            if d in e['uv']: f['uv'] = e['uv'][d]
            faces[d] = f
        els.append({'from': e['fr'], 'to': e['to'], 'faces': faces})
    return {
        'textures': {t: f'sacoender:item/saco_{t}' for t in sorted(TEX)} | {'particle': 'sacoender:item/saco_cloth'},
        'elements': els,
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 1.25, 0], 'scale': [0.8, 0.8, 0.8]},
            'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'translation': [0, 1, 0], 'scale': [0.75, 0.75, 0.75]},
            'head': {'translation': [0, 13, 0], 'scale': [0.8, 0.8, 0.8]},
            'thirdperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2.5, 1], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 2.5, 1], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
        }}

PAPER = {'model': {
    'type': 'minecraft:select',
    'property': 'minecraft:custom_model_data',
    'index': 0,
    'cases': [{'when': 'sacoender', 'model': {'type': 'minecraft:model', 'model': 'sacoender:item/saco'}}],
    'fallback': {'type': 'minecraft:model', 'model': 'minecraft:item/paper'},
}}

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)
        f.write('\n')

if __name__ == '__main__':
    import sys
    os.makedirs(RES + '/sacoender/textures/item', exist_ok=True)
    for t, arr in TEX.items():
        Image.fromarray(arr).save(f'{RES}/sacoender/textures/item/saco_{t}.png', optimize=True)
    dump(RES + '/sacoender/models/item/saco.json', model_json())
    dump(RES + '/minecraft/items/paper.json', PAPER)
    os.makedirs(ROOT + '/src/pack', exist_ok=True)
    icon = render(ELEMENTS, TEX, 64, scale=0.7)
    bg = Image.new('RGBA', (64, 64), (24, 16, 36, 255)); bg.alpha_composite(icon)
    bg.save(ROOT + '/src/pack/pack.png', optimize=True)
    if len(sys.argv) > 1:
        big = render(ELEMENTS, TEX, 420, scale=0.52)
        back = render(ELEMENTS, TEX, 420, rx=30, ry=45, scale=0.52)
        slot = render(ELEMENTS, TEX, 32, scale=0.72).resize((96, 96), Image.NEAREST)
        sheet = Image.new('RGBA', (900, 560), (28, 28, 33, 255))
        sheet.alpha_composite(big, (10, 20)); sheet.alpha_composite(back, (450, 20))
        from PIL import ImageDraw, ImageFont
        d = ImageDraw.Draw(sheet)
        d.rectangle([20, 440, 124, 544], fill=(139, 139, 139, 255), outline=(55, 55, 55, 255), width=3)
        sheet.alpha_composite(slot, (24, 444))
        fnt = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 24)
        d.text((150, 470), 'Saco do Ender - frente e costas', font=fnt, fill=(235, 235, 235, 255))
        sheet.save(sys.argv[1])
    print('ok')
