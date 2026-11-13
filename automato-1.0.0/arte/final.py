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
