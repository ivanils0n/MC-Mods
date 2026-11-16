"""Gera o ícone do mod (assets/spawninfo/icon.png): um slime pixelado com um mostrador de chance.
Uso: python3 arte/gerar.py (na pasta do mod)."""
from pathlib import Path
from PIL import Image, ImageDraw

S = 16  # desenho em 16x16, ampliado 8x
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# Fundo: céu de caverna escuro com borda.
d.rectangle([0, 0, 15, 15], fill=(28, 34, 44, 255))
d.rectangle([0, 0, 15, 0], fill=(255, 216, 102, 255))
# Slime: cubo verde translúcido com núcleo e olhos.
d.rectangle([2, 4, 11, 13], fill=(94, 181, 72, 255))
d.rectangle([2, 4, 11, 4], fill=(138, 214, 112, 255))
d.rectangle([2, 4, 2, 13], fill=(138, 214, 112, 255))
d.rectangle([11, 5, 11, 13], fill=(62, 132, 52, 255))
d.rectangle([3, 13, 11, 13], fill=(62, 132, 52, 255))
d.rectangle([4, 7, 9, 11], fill=(76, 156, 60, 255))
d.point([(4, 7), (8, 7)], fill=(20, 40, 20, 255))
d.point([(5, 7), (9, 7)], fill=(20, 40, 20, 255))
d.rectangle([6, 10, 7, 10], fill=(20, 40, 20, 255))
# Mostrador: barra de chance no canto (verde, amarelo, vermelho).
d.rectangle([13, 3, 14, 14], fill=(60, 66, 76, 255))
d.rectangle([13, 10, 14, 14], fill=(102, 209, 122, 255))
d.rectangle([13, 7, 14, 9], fill=(255, 201, 77, 255))
out = Path(__file__).resolve().parent.parent / "src/main/resources/assets/spawninfo/icon.png"
img.resize((128, 128), Image.NEAREST).save(out)
print("ícone salvo em", out)
