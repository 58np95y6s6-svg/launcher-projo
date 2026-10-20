#!/usr/bin/env python3
"""Génère les icônes de la télécommande (remote-pwa/icones/) avec Pillow.

Motif : pavé directionnel stylisé — anneau à dégradé violet → cyan (#7C5CFF → #22D3EE),
quatre chevrons et un disque central « OK », sur fond sombre.
Usage : python3 remote-pwa-dev/outils/icones.py
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SORTIE = Path(__file__).resolve().parents[2] / "remote-pwa" / "icones"
VIOLET = (0x7C, 0x5C, 0xFF)
CYAN = (0x22, 0xD3, 0xEE)
FOND_HAUT = (0x16, 0x14, 0x2E)
FOND_BAS = (0x0B, 0x0B, 0x14)
SUR = 4  # suréchantillonnage (anticrénelage)


def degrade_diagonal(taille, c1, c2):
    """Image RGB : dégradé diagonal haut-gauche → bas-droite."""
    petit = Image.new("RGB", (256, 256))
    px = petit.load()
    for y in range(256):
        for x in range(256):
            t = (x + y) / 510
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))
    return petit.resize((taille, taille), Image.BILINEAR)


def degrade_vertical(taille, c1, c2):
    col = Image.new("RGB", (1, 256))
    for y in range(256):
        t = y / 255
        col.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(c1, c2)))
    return col.resize((taille, taille), Image.BILINEAR)


def motif(taille, echelle):
    """Calque RGBA du motif, centré, de diamètre extérieur = echelle × taille."""
    T = taille * SUR
    c = T / 2
    r_ext = echelle * T / 2
    epaisseur = r_ext * 0.16
    # Dégradé calé sur le motif (et non sur toute l'icône) : violet et cyan bien visibles
    cote = round(2 * r_ext + epaisseur)
    grad = Image.new("RGB", (T, T), VIOLET)
    grad.paste(degrade_diagonal(cote, VIOLET, CYAN), (round(c - cote / 2), round(c - cote / 2)))
    grad.paste(CYAN, (round(c + cote / 2), 0, T, T))
    grad.paste(CYAN, (0, round(c + cote / 2), T, T))

    # Lueur
    lueur_masque = Image.new("L", (T, T), 0)
    d = ImageDraw.Draw(lueur_masque)
    d.ellipse([c - r_ext, c - r_ext, c + r_ext, c + r_ext], outline=150, width=round(epaisseur * 1.6))
    lueur_masque = lueur_masque.filter(ImageFilter.GaussianBlur(r_ext * 0.12))

    # Anneau + chevrons + disque central (un seul masque, peint avec le dégradé)
    m = Image.new("L", (T, T), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([c - r_ext, c - r_ext, c + r_ext, c + r_ext], outline=255, width=round(epaisseur))
    r_ok = r_ext * 0.30
    d.ellipse([c - r_ok, c - r_ok, c + r_ok, c + r_ok], fill=255)
    # Chevrons à mi-chemin entre l'anneau et le disque central
    r_chev = r_ext * 0.64
    bras = r_ext * 0.13
    ep_chev = round(epaisseur * 0.62)
    for angle in (-90, 0, 90, 180):
        a = math.radians(angle)
        px, py = c + r_chev * math.cos(a), c + r_chev * math.sin(a)
        # pointe vers l'extérieur
        ux, uy = math.cos(a), math.sin(a)
        vx, vy = -uy, ux
        p1 = (px - ux * bras + vx * bras, py - uy * bras + vy * bras)
        p2 = (px + ux * bras * 0.25, py + uy * bras * 0.25)
        p3 = (px - ux * bras - vx * bras, py - uy * bras - vy * bras)
        d.line([p1, p2, p3], fill=255, width=ep_chev, joint="curve")
        for p in (p1, p2, p3):
            d.ellipse([p[0] - ep_chev / 2, p[1] - ep_chev / 2, p[0] + ep_chev / 2, p[1] + ep_chev / 2], fill=255)

    calque = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    lueur = Image.new("RGBA", (T, T), VIOLET + (0,))
    lueur.putalpha(lueur_masque)
    calque = Image.alpha_composite(calque, lueur)
    peint = grad.convert("RGBA")
    peint.putalpha(m)
    calque = Image.alpha_composite(calque, peint)
    # Reflet blanc léger sur le disque central
    reflet = Image.new("L", (T, T), 0)
    ImageDraw.Draw(reflet).ellipse([c - r_ok * 0.42, c - r_ok * 0.42, c + r_ok * 0.42, c + r_ok * 0.42], fill=90)
    reflet = reflet.filter(ImageFilter.GaussianBlur(r_ok * 0.25))
    blanc = Image.new("RGBA", (T, T), (255, 255, 255, 0))
    blanc.putalpha(reflet)
    return Image.alpha_composite(calque, blanc)


def icone(taille, *, maskable=False, arrondi=True):
    T = taille * SUR
    fond = degrade_vertical(T, FOND_HAUT, FOND_BAS).convert("RGBA")
    # Halo coloré en haut à gauche, comme le fond de l'app
    halo = Image.new("L", (T, T), 0)
    ImageDraw.Draw(halo).ellipse([-T * 0.3, -T * 0.45, T * 0.8, T * 0.5], fill=70)
    halo = halo.filter(ImageFilter.GaussianBlur(T * 0.12))
    teinte = Image.new("RGBA", (T, T), VIOLET + (0,))
    teinte.putalpha(halo)
    fond = Image.alpha_composite(fond, teinte)
    # maskable : motif dans la zone sûre (cercle de 80 %) ; sinon plus grand
    img = Image.alpha_composite(fond, motif(taille, 0.56 if maskable else 0.66))
    if arrondi and not maskable:
        masque = Image.new("L", (T, T), 0)
        ImageDraw.Draw(masque).rounded_rectangle([0, 0, T - 1, T - 1], radius=T * 0.225, fill=255)
        img.putalpha(ImageChops.multiply(img.getchannel("A"), masque))
    return img.resize((taille, taille), Image.LANCZOS)


SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#7C5CFF"/><stop offset="1" stop-color="#22D3EE"/>
    </linearGradient>
    <linearGradient id="f" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#16142E"/><stop offset="1" stop-color="#0B0B14"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="115" fill="url(#f)"/>
  <g fill="none" stroke="url(#g)" stroke-linecap="round" stroke-linejoin="round">
    <circle cx="256" cy="256" r="155" stroke-width="27"/>
    <path stroke-width="17" d="M234 175l22-22 22 22M337 234l22 22-22 22M278 337l-22 22-22-22M175 278l-22-22 22-22"/>
  </g>
  <circle cx="256" cy="256" r="51" fill="url(#g)"/>
</svg>
"""


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    for t in (192, 512):
        icone(t).save(SORTIE / f"icone-{t}.png", optimize=True)
        icone(t, maskable=True).save(SORTIE / f"icone-maskable-{t}.png", optimize=True)
    icone(180, arrondi=False).convert("RGB").save(SORTIE / "apple-touch-icon.png", optimize=True)
    (SORTIE / "icone.svg").write_text(SVG, encoding="utf-8")
    print("Icônes écrites dans", SORTIE)


if __name__ == "__main__":
    main()
