package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import fr.projo.launcher.boxscore.EquipesNba
import fr.projo.launcher.data.AmbianceCarteNba
import fr.projo.launcher.data.AmbianceNba
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.ClassementNba
import fr.projo.launcher.nba.ConferenceNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.DonneesNba
import fr.projo.launcher.nba.FicheEquipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Design « NBA » (docs/design-nba.md, maquette validée) : la carte des conférences et des
 * divisions, une île par division, les 30 équipes en pastilles (logo, bilan, rangs) ; en bas le
 * top 3 de chaque conférence, le dock des favoris et l'heure des derniers bilans.
 *
 * Navigation : focus initial sur le 1er favori du dock ; ▲ entre sur la carte (pastille la plus
 * proche au-dessus de la tuile), ◀ ▲ ▶ ▼ vont de pastille en pastille, ▼ en bas de la carte ou
 * Retour reviennent au dock ; OK ouvre la fiche de l'équipe (Résumé / Effectif avec ◀ ▶, Retour
 * la ferme).
 */

/** Ce que l'écran NBA lit : DonneesNba en vrai, des données factices dans les tests. */
interface SourceNba {
    /** Dernier classement connu (null : aucun pour l'instant). */
    val classement: StateFlow<ClassementNba?>

    /** Le design est-il affiché (et iBiTV au premier plan) ? */
    fun suivre(actif: Boolean)

    /** Fiche d'une équipe, null si indisponible. */
    suspend fun fiche(code: String): FicheEquipe?

    /** Logo déjà en mémoire, ou null (fil principal). */
    fun logoEnMemoire(code: String): ImageBitmap?

    /** Logo (cache disque, réseau) ; fonction bloquante, hors du fil principal (sauf [synchrone]). */
    fun logo(code: String): ImageBitmap?

    /** true : tout se lit et se dessine tout de suite, sans autre fil (tests). */
    val synchrone: Boolean get() = false

    /** Heure affichée (tests) ; null : l'heure courante. */
    val horloge: Long? get() = null
}

/** Source de l'écran NBA ; null : DonneesNba (fournie par HomeNba). */
val LocalSourceNba = staticCompositionLocalOf<SourceNba?> { null }

/** Source réelle : DonneesNba, logos par le chargeur des logos NBA de l'app (caches mémoire et disque). */
class SourceNbaAndroid(private val depot: NbaRepository) : SourceNba {
    override val classement: StateFlow<ClassementNba?> get() = DonneesNba.classement
    override fun suivre(actif: Boolean) = DonneesNba.suivre(actif)
    override suspend fun fiche(code: String): FicheEquipe? = DonneesNba.fiche(code)
    override fun logoEnMemoire(code: String): ImageBitmap? = depot.cachedLogo(DonneesNba.logo(code))
    override fun logo(code: String): ImageBitmap? = depot.loadLogo(DonneesNba.logo(code))
}

/** Écran d'accueil du design NBA : branché sur DonneesNba (ou la source des tests). */
@Composable
fun HomeNba(ctx: HomeContext) {
    val contexte = LocalContext.current
    val source = LocalSourceNba.current ?: remember(ctx.nbaRepo) { SourceNbaAndroid(ctx.nbaRepo) }
    // Classement suivi tant que le design est affiché et iBiTV au premier plan (écran allumé)
    val cycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = cycle.isAtLeast(Lifecycle.State.STARTED)
    DisposableEffect(source, visible) {
        source.suivre(visible)
        onDispose { source.suivre(false) }
    }
    remember { AmbianceCarteNba.charger(contexte) }
    val classement by source.classement.collectAsState()
    val minute = rememberMaintenant()
    EcranNba(ctx, source, classement, AmbianceCarteNba.ambiance, source.horloge ?: minute)
}

// Mesures de la maquette (px de l'écran de référence 1920 × 1080)
/** Haut du dock (sous la carte) : départ de ▲ vers la pastille la plus proche. */
private const val HAUT_DOCK = 934f

/**
 * L'écran, avec ses données : [classement] (null : pas encore de bilans), [ambiance],
 * [maintenant] (heure affichée). [source] fournit les logos et les fiches.
 */
@Composable
internal fun EcranNba(
    ctx: HomeContext,
    source: SourceNba,
    classement: ClassementNba?,
    ambiance: AmbianceNba,
    maintenant: Long,
) {
    val contexte = LocalContext.current
    val k = pxMaquette()
    // Hauteur de l'écran en px de maquette (1080 en 16:9)
    val hauteur = LocalConfiguration.current.screenHeightDp / LocalScale.current * 2f
    val bilans = classement?.equipes

    // ------------------------------------------------ carte (lue une fois, hors du fil principal)
    val carte by produceState(CarteNba.enMemoire, source) {
        if (value == null) {
            val lire = { CarteNba.charger { lireCarteNba(contexte) } }
            value = runCatching { if (source.synchrone) lire() else withContext(Dispatchers.IO) { lire() } }.getOrNull()
        }
    }

    // ------------------------------------------------ leaders et palette
    val leaders: Map<DivisionNba, String> = remember(bilans) {
        bilans.orEmpty().values.filter { it.rangDivision == 1 }
            .mapNotNull { b -> DivisionNba.de(b.code)?.let { it to b.code } }
            .toMap()
    }
    val palette = remember(ambiance, leaders) { PaletteNba.de(ambiance, leaders) }

    // ------------------------------------------------ logos (leaders d'abord), mémoire puis disque et réseau
    val logos = remember { mutableStateMapOf<String, ImageBitmap>() }
    val echecs = remember { HashSet<String>() }
    var essais by remember { mutableIntStateOf(0) }
    // Logos manquants (premier démarrage hors ligne) : nouvel essai à chaque classement reçu du réseau
    val majEnLigne = classement?.takeIf { !it.horsLigne }?.misAJour
    LaunchedEffect(source, leaders, majEnLigne) {
        for (code in (leaders.values + EquipesNba.toutes.map { it.code }).distinct()) {
            if (code in logos) continue
            val logo = source.logoEnMemoire(code)
                ?: if (source.synchrone) source.logo(code) else withContext(Dispatchers.IO) { runCatching { source.logo(code) }.getOrNull() }
            if (logo != null) {
                logos[code] = logo
                echecs -= code
            } else {
                echecs += code
            }
            essais++
        }
    }
    // Filigranes : les logos des leaders, une fois tous essayés (un seul nouveau dessin de la carte,
    // puis un autre seulement si un logo manquant arrive plus tard)
    val leadersPrets = essais >= 0 && leaders.values.all { it in logos || it in echecs }
    val logosLeaders = if (leadersPrets) leaders.values.count { it in logos } else -1
    val filigranes = remember(leaders, logosLeaders) {
        leaders.mapValues { (_, code) -> FiligraneNba(code, if (logosLeaders >= 0) logos[code] else null) }
    }

    // ------------------------------------------------ image de la carte (une par ambiance et jeu de leaders)
    val resolveur = LocalFontFamilyResolver.current
    val echelleImage = min(k, 1f)
    val image by produceState<ImageCarteNba?>(null, carte, palette, filigranes, echelleImage) {
        val c = carte ?: return@produceState
        val dessiner = { rendreCarteNba(c, palette, filigranes, TextMeasurer(resolveur, Density(1f), LayoutDirection.Ltr, 0), echelleImage) }
        value = runCatching { if (source.synchrone) dessiner() else withContext(Dispatchers.Default) { dessiner() } }.getOrNull() ?: value
    }
    val apparition by animateFloatAsState(if (image != null) 1f else 0f, tween(400), label = "carte")

    // ------------------------------------------------ sélection et fiche
    val centres = remember(carte) { carte?.pastilles?.associate { it.code to FormePastille.centre(it) } ?: emptyMap() }
    var choisie by remember { mutableStateOf<String?>(null) }
    var carteActive by remember { mutableStateOf(false) }
    var fiche by remember { mutableStateOf<String?>(null) }
    var ficheActive by remember { mutableStateOf(false) }
    var touchesVues by remember { mutableIntStateOf(0) }
    var onglet by remember { mutableIntStateOf(0) }
    val focusCarte = remember { FocusRequester() }
    val focusFiche = remember { FocusRequester() }
    var retourDock by remember { mutableStateOf<FocusRequester?>(null) }
    val versDock: () -> Unit = {
        choisie = null
        val fr = retourDock
        if (fr == null || runCatching { fr.requestFocus() }.isFailure) ctx.sortieNba()
    }
    val entrerCarte: (Float, FocusRequester) -> Unit = { x, fr ->
        NavCarteNba.voisine(Offset(x, HAUT_DOCK), SensNba.HAUT, centres)?.let { code ->
            choisie = code
            retourDock = fr
            runCatching { focusCarte.requestFocus() }
        }
    }
    val ouvrirFiche: (String) -> Unit = { code ->
        choisie = code
        onglet = 0
        fiche = code
    }
    LaunchedEffect(fiche) {
        if (fiche == null) return@LaunchedEffect
        withFrameNanos {}
        runCatching { focusFiche.requestFocus() }
    }
    // Retour : la carte reprend le focus ; focus parti ailleurs (Maison…) : plus de sélection
    val fermerFiche: (Boolean) -> Unit = { rendre ->
        if (fiche != null) {
            fiche = null
            if (rendre) {
                runCatching { focusCarte.requestFocus() }
            } else {
                choisie = null
            }
        }
    }
    val toucheCarte: (KeyEvent) -> Boolean = { e ->
        val sens = when (e.key) {
            Key.DirectionLeft -> SensNba.GAUCHE
            Key.DirectionRight -> SensNba.DROITE
            Key.DirectionUp -> SensNba.HAUT
            Key.DirectionDown -> SensNba.BAS
            else -> null
        }
        when {
            sens != null -> {
                if (e.type == KeyEventType.KeyDown) {
                    val depuis = choisie?.let { centres[it] }
                    val suivante = depuis?.let { NavCarteNba.voisine(it, sens, centres, sauf = choisie) }
                    if (suivante != null) {
                        choisie = suivante
                    } else if (sens == SensNba.BAS) {
                        versDock()
                    }
                }
                true
            }
            e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                if (e.type == KeyEventType.KeyUp) choisie?.let(ouvrirFiche)
                true
            }
            e.key == Key.Back || e.key == Key.Escape -> {
                if (e.type == KeyEventType.KeyUp) versDock()
                true
            }
            else -> false
        }
    }
    // Fiche : ◀ ▶ changent d'onglet, Retour la ferme ; le reste ne la quitte pas
    val toucheFiche: (KeyEvent) -> Boolean = { e ->
        when (e.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                if (e.type == KeyEventType.KeyDown) onglet = if (e.key == Key.DirectionLeft) 0 else 1
                true
            }
            Key.Back, Key.Escape -> {
                if (e.type == KeyEventType.KeyUp) fermerFiche(true)
                true
            }
            Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
            else -> false
        }
    }
    val choisieAffichee = if (carteActive || fiche != null) choisie else null

    // Touches de la carte et de la fiche, lues avant tout le reste (elles ont le focus)
    Box(
        Modifier
            .fillMaxSize()
            .fondNba(palette.fond)
            .testTag("nba:ecran")
            .semantics { stateDescription = "touches=$touchesVues carte=$carteActive choisie=$choisie fiche=$fiche ficheActive=$ficheActive centres=${centres.size}" }
            .onPreviewKeyEvent { e ->
                touchesVues++
                when {
                    ficheActive -> toucheFiche(e)
                    carteActive -> toucheCarte(e)
                    else -> false
                }
            },
    ) {
        // Carte : l'image en mémoire, réaffichée telle quelle
        Spacer(
            Modifier.fillMaxSize().testTag("nba:image").drawBehind {
                val im = image ?: return@drawBehind
                val e = k / im.echelle
                drawImage(
                    im.image,
                    dstOffset = IntOffset((im.cadre.left * k).roundToInt(), (im.cadre.top * k).roundToInt()),
                    dstSize = IntSize((im.image.width * e).roundToInt(), (im.image.height * e).roundToInt()),
                    alpha = apparition,
                    filterQuality = FilterQuality.Low,
                )
            },
        )
        carte?.let { c ->
            CouchePastilles(c, bilans, logos, choisieAffichee, k) { code ->
                choisie = code
                runCatching { focusCarte.requestFocus() }
                ouvrirFiche(code)
            }
        }
        EnTeteNba(palette, maintenant, k)
        TuileTop3(bilans, palette, logos, k, hauteur)
        EtatBilans(classement, maintenant, k, hauteur)
        DockNba(ctx, k, hauteur, entrer = entrerCarte)
        // « Mes apps » n'a pas de rangée ici : son état de défilement doit tout de même être attaché à une rangée
        LazyRow(state = ctx.rangeeApps, modifier = Modifier.size(0.dp)) {}
        // Focus de la carte : invisible, sur toute la carte ; atteint seulement par ▲ depuis le dock
        // (intercepté par ses tuiles) ou un clic sur une pastille
        Box(
            Modifier
                .offset { IntOffset((100f * k).roundToInt(), (180f * k).roundToInt()) }
                .size(1600.mq, 720.mq)
                .testTag("nba:carte")
                .focusRequester(focusCarte)
                .onFocusChanged {
                    carteActive = it.isFocused
                    // Focus parti ailleurs que sur la fiche (Maison, clic sur le dock…) : plus de sélection
                    if (!it.isFocused && fiche == null) choisie = null
                }
                .focusable(),
        )
        val code = fiche
        if (code != null) {
            FicheNba(
                code = code,
                bilan = bilans?.get(code),
                source = source,
                logos = logos,
                k = k,
                onglet = onglet,
                focus = focusFiche,
                surFocus = { ficheActive = it },
                fermer = fermerFiche,
            )
        }
    }
}

/** Fond de l'ambiance : dégradé radial elliptique (comme en CSS : rayons en fraction de l'écran). */
private fun Modifier.fondNba(f: FondNba): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val c = Offset(w * f.cx, h * f.cy)
    val rx = w * f.rx
    val ry = h * f.ry
    val brosse = Brush.radialGradient(*f.arrets.toTypedArray(), center = c, radius = rx)
    onDrawBehind {
        drawRect(f.arrets.last().second)
        withTransform({ scale(1f, ry / rx, pivot = c) }) {
            val e = rx / ry
            drawRect(brosse, topLeft = Offset(0f, c.y - c.y * e), size = Size(w, h * e))
        }
    }
}

/** En haut : barre bicolore, « CLASSEMENT NBA », la saison ; à droite l'heure et la date. */
@Composable
private fun EnTeteNba(palette: PaletteNba, maintenant: Long, k: Float) {
    Box(Modifier.fillMaxSize()) {
        Spacer(
            Modifier
                .offset { IntOffset((64f * k).roundToInt(), (52f * k).roundToInt()) }
                .size(10.mq, 64.mq)
                .drawBehind {
                    clipPath(formeArrondie(0f, 0f, size.width, size.height, 3f * k)) {
                        drawRect(palette.barre.first, size = Size(size.width, size.height / 2f))
                        drawRect(palette.barre.second, topLeft = Offset(0f, size.height / 2f), size = Size(size.width, size.height / 2f))
                    }
                },
        )
        TexteNba("CLASSEMENT NBA", 40f, Color.White, Modifier.ligneDeBase(92f, 87f, k), titre = true)
        TexteNba(
            ("Saison " + TextesNba.saison(maintenant) + " · " + palette.sousTitre).uppercase(java.util.Locale.FRENCH),
            18f, Color.White.copy(alpha = 0.6f), Modifier.ligneDeBase(92f, 114f, k), espace = 3.96f,
        )
        TexteNba(
            TextesNba.horloge(maintenant), 60f, Color.White, Modifier.ligneDeBase(1856f, 94f, k, ancre = 1f),
            titre = true, poids = FontWeight.SemiBold,
        )
        TexteNba(
            TextesNba.dateEnTete(maintenant), 18f, Color.White.copy(alpha = 0.6f),
            Modifier.ligneDeBase(1856f, 123f, k, ancre = 1f), espace = 2.88f,
        )
    }
}

/** Verre sombre des tuiles du bas (top 3, dock, fiche) : ombre, fond et liseré. */
internal fun Modifier.verreNba(k: Float, rayon: Float, opacite: Float = 0.84f): Modifier = drawBehind {
    OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 60f * k, 20f * k, Color.Black.copy(alpha = 0.5f))
    drawRoundRect(Color(0xFF090C18).copy(alpha = opacite), cornerRadius = CornerRadius(rayon * k))
    val e = 1.5f * k
    drawRoundRect(
        Color.White.copy(alpha = 0.10f), topLeft = Offset(e / 2f, e / 2f), size = Size(size.width - e, size.height - e),
        cornerRadius = CornerRadius(rayon * k - e / 2f), style = Stroke(e),
    )
}

/** « TOP 3 DES CONFÉRENCES » : Ouest | Est, trois lignes (rang, logo, code, bilan). */
@Composable
private fun TuileTop3(bilans: Map<String, BilanNba>?, palette: PaletteNba, logos: Map<String, ImageBitmap>, k: Float, hauteur: Float) {
    val haut = hauteur - 30f - 229f
    Box(
        Modifier
            .offset { IntOffset((48f * k).roundToInt(), (haut * k).roundToInt()) }
            .size(452.mq, 229.mq)
            .verreNba(k, 26f)
            .testTag("nba:top3"),
    ) {
        TexteNba("TOP 3 DES CONFÉRENCES", 17f, Color.White.copy(alpha = 0.55f), Modifier.ligneDeBase(23.5f, 36.5f, k), poids = FontWeight.ExtraBold, espace = 4.08f)
        val colonnes = listOf(ConferenceNba.OUEST to 23.5f, ConferenceNba.EST to 237f)
        for ((conf, x0) in colonnes) {
            Spacer(
                Modifier.offset { IntOffset((x0 * k).roundToInt(), (61.7f * k).roundToInt()) }.size(18.mq, 6.mq)
                    .drawBehind { drawRoundRect(palette.pastillesConference[conf] ?: Color.White, cornerRadius = CornerRadius(3f * k)) },
            )
            TexteNba(TextesNba.conference(conf), 20f, Color.White, Modifier.ligneDeBase(x0 + 26f, 73.8f, k), titre = true, espace = 1.2f)
            val tete = bilans?.values?.filter { DivisionNba.de(it.code)?.conference == conf }?.sortedBy { it.rangConference }?.take(3).orEmpty()
            for ((i, b) in tete.withIndex()) LigneTop3(i, b, x0, logos[b.code], k)
        }
        if (bilans == null) {
            TexteNba(
                "Classement en cours de chargement", 18f, Color.White.copy(alpha = 0.5f),
                Modifier.ligneDeBase(226f, 158f, k, ancre = 0.5f), poids = FontWeight.SemiBold,
            )
        }
    }
}

/** Ligne du top 3 (repère de la tuile) : filet, rang, logo (disque de 34), code, bilan. */
@Composable
private fun LigneTop3(i: Int, b: BilanNba, x0: Float, logo: ImageBitmap?, k: Float) {
    val haut = 85.5f + 42f * i
    val centre = haut + 21.5f
    Spacer(
        Modifier.offset { IntOffset((x0 * k).roundToInt(), (haut * k).roundToInt()) }.size(191.5f.mq, 42.mq).drawBehind {
            drawRect(Color.White.copy(alpha = 0.07f), size = Size(size.width, k))
            val c = Offset(44f * k, 21.5f * k)
            drawCircle(Color.White, 17f * k, c)
            drawCircle(FormePastille.anneau(b.code), 15.75f * k, c, style = Stroke(2.5f * k))
            if (logo != null) dessinerLogo(logo, c, 22f * k)
        },
    )
    TexteNba("${i + 1}", 18f, Color.White.copy(alpha = 0.5f), Modifier.ligneDeBase(x0, centre + 8.1f, k), titre = true)
    if (logo == null) {
        TexteNba(b.code, 11f, FormePastille.lettres(b.code), Modifier.ligneDeBase(x0 + 44f, centre + 4f, k, ancre = 0.5f), titre = true)
    }
    TexteNba(b.code, 19f, Color.White, Modifier.ligneDeBase(x0 + 70f, centre + 8.6f, k), titre = true, poids = FontWeight.SemiBold)
    TexteNba(TextesNba.bilan(b), 21f, Color.White, Modifier.ligneDeBase(x0 + 191.5f, centre + 9.5f, k, ancre = 1f), titre = true)
}

/** En bas à droite : « BILANS À JOUR · IL Y A 4 MIN », ou « HORS LIGNE · BILANS DE 21:12 » ; rien sans bilans. */
@Composable
private fun EtatBilans(classement: ClassementNba?, maintenant: Long, k: Float, hauteur: Float) {
    if (classement == null) return
    val (l1, l2) = if (classement.horsLigne) {
        "HORS LIGNE" to ("bilans " + TextesNba.heureBilans(classement.misAJour, maintenant))
    } else {
        "BILANS À JOUR" to TextesNba.depuis(classement.misAJour, maintenant)
    }
    val couleur = Color.White.copy(alpha = 0.45f)
    Box(Modifier.fillMaxSize().testTag("nba:etat")) {
        TexteNba(l1, 16f, couleur, Modifier.ligneDeBase(1856f, hauteur - 74f, k, ancre = 1f), poids = FontWeight.SemiBold, espace = 1.92f)
        TexteNba(
            l2.uppercase(java.util.Locale.FRENCH), 16f, couleur, Modifier.ligneDeBase(1856f, hauteur - 50f, k, ancre = 1f),
            poids = FontWeight.SemiBold, espace = 1.92f,
        )
    }
}
