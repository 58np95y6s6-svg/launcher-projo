"""Génère les sources SVG du logo iBiTube, les VectorDrawable Android et les pages HTML
qui servent à produire les PNG (icônes, bannière, splash, logo d'en-tête).

Usage : python3 gen.py <dossier_sortie>
  <sortie>/ibitube/logo/*.svg                       sources vectorielles (versionnées)
  <sortie>/smarttubetv/src/ibitube/res/...          VectorDrawable / AVD XML
  <sortie>/_png/*.html + liste.json                 pages à photographier pour les PNG
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geo import (ACCENT, BLEU_A, BLEU_B, FOND, TEXTE, badge, cercle, logotype, monogramme)  # noqa: E402

SORTIE = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "out")
RES = os.path.join(SORTIE, "smarttubetv/src/ibitube/res")
LOGO = os.path.join(SORTIE, "ibitube/logo")
PNG = os.path.join(SORTIE, "_png")
GRIS_TEXTE = "#A8B3CF"


def ecrire(chemin, texte):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8") as fh:
        fh.write(texte)


ENTETE_SVG = "<!-- iBiTube : logo original (monogramme « iB », boucles du B en triangles « lecture »).\n" \
             "     Dégradé #1E6BFF → #00C2FF. Logotype : police Outfit Bold (SIL OFL 1.1) convertie en tracés. -->\n"


def svg(l, h, corps, defs=""):
    return ('<?xml version="1.0" encoding="UTF-8"?>\n' + ENTETE_SVG +
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{l:g}" height="{h:g}" viewBox="0 0 {l:g} {h:g}">\n'
            + (f"  <defs>{defs}</defs>\n" if defs else "") + corps + "</svg>\n")


def degrade_svg(ident, x1, y1, x2, y2):
    return (f'<linearGradient id="{ident}" gradientUnits="userSpaceOnUse" x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}">'
            f'<stop offset="0" stop-color="{BLEU_A}"/><stop offset="1" stop-color="{BLEU_B}"/></linearGradient>')


def chemins_svg(traces, couleur, indent="  "):
    return "".join(f'{indent}<path fill="{couleur}" d="{d}"/>\n' for d in traces)


# ---------- éléments composés ----------
def logo_badge(x, y, cote, ident="g"):
    """Badge dégradé + monogramme blanc (SVG). Le monogramme occupe 64 % du côté."""
    defs = degrade_svg(ident, x, y, x + cote, y + cote)
    corps = f'  <path fill="url(#{ident})" d="{badge(x, y, cote)}"/>\n'
    corps += chemins_svg(monogramme(cote * 0.64, x + cote / 2, y + cote / 2), TEXTE)
    return defs, corps


def logotype_svg(hauteur_cap, x, base):
    morceaux, largeur = logotype(hauteur_cap, x, base)
    corps = f'  <path fill="{ACCENT}" d="{morceaux[0][1]}"/>\n  <path fill="{TEXTE}" d="{morceaux[1][1]}"/>\n'
    return corps, largeur


# ---------- 1. sources SVG ----------
def sources():
    d, c = logo_badge(0, 0, 512)
    ecrire(f"{LOGO}/ibitube-logo.svg", svg(512, 512, c, d))

    # Icône adaptative : premier plan (monogramme blanc dans la zone sûre de 66 dp) et fond dégradé
    ecrire(f"{LOGO}/ibitube-icon-foreground.svg", svg(108, 108, chemins_svg(monogramme(50, 54, 54), TEXTE)))
    ecrire(f"{LOGO}/ibitube-icon-background.svg",
           svg(108, 108, '  <rect width="108" height="108" fill="url(#g)"/>\n', degrade_svg("g", 0, 0, 108, 108)))

    # Variante sombre (icône alternative) : fond nuit, monogramme en dégradé
    dg = degrade_svg("g", 27, 27, 81, 81)
    ecrire(f"{LOGO}/ibitube-icon-alt.svg", svg(108, 108, f'  <rect width="108" height="108" fill="{FOND}"/>\n'
                                              + chemins_svg(monogramme(50, 54, 54), "url(#g)"), dg))

    # Logotype seul
    corps, L = logotype_svg(60, 4, 74)
    ecrire(f"{LOGO}/ibitube-wordmark.svg", svg(round(L + 8), 84, corps))

    # Bannière Android TV 320 x 180
    ecrire(f"{LOGO}/ibitube-banner.svg", banniere_svg())

    # Logo d'en-tête (badge + logotype), 320 x 180 comme l'original
    ecrire(f"{LOGO}/ibitube-lockup.svg", entete_svg())

    # Écran de démarrage (aperçu 1920 x 1080 de ce que montre le thème)
    ecrire(f"{LOGO}/ibitube-splash.svg", splash_svg())


def banniere_svg():
    cap = 34
    _, L = logotype(cap, 0, 0)
    cote = 92
    ecart = 16
    total = cote + ecart + L
    x0 = (320 - total) / 2
    y0 = (180 - cote) / 2
    d, c = logo_badge(x0, y0, cote)
    glow = ('<radialGradient id="h" cx="0.3" cy="0.5" r="0.75"><stop offset="0" stop-color="#1E6BFF" stop-opacity="0.28"/>'
            '<stop offset="1" stop-color="#1E6BFF" stop-opacity="0"/></radialGradient>')
    corps = f'  <rect width="320" height="180" fill="{FOND}"/>\n  <rect width="320" height="180" fill="url(#h)"/>\n' + c
    t, _ = logotype_svg(cap, x0 + cote + ecart, 90 + cap / 2)
    return svg(320, 180, corps + t, d + glow)


def entete_svg():
    cap = 36
    _, L = logotype(cap, 0, 0)
    cote = 88
    ecart = 14
    total = cote + ecart + L
    x0 = 320 - total - 6        # calé à droite comme l'original (badge de la barre de titre)
    y0 = 18
    d, c = logo_badge(x0, y0, cote)
    t, _ = logotype_svg(cap, x0 + cote + ecart, y0 + cote / 2 + cap / 2)
    return svg(320, 180, c + t, d)


def splash_svg():
    cote = 240
    cx, cy = 960, 500
    d, c = logo_badge(cx - cote / 2, cy - cote / 2, cote)
    _, L = logotype(56, 0, 0)
    t, _ = logotype_svg(56, cx - L / 2, cy + cote / 2 + 56 + 48)
    return svg(1920, 1080, f'  <rect width="1920" height="1080" fill="{FOND}"/>\n' + c + t, d)


# ---------- 2. ressources Android ----------
XML = '<?xml version="1.0" encoding="utf-8"?>\n'


def vd_chemins(traces, couleur, indent="    "):
    return "".join(f'{indent}<path\n{indent}    android:fillColor="{couleur}"\n{indent}    android:pathData="{d}" />\n'
                   for d in traces)


def vd_degrade(traces, x1, y1, x2, y2, indent="    "):
    out = ""
    for d in traces:
        out += (f'{indent}<path android:pathData="{d}">\n'
                f'{indent}    <aapt:attr name="android:fillColor">\n'
                f'{indent}        <gradient android:type="linear" android:startX="{x1:g}" android:startY="{y1:g}"'
                f' android:endX="{x2:g}" android:endY="{y2:g}"\n'
                f'{indent}            android:startColor="{BLEU_A}" android:endColor="{BLEU_B}" />\n'
                f'{indent}    </aapt:attr>\n{indent}</path>\n')
    return out


def android():
    # Premier plan de l'icône adaptative : blanc uni (aucun dégradé, donc compatible avec la
    # conversion PNG automatique d'AGP pour les vieilles versions d'Android).
    ecrire(f"{RES}/drawable/ibitube_icon_foreground.xml", XML +
           '<!-- iBiTube : premier plan de l\'icône adaptative (source : ibitube/logo/ibitube-icon-foreground.svg) -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           + vd_chemins(monogramme(50, 54, 54), "#FFFFFFFF") + "</vector>\n")

    # Premier plan de l'icône alternative : monogramme en dégradé → API 24+ uniquement
    ecrire(f"{RES}/drawable-v24/ibitube_icon_alt_foreground.xml", XML +
           '<!-- iBiTube : premier plan de l\'icône alternative (dégradé, Android 7+) -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           + vd_degrade(monogramme(50, 54, 54), 27, 27, 81, 81) + "</vector>\n")
    # Repli sous Android 7 : même dessin en bleu uni
    ecrire(f"{RES}/drawable/ibitube_icon_alt_foreground.xml", XML +
           '<!-- iBiTube : premier plan de l\'icône alternative, repli en bleu uni (avant Android 7) -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           + vd_chemins(monogramme(50, 54, 54), "#FF1E90FF") + "</vector>\n")

    # Logo complet en VectorDrawable (badge dégradé + monogramme), Android 7+ ; repli en calques
    ecrire(f"{RES}/drawable-v24/ibitube_logo.xml", XML +
           '<!-- iBiTube : logo complet en VectorDrawable (source : ibitube/logo/ibitube-logo.svg) -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="120dp" android:height="120dp"\n'
           '    android:viewportWidth="100" android:viewportHeight="100">\n'
           + vd_degrade([badge(0, 0, 100)], 0, 0, 100, 100)
           + vd_chemins(monogramme(64, 50, 50), "#FFFFFFFF") + "</vector>\n")
    ecrire(f"{RES}/drawable/ibitube_logo.xml", XML +
           '<!-- iBiTube : logo complet, repli avant Android 7 (forme dégradée + monogramme vectoriel) -->\n'
           '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <item>\n'
           '        <shape android:shape="rectangle">\n'
           '            <size android:width="120dp" android:height="120dp" />\n'
           '            <corners android:radius="27.6dp" />\n'
           f'            <gradient android:angle="315" android:startColor="{BLEU_A}" android:endColor="{BLEU_B}" />\n'
           '        </shape>\n'
           '    </item>\n'
           '    <item android:drawable="@drawable/ibitube_logo_monogram" />\n'
           '</layer-list>\n')
    ecrire(f"{RES}/drawable/ibitube_logo_monogram.xml", XML +
           '<!-- iBiTube : monogramme seul, à poser sur le badge dégradé -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="120dp" android:height="120dp"\n'
           '    android:viewportWidth="100" android:viewportHeight="100">\n'
           + vd_chemins(monogramme(64, 50, 50), "#FFFFFFFF") + "</vector>\n")

    # Logotype (« iBi » bleu, « Tube » blanc) : sert d'image de marque sur le splash Android 12+
    cap = 30
    _, L = logotype(cap, 0, 0)
    lw, lh = 200, 80
    morceaux, _ = logotype(cap, (lw - L) / 2, lh / 2 + cap / 2)
    ecrire(f"{RES}/drawable/ibitube_wordmark.xml", XML +
           '<!-- iBiTube : logotype (source : ibitube/logo/ibitube-wordmark.svg), 200 x 80 dp -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           f'    android:width="{lw}dp" android:height="{lh}dp"\n'
           f'    android:viewportWidth="{lw}" android:viewportHeight="{lh}">\n'
           + vd_chemins([morceaux[0][1]], "#FF1E90FF") + vd_chemins([morceaux[1][1]], "#FFFFFFFF") + "</vector>\n")

    # Icône animée du splash système (Android 12+) : fondu + léger agrandissement, 600 ms.
    # Zone utile : cercle de 192 dp dans 288 dp ; le badge (128 dp) y tient entièrement.
    c = 128
    o = (288 - c) / 2
    ecrire(f"{RES}/drawable-v31/ibitube_splash_icon.xml", XML +
           '<!-- iBiTube : icône animée de l\'écran de démarrage (Android 12+) : fondu + léger zoom, 600 ms.\n'
           '     Le système la joue tout seul pendant le lancement : aucune attente ajoutée. -->\n'
           '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt">\n'
           '    <aapt:attr name="android:drawable">\n'
           '        <vector android:name="logo"\n'
           '            android:width="288dp" android:height="288dp"\n'
           '            android:viewportWidth="288" android:viewportHeight="288">\n'
           '            <!-- État de repos = état final : si l\'animation ne démarre pas, le logo reste visible -->\n'
           '            <group android:name="zoom" android:pivotX="144" android:pivotY="144">\n'
           + vd_degrade([badge(o, o, c)], o, o, o + c, o + c, indent="                ")
           + vd_chemins(monogramme(c * 0.64, 144, 144), "#FFFFFFFF", indent="                ")
           + '            </group>\n'
           '        </vector>\n'
           '    </aapt:attr>\n'
           '    <target android:name="logo">\n'
           '        <aapt:attr name="android:animation">\n'
           '            <objectAnimator android:propertyName="alpha" android:valueFrom="0" android:valueTo="1"\n'
           '                android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '        </aapt:attr>\n'
           '    </target>\n'
           '    <target android:name="zoom">\n'
           '        <aapt:attr name="android:animation">\n'
           '            <set>\n'
           '                <objectAnimator android:propertyName="scaleX" android:valueFrom="0.86" android:valueTo="1"\n'
           '                    android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '                <objectAnimator android:propertyName="scaleY" android:valueFrom="0.86" android:valueTo="1"\n'
           '                    android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '            </set>\n'
           '        </aapt:attr>\n'
           '    </target>\n'
           '</animated-vector>\n')


# ---------- 3. pages HTML pour les PNG ----------
def pages_png():
    """Chaque entrée : (fichier PNG relatif à la sortie, svg, largeur px, hauteur px)."""
    os.makedirs(PNG, exist_ok=True)
    liste = []

    def ajoute(chemin, contenu_svg, l, h):
        nom = chemin.replace("/", "__") + ".html"
        page = ('<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent}'
                'svg{display:block;width:%dpx;height:%dpx}</style></head><body>%s</body></html>'
                % (l, h, contenu_svg.split("?>", 1)[1]))
        ecrire(os.path.join(PNG, nom), page)
        liste.append({"page": os.path.join(PNG, nom), "png": os.path.join(SORTIE, chemin), "l": l, "h": h})

    logo = open(f"{LOGO}/ibitube-logo.svg", encoding="utf-8").read()
    alt = svg(512, 512, '', '')  # placeholder remplacé ci-dessous
    d, c = logo_badge(0, 0, 512)
    dg = degrade_svg("a", 0, 0, 512, 512)
    alt = svg(512, 512, f'  <path fill="{FOND}" d="{badge(0, 0, 512)}"/>\n'
              + chemins_svg(monogramme(512 * 0.64, 256, 256), "url(#a)"), dg)

    # Icône classique (avant Android 8) : badge plein cadre, toutes densités + nodpi 320
    for dossier, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        ajoute(f"smarttubetv/src/ibitube/res/mipmap-{dossier}/app_icon.png", logo, px, px)
        ajoute(f"smarttubetv/src/ibitube/res/mipmap-{dossier}/app_icon_alt.png", alt, px, px)
    ajoute("smarttubetv/src/ibitube/res/mipmap-nodpi/app_icon.png", logo, 320, 320)
    ajoute("smarttubetv/src/ibitube/res/mipmap-nodpi/app_icon_alt.png", alt, 320, 320)

    # Bannière Android TV
    ajoute("smarttubetv/src/ibitube/res/mipmap-nodpi/app_banner.png",
           open(f"{LOGO}/ibitube-banner.svg", encoding="utf-8").read(), 320, 180)

    # Logo de la barre de titre (3 variantes de l'amont, une par jeu de couleurs)
    entete = open(f"{LOGO}/ibitube-lockup.svg", encoding="utf-8").read()
    for nom in ("app_logo", "app_logo_semi_red", "app_logo_semi_grey"):
        ajoute(f"smarttubetv/src/ibitube/res/mipmap-nodpi/{nom}.png", entete, 320, 180)

    # Logo de la chaîne sur l'écran d'accueil Android TV (remplace celui de leanbackassistant)
    ajoute("smarttubetv/src/ibitube/res/drawable/generic_channels.png", logo, 300, 300)

    # Splash avant Android 12 : logo 120 dp et logotype en xxhdpi (×3)
    ajoute("smarttubetv/src/ibitube/res/drawable-xxhdpi/ibitube_splash_logo.png", logo, 360, 360)
    cap = 54
    _, L = logotype(cap, 0, 0)
    morceaux, _ = logotype(cap, (480 - L) / 2, 60 + cap / 2)
    mot = svg(480, 120, f'  <path fill="{ACCENT}" d="{morceaux[0][1]}"/>\n  <path fill="{TEXTE}" d="{morceaux[1][1]}"/>\n')
    ajoute("smarttubetv/src/ibitube/res/drawable-xxhdpi/ibitube_splash_wordmark.png", mot, 480, 120)

    with open(os.path.join(PNG, "liste.json"), "w") as fh:
        json.dump(liste, fh, indent=1)
    return liste


if __name__ == "__main__":
    sources()
    android()
    l = pages_png()
    print(len(l), "PNG à produire")
