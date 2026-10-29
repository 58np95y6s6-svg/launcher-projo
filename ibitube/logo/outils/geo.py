"""Identité iBiTube : le mot « iBiTube » seul, « iBi » en bleu et « Tube » en blanc.

Police : Outfit Bold (SIL Open Font License 1.1), convertie en tracés. Les fonctions renvoient des
tracés SVG en coordonnées absolues, utilisables tels quels dans un SVG et dans le pathData d'un
VectorDrawable Android.
"""
import io
import os

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
import uharfbuzz as hb

ICI = os.path.dirname(os.path.abspath(__file__))
POLICE = os.path.join(ICI, "fonts/outfit/package/files/outfit-latin-700-normal.woff2")

FOND = "#0A1020"
SURFACE = "#141C30"
ACCENT = "#1E90FF"
TEXTE = "#FFFFFF"
TEXTE2 = "#A8B3CF"
TEXTE_MOT = "iBiTube"
COUPURE = 3  # « iBi » | « Tube »


def f(v):
    s = "%.2f" % v
    s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


_police = TTFont(POLICE)
_glyphes = _police.getGlyphSet()
_noms = _police.getGlyphOrder()


def _face():
    # HarfBuzz ne lit pas le WOFF2 : police décompressée en mémoire
    tampon = io.BytesIO()
    copie = TTFont(POLICE)
    copie.flavor = None
    copie.save(tampon)
    return hb.Face(tampon.getvalue())


_FACE = _face()


def _mise_en_forme():
    buf = hb.Buffer()
    buf.add_str(TEXTE_MOT)
    buf.guess_segment_properties()
    hb.shape(hb.Font(_FACE), buf, {"kern": True, "liga": False})
    return list(zip(buf.glyph_infos, buf.glyph_positions))


_GLYPHES = _mise_en_forme()


def _dessiner(stylos, s, x, base):
    curseur = 0
    for i, (info, pos) in enumerate(_GLYPHES):
        stylo = stylos[0] if i < COUPURE else stylos[1]
        tp = TransformPen(stylo, (s, 0, 0, -s, x + (curseur + pos.x_offset) * s, base))
        _glyphes[_noms[info.codepoint]].draw(tp)
        curseur += pos.x_advance


# Emprise réelle du mot en unités de police (point du i et hampe du b compris)
_b = BoundsPen(_glyphes)
_dessiner([_b, _b], 1, 0, 0)
X0, Y0, X1, Y1 = _b.bounds  # repère de sortie (y vers le bas), ligne de base à 0
LARGEUR_UPM = X1 - X0
HAUTEUR_UPM = Y1 - Y0
RAPPORT = HAUTEUR_UPM / LARGEUR_UPM


def logotype(largeur, cx, cy):
    """Le mot centré visuellement en (cx, cy), largeur d'encre = largeur.
    Renvoie (tracé « iBi », tracé « Tube », hauteur d'encre)."""
    s = largeur / LARGEUR_UPM
    x = cx - (X0 + LARGEUR_UPM / 2) * s
    base = cy - (Y0 + HAUTEUR_UPM / 2) * s
    a, b = SVGPathPen(_glyphes), SVGPathPen(_glyphes)
    _dessiner([a, b], s, x, base)
    return a.getCommands(), b.getCommands(), HAUTEUR_UPM * s
