"""Géométrie du logo iBiTube : monogramme « iB » dont le B a deux boucles en triangles « lecture ».

Toutes les fonctions renvoient des tracés SVG en coordonnées absolues (déjà mises à l'échelle),
utilisables tels quels dans un SVG et dans le pathData d'un VectorDrawable Android.
"""
import math
import os

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
import uharfbuzz as hb

ICI = os.path.dirname(os.path.abspath(__file__))
POLICE = os.path.join(ICI, "fonts/outfit/package/files/outfit-latin-700-normal.woff2")

BLEU_A = "#1E6BFF"
BLEU_B = "#00C2FF"
FOND = "#0A1020"
SURFACE = "#141C30"
ACCENT = "#1E90FF"
TEXTE = "#FFFFFF"
TEXTE2 = "#A8B3CF"


def f(v):
    s = "%.2f" % v
    s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def polygone_arrondi(pts, rayons):
    """Polygone fermé dont chaque sommet est arrondi (congé de rayon donné)."""
    n = len(pts)
    coins = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        r = rayons[i]
        v1 = (p0[0] - p1[0], p0[1] - p1[1])
        v2 = (p2[0] - p1[0], p2[1] - p1[1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        u1 = (v1[0] / l1, v1[1] / l1)
        u2 = (v2[0] / l2, v2[1] / l2)
        ang = math.acos(max(-1.0, min(1.0, u1[0] * u2[0] + u1[1] * u2[1])))
        t = r / math.tan(ang / 2) if r > 0 else 0
        a = (p1[0] + u1[0] * t, p1[1] + u1[1] * t)
        b = (p1[0] + u2[0] * t, p1[1] + u2[1] * t)
        e1 = (p1[0] - p0[0], p1[1] - p0[1])
        e2 = (p2[0] - p1[0], p2[1] - p1[1])
        balayage = 1 if e1[0] * e2[1] - e1[1] * e2[0] > 0 else 0
        coins.append((a, b, r, balayage))
    d = "M%s,%s" % (f(coins[0][1][0]), f(coins[0][1][1]))
    for i in range(1, n + 1):
        a, b, r, sw = coins[i % n]
        d += "L%s,%s" % (f(a[0]), f(a[1]))
        if r > 0:
            d += "A%s,%s 0 0,%d %s,%s" % (f(r), f(r), sw, f(b[0]), f(b[1]))
    return d + "Z"


def rect_arrondi(x, y, w, h, r):
    return polygone_arrondi([(x, y), (x + w, y), (x + w, y + h), (x, y + h)], [r] * 4)


def cercle(cx, cy, r):
    return "M%s,%sA%s,%s 0 1,0 %s,%sA%s,%s 0 1,0 %s,%sZ" % (
        f(cx - r), f(cy), f(r), f(r), f(cx + r), f(cy), f(r), f(r), f(cx - r), f(cy))


# --- Monogramme, dessiné dans une boîte de 100 unités -------------------------------------------
# i : point rond + fût arrondi.  B : fût + deux boucles triangulaires (des « play ») qui se rejoignent
# en une encoche au milieu ; la boucle du bas est un peu plus large, comme dans un B classique.
I_POINT = (24.0, 25.0, 7.2)
I_FUT = (17.0, 37.0, 14.0, 42.0, 7.0)
B_SOMMETS = [(38, 21), (46, 21), (77.5, 35.6), (54, 50), (81.5, 64.4), (46, 79), (38, 79)]
B_RAYONS = [7, 6, 6, 1.6, 6, 6, 7]
MONO_X0, MONO_X1, MONO_Y0, MONO_Y1 = 17.0, 81.5, 17.8, 79.0  # emprise utile


def monogramme(taille, cx, cy):
    """Tracés du monogramme centré en (cx, cy), emprise la plus large = taille."""
    largeur = MONO_X1 - MONO_X0
    hauteur = MONO_Y1 - MONO_Y0
    s = taille / max(largeur, hauteur)
    ox = cx - (MONO_X0 + largeur / 2) * s
    oy = cy - (MONO_Y0 + hauteur / 2) * s

    def P(x, y):
        return (x * s + ox, y * s + oy)

    px, py, pr = I_POINT
    x, y, w, h, r = I_FUT
    return [
        cercle(*P(px, py), pr * s),
        rect_arrondi(*P(x, y), w * s, h * s, r * s),
        polygone_arrondi([P(*p) for p in B_SOMMETS], [r * s for r in B_RAYONS]),
    ]


def badge(x, y, cote):
    """Carré aux coins arrondis (rayon 23 %), support du dégradé."""
    return rect_arrondi(x, y, cote, cote, cote * 0.23)


# --- Logotype « iBiTube » (Outfit Bold, SIL OFL 1.1, converti en tracés) ------------------------
_police = TTFont(POLICE)
_glyphes = _police.getGlyphSet()
_upem = _police["head"].unitsPerEm


def _brut():
    # HarfBuzz ne lit pas le WOFF2 : on lui passe la police décompressée en mémoire.
    import io
    tampon = io.BytesIO()
    copie = TTFont(POLICE)
    copie.flavor = None
    copie.save(tampon)
    return hb.Face(tampon.getvalue())


def logotype(hauteur_capitale, x, ligne_de_base, texte="iBiTube", espacement=0.0):
    """Renvoie [(morceau, tracé)] : « iBi » et « Tube » séparés pour les colorer différemment,
    plus la largeur totale. hauteur_capitale = hauteur visée du « B » en unités de sortie."""
    font = hb.Font(_brut())
    buf = hb.Buffer()
    buf.add_str(texte)
    buf.guess_segment_properties()
    hb.shape(font, buf, {"kern": True, "liga": False})
    cap = _police["OS/2"].sCapHeight or 700
    s = hauteur_capitale / cap
    curseur = 0.0
    morceaux = {"iBi": SVGPathPen(_glyphes), "Tube": SVGPathPen(_glyphes)}
    noms = _police.getGlyphOrder()
    for i, (info, pos) in enumerate(zip(buf.glyph_infos, buf.glyph_positions)):
        cible = morceaux["iBi" if i < 3 else "Tube"]
        nom = noms[info.codepoint]
        tp = TransformPen(cible, (s, 0, 0, -s, x + (curseur + pos.x_offset) * s, ligne_de_base))
        _glyphes[nom].draw(tp)
        curseur += pos.x_advance + espacement * _upem
    largeur = curseur * s
    return [("iBi", morceaux["iBi"].getCommands()), ("Tube", morceaux["Tube"].getCommands())], largeur


def largeur_logotype(hauteur_capitale, espacement=0.0):
    return logotype(hauteur_capitale, 0, 0, espacement=espacement)[1]
