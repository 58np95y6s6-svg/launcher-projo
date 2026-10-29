"""Génère l'identité iBiTube : le mot « iBiTube » (« iBi » bleu, « Tube » blanc) sur fond nuit, et les
vagues bleues. Produit les sources SVG, les VectorDrawable Android et les pages HTML à photographier
pour les PNG (rendre_png.js).

Usage : python3 gen.py <dossier_sortie>
  <sortie>/ibitube/logo/*.svg                 sources vectorielles
  <sortie>/smarttubetv/src/ibitube/res/...    VectorDrawable / AVD
  <sortie>/_png/*.html + liste.json           pages pour les PNG
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geo import ACCENT, FOND, SURFACE, TEXTE, logotype  # noqa: E402
from vagues import OPACITES_DISCRETES, OPACITES_FORTES, TEINTES, vagues, vagues_svg  # noqa: E402

SORTIE = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "out")
RES = os.path.join(SORTIE, "smarttubetv/src/ibitube/res")
LOGO = os.path.join(SORTIE, "ibitube/logo")
PNG = os.path.join(SORTIE, "_png")
XML = '<?xml version="1.0" encoding="utf-8"?>\n'


def ecrire(chemin, texte):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8") as fh:
        fh.write(texte)


def svg(l, h, corps):
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<!-- iBiTube : le mot « iBiTube », « iBi » en #1E90FF et « Tube » en blanc, sur fond #0A1020.\n'
            '     Police Outfit Bold (SIL Open Font License 1.1) convertie en tracés. -->\n'
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{l:g}" height="{h:g}" viewBox="0 0 {l:g} {h:g}">\n'
            + corps + "</svg>\n")


def mot_svg(largeur, cx, cy):
    a, b, _ = logotype(largeur, cx, cy)
    return f'  <path fill="{ACCENT}" d="{a}"/>\n  <path fill="{TEXTE}" d="{b}"/>\n'


def rect_arrondi(l, h, r, couleur):
    return f'  <rect width="{l:g}" height="{h:g}" rx="{r:g}" fill="{couleur}"/>\n'


# ---------- dessins (SVG) ----------
def icone(cote=512, fond=FOND):
    return svg(cote, cote, rect_arrondi(cote, cote, cote * 0.22, fond) + mot_svg(cote * 0.80, cote / 2, cote / 2))


def banniere():
    return svg(320, 180, rect_arrondi(320, 180, 0, FOND) + mot_svg(244, 160, 90))


def entete():
    # Logo de la barre de titre (badge de SmartTube) : le mot seul, fond transparent
    return svg(480, 120, mot_svg(440, 240, 60))


def mot_splash():
    return svg(780, 240, mot_svg(720, 390, 120))


def splash():
    return svg(1920, 1080, rect_arrondi(1920, 1080, 0, FOND) + vagues_svg(0, 600, 1920, 480, fortes=True)
               + mot_svg(720, 960, 470))


def sources():
    ecrire(f"{LOGO}/ibitube-wordmark.svg", svg(1000, 300, mot_svg(960, 500, 150)))
    ecrire(f"{LOGO}/ibitube-icon.svg", icone())
    ecrire(f"{LOGO}/ibitube-icon-foreground.svg", svg(108, 108, mot_svg(62, 54, 54)))
    ecrire(f"{LOGO}/ibitube-banner.svg", banniere())
    ecrire(f"{LOGO}/ibitube-header.svg", entete())
    ecrire(f"{LOGO}/ibitube-splash.svg", splash())
    ecrire(f"{LOGO}/ibitube-vagues.svg", svg(1920, 1080, vagues_svg(0, 0, 1920, 1080, fortes=True)))
    ecrire(f"{LOGO}/ibitube-vagues-discretes.svg",
           svg(1920, 1080, rect_arrondi(1920, 1080, 0, FOND) + vagues_svg(0, 520, 1920, 560, fortes=False)))


# ---------- ressources Android ----------
def vd_mot(largeur, cx, cy, indent="    "):
    a, b, _ = logotype(largeur, cx, cy)
    return (f'{indent}<path\n{indent}    android:fillColor="#FF1E90FF"\n{indent}    android:pathData="{a}" />\n'
            f'{indent}<path\n{indent}    android:fillColor="#FFFFFFFF"\n{indent}    android:pathData="{b}" />\n')


def android():
    ecrire(f"{RES}/drawable/ibitube_icon_foreground.xml", XML +
           '<!-- iBiTube : premier plan de l\'icône adaptative, le mot dans la zone sûre (66 dp).\n'
           '     Source : ibitube/logo/ibitube-icon-foreground.svg -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           + vd_mot(62, 54, 54) + "</vector>\n")

    ecrire(f"{RES}/drawable/ibitube_wordmark.xml", XML +
           '<!-- iBiTube : le mot « iBiTube » (source : ibitube/logo/ibitube-wordmark.svg), 200 x 80 dp -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="200dp" android:height="80dp"\n'
           '    android:viewportWidth="200" android:viewportHeight="80">\n'
           + vd_mot(184, 100, 40) + "</vector>\n")

    def vd_vagues(nom, y0, h, op, commentaire):
        corps = "".join(f'    <path\n        android:fillColor="{TEINTES[n]}"\n        android:fillAlpha="{op[n]}"\n'
                        f'        android:pathData="{d}" />\n' for n, d in vagues(0, y0, 1920, h))
        ecrire(f"{RES}/drawable/{nom}.xml", XML +
               f'<!-- iBiTube : {commentaire}. Vecteur 16:9 étiré par Android sur toute la fenêtre ;\n'
               '     taille intrinsèque réduite (192 x 108 dp), utilisée seulement par la conversion PNG des\n'
               '     très vieux Android. Source : ibitube/logo/outils/vagues.py -->\n'
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="192dp" android:height="108dp"\n'
               '    android:viewportWidth="1920" android:viewportHeight="1080">\n' + corps + "</vector>\n")

    vd_vagues("ibitube_vagues_fortes", 600, 480, OPACITES_FORTES, "vagues franches du bas de l'écran de démarrage")
    vd_vagues("ibitube_vagues_discretes", 520, 560, OPACITES_DISCRETES, "vagues discrètes du fond de l'accueil")

    # Icône animée du splash système (Android 12+) : le mot, fondu + léger zoom, 600 ms.
    # Zone visible : cercle de 192 dp au centre de 288 dp ; le mot (180 dp de large) y tient.
    ecrire(f"{RES}/drawable-v31/ibitube_splash_icon.xml", XML +
           '<!-- iBiTube : icône animée de l\'écran de démarrage (Android 12+) : le mot « iBiTube »,\n'
           '     fondu + léger zoom, 600 ms. Le système la joue pendant le lancement : aucune attente ajoutée. -->\n'
           '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt">\n'
           '    <aapt:attr name="android:drawable">\n'
           '        <vector android:name="mot"\n'
           '            android:width="288dp" android:height="288dp"\n'
           '            android:viewportWidth="288" android:viewportHeight="288">\n'
           '            <!-- État de repos = état final : si l\'animation ne démarre pas, le mot reste visible -->\n'
           '            <group android:name="zoom" android:pivotX="144" android:pivotY="144">\n'
           + vd_mot(180, 144, 144, indent="                ")
           + '            </group>\n'
           '        </vector>\n'
           '    </aapt:attr>\n'
           '    <target android:name="mot">\n'
           '        <aapt:attr name="android:animation">\n'
           '            <objectAnimator android:propertyName="alpha" android:valueFrom="0" android:valueTo="1"\n'
           '                android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '        </aapt:attr>\n'
           '    </target>\n'
           '    <target android:name="zoom">\n'
           '        <aapt:attr name="android:animation">\n'
           '            <set>\n'
           '                <objectAnimator android:propertyName="scaleX" android:valueFrom="0.88" android:valueTo="1"\n'
           '                    android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '                <objectAnimator android:propertyName="scaleY" android:valueFrom="0.88" android:valueTo="1"\n'
           '                    android:duration="600" android:interpolator="@android:interpolator/decelerate_cubic" />\n'
           '            </set>\n'
           '        </aapt:attr>\n'
           '    </target>\n'
           '</animated-vector>\n')


# ---------- PNG ----------
def pages_png():
    os.makedirs(PNG, exist_ok=True)
    liste = []

    def ajoute(chemin, contenu, l, h):
        nom = chemin.replace("/", "__") + ".html"
        page = ('<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent}'
                'svg{display:block;width:%dpx;height:%dpx}</style></head><body>%s</body></html>'
                % (l, h, contenu.split("?>", 1)[1]))
        ecrire(os.path.join(PNG, nom), page)
        liste.append({"page": os.path.join(PNG, nom), "png": os.path.join(SORTIE, chemin), "l": l, "h": h})

    base = "smarttubetv/src/ibitube/res"
    for dossier, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        ajoute(f"{base}/mipmap-{dossier}/app_icon.png", icone(), px, px)
        ajoute(f"{base}/mipmap-{dossier}/app_icon_alt.png", icone(fond=SURFACE), px, px)
    ajoute(f"{base}/mipmap-nodpi/app_icon.png", icone(), 320, 320)
    ajoute(f"{base}/mipmap-nodpi/app_icon_alt.png", icone(fond=SURFACE), 320, 320)
    ajoute(f"{base}/mipmap-nodpi/app_banner.png", banniere(), 320, 180)
    for nom in ("app_logo", "app_logo_semi_red", "app_logo_semi_grey"):
        ajoute(f"{base}/mipmap-nodpi/{nom}.png", entete(), 480, 120)
    ajoute(f"{base}/drawable/generic_channels.png", icone(), 300, 300)
    ajoute(f"{base}/drawable-xxhdpi/ibitube_splash_wordmark.png", mot_splash(), 780, 240)
    with open(os.path.join(PNG, "liste.json"), "w") as fh:
        json.dump(liste, fh, indent=1)
    return liste


if __name__ == "__main__":
    sources()
    android()
    print(len(pages_png()), "PNG à produire")
