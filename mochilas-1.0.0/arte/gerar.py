"""Gera modelos, texturas (64x64), icones 2D (16x16) e definicoes de item das Mochilas.

Rode da pasta mochilas-1.0.0:  python3 arte/gerar.py [--preview pasta]
Saida: src/main/resources/assets/mochilas/... (vai no .jar e o Polymer monta o resource pack com elas).

Cada face de cada cubo ganha um pedaco proprio da textura (1 pixel da textura = 1 pixel do modelo, como no jogo),
pintado com a paleta do nivel: ruido de pixel art, borda um tom mais escuro e costura tracejada no couro.
"""
import hashlib
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from modelos import MODELOS, P  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
AS = os.path.join(ROOT, "src/main/resources/assets/mochilas")
TEX = 64
FACES = ("north", "south", "east", "west", "up", "down")
COSTURA = ("corpo", "tampa", "bolso")
BRILHO = ("brilho",)


def hx(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def r01(*k):
    return hashlib.md5(repr(k).encode()).digest()[0] / 255


def tamanho(face, a, b):
    dx, dy, dz = (b[i] - a[i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
            "up": (dx, dz), "down": (dx, dz)}[face]


def empacotar(faces):
    """Prateleiras simples numa textura TEX x TEX. faces: [(chave, tw, th)] -> {chave: (x, y)}."""
    pos = {}
    x = y = alt = 0
    for k, tw, th in sorted(faces, key=lambda f: (-f[2], -f[1])):
        if x + tw > TEX:
            x, y, alt = 0, y + alt, 0
        if y + th > TEX:
            raise SystemExit(f"textura cheia ({k})")
        pos[k] = (x, y)
        x += tw
        alt = max(alt, th)
    return pos, y + alt


def pinta_face(img, x0, y0, tw, th, parte, pal, semente, w, h):
    cores = [hx(c) for c in pal.get(parte, pal["corpo"])]
    cost = hx(pal["_costura"]) if parte in COSTURA else None
    for j in range(th):
        for i in range(tw):
            r = r01(semente, i, j)
            c = cores[0] if r < .22 else cores[2] if r > .8 else cores[1]
            borda = i == 0 or j == 0 or i == tw - 1 or j == th - 1
            if parte not in BRILHO and borda and tw > 2 and th > 2:
                c = tuple(int(v * .82) for v in c[:3]) + (255,)
            # costura tracejada 1 px para dentro da borda, so em faces grandes
            if cost and tw >= 6 and th >= 6 and min(i, j, tw - 1 - i, th - 1 - j) == 1:
                k = i if j in (1, th - 2) else j
                if k % 3 != 0:
                    c = cost
            img.putpixel((x0 + i, y0 + j), c)


# Cor-marca (quase branca) da cópia que o próprio dono vê. Os shaders item/entity do resource pack escondem
# o que tem essa cor quando está a menos de 1,6 bloco da câmera: na 1ª pessoa a câmera fica dentro do jogador,
# então a mochila some; na 3ª pessoa (câmera a 4 blocos) ela aparece. Sem os shaders (ex.: Iris) fica normal.
MARCA = (254, 253, 252)


def do_dono(modelo):
    """Mesmo modelo, com tintindex 0 em todas as faces para receber a cor-marca."""
    out = json.loads(json.dumps(modelo))
    for e in out["elements"]:
        for f in e["faces"].values():
            f["tintindex"] = 0
    return out


def gera(nivel, partes=None):
    partes = partes or MODELOS[nivel]
    pal = P[nivel]
    lista = []
    for n, (parte, a, b) in enumerate(partes):
        for f in FACES:
            w, h = tamanho(f, a, b)
            lista.append(((n, f), max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))))
    pos, usado = empacotar(lista)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    elems = []
    for n, (parte, a, b) in enumerate(partes):
        faces = {}
        for f in FACES:
            w, h = tamanho(f, a, b)
            x0, y0 = pos[(n, f)]
            tw, th = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
            pinta_face(img, x0, y0, tw, th, parte, pal, (nivel, n, f), w, h)
            u = 16 / TEX
            faces[f] = {"uv": [round(x0 * u, 4), round(y0 * u, 4), round((x0 + w) * u, 4), round((y0 + h) * u, 4)],
                        "texture": "#t"}
        e = {"name": parte, "from": a, "to": b, "faces": faces}
        if parte in BRILHO:
            e["light_emission"] = 12
        elems.append(e)
    modelo = {
        "credit": "Mochilas - modelo original (arte/gerar.py)",
        "textures": {"t": f"mochilas:item/mochila_{nivel}", "particle": f"mochilas:item/mochila_{nivel}"},
        "elements": elems,
        "display": {
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1, -2], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 1, -2], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 200, 0], "translation": [1, 2, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 160, 0], "translation": [1, 2, 0], "scale": [0.45, 0.45, 0.45]},
            "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.7, 0.7, 0.7]},
            "head": {"translation": [0, 6, 0], "scale": [0.9, 0.9, 0.9]},
        },
    }
    return modelo, img, usado


def icone(nivel, modelo, img):
    """Icone 16x16: vista de frente (do lado de fora da mochila), pixel a pixel, com contorno escuro."""
    ic = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    prof = [[-1e9] * 16 for _ in range(16)]
    for e in modelo["elements"]:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        uv = e["faces"]["south"]["uv"]
        for py in range(16):
            for px in range(16):
                cx, cy = px + .5, 15.5 - py + .3  # leve deslocamento para caber a alca de cima
                if x0 <= cx < x1 and y0 <= cy < y1 and z1 > prof[py][px]:
                    prof[py][px] = z1
                    tu = uv[0] * TEX / 16 + (cx - x0)
                    tv = uv[1] * TEX / 16 + (y1 - cy)
                    ic.putpixel((px, py), img.getpixel((min(TEX - 1, int(tu)), min(TEX - 1, int(tv)))))
    out = ic.copy()
    esc = hx(P[nivel]["alca"][0])
    for py in range(16):
        for px in range(16):
            if ic.getpixel((px, py))[3]:
                continue
            viz = [(px + dx, py + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(0 <= a < 16 and 0 <= b < 16 and ic.getpixel((a, b))[3] for a, b in viz):
                out.putpixel((px, py), esc)
    return out


def escreve(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)


def main():
    for nivel in MODELOS:
        modelo, img, usado = gera(nivel)
        nome = f"mochila_{nivel}"
        os.makedirs(os.path.join(AS, "textures/item"), exist_ok=True)
        img.save(os.path.join(AS, f"textures/item/{nome}.png"))
        icone(nivel, modelo, img).save(os.path.join(AS, f"textures/item/{nome}_icone.png"))
        escreve(os.path.join(AS, f"models/item/{nome}.json"), modelo)
        escreve(os.path.join(AS, f"models/item/{nome}_icone.json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"mochilas:item/{nome}_icone"}})
        # Item na mao/chao: modelo 3D; no inventario: icone 2D.
        escreve(os.path.join(AS, f"items/{nome}.json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"mochilas:item/{nome}_icone"}}],
            "fallback": {"type": "minecraft:model", "model": f"mochilas:item/{nome}"}}})
        # Versao do dono: igual, com a cor-marca (some na 1a pessoa pelos shaders).
        escreve(os.path.join(AS, f"models/item/{nome}_dono.json"), do_dono(modelo))
        escreve(os.path.join(AS, f"items/{nome}_dono.json"), {"model": {
            "type": "minecraft:model", "model": f"mochilas:item/{nome}_dono",
            "tints": [{"type": "minecraft:constant", "value": (MARCA[0] << 16) | (MARCA[1] << 8) | MARCA[2]}]}})
        # Modelo usado pela entidade virtual nas costas (sempre 3D).
        escreve(os.path.join(AS, f"items/{nome}_costas.json"),
                {"model": {"type": "minecraft:model", "model": f"mochilas:item/{nome}"}})
        print(nivel, "textura usada ate y =", usado)


if __name__ == "__main__":
    main()
