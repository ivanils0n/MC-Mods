"""Geometria das mochilas (unidades de modelo: 16 = 1 bloco) e paletas.

z=6 encosta nas costas do jogador e z cresce para fora; as alcas descem pelo peito (z~2).
Escolhidos: couro e ferro (imagens do usuario), ouro B (Mercador), diamante E (Geodo).
"""

# z=6 encosta nas costas; z cresce para fora. Alcas descem pelo peito (z~2).
def alcas(y_top=12):
    out = []
    for x in (5, 10):
        out += [
            ("alca", [x, y_top, 2], [x + 1, y_top + 1, 8]),       # sobre o ombro
            ("alca", [x, 4, 1.75], [x + 1, y_top + 1, 2.5]),     # desce pelo peito
            ("alca", [x, 4, 1.75], [x + 1, 5, 6]),               # volta por baixo do braco
        ]
    return out

COURO = [
    ("corpo",  [4, 1, 6],        [12, 11, 11]),
    ("tampa",  [3.75, 10, 5.75], [12.25, 11.5, 11.25]),
    ("bolso",  [4.25, 1, 11],    [11.75, 6.5, 13]),
    ("bolso",  [2.5, 1.5, 7],    [4, 6.5, 10]),
    ("bolso",  [12, 1.5, 7],     [13.5, 6.5, 10]),
    ("fecho",  [7, 7.5, 11],     [9, 9.5, 11.5]),
] + alcas(11)

def base_grande():
    return [
        ("corpo",  [3.5, 0, 6],       [12.5, 11, 11]),
        ("tampa",  [3.25, 10.5, 5.75], [12.75, 13, 11.75]),
        ("faixa",  [3.2, 10.25, 5.7],  [12.8, 10.75, 11.8]),
        ("faixa",  [3.4, 0.25, 5.9],   [12.6, 1, 11.1]),
        # tiras da tampa + fivelas
        ("alca",   [5, 13, 6],    [6, 13.25, 12]),
        ("alca",   [10, 13, 6],   [11, 13.25, 12]),
        ("alca",   [5, 8, 11.75], [6, 13.25, 12]),
        ("alca",   [10, 8, 11.75], [11, 13.25, 12]),
        ("fivela", [4.75, 7.25, 11.75], [6.25, 8.75, 12.25]),
        ("fivela", [9.75, 7.25, 11.75], [11.25, 8.75, 12.25]),
        # alca de mao
        ("metal",  [6, 13, 8],   [6.75, 14.5, 9]),
        ("metal",  [9.25, 13, 8], [10, 14.5, 9]),
        ("metal",  [6, 14, 8],   [10, 14.75, 9]),
    ] + alcas(12)

FERRO = base_grande() + [
    ("bolso",  [4.5, 0.5, 11],  [11.5, 6, 13.5]),
    ("faixa",  [4.4, 3, 11],    [11.6, 3.75, 13.6]),
    ("fivela", [5.25, 2.5, 13.5], [6.5, 4.5, 13.85]),
    ("fivela", [9.5, 2.5, 13.5],  [10.75, 4.5, 13.85]),
    ("bolso",  [1.75, 0.5, 7],  [3.5, 6, 10.5]),
    ("bolso",  [12.5, 0.5, 7],  [14.25, 6, 10.5]),
    ("faixa",  [1.65, 3, 6.9],  [3.5, 3.75, 10.6]),
    ("faixa",  [12.5, 3, 6.9],  [14.35, 3.75, 10.6]),
    ("placa",  [3, 6.5, 7],     [3.5, 10, 10]),     # placas laterais
    ("placa",  [12.5, 6.5, 7],  [13, 10, 10]),
]

OURO_B = base_grande() + [  # "Mercador": dois bolsos frontais e emblema na tampa
    ("bolso",  [3.75, 0.5, 11], [7.75, 6, 13]),
    ("bolso",  [8.25, 0.5, 11], [12.25, 6, 13]),
    ("fivela", [5.25, 4, 13],   [6.25, 5.5, 13.35]),
    ("fivela", [9.75, 4, 13],   [10.75, 5.5, 13.35]),
    ("placa",  [7, 10.75, 11.75], [9, 12.75, 12]),     # emblema
    ("bolso",  [1.75, 0.5, 7],  [3.5, 7, 10.5]),
    ("bolso",  [12.5, 0.5, 7],  [14.25, 7, 10.5]),
    ("placa",  [1.5, 1.5, 7.5], [1.75, 6, 10]),
    ("placa",  [14.25, 1.5, 7.5], [14.5, 6, 10]),
]

# Paletas: parte -> tons (escuro, base, claro)
P = {
    "couro": {
        "corpo": ["#593A25", "#805331", "#8C5C38"], "tampa": ["#805331", "#A87548", "#B47F50"],
        "bolso": ["#8C5C38", "#A87548", "#B8865A"], "alca": ["#4A3020", "#593A25", "#6A452C"],
        "fecho": ["#AEB5BB", "#D5D9DD", "#C4C9CE"], "_costura": "#D1AE7A"},
    "ferro": {
        "corpo": ["#454B52", "#5A6168", "#646B72"], "tampa": ["#5A6168", "#777F87", "#808890"],
        "bolso": ["#5A6168", "#777F87", "#808890"], "alca": ["#2F343A", "#3A4046", "#454B52"],
        "faixa": ["#777F87", "#AEB5BB", "#C0C6CB"], "placa": ["#AEB5BB", "#D5D9DD", "#C8CDD1"],
        "fivela": ["#AEB5BB", "#D5D9DD", "#E8EBED"], "metal": ["#777F87", "#AEB5BB", "#D5D9DD"],
        "_costura": "#AEB5BB"},
    "ouro": {
        "corpo": ["#5A2E1E", "#7A3F28", "#86492F"], "tampa": ["#654126", "#8A4A2E", "#965437"],
        "bolso": ["#6E3A24", "#8A4A2E", "#965437"], "alca": ["#3E2416", "#4D2C1B", "#654126"],
        "faixa": ["#A66F16", "#E1AE32", "#FFE17A"], "placa": ["#A66F16", "#E1AE32", "#FFE17A"],
        "fivela": ["#E1AE32", "#FFE17A", "#FFE17A"], "metal": ["#A66F16", "#E1AE32", "#FFE17A"],
        "_costura": "#E1AE32"},
    "diamante": {
        "corpo": ["#0F3540", "#164A58", "#1B5564"], "tampa": ["#164A58", "#1D5F6E", "#238A9C"],
        "bolso": ["#164A58", "#1D5F6E", "#238A9C"], "alca": ["#0B2830", "#0F3540", "#164A58"],
        "faixa": ["#238A9C", "#53D4D5", "#A5FFFF"], "placa": ["#238A9C", "#53D4D5", "#A5FFFF"],
        "fivela": ["#53D4D5", "#A5FFFF", "#A5FFFF"], "metal": ["#238A9C", "#53D4D5", "#A5FFFF"],
        "brilho": ["#A5FFFF", "#D8FFFF", "#A5FFFF"], "_costura": "#53D4D5"},
}


# E - Geodo: cristais de diamante brotando da tampa e laterais
def cristal(x, y, z, h, base=1.0):
    return [("placa", [x, y, z], [x + base, y + h, z + base]),
            ("brilho", [x + 0.25, y + h, z + 0.25], [x + base - 0.25, y + h + 0.75, z + base - 0.25])]

DIAM_E = base_grande() + [
    ("bolso",  [4.5, 0.5, 11],  [11.5, 6, 13.5]),
    ("faixa",  [4.4, 4.75, 11], [11.6, 5.5, 13.6]),
    ("fivela", [7.25, 4.25, 13.5], [8.75, 6, 13.85]),
    ("bolso",  [1.75, 0.5, 7],  [3.5, 6, 10.5]),
    ("bolso",  [12.5, 0.5, 7],  [14.25, 6, 10.5]),
] + cristal(3.5, 13, 6.5, 1.5) + cristal(4.25, 13, 9, 1) + cristal(11.25, 13, 6.75, 2) \
  + cristal(1.25, 6, 8, 1.25, 1) + cristal(13.75, 6, 7.5, 1.75, 1) + cristal(2, 6, 9.25, 0.75, 0.75)


MODELOS = {"couro": COURO, "ferro": FERRO, "ouro": OURO_B, "diamante": DIAM_E}
