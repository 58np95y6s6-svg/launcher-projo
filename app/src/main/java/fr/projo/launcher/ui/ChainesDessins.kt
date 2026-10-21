package fr.projo.launcher.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/*
 * Design « Chaînes » : dessins vectoriels (horloge à segments, main-curseur, enveloppe,
 * flèches de page, bande du bas). Tout est tracé à la volée : aucune image à charger.
 */

// ------------------------------------------------------------------ palette

/** Fond de l'écran : blanc grisé, très légèrement bleuté. */
internal val ChainesFondHaut = Color(0xFFF7F8F9)
internal val ChainesFondBas = Color(0xFFE8EAEC)
/** Rayures horizontales très discrètes du fond. */
internal val ChainesRayure = Color(0xFF8A9096)
/** Liseré gris des tuiles et des boutons. */
internal val ChainesLisere = Color(0xFFBEC3C9)
/** Tuile vide. */
internal val ChainesVideHaut = Color(0xFFE6E8EB)
internal val ChainesVideBas = Color(0xFFD6D9DD)
/** Bande du bas. */
internal val ChainesBandeHaut = Color(0xFFDDE0E3)
internal val ChainesBandeBas = Color(0xFFC8CCD1)
/** Filet bleu clair qui borde la bande, et bleu de la sélection. */
internal val ChainesBleuFilet = Color(0xFF8CD5F3)
internal val ChainesBleu = Color(0xFF34BEED)
internal val ChainesBleuLueur = Color(0xFF5FCDF6)
/** Gris des chiffres, des textes et des icônes. */
internal val ChainesGris = Color(0xFF8C9298)
internal val ChainesGrisFonce = Color(0xFF5F656B)
internal val ChainesEncre = Color(0xFF3A3F45)

// ------------------------------------------------------------------ horloge à segments

/** Segments allumés de chaque chiffre (a haut, b haut droite, c bas droite, d bas, e bas gauche, f haut gauche, g milieu). */
private val SEGMENTS = arrayOf(
    "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg",
)

/** Largeur d'un caractère de l'horloge, en fraction de sa hauteur (chiffre ou deux-points). */
internal fun largeurCaractereHorloge(c: Char): Float = if (c == ':') 0.3f else 0.56f

/** Espace entre deux caractères, en fraction de la hauteur. */
internal const val ECART_HORLOGE = 0.16f

/** Largeur totale de [texte] dessiné à la hauteur [h]. */
internal fun largeurHorloge(texte: String, h: Float): Float =
    texte.sumOf { largeurCaractereHorloge(it).toDouble() }.toFloat() * h + ECART_HORLOGE * h * (texte.length - 1)

/**
 * Heure façon afficheur à segments aux bouts arrondis, gris, avec un léger relief
 * (copie blanche décalée vers le bas). [texte] : chiffres et « : ».
 */
internal fun DrawScope.horlogeSegments(texte: String, origine: Offset, h: Float, couleur: Color, relief: Color) {
    val e = h * 0.13f
    for ((dy, c) in listOf(e * 0.22f to relief, 0f to couleur)) {
        var x = origine.x
        for (ch in texte) {
            val l = largeurCaractereHorloge(ch) * h
            if (ch == ':') {
                val cx = x + l / 2f
                drawCircle(c, e * 0.62f, Offset(cx, origine.y + h * 0.32f + dy))
                drawCircle(c, e * 0.62f, Offset(cx, origine.y + h * 0.72f + dy))
            } else {
                chiffre(ch - '0', x, origine.y + dy, l, h, e, c)
            }
            x += l + ECART_HORLOGE * h
        }
    }
}

private fun DrawScope.chiffre(n: Int, x: Float, y: Float, l: Float, h: Float, e: Float, c: Color) {
    val s = SEGMENTS.getOrNull(n) ?: return
    // Segments un peu écartés les uns des autres : bouts arrondis bien séparés
    val g = x + e / 2f
    val d = x + l - e / 2f
    val haut = y + e / 2f
    val milieu = y + h / 2f
    val bas = y + h - e / 2f
    val jeu = e * 0.95f
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = e, cap = StrokeCap.Round)
    for (k in s) when (k) {
        'a' -> seg(g + jeu, haut, d - jeu, haut)
        'g' -> seg(g + jeu, milieu, d - jeu, milieu)
        'd' -> seg(g + jeu, bas, d - jeu, bas)
        'f' -> seg(g, haut + jeu, g, milieu - jeu)
        'b' -> seg(d, haut + jeu, d, milieu - jeu)
        'e' -> seg(g, milieu + jeu, g, bas - jeu)
        'c' -> seg(d, milieu + jeu, d, bas - jeu)
    }
}

// ------------------------------------------------------------------ main-curseur

/**
 * Petite main blanche qui pointe vers le haut à gauche, liseré gris (la silhouette est
 * tracée deux fois : en gris un peu plus grosse, puis en blanc par-dessus).
 * [taille] : hauteur de la main ; [pointe] : bout de l'index.
 */
internal fun DrawScope.mainCurseur(pointe: Offset, taille: Float) {
    val k = taille / 30f
    rotate(-18f, pivot = pointe) {
        val o = pointe
        fun parts(grossi: Float, c: Color) {
            fun rr(x: Float, y: Float, w: Float, hh: Float, r: Float) = drawRoundRect(
                c, Offset(o.x + (x - grossi) * k, o.y + (y - grossi) * k), Size((w + 2 * grossi) * k, (hh + 2 * grossi) * k),
                CornerRadius((r + grossi) * k),
            )
            // Index tendu
            rr(-3.2f, 0f, 6.4f, 16f, 3.2f)
            // Doigts repliés
            rr(2.6f, 9.5f, 6f, 9f, 3f)
            rr(7.6f, 11f, 5.6f, 8.5f, 2.8f)
            // Paume
            rr(-3.6f, 12.5f, 16.2f, 14.5f, 5.5f)
            // Pouce
            rr(-9.2f, 13.5f, 9f, 5.6f, 2.8f)
        }
        // Ombre douce sous la main
        translate(1.2f * k, 1.8f * k) { parts(1.6f, Color.Black.copy(alpha = 0.10f)) }
        parts(1.4f, ChainesGrisFonce)
        parts(0f, Color.White)
        // Plis des doigts
        val pli = ChainesLisere
        drawLine(pli, Offset(o.x + 3f * k, o.y + 13f * k), Offset(o.x + 3f * k, o.y + 17.5f * k), strokeWidth = 1.1f * k, cap = StrokeCap.Round)
        drawLine(pli, Offset(o.x + 8f * k, o.y + 14f * k), Offset(o.x + 8f * k, o.y + 18f * k), strokeWidth = 1.1f * k, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------ enveloppe

/** Enveloppe grise pleine, rabat en V blanc, centrée sur [centre], de largeur [l]. */
internal fun DrawScope.enveloppe(centre: Offset, l: Float, couleur: Color) {
    val h = l * 0.68f
    val g = centre.x - l / 2f
    val t = centre.y - h / 2f
    drawRoundRect(couleur, Offset(g, t), Size(l, h), CornerRadius(l * 0.1f))
    val v = Path().apply {
        moveTo(g + l * 0.08f, t + h * 0.14f)
        lineTo(centre.x, t + h * 0.58f)
        lineTo(g + l * 0.92f, t + h * 0.14f)
    }
    drawPath(v, Color.White.copy(alpha = 0.9f), style = Stroke(width = l * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ------------------------------------------------------------------ flèche de page

/** Triangle bleu aux coins arrondis, pointe vers la droite ([droite]) ou la gauche. */
internal fun DrawScope.flechePage(droite: Boolean) {
    val w = size.width * 0.42f
    val h = size.height * 0.36f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val sx = if (droite) 1f else -1f
    val p = Path().apply {
        moveTo(cx - sx * w * 0.42f, cy - h / 2f)
        lineTo(cx + sx * w * 0.58f, cy)
        lineTo(cx - sx * w * 0.42f, cy + h / 2f)
        close()
    }
    val bord = Stroke(width = w * 0.2f, join = StrokeJoin.Round)
    drawPath(p, ChainesBleu, style = bord)
    drawPath(p, ChainesBleu)
    // Reflet clair sur la moitié haute
    val reflet = Path().apply {
        moveTo(cx - sx * w * 0.3f, cy - h * 0.3f)
        lineTo(cx + sx * w * 0.3f, cy - h * 0.02f)
    }
    drawPath(reflet, Color.White.copy(alpha = 0.55f), style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
}

// ------------------------------------------------------------------ bande du bas

/**
 * Contour de la bande du bas : bord haut droit sur les côtés, creusé au centre en
 * cuvette arrondie (l'heure se pose au-dessus du creux).
 */
internal fun contourBande(w: Float, h: Float, bord: Float, creux: Float, demiFond: Float, raccord: Float): Path {
    val cx = w / 2f
    return Path().apply {
        moveTo(0f, bord)
        lineTo(cx - demiFond - raccord, bord)
        cubicTo(cx - demiFond - raccord * 0.45f, bord, cx - demiFond - raccord * 0.5f, creux, cx - demiFond, creux)
        lineTo(cx + demiFond, creux)
        cubicTo(cx + demiFond + raccord * 0.5f, creux, cx + demiFond + raccord * 0.45f, bord, cx + demiFond + raccord, bord)
        lineTo(w, bord)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
}

/** Le seul bord haut de [contourBande] (pour le filet bleu). */
internal fun filetBande(w: Float, bord: Float, creux: Float, demiFond: Float, raccord: Float): Path {
    val cx = w / 2f
    return Path().apply {
        moveTo(0f, bord)
        lineTo(cx - demiFond - raccord, bord)
        cubicTo(cx - demiFond - raccord * 0.45f, bord, cx - demiFond - raccord * 0.5f, creux, cx - demiFond, creux)
        lineTo(cx + demiFond, creux)
        cubicTo(cx + demiFond + raccord * 0.5f, creux, cx + demiFond + raccord * 0.45f, bord, cx + demiFond + raccord, bord)
        lineTo(w, bord)
    }
}
