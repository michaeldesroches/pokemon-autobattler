#!/usr/bin/env python3
"""Génère les assets du jeu dans assets/ :

- sprites.png + sprites.txt : atlas des sprites PokéAPI (face et dos), marges transparentes rognées ;
- ui/*.png : textures d'interface (rectangle arrondi plein et contour, cercle) ;
- fonts/*.fnt + .png : polices bitmap au format AngelCode (Fira Sans, repli DejaVu Sans pour ★ ● → …).

Les sprites et polices sont générés en 2x pour rester nets ; le jeu les affiche à l'échelle 0.5.

Usage : python3 tools/gen_assets.py   (nécessite Pillow ; télécharge les sprites manquants dans tools/.cache)
"""
import os
import re
import urllib.request

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
CACHE = os.path.join(ROOT, "tools", ".cache")
POKEDEX = os.path.join(ROOT, "core", "src", "main", "java", "autobattler", "Pokedex.java")
SPRITE_URL = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{back}{dex}.png"

FONT_DIRS = ["/usr/share/fonts/truetype/google-fonts/ofl/firasans", "/usr/share/fonts/truetype/dejavu"]
REGULAR = "FiraSans-Regular.ttf"
BOLD = "FiraSans-Bold.ttf"
FALLBACK = "DejaVuSans-Bold.ttf"
FALLBACK_CHARS = "★●→…—"

# nom -> (fichier, taille en pixels du jeu) ; rendu en 2x
FONTS = {
    "regular12": (REGULAR, 12),
    "regular13": (REGULAR, 13),
    "bold10": (BOLD, 10),
    "bold11": (BOLD, 11),
    "bold12": (BOLD, 12),
    "bold13": (BOLD, 13),
    "bold15": (BOLD, 15),
    "bold18": (BOLD, 18),
    "bold22": (BOLD, 22),
}
CHARSET = "".join(chr(c) for c in range(32, 127)) + "".join(chr(c) for c in range(160, 256)) + "’…—·•★●→«»œŒ"


def find_font(name):
    for d in FONT_DIRS:
        p = os.path.join(d, name)
        if os.path.exists(p):
            return p
    raise SystemExit("Police introuvable : " + name)


def dex_numbers():
    with open(POKEDEX, encoding="utf-8") as f:
        src = f.read()
    return sorted({int(m) for m in re.findall(r'add\("[a-z]+", "[^"]+", (\d+),', src)})


def fetch(dex, back):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, ("back_" if back else "front_") + str(dex) + ".png")
    if not os.path.exists(path):
        url = SPRITE_URL.format(back="back/" if back else "", dex=dex)
        urllib.request.urlretrieve(url, path)
    return Image.open(path).convert("RGBA")


def trim(img):
    bbox = img.getchannel("A").point(lambda a: 255 if a > 16 else 0).getbbox()
    return img.crop(bbox) if bbox else img


def pack(images, width):
    """Rangement en étagères. images: liste (nom, Image). Renvoie (atlas, positions)."""
    pad = 2
    order = sorted(images, key=lambda t: -t[1].height)
    x = y = shelf = 0
    pos = {}
    for name, im in order:
        if x + im.width + pad > width:
            x = 0
            y += shelf + pad
            shelf = 0
        pos[name] = (x, y, im.width, im.height)
        x += im.width + pad
        shelf = max(shelf, im.height)
    height = 1
    while height < y + shelf:
        height *= 2
    atlas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for name, im in images:
        px, py, _, _ = pos[name]
        atlas.paste(im, (px, py))
    return atlas, pos


def gen_sprites():
    images = []
    for dex in dex_numbers():
        for back in (False, True):
            images.append((("back/" if back else "front/") + str(dex), trim(fetch(dex, back))))
    atlas, pos = pack(images, 1024)
    atlas.save(os.path.join(ASSETS, "sprites.png"))
    with open(os.path.join(ASSETS, "sprites.txt"), "w", encoding="utf-8") as f:
        f.write("# nom x y largeur hauteur (pixels, origine en haut à gauche)\n")
        for name, _ in images:
            x, y, w, h = pos[name]
            f.write(f"{name} {x} {y} {w} {h}\n")
    print("sprites :", len(images), "images, atlas", atlas.size)


def gen_ui():
    ui = os.path.join(ASSETS, "ui")
    os.makedirs(ui, exist_ok=True)
    s = 4  # suréchantillonnage pour l'anticrénelage
    # Rectangle arrondi plein : 40x40, rayon 14 (= rayon 7 à l'écran), 9-patch 14 px
    big = Image.new("RGBA", (40 * s, 40 * s), (0, 0, 0, 0))
    ImageDraw.Draw(big).rounded_rectangle((0, 0, 40 * s - 1, 40 * s - 1), radius=14 * s, fill=(255, 255, 255, 255))
    big.resize((40, 40), Image.LANCZOS).save(os.path.join(ui, "round.png"))
    # Contour arrondi : trait de 4 px (= 2 à l'écran)
    big = Image.new("RGBA", (40 * s, 40 * s), (0, 0, 0, 0))
    ImageDraw.Draw(big).rounded_rectangle((2 * s, 2 * s, 38 * s - 1, 38 * s - 1), radius=12 * s,
                                          outline=(255, 255, 255, 255), width=4 * s)
    big.resize((40, 40), Image.LANCZOS).save(os.path.join(ui, "round_outline.png"))
    # Cercle plein 128x128
    big = Image.new("RGBA", (128 * s, 128 * s), (0, 0, 0, 0))
    ImageDraw.Draw(big).ellipse((0, 0, 128 * s - 1, 128 * s - 1), fill=(255, 255, 255, 255))
    big.resize((128, 128), Image.LANCZOS).save(os.path.join(ui, "circle.png"))
    # Hexagone « pointe en haut » inscrit dans un carré 112x112 (largeur = hauteur × √3/2)
    import math
    n = 112 * s
    pts = [(n / 2 + (n / 2 - 1) * math.cos(math.radians(90 + 60 * k)),
            n / 2 - (n / 2 - 1) * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
    big = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ImageDraw.Draw(big).polygon(pts, fill=(255, 255, 255, 255))
    big.resize((112, 112), Image.LANCZOS).save(os.path.join(ui, "hex.png"))
    big = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ImageDraw.Draw(big).line(pts + [pts[0]], fill=(255, 255, 255, 255), width=5 * s, joint="curve")
    big.resize((112, 112), Image.LANCZOS).save(os.path.join(ui, "hex_outline.png"))
    print("ui : ok")


def gen_font(name, file, size):
    scale = 2
    px = size * scale
    main = ImageFont.truetype(find_font(file), px)
    fallback = ImageFont.truetype(find_font(FALLBACK), int(px * 0.95))
    ascent, descent = main.getmetrics()
    glyphs = []
    for ch in CHARSET:
        font = fallback if ch in FALLBACK_CHARS else main
        x0, y0, x1, y1 = font.getbbox(ch, anchor="ls")
        adv = int(round(font.getlength(ch)))
        if x1 <= x0 or y1 <= y0:
            glyphs.append((ch, None, 0, 0, adv))
            continue
        im = Image.new("RGBA", (x1 - x0 + 2, y1 - y0 + 2), (255, 255, 255, 0))
        d = ImageDraw.Draw(im)
        d.text((1 - x0, 1 - y0), ch, font=font, fill=(255, 255, 255, 255), anchor="ls")
        glyphs.append((ch, im, x0 - 1, ascent + y0 - 1, adv))
    images = [(ch, im) for ch, im, _, _, _ in glyphs if im is not None]
    atlas, pos = pack(images, 512)
    os.makedirs(os.path.join(ASSETS, "fonts"), exist_ok=True)
    atlas.save(os.path.join(ASSETS, "fonts", name + ".png"))
    lines = [
        f'info face="{name}" size={px} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
        f'padding=0,0,0,0 spacing=1,1',
        f"common lineHeight={ascent + descent} base={ascent} scaleW={atlas.width} scaleH={atlas.height} pages=1 packed=0",
        f'page id=0 file="{name}.png"',
        f"chars count={len(glyphs)}",
    ]
    for ch, im, xo, yo, adv in glyphs:
        if im is None:
            lines.append(f"char id={ord(ch)} x=0 y=0 width=0 height=0 xoffset=0 yoffset=0 xadvance={adv} page=0 chnl=15")
        else:
            x, y, w, h = pos[ch]
            lines.append(f"char id={ord(ch)} x={x} y={y} width={w} height={h} xoffset={xo} yoffset={yo} "
                         f"xadvance={adv} page=0 chnl=15")
    with open(os.path.join(ASSETS, "fonts", name + ".fnt"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    os.makedirs(ASSETS, exist_ok=True)
    gen_sprites()
    gen_ui()
    for name, (file, size) in FONTS.items():
        gen_font(name, file, size)
    print("polices :", ", ".join(FONTS))


if __name__ == "__main__":
    main()
