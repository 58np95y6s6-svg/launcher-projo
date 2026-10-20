package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.modules.FocusModules
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.pow

// ------------------------------------------------------------------ palette « À la une »

/** Fond général (sous l'affiche et la bande d'index). */
internal val UneFond = Color(0xFF0A0A0D)
/** Encre foncée : textes sur fond blanc, liseré sombre des anneaux de focus. */
internal val UneEncreSombre = Color(0xFF0A0A0D)
/** Cartouche noir (logo de l'app sélectionnée, icônes sans fond). */
internal val UneCartouche = Color(0xFF0B0B0D)
/** Base de la bande d'index (mélangée à 16 % de l'aplat). */
internal val UneBandeBase = Color(0xFF09090C)
internal val UneVert = Color(0xFF3DDC84)
internal val UneJaune = Color(0xFFFFD54A)
internal val UneRouge = Color(0xFFE3261C)
/** Affiche d'un raccourci (Sources, Wi-Fi…), du bloc NBA et de « Toutes les apps ». */
internal val UneGraphite = Color(0xFF30333D)
internal val UneBleuNba = Color(0xFF1D428A)
internal val UneIndigo = Color(0xFF4B3BD6)
internal val UneHdmi = Color(0xFF4A505E)
/** Aplat « inversé » des marques jaunes (le nom géant prend la couleur de la marque). */
private val UneAplatInverse = Color(0xFF16140E)

/** Voile de l'aplat : un seul dégradé vertical (blanc 5 % → 0 à 40 % → noir 14 %). */
private val UneVoile = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.05f),
    0.4f to Color.White.copy(alpha = 0f),
    1f to Color.Black.copy(alpha = 0.14f),
)

/** Police : celle d'Android (Roboto), en graisses pleines. */
internal val UnePolice = FontFamily.SansSerif

/** Couleurs de marque des apps courantes (sinon : couleur extraite de l'icône). */
private val UneMarques: List<Pair<String, Color>> = listOf(
    "com.netflix." to Color(0xFFE50914),
    "com.amazon.amazonvideo" to Color(0xFF1A82E6),
    "com.amazon.avod" to Color(0xFF1A82E6),
    "com.canal." to Color(0xFF2A2A30),
    "com.google.android.youtube" to Color(0xFFE21B1B),
    "tv.twitch." to Color(0xFF8C45F7),
    "com.disney.disneyplus" to Color(0xFF1A3FB8),
    "com.spotify." to Color(0xFF1ED760),
    "com.android.vending" to Color(0xFF01875F),
    "tv.molotov." to Color(0xFFF7C600),
    "tv.arte." to Color(0xFFFD4600),
    "com.plexapp." to Color(0xFFEBAF00),
)

/** Couleur de marque d'une app : table des apps courantes, sinon la couleur extraite de son icône. */
internal fun uneCouleurApp(app: AppInfo, extraite: Color): Color = when {
    app.isSpecial -> UneHdmi
    else -> UneMarques.firstOrNull { app.packageName.startsWith(it.first) }?.second ?: extraite
}

// ------------------------------------------------------------------ calculs de couleur (aucun par image)

private fun uneLineaire(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
private fun uneSrgb(v: Float): Float =
    (if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f).coerceIn(0f, 1f)

/** Luminance relative (0 = noir, 1 = blanc). */
internal fun uneLuminance(c: Color): Float =
    0.2126f * uneLineaire(c.red) + 0.7152f * uneLineaire(c.green) + 0.0722f * uneLineaire(c.blue)

/** Teinte en degrés (-1 pour un gris). */
private fun uneTeinte(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    if (d <= 0f) return -1f
    val x = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return (x * 60f + 360f) % 360f
}

/** Encre au meilleur contraste sur une couleur (texte du bloc de focus). */
internal fun uneEncreSur(c: Color): Color = if (uneLuminance(c) > 0.20f) Color(0xFF0A0A0A) else Color.White

/** Aplat de l'affiche et couleur du nom géant. */
internal data class UneAplat(val fond: Color, val geant: Color)

/**
 * Aplat « calibré » : la luminance est plafonnée (0,07 le soir, 0,18 le jour) en gardant la
 * teinte, pour ne pas éblouir et garder l'encre blanche lisible. Les jaunes et ambres, qui
 * deviendraient kaki, sont « inversés » : aplat noir chaud et nom géant à la couleur de la marque.
 */
internal fun uneAplat(marque: Color, soir: Boolean): UneAplat {
    val y = uneLuminance(marque)
    val teinte = uneTeinte(marque)
    if (y > 0.35f && teinte in 35f..70f) return UneAplat(UneAplatInverse, marque.copy(alpha = 1f))
    val plafond = if (soir) 0.07f else 0.18f
    if (y <= plafond) return UneAplat(marque.copy(alpha = 1f), Color.White)
    val k = plafond / y
    val fond = Color(
        uneSrgb(uneLineaire(marque.red) * k),
        uneSrgb(uneLineaire(marque.green) * k),
        uneSrgb(uneLineaire(marque.blue) * k),
    )
    return UneAplat(fond, Color.White)
}

/** Mode Soir automatique de 19 h à 7 h. */
private fun uneSoir(t: Long): Boolean =
    Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 7 }

// ------------------------------------------------------------------ unités

/** Unités fractionnaires (1,25.uf…) : même échelle que Int.u. */
internal val Float.uf: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).dp

/**
 * Place l'élément avec son bord gauche en [x] et sa première ligne de base (la dernière si
 * [derniere]) en [base] : mesures de la maquette, indépendantes des marges internes de la police.
 */
internal fun Modifier.uneLigne(x: Dp, base: Dp, derniere: Boolean = false): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    val b = p[if (derniere) LastBaseline else FirstBaseline].let { if (it == androidx.compose.ui.layout.AlignmentLine.Unspecified) p.height else it }
    val px = x.roundToPx()
    val py = base.roundToPx() - b
    layout(px + p.width, maxOf(0, py + p.height)) { p.place(px, py) }
}

// ------------------------------------------------------------------ sujet de « la une »

/** Ce que présente l'affiche : l'élément sélectionné (app, raccourci, NBA, bibliothèque…). */
internal data class UneSujet(
    /** Identité (change d'animation seulement si elle change). */
    val cle: String,
    /** Nom en lettres géantes, et nom suivant (en contour), null sur le dernier élément. */
    val titre: String,
    val suivant: String?,
    /** « FAVORI », « MES APPS », « RACCOURCI »… */
    val rubrique: String,
    /** Position dans la rangée (01 / 06) ; total = 0 : pas de compteur. */
    val position: Int,
    val total: Int,
    val detail: String,
    /** Libellé de la touche OK (null : pas d'action). */
    val action: String?,
    val couleur: Color,
    val app: AppInfo? = null,
    val icone: ImageVector? = null,
    /** Ordre global : le nom géant entre du côté de la flèche. */
    val ordre: Int,
)

// ------------------------------------------------------------------ écran

/**
 * Design « À la une » : l'accueil est une affiche, l'app sélectionnée fait la une.
 *  - en haut, l'écran prend la couleur de l'app (aplat calibré, fondu de couleur) : salutation
 *    et boutons rapides en pilules, grande heure, score NBA façon journal sportif ;
 *  - ligne « à la une » (cartouche, rangée, compteur, rubrique, touches) puis le NOM de l'app
 *    en lettres géantes, suivi du nom suivant en contour ;
 *  - en bas, une bande sombre teintée : les favoris et « Mes apps » en index typographique
 *    (icône + nom), « Toutes les apps » en dernier.
 * Navigation déterministe (voir UneNav) : ▲ ▼ vont toujours à l'élément le plus aligné.
 */
@Composable
fun HomeUne(ctx: HomeContext) {
    val nav = remember { UneNav() }
    var sujet by remember { mutableStateOf<UneSujet?>(null) }
    val montrer: (UneSujet) -> Unit = { sujet = it }
    // Zone de modules sélectionnée : le titre du module (« Météo · Lyon »…) fait la une
    val titreModule = FocusModules.titre
    val affiche = sujet?.let { s ->
        if (s.cle == UneNav.CLE_ZONE && titreModule != null) s.copy(cle = "zone:$titreModule", titre = titreModule) else s
    }
    val t = rememberMaintenant()
    val soir = uneSoir(t)

    // Aplat : la couleur ne change qu'après 150 ms d'immobilité (flèche maintenue : pas de clignotement).
    // Premier élément sélectionné : couleur posée d'emblée (pas de fondu depuis le gris au démarrage).
    val marque = sujet?.couleur ?: UneGraphite
    val cible = remember(marque, soir) { uneAplat(marque, soir) }
    var retenu by remember { mutableStateOf<UneAplat?>(null) }
    val avecSujet = sujet != null
    LaunchedEffect(cible, avecSujet) {
        if (!avecSujet) return@LaunchedEffect
        if (retenu != null && retenu != cible) delay(150)
        retenu = cible
    }
    val affiche = retenu ?: cible
    // Lu seulement au dessin : le fondu ne recompose rien
    val aplat = animateColorAsState(
        affiche.fond, if (retenu == null) snap() else tween(280), label = "aplatUne",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(UneFond)
    ) {
        UneAffiche(ctx, nav, affiche, soir, aplat, t, montrer)
        UneIndex(ctx, nav, aplat, montrer)
    }
}

/** Affiche (aplat de couleur) : ligne haute, heure, NBA, filet, ligne « à la une », nom géant. */
@Composable
private fun UneAffiche(
    ctx: HomeContext,
    nav: UneNav,
    sujet: UneSujet?,
    soir: Boolean,
    aplat: State<Color>,
    t: Long,
    montrer: (UneSujet) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(380.u)
            .clipToBounds()
            .drawBehind {
                drawRect(aplat.value)
                drawRect(UneVoile)
            }
    ) {
        UneRuban(sujet, soir, Modifier.fillMaxSize())

        UneLigneHaute(ctx, nav, aplat, t, montrer)
        UneHorloge(t)
        UneZone(
            ctx, nav, montrer,
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 48.u),
        )
        // Filet sous l'heure et le score
        Box(
            Modifier
                .padding(top = 183.u)
                .fillMaxWidth()
                .padding(horizontal = 48.u)
                .height(1.25f.uf)
                .background(Color.White.copy(alpha = 0.3f))
        )
        UneManchette(ctx, sujet)
    }
}

// ------------------------------------------------------------------ ligne haute

/** Un bouton de la ligne haute. */
private class UnePilule(
    val cle: String,
    val libelle: String,
    val icone: ImageVector?,
    val detail: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
    val estReglages: Boolean = false,
    val statut: Boolean = false,
    val maj: Boolean = false,
)

/** Salutation et date à gauche ; pilules Sources · Wi-Fi · Bluetooth · Réglages · [Mise à jour] à droite. */
@Composable
private fun UneLigneHaute(
    ctx: HomeContext,
    nav: UneNav,
    aplat: State<Color>,
    t: Long,
    montrer: (UneSujet) -> Unit,
) {
    val pilules = buildList {
        ctx.quickActions().forEachIndexed { i, a ->
            val detail = when (a.icon) {
                ProjoIcons.Sources -> "Entrée HDMI"
                ProjoIcons.Wifi -> "Réseau"
                ProjoIcons.Bluetooth -> "Appareils sans fil"
                else -> "Volume, réseau, design…"
            }
            add(
                UnePilule(
                    "pil:$i", a.label, a.icon, detail, a.onClick, a.onLongClick,
                    estReglages = a.estReglages,
                    // Point vert : connecté (le Wi-Fi seulement : l'état Bluetooth n'est pas connu ici)
                    statut = a.icon == ProjoIcons.Wifi && a.label != "Hors ligne",
                )
            )
        }
        if (ctx.majDisponible) {
            add(UnePilule("pil:maj", "Mise à jour", null, "Nouvelle version prête", ctx.actions.update, null, maj = true))
        }
    }
    nav.pilules = pilules.map { it.cle }

    Row(
        Modifier
            .padding(top = 27.u)
            .fillMaxWidth()
            .padding(horizontal = 48.u)
            .height(32.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                salutation(t), color = Color.White, fontSize = 17.us, fontWeight = FontWeight.Bold,
                fontFamily = UnePolice, letterSpacing = (-0.01).em, maxLines = 1,
            )
            Text(
                dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                color = Color.White.copy(alpha = 0.8f), fontSize = 17.us, fontWeight = FontWeight.Medium,
                fontFamily = UnePolice, letterSpacing = (-0.01).em,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 9.u, end = 12.u),
            )
        }
        Row(
            Modifier.onPreviewKeyEvent { nav.toucheRaccourcis(it, ctx) },
            horizontalArrangement = Arrangement.spacedBy(7.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pilules.forEachIndexed { i, p ->
                val sujet = UneSujet(
                    cle = p.cle, titre = p.libelle, suivant = pilules.getOrNull(i + 1)?.libelle,
                    rubrique = "Raccourci", position = i + 1, total = pilules.size, detail = p.detail,
                    action = "Ouvrir", couleur = UneGraphite, icone = p.icone ?: ProjoIcons.MiseAJour, ordre = i,
                )
                UneBouton(
                    onClick = p.onClick,
                    onLongClick = p.onLongClick,
                    onFocus = {
                        nav.focusCle = p.cle
                        ctx.sansSelection()
                        montrer(sujet)
                    },
                    hauteur = 32.u,
                    rayon = 16.u,
                    fond = if (p.maj) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    bordure = if (p.maj) null else Color.White.copy(alpha = 0.45f),
                    padding = PaddingValues(start = if (p.maj) 11.u else 10.u, end = 13.u),
                    modifier = Modifier
                        .testTag("rapide:" + p.libelle)
                        .onGloballyPositioned { nav.centres[p.cle] = it.positionInRoot().x + it.size.width / 2f }
                        .then(nav.cible(p.cle))
                        .then(if (p.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier),
                ) { focus ->
                    if (p.maj) {
                        Box(Modifier.size(6.u).clip(CircleShape).background(UneJaune))
                        Spacer(Modifier.width(7.u))
                    } else if (p.icone != null) {
                        Box(Modifier.size(15.u)) {
                            Icon(p.icone, contentDescription = null, modifier = Modifier.size(15.u))
                            if (p.statut) UnePoint(aplat, focus, Modifier.offset(x = 10.u, y = (-3).u))
                        }
                        Spacer(Modifier.width(6.u))
                    }
                    Text(
                        p.libelle, fontSize = 14.us, fontWeight = FontWeight.SemiBold, fontFamily = UnePolice,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.u),
                    )
                }
            }
        }
    }
}

/** Point d'état vert, cerclé de la couleur de l'aplat (de blanc sur la pilule sélectionnée). */
@Composable
private fun UnePoint(aplat: State<Color>, focus: Boolean, modifier: Modifier) {
    val cercle = 1.25f.uf
    Spacer(
        modifier
            .size(6.u)
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(if (focus) Color.White else aplat.value, radius = r + cercle.toPx())
                drawCircle(UneVert, radius = r)
            }
    )
}

// ------------------------------------------------------------------ heure

/** « 21:42 » en Black géant, deux-points à 55 %. */
@Composable
private fun UneHorloge(t: Long) {
    val heure = heureTexte(t)
    val texte = remember(heure) {
        buildAnnotatedString {
            val i = heure.indexOf(':')
            if (i < 0) {
                append(heure)
            } else {
                append(heure.substring(0, i))
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.55f))) { append(":") }
                append(heure.substring(i + 1))
            }
        }
    }
    BasicText(
        texte,
        style = TextStyle(
            color = Color.White,
            fontSize = 103.us,
            fontWeight = FontWeight.Black,
            fontFamily = UnePolice,
            letterSpacing = (-0.05).em,
            fontFeatureSettings = "tnum",
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.uneLigne(42.u, 150.u),
    )
}

// ------------------------------------------------------------------ ligne « à la une »

/** Cartouche (icône + nom), rubrique, compteur « 01 / 06 », détail ; touches à droite. */
@Composable
private fun UneManchette(ctx: HomeContext, sujet: UneSujet?) {
    if (sujet == null) return
    Row(
        Modifier
            .padding(top = 195.u)
            .fillMaxWidth()
            .padding(horizontal = 48.u)
            .height(32.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cartouche noir : logo de l'élément sélectionné
        Row(
            Modifier
                .height(32.u)
                .clip(RoundedCornerShape(7.u))
                .background(UneCartouche)
                .padding(start = 7.u, end = 11.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val app = sujet.app
            if (app != null) {
                val paquet = ctx.ui.iconSourceOf(app.packageName)
                // key : l'icône est rechargée pour chaque app (sinon l'état garde l'icône précédente)
                key(paquet) {
                    UnePastille(app, rememberAppIcon(paquet, ctx.icons), null, UneCartouche, 22.u, 5.u, lisere = false)
                }
            } else if (sujet.icone != null) {
                UnePastille(null, null, sujet.icone, sujet.couleur, 22.u, 5.u, lisere = false)
            }
            Spacer(Modifier.width(7.u))
            Text(
                sujet.titre, color = Color.White, fontSize = 13.us, fontWeight = FontWeight.Bold,
                fontFamily = UnePolice, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.u),
            )
        }
        Spacer(Modifier.width(14.u))
        Text(
            sujet.rubrique.uppercase(Locale.FRENCH), color = Color.White.copy(alpha = 0.85f), fontSize = 11.us,
            fontWeight = FontWeight.Bold, fontFamily = UnePolice, letterSpacing = 0.16.em, maxLines = 1,
        )
        if (sujet.total > 0) {
            Spacer(Modifier.width(10.u))
            BasicText(
                buildAnnotatedString {
                    append("%02d".format(sujet.position))
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)) {
                        append(" / %02d".format(sujet.total))
                    }
                },
                style = TextStyle(
                    color = Color.White, fontSize = 20.us, fontWeight = FontWeight.ExtraBold, fontFamily = UnePolice,
                    letterSpacing = (-0.01).em, fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
            )
        }
        Text(
            if (sujet.detail.isEmpty()) "" else "  ·  " + sujet.detail,
            color = Color.White.copy(alpha = 0.85f), fontSize = 14.us, fontWeight = FontWeight.SemiBold,
            fontFamily = UnePolice, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.u),
        )
        if (sujet.total > 1) {
            UneTouche(fleches = true, texte = null, libelle = "Parcourir", discret = true)
        }
        if (sujet.action != null) {
            Spacer(Modifier.width(15.u))
            UneTouche(fleches = false, texte = "OK", libelle = sujet.action, discret = false)
        }
    }
}

/** Touche de télécommande dessinée (« ◀ ▶ », « OK ») suivie de son effet. */
@Composable
private fun UneTouche(fleches: Boolean, texte: String?, libelle: String, discret: Boolean) {
    val encre = Color.White.copy(alpha = if (discret) 0.8f else 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(19.u)
                .widthIn(min = 22.u)
                .border(1.25f.uf, encre, RoundedCornerShape(4.u))
                .padding(horizontal = 5.u),
            contentAlignment = Alignment.Center,
        ) {
            if (fleches) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UneTriangle(encre, versDroite = false, modifier = Modifier.size(7.u, 8.u))
                    Spacer(Modifier.width(3.u))
                    UneTriangle(encre, versDroite = true, modifier = Modifier.size(7.u, 8.u))
                }
            } else if (texte != null) {
                Text(texte, color = encre, fontSize = 10.us, fontWeight = FontWeight.ExtraBold, fontFamily = UnePolice, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.u))
        Text(
            libelle, color = encre, fontSize = 14.us,
            fontWeight = if (discret) FontWeight.SemiBold else FontWeight.Bold, fontFamily = UnePolice, maxLines = 1,
        )
    }
}

/** Petit triangle plein (◀ ▶ ▲) dessiné : pas de dépendance aux glyphes de la police. */
@Composable
internal fun UneTriangle(couleur: Color, versDroite: Boolean, modifier: Modifier, versHaut: Boolean = false) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                when {
                    versHaut -> { moveTo(0f, h); lineTo(w / 2f, 0f); lineTo(w, h) }
                    versDroite -> { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h) }
                    else -> { moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h) }
                }
                close()
            }
            onDrawBehind { drawPath(p, couleur) }
        }
    )
}

// ------------------------------------------------------------------ nom géant

/** Nom géant de l'élément sélectionné et nom suivant en contour ; entrée du côté de la flèche. */
@Composable
private fun UneRuban(sujet: UneSujet?, soir: Boolean, modifier: Modifier) {
    val mesureur = rememberTextMeasurer(cacheSize = 16)
    AnimatedContent(
        targetState = sujet,
        modifier = modifier,
        transitionSpec = {
            val sens = if ((targetState?.ordre ?: 0) >= (initialState?.ordre ?: 0)) 1 else -1
            (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { sens * it / 12 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -sens * it / 12 } + fadeOut(tween(180)))
        },
        contentKey = { it?.cle },
        label = "uneGeant",
    ) { s ->
        if (s == null) {
            Spacer(Modifier.fillMaxSize())
        } else {
            val encre = remember(s.couleur, soir) { uneAplat(s.couleur, soir).geant }
            UneRubanTexte(s.titre, s.suivant, encre, mesureur)
        }
    }
}

/** Mesures du nom géant et du nom suivant (calculées une fois par nom et par taille d'écran). */
private class UneRubanMesure(
    val geant: TextLayoutResult,
    val suivant: TextLayoutResult?,
    val etiquette: TextLayoutResult?,
    val tailleSuivantPx: Float,
)

private val UneStyleGeant = TextStyle(
    fontFamily = UnePolice,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.055).em,
)

/**
 * Nom géant (134.u au plus, réduit pour tenir dans 750.u) dessiné sur sa ligne de base, puis
 * le nom suivant en contour (60 % du géant au plus, réduit pour tenir avant la marge droite,
 * masqué sous 48.u), surmonté de « SUIVANT › ». Contour : TextStyle.drawStyle, aucun calque.
 */
@Composable
private fun UneRubanTexte(titre: String, suivant: String?, encre: Color, mesureur: TextMeasurer) {
    val echelle = LocalScale.current
    val densite = LocalDensity.current
    val k = with(densite) { 1.dp.toPx() } * echelle
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val m = uneMesurerRuban(titre, suivant, mesureur, echelle, k)
                val x0 = 40f * k
                val base = 344f * k
                val contour = Stroke(width = 1.5f * k, join = StrokeJoin.Round)
                onDrawBehind {
                    drawText(m.geant, color = encre, topLeft = Offset(x0, base - m.geant.firstBaseline))
                    val s = m.suivant ?: return@onDrawBehind
                    val gx = x0 + m.geant.size.width + 38f * k
                    drawText(
                        s, color = Color.White.copy(alpha = 0.42f),
                        topLeft = Offset(gx, base - s.firstBaseline), drawStyle = contour,
                    )
                    val e = m.etiquette ?: return@onDrawBehind
                    // Au-dessus des capitales du nom suivant (hauteur de capitale ≈ 0,71 em)
                    val hautCapitales = base - 0.711f * m.tailleSuivantPx
                    drawText(
                        e, color = Color.White.copy(alpha = 0.7f),
                        topLeft = Offset(gx + 2f * k, hautCapitales - 17f * k - e.firstBaseline),
                    )
                }
            }
    )
}

private fun uneMesurerRuban(
    titre: String,
    suivant: String?,
    mesureur: TextMeasurer,
    echelle: Float,
    k: Float,
): UneRubanMesure {
    fun mesure(t: String, tailleU: Float, largeurMax: Int = Constraints.Infinity) = mesureur.measure(
        AnnotatedString(t),
        style = UneStyleGeant.copy(fontSize = (tailleU * echelle).sp),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = largeurMax),
    )
    val largeurMax = 750f * k
    var taille = 134f
    var geant = mesure(titre, taille)
    if (geant.size.width > largeurMax) {
        taille = maxOf(75f, floor(taille * largeurMax / geant.size.width / 2f) * 2f)
        geant = mesure(titre, taille, largeurMax.toInt())
    }
    var lSuivant: TextLayoutResult? = null
    var tSuivant = 0f
    if (suivant != null) {
        val dispo = 912f * k - (40f * k + geant.size.width + 38f * k)
        tSuivant = floor(taille * 0.6f / 2f) * 2f
        var l = mesure(suivant, tSuivant)
        if (l.size.width > dispo) {
            tSuivant = floor(tSuivant * dispo / l.size.width / 2f) * 2f
            if (tSuivant >= 48f) l = mesure(suivant, tSuivant)
        }
        if (tSuivant >= 48f && l.size.width <= dispo + 1f) lSuivant = l
    }
    val etiquette = if (lSuivant == null) null else mesureur.measure(
        AnnotatedString("SUIVANT  ›"),
        style = TextStyle(
            fontFamily = UnePolice, fontWeight = FontWeight.Bold,
            fontSize = (10f * echelle).sp, letterSpacing = 0.16.em,
        ),
        maxLines = 1,
        softWrap = false,
    )
    return UneRubanMesure(geant, lSuivant, etiquette, tSuivant * k)
}

// ------------------------------------------------------------------ éléments communs

/**
 * Élément focalisable de « Une » (pilule, onglet, entrée d'index). Sélectionné : fond plein
 * [focusFond], encre [focusEncre], agrandi à 104 %, anneau blanc 2.u + liseré sombre 1.u à
 * l'intérieur. Tout est dessiné (drawWithCache) : le fondu ne recompose rien et ne change
 * pas la taille de l'élément. [repere] : petit trait blanc sous l'élément (cible de ▲ / ▼).
 */
@Composable
internal fun UneBouton(
    onClick: () -> Unit,
    onFocus: () -> Unit,
    hauteur: Dp,
    rayon: Dp,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    fond: Color = Color.Transparent,
    bordure: Color? = null,
    pointille: Boolean = false,
    encre: Color = Color.White,
    focusFond: Color = Color.White,
    focusEncre: Color = UneEncreSombre,
    repere: Boolean = false,
    contenu: @Composable RowScope.(focus: Boolean) -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val p = animateFloatAsState(
        if (focus) 1f else 0f, tween(160, easing = FastOutSlowInEasing), label = "focusUne",
    )
    val k = with(LocalDensity.current) { 1.u.toPx() }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .zIndex(if (focus) 1f else 0f)
            .graphicsLayer {
                val s = 1f + 0.04f * p.value
                scaleX = s
                scaleY = s
            }
            .drawWithCache {
                val r = rayon.toPx()
                val trait = Stroke(width = 1.25f * k)
                val tirets = Stroke(width = k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 3f * k)))
                val ecart = Stroke(width = k)
                val anneau = Stroke(width = 2f * k)
                onDrawBehind {
                    val q = p.value
                    if (fond.alpha > 0f && q < 1f) drawRoundRect(fond, cornerRadius = CornerRadius(r), alpha = 1f - q)
                    if (q > 0f) drawRoundRect(focusFond, cornerRadius = CornerRadius(r), alpha = q)
                    if (bordure != null && q < 1f) {
                        val s = if (pointille) tirets else trait
                        val m = s.width / 2f
                        drawRoundRect(
                            bordure, topLeft = Offset(m, m), size = Size(size.width - 2 * m, size.height - 2 * m),
                            cornerRadius = CornerRadius(r - m), style = s, alpha = 1f - q,
                        )
                    }
                    if (q > 0f) {
                        uneContour(k / 2f, r, ecart, UneEncreSombre, q)
                        uneContour(2f * k, r, anneau, Color.White, q)
                    }
                    if (repere && q == 0f) {
                        drawRoundRect(
                            Color.White,
                            topLeft = Offset(size.width / 2f - 10f * k, size.height + 0.5f * k),
                            size = Size(20f * k, 2.5f * k),
                            cornerRadius = CornerRadius(1.25f * k),
                        )
                    }
                }
            }
            .height(hauteur)
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(rayon)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = encre,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = focusEncre,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = focusEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            contenu(focus)
        }
    }
}

/** Contour arrondi centré à [decalage] px à l'extérieur des bords. */
private fun DrawScope.uneContour(decalage: Float, rayon: Float, trait: Stroke, couleur: Color, alpha: Float) {
    drawRoundRect(
        color = couleur,
        topLeft = Offset(-decalage, -decalage),
        size = Size(size.width + 2 * decalage, size.height + 2 * decalage),
        cornerRadius = CornerRadius(rayon + decalage),
        style = trait,
        alpha = alpha,
    )
}

/**
 * Icône carrée arrondie : Source HDMI dessinée, icône de l'app sur cartouche noir (liseré
 * blanc 14 %), icône vectorielle sur fond de couleur, sinon l'initiale.
 */
@Composable
internal fun UnePastille(
    app: AppInfo?,
    icone: ImageBitmap?,
    vecteur: ImageVector?,
    fond: Color,
    taille: Dp,
    rayon: Dp,
    lisere: Boolean,
) {
    val forme = RoundedCornerShape(rayon)
    Box(
        Modifier
            .size(taille)
            .clip(forme)
            .background(if (app?.isSpecial == true) UneHdmi else fond),
        contentAlignment = Alignment.Center,
    ) {
        when {
            app?.isSpecial == true -> Icon(
                ProjoIcons.Sources, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(taille * 0.6f),
            )
            icone != null -> Image(
                bitmap = icone, contentDescription = null, filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            vecteur != null -> Icon(
                vecteur, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(taille * 0.6f),
            )
            app != null -> Text(
                app.label.take(1).uppercase(), color = Color.White, fontSize = 12.us,
                fontWeight = FontWeight.Black, fontFamily = UnePolice,
            )
        }
        if (lisere) Box(Modifier.matchParentSize().border(0.75f.uf, Color.White.copy(alpha = 0.14f), forme))
    }
}

/** Rubrique d'une app (« Vidéo et streaming »…). */
internal fun uneRubrique(app: AppInfo): String = AppCategories.rubrique(app).titre

/** Touche « Menu » de la télécommande = même menu que l'appui long. */
internal fun Modifier.uneToucheMenu(onMenu: () -> Unit): Modifier = onPreviewKeyEvent {
    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
        onMenu()
        true
    } else {
        false
    }
}
