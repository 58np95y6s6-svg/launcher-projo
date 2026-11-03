package fr.projo.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.DefilementGalerie
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.GenreTmdb
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.Illustrations
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.meteo.IconeMeteo
import fr.projo.launcher.modules.meteo.MeteoDonnees
import fr.projo.launcher.modules.meteo.ModuleMeteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Onglets de Galerie : « Pour vous », « Films », « Séries » (rubriques de Studio) et « Ma liste ». */
internal enum class OngletGalerie(val titre: String, val cle: String) {
    POUR_VOUS("Pour vous", "pourvous"),
    FILMS("Films", "films"),
    SERIES("Séries", "series"),
    MA_LISTE("Ma liste", "maliste"),
}

// Mesures de la maquette, en px de l'écran de référence 1920 × 1080
private const val G_PANNEAU_L = 650f
private const val G_PANNEAU_T = 600f
private const val G_PANNEAU_W = 880f
private const val G_PANNEAU_H = 236f
/** Points de position affichés au plus sous le synopsis. */
private const val G_POINTS = 11
/** Haut du dock au-dessus du bas de l'écran (914 sur un écran de 1080). */
private const val G_DOCK_BAS = 166f
/** Haut du dock sur l'écran 2 (modules). */
private const val G_DOCK_ECRAN2 = 110f
/** Haut du sol au-dessus du bas de l'écran (860 sur 1080). */
private const val G_SOL_BAS = 220f
/** Zone du carrousel (cartes, actions, panneau) : de 140 à 860 px ; glisser et molette y font défiler les titres. */
private const val G_HAUT = 140f
private const val G_BAS = 860f

/** Position (px de maquette, repère de l'écran) d'un élément de la zone du carrousel. */
private fun Modifier.dansGalerie(x: Float, y: Float, k: Float): Modifier =
    offset { IntOffset((x * k).roundToInt(), ((y - G_HAUT) * k).roundToInt()) }

/**
 * Design « Galerie » (maquette validée) : studio gris clair, verre flottant, trois étages.
 *
 *  1. En haut : iBiTV, l'heure et la date ; onglets « Pour vous | Films | Séries | Ma liste » ;
 *     la météo (seulement si le module Météo en donne une) et Réglages.
 *  2. Au centre : le carrousel des films et séries de TMDB disponibles sur les services
 *     installés (comme Studio). Le titre du centre est une grande carte ; les autres occupent
 *     les places de la composition (GalerieCarrousel.kt) et glissent de place en place (◀ ▶,
 *     flèches, tuile choisie). Sous la carte, un panneau de verre : synopsis, « Regarder sur … »
 *     et « Bande-annonce » ; à gauche « Ajouter à ma liste » et « agrandir » (grille de tous les
 *     titres de l'onglet).
 *  3. En bas : le dock des applis façon Mac (favoris puis « Mes apps », « Toutes les applis »).
 *  Écran 2 (▼ depuis le dock) : les modules choisis dans Réglages → Modules.
 *  Défilement automatique (Réglages → Défilement des affiches) : titre suivant après quelques
 *  secondes sans rien toucher, en pause tant que le focus est sur le carrousel ou ses boutons.
 *
 * Sans clé TMDB ou sans réseau : les apps à la une (repli de Studio). Focus initial : 1re app
 * du dock. Navigation : GalerieNav (étages, ◀ ▶ dans l'ordre du carrousel).
 */
@Composable
fun HomeGalerie(ctx: HomeContext) {
    val ui = ctx.ui
    val contexte = LocalContext.current
    val k = pxMaquette()
    val nav = remember { GalerieNav() }
    val scope = rememberCoroutineScope()
    // Hauteur de l'écran en px de maquette (1080 en 16:9)
    val hauteur = LocalConfiguration.current.screenHeightDp / LocalScale.current * 2f
    val hautDock = hauteur - G_DOCK_BAS

    // ------------------------------------------------ titres
    val paquets = remember(ui.favorites, ui.homeApps) {
        (ui.favorites + ui.homeApps).filter { !it.isSpecial }.map { it.packageName }.distinct()
    }
    // Services installés : ceux des favoris et de « Mes apps » d'abord (comme Studio)
    val installees = remember(ui.favorites, ui.homeApps, ui.allApps) {
        (ui.favorites + ui.homeApps + ui.allApps).distinctBy { it.packageName }
    }
    val etat = rememberStudioSuggestions(installees, paquets)
    val programmes = etat.resultat.programmes
    val maListe = remember(contexte) { MaListe.de(contexte) }
    val gardes = maListe.titres
    val onglets = remember(programmes) {
        listOfNotNull(
            OngletGalerie.POUR_VOUS,
            OngletGalerie.FILMS.takeIf { programmes.any { it.genre == GenreTmdb.FILM } },
            OngletGalerie.SERIES.takeIf { programmes.any { it.genre == GenreTmdb.SERIE } },
            OngletGalerie.MA_LISTE,
        )
    }
    var ongletChoisi by remember { mutableStateOf(OngletGalerie.POUR_VOUS) }
    val onglet = if (ongletChoisi in onglets) ongletChoisi else OngletGalerie.POUR_VOUS
    val titres = remember(onglet, programmes, gardes, ui.allApps, ui.favorites, ui.homeApps, etat.recente) {
        titresGalerie(onglet, programmes, gardes, ui, etat.recente)
    }
    val marques = remember(gardes) { gardes.map { it.id }.toSet() }

    // ------------------------------------------------ carrousel
    // Titre du centre gardé par onglet (clé, et place si le titre a disparu de la liste)
    val memoire = remember { HashMap<OngletGalerie, Pair<String, Int>>() }
    val cles = remember(titres) { titres.map { it.cle } }
    val anim = remember(onglet, cles) {
        val m = memoire[onglet]
        val i = m?.let { (c, place) -> cles.indexOf(c).takeIf { it >= 0 } ?: place } ?: 0
        AnimationGalerie().also { it.installer(cles.size, i) }
    }
    // Temps écoulé depuis le dernier glissement (ms) : toutes les animations en découlent, lues au dessin
    var temps by remember(anim) { mutableFloatStateOf(CarrouselGalerie.FIN) }
    var courant by remember(anim) { mutableIntStateOf(anim.courant) }
    var precedent by remember(anim) { mutableIntStateOf(-1) }
    var trace by remember(anim) { mutableIntStateOf(0) }
    val tache = remember { arrayOfNulls<Job>(1) }
    DisposableEffect(anim) { onDispose { tache[0]?.cancel() } }
    val noter: () -> Unit = { titres.getOrNull(anim.courant)?.let { memoire[onglet] = it.cle to anim.courant } }
    val deplacer: (Int) -> Unit = { pas ->
        if (anim.deplacer(pas, CarrouselGalerie.avancement(temps))) {
            precedent = courant
            courant = anim.courant
            noter()
            trace++
            temps = 0f
            tache[0]?.cancel()
            tache[0] = scope.launch {
                animate(0f, CarrouselGalerie.FIN, animationSpec = tween(CarrouselGalerie.FIN.toInt(), easing = LinearEasing)) { v, _ -> temps = v }
                anim.terminer()
                trace++
            }
        }
    }
    /** Place [i] au centre sans animation (choix dans la grille). */
    val installerAu: (Int) -> Unit = { i ->
        tache[0]?.cancel()
        anim.installer(titres.size, i)
        courant = anim.courant
        precedent = -1
        temps = CarrouselGalerie.FIN
        noter()
        trace++
    }
    nav.surCarte = deplacer
    val heros = titres.getOrNull(courant)

    // ------------------------------------------------ images
    val source = rememberSourceStudio()
    val visuels = remember { mutableStateMapOf<String, VisuelGalerie>() }
    val largeurNet = (CarrouselGalerie.place(0).w * k).roundToInt().coerceIn(320, 780)
    val largeurAffiche = (220f * k).roundToInt().coerceIn(120, 342)
    val voulus = remember(titres, courant, trace) {
        val n = titres.size
        val fantomes = anim.trajets.filter { it.fantome }.map { it.index }.toSet()
        titres.indices
            .filter { CarrouselGalerie.decalage(it, courant, n) in CarrouselGalerie.MIN..CarrouselGalerie.MAX || it in fantomes }
            .sortedBy { kotlin.math.abs(CarrouselGalerie.decalage(it, courant, n)) }
            .map { titres[it] }
    }
    LaunchedEffect(voulus, source) {
        val gardesVisuels = voulus.map { it.cle }.toSet()
        visuels.keys.retainAll(gardesVisuels)
        for (t in voulus) {
            if (t.cle in visuels) continue
            val v = when {
                // Visuel d'app déjà en mémoire, ou source des tests : tout de suite
                t is StudioSuggestion.AppUne && ctx.icons.cached(ui.iconSourceOf(t.app.packageName), FormatVisuel.BANNIERE) != null ->
                    chargerVisuel(t, source, ctx.icons, ui, largeurNet, largeurAffiche)
                source.synchrone -> chargerVisuel(t, source, ctx.icons, ui, largeurNet, largeurAffiche)
                else -> withContext(Dispatchers.IO) { chargerVisuel(t, source, ctx.icons, ui, largeurNet, largeurAffiche) }
            }
            if (!v.vide) visuels[t.cle] = v
        }
    }

    // ------------------------------------------------ actions
    val regarder: (StudioSuggestion) -> Unit = { s ->
        when (s) {
            is StudioSuggestion.Programme -> ouvrirProgramme(contexte, s.programme) { s.app?.let(ctx.actions.launch) }
            is StudioSuggestion.AppUne -> ctx.actions.launch(s.app)
        }
    }
    val bandeAnnonceDispo = remember(contexte) { BandeAnnonceGalerie.disponible(contexte) }
    val focusCarte = remember { FocusRequester() }
    val focusAgrandir = remember { FocusRequester() }
    val focusOnglets = remember { OngletGalerie.entries.associateWith { FocusRequester() } }
    var grille by remember { mutableStateOf(false) }
    // Après la grille : focus sur la carte (titre choisi) ou sur « agrandir » (Retour)
    var retourGrille by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(retourGrille) {
        val f = retourGrille ?: return@LaunchedEffect
        withFrameNanos {}
        runCatching { f.requestFocus() }
        retourGrille = null
    }
    // Ma liste vidée depuis son onglet : le focus revient sur l'onglet
    var versOnglet by remember { mutableIntStateOf(0) }
    LaunchedEffect(versOnglet) {
        if (versOnglet == 0) return@LaunchedEffect
        withFrameNanos {}
        runCatching { focusOnglets.getValue(onglet).requestFocus() }
    }
    val basculerListe: () -> Unit = {
        val p = (heros as? StudioSuggestion.Programme)?.programme
        if (p != null) {
            val reste = maListe.basculer(p)
            if (!reste && onglet == OngletGalerie.MA_LISTE && maListe.titres.none { g -> ui.allApps.any { it.packageName == g.paquet } }) versOnglet++
        }
    }
    nav.preferee = { e, cle, vers ->
        when {
            e == EtageGalerie.GALERIE && cle == CLE_CARTE && vers == EtageGalerie.HAUT -> "onglet:" + onglet.cle
            e == EtageGalerie.GALERIE && cle == CLE_CARTE && vers == EtageGalerie.ACTIONS -> "regarder"
            else -> null
        }
    }

    // ------------------------------------------------ écrans
    val decalagePx = ((hautDock - G_DOCK_ECRAN2) * k).roundToInt()
    val versEcran: (EtageGalerie) -> Unit = { e ->
        val cible = when (e) {
            EtageGalerie.HAUT, EtageGalerie.GALERIE, EtageGalerie.ACTIONS -> 0
            EtageGalerie.MODULES -> decalagePx
            EtageGalerie.DOCK -> null
        }
        if (cible != null && ctx.defilement.value != cible) {
            scope.launch { ctx.defilement.animateScrollTo(cible, tween(480, easing = FastOutSlowInEasing)) }
        }
    }
    // Pointeur : glisser vers le haut ou molette vers le bas → écran 2 (modules), l'inverse → écran 1
    val surEcran2 = { ctx.defilement.value > decalagePx / 2 }
    val gestesPage = Modifier.gestesPages(
        Orientation.Vertical,
        precedent = { if (surEcran2()) nav.allerVers(EtageGalerie.GALERIE, CLE_CARTE) || nav.allerVers(EtageGalerie.HAUT) },
        suivant = { if (!surEcran2()) nav.allerVers(EtageGalerie.MODULES) },
    )
    // ------------------------------------------------ défilement automatique
    // Personne ne touche à rien (ni touche ni pointeur) depuis le temps choisi : titre suivant. En
    // pause sur le carrousel et ses boutons (on lit, on va choisir), sur l'écran 2, dans la grille,
    // quand le focus a quitté l'accueil (Réglages ouverts) et quand une app est au premier plan.
    LaunchedEffect(Unit) { DefilementGalerie.charger(contexte) }
    val intervalle = DefilementGalerie.secondes * 1_000L
    val cycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var focusDedans by remember { mutableStateOf(false) }
    // Compteur d'activité : lu seulement par le défilement (une touche ne recompose rien)
    val activite = remember { mutableIntStateOf(0) }
    val deplacerAJour by rememberUpdatedState(deplacer)
    val defilementActif = intervalle > 0 && titres.size > 1 && !grille && focusDedans && cycle == Lifecycle.State.RESUMED
    LaunchedEffect(defilementActif, intervalle, anim) {
        if (!defilementActif) return@LaunchedEffect
        // Chaque activité relance l'attente complète
        snapshotFlow { activite.intValue }.collectLatest {
            while (true) {
                delay(intervalle)
                val etage = nav.focus?.first
                if (etage == EtageGalerie.GALERIE || etage == EtageGalerie.ACTIONS || surEcran2()) continue
                deplacerAJour(1)
            }
        }
    }

    val tempsLu = { temps }
    val traceLue = { trace }
    // Écran 2 : l'écran 1 (carrousel, en-tête) s'efface à mesure que la page glisse
    val presenceEcran1 = { 1f - (ctx.defilement.value / decalagePx.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) }

    Box(
        Modifier
            .fillMaxSize()
            .fondGalerie()
            .onFocusChanged { focusDedans = it.hasFocus }
            .onPreviewKeyEvent {
                activite.intValue++
                false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        activite.intValue++
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height((2f * hauteur).mq)
                    // Page sur deux écrans, déplacée d'un bloc (pas de défilement automatique au focus)
                    .offset { IntOffset(0, -ctx.defilement.value) }
                    .then(gestesPage)
                    .testTag("galerie:page"),
            ) {
                SolGalerie(k, hauteur)
                // ------------------------------------------------ étage 2 : carrousel (glisser, molette : titre voisin)
                Box(
                    Modifier
                        .offset { IntOffset(0, (G_HAUT * k).roundToInt()) }
                        .fillMaxWidth()
                        .height((G_BAS - G_HAUT).mq)
                        .graphicsLayer { alpha = presenceEcran1() }
                        .gestesPages(Orientation.Horizontal, precedent = { deplacer(-1) }, suivant = { deplacer(1) }),
                ) {
                    DessinCarrousel(anim, titres, visuels, tempsLu, traceLue, courant, precedent, marques, titres.isNotEmpty(), k)
                    if (titres.isEmpty()) {
                        ListeVideGalerie(k)
                    } else {
                        Box(
                            Modifier.fillMaxSize().etageGalerie(nav, EtageGalerie.GALERIE, lateral = true, surFocus = {
                                versEcran(it)
                                ctx.sansSelection()
                            }),
                        ) {
                            for ((s, entrant) in affichesAuCentre(titres, courant, precedent)) {
                                key(s.cle, entrant) {
                                    ContenuHeros(
                                        s, onglet, ctx, k,
                                        alpha = if (entrant) ({ CarrouselGalerie.apparitionContenu(tempsLu()) }) else ({ CarrouselGalerie.disparition(tempsLu()) }),
                                        tag = if (entrant) "galerie:heros:" + s.cle else null,
                                    )
                                }
                            }
                            CiblesCarrousel(titres, courant, nav, k, focusCarte, heros, regarder, ctx, deplacer, tempsLu)
                        }
                    }
                    // ------------------------------------------------ actions et panneau
                    Box(
                        Modifier.fillMaxSize().etageGalerie(nav, EtageGalerie.ACTIONS, lateral = true, surFocus = {
                            versEcran(it)
                            ctx.sansSelection()
                        }),
                    ) {
                        if (heros is StudioSuggestion.Programme) {
                            BoutonAjout(
                                dansLaListe = heros.programme.id in marques, k = k, onClick = basculerListe,
                                modifier = Modifier.dansGalerie(132f, 452f, k)
                                    .testTag("galerie:ajout")
                                    .then(rememberCibleGalerie(nav, EtageGalerie.ACTIONS, "ajout")),
                            )
                        }
                        if (titres.size > 1) {
                            BoutonRond(
                                k = k, taille = 52f, onClick = { grille = true },
                                modifier = Modifier.dansGalerie(372f, 448f, k)
                                    .testTag("galerie:agrandir")
                                    .then(rememberCibleGalerie(nav, EtageGalerie.ACTIONS, "agrandir", fr = focusAgrandir)),
                                description = "Agrandir : tous les titres",
                            ) {
                                pictogramme(Color(0xFF3B3D44), 2f) {
                                    moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
                                    moveTo(10f, 20f); lineTo(4f, 20f); lineTo(4f, 14f)
                                    moveTo(20f, 4f); lineTo(13f, 11f)
                                    moveTo(4f, 20f); lineTo(11f, 13f)
                                }
                            }
                        }
                        if (heros != null) {
                            PanneauGalerie(
                                titres, courant, precedent, tempsLu, k, nav, ctx, etat.resultat.etat,
                                regarder = { regarder(heros) },
                                bandeAnnonce = if (bandeAnnonceDispo && heros is StudioSuggestion.Programme) {
                                    { BandeAnnonceGalerie.ouvrir(contexte, heros.programme.titre) }
                                } else null,
                            )
                        }
                    }
                }
                // ------------------------------------------------ étage 1 : en-tête
                Box(Modifier.graphicsLayer { alpha = presenceEcran1() }) {
                    EnTeteGalerie(
                        ctx, nav, k, onglets, onglet, focusOnglets,
                        choisir = { ongletChoisi = it },
                        credit = titres.any { it is StudioSuggestion.Programme },
                        versEcran = versEcran,
                    )
                }
                // ------------------------------------------------ étage 3 : dock (et écran 2 : modules)
                DockGalerie(ctx, nav, k, hautDock, versEcran)
                ModulesGalerie(ctx, nav, k, hauteur, hautDock - G_DOCK_ECRAN2, versEcran)
            }
        }
        // « Mes apps » n'a pas de rangée à elle (tout est dans le dock) : son état de défilement doit
        // tout de même être attaché à une rangée (HomeScreen le remet au début à chaque Maison)
        LazyRow(state = ctx.rangeeApps, modifier = Modifier.size(0.dp)) {}
        if (grille) {
            GrilleGalerie(
                titres = titres, courant = courant, onglet = onglet, ctx = ctx, k = k,
                choisir = { i ->
                    grille = false
                    installerAu(i)
                    retourGrille = focusCarte
                },
                fermer = {
                    grille = false
                    retourGrille = focusAgrandir
                },
            )
        }
    }
}

/** Titres dont le contenu est affiché au centre : celui qui s'en va (s'il y en a un) puis celui qui arrive. */
private fun affichesAuCentre(titres: List<StudioSuggestion>, courant: Int, precedent: Int): List<Pair<StudioSuggestion, Boolean>> =
    listOfNotNull(
        titres.getOrNull(precedent)?.takeIf { precedent != courant }?.let { it to false },
        titres.getOrNull(courant)?.let { it to true },
    )

/** Titres de l'onglet : programmes TMDB (filtrés), « Ma liste », ou apps à la une (repli). */
private fun titresGalerie(
    onglet: OngletGalerie,
    programmes: List<StudioProgramme>,
    gardes: List<StudioProgramme>,
    ui: fr.projo.launcher.HomeUi,
    recente: String?,
): List<StudioSuggestion> {
    fun programme(p: StudioProgramme) = StudioSuggestion.Programme(p, ui.allApps.firstOrNull { it.packageName == p.paquet })
    return when (onglet) {
        OngletGalerie.POUR_VOUS ->
            if (programmes.isNotEmpty()) programmes.map(::programme) else appsALaUne(ui.favorites, ui.homeApps, recente)
        OngletGalerie.FILMS -> programmes.filter { it.genre == GenreTmdb.FILM }.map(::programme)
        OngletGalerie.SERIES -> programmes.filter { it.genre == GenreTmdb.SERIE }.map(::programme)
        // Seulement les titres d'un service encore installé (« Regarder sur … » reste vrai)
        OngletGalerie.MA_LISTE -> gardes.filter { g -> ui.allApps.any { it.packageName == g.paquet } }.map(::programme)
    }
}

/** Images d'un titre (fonction bloquante : hors du thread principal, sauf source des tests). */
private fun chargerVisuel(
    t: StudioSuggestion,
    source: StudioSource,
    icons: IconLoader,
    ui: fr.projo.launcher.HomeUi,
    largeurNet: Int,
    largeurAffiche: Int,
): VisuelGalerie = when (t) {
    is StudioSuggestion.Programme -> {
        val adresse = t.programme.image
        val net = adresse?.let { source.image(it, largeurNet) }
        val affiche = t.programme.affiche?.let { source.image(it, largeurAffiche) }
        val flous = if (net != null && adresse != null) FlousGalerie.preparer(adresse, net) else null
        VisuelGalerie(net, flous?.first, flous?.second, affiche, opaque = true).also { it.envoyerAuGpu() }
    }
    is StudioSuggestion.AppUne -> {
        val paquet = ui.iconSourceOf(t.app.packageName)
        val net = (icons.cached(paquet, FormatVisuel.BANNIERE) ?: icons.load(paquet, FormatVisuel.BANNIERE)).art
        val flous = net?.let { FlousGalerie.preparer("app:$paquet", it) }
        VisuelGalerie(net, flous?.first, flous?.second, null).also { it.envoyerAuGpu() }
    }
}

/** Bande-annonce : recherche YouTube du titre (iBiTube, SmartTube, YouTube ou navigateur, s'ils savent l'ouvrir). */
internal object BandeAnnonceGalerie {
    fun intention(titre: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("$titre bande annonce")),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Intention d'essai : une appli sait-elle ouvrir une recherche YouTube ? */
    fun sonde(): Intent = intention("film")

    fun disponible(context: Context): Boolean =
        runCatching { sonde().resolveActivity(context.packageManager) != null }.getOrDefault(false)

    fun ouvrir(context: Context, titre: String) {
        runCatching { context.startActivity(intention(titre)) }
    }
}

// ------------------------------------------------------------------ fond

/** Studio gris clair : dégradé radial elliptique (centre à 50 % / 22 %), fixe derrière la page. */
private fun Modifier.fondGalerie(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.5f, h * 0.22f)
    val rx = w * 1.2f
    val ry = h * 0.85f
    val brosse = Brush.radialGradient(
        0f to Color(0xFFE6E7EA), 0.45f to Color(0xFFD0D1D5), 0.75f to Color(0xFFB4B6BB), 1f to Color(0xFFA2A4A9),
        center = c, radius = rx,
    )
    val e = rx / ry
    onDrawBehind {
        withTransform({ scale(1f, ry / rx, pivot = c) }) {
            drawRect(brosse, topLeft = Offset(0f, c.y - c.y * e), size = Size(w, h * e))
        }
    }
}

/** Sol plus clair et ligne d'horizon (écran 1), puis le sol vu de haut (écran 2, modules). */
@Composable
private fun SolGalerie(k: Float, hauteur: Float) {
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val haut = (hauteur - G_SOL_BAS) * k
            val bas = hauteur * k
            val sol = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0f), 0.35f to Color(0xFFECEDF0).copy(alpha = 0.55f), 1f to Color(0xFFC4C6CB).copy(alpha = 0.9f),
                startY = haut, endY = bas,
            )
            val horizon = Brush.horizontalGradient(
                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.7f), 1f to Color.Transparent,
            )
            onDrawBehind {
                drawRect(sol, topLeft = Offset(0f, haut), size = Size(size.width, bas - haut))
                drawRect(horizon, topLeft = Offset(0f, haut), size = Size(size.width, 2f * k))
                drawRect(Color(0xFFC4C6CB).copy(alpha = 0.9f), topLeft = Offset(0f, bas), size = Size(size.width, size.height - bas))
            }
        },
    )
}

// ------------------------------------------------------------------ textes

/** Texte de Galerie (Manrope, ou Instrument Serif) ; tailles en px de maquette. */
@Composable
internal fun TexteGalerie(
    texte: String,
    taille: Float,
    couleur: Color,
    modifier: Modifier = Modifier,
    poids: FontWeight = FontWeight.Medium,
    serif: Boolean = false,
    interligne: Float = taille * 1.32f,
    lignes: Int = 1,
    centre: Boolean = false,
    espacement: Float = 0f,
    ombre: Shadow? = null,
) {
    Text(
        texte,
        modifier = modifier,
        color = couleur,
        fontSize = taille.mqs,
        fontWeight = poids,
        fontFamily = if (serif) GalerieSerif else GalerieSans,
        letterSpacing = espacement.mqs,
        textAlign = if (centre) TextAlign.Center else null,
        maxLines = lignes,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(lineHeight = interligne.mqs, shadow = ombre),
    )
}

// ------------------------------------------------------------------ carrousel

/** Les cartes du carrousel, leurs halos et le flou sous le panneau, en un seul dessin. */
@Composable
private fun DessinCarrousel(
    anim: AnimationGalerie,
    titres: List<StudioSuggestion>,
    visuels: Map<String, VisuelGalerie>,
    temps: () -> Float,
    trace: () -> Int,
    courant: Int,
    precedent: Int,
    marques: Set<String>,
    panneau: Boolean,
    k: Float,
) {
    Spacer(
        Modifier
            .fillMaxSize()
            // Calque à lui : redessiné à chaque image du glissement sans réenregistrer le reste de l'écran
            .graphicsLayer()
            .drawBehind {
                trace()
                // Repère de l'écran (la zone du carrousel commence à G_HAUT)
                translate(0f, -G_HAUT * k) {
                    dessinerCarrousel(anim, titres, visuels, temps(), courant, precedent, marques, panneau, k)
                }
            },
    )
}

private val VOILE = listOf(Color(0xE6050508), Color(0x80050508), Color(0x0D050508), Color(0x66050508))

private fun DrawScope.dessinerCarrousel(
    anim: AnimationGalerie,
    titres: List<StudioSuggestion>,
    visuels: Map<String, VisuelGalerie>,
    temps: Float,
    courant: Int,
    precedent: Int,
    marques: Set<String>,
    panneau: Boolean,
    k: Float,
) {
    val p = CarrouselGalerie.avancement(temps)
    val apparition = CarrouselGalerie.apparitionContenu(temps)
    val disparition = CarrouselGalerie.disparition(temps)
    val trajets = anim.trajets
    // Halos : image très floutée derrière la carte du centre (fondus d'un titre à l'autre)
    for (tr in trajets) {
        if (tr.fantome) continue
        val g = tr.a(p)
        if (g.heros <= 0.01f) continue
        val halo = titres.getOrNull(tr.index)?.let { visuels[it.cle] }?.halo ?: continue
        drawImage(
            halo,
            dstOffset = IntOffset((510f * k).roundToInt(), (50f * k).roundToInt()),
            dstSize = IntSize((1160f * k).roundToInt(), (800f * k).roundToInt()),
            alpha = 0.7f * g.heros,
            filterQuality = FilterQuality.Low,
        )
    }
    // Cartes : fantômes d'abord, le titre qui arrive au centre par-dessus tout
    val ordre = trajets.sortedBy { (if (it.fantome) 0 else 1) + (if (!it.fantome && it.arrivee.heros >= 1f) 10 else 0) }
    val dessinees = ArrayList<Triple<PlaceGalerie, VisuelGalerie?, Float>>(ordre.size)
    for (tr in ordre) {
        val g = tr.a(p)
        if (g.alpha <= 0.004f) continue
        val l = g.l * k
        val w = g.w * k
        if (l > size.width || l + w < 0f) continue
        val s = titres.getOrNull(tr.index) ?: continue
        val contenu = if (tr.fantome) 0f else when (tr.index) {
            courant -> apparition
            precedent -> disparition
            else -> 0f
        }
        val v = visuels[s.cle]
        val marque = s is StudioSuggestion.Programme && s.programme.id in marques
        dessinerCarte(g, v, contenu, marque, k)
        dessinees += Triple(g, v, contenu)
    }
    // Panneau de verre : l'image passe floutée dessous (flou d'arrière-plan imité)
    if (panneau) {
        val forme = formeArrondie(G_PANNEAU_L * k, G_PANNEAU_T * k, G_PANNEAU_W * k, G_PANNEAU_H * k, 32f * k)
        clipPath(forme) {
            for ((g, v, contenu) in dessinees) {
                val doux = v?.doux ?: continue
                val l = g.l * k
                val t = g.t * k
                val w = g.w * k
                val h = g.h * k
                if (t + h < G_PANNEAU_T * k || g.affiche >= 1f) continue
                imageArrondie(
                    doux, l, t, w, h, g.rayon * k, 0.3f, g.alpha * (1f - g.affiche),
                    FiltresGalerie.pour(g.saturation * (1f - g.gris), g.contraste),
                )
                // Le voile du titre reste sous le verre
                if (contenu > 0.003f) {
                    drawRoundRect(
                        Brush.verticalGradient(0f to VOILE[0], 0.32f to VOILE[1], 0.62f to VOILE[2], 1f to VOILE[3], startY = t, endY = t + h),
                        topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(g.rayon * k), alpha = contenu * g.alpha,
                    )
                }
            }
        }
    }
}

/** Une carte : ombre, image (adoucie, noir et blanc, affiche…), voile du titre, reflet et liseré. */
private fun DrawScope.dessinerCarte(g: PlaceGalerie, v: VisuelGalerie?, contenu: Float, marque: Boolean, k: Float) {
    val l = g.l * k
    val t = g.t * k
    val w = g.w * k
    val h = g.h * k
    val r = g.rayon * k
    val a = g.alpha
    OmbreGalerie.dessiner(
        this, l, t, w, h, (50f + 30f * g.heros) * k, (26f + 14f * g.heros) * k,
        Color(0xFF14161C).copy(alpha = (0.28f + 0.07f * g.heros) * a),
    )
    val filtre = FiltresGalerie.pour(g.saturation * (1f - g.gris), g.contraste)
    val affiche = v?.affiche
    val partFond = if (affiche == null) 1f else 1f - g.affiche
    val net = v?.net
    // Fond sombre : inutile sous une photo opaque qui couvre toute la carte (une surface de moins à remplir)
    val couverte = net != null && v.opaque && partFond >= 0.999f && a >= 0.999f
    if (!couverte) {
        drawRoundRect(Color(0xFF0B0B0E), topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(r), alpha = a)
    }
    if (net != null && partFond > 0.003f) {
        val doux = v.doux
        if (doux != null && g.flou > 0.01f) {
            imageArrondie(doux, l, t, w, h, r, 0.3f, a * partFond, filtre)
            imageArrondie(net, l, t, w, h, r, 0.3f, a * partFond * (1f - g.flou), filtre)
        } else {
            imageArrondie(net, l, t, w, h, r, 0.3f, a * partFond, filtre)
        }
    }
    if (affiche != null && g.affiche > 0.003f) imageArrondie(affiche, l, t, w, h, r, 0.5f, a * g.affiche, filtre)
    // Voile du titre (avec son contenu)
    if (contenu > 0.003f) {
        drawRoundRect(
            Brush.verticalGradient(0f to VOILE[0], 0.32f to VOILE[1], 0.62f to VOILE[2], 1f to VOILE[3], startY = t, endY = t + h),
            topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(r), alpha = contenu * a,
        )
    }
    // Reflet en haut à gauche et liseré blanc intérieur
    drawRoundRect(
        degradeCss(140f, w, h, 0f to Color.White.copy(alpha = 0.26f), 0.35f to Color.White.copy(alpha = 0f), l = l, t = t),
        topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(r), alpha = a,
    )
    val e = 1.5f * k
    drawRoundRect(
        Color.White.copy(alpha = 0.4f * a), topLeft = Offset(l + e / 2f, t + e / 2f), size = Size(w - e, h - e),
        cornerRadius = CornerRadius((r - e / 2f).coerceAtLeast(0f)), style = Stroke(e),
    )
    // Titre déjà dans « Ma liste » : coche (pas sur la carte du centre, qui l'affiche en clair)
    val coche = a * (1f - g.heros)
    if (marque && coche > 0.01f) {
        val c = Offset(l + w / 2f, t + h * 0.52f)
        drawCircle(Color.White.copy(alpha = 0.92f * coche), radius = 13f * k, center = c)
        val chemin = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - 6f * k, c.y + 0.5f * k)
            lineTo(c.x - 2f * k, c.y + 4.5f * k)
            lineTo(c.x + 6f * k, c.y - 4.5f * k)
        }
        drawPath(
            chemin, GalerieEncre.copy(alpha = coche),
            style = Stroke(2.4f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/** Texte de la carte du centre : service, rang, titre en serif, pastilles ; fondu par [alpha]. */
@Composable
private fun ContenuHeros(
    s: StudioSuggestion,
    onglet: OngletGalerie,
    ctx: HomeContext,
    k: Float,
    alpha: () -> Float,
    tag: String?,
) {
    val place = CarrouselGalerie.place(0)
    val (nom, paquet) = when (s) {
        is StudioSuggestion.Programme -> s.programme.titre to (s.app?.packageName ?: s.programme.paquet)
        is StudioSuggestion.AppUne -> s.app.label to s.app.packageName
    }
    val service = when (s) {
        is StudioSuggestion.Programme -> s.programme.service
        is StudioSuggestion.AppUne -> s.phrase
    }
    val droite = when (s) {
        is StudioSuggestion.Programme -> s.programme.rang?.let { "N°$it cette semaine" }
            ?: if (onglet == OngletGalerie.MA_LISTE) "Dans ma liste" else "Populaire en ce moment"
        is StudioSuggestion.AppUne -> null
    }
    val icone = rememberAppIcon(ctx.ui.iconSourceOf(paquet), ctx.icons)
    Box(
        Modifier
            .dansGalerie(place.l, place.t, k)
            .size(place.w.mq, place.h.mq)
            // Fondu appliqué à chaque dessin (pas de calque intermédiaire à remplir à chaque image)
            .graphicsLayer {
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 34.mq, end = 34.mq, top = 28.mq).height(30.mq),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icone != null) {
                Image(
                    icone, contentDescription = null, filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(30.mq).clip(RoundedCornerShape(8.mq)),
                )
                Spacer(Modifier.width(10.mq))
            }
            TexteGalerie(
                service.uppercase(Locale.FRENCH), 15f, Color.White, Modifier.weight(1f),
                poids = FontWeight.Bold, espacement = 0.9f, interligne = 20f,
            )
            if (droite != null) TexteGalerie(droite, 22f, Color.White.copy(alpha = 0.9f), serif = true, interligne = 28f)
        }
        if (s is StudioSuggestion.Programme) {
            // Titre en serif (pas pour une app à la une : son logo est déjà sur l'image)
            val taille = when {
                nom.length <= 22 -> 60f
                nom.length <= 34 -> 50f
                else -> 42f
            }
            TexteGalerie(
                nom, taille, Color.White,
                Modifier.fillMaxWidth().padding(start = 60.mq, end = 60.mq, top = 104.mq),
                poids = FontWeight.Normal, serif = true, interligne = taille * 1.04f, lignes = 2, centre = true,
                ombre = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f * k), 22f * k),
            )
            val p = s.programme
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 262.mq),
                horizontalArrangement = Arrangement.spacedBy(8.mq),
            ) {
                for (b in pastillesProgramme(p)) Pastille(b, plein = false)
                p.note?.let { Pastille("★ " + String.format(Locale.FRENCH, "%.1f", it), plein = true) }
            }
        }
    }
}

/** « Film », genre, année. */
private fun pastillesProgramme(p: StudioProgramme): List<String> = listOfNotNull(
    if (p.genre == GenreTmdb.FILM) "Film" else "Série",
    p.genres.firstOrNull(),
    p.sortie?.take(4),
)

/** Pastille d'information sur la carte du centre (pas un bouton : simple liseré). */
@Composable
private fun Pastille(texte: String, plein: Boolean) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (plein) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f))
            .then(
                if (plein) Modifier else Modifier.drawBehind {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.5f), topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f),
                        cornerRadius = CornerRadius(size.height / 2f), style = Stroke(1f * density),
                    )
                },
            )
            .padding(horizontal = 12.mq, vertical = 4.mq),
    ) {
        TexteGalerie(texte, 14f, if (plein) Color(0xFF1B1C20) else Color.White, poids = FontWeight.Bold, interligne = 19f)
    }
}

/**
 * Éléments sélectionnables du carrousel, à des places fixes : la carte du centre (◀ ▶ : glissement,
 * OK : regarder, appui long : menu de l'app du service), les flèches et les tuiles (OK ou clic :
 * le titre vient au centre).
 */
@Composable
private fun CiblesCarrousel(
    titres: List<StudioSuggestion>,
    courant: Int,
    nav: GalerieNav,
    k: Float,
    focusCarte: FocusRequester,
    heros: StudioSuggestion?,
    regarder: (StudioSuggestion) -> Unit,
    ctx: HomeContext,
    deplacer: (Int) -> Unit,
    temps: () -> Float,
) {
    val n = titres.size
    // Tuiles : une par place visible occupée (ordre ◀ ▶ : celui du carrousel)
    val occupees = remember(n, courant) {
        (0 until n).map { CarrouselGalerie.decalage(it, courant, n) }
            .filter { it != 0 && it in CarrouselGalerie.VISIBLES }.distinct().sorted()
    }
    for (o in occupees) {
        key(o) {
            val pl = CarrouselGalerie.place(o)
            val s = titres[CarrouselGalerie.pas(courant, o, n)]
            CibleTransparente(
                k = k, rayon = pl.rayon,
                onClick = {
                    deplacer(o)
                    runCatching { focusCarte.requestFocus() }
                },
                description = when (s) {
                    is StudioSuggestion.Programme -> s.programme.titre
                    is StudioSuggestion.AppUne -> s.app.label
                },
                modifier = Modifier
                    .dansGalerie(pl.l, pl.t, k)
                    .size(pl.w.mq, pl.h.mq)
                    .testTag("galerie:place:$o")
                    .then(rememberCibleGalerie(nav, EtageGalerie.GALERIE, "place:$o", ordre = o * 10f)),
            )
        }
    }
    // Carte du centre avant les flèches : son anneau passe sous leur verre
    val c = CarrouselGalerie.place(0)
    val app = when (heros) {
        is StudioSuggestion.Programme -> heros.app
        is StudioSuggestion.AppUne -> heros.app
        null -> null
    }
    CibleTransparente(
        k = k, rayon = c.rayon,
        // L'anneau suit le contenu du titre : il quitte la carte qui s'en va et revient sur la nouvelle
        visibilite = { maxOf(CarrouselGalerie.apparitionContenu(temps()), CarrouselGalerie.disparition(temps())) },
        onClick = { heros?.let(regarder) },
        onLongClick = app?.let { a -> { ctx.actions.openMenu(a) } },
        description = when (heros) {
            is StudioSuggestion.Programme -> heros.programme.titre
            is StudioSuggestion.AppUne -> heros.app.label
            null -> null
        },
        modifier = Modifier
            .dansGalerie(c.l, c.t, k)
            .size(c.w.mq, c.h.mq)
            .testTag("galerie:carte")
            .then(rememberCibleGalerie(nav, EtageGalerie.GALERIE, CLE_CARTE, fr = focusCarte, ordre = 0f)),
    )
    if (n > 1) {
        Fleche(k, 588f, -1, nav, deplacer)
        Fleche(k, 1530f, 1, nav, deplacer)
    }
}

/** Élément sélectionnable posé sur une carte du dessin : invisible, sauf son anneau de sélection. */
@Composable
private fun CibleTransparente(
    k: Float,
    rayon: Float,
    onClick: () -> Unit,
    modifier: Modifier,
    description: String?,
    onLongClick: (() -> Unit)? = null,
    visibilite: () -> Float = { 1f },
) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            // Calque à lui : l'anneau suit le glissement (relu à chaque image) sans réenregistrer l'écran
            .graphicsLayer()
            .drawBehind { anneauGalerie(k, rayon * k, t * visibilite(), ombre = false) }
            .onFocusChanged { focus = it.hasFocus }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape((rayon / 2f).dp)),
        colors = couleursTransparentes(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {}
}

@Composable
private fun couleursTransparentes() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent, contentColor = Color.White,
    focusedContainerColor = Color.Transparent, focusedContentColor = Color.White,
    pressedContainerColor = Color.Transparent, pressedContentColor = Color.White,
)

/** Flèche ronde ◀ / ▶ en verre de part et d'autre de la carte du centre. */
@Composable
private fun Fleche(k: Float, x: Float, sens: Int, nav: GalerieNav, deplacer: (Int) -> Unit) {
    BoutonRond(
        k = k, taille = 62f, onClick = { deplacer(sens) },
        modifier = Modifier.dansGalerie(x, 402f, k)
            .testTag(if (sens < 0) "galerie:precedent" else "galerie:suivant")
            .then(rememberCibleGalerie(nav, EtageGalerie.GALERIE, if (sens < 0) "precedent" else "suivant", ordre = sens * 5f)),
        description = if (sens < 0) "Titre précédent" else "Titre suivant",
    ) {
        pictogramme(GalerieEncreTitre, 2.2f) {
            if (sens < 0) {
                moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
            } else {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }
}

/** Bouton rond de verre ([taille] px de maquette), pictogramme dessiné par [dessin]. */
@Composable
internal fun BoutonRond(
    k: Float,
    taille: Float,
    onClick: () -> Unit,
    modifier: Modifier,
    description: String,
    onLongClick: (() -> Unit)? = null,
    dessin: DrawScope.() -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(taille.mq)
            .verreGalerie(k, 999f)
            .drawBehind { anneauGalerie(k, size.height / 2f, t) }
            .onFocusChanged { focus = it.hasFocus }
            .semantics { contentDescription = description },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = couleursTransparentes(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.96f),
    ) {
        Spacer(Modifier.fillMaxSize().padding((taille * 0.29f).mq).drawBehind(dessin))
    }
}

/** « Ajouter à ma liste » / « Retirer de ma liste » : pilule de verre sombre. */
@Composable
private fun BoutonAjout(dansLaListe: Boolean, k: Float, onClick: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .verreGalerie(k, 999f, fond = { _, _ -> Brush.linearGradient(listOf(Color(0x8C3C3E46), Color(0x8C3C3E46))) }, lisere = Color.White.copy(alpha = 0.25f))
            .drawBehind { anneauGalerie(k, size.height / 2f, t) }
            .onFocusChanged { focus = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = couleursTransparentes(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        TexteGalerie(
            if (dansLaListe) "Retirer de ma liste" else "Ajouter à ma liste", 16f, Color.White,
            Modifier.padding(horizontal = 28.mq, vertical = 12.mq), poids = FontWeight.Bold, interligne = 24f,
        )
    }
}

/** « Ma liste » vide : une grande carte de verre à la place du titre du centre. */
@Composable
private fun ListeVideGalerie(k: Float) {
    val c = CarrouselGalerie.place(0)
    Column(
        Modifier
            .dansGalerie(c.l, c.t, k)
            .size(c.w.mq, c.h.mq)
            .verreGalerie(k, c.rayon, fond = { w, h ->
                degradeCss(160f, w, h, 0f to Color.White.copy(alpha = 0.62f), 0.6f to Color.White.copy(alpha = 0.34f), 1f to Color.White.copy(alpha = 0.5f))
            })
            .testTag("galerie:liste-vide"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(
            Modifier.size(64.mq).drawBehind {
                pictogramme(GalerieEncreTitre, 1.6f) {
                    moveTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 20.5f); lineTo(12f, 16f); lineTo(6f, 20.5f); close()
                    moveTo(12f, 7.5f); lineTo(12f, 12.5f); moveTo(9.5f, 10f); lineTo(14.5f, 10f)
                }
            },
        )
        TexteGalerie("Ma liste est vide", 56f, GalerieEncre, Modifier.padding(top = 22.mq), poids = FontWeight.Normal, serif = true, interligne = 62f)
        TexteGalerie(
            "Ajoutez des titres avec « Ajouter à ma liste »", 21f, GalerieTete, Modifier.padding(top = 12.mq), interligne = 30f,
        )
        TexteGalerie(
            "Ils vous attendront ici, prêts à regarder.", 17f, GalerieGris, Modifier.padding(top = 6.mq), interligne = 24f,
        )
    }
}

// ------------------------------------------------------------------ panneau du synopsis

/**
 * Panneau de verre clair à cheval sur le bas de la carte du centre : « Titre · Film · Genre ·
 * Année », synopsis (2 lignes), « Regarder sur … » et « Bande-annonce », points de position,
 * trait de la couleur du service.
 */
@Composable
private fun PanneauGalerie(
    titres: List<StudioSuggestion>,
    courant: Int,
    precedent: Int,
    temps: () -> Float,
    k: Float,
    nav: GalerieNav,
    ctx: HomeContext,
    etat: StudioEtat,
    regarder: () -> Unit,
    bandeAnnonce: (() -> Unit)?,
) {
    val heros = titres.getOrNull(courant) ?: return
    val couleur by animateColorAsState(couleurService(heros, ctx), tween(CarrouselGalerie.DUREE), label = "trait")
    Box(
        Modifier
            .dansGalerie(G_PANNEAU_L, G_PANNEAU_T, k)
            .size(G_PANNEAU_W.mq, G_PANNEAU_H.mq)
            .verreGalerie(k, 32f, fond = { w, h ->
                degradeCss(
                    160f, w, h,
                    0f to Color.White.copy(alpha = 0.78f), 0.6f to Color(0xFFF6F7F9).copy(alpha = 0.6f), 1f to Color.White.copy(alpha = 0.7f),
                )
            })
            .clip(RoundedCornerShape(32.mq))
            .drawBehind {
                // Lueur douce de la couleur du service, sous le trait
                OmbreGalerie.dessiner(this, 80f * k, size.height - 6f * k, size.width - 160f * k, 20f * k, 22f * k, 0f, couleur.copy(alpha = 0.55f))
            },
    ) {
        for ((s, entrant) in affichesAuCentre(titres, courant, precedent)) {
            key(s.cle, entrant) {
                TexteSynopsis(
                    s, etat, k,
                    alpha = if (entrant) ({ CarrouselGalerie.apparitionSynopsis(temps()) }) else ({ CarrouselGalerie.disparition(temps()) }),
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 26.mq, bottom = 26.mq),
            horizontalArrangement = Arrangement.spacedBy(12.mq),
        ) {
            BoutonRegarder(titres, courant, precedent, temps, k, regarder, Modifier.testTag("galerie:regarder").then(rememberCibleGalerie(nav, EtageGalerie.ACTIONS, "regarder")))
            if (bandeAnnonce != null) {
                BoutonBandeAnnonce(k, bandeAnnonce, Modifier.testTag("galerie:bande-annonce").then(rememberCibleGalerie(nav, EtageGalerie.ACTIONS, "bande-annonce")))
            }
        }
        // Points de position : au plus 11 (au-delà, une fenêtre qui suit le titre du centre)
        val n = titres.size
        if (n > 1) {
            val debut = if (n <= G_POINTS) 0 else (courant - G_POINTS / 2).coerceIn(0, n - G_POINTS)
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 30.mq, bottom = 44.mq), horizontalArrangement = Arrangement.spacedBy(7.mq)) {
                for (i in debut until minOf(n, debut + G_POINTS)) key(i) { Point(i == courant) }
            }
        }
        // Trait de la couleur du service
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 30.mq)
                .fillMaxWidth()
                .height(4.mq)
                .drawBehind {
                    drawRoundRect(
                        Brush.horizontalGradient(0f to couleur, 0.55f to lerp(couleur, Color.White, 0.45f), 1f to couleur),
                        cornerRadius = CornerRadius(4f * k),
                    )
                },
        )
    }
}

/** Couleur du service d'un titre (Netflix rouge, Prime Video bleu…), sinon celle de son app. */
@Composable
private fun couleurService(s: StudioSuggestion, ctx: HomeContext): Color {
    val app: AppInfo? = when (s) {
        is StudioSuggestion.Programme -> s.app
        is StudioSuggestion.AppUne -> s.app
    }
    val paquet = when (s) {
        is StudioSuggestion.Programme -> s.programme.paquet
        is StudioSuggestion.AppUne -> s.app.packageName
    }
    val connue = (Illustrations.parPaquet(paquet) ?: app?.let { Illustrations.parLibelle(it.label) })?.couleur
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(paquet), ctx.icons, FormatVisuel.CARRE)
    return connue?.let { Color(it) } ?: visuel?.color ?: Color(0xFF4F8DF5)
}

@Composable
private fun Point(actif: Boolean) {
    val l by animateFloatAsState(if (actif) 26f else 8f, tween(500, easing = { CarrouselGalerie.COURBE.transform(it) }), label = "point")
    Box(
        Modifier
            .size(l.mq, 8.mq)
            .background(if (actif) GalerieEncre else GalerieEncre.copy(alpha = 0.25f), RoundedCornerShape(4.mq)),
    )
}

/** En-tête et synopsis d'un titre dans le panneau. */
@Composable
private fun TexteSynopsis(s: StudioSuggestion, etat: StudioEtat, k: Float, alpha: () -> Float) {
    val (titre, details, resume) = when (s) {
        is StudioSuggestion.Programme -> Triple(
            s.programme.titre,
            pastillesProgramme(s.programme).joinToString(" · "),
            s.programme.resume.ifBlank { "Pas encore de résumé en français pour ce titre." },
        )
        is StudioSuggestion.AppUne -> Triple(
            s.app.label,
            s.phrase,
            when (etat) {
                StudioEtat.SANS_CLE -> "Activez les suggestions dans Réglages → « Suggestions de films et séries » : les films et séries de vos applis s'afficheront ici."
                StudioEtat.ERREUR -> "Suggestions indisponibles pour le moment (pas de connexion à TMDB). Vos applis restent à portée de main."
                StudioEtat.OK -> "Installez Netflix, Prime Video, Disney+ ou Canal+ pour voir ici leurs films et séries du moment."
            },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.mq, end = 30.mq, top = 26.mq)
            .graphicsLayer {
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TexteGalerie(titre, 15f, GalerieEncre, Modifier.widthIn(max = 520.mq), poids = FontWeight.Bold, interligne = 20f)
            TexteGalerie("·", 15f, GalerieTete, Modifier.padding(horizontal = 12.mq), poids = FontWeight.Bold, interligne = 20f)
            TexteGalerie(details, 15f, GalerieTete, poids = FontWeight.Bold, interligne = 20f)
        }
        TexteGalerie(resume, 19.5f, GalerieTexte, Modifier.padding(top = 10.mq), interligne = 29.25f, lignes = 2)
    }
}

/** « Regarder sur Netflix » (« Ouvrir … » pour une app à la une) : grande pilule d'encre. */
@Composable
private fun BoutonRegarder(
    titres: List<StudioSuggestion>,
    courant: Int,
    precedent: Int,
    temps: () -> Float,
    k: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 16f * k, 6f * k, Color.Black.copy(alpha = 0.12f))
                anneauGalerie(k, size.height / 2f, t)
            }
            .onFocusChanged { focus = it.hasFocus }
            .height(54.mq)
            .widthIn(min = 300.mq),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1D1E22), contentColor = Color.White,
            focusedContainerColor = Color(0xFF1D1E22), focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF2B2D33), pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Spacer(
            Modifier.padding(start = 26.mq).size(20.mq).align(Alignment.CenterStart).drawBehind { triangleLecture(Color.White) },
        )
        Box(Modifier.align(Alignment.Center).padding(start = 56.mq, end = 24.mq), contentAlignment = Alignment.Center) {
            for ((s, entrant) in affichesAuCentre(titres, courant, precedent)) {
                key(s.cle, entrant) {
                    TexteGalerie(
                        libelleRegarder(s), 18f, Color.White,
                        Modifier.graphicsLayer {
                            alpha = if (entrant) CarrouselGalerie.apparitionSynopsis(temps()) else CarrouselGalerie.disparition(temps())
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                        poids = FontWeight.Bold, interligne = 24f,
                    )
                }
            }
        }
    }
}

private fun libelleRegarder(s: StudioSuggestion): String = when (s) {
    is StudioSuggestion.Programme -> "Regarder sur " + s.programme.service
    is StudioSuggestion.AppUne -> "Ouvrir " + s.app.label
}

/** « Bande-annonce » : pilule de verre claire. */
@Composable
private fun BoutonBandeAnnonce(k: Float, onClick: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 16f * k, 6f * k, Color.Black.copy(alpha = 0.08f))
                drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = CornerRadius(size.height / 2f))
                val e = 1.3f * k
                drawRoundRect(
                    Color.White.copy(alpha = 0.75f), topLeft = Offset(e / 2f, e / 2f), size = Size(size.width - e, size.height - e),
                    cornerRadius = CornerRadius(size.height / 2f - e / 2f), style = Stroke(e),
                )
                anneauGalerie(k, size.height / 2f, t)
            }
            .onFocusChanged { focus = it.hasFocus }
            .height(54.mq),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = GalerieEncre,
            focusedContainerColor = Color.White.copy(alpha = 0.35f), focusedContentColor = GalerieEncre,
            pressedContainerColor = Color.White.copy(alpha = 0.5f), pressedContentColor = GalerieEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 24.mq), verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.size(20.mq).drawBehind {
                    pictogramme(GalerieEncre, 2f) {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(3f, 5f, 21f, 19f, CornerRadius(3f)))
                    }
                    triangleLecture(GalerieEncre, 10f, 9.5f, 14f, 14.5f)
                },
            )
            Spacer(Modifier.width(10.mq))
            TexteGalerie("Bande-annonce", 18f, GalerieEncre, poids = FontWeight.Bold, interligne = 24f)
        }
    }
}

// ------------------------------------------------------------------ en-tête

@Composable
private fun EnTeteGalerie(
    ctx: HomeContext,
    nav: GalerieNav,
    k: Float,
    onglets: List<OngletGalerie>,
    onglet: OngletGalerie,
    focusOnglets: Map<OngletGalerie, FocusRequester>,
    choisir: (OngletGalerie) -> Unit,
    credit: Boolean,
    versEcran: (EtageGalerie) -> Unit,
) {
    val a = ctx.actions
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.mq)
            .etageGalerie(nav, EtageGalerie.HAUT, lateral = true, surFocus = {
                versEcran(it)
                ctx.sansSelection()
            }),
    ) {
        // iBiTV, l'heure et la date
        Row(Modifier.padding(start = 72.mq, top = 50.mq), verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.size(48.mq).verreGalerie(k, 999f).padding(11.mq).drawBehind {
                    pictogramme(GalerieEncreTitre, 2f) {
                        moveTo(12f, 3f); lineTo(12f, 21f)
                        moveTo(3f, 12f); lineTo(21f, 12f)
                        moveTo(5.6f, 5.6f); lineTo(18.4f, 18.4f)
                        moveTo(18.4f, 5.6f); lineTo(5.6f, 18.4f)
                    }
                    val s = size.minDimension / 24f
                    drawCircle(Color(0xFFE9EAEE), radius = 3.2f * s, center = center)
                    drawCircle(GalerieEncreTitre, radius = 3.2f * s, center = center, style = Stroke(2f * s))
                },
            )
            Spacer(Modifier.width(14.mq))
            Column {
                TexteGalerie("iBiTV", 27f, GalerieEncreTitre, poids = FontWeight.Bold, interligne = 32f)
                val t = rememberMaintenant()
                TexteGalerie("${heureTexte(t)} · ${dateTexte(t)}", 15f, GalerieGris, Modifier.padding(top = 2.mq), interligne = 20f)
            }
        }
        // Onglets
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 52.mq).verreGalerie(k, 999f).padding(6.mq),
            horizontalArrangement = Arrangement.spacedBy(4.mq),
        ) {
            for (o in onglets) {
                BoutonOnglet(
                    o.titre, o == onglet, k, onClick = { choisir(o) },
                    modifier = Modifier.testTag("onglet:" + o.cle)
                        .then(rememberCibleGalerie(nav, EtageGalerie.HAUT, "onglet:" + o.cle, fr = focusOnglets.getValue(o))),
                )
            }
        }
        // Mise à jour, météo, Réglages
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 52.mq, end = 72.mq).height(60.mq),
            horizontalArrangement = Arrangement.spacedBy(18.mq),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ctx.majDisponible) {
                PiluleMiseAJour(k, a.update, Modifier.testTag("rapide:Mise à jour").then(rememberCibleGalerie(nav, EtageGalerie.HAUT, "maj")))
            }
            meteoGalerie()?.let { PiluleMeteo(it, k) }
            BoutonRond(
                k = k, taille = 60f, onClick = a.settings, onLongClick = a.settingsLong,
                modifier = Modifier.testTag("rapide:Réglages")
                    .then(rememberCibleGalerie(nav, EtageGalerie.HAUT, "reglages", fr = ctx.focusReglages)),
                description = "Réglages",
            ) {
                // Roue crantée (Réglages) au trait
                val s = size.minDimension / 24f
                val c = center
                val trait = Stroke(1.9f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawCircle(GalerieEncreTitre, radius = 3f * s, center = c, style = trait)
                val roue = androidx.compose.ui.graphics.Path()
                val n = 8
                for (i in 0 until 2 * n) {
                    val ang = Math.PI * i / n - Math.PI / 2 - Math.PI / (2 * n)
                    val r1 = if (i % 2 == 0) 9.6f else 7.4f
                    for (j in 0..1) {
                        val aj = ang + (Math.PI / n) * j
                        val x = c.x + (r1 * s * kotlin.math.cos(aj)).toFloat()
                        val y = c.y + (r1 * s * kotlin.math.sin(aj)).toFloat()
                        if (i == 0 && j == 0) roue.moveTo(x, y) else roue.lineTo(x, y)
                    }
                }
                roue.close()
                drawPath(roue, GalerieEncreTitre, style = trait)
            }
        }
        if (credit) {
            TexteGalerie(
                "Titres et images : TMDB", 13f, Color(0xFF3C3E46).copy(alpha = 0.55f),
                Modifier.align(Alignment.TopEnd).padding(top = 126.mq, end = 74.mq), interligne = 17f,
            )
        }
    }
}

/** Onglet : pilule d'encre s'il est choisi ; OK (ou clic) le choisit, l'anneau montre la sélection. */
@Composable
private fun BoutonOnglet(titre: String, actif: Boolean, k: Float, onClick: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    val fond = if (actif) GalerieEncre else Color.Transparent
    val encre = if (actif) Color.White else GalerieGrisOnglet
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                if (actif) OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 18f * k, 8f * k, Color.Black.copy(alpha = 0.22f))
                anneauGalerie(k, size.height / 2f, t, ombre = false)
            }
            .onFocusChanged { focus = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond, contentColor = encre,
            focusedContainerColor = if (actif) fond else Color.White.copy(alpha = 0.45f), focusedContentColor = if (actif) encre else GalerieEncre,
            pressedContainerColor = if (actif) fond else Color.White.copy(alpha = 0.6f), pressedContentColor = if (actif) encre else GalerieEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        TexteGalerie(
            titre, 19f, if (actif) Color.White else if (focus) GalerieEncre else GalerieGrisOnglet,
            Modifier.padding(horizontal = 30.mq, vertical = 12.mq), poids = FontWeight.Bold, interligne = 26f,
        )
    }
}

/** Mise à jour disponible : pilule d'encre (elle ouvre l'installation). */
@Composable
private fun PiluleMiseAJour(k: Float, onClick: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .drawBehind { anneauGalerie(k, size.height / 2f, t) }
            .onFocusChanged { focus = it.hasFocus }
            .height(54.mq),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GalerieEncre, contentColor = Color.White,
            focusedContainerColor = GalerieEncre, focusedContentColor = Color.White,
            pressedContainerColor = GalerieEncre, pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 22.mq), verticalAlignment = Alignment.CenterVertically) {
            Icon(ProjoIcons.MiseAJour, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.mq))
            Spacer(Modifier.width(10.mq))
            TexteGalerie("Mise à jour", 17f, Color.White, poids = FontWeight.Bold, interligne = 22f)
        }
    }
}

/** Météo du module Météo (s'il est choisi dans Réglages → Modules et qu'il a une donnée), sinon null. */
@Composable
private fun meteoGalerie(): MeteoDonnees? {
    val hote = LocalHoteModules.current ?: return null
    if (hote.modules.none { it.id == ModuleMeteo.id }) return null
    val cle = ModuleMeteo.cleDonnees(hote.reglagesDe(ModuleMeteo.id))
    val flux = remember(hote.donnees, cle) { hote.donnees.flux(cle) }
    val e by flux.collectAsState()
    return e.valeur as? MeteoDonnees
}

/** Pilule météo (information, pas un bouton) : ciel, température et ville. */
@Composable
private fun PiluleMeteo(m: MeteoDonnees, k: Float) {
    Row(
        Modifier
            .testTag("galerie:meteo")
            .verreGalerie(k, 999f)
            .padding(start = 14.mq, end = 22.mq, top = 10.mq, bottom = 10.mq),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mq),
    ) {
        IconeMeteo(m.code, m.jour, GalerieEncreTitre, Modifier.size(34.mq))
        val r = m.temperature.roundToInt()
        TexteGalerie("${if (r == 0) 0 else r}°", 18f, GalerieEncreTitre, poids = FontWeight.Bold, interligne = 24f)
        TexteGalerie(m.lieu, 15f, GalerieGris, Modifier.widthIn(max = 220.mq), interligne = 20f)
    }
}

// ------------------------------------------------------------------ écran 2 : modules

/**
 * Modules choisis dans Réglages → Modules, sur l'écran 2 (▼ depuis le dock ; la page glisse,
 * le dock remonte en haut) : chacun dans un grand panneau de verre, deux côte à côte ou un
 * seul sur toute la largeur. Taille GRAND si un module la propose, sinon MOYEN.
 */
@Composable
private fun ModulesGalerie(ctx: HomeContext, nav: GalerieNav, k: Float, hauteur: Float, decalage: Float, versEcran: (EtageGalerie) -> Unit) {
    val hote = LocalHoteModules.current
    val modules = hote?.modules
    val grand = modules == null || modules.any { TailleModule.GRAND in it.tailles }
    // Place sur l'écran 2 : sous le dock (et son reflet), jusqu'à 40 px du bas
    val haut = G_DOCK_ECRAN2 + 128f + 62f
    val dispo = hauteur - haut - 40f
    val hPx = if (grand) minOf(dispo, 640f) else minOf(dispo, 300f)
    val hU = (hPx / 2f).roundToInt()
    val n = if (hote != null && modules != null && modules.size > 1) 2 else 1
    val pleine = 880
    val ecart = 12
    val largeur = if (n == 2) (pleine - ecart) / 2 else pleine
    val y = decalage + haut + (dispo - hPx) / 2f
    val entrees = remember { listOf(FocusRequester(), FocusRequester()) }
    Row(
        Modifier
            .offset { IntOffset(((1920f - 2f * pleine) / 2f * k).roundToInt(), (y * k).roundToInt()) }
            .height(hU.u)
            .etageModulesGalerie(nav, versEcran),
        horizontalArrangement = Arrangement.spacedBy(ecart.u),
    ) {
        for (i in 0 until n) {
            key(i, modules?.getOrNull(i)?.id) {
                val hoteZone = remember(hote, i, n) {
                    if (hote == null || n == 1) hote
                    else HoteModules(listOf(hote.modules[i].id), hote.reglages, hote.donnees, hote.ecrireReglage)
                }
                Box(
                    Modifier
                        .width(largeur.u)
                        .fillMaxHeight()
                        .testTag("galerie:panneau")
                        .verreGalerie(k, 32f, fond = { w, h ->
                            degradeCss(160f, w, h, 0f to Color.White.copy(alpha = 0.7f), 0.6f to Color.White.copy(alpha = 0.45f), 1f to Color.White.copy(alpha = 0.58f))
                        })
                        .then(rememberCibleGalerie(nav, EtageGalerie.MODULES, "zone:$i", fr = entrees[i], attacher = false))
                        .onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalHoteModules provides hoteZone) {
                        NbaPanel(
                            data = ctx.nbaData,
                            erreur = ctx.nbaErreur,
                            repo = ctx.nbaRepo,
                            onExit = ctx.sortieNba,
                            style = styleModulesGalerie(largeur, hU),
                            entree = entrees[i],
                            onGauche = if (i == 1) ({ runCatching { entrees[0].requestFocus() }; Unit }) else null,
                        )
                    }
                }
            }
        }
    }
}

/** Style de la zone de modules de Galerie : transparente (le verre est dessiné autour), encre et gris. */
private fun styleModulesGalerie(largeur: Int, hauteur: Int) = NbaStyle(
    largeur = largeur,
    rayon = 24,
    fond = Color.Transparent,
    bordure = Color.Transparent,
    texte = GalerieEncre,
    texteDiscret = Color(0xFF6C6F78),
    ongletActif = GalerieEncre.copy(alpha = 0.07f),
    focusFond = GalerieEncre,
    focusTexte = Color.White,
    pastilleFond = Color.White,
    zoneLargeur = largeur,
    zoneHauteur = hauteur,
)
