"""Vagues iBiTube : trois couches superposées (fond, milieu, avant), inspirées de l'image de
référence de l'utilisateur. Chaque couche est une courbe lisse (Catmull-Rom → Bézier cubiques),
fermée vers le bas du cadre. Tracés en coordonnées absolues : SVG et VectorDrawable.
"""
from geo import f

# Points de passage (x, y) en fractions du cadre, de gauche à droite.
# Couche du fond : la plus haute, remonte fortement à droite (comme la référence).
COUCHES = {
    "fond": [(-0.05, 0.50), (0.18, 0.40), (0.42, 0.58), (0.66, 0.46), (0.86, 0.22), (1.05, 0.12)],
    "milieu": [(-0.05, 0.70), (0.20, 0.58), (0.46, 0.72), (0.72, 0.70), (0.90, 0.58), (1.05, 0.56)],
    "avant": [(-0.05, 0.82), (0.24, 0.76), (0.52, 0.86), (0.78, 0.80), (1.05, 0.74)],
}

# Couleurs sur fond nuit (#0A1020) : vagues franches (splash, bannière) et discrètes (écrans).
TEINTES = {"fond": "#1E6BFF", "milieu": "#1E90FF", "avant": "#7CC2FF"}
OPACITES_FORTES = {"fond": 0.85, "milieu": 0.62, "avant": 0.45}
OPACITES_DISCRETES = {"fond": 0.26, "milieu": 0.19, "avant": 0.13}


def courbe(points, x0, y0, l, h):
    """Bord supérieur lisse passant par les points, puis fermeture par le bas du cadre."""
    P = [(x0 + px * l, y0 + py * h) for px, py in points]
    d = "M%s,%s" % (f(P[0][0]), f(P[0][1]))
    for i in range(len(P) - 1):
        p0 = P[i - 1] if i > 0 else P[i]
        p1, p2 = P[i], P[i + 1]
        p3 = P[i + 2] if i + 2 < len(P) else P[i + 1]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += "C%s,%s %s,%s %s,%s" % (f(c1[0]), f(c1[1]), f(c2[0]), f(c2[1]), f(p2[0]), f(p2[1]))
    d += "L%s,%sL%s,%sZ" % (f(P[-1][0]), f(y0 + h), f(P[0][0]), f(y0 + h))
    return d


def vagues(x0, y0, l, h):
    """[(nom, tracé)] du fond vers l'avant, dans le rectangle (x0, y0, l, h)."""
    return [(nom, courbe(pts, x0, y0, l, h)) for nom, pts in COUCHES.items()]


def vagues_svg(x0, y0, l, h, fortes=True, indent="  "):
    op = OPACITES_FORTES if fortes else OPACITES_DISCRETES
    return "".join(f'{indent}<path fill="{TEINTES[n]}" fill-opacity="{op[n]}" d="{d}"/>\n'
                   for n, d in vagues(x0, y0, l, h))
