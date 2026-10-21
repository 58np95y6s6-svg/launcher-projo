package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Design « Chaînes » : l'accueil façon menu des chaînes d'une console de salon.
 *  - fond blanc grisé à fines rayures, grille de 4 × 3 tuiles arrondies au liseré gris ;
 *  - pages de 12 tuiles (favoris, « Mes apps », « Toutes les apps »), ◀ ▶ aux bords,
 *    glissement d'une page à l'autre, cases vides grises sur la dernière page ;
 *  - tuile sélectionnée : légèrement agrandie, contour bleu clair lumineux, petite main-curseur ;
 *  - en bas, bande grise creusée au centre : bouton rond « iBi » (Réglages) à gauche,
 *    heure à segments et date au centre, bouton rond « enveloppe » à droite (modules),
 *    pastilles Wi-Fi / Bluetooth à gauche, Sources / Mise à jour à droite.
 * Navigation entièrement déterministe (voir GrilleChaines) ; l'enveloppe ouvre la zone de
 * modules en grand, par-dessus l'accueil.
 */

// ------------------------------------------------------------------ mesures (unités .u)

/** Tuile : proche du 16:9. */
private const val CH_TUILE_L = 196
private const val CH_TUILE_H = 112
private const val CH_ECART = 14
private const val CH_RAYON = 14
/** Haut de la grille. */
private const val CH_HAUT = 26
/** Hauteur de la bande du bas, bord haut sur les côtés et fond du creux (dans la bande). */
private const val CH_BANDE = 150
private const val CH_BANDE_BORD = 30
private const val CH_BANDE_CREUX = 74
private const val CH_BANDE_DEMI_FOND = 150
private const val CH_BANDE_RACCORD = 78
/** Boutons ronds de la bande. */
private const val CH_ROND = 70
private const val CH_PASTILLE = 38

private val CH_GRILLE_L get() = CH_TUILE_L * GrilleChaines.COLONNES + CH_ECART * (GrilleChaines.COLONNES - 1)
private val CH_GRILLE_H get() = CH_TUILE_H * GrilleChaines.RANGEES + CH_ECART * (GrilleChaines.RANGEES - 1)

/** Zone de modules, ouverte en grand par l'enveloppe : carte blanche, focus bleu clair. */
private val ChainesStyleModules = NbaStyle(
    largeur = 440,
    rayon = 18,
    fond = Color.White,
    bordure = ChainesLisere,
    texte = ChainesEncre,
    texteDiscret = Color(0xFF6E747A),
    ongletActif = Color(0xFFE2F4FC),
    focusFond = Color(0xFFCDEEFC),
    focusTexte = Color(0xFF0E4A63),
    pastilleFond = Color(0xFFEFF1F3),
    zoneLargeur = 720,
    zoneHauteur = 290,
)

// ------------------------------------------------------------------ état

/** État de l'écran : page affichée, focus à donner, bande du bas, panneau des modules. */
private class EtatChaines {
    var page by mutableIntStateOf(0)
    /** Tuile à sélectionner dès qu'elle est composée (après un changement de page). */
    var cible by mutableStateOf<Int?>(null)
    /** Tuile sélectionnée (-1 : aucune), pour passer sa rangée au premier plan. */
    var tuileFocus by mutableIntStateOf(-1)
    var panneau by mutableStateOf(false)
    val entreeModules = FocusRequester()

    private val tuiles = HashMap<Int, FocusRequester>()
    fun tuile(i: Int): FocusRequester = tuiles.getOrPut(i) { FocusRequester() }

    /** Boutons de la bande, de gauche à droite : clé → demandeur de focus, centre (px). */
    var ordreBande: List<String> = emptyList()
    val bande = HashMap<String, FocusRequester>()
    val centresBande = HashMap<String, Float>()
    /** ▼ depuis une tuile vers la bande : (tuile, bouton) pour y revenir par ▲. */
    var retour: Pair<Int, String>? = null

    /** Bord gauche de la grille (px, repère de la fenêtre) et pas d'une colonne (px). */
    var grilleGauche = 0f
    var pasColonne = 0f
    var demiTuile = 0f

    fun centreColonne(c: Int) = grilleGauche + c * pasColonne + demiTuile

    /** Sélectionne la tuile [i], en changeant de page si besoin (glissement). */
    fun allerTuile(i: Int) {
        val p = GrilleChaines.page(i)
        if (p != page) {
            page = p
            cible = i
        } else if (runCatching { tuile(i).requestFocus() }.isFailure) {
            cible = i
        }
    }

    fun allerBande(cle: String) {
        runCatching { bande[cle]?.requestFocus() }
    }

    /** Touche sur la tuile [i] (n tuiles en tout). */
    fun toucheTuile(e: KeyEvent, i: Int, n: Int): Boolean {
        val sens = sensDe(e) ?: return false
        if (e.type != KeyEventType.KeyDown) return true
        when (val v = GrilleChaines.voisin(i, n, sens)) {
            GrilleChaines.BANDE -> {
                val x = centreColonne(GrilleChaines.colonne(i))
                val cle = ordreBande.minByOrNull { kotlin.math.abs((centresBande[it] ?: Float.MAX_VALUE) - x) } ?: return true
                retour = i to cle
                allerBande(cle)
            }
            i -> Unit
            else -> allerTuile(v)
        }
        return true
    }

    /** Touche sur le bouton [cle] de la bande. */
    fun toucheBande(e: KeyEvent, cle: String, n: Int): Boolean {
        val sens = sensDe(e) ?: return false
        if (e.type != KeyEventType.KeyDown) return true
        val k = ordreBande.indexOf(cle)
        when (sens) {
            Sens.GAUCHE, Sens.DROITE -> {
                val suivant = ordreBande.getOrNull(if (sens == Sens.GAUCHE) k - 1 else k + 1)
                if (suivant != null) {
                    retour = null
                    allerBande(suivant)
                }
            }
            Sens.HAUT -> {
                val r = retour
                val arrivee = if (r != null && r.second == cle && r.first < n && GrilleChaines.page(r.first) == page) r.first
                else {
                    val x = centresBande[cle] ?: 0f
                    GrilleChaines.basDeColonnes(page, n).minByOrNull { (c, _) -> kotlin.math.abs(centreColonne(c) - x) }?.second
                }
                retour = null
                if (arrivee != null) allerTuile(arrivee)
            }
            Sens.BAS -> Unit
        }
        return true
    }

    private fun sensDe(e: KeyEvent): Sens? = when (e.key) {
        Key.DirectionLeft -> Sens.GAUCHE
        Key.DirectionRight -> Sens.DROITE
        Key.DirectionUp -> Sens.HAUT
        Key.DirectionDown -> Sens.BAS
        else -> null
    }
}

/** Bouton de la bande du bas. */
private class BoutonBande(
    val cle: String,
    val etiquette: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
    val rond: Boolean,
    val contenu: @Composable (Boolean) -> Unit,
    val requete: FocusRequester? = null,
)

// ------------------------------------------------------------------ écran

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeChaines(ctx: HomeContext) {
    val ui = ctx.ui
    val tuiles = remember(ui.loaded, ui.favorites, ui.homeApps) { tuilesChaines(ui) }
    val n = tuiles.size
    val pages = GrilleChaines.pages(n)
    val etat = remember { EtatChaines() }
    val page = etat.page.coerceIn(0, pages - 1)
    LaunchedEffect(pages) { if (etat.page > pages - 1) etat.page = pages - 1 }

    // Focus à donner après un changement de page : la tuile n'existe qu'une fois la page composée
    LaunchedEffect(etat.cible) {
        val c = etat.cible ?: return@LaunchedEffect
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { etat.tuile(c).requestFocus() }.isSuccess) break
        }
        etat.cible = null
    }

    // Panneau des modules : le focus y entre dès qu'il est composé
    LaunchedEffect(etat.panneau) {
        if (!etat.panneau) return@LaunchedEffect
        for (essai in 1..6) {
            withFrameNanos {}
            if (runCatching { etat.entreeModules.requestFocus() }.isSuccess) break
        }
    }
    // Sortie de la zone de modules (◀ ou Retour) : panneau fermé, retour à la 1re tuile
    val sortieModules: () -> Unit = {
        etat.panneau = false
        etat.page = 0
        ctx.sortieNba()
    }

    val densite = LocalDensity.current
    val grilleL = CH_GRILLE_L.u
    val pas = (CH_TUILE_L + CH_ECART).u
    val demi = (CH_TUILE_L / 2).u

    Box(
        Modifier
            .fillMaxSize()
            .fondChaines()
    ) {
        // --- Relais du focus : premierFocus (Maison, sortie des modules) et focusBiblio
        // (retour de la bibliothèque) visent des tuiles qui ne sont composées que sur leur page.
        if (page != 0) Relais(ctx.premierFocus) { etat.page = 0; etat.cible = 0 }
        val pageBiblio = GrilleChaines.page(n - 1)
        if (page != pageBiblio) Relais(ctx.focusBiblio) { etat.page = pageBiblio; etat.cible = n - 1 }

        // --- Grille paginée
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = CH_HAUT.u)
                .height(CH_GRILLE_H.u + 24.u)
                .testTag("chaines:grille"),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .width(grilleL)
                    .height(CH_GRILLE_H.u)
                    .onGloballyPositioned {
                        etat.grilleGauche = it.positionInRoot().x
                        with(densite) {
                            etat.pasColonne = pas.toPx()
                            etat.demiTuile = demi.toPx()
                        }
                    }
            )
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val d = if (targetState > initialState) 1 else -1
                    slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it * d } togetherWith
                        slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it * d }
                },
                label = "page",
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) { p ->
                PageChaines(ctx, etat, tuiles, p)
            }
        }

        // --- Flèches de page, aux bords de l'écran
        if (page > 0) OngletPage(droite = false, Modifier.align(Alignment.TopStart).padding(top = (CH_HAUT + CH_GRILLE_H / 2 - 44).u))
        if (page < pages - 1) OngletPage(droite = true, Modifier.align(Alignment.TopEnd).padding(top = (CH_HAUT + CH_GRILLE_H / 2 - 44).u))

        // --- Bande du bas
        BandeChaines(ctx, etat, n, Modifier.align(Alignment.BottomCenter))

        // --- Panneau des modules (enveloppe)
        if (etat.panneau) PanneauModules(ctx, etat, sortieModules)
    }
}

/** Fond blanc grisé à fines rayures horizontales. */
private fun Modifier.fondChaines(): Modifier = drawWithCache {
    val degrade = Brush.verticalGradient(listOf(ChainesFondHaut, ChainesFondBas))
    val pasRayure = 3f * density
    val rayure = ChainesRayure.copy(alpha = 0.035f)
    onDrawBehind {
        drawRect(degrade)
        var y = 0f
        while (y < size.height) {
            drawLine(rayure, Offset(0f, y), Offset(size.width, y), strokeWidth = density * 0.9f)
            y += pasRayure
        }
    }
}

/**
 * Élément invisible qui porte un demandeur de focus quand sa tuile n'est pas à l'écran :
 * le focus qu'il reçoit est aussitôt passé à la bonne tuile, après changement de page.
 */
@Composable
private fun Relais(requete: FocusRequester, surFocus: () -> Unit) {
    Box(
        Modifier
            .size(1.dp)
            .focusRequester(requete)
            .onFocusChanged { if (it.isFocused) surFocus() }
            .focusable()
    )
}

// ------------------------------------------------------------------ page et tuiles

@Composable
private fun PageChaines(ctx: HomeContext, etat: EtatChaines, tuiles: List<TuileChaine>, p: Int) {
    val n = tuiles.size
    Column(
        Modifier.width(CH_GRILLE_L.u),
        verticalArrangement = Arrangement.spacedBy(CH_ECART.u),
    ) {
        for (r in 0 until GrilleChaines.RANGEES) {
            val debut = p * GrilleChaines.PAR_PAGE + r * GrilleChaines.COLONNES
            val avantPlan = etat.tuileFocus in debut until debut + GrilleChaines.COLONNES
            Row(
                Modifier.zIndex(if (avantPlan) 1f else 0f),
                horizontalArrangement = Arrangement.spacedBy(CH_ECART.u),
            ) {
                for (c in 0 until GrilleChaines.COLONNES) {
                    val i = debut + c
                    if (i < n) TuileChaines(ctx, etat, tuiles[i], i, n)
                    else CaseVide()
                }
            }
        }
    }
}

/** Case vide de la dernière page : grise, liseré, rien dedans. */
@Composable
private fun CaseVide() {
    val forme = RoundedCornerShape(CH_RAYON.u)
    Box(
        Modifier
            .size(CH_TUILE_L.u, CH_TUILE_H.u)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(ChainesVideHaut, ChainesVideBas)))
            .border(2.u, ChainesLisere.copy(alpha = 0.8f), forme)
            .padding(2.u)
            .border(1.u, Color.White.copy(alpha = 0.55f), RoundedCornerShape((CH_RAYON - 2).u))
    )
}

@Composable
private fun TuileChaines(ctx: HomeContext, etat: EtatChaines, tuile: TuileChaine, i: Int, n: Int) {
    val forme = RoundedCornerShape(CH_RAYON.u)
    var focus by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focus) 1f else 0f, tween(180), label = "focus")
    val app = (tuile as? TuileChaine.App)?.app
    val visuel = app?.let { rememberAppVisual(ctx.ui.iconSourceOf(it.packageName), ctx.icons, FormatVisuel.pour(CH_TUILE_L, CH_TUILE_H)) }
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val rayon = CH_RAYON.u
    val trait = 3.u

    Box(
        Modifier
            .testTag(tuile.etiquette)
            .zIndex(if (focus) 1f else 0f)
            .size(CH_TUILE_L.u, CH_TUILE_H.u)
            .graphicsLayer { val s = 1f + 0.06f * f; scaleX = s; scaleY = s }
            .drawWithContent {
                if (f > 0f) lueurBleue(rayon.toPx(), trait.toPx(), f)
                drawContent()
                if (f > 0f) {
                    val t = trait.toPx()
                    drawRoundRect(
                        ChainesBleu.copy(alpha = f), Offset(-t / 2f, -t / 2f), Size(size.width + t, size.height + t),
                        CornerRadius(rayon.toPx() + t / 2f), style = Stroke(t),
                    )
                }
            }
    ) {
        Surface(
            onClick = {
                when (tuile) {
                    is TuileChaine.App -> ctx.actions.launch(tuile.app)
                    TuileChaine.Biblio -> ctx.actions.openLibrary()
                    TuileChaine.Aide -> Unit
                }
            },
            onLongClick = app?.let { a -> { ctx.actions.openMenu(a) } },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(etat.tuile(i))
                .then(if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier)
                .then(if (tuile == TuileChaine.Biblio) Modifier.focusRequester(ctx.focusBiblio) else Modifier)
                .onFocusChanged {
                    focus = it.isFocused
                    if (it.isFocused) {
                        etat.tuileFocus = i
                        if (app != null) ctx.surFocus(app, couleur) else ctx.sansSelection()
                    } else if (etat.tuileFocus == i) {
                        etat.tuileFocus = -1
                    }
                }
                .onPreviewKeyEvent { e ->
                    // Touche « Menu » de la télécommande = même menu que l'appui long
                    if (e.key == Key.Menu) {
                        if (e.type == KeyEventType.KeyUp && app != null) ctx.actions.openMenu(app)
                        app != null
                    } else {
                        etat.toucheTuile(e, i, n)
                    }
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White,
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
            glow = ClickableSurfaceDefaults.glow(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (tuile) {
                    is TuileChaine.App -> ContenuApp(tuile, visuel?.art != null, couleur) {
                        ImageApp(visuel, tuile.app.label, Modifier.fillMaxSize())
                    }
                    TuileChaine.Biblio -> ContenuBiblio(ctx.ui.allApps.size)
                    TuileChaine.Aide -> ContenuTexte("Aucun favori.\nMaintiens OK sur une app pour l'ajouter.")
                }
                // Liseré gris et reflet blanc intérieur, comme une tuile en plastique
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.u, ChainesLisere, forme)
                        .padding(2.u)
                        .border(1.u, Color.White.copy(alpha = 0.45f), RoundedCornerShape((CH_RAYON - 2).u))
                )
            }
        }
        // Petite main-curseur posée sur la tuile sélectionnée
        if (focus) {
            Canvas(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-30).u, y = 10.u)
                    .size(34.u)
                    .graphicsLayer { alpha = f }
            ) {
                mainCurseur(Offset(size.width * 0.35f, 0f), size.height)
            }
        }
    }
}

/** Halo bleu clair autour de la tuile (anneaux de plus en plus larges et transparents). */
private fun DrawScope.lueurBleue(rayon: Float, trait: Float, f: Float) {
    for (k in 1..4) {
        val e = trait * (0.6f + k * 0.9f)
        drawRoundRect(
            ChainesBleuLueur.copy(alpha = f * 0.30f * (1f - k / 5f)),
            Offset(-e, -e), Size(size.width + 2 * e, size.height + 2 * e),
            CornerRadius(rayon + e), style = Stroke(trait * 1.4f),
        )
    }
}

@Composable
private fun ContenuApp(tuile: TuileChaine.App, image: Boolean, couleur: Color, imageApp: @Composable () -> Unit) {
    val app = tuile.app
    when {
        app.isSpecial -> {
            // Tuile « Source HDMI » : dégradé de sa couleur + icône
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(lerp(couleur, Color.White, 0.15f), lerp(couleur, Color.Black, 0.45f)))
                )
            )
            Icon(ProjoIcons.Sources, contentDescription = app.label, tint = Color.White, modifier = Modifier.size(42.u))
        }
        image -> imageApp()
        else -> ContenuTexte(app.label)
    }
}

/** Tuile « Toutes les apps » : icône bleue et nombre d'apps, sur fond blanc. */
@Composable
private fun ContenuBiblio(total: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEFF1)))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ProjoIcons.Apps, contentDescription = null, tint = ChainesBleu, modifier = Modifier.size(34.u))
        Spacer(Modifier.height(6.u))
        Text(
            "Toutes les apps", color = ChainesGrisFonce, fontSize = 15.us, fontWeight = FontWeight.Bold,
            style = TextStyle.Default, maxLines = 1,
        )
        Text(
            "$total apps", color = ChainesGris, fontSize = 12.us, fontWeight = FontWeight.Medium,
            style = TextStyle.Default, maxLines = 1,
        )
    }
}

@Composable
private fun ContenuTexte(texte: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEFF1))))
            .padding(horizontal = 14.u),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = ChainesGrisFonce, fontSize = 15.us, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, style = TextStyle.Default, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Onglet blanc collé au bord de l'écran, avec la flèche bleue de page. */
@Composable
private fun OngletPage(droite: Boolean, modifier: Modifier) {
    val forme = if (droite) RoundedCornerShape(topStart = 22.u, bottomStart = 22.u)
    else RoundedCornerShape(topEnd = 22.u, bottomEnd = 22.u)
    Box(
        modifier
            .testTag(if (droite) "chaines:page-suivante" else "chaines:page-precedente")
            .size(40.u, 88.u)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9ECEF))))
            .border(2.u, ChainesLisere, forme)
    ) {
        Canvas(Modifier.fillMaxSize().padding(start = if (droite) 4.u else 0.u, end = if (droite) 0.u else 4.u)) {
            flechePage(droite)
        }
    }
}

// ------------------------------------------------------------------ bande du bas

@Composable
private fun BandeChaines(ctx: HomeContext, etat: EtatChaines, n: Int, modifier: Modifier) {
    val actions = ctx.quickActions()
    val reglages = actions.firstOrNull { it.estReglages }
    val wifi = actions.firstOrNull { it.icon == ProjoIcons.Wifi }
    val bluetooth = actions.firstOrNull { it.icon == ProjoIcons.Bluetooth }
    val sources = actions.firstOrNull { it.icon == ProjoIcons.Sources }

    fun pastille(cle: String, qa: QuickAction) = BoutonBande(
        cle, "rapide:" + qa.label, qa.onClick, qa.onLongClick, rond = false,
        contenu = { Icon(qa.icon, contentDescription = qa.label, tint = ChainesGrisFonce, modifier = Modifier.size(18.u)) },
    )

    val gauche = buildList {
        if (reglages != null) add(
            BoutonBande(
                "reglages", "rapide:Réglages", reglages.onClick, reglages.onLongClick, rond = true,
                contenu = {
                    Text(
                        "iBi", color = ChainesGris, fontSize = 21.us, fontWeight = FontWeight.Bold,
                        style = TextStyle.Default, maxLines = 1,
                    )
                },
                requete = ctx.focusReglages,
            )
        )
        if (wifi != null) add(pastille("wifi", wifi))
        if (bluetooth != null) add(pastille("bluetooth", bluetooth))
    }
    val droite = buildList {
        if (sources != null) add(pastille("sources", sources))
        if (ctx.majDisponible) add(
            BoutonBande(
                "maj", "rapide:Mise à jour", ctx.actions.update, null, rond = false,
                contenu = {
                    Box {
                        Icon(ProjoIcons.MiseAJour, contentDescription = "Mise à jour", tint = ChainesBleu, modifier = Modifier.size(19.u))
                    }
                },
            )
        )
        add(
            BoutonBande(
                "modules", "rapide:Modules", { etat.panneau = true }, null, rond = true,
                contenu = { Canvas(Modifier.size(30.u)) { enveloppe(center, size.width, ChainesGris) } },
            )
        )
    }
    etat.ordreBande = (gauche + droite).map { it.cle }

    Box(
        modifier
            .fillMaxWidth()
            .height(CH_BANDE.u)
            .testTag("chaines:bande")
    ) {
        // Fond de la bande, filet bleu clair et ombre douce au-dessus
        Canvas(Modifier.fillMaxSize()) {
            val u = size.height / CH_BANDE
            val bord = CH_BANDE_BORD * u
            val creux = CH_BANDE_CREUX * u
            val demiFond = CH_BANDE_DEMI_FOND * u
            val raccord = CH_BANDE_RACCORD * u
            val contour = contourBande(size.width, size.height, bord, creux, demiFond, raccord)
            val filet = filetBande(size.width, bord, creux, demiFond, raccord)
            drawPath(filet, Color.Black.copy(alpha = 0.06f), style = Stroke(6f * u))
            drawPath(contour, Brush.verticalGradient(listOf(ChainesBandeHaut, ChainesBandeBas), startY = bord, endY = size.height))
            drawPath(filet, ChainesBleuFilet, style = Stroke(2.4f * u))
            translateY(2.4f * u) { drawPath(filet, Color.White.copy(alpha = 0.7f), style = Stroke(1.2f * u)) }
        }

        // Heure (au-dessus du creux) et date (dans la bande)
        HorlogeChaines(Modifier.align(Alignment.TopCenter).padding(top = 4.u))

        // Boutons : ronds aux extrémités, pastilles à côté
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 40.u, bottom = 24.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.u),
        ) {
            gauche.forEach { BoutonRond(it, etat, ctx, n) }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 40.u, bottom = 24.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.u),
        ) {
            droite.forEach { BoutonRond(it, etat, ctx, n) }
        }
    }
}

private inline fun DrawScope.translateY(dy: Float, bloc: DrawScope.() -> Unit) {
    drawContext.transform.translate(0f, dy)
    bloc()
    drawContext.transform.translate(0f, -dy)
}

/** Heure à segments arrondis (sans zéro initial) et date « Mer 21/10 ». */
@Composable
private fun HorlogeChaines(modifier: Modifier) {
    val t = rememberMaintenant()
    val heure = heureTexte(t).let { if (it.length == 5 && it[0] == '0') it.substring(1) else it }
    val date = remember(t / 60_000L) {
        SimpleDateFormat("EEE dd/MM", Locale.FRENCH).format(Date(t)).replace(".", "")
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(170.u, 50.u).testTag("chaines:heure")) {
            val h = size.height * 0.9f
            val l = largeurHorloge(heure, h)
            horlogeSegments(heure, Offset((size.width - l) / 2f, (size.height - h) / 2f), h, ChainesGris, Color.White)
        }
        Spacer(Modifier.height(18.u))
        Text(
            date, color = ChainesGris, fontSize = 22.us, fontWeight = FontWeight.Bold,
            style = TextStyle.Default, maxLines = 1,
        )
    }
}

/** Bouton rond de la bande (grand : iBi, enveloppe ; petit : pastilles). */
@Composable
private fun BoutonRond(b: BoutonBande, etat: EtatChaines, ctx: HomeContext, n: Int) {
    val diametre: Dp = if (b.rond) CH_ROND.u else CH_PASTILLE.u
    var focus by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focus) 1f else 0f, tween(180), label = "focus")
    val requete = remember(b.cle) { FocusRequester() }
    etat.bande[b.cle] = requete
    val trait = 3.u
    Box(
        Modifier
            .testTag(b.etiquette)
            .onGloballyPositioned { etat.centresBande[b.cle] = it.positionInRoot().x + it.size.width / 2f }
            .size(diametre)
            .graphicsLayer { val s = 1f + 0.08f * f; scaleX = s; scaleY = s }
            .drawWithContent {
                if (f > 0f) {
                    val t = trait.toPx()
                    for (k in 1..3) {
                        drawCircle(
                            ChainesBleuLueur.copy(alpha = f * 0.3f * (1f - k / 4f)),
                            radius = size.minDimension / 2f + t * (0.6f + k * 0.9f), style = Stroke(t * 1.4f),
                        )
                    }
                }
                drawContent()
                if (f > 0f) drawCircle(ChainesBleu.copy(alpha = f), radius = size.minDimension / 2f, style = Stroke(trait.toPx()))
            }
    ) {
        Surface(
            onClick = b.onClick,
            onLongClick = b.onLongClick,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(requete)
                .then(if (b.requete != null) Modifier.focusRequester(b.requete) else Modifier)
                .onFocusChanged {
                    focus = it.isFocused
                    if (it.isFocused) ctx.sansSelection()
                }
                .onPreviewKeyEvent { etat.toucheBande(it, b.cle, n) },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
            glow = ClickableSurfaceDefaults.glow(),
        ) {
            // Anneau gris en relief autour d'un disque blanc
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val r = size.minDimension / 2f
                        val anneau = r * (if (b.rond) 0.16f else 0.13f)
                        drawCircle(Brush.verticalGradient(listOf(Color(0xFFF1F2F4), Color(0xFFB4BAC0))), r)
                        drawCircle(Brush.verticalGradient(listOf(Color(0xFFC3C8CE), Color(0xFFF4F5F6))), r - anneau * 0.45f)
                        drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFECEEF0))), r - anneau)
                    },
                contentAlignment = Alignment.Center,
            ) {
                b.contenu(focus)
            }
        }
    }
}

// ------------------------------------------------------------------ panneau des modules

/**
 * Zone de modules en grand, par-dessus l'accueil voilé de blanc. Le focus y reste
 * (▲ ▼ ▶ ne s'en échappent pas) ; ◀ depuis le 1er module ou Retour la ferment.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanneauModules(ctx: HomeContext, etat: EtatChaines, sortie: () -> Unit) {
    var euLeFocus by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.72f))
            .testTag("chaines:panneau"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .onFocusChanged {
                    // Le focus est parti ailleurs (touche Maison…) : le panneau se ferme
                    if (it.hasFocus) euLeFocus = true else if (euLeFocus) etat.panneau = false
                }
                .focusProperties {
                    exit = { d ->
                        if (d == FocusDirection.Up || d == FocusDirection.Down || d == FocusDirection.Right) FocusRequester.Cancel
                        else FocusRequester.Default
                    }
                }
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.u)) {
                Canvas(Modifier.size(28.u)) { enveloppe(center, size.width, ChainesGris) }
                Spacer(Modifier.width(10.u))
                Text(
                    "Modules", color = ChainesGrisFonce, fontSize = 22.us, fontWeight = FontWeight.Bold,
                    style = TextStyle.Default,
                )
            }
            NbaPanel(
                data = ctx.nbaData,
                erreur = ctx.nbaErreur,
                repo = ctx.nbaRepo,
                onExit = sortie,
                style = ChainesStyleModules,
                entree = etat.entreeModules,
            )
            Text(
                "◀ ou Retour pour fermer", color = ChainesGris, fontSize = 13.us, fontWeight = FontWeight.Medium,
                style = TextStyle.Default, modifier = Modifier.padding(top = 12.u),
            )
        }
    }
}
