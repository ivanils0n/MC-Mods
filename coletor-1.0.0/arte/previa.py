"""Prévia das 3 opções: cubo 3D (isométrico) + faces planas. python3 arte/previa.py saida.png"""
import os
import sys

from PIL import Image, ImageDraw, ImageEnhance, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gerar  # noqa: E402

NOMES = {"A": "A - Celeiro (carvalho e trigo)", "B": "B - Ferro e Enxada (pedra)", "C": "C - Cesto de Vime"}


def face(im, size, shade):
    f = im.resize((size, size), Image.NEAREST).convert("RGBA")
    return ImageEnhance.Brightness(f).enhance(shade)


def cubo(tex, s=128):
    """Cubo isométrico: topo, frente-esquerda e frente-direita."""
    w, h = int(s * 1.732), s * 2
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hw = w / 2
    top = face(tex["top"], s, 1.0)
    left = face(tex["side"], s, 0.82)
    right = face(tex["side"], s, 0.64)
    # topo: (u,v) -> (hw + (u - v)*hw/s, (u + v)*s/2/s)
    a = hw / s
    t = top.transform((w, h), Image.AFFINE, _inv(a, -a, hw, 0.5, 0.5, 0), Image.NEAREST)
    l = left.transform((w, h), Image.AFFINE, _inv(a, 0, 0, 0.5, 1, s / 2), Image.NEAREST)
    r = right.transform((w, h), Image.AFFINE, _inv(a, 0, hw, -0.5, 1, s), Image.NEAREST)
    for p in (l, r, t):
        out.alpha_composite(p)
    return out


def _inv(a, b, c, d, e, f):
    """Coeficientes inversos (saída -> entrada) do mapa x=a*u+b*v+c, y=d*u+e*v+f."""
    det = a * e - b * d
    ia, ib, id_, ie = e / det, -b / det, -d / det, a / det
    return (ia, ib, -(ia * c + ib * f), id_, ie, -(id_ * c + ie * f))


def fonte(n):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, n)
    return ImageFont.load_default()


def main(saida):
    cols = []
    for op in "ABC":
        tex = gerar.OPCOES[op]()
        card = Image.new("RGBA", (300, 470), (38, 38, 44, 255))
        d = ImageDraw.Draw(card)
        c = cubo(tex, 110)
        card.alpha_composite(c, ((300 - c.width) // 2, 50))
        for i, nome in enumerate(("top", "side", "bottom")):
            f = tex[nome].resize((80, 80), Image.NEAREST)
            card.alpha_composite(f, (16 + i * 94, 320))
            d.text((16 + i * 94 + 40, 408), {"top": "topo", "side": "lado", "bottom": "base"}[nome], fill="#bbb", font=fonte(13), anchor="mt")
        d.text((150, 18), NOMES[op], fill="white", font=fonte(17), anchor="mt")
        cols.append(card)
    sheet = Image.new("RGBA", (300 * 3 + 40, 470 + 20), (24, 24, 28, 255))
    for i, c in enumerate(cols):
        sheet.alpha_composite(c, (10 + i * 310, 10))
    sheet.save(saida)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "previa.png")
