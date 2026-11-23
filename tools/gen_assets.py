#!/usr/bin/env python3
"""Génère les assets du jeu dans assets/ :

- sprites.png + sprites.txt : atlas des sprites PokéAPI (face et dos), marges transparentes rognées ;
- ui/*.png : textures d'interface (rectangle arrondi plein et contour, cercle, hexagones, icônes de classe) ;
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
PMD_URL = "https://raw.githubusercontent.com/PMDCollab/SpriteCollab/master/{kind}/{dex:04d}/{file}"
# Animations PMD utilisées par le jeu ; directions gardées : 0 bas, 1 bas-droite, 2 droite, 3 haut-droite, 4 haut
# (les directions gauches sont obtenues par symétrie à l'affichage).
PMD_ANIMS = ["Idle", "Walk", "Attack", "Hurt", "Sleep"]
PMD_DIRS = 5
PMD_PAGE = 2048

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


def fetch_pmd(kind, dex, file):
    d = os.path.join(CACHE, "pmd", kind, "%04d" % dex)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, file)
    if not os.path.exists(path):
        try:
            urllib.request.urlretrieve(PMD_URL.format(kind=kind, dex=dex, file=file), path)
        except Exception:
            return None
    return path


def pmd_anims(dex):
    """Lit AnimData.xml : {nom: (largeur, hauteur, [durées])} en résolvant les CopyOf."""
    import xml.etree.ElementTree as ET
    path = fetch_pmd("sprite", dex, "AnimData.xml")
    if path is None:
        return {}
    root = ET.parse(path).getroot()
    raw = {}
    for a in root.iter("Anim"):
        name = a.findtext("Name")
        copy = a.findtext("CopyOf")
        if copy:
            raw[name] = ("copy", copy)
        else:
            durs = [int(d.text) for d in a.find("Durations").iter("Duration")]
            raw[name] = (int(a.findtext("FrameWidth")), int(a.findtext("FrameHeight")), durs)
    res = {}
    for name in raw:
        v, seen = raw[name], 0
        src = name
        while v[0] == "copy" and seen < 5:
            src = v[1]
            v = raw[src]
            seen += 1
        if v[0] != "copy":
            res[name] = (src,) + v
    return res


def gen_pmd():
    """Atlas des sprites animés PMDCollab (frames rognées) + portraits, avec index pmd.txt et crédits."""
    frames = []      # (clé, Image)
    lines = []       # lignes d'index sans les positions (complétées après empaquetage)
    credits = {}
    for dex in dex_numbers():
        anims = pmd_anims(dex)
        if not anims:
            print("  pas de sprite PMD pour", dex)
            continue
        cpath = fetch_pmd("sprite", dex, "credits.txt")
        if cpath:
            for row in open(cpath, encoding="utf-8", errors="ignore"):
                parts = row.split("\t")
                if len(parts) > 1:
                    credits.setdefault(parts[1].strip(), set()).add(dex)
        for anim in PMD_ANIMS:
            if anim not in anims:
                continue
            src, fw, fh, durs = anims[anim]
            sheet_path = fetch_pmd("sprite", dex, src + "-Anim.png")
            if sheet_path is None:
                continue
            sheet = Image.open(sheet_path).convert("RGBA")
            n = sheet.width // fw
            rows = sheet.height // fh
            lines.append(("A", dex, anim, n, fw, fh, ",".join(str(d) for d in durs[:n])))
            for d in range(min(PMD_DIRS, rows)):
                for f in range(n):
                    fr = sheet.crop((f * fw, d * fh, (f + 1) * fw, (d + 1) * fh))
                    bbox = fr.getchannel("A").getbbox()
                    if bbox is None:
                        bbox = (0, 0, 1, 1)
                    key = "F %d %s %d %d" % (dex, anim, d, f)
                    frames.append((key, fr.crop(bbox), bbox[0] - fw // 2, bbox[1] - fh // 2))
        ppath = fetch_pmd("portrait", dex, "Normal.png")
        if ppath:
            frames.append(("P %d" % dex, Image.open(ppath).convert("RGBA"), 0, 0))
    # empaquetage sur plusieurs pages de 2048 de large
    pad = 1
    order = sorted(frames, key=lambda t: -t[1].height)
    pages, pos = [], {}
    x = y = shelf = 0
    page = 0
    for key, im, ox, oy in order:
        if x + im.width + pad > PMD_PAGE:
            x = 0
            y += shelf + pad
            shelf = 0
        if y + im.height > PMD_PAGE:
            page += 1
            x = y = shelf = 0
        pos[key] = (page, x, y)
        x += im.width + pad
        shelf = max(shelf, im.height)
    heights = {}
    for key, im, ox, oy in frames:
        p, px, py = pos[key]
        heights[p] = max(heights.get(p, 0), py + im.height)
    for p in range(page + 1):
        h = 1
        while h < heights.get(p, 1):
            h *= 2
        pages.append(Image.new("RGBA", (PMD_PAGE, h), (0, 0, 0, 0)))
    out = ["# Index des sprites PMDCollab (généré par tools/gen_assets.py)",
           "# A dex anim nbFrames largeur hauteur durées(1/60 s)",
           "# F dex anim direction frame page x y largeur hauteur décalageX décalageY (depuis le centre de la frame)",
           "# P dex page x y largeur hauteur (portrait 40x40)"]
    for l in lines:
        out.append(" ".join(str(v) for v in l))
    for key, im, ox, oy in frames:
        p, px, py = pos[key]
        pages[p].paste(im, (px, py))
        if key.startswith("F"):
            out.append("%s %d %d %d %d %d %d %d" % (key, p, px, py, im.width, im.height, ox, oy))
        else:
            out.append("%s %d %d %d %d %d" % (key, p, px, py, im.width, im.height))
    for p, img in enumerate(pages):
        img.save(os.path.join(ASSETS, "pmd%d.png" % p), optimize=True)
    with open(os.path.join(ASSETS, "pmd.txt"), "w", encoding="utf-8") as f:
        f.write("pages %d\n" % len(pages))
        f.write("\n".join(out) + "\n")
    # identifiants Discord -> noms (credit_names.txt du dépôt SpriteCollab)
    names = {}
    npath = os.path.join(CACHE, "pmd", "credit_names.txt")
    if not os.path.exists(npath):
        try:
            urllib.request.urlretrieve("https://raw.githubusercontent.com/PMDCollab/SpriteCollab/master/credit_names.txt", npath)
        except Exception:
            npath = None
    if npath:
        for row in open(npath, encoding="utf-8", errors="ignore"):
            parts = row.rstrip("\n").split("\t")
            if len(parts) >= 2 and parts[0] != "Name":
                names[parts[1].strip()] = parts[0].strip()
    merged = {}
    for author, dexes in credits.items():
        merged.setdefault(names.get(author) or author, set()).update(dexes)
    credits = merged
    with open(os.path.join(ROOT, "CREDITS-PMD.md"), "w", encoding="utf-8") as f:
        f.write("# Crédits des sprites et portraits (PMDCollab SpriteCollab)\n\n")
        f.write("Sprites animés et portraits issus de [PMDCollab/SpriteCollab](https://github.com/PMDCollab/SpriteCollab), "
                "sous licence [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) : usage non commercial, crédit obligatoire.\n\n")
        f.write("| Auteur | Pokémon (n° du Pokédex) |\n| --- | --- |\n")
        for author in sorted(credits, key=lambda a: a.lower()):
            f.write("| %s | %s |\n" % (author, ", ".join(str(d) for d in sorted(credits[author]))))
    print("pmd :", len(frames), "images,", len(pages), "page(s)", [p.size for p in pages])


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
    gen_role_icons(ui)
    print("ui : ok")


def gen_role_icons(ui):
    """Icônes de classe (blanches, teintées à l'affichage), 6 cases de 64x64 dans l'ordre de Trait :
    Gardien (bouclier), Combattant (poing/épée), Assassin (dague), Mystique (étincelle), Tireur (cible), Starter (étoile)."""
    import math
    s, n = 4, 64
    N = n * s
    W = (255, 255, 255, 255)
    strip = Image.new("RGBA", (n * 6, n), (0, 0, 0, 0))

    def canvas():
        im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        return im, ImageDraw.Draw(im)

    def P(*pts):
        return [(x * N, y * N) for x, y in pts]

    icons = []
    # Gardien : bouclier
    im, d = canvas()
    d.polygon(P((.16, .14), (.5, .06), (.84, .14), (.84, .48), (.5, .94), (.16, .48)), fill=W)
    icons.append(im)
    # Combattant : épée
    im, d = canvas()
    d.polygon(P((.44, .62), (.44, .16), (.5, .05), (.56, .16), (.56, .62)), fill=W)
    d.rectangle(P((.24, .62), (.76, .71)), fill=W)
    d.rectangle(P((.45, .71), (.55, .88)), fill=W)
    d.ellipse(P((.40, .86), (.60, .98)), fill=W)
    icons.append(im)
    # Assassin : dague courbe (croissant) + lame
    im, d = canvas()
    d.polygon(P((.80, .08), (.62, .50), (.46, .60), (.40, .54), (.52, .40)), fill=W)
    d.line(P((.30, .52), (.56, .78)), fill=W, width=int(.08 * N))
    d.line(P((.42, .66), (.18, .90)), fill=W, width=int(.11 * N))
    icons.append(im)
    # Mystique : étincelle à 4 branches + petite
    im, d = canvas()
    def sparkle(cx, cy, R, r):
        pts = []
        for k in range(8):
            a = math.radians(90 + 45 * k)
            rr = R if k % 2 == 0 else r
            pts.append((cx + rr * math.cos(a), cy - rr * math.sin(a)))
        return [(x * N, y * N) for x, y in pts]
    d.polygon(sparkle(.44, .54, .40, .10), fill=W)
    d.polygon(sparkle(.80, .20, .16, .045), fill=W)
    icons.append(im)
    # Tireur : cible
    im, d = canvas()
    w = int(.09 * N)
    d.ellipse(P((.14, .14), (.86, .86)), outline=W, width=w)
    d.ellipse(P((.38, .38), (.62, .62)), fill=W)
    for a, b in (((.5, .02), (.5, .26)), ((.5, .74), (.5, .98)), ((.02, .5), (.26, .5)), ((.74, .5), (.98, .5))):
        d.line(P(a, b), fill=W, width=w)
    icons.append(im)
    # Starter : étoile à 5 branches
    im, d = canvas()
    pts = []
    for k in range(10):
        a = math.radians(90 + 36 * k)
        rr = .46 if k % 2 == 0 else .19
        pts.append((.5 + rr * math.cos(a), .54 - rr * math.sin(a)))
    d.polygon(P(*pts), fill=W)
    icons.append(im)

    for i, im in enumerate(icons):
        strip.paste(im.resize((n, n), Image.LANCZOS), (i * n, 0))
    strip.save(os.path.join(ui, "roles.png"))


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
    gen_pmd()
    gen_ui()
    for name, (file, size) in FONTS.items():
        gen_font(name, file, size)
    print("polices :", ", ".join(FONTS))


if __name__ == "__main__":
    main()
