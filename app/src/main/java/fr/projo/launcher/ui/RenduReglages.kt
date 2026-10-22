package fr.projo.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Dessin du panneau Réglages d'après le style du design (StylesReglages.kt). Aucune logique
 * ici : les tuiles, leurs actions et le focus viennent de SettingsPanel.kt (PanneauReglages).
 * Chaque tuile pose p.comportement(...) sur son élément focalisable.
 */

// ------------------------------------------------------------------ unités

/** Tailles fractionnaires, à la même échelle que Int.u / Int.us. */
private val Float.uu: Dp @Composable get() = (this * LocalScale.current).dp
private val Float.uss: TextUnit @Composable get() = (this * LocalScale.current).sp

/** Texte à l'échelle de l'interface, interligne adapté à sa taille (jamais de lignes qui se chevauchent). */
@Composable
private fun T(
    texte: String,
    taille: Float,
    couleur: Color,
    modifier: Modifier = Modifier,
    poids: FontWeight? = null,
    espacement: Float = 0f,
    police: FontFamily? = null,
    alignement: TextAlign? = null,
    interligne: Float = 1.22f,
) {
    Text(
        texte, modifier, color = couleur, fontSize = taille.uss, fontWeight = poids, fontFamily = police,
        letterSpacing = espacement.uss, lineHeight = (taille * interligne).uss, textAlign = alignement,
    )
}

/** Teinte d'un réglage pour ce style. */
private fun StyleReglages.teinte(r: Reglage): Color = teintes[r.cle] ?: r.teinte

/** Teinte « Soir » : luminance plafonnée à 0,07 en gardant la teinte (carrés d'icônes de « Une »). */
private fun soir(c: Color): Color {
    fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    fun srgb(v: Float) = (if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f).coerceIn(0f, 1f)
    val r = lin(c.red)
    val g = lin(c.green)
    val b = lin(c.blue)
    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
    if (y <= 0.07f) return c
    val k = 0.07f / y
    return Color(srgb(r * k), srgb(g * k), srgb(b * k), c.alpha)
}

private fun brosse(couleurs: List<Color>): Brush =
    if (couleurs.size == 1) androidx.compose.ui.graphics.SolidColor(couleurs[0]) else Brush.verticalGradient(couleurs)

// ------------------------------------------------------------------ panneau

/** Le panneau entier : voile, position, matière, animation, piège du focus, ◀ pour fermer. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PanneauStyle(style: StyleReglages, p: PanneauReglages, gaucheFerme: () -> Boolean, fermer: () -> Unit) {
    val s = style
    val m = s.mesures
    val c = s.couleurs
    val ouverture = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ouverture.animateTo(1f, tween(s.ouverture.duree, easing = FastOutSlowInEasing)) }
    val forme = formePanneau(s)
    val unite = 1.u

    // Pointeur : toucher le voile (hors du panneau) ferme le panneau, comme Retour
    Box(Modifier.fillMaxSize().drawBehind { drawRect(c.voile, alpha = ouverture.value) }.cliquableSansFocus(fermer)) {
        val place = when (s.disposition) {
            Disposition.FEUILLE_DROITE -> Modifier.align(Alignment.CenterEnd)
                .padding(top = m.haut.u, bottom = m.bas.u, end = m.cote.u).width(m.largeur.u).fillMaxHeight()
            Disposition.FEUILLE_GAUCHE -> Modifier.align(Alignment.CenterStart)
                .padding(top = m.haut.u, bottom = m.bas.u, start = m.cote.u).width(m.largeur.u).fillMaxHeight()
            Disposition.FENETRE_CENTREE -> Modifier.align(Alignment.TopCenter)
                .padding(top = m.haut.u, bottom = m.bas.u).width(m.largeur.u).fillMaxHeight()
            Disposition.FEUILLE_BAS -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(m.hauteur.u)
            Disposition.PLEIN_ECRAN -> Modifier.fillMaxSize()
        }
        Column(
            place
                .graphicsLayer {
                    val a = ouverture.value
                    val d = 1f - a
                    alpha = a
                    when (s.ouverture) {
                        Ouverture.GLISSE_DROITE -> translationX =
                            d * 40f * unite.toPx() * (if (s.disposition == Disposition.FEUILLE_GAUCHE) -1f else 1f)
                        Ouverture.GLISSE_BAS -> translationY = d * 30f * unite.toPx()
                        Ouverture.ZOOM -> { scaleX = 0.94f + 0.06f * a; scaleY = scaleX }
                        Ouverture.FONDU -> { scaleX = 1f + 0.03f * d; scaleY = scaleX }
                    }
                }
                .then(if (s.ombre > 0) Modifier.shadow(s.ombre.u, forme, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .clip(forme)
                .drawBehind { matiere(s, unite.toPx()) }
                // Le focus ne peut pas s'échapper vers l'accueil caché derrière
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                // ◀ ferme le panneau depuis la colonne de gauche (sauf pendant le réglage du volume,
                // qui consomme la touche avant)
                .onKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown && gaucheFerme()) {
                        fermer()
                        true
                    } else {
                        false
                    }
                }
                // Un clic sur une partie vide du panneau ne le ferme pas (ni n'atteint l'accueil)
                .retenirClics()
                .testTag("reglages:panneau"),
        ) {
            EnTeteReglages(s, p, fermer)
            CorpsReglages(s, p, Modifier.weight(1f))
            PiedReglages(s, p, fermer)
        }
        // Verre : poignée et point de fermeture sous la fenêtre, façon visionOS
        if (s.disposition == Disposition.FENETRE_CENTREE && s.reflet) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (m.bas - 24).coerceAtLeast(4).u)
                    .graphicsLayer { alpha = ouverture.value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.u).background(Color.White.copy(alpha = 0.34f), CircleShape))
                Spacer(Modifier.width(7.u))
                Box(Modifier.width(85.u).height(6.u).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.u)))
            }
        }
    }
}

@Composable
private fun formePanneau(s: StyleReglages): Shape {
    val m = s.mesures
    val r = m.rayon.u
    return when {
        m.rayon == 0 -> RectangleShape
        s.disposition == Disposition.FEUILLE_DROITE && m.cote == 0 -> RoundedCornerShape(topStart = r, bottomStart = r)
        s.disposition == Disposition.FEUILLE_GAUCHE && m.cote == 0 -> RoundedCornerShape(topEnd = r, bottomEnd = r)
        s.disposition == Disposition.FEUILLE_BAS -> RoundedCornerShape(topStart = r, topEnd = r)
        s.disposition == Disposition.PLEIN_ECRAN -> RectangleShape
        else -> RoundedCornerShape(r)
    }
}

/** Matière du panneau : fond, reflet, liseré, verre clair, rayures. Aucun flou. */
private fun DrawScope.matiere(s: StyleReglages, u: Float) {
    val c = s.couleurs
    val w = size.width
    val h = size.height
    if (s.fonduHaut > 0) {
        // Le haut du panneau se fond dans l'accueil (feuille sans bord net)
        val f = (s.fonduHaut * u / h).coerceIn(0f, 0.9f)
        drawRect(Brush.verticalGradient(0f to c.fond.first().copy(alpha = 0f), f to c.fond.first(), 1f to c.fond.last()))
    } else {
        drawRect(brosse(c.fond))
    }
    if (s.rayures) {
        // Fines rayures horizontales, comme le fond de l'accueil Chaînes
        var y = 0f
        while (y < h) {
            drawRect(ChainesRayure.copy(alpha = 0.045f), topLeft = Offset(0f, y), size = Size(w, u))
            y += 4f * u
        }
    }
    if (s.reflet) {
        // Verre : reflet en haut à gauche et voile blanc dégressif, puis liseré lumineux
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.15f, 0f), radius = w * 0.7f))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), endY = h * 0.5f))
        val e = 1.5f * u
        val r = s.mesures.rayon * u
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.34f)),
                start = Offset.Zero, end = Offset(w, h),
            ),
            topLeft = Offset(e / 2, e / 2), size = Size(w - e, h - e), cornerRadius = CornerRadius(r - e / 2), style = Stroke(e),
        )
    }
    if (s.verreClair) {
        // Studio : lumière blanche en haut à gauche, reflet froid en bas à droite (comme le fond
        // de l'accueil), puis liseré lumineux (bord haut plus clair) et contour gris très fin
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = Offset(w * 0.2f, 0f), radius = w * 0.9f))
        drawRect(Brush.radialGradient(listOf(Color(0xFFD7DCE6).copy(alpha = 0.6f), Color(0xFFD7DCE6).copy(alpha = 0f)), center = Offset(w, h), radius = w * 0.9f))
        val r = s.mesures.rayon * u
        drawRoundRect(
            Brush.verticalGradient(0f to Color.White, 0.5f to Color.White.copy(alpha = 0.35f), 1f to Color.White.copy(alpha = 0.7f)),
            topLeft = Offset(u, u), size = Size(w - 2 * u, h - 2 * u), cornerRadius = CornerRadius(r - u), style = Stroke(1.2f * u),
        )
        drawRoundRect(StudioContour, topLeft = Offset(u / 2, u / 2), size = Size(w - u, h - u), cornerRadius = CornerRadius(r), style = Stroke(u))
    }
}

// ------------------------------------------------------------------ en-têtes

@Composable
private fun EnTeteReglages(s: StyleReglages, p: PanneauReglages, fermer: () -> Unit) {
    val c = s.couleurs
    val m = s.mesures
    val ty = s.typo
    when (s.enTete) {
        EnTete.SIMPLE -> Column(Modifier.padding(start = (m.marge + 4).u, end = m.marge.u, top = 26.u, bottom = 8.u)) {
            T("Réglages", ty.enTete.toFloat(), c.texte, poids = FontWeight.Bold, espacement = -0.4f)
            T("Projo ${p.version}", 12f, c.texteDiscret, Modifier.padding(top = 2.u), poids = FontWeight.Medium)
        }
        EnTete.CENTRE -> Box(
            Modifier.fillMaxWidth().padding(start = m.marge.u, end = m.marge.u, top = 16.u, bottom = 4.u).height(30.u),
        ) {
            BoutonFermer(30, c.pastille, c.texte, fermer, Modifier.align(Alignment.CenterStart))
            T("Réglages", ty.enTete.toFloat(), c.texte, Modifier.align(Alignment.Center), poids = FontWeight.Bold, espacement = -0.3f)
        }
        EnTete.TITRE_FERMER -> Row(
            Modifier.fillMaxWidth().padding(start = (m.marge + 4).u, end = (m.marge + 2).u, top = 20.u, bottom = 10.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("Réglages", ty.enTete.toFloat(), c.texte, Modifier.weight(1f), poids = FontWeight.Bold, espacement = -0.6f)
            BoutonFermer(32, Color(0xFFE9E1EE), c.texte, fermer)
        }
        EnTete.AIDES -> Row(
            Modifier.fillMaxWidth().padding(start = m.marge.u, end = m.marge.u, top = 30.u, bottom = 8.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("Réglages", ty.enTete.toFloat(), c.texte, poids = FontWeight.ExtraLight, espacement = -1f, interligne = 1.1f)
            Spacer(Modifier.weight(1f))
            AidesTelecommande(s, p, FormeTouche.ROND)
        }
        EnTete.AFFICHE -> AfficheReglages(s, p)
        EnTete.BANDEAU -> BandeauReglages(s, p)
    }
}

/** Bouton × : décoratif à la télécommande (Retour ferme le panneau), il ferme au clic ou au toucher. */
@Composable
private fun BoutonFermer(taille: Int, fond: Color, encre: Color, fermer: () -> Unit, modifier: Modifier = Modifier) {
    val unite = 1.u
    Box(
        modifier.testTag("reglages:fermer").cliquableSansFocus(fermer).size(taille.u).background(fond, CircleShape).drawBehind {
            val k = unite.toPx()
            val d = 4.5f * k
            val cx = size.width / 2
            val cy = size.height / 2
            val trait = Stroke(width = 1.6f * k, cap = StrokeCap.Round)
            drawLine(encre.copy(alpha = 0.85f), Offset(cx - d, cy - d), Offset(cx + d, cy + d), trait.width, StrokeCap.Round)
            drawLine(encre.copy(alpha = 0.85f), Offset(cx + d, cy - d), Offset(cx - d, cy + d), trait.width, StrokeCap.Round)
        },
    )
}

private enum class FormeTouche { ROND, CONTOUR }

/** Aides de la télécommande : ce que font les touches ici (en-tête Console, affiche Une). */
@Composable
private fun AidesTelecommande(s: StyleReglages, p: PanneauReglages, forme: FormeTouche, taille: Float = 11f) {
    val sel = p.selection
    val aides = when {
        sel?.curseur == true && sel.enReglage -> listOf("◀▶" to "Régler", "OK" to "Valider", "↩" to "Retour")
        sel?.curseur == true -> listOf("OK" to "Régler", "▲▼" to "Naviguer", "↩" to "Fermer")
        s.colonnes > 1 -> listOf("◀▶" to "Parcourir", "OK" to "Ouvrir", "↩" to "Fermer")
        else -> listOf("▲▼" to "Naviguer", "OK" to "Ouvrir", "↩" to "Fermer")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((taille * 1.3f).uu)) {
        for ((touche, libelle) in aides) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Touche(touche, s, forme, taille)
                Spacer(Modifier.width((taille * 0.55f).uu))
                T(libelle, taille, if (forme == FormeTouche.CONTOUR) s.couleurs.texte else s.couleurs.texteDiscret,
                    poids = if (forme == FormeTouche.CONTOUR) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Touche(texte: String, s: StyleReglages, forme: FormeTouche, taille: Float) {
    val c = s.couleurs
    val h = (taille * 1.65f).uu
    val mod = when (forme) {
        FormeTouche.ROND -> Modifier.border(1.25f.uu, c.texte.copy(alpha = 0.55f), CircleShape)
        FormeTouche.CONTOUR -> Modifier.border(1.5f.uu, c.texte, RoundedCornerShape(4.u))
    }
    Box(mod.heightIn(min = h).width(IntrinsicSize.Max).padding(horizontal = (taille * 0.45f).uu), contentAlignment = Alignment.Center) {
        if (texte == "↩") {
            // Le caractère ↩ est minuscule dans certaines polices d'Android : icône dessinée
            Icon(IconeRetour, null, Modifier.size((taille * 1.05f).uu), tint = c.texte)
        } else {
            T(texte, taille * 0.8f, c.texte, poids = FontWeight.Bold)
        }
    }
}

/** Touche Retour (icône Material « keyboard_return »). */
private val IconeRetour: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString("M19,7v4H5.83l3.58,-3.59L8,6l-6,6 6,6 1.41,-1.41L5.83,13H21V7z").toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.White),
        )
        .build()
}

/**
 * « Une » : aplat de la couleur du réglage sélectionné (luminance du soir), compteur, aides,
 * nom géant du réglage, le suivant en contour, et sa valeur (les thèmes pour Design).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AfficheReglages(s: StyleReglages, p: PanneauReglages) {
    val c = s.couleurs
    val m = s.mesures
    val r = p.selection ?: p.reglages.first()
    val aplat = uneAplat(s.teinte(r), soir = true)
    val fond by animateColorAsState(aplat.fond, tween(280), label = "aplat")
    val geant by animateColorAsState(aplat.geant, tween(280), label = "geant")
    val i = p.reglages.indexOf(r)
    val suivant = p.reglages.getOrNull(i + 1)
    Column(Modifier.fillMaxWidth().height(239.u).drawBehind { drawRect(fond) }.padding(horizontal = m.marge.u)) {
        Row(Modifier.padding(top = 17.u).height(30.u), verticalAlignment = Alignment.CenterVertically) {
            Icon(ProjoIcons.Reglages, null, Modifier.size(14.u), tint = Color.White)
            Spacer(Modifier.width(8.u))
            T("RÉGLAGES", 13f, Color.White, poids = FontWeight.Bold, espacement = 2.2f)
            Spacer(Modifier.width(14.u))
            T(String.format(Locale.FRENCH, "%02d", p.rang), 19f, Color.White, poids = FontWeight.ExtraBold)
            T(String.format(Locale.FRENCH, " / %02d", p.reglages.size), 19f, Color.White.copy(alpha = 0.6f), poids = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            AidesTelecommande(s, p, FormeTouche.CONTOUR, 13f)
        }
        Box(Modifier.padding(top = 8.u).fillMaxWidth().height(1.25f.uu).background(Color.White.copy(alpha = 0.28f)))
        val nom = r.titre.substringBefore(" · ")
        val reste = r.titre.substringAfter(" · ", "")
        Row(Modifier.padding(top = 10.u).fillMaxWidth()) {
            TexteGeant(nom, geant, 92f, 520f, contour = false)
            if (suivant != null) {
                Spacer(Modifier.width(26.u))
                Column(Modifier.weight(1f)) {
                    T("SUIVANT  ›", 11f, Color.White.copy(alpha = 0.8f), poids = FontWeight.Bold, espacement = 2f)
                    TexteGeant(suivant.titre.substringBefore(" · "), Color.White.copy(alpha = 0.55f), 60f, 380f, contour = true, minimum = 34f)
                }
            }
        }
        if (r.options.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 6.u),
                horizontalArrangement = Arrangement.spacedBy(8.u),
                verticalArrangement = Arrangement.spacedBy(6.u),
            ) {
                T("Thème de l'accueil", 13f, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.CenterVertically).padding(end = 6.u), poids = FontWeight.SemiBold)
                r.options.forEachIndexed { k, nomOption ->
                    val actif = k == r.optionActive
                    val couleurOption = Design.entries.getOrNull(k)?.colors()?.accent ?: c.accent
                    Row(
                        Modifier.height(26.u)
                            .background(if (actif) Color.White else Color.Transparent, RoundedCornerShape(13.u))
                            .border(1.25f.uu, Color.White.copy(alpha = if (actif) 1f else 0.4f), RoundedCornerShape(13.u))
                            .padding(start = 5.u, end = 11.u),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(15.u).background(Brush.linearGradient(listOf(couleurOption, couleurOption.copy(alpha = 0.35f))), CircleShape))
                        Spacer(Modifier.width(6.u))
                        T(if (actif) "$nomOption ✓" else nomOption, 12f, if (actif) Color(0xFF0A0A0D) else Color.White, poids = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            val detail = listOf(reste, r.valeur).filter { it.isNotEmpty() }.joinToString(" · ")
            T(detail, 15f, Color.White.copy(alpha = 0.88f), Modifier.padding(top = 4.u), poids = FontWeight.SemiBold)
        }
    }
}

/**
 * Texte géant sur une ligne : la plus grande taille (≤ [tailleMax]) qui tient dans [largeurMax] ;
 * en contour si [contour]. Sous [minimum], rien n'est affiché (jamais de mot coupé).
 */
@Composable
private fun TexteGeant(texte: String, couleur: Color, tailleMax: Float, largeurMax: Float, contour: Boolean, minimum: Float = 0f) {
    val mesureur = rememberTextMeasurer()
    val k = LocalScale.current
    val densite = LocalDensity.current
    BoxWithConstraints {
        val limite = min(largeurMax * k * densite.density, constraints.maxWidth.toFloat())
        val resultat = remember(texte, tailleMax, limite, k) {
            var taille = tailleMax
            var r = mesureur.measure(texte, TextStyle(fontSize = (taille * k).sp, fontWeight = FontWeight.Black, letterSpacing = (-taille * k * 0.035f).sp), softWrap = false, maxLines = 1)
            while (r.size.width > limite && taille > minimum.coerceAtLeast(8f)) {
                taille -= 2f
                r = mesureur.measure(texte, TextStyle(fontSize = (taille * k).sp, fontWeight = FontWeight.Black, letterSpacing = (-taille * k * 0.035f).sp), softWrap = false, maxLines = 1)
            }
            if (r.size.width > limite) null else r
        }
        if (resultat != null) {
            val unite = 1.u
            Box(
                Modifier
                    .size(with(densite) { resultat.size.width.toDp() }, with(densite) { resultat.size.height.toDp() })
                    .drawBehind {
                        if (contour) drawText(resultat, couleur, drawStyle = Stroke(width = 1.5f * unite.toPx()))
                        else drawText(resultat, couleur)
                    },
            )
        }
    }
}

/** Chaînes : bandeau du haut façon menu de console de salon. */
@Composable
private fun BandeauReglages(s: StyleReglages, p: PanneauReglages) {
    val c = s.couleurs
    val m = s.mesures
    val unite = 1.u
    Box(
        Modifier
            .fillMaxWidth()
            .height(78.u)
            .drawBehind {
                val k = unite.toPx()
                drawRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9ECEF))))
                // Filet bleu clair, léger creux au centre comme la bande de l'accueil
                val chemin = Path().apply {
                    moveTo(0f, size.height - 2f * k)
                    lineTo(size.width * 0.32f, size.height - 2f * k)
                    cubicTo(size.width * 0.40f, size.height - 2f * k, size.width * 0.42f, size.height - 12f * k, size.width * 0.5f, size.height - 12f * k)
                    cubicTo(size.width * 0.58f, size.height - 12f * k, size.width * 0.60f, size.height - 2f * k, size.width * 0.68f, size.height - 2f * k)
                    lineTo(size.width, size.height - 2f * k)
                }
                drawPath(chemin, ChainesBleuFilet, style = Stroke(2.5f * k))
            }
            .padding(horizontal = m.marge.u),
    ) {
        Row(Modifier.align(Alignment.CenterStart).padding(bottom = 6.u), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.u).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE4E7EA))), CircleShape)
                    .border(2.u, ChainesBleuFilet, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(ProjoIcons.Reglages, null, Modifier.size(20.u), tint = ChainesGris) }
            Spacer(Modifier.width(14.u))
            T("Réglages", s.typo.enTete.toFloat(), c.texte, poids = FontWeight.Medium)
        }
        T("Projo ${p.version}", 13f, c.texteDiscret, Modifier.align(Alignment.CenterEnd).padding(bottom = 6.u), poids = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ pieds

@Composable
private fun PiedReglages(s: StyleReglages, p: PanneauReglages, fermer: () -> Unit) {
    val c = s.couleurs
    val m = s.mesures
    when {
        s.enTete == EnTete.BANDEAU -> Row(
            Modifier.fillMaxWidth().padding(horizontal = m.marge.u, vertical = 12.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bouton rond « Retour », décoratif à la télécommande (la touche Retour ferme) ; il ferme
            // au clic ou au toucher (le panneau occupe tout l'écran : pas de voile à toucher)
            Row(
                Modifier.testTag("reglages:fermer").cliquableSansFocus(fermer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.u).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6E9EC))), CircleShape)
                        .border(2.u, ChainesBleuFilet, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(IconeRetour, null, Modifier.size(22.u), tint = ChainesGris) }
                Spacer(Modifier.width(12.u))
                T("Retour", 15f, c.texte, poids = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            T("OK  Choisir", 13f, c.texteDiscret, poids = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ corps

/** Défilement : la tuile sélectionnée reste à [marge] des bords (place pour l'anneau et l'agrandissement). */
@OptIn(ExperimentalFoundationApi::class)
private class DefilementReglages(private val marge: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val fin = offset + size
        return when {
            size > containerSize - 2 * marge -> offset - marge
            offset < marge -> offset - marge
            fin > containerSize - marge -> fin - (containerSize - marge)
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CorpsReglages(s: StyleReglages, p: PanneauReglages, modifier: Modifier) {
    val m = s.mesures
    // Grilles : fondu plus long (une rangée à moitié défilée s'efface au lieu d'être coupée),
    // et la rangée sélectionnée reste hors du fondu
    val grille = s.tuiles == FormeTuiles.CARTES || s.tuiles == FormeTuiles.TUILES
    val fondu = if (grille) 34 else 20
    val marge = with(LocalDensity.current) { (fondu + 4).u.toPx() }
    val spec = remember(marge) { DefilementReglages(marge) }
    val defilement = rememberScrollState()
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Column(
            modifier
                .fillMaxWidth()
                .fonduVertical(defilement, fondu.u)
                .verticalScroll(defilement)
                .padding(
                    start = m.marge.u, end = m.marge.u,
                    top = if (s.enTete == EnTete.AFFICHE || s.enTete == EnTete.BANDEAU) 16.u else 12.u,
                    bottom = 24.u,
                ),
        ) {
            when (s.tuiles) {
                FormeTuiles.LIGNES, FormeTuiles.GROUPES, FormeTuiles.SEGMENTS -> ListeReglages(s, p)
                FormeTuiles.CARTES, FormeTuiles.TUILES -> GrilleReglages(s, p)
                FormeTuiles.BOUTONS -> BoutonsReglages(s, p)
            }
        }
    }
}

/**
 * Bords du défilement fondus (au lieu d'une tuile coupée net) : en haut quand la liste a défilé,
 * en bas tant qu'il reste des tuiles dessous. Simple masque de transparence, sans flou.
 */
private fun Modifier.fonduVertical(etat: ScrollState, hauteur: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = hauteur.toPx()
        if (size.height <= 3 * h) return@drawWithContent
        val haut = (etat.value / h).coerceIn(0f, 1f)
        val bas = ((etat.maxValue - etat.value) / h).coerceIn(0f, 1f)
        if (haut == 0f && bas == 0f) return@drawWithContent
        val f = h / size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 1f - haut), f to Color.Black,
                1f - f to Color.Black, 1f to Color.Black.copy(alpha = 1f - bas),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun TitreSection(s: StyleReglages, titre: String, modifier: Modifier = Modifier) {
    val texte = if (s.typo.sectionsCapitales) titre else titre.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    T(
        texte, s.typo.section.toFloat(),
        s.couleurs.section ?: if (s.typo.sectionsCapitales) s.couleurs.texteDiscret else s.couleurs.accent,
        modifier.padding(start = 6.u, top = 14.u, bottom = 6.u),
        poids = FontWeight.SemiBold,
        espacement = if (s.typo.sectionsCapitales) s.typo.section * 0.25f else 0.1f,
    )
}

/** Listes : lignes séparées (LIGNES), groupées (GROUPES) ou segmentées (SEGMENTS). */
@Composable
private fun ListeReglages(s: StyleReglages, p: PanneauReglages) {
    val c = s.couleurs
    val m = s.mesures
    p.sections.forEachIndexed { i, section ->
        key(section.cle) {
            if (section.titre.isNotEmpty()) TitreSection(s, section.titre)
            else if (i > 0) Spacer(Modifier.height(10.u))
            val n = section.reglages.size
            when (s.tuiles) {
                FormeTuiles.GROUPES -> {
                    val forme = RoundedCornerShape(m.rayonTuile.u)
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 4.u).background(c.carte, forme),
                    ) {
                        section.reglages.forEachIndexed { j, r ->
                            key(r.cle) {
                                LigneReglage(
                                    s, p, r,
                                    forme = forme,
                                    fond = if (r.accent) c.accentFond else Color.Transparent,
                                    filet = j > 0 && p.focus != r.cle && p.focus != section.reglages[j - 1].cle,
                                )
                            }
                        }
                    }
                }
                FormeTuiles.SEGMENTS -> section.reglages.forEachIndexed { j, r ->
                    key(r.cle) {
                        val grand = m.rayonTuile.u
                        val petit = 5.u
                        val forme = RoundedCornerShape(
                            topStart = if (j == 0) grand else petit, topEnd = if (j == 0) grand else petit,
                            bottomStart = if (j == n - 1) grand else petit, bottomEnd = if (j == n - 1) grand else petit,
                        )
                        LigneReglage(
                            s, p, r, forme = forme, formeFocus = RoundedCornerShape(grand + 6.u),
                            fond = if (r.accent) c.accentFond else c.carte,
                            modifier = Modifier.padding(bottom = m.ecart.u),
                        )
                    }
                }
                else -> section.reglages.forEach { r ->
                    key(r.cle) {
                        LigneReglage(
                            s, p, r, forme = RoundedCornerShape(m.rayonTuile.u),
                            fond = if (r.accent) c.accentFond else c.carte,
                            modifier = Modifier.padding(bottom = m.ecart.u),
                        )
                    }
                }
            }
        }
    }
}

/** Une ligne : pastille, titre, valeur (à droite ou dessous), chiffre ou chevron, jauge, note. */
@Composable
private fun LigneReglage(
    s: StyleReglages,
    p: PanneauReglages,
    r: Reglage,
    forme: Shape,
    fond: Color,
    modifier: Modifier = Modifier,
    formeFocus: Shape = forme,
    filet: Boolean = false,
) {
    val c = s.couleurs
    val m = s.mesures
    val unite = 1.u
    CaseReglage(s, p, r, 0, true, forme, formeFocus, fond, modifier) { t ->
        val texte = lerp(c.texte, c.texteFocus, t)
        val discret = lerp(c.texteDiscret, c.texteDiscretFocus, t)
        val decalage = (m.padH + m.pastille + 12).u
        Column(
            Modifier
                .drawBehind {
                    if (filet) drawRect(c.filet, topLeft = Offset(decalage.toPx(), 0f), size = Size(size.width - decalage.toPx(), unite.toPx() * 0.75f))
                }
                .padding(horizontal = m.padH.u, vertical = m.padV.u),
        ) {
            val sansPastille = r.curseur && s.jaugeVolume == Jauge.EXPRESSIVE
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!sansPastille) {
                    PastilleReglage(s, r, t)
                    Spacer(Modifier.width(12.u))
                }
                val cote = s.valeurADroite && r.chiffre == null && r.jauge == null
                TitreEtValeur(
                    titre = { T(r.titre, s.typo.titre.toFloat(), texte, poids = if (s.typo.titreGras) FontWeight.SemiBold else FontWeight.Medium) },
                    valeur = { T(r.valeur, s.typo.valeur.toFloat(), discret, alignement = if (cote) TextAlign.End else null) },
                    cote = cote,
                    modifier = Modifier.weight(1f),
                )
                DroiteLigne(s, r, t, cote)
            }
            if (r.jauge != null) {
                Spacer(Modifier.height(if (r.curseur) 10.u else 8.u))
                if (r.curseur) {
                    JaugeVolume(s, r, t)
                } else {
                    Box(Modifier.padding(start = if (s.tuiles == FormeTuiles.GROUPES) (m.pastille + 12).u else 0.u)) {
                        JaugeSimple(if (r.accent) Jauge.FINE else s.jaugeStockage, r.jauge, s, t)
                    }
                }
            }
            r.note?.let { T(it, s.typo.valeur.toFloat(), c.alerte, Modifier.padding(top = 6.u), poids = FontWeight.Medium) }
        }
    }
}

/** Élément à droite d'une ligne : chiffre (« 70 % »), barres Wi-Fi, chevron. */
@Composable
private fun DroiteLigne(s: StyleReglages, r: Reglage, t: Float, valeurACote: Boolean) {
    val c = s.couleurs
    val texte = lerp(c.texte, c.texteFocus, t)
    if (r.chiffre != null) {
        Spacer(Modifier.width(10.u))
        val couleur = if (s.jaugeVolume == Jauge.EXPRESSIVE) c.accent else texte
        T(r.chiffre, s.typo.chiffre.toFloat(), couleur, poids = FontWeight.Bold)
    } else if (r.signal != null && !valeurACote && s.tuiles != FormeTuiles.BOUTONS) {
        Spacer(Modifier.width(8.u))
        Barres(r.signal, if (s.couleurs.carteFocus.first().luminance() > 0.5f) lerp(c.accent, c.texteFocus, t * 0.3f) else c.accent, texte.copy(alpha = 0.2f))
    }
    if (s.chevron && !r.curseur) {
        Spacer(Modifier.width(9.u))
        Chevron(lerp(c.texteDiscret, c.texteDiscretFocus, t).copy(alpha = 0.7f))
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun Chevron(couleur: Color) {
    val unite = 1.u
    Box(
        Modifier.size(width = 7.u, height = 12.u).drawBehind {
            val k = unite.toPx()
            val trait = 1.4f * k
            drawLine(couleur, Offset(1.5f * k, 1.5f * k), Offset(size.width - 1.5f * k, size.height / 2), trait, StrokeCap.Round)
            drawLine(couleur, Offset(size.width - 1.5f * k, size.height / 2), Offset(1.5f * k, size.height - 1.5f * k), trait, StrokeCap.Round)
        },
    )
}

/**
 * Titre et valeur : côte à côte (valeur alignée à droite) si [cote] et que tout tient sur la
 * ligne, sinon la valeur passe dessous. Rien n'est jamais coupé : la valeur va à la ligne.
 */
@Composable
private fun TitreEtValeur(titre: @Composable () -> Unit, valeur: @Composable () -> Unit, cote: Boolean, modifier: Modifier = Modifier) {
    val ecart = with(LocalDensity.current) { 14.u.roundToPx() }
    val interligne = with(LocalDensity.current) { 1.u.roundToPx() }
    Layout(content = { titre(); valeur() }, modifier = modifier) { mesurables, contraintes ->
        val largeur = contraintes.maxWidth
        val libre = contraintes.copy(minWidth = 0, minHeight = 0)
        val tMax = mesurables[0].maxIntrinsicWidth(contraintes.maxHeight)
        val vMax = mesurables[1].maxIntrinsicWidth(contraintes.maxHeight)
        if (cote && tMax + ecart + vMax <= largeur) {
            val pt = mesurables[0].measure(libre.copy(maxWidth = tMax))
            val pv = mesurables[1].measure(libre.copy(maxWidth = vMax))
            val h = max(pt.height, pv.height)
            layout(largeur, h) {
                pt.placeRelative(0, (h - pt.height) / 2)
                pv.placeRelative(largeur - pv.width, (h - pv.height) / 2)
            }
        } else {
            val pt = mesurables[0].measure(libre)
            val pv = mesurables[1].measure(libre)
            layout(largeur, pt.height + interligne + pv.height) {
                pt.placeRelative(0, 0)
                pv.placeRelative(0, pt.height + interligne)
            }
        }
    }
}

// ------------------------------------------------------------------ grilles

/** Grille de cartes (Console) ou de tuiles (Une) : toutes les tuiles, rangée par rangée. */
@Composable
private fun GrilleReglages(s: StyleReglages, p: PanneauReglages) {
    val m = s.mesures
    p.reglages.chunked(s.colonnes).forEachIndexed { i, rangee ->
        key(rangee.first().cle) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = if (i == 0) 0.u else m.ecart.u),
                horizontalArrangement = Arrangement.spacedBy(m.ecart.u),
            ) {
                rangee.forEachIndexed { colonne, r ->
                    key(r.cle) {
                        val mod = Modifier.weight(1f).fillMaxHeight().heightIn(min = m.hauteurTuile.u)
                        val fin = colonne == rangee.size - 1
                        if (s.tuiles == FormeTuiles.CARTES) CarteReglage(s, p, r, colonne, fin, mod) else TuileReglage(s, p, r, colonne, fin, mod)
                    }
                }
                repeat(s.colonnes - rangee.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Console : pastille et indicateur en haut, titre et valeur en bas. */
@Composable
private fun CarteReglage(s: StyleReglages, p: PanneauReglages, r: Reglage, colonne: Int, fin: Boolean, modifier: Modifier) {
    val c = s.couleurs
    val m = s.mesures
    val forme = RoundedCornerShape(m.rayonTuile.u)
    CaseReglage(s, p, r, colonne, fin, forme, forme, if (r.accent) c.accentFond else c.carte, modifier, remplir = true) { t ->
        val texte = lerp(c.texte, c.texteFocus, t)
        val discret = lerp(c.texteDiscret, c.texteDiscretFocus, t)
        Column(Modifier.fillMaxSize().border(1.u, c.filet, forme).padding(horizontal = m.padH.u, vertical = m.padV.u)) {
            Row(verticalAlignment = Alignment.Top) {
                PastilleReglage(s, r, t)
                Spacer(Modifier.weight(1f))
                when {
                    r.curseur && r.chiffre != null -> Row(verticalAlignment = Alignment.Bottom) {
                        T(r.chiffre.removeSuffix(" %"), s.typo.chiffre.toFloat(), texte, poids = FontWeight.ExtraLight, interligne = 1.0f, espacement = -1f)
                        if (r.chiffre.endsWith("%")) T("%", 16f, texte, Modifier.padding(bottom = 5.u), poids = FontWeight.Light)
                    }
                    r.signal != null -> Barres(r.signal, texte, texte.copy(alpha = 0.22f), 1.4f)
                    r.jauge != null -> Box(Modifier.width(70.u).padding(top = 10.u)) { JaugeSimple(Jauge.FINE, r.jauge, s, t) }
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 8.u))
            T(r.titre, s.typo.titre.toFloat(), texte, poids = FontWeight.Medium)
            if (r.curseur && r.jauge != null) {
                Spacer(Modifier.height(6.u))
                JaugeVolume(s, r, t)
            }
            if (!r.curseur) T(r.valeur, s.typo.valeur.toFloat(), discret, Modifier.padding(top = 3.u))
            r.note?.let { T(it, s.typo.valeur.toFloat(), c.alerte, Modifier.padding(top = 4.u)) }
        }
    }
}

/** Une : pastille et titre en haut, valeur (ou jauge) en bas. */
@Composable
private fun TuileReglage(s: StyleReglages, p: PanneauReglages, r: Reglage, colonne: Int, fin: Boolean, modifier: Modifier) {
    val c = s.couleurs
    val m = s.mesures
    val forme = RoundedCornerShape(m.rayonTuile.u)
    val unite = 1.u
    CaseReglage(s, p, r, colonne, fin, forme, forme, if (r.accent) c.accentFond else c.carte, modifier, remplir = true) { t ->
        val discret = lerp(c.texteDiscret, c.texteDiscretFocus, t)
        Column(
            Modifier.fillMaxSize()
                .drawBehind {
                    // Liseré intérieur blanc 8 %, effacé sur la tuile sélectionnée
                    val k = unite.toPx()
                    drawRoundRect(
                        c.filet.copy(alpha = c.filet.alpha * (1f - t)), topLeft = Offset(k / 2, k / 2), size = Size(size.width - k, size.height - k),
                        cornerRadius = CornerRadius(m.rayonTuile * k), style = Stroke(k),
                    )
                }
                .padding(horizontal = m.padH.u, vertical = m.padV.u),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastilleReglage(s, r, t)
                Spacer(Modifier.width(12.u))
                T(r.titre, s.typo.titre.toFloat(), c.texte, poids = FontWeight.SemiBold, interligne = 1.15f)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 8.u))
            if (r.curseur && r.jauge != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).jaugeCliquable(r)) { JaugeSimple(Jauge.FINE, r.jauge, s, t, epaisseur = 4f, couleur = Color.White) }
                    Spacer(Modifier.width(10.u))
                    T(r.chiffre ?: "", 13f, Color.White, poids = FontWeight.Bold)
                }
            } else {
                T(r.valeur, s.typo.valeur.toFloat(), discret, poids = FontWeight.Medium)
            }
            r.note?.let { T(it, s.typo.valeur.toFloat(), c.alerte, Modifier.padding(top = 3.u), poids = FontWeight.Medium) }
        }
    }
}

/** Chaînes : gros boutons arrondis en colonnes, intitulés de section au-dessus. */
@Composable
private fun BoutonsReglages(s: StyleReglages, p: PanneauReglages) {
    val m = s.mesures
    p.sections.forEachIndexed { i, section ->
        key(section.cle) {
            if (section.titre.isNotEmpty()) TitreSection(s, section.titre, Modifier.padding(start = 4.u))
            else if (i > 0) Spacer(Modifier.height(10.u))
            section.reglages.chunked(s.colonnes).forEach { rangee ->
                key(rangee.first().cle) {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = m.ecart.u),
                        horizontalArrangement = Arrangement.spacedBy((m.ecart + 6).u),
                    ) {
                        rangee.forEachIndexed { colonne, r ->
                            key(r.cle) {
                                BoutonReglage(s, p, r, colonne, colonne == rangee.size - 1, Modifier.weight(1f).fillMaxHeight().heightIn(min = m.hauteurTuile.u))
                            }
                        }
                        repeat(s.colonnes - rangee.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoutonReglage(s: StyleReglages, p: PanneauReglages, r: Reglage, colonne: Int, fin: Boolean, modifier: Modifier) {
    val c = s.couleurs
    val m = s.mesures
    val forme = RoundedCornerShape(m.rayonTuile.u)
    CaseReglage(s, p, r, colonne, fin, forme, forme, c.carte, modifier, remplir = true) { t ->
        val texte = lerp(c.texte, c.texteFocus, t)
        val discret = lerp(c.texteDiscret, c.texteDiscretFocus, t)
        Column(Modifier.fillMaxSize().padding(horizontal = m.padH.u, vertical = m.padV.u), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastilleReglage(s, r, t)
                Spacer(Modifier.width(14.u))
                Column(Modifier.weight(1f)) {
                    T(r.titre, s.typo.titre.toFloat(), texte, poids = FontWeight.Medium, interligne = 1.15f)
                    T(r.valeur, s.typo.valeur.toFloat(), discret, Modifier.padding(top = 2.u), poids = FontWeight.Medium)
                }
                if (r.chiffre != null) {
                    Spacer(Modifier.width(10.u))
                    T(r.chiffre, s.typo.chiffre.toFloat(), texte, poids = FontWeight.Bold)
                } else if (r.signal != null) {
                    Spacer(Modifier.width(10.u))
                    Barres(r.signal, c.accent, c.texteDiscret.copy(alpha = 0.3f), 1.3f)
                }
            }
            if (r.jauge != null) {
                Spacer(Modifier.height(8.u))
                if (r.curseur) JaugeVolume(s, r, t) else JaugeSimple(s.jaugeStockage, r.jauge, s, t)
            }
            r.note?.let { T(it, s.typo.valeur.toFloat(), c.alerte, Modifier.padding(top = 4.u), poids = FontWeight.Medium) }
        }
    }
}

// ------------------------------------------------------------------ tuile focalisable et sélection

/**
 * Tuile focalisable : pose le comportement commun (p.comportement) sur la Surface TV et dessine
 * la sélection du style. [contenu] reçoit l'avancement de la sélection (0 au repos, 1 sélectionnée).
 */
@Composable
private fun CaseReglage(
    s: StyleReglages,
    p: PanneauReglages,
    r: Reglage,
    colonne: Int,
    finDeRangee: Boolean,
    forme: Shape,
    formeFocus: Shape,
    fond: Color,
    modifier: Modifier = Modifier,
    remplir: Boolean = false,
    contenu: @Composable (Float) -> Unit,
) {
    val c = s.couleurs
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(170), label = "selection")
    val teinte = s.teinte(r)
    val unite = 1.u
    val rayon = s.mesures.rayonTuile.u
    Box(
        modifier
            // La tuile sélectionnée passe devant ses voisines (anneau, halo)
            .zIndex(if (focus) 1f else 0f)
            .graphicsLayer {
                val k = 1f + (s.agrandissement - 1f) * t
                scaleX = k
                scaleY = k
            }
            .drawBehind {
                val k = unite.toPx()
                val rp = rayon.toPx()
                // Lueurs et anneaux (hors de la tuile)
                when (s.selection) {
                    Selection.PLATEAU -> {
                        // Verre clair : ombre douce qui s'élargit et descend ; sinon halo clair discret
                        if (s.verreClair) ombreDouce(rp, 0.45f + 0.55f * t, t)
                        else halo(c.anneau, t * 0.16f, rp, k, 3)
                    }
                    Selection.ANNEAU -> {
                        val epaisseur = if (s.couleurs.carteFocus.first().luminance() > 0.5f) 3f else 2f
                        if (epaisseur < 3f) halo(c.anneau, t * 0.12f, rp + 5f * k, k * 1.6f, 4, depart = 5f)
                        anneau(c.anneau.copy(alpha = c.anneau.alpha * t), rp, 3.5f * k, epaisseur * k)
                    }
                    Selection.TEINTE -> anneau(c.anneau.copy(alpha = t), rp, 3f * k, 2.5f * k)
                    Selection.LISERE -> halo(ChainesBleuLueur, t * 0.30f, rp, k * 1.5f, 4)
                }
                // Fond au repos, puis fond de sélection
                if (fond.alpha > 0f) drawOutline(forme.createOutline(size, layoutDirection, this), fond, alpha = 1f - t * 0.999f)
                val fondFocus = when (s.selection) {
                    Selection.TEINTE -> androidx.compose.ui.graphics.SolidColor(teinte)
                    else -> brosse(c.carteFocus)
                }
                if (t > 0f) drawOutline(formeFocus.createOutline(size, layoutDirection, this), fondFocus, alpha = t)
                if (s.verreClair) {
                    // Tuile de verre : liseré blanc (effacé à la sélection) puis contour gris très fin
                    val e = 1.2f * k
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f * (1f - t)), topLeft = Offset(e, e), size = Size(size.width - 2 * e, size.height - 2 * e),
                        cornerRadius = CornerRadius(rp - e), style = Stroke(e),
                    )
                    drawRoundRect(c.filet, topLeft = Offset(k / 2, k / 2), size = Size(size.width - k, size.height - k), cornerRadius = CornerRadius(rp), style = Stroke(k))
                }
                if (s.selection == Selection.LISERE) {
                    val e = (2f + 1.5f * t) * k
                    drawRoundRect(
                        lerp(ChainesBleuFilet, ChainesBleu, t), topLeft = Offset(e / 2, e / 2), size = Size(size.width - e, size.height - e),
                        cornerRadius = CornerRadius(rp - e / 2), style = Stroke(e),
                    )
                }
            },
    ) {
        SurfaceCliquable(
            onClick = r.onClick,
            onLongClick = r.onLongClick,
            modifier = p.comportement(r, colonne, finDeRangee)
                .onFocusChanged { focus = it.isFocused }
                .then(if (remplir) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
            shape = ClickableSurfaceDefaults.shape(formeFocus),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent, contentColor = c.texte,
                focusedContainerColor = Color.Transparent, focusedContentColor = c.texteFocus,
                pressedContainerColor = Color.Transparent, pressedContentColor = c.texteFocus,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            contenu(t)
        }
    }
}

/** Halo doux autour d'une tuile : quelques contours de plus en plus larges et transparents. */
private fun DrawScope.halo(couleur: Color, force: Float, rayon: Float, pas: Float, couches: Int, depart: Float = 0f) {
    if (force <= 0.001f) return
    for (i in 1..couches) {
        val e = depart * pas / 1.6f + i * pas
        drawRoundRect(
            couleur.copy(alpha = couleur.alpha * force * (1f - (i - 1f) / couches)),
            topLeft = Offset(-e, -e), size = Size(size.width + 2 * e, size.height + 2 * e),
            cornerRadius = CornerRadius(rayon + e), style = Stroke(pas * 1.1f),
        )
    }
}

/**
 * Ombre douce sous une tuile (verre clair de Studio, comme ombreStudio) : quelques calques de
 * plus en plus larges, transparents et décalés vers le bas. [force] : 0 à 1 ; [levee] : 0 au
 * repos, 1 sélectionnée (ombre plus large et plus basse).
 */
private fun DrawScope.ombreDouce(rayon: Float, force: Float, levee: Float) {
    if (force <= 0.001f) return
    val pas = (1.3f + 0.9f * levee) * density
    for (i in 1..5) {
        val e = i * pas
        drawRoundRect(
            Color.Black.copy(alpha = 0.022f * force),
            topLeft = Offset(-e * 0.6f, -e * 0.3f + i * pas * 0.8f),
            size = Size(size.width + e * 1.2f, size.height + e * 0.7f),
            cornerRadius = CornerRadius(rayon + e * 0.6f),
        )
    }
}

/** Anneau décollé de la tuile de [ecart], d'épaisseur [epaisseur]. */
private fun DrawScope.anneau(couleur: Color, rayon: Float, ecart: Float, epaisseur: Float) {
    if (couleur.alpha <= 0.001f) return
    val o = ecart + epaisseur / 2
    drawRoundRect(
        couleur, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius(rayon + o), style = Stroke(epaisseur),
    )
}

// ------------------------------------------------------------------ pastilles

@Composable
private fun PastilleReglage(s: StyleReglages, r: Reglage, t: Float) {
    val c = s.couleurs
    val taille = s.mesures.pastille
    val teinte = s.teinte(r)
    val icone = (taille * 0.54f)
    when (s.pastille) {
        Pastille.RONDE -> {
            // Plateau clair : la pastille prend la teinte du réglage ; plateau sombre (Console, Studio) : disque blanc
            val surSombre = s.couleurs.carteFocus.first().luminance() < 0.5f
            // Tuile mise en avant, au repos : pastille pleine de l'accent (icône blanche sur un accent foncé)
            val enAvant = r.accent && t == 0f
            val plein = c.accentFond.copy(alpha = 1f)
            val fond = when {
                enAvant -> plein
                surSombre -> lerp(c.pastille, Color.White, t)
                else -> lerp(c.pastille, teinte, t)
            }
            val encre = when {
                enAvant && plein.luminance() < 0.45f -> Color.White
                enAvant -> c.texte
                surSombre -> lerp(c.texte, Color(0xFF050817), t)
                else -> lerp(c.texte, Color.White, t)
            }
            IconeSur(r.icone, taille.toFloat(), icone, CircleShape, androidx.compose.ui.graphics.SolidColor(fond), encre)
        }
        Pastille.CARREE_COULEUR -> IconeSur(
            r.icone, taille.toFloat(), icone, RoundedCornerShape((taille * 0.26f).uu),
            Brush.verticalGradient(listOf(lerp(teinte, Color.White, 0.18f), teinte)), Color.White,
        )
        Pastille.CARREE_SOIR -> IconeSur(
            r.icone, taille.toFloat(), icone * 0.95f, RoundedCornerShape((taille * 0.25f).uu),
            androidx.compose.ui.graphics.SolidColor(lerp(soir(teinte), Color(0xFF0B0B0D), t)), Color.White,
        )
        Pastille.RONDE_TONALE -> {
            val propre = s.teintes[r.cle]
            val fond = propre?.let { lerp(Color.White, it, 0.2f) } ?: c.pastille
            val encre = propre ?: c.texte
            IconeSur(r.icone, taille.toFloat(), icone, CircleShape, androidx.compose.ui.graphics.SolidColor(lerp(fond, Color.White, t * 0.6f)), lerp(encre, c.texteFocus, t))
        }
        Pastille.AUCUNE -> Box(Modifier.size(taille.u), contentAlignment = Alignment.Center) {
            Icon(r.icone, null, Modifier.size((taille * 0.85f).uu), tint = lerp(c.texteDiscret, c.accent, t))
        }
    }
}

@Composable
private fun IconeSur(icone: ImageVector, taille: Float, tailleIcone: Float, forme: Shape, fond: Brush, encre: Color) {
    Box(Modifier.size(taille.uu).background(fond, forme), contentAlignment = Alignment.Center) {
        Icon(icone, null, Modifier.size(tailleIcone.uu), tint = encre)
    }
}

/** Barres de signal Wi-Fi (0 à 4). */
@Composable
private fun Barres(niveau: Int, allumee: Color, eteinte: Color, echelle: Float = 1f) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((2f * echelle).uu)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width((3.5f * echelle).uu)
                    .height(((3 + i * 3) * echelle).uu)
                    .background(if (i <= niveau) allumee else eteinte, RoundedCornerShape((1f * echelle).uu)),
            )
        }
    }
}

// ------------------------------------------------------------------ jauges

/** Jauge du volume (curseur réglable) selon le style. */
@Composable
private fun JaugeVolume(s: StyleReglages, r: Reglage, t: Float) {
    val c = s.couleurs
    val v = (r.jauge ?: 0f).coerceIn(0f, 1f)
    val discret = lerp(c.texteDiscret, c.texteDiscretFocus, t)
    when (s.jaugeVolume) {
        Jauge.CURSEUR -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ProjoIcons.Muet, null, Modifier.size(13.u), tint = discret)
            Spacer(Modifier.width(9.u))
            Box(Modifier.weight(1f).jaugeCliquable(r)) { Curseur(s, v, t) }
            Spacer(Modifier.width(9.u))
            Icon(ProjoIcons.Volume, null, Modifier.size(15.u), tint = discret)
            if (t > 0.5f) {
                Spacer(Modifier.width(10.u))
                FlecheReglage("◀", s, t, r.enReglage, r.surFleche?.let { f -> { f(-1) } })
                Spacer(Modifier.width(4.u))
                FlecheReglage("▶", s, t, r.enReglage, r.surFleche?.let { f -> { f(1) } })
            }
        }
        Jauge.EXPRESSIVE -> Box(Modifier.jaugeCliquable(r)) { Expressive(s, v, t) }
        Jauge.BLOCS -> Box(Modifier.jaugeCliquable(r)) { Blocs(v, 20) }
        else -> Box(Modifier.jaugeCliquable(r)) { if (s.tuiles == FormeTuiles.CARTES) {
            // Console : piste fine, bouton blanc, « Muet » et « Max » dessous
            Column {
                Curseur(s, v, t, fin = true)
                Row(Modifier.fillMaxWidth().padding(top = 3.u)) {
                    T("Muet", 10f, discret)
                    Spacer(Modifier.weight(1f))
                    T("Max", 10f, discret)
                }
            }
        } else {
            JaugeSimple(Jauge.FINE, v, s, t, couleur = if (r.enReglage) c.accent else null)
        } }
    }
}

/**
 * Pointeur : un clic ou un toucher sur la jauge du volume règle directement le niveau
 * (Reglage.surJauge, position de 0 à 1 sur la largeur de la jauge). Rien à la télécommande.
 */
private fun Modifier.jaugeCliquable(r: Reglage): Modifier {
    val regler = r.surJauge ?: return this
    return testTag("jauge:" + r.cle).pointerInput(regler) {
        detectTapGestures { regler((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }
    }
}

@Composable
private fun FlecheReglage(fleche: String, s: StyleReglages, t: Float, actif: Boolean, onClick: (() -> Unit)?) {
    val c = s.couleurs
    val fond = if (actif) c.accent else lerp(c.texte, c.texteFocus, t).copy(alpha = 0.08f)
    // Pointeur : la flèche dessinée règle d'un cran (affichée seulement sur la tuile sélectionnée)
    val clic = if (onClick != null) Modifier.cliquableSansFocus(onClick) else Modifier
    Box(Modifier.then(clic).size(16.u).background(fond, RoundedCornerShape(4.u)), contentAlignment = Alignment.Center) {
        T(fleche, 8f, if (actif) Color.White else lerp(c.texte, c.texteFocus, t), interligne = 1f)
    }
}

/** Piste, remplissage (en dégradé pour Verre) et bouton rond ; [fin] : piste fine (Console). */
@Composable
private fun Curseur(s: StyleReglages, v: Float, t: Float, fin: Boolean = false) {
    val c = s.couleurs
    val unite = 1.u
    val claire = c.carteFocus.first().luminance() > 0.5f
    val remplissage = if (s.reflet) listOf(Color(0xFF5E5CE6), Color(0xFF0A84FF)) else listOf(c.accent, c.accent)
    // Piste : sur un plateau sombre, elle prend la couleur du texte sélectionné (encre claire de Studio)
    val piste = lerp(c.texte.copy(alpha = 0.16f), if (claire) Color.Black.copy(alpha = 0.10f) else c.texteFocus.copy(alpha = 0.16f), t)
    val hauteur = if (fin) 14f else 20f
    Box(
        Modifier.fillMaxWidth().height(hauteur.uu).drawBehind {
            val k = unite.toPx()
            val e = (if (fin) 3f else 8f) * k
            val cy = size.height / 2
            val bouton = (if (fin) 5.5f else 9.5f) * k
            val x = bouton + (size.width - 2 * bouton) * v
            drawRoundRect(piste, topLeft = Offset(0f, cy - e / 2), size = Size(size.width, e), cornerRadius = CornerRadius(e / 2))
            drawRoundRect(
                Brush.horizontalGradient(remplissage, startX = 0f, endX = size.width),
                topLeft = Offset(0f, cy - e / 2), size = Size(x, e), cornerRadius = CornerRadius(e / 2),
            )
            drawCircle(Color.Black.copy(alpha = 0.18f), bouton + 1f * k, Offset(x, cy + 1.5f * k))
            drawCircle(Color.White, bouton, Offset(x, cy))
        },
    )
}

/** Material 3 Expressive : piste épaisse avec icône, poignée verticale, point d'arrêt. */
@Composable
private fun Expressive(s: StyleReglages, v: Float, t: Float) {
    val c = s.couleurs
    val unite = 1.u
    val actif = c.accent
    val inactif = lerp(c.accent, Color.White, 0.68f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(42.u)) {
        val largeur = maxWidth
        Box(
            Modifier.fillMaxSize().drawBehind {
                val k = unite.toPx()
                val h = 36f * k
                val top = (size.height - h) / 2
                val poignee = 4f * k
                val jeu = 5f * k
                val utile = size.width - poignee - 2 * jeu
                val xa = utile * v
                if (xa > 0.5f) drawRoundRect(actif, Offset(0f, top), Size(xa, h), CornerRadius(min(h / 2, 12f * k)))
                val xi = xa + poignee + 2 * jeu
                if (xi < size.width) {
                    drawRoundRect(inactif, Offset(xi, top), Size(size.width - xi, h), CornerRadius(min(h / 2, 12f * k)))
                    drawCircle(actif, 2.5f * k, Offset(size.width - 9f * k, size.height / 2))
                }
                drawRoundRect(actif, Offset(xa + jeu, 0f), Size(poignee, size.height), CornerRadius(poignee / 2))
            },
        )
        if (v * largeur.value > 34f * LocalScale.current) {
            Icon(ProjoIcons.Volume, null, Modifier.align(Alignment.CenterStart).padding(start = 12.u).size(17.u), tint = Color.White)
        }
    }
}

/** Jauge sans réglage (stockage, téléchargement). */
@Composable
private fun JaugeSimple(jauge: Jauge, v: Float, s: StyleReglages, t: Float, epaisseur: Float = 5f, couleur: Color? = null) {
    val c = s.couleurs
    val unite = 1.u
    val valeur = v.coerceIn(0f, 1f)
    when (jauge) {
        Jauge.ONDULEE -> Box(
            Modifier.fillMaxWidth().height(10.u).drawBehind {
                val k = unite.toPx()
                val e = 3.5f * k
                val cy = size.height / 2
                val fin = (size.width * valeur).coerceAtLeast(0f)
                val onde = Path()
                var x = 0f
                onde.moveTo(0f, cy)
                while (x < fin - 6f * k) {
                    x += k
                    onde.lineTo(x, cy + 2.6f * k * sin(x / (14f * k) * 2f * PI.toFloat()))
                }
                drawPath(onde, c.accent, style = Stroke(e, cap = StrokeCap.Round))
                val debut = fin + 4f * k
                if (debut < size.width - 4f * k) {
                    drawLine(lerp(c.accent, Color.White, 0.72f), Offset(debut, cy), Offset(size.width - 4f * k, cy), e, StrokeCap.Round)
                }
                drawCircle(c.accent, 2.2f * k, Offset(size.width - 2.5f * k, cy))
            },
        )
        Jauge.CURSEUR -> {
            val remplissage = listOf(Color(0xFF8E8E93), Color(0xFFB4B4BB))
            Box(
                Modifier.fillMaxWidth().height(7.u).drawBehind {
                    val k = unite.toPx()
                    val e = size.height
                    drawRoundRect(lerp(c.texte, c.texteFocus, t).copy(alpha = 0.14f), size = size, cornerRadius = CornerRadius(e / 2))
                    drawRoundRect(
                        Brush.horizontalGradient(remplissage, endX = size.width), size = Size(size.width * valeur, e), cornerRadius = CornerRadius(e / 2),
                    )
                },
            )
        }
        Jauge.BLOCS -> Blocs(valeur, 20)
        else -> Box(
            Modifier.fillMaxWidth().height(epaisseur.uu).drawBehind {
                val e = size.height
                drawRoundRect(lerp(c.texte, c.texteFocus, t).copy(alpha = 0.14f), size = size, cornerRadius = CornerRadius(e / 2))
                drawRoundRect(couleur ?: c.accent, size = Size(size.width * valeur, e), cornerRadius = CornerRadius(e / 2))
            },
        )
    }
}

/** Jauge en blocs arrondis (façon console de salon). */
@Composable
private fun Blocs(v: Float, n: Int) {
    val unite = 1.u
    Box(
        Modifier.fillMaxWidth().height(12.u).drawBehind {
            val k = unite.toPx()
            val ecart = 3f * k
            val l = (size.width - ecart * (n - 1)) / n
            val pleins = (v * n + 0.001f).toInt().coerceIn(0, n)
            for (i in 0 until n) {
                val x = i * (l + ecart)
                if (i < pleins) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(ChainesBleuLueur, ChainesBleu), startY = 0f, endY = size.height),
                        topLeft = Offset(x, 0f), size = Size(l, size.height), cornerRadius = CornerRadius(3f * k),
                    )
                } else {
                    drawRoundRect(Color.White, topLeft = Offset(x, 0f), size = Size(l, size.height), cornerRadius = CornerRadius(3f * k))
                    drawRoundRect(
                        ChainesLisere, topLeft = Offset(x + k / 2, k / 2), size = Size(l - k, size.height - k),
                        cornerRadius = CornerRadius(3f * k), style = Stroke(k),
                    )
                }
            }
        },
    )
}

// ------------------------------------------------------------------ dialogues

/**
 * Habillage des dialogues ouverts depuis le panneau (menus, listes à cocher, saisie de la clé
 * TMDB, appairage…) : matière, textes et sélection du style du design.
 */
internal class HabillageDialogue(
    val fond: Brush,
    val forme: Shape,
    val texte: Color,
    /** Texte secondaire, assez contrasté pour être lu de loin. */
    val discret: Color,
    val focusFond: Color,
    val focusTexte: Color,
    val formeLigne: Shape,
    /** Boutons et champs de saisie au repos (léger voile du texte sur le fond). */
    val carte: Color,
    /** Liens, curseur, jauges. */
    val accent: Color,
    /** Bouton principal au repos. */
    val accentFond: Color,
    /** Messages d'erreur. */
    val alerte: Color,
    /** Contour fin : détache le dialogue d'un accueil de la même couleur. */
    val bord: Color,
)

/** Fond et contour d'un dialogue aux couleurs du design. */
internal fun Modifier.fondDialogue(h: HabillageDialogue): Modifier =
    background(h.fond, h.forme).border(1.dp, h.bord, h.forme)

@Composable
internal fun habillageDialogue(): HabillageDialogue {
    val s = LocalDesign.current.styleReglages()
    val c = s.couleurs
    val m = s.mesures
    val rayon = (if (m.rayon > 0) m.rayon else m.rayonTuile).coerceIn(10, 26)
    val (focusFond, focusTexte) = when (s.selection) {
        Selection.TEINTE -> c.carteFocus.first() to c.texteFocus
        // Console : anneau blanc sur les cartes ; dans une liste, plateau blanc
        Selection.ANNEAU -> if (c.carteFocus.last().luminance() < 0.3f) Color.White to c.fond.last().copy(alpha = 1f) else c.carteFocus.last() to c.texteFocus
        else -> c.carteFocus.last() to c.texteFocus
    }
    val fondMoyen = c.fond[c.fond.size / 2].copy(alpha = 1f)
    return HabillageDialogue(
        fond = brosse(c.fond.map { it.copy(alpha = 1f) }),
        forme = RoundedCornerShape(rayon.u),
        texte = c.texte,
        discret = lisible(c.texteDiscret, c.texte, fondMoyen),
        focusFond = focusFond,
        focusTexte = focusTexte,
        formeLigne = RoundedCornerShape(m.rayonTuile.coerceIn(8, 16).u),
        carte = c.texte.copy(alpha = 0.09f),
        accent = c.accent,
        accentFond = c.accent.copy(alpha = 0.24f),
        alerte = c.alerte,
        bord = c.filet.copy(alpha = c.filet.alpha.coerceAtLeast(0.08f)),
    )
}

/** [couleur] rapprochée de [texte] tant que son contraste sur [fond] reste sous 4,5:1. */
private fun lisible(couleur: Color, texte: Color, fond: Color): Color {
    fun lum(c: Color): Float {
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }
    fun contraste(a: Color): Float {
        val la = lum(a.compositeOver(fond))
        val lf = lum(fond)
        return (max(la, lf) + 0.05f) / (min(la, lf) + 0.05f)
    }
    var c = couleur
    var k = 0f
    while (contraste(c) < 4.5f && k < 1f) {
        k += 0.1f
        c = lerp(couleur, texte, k)
    }
    return c
}
