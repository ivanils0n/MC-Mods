"""Gera tudo o que vai no .jar e no resource pack do Autômato (visual escolhido).

Rode da pasta automato-1.0.0:  python3 arte/final.py

- Bloco: opção G (tijolos de pedra com relevo) com a frente em arco vazia (relevo.py, desenho 5). A ferramenta guardada
  aparece de verdade saindo do arco (um segundo item_display com o próprio item; não precisa de textura).
- Tela: fundo próprio (fonte automato:gui, U+E000) com os 9 espaços de ferramenta e as placas de alcance 1 a 5.
- Placas: modelos para flecha / flecha espectral marcadas (custom_model_data "automato_range_<n>[_on]").
Nada aqui registra bloco ou item novo; os modelos vanilla só mudam para itens marcados pelo mod.
"""
import json
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import relevo  # noqa: E402
import tela  # noqa: E402
from gerar import hx, iso, textures as flat_textures  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src/main/resources/assets")
AU = os.path.join(RES, "automato")

DIGITS = {
    1: ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    2: [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    3: ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
    4: ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    5: ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
}


def save_json(data, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1, ensure_ascii=True)
        f.write("\n")


def save_png(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def plate(n, on):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tela.button(im, 0, 0, on)
    for y, row in enumerate(DIGITS[n]):
        for x, ch in enumerate(row):
            if ch == "#":
                im.putpixel((6 + x, 5 + y), hx("#1A1410"))
                im.putpixel((5 + x, 4 + y), hx("#FFC060" if on else "#D8C8A8"))
    return im


def xp_jar(stage):
    """Pote de vidro com XP verde; stage 0 (vazio) a 8 (cheio)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(3, 15):
        for x in range(3, 13):
            im.putpixel((x, y), hx("#2A3A3E"))
    lvl = round(11 * stage / 8)
    for y in range(15 - lvl, 15):
        for x in range(4, 12):
            im.putpixel((x, y), hx("#7CFC3A" if (x + y) % 3 else "#C8FF6A"))
    for x in range(3, 13):
        im.putpixel((x, 3), hx("#BFE8F0"))
        im.putpixel((x, 15), hx("#BFE8F0"))
    for y in range(3, 16):
        im.putpixel((3, y), hx("#BFE8F0"))
        im.putpixel((12, y), hx("#BFE8F0"))
    for y in range(1, 3):
        for x in range(5, 11):
            im.putpixel((x, y), hx("#8A6A3E"))
    im.putpixel((5, 6), hx("#FFFFFF"))
    im.putpixel((5, 7), hx("#FFFFFF"))
    return im


def xp_button():
    im = Image.new("RGBA", (16, 16), hx("#3E7A1E"))
    for i in range(16):
        im.putpixel((i, 0), hx("#7CFC3A"))
        im.putpixel((0, i), hx("#7CFC3A"))
        im.putpixel((i, 15), hx("#244A10"))
        im.putpixel((15, i), hx("#244A10"))
    for y in range(2, 10):
        for x in range(4, 12):
            if (x - 7.5) ** 2 + (y - 5.5) ** 2 <= 13:
                im.putpixel((x, y), hx("#C8FF6A" if (x - 6) ** 2 + (y - 4) ** 2 > 2 else "#F0FFD0"))
    for y in range(10, 15):
        w = 14 - y
        for x in range(8 - w, 8 + w):
            im.putpixel((x, y), hx("#FFE27A"))
    for c in ((1, 1), (14, 1), (1, 14), (14, 14)):
        im.putpixel(c, hx("#FFD27A"))
    return im


def model(wall):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "automato:block/side", "side": "automato:block/side", "top": "automato:block/top",
                         "back": "automato:block/back", "inner": "automato:block/inner", "smooth": "automato:block/smooth",
                         "wall": "automato:block/" + wall},
            "elements": relevo.elements_out(5)}


def main():
    for sub in ("textures", "models", "items", "font"):
        shutil.rmtree(os.path.join(AU, sub), ignore_errors=True)
    t = relevo.textures()
    for k in ("side", "top", "back", "inner", "smooth", "wall", "wall_on"):
        save_png(t[k], os.path.join(AU, "textures/block", k + ".png"))
    save_json({"animation": {"frametime": 6}}, os.path.join(AU, "textures/block/wall_on.png.mcmeta"))
    save_json(model("wall"), os.path.join(AU, "models/item/machine.json"))
    save_json(model("wall_on"), os.path.join(AU, "models/item/machine_on.json"))

    m = lambda name: {"type": "minecraft:model", "model": "automato:item/" + name}
    save_json({"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:custom_data",
        "on_true": m("machine"),
        "on_false": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                     "cases": [{"when": "automato_idle", "model": m("machine")}, {"when": "automato_on", "model": m("machine_on")}],
                     "fallback": {"type": "minecraft:model", "model": "minecraft:block/dispenser"}}}},
        os.path.join(RES, "minecraft/items/dispenser.json"))

    # Placas de alcance: flecha = placa apagada, flecha espectral = placa escolhida (com brasa).
    for vanilla, on in (("arrow", False), ("spectral_arrow", True)):
        cases = []
        for n in range(1, 6):
            name = f"range_{n}" + ("_on" if on else "")
            save_png(plate(n, on), os.path.join(AU, "textures/item", name + ".png"))
            save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "automato:item/" + name}},
                      os.path.join(AU, "models/item", name + ".json"))
            cases.append({"when": "automato_" + name, "model": m(name)})
        save_json({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
                             "fallback": {"type": "minecraft:model", "model": "minecraft:item/" + vanilla}}},
                  os.path.join(RES, "minecraft/items", vanilla + ".json"))

    # Pote de XP (garrafa de XP marcada) e botão Recolher (esmeralda marcada).
    cases = []
    for st in range(9):
        name = f"xp_{st}"
        save_png(xp_jar(st), os.path.join(AU, "textures/item", name + ".png"))
        save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "automato:item/" + name}}, os.path.join(AU, "models/item", name + ".json"))
        cases.append({"when": "automato_" + name, "model": m(name)})
    save_json({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
                         "fallback": {"type": "minecraft:model", "model": "minecraft:item/experience_bottle"}}},
              os.path.join(RES, "minecraft/items/experience_bottle.json"))
    save_png(xp_button(), os.path.join(AU, "textures/item/xp_collect.png"))
    save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "automato:item/xp_collect"}}, os.path.join(AU, "models/item/xp_collect.json"))
    save_json({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                         "cases": [{"when": "automato_xp_collect", "model": m("xp_collect")}],
                         "fallback": {"type": "minecraft:model", "model": "minecraft:item/emerald"}}},
              os.path.join(RES, "minecraft/items/emerald.json"))

    # Fonte da tela: U+F000 volta 8 px (do x do título ao canto), U+E000 é o fundo inteiro, U+F001 desfaz a largura
    # dele (176 + 1) e U+F002 avança até o x=8 do título. Ver Automato.title().
    save_png(tela.draw_background(), os.path.join(AU, "textures/gui/machine.png"))
    save_json({"providers": [
        {"type": "space", "advances": {chr(0xF000): -8, chr(0xF001): -177, chr(0xF002): 8}},
        {"type": "bitmap", "file": "automato:gui/machine.png", "ascent": 13, "height": 150, "chars": [chr(0xE000)]},
    ]}, os.path.join(AU, "font/gui.json"))

    g = flat_textures("G")  # ícone do pack
    iso(g["front"], g["side"], g["top"], 8).resize((64, 64), Image.NEAREST).save(os.path.join(ROOT, "src/pack/pack.png"))


if __name__ == "__main__":
    main()
