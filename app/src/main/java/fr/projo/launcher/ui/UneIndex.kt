package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------ navigation déterministe

/**
 * Navigation à la télécommande de « Une ». Étages, de haut en bas : raccourcis → NBA → favoris
 * → « Mes apps ». Toutes les flèches verticales sont traitées ici (jamais la recherche de focus
 * standard, qui partait en biais) :
 *  - ▼ depuis un favori : l'élément de « Mes apps » ENTIÈREMENT visible le plus aligné ;
 *  - ▲ depuis « Mes apps » : le favori le plus proche ; ▲ depuis un favori : la pilule la plus proche ;
 *  - ▼ puis ▲ (ou ▲ puis ▼) sans bouger entre-temps : retour sur l'élément de départ ;
 *  - ▼ (ou ▶ sur la dernière) depuis une pilule : le bloc NBA ; ▲ depuis le NBA : la pilule la plus proche ;
 *    ▼ (ou ◀, ou Retour) depuis le NBA : retour aux favoris ;
 *  - ◀ ▶ : élément voisin dans la rangée, sans boucle (on bute aux deux bouts).
 * La cible de ▲ / ▼ est marquée d'un petit trait blanc sous l'entrée de l'autre rangée.
 */
internal class UneNav {
    /** Demandeurs de focus des éléments composés, par clé (fav:…, app:…, bibliotheque, aide, pil:…, nba:…). */
    val cibles = HashMap<String, FocusRequester>()
    /** Centre horizontal (px, repère de la fenêtre) des pilules et des onglets NBA. */
    val centres = HashMap<String, Float>()
    /** Bord gauche des deux rangées et largeur de la fenêtre (px). */
    var origineFavoris = 0f
    var origineApps = 0f
    var largeurFenetre = Float.MAX_VALUE
    /** Clés des pilules, dans l'ordre. */
    var pilules: List<String> = emptyList()
    /** Onglet NBA affiché (0 : cette nuit, 1 : résultats). */
    var ongletNba = 0

    /** Élément sélectionné, et dernier favori sélectionné (retour depuis le NBA). */
    var focusCle by mutableStateOf<String?>(null)
    var memFavori by mutableStateOf<String?>(null)

    /** Dernier saut ▲ / ▼ : vers « Mes apps » ?, élément d'arrivée, élément de départ. */
    private var dernierSaut by mutableStateOf<Triple<Boolean, String, String>?>(null)

    fun aller(cle: String?): Boolean {
        val fr = cle?.let { cibles[it] } ?: return false
        return runCatching { fr.requestFocus() }.isSuccess
    }

    /** Centre horizontal (repère de la fenêtre) d'un élément visible d'une rangée. */
    fun centre(etat: LazyListState, origine: Float, cle: String): Float? {
        val infos = etat.layoutInfo
        val item = infos.visibleItemsInfo.firstOrNull { it.key == cle } ?: return null
        return origine + item.offset - infos.viewportStartOffset + item.size / 2f
    }

    /**
     * Cible de ▼ ([versApps]) ou de ▲ depuis l'élément [cle] (même règle que NavRangees) :
     *  - retour sur l'élément de départ si l'on vient d'arriver par le saut inverse ;
     *  - sinon l'élément ENTIÈREMENT visible le plus aligné, à défaut un élément au moins à
     *    moitié visible.
     * Sert au saut et au repère blanc : le repère montre exactement où ira la flèche.
     */
    fun cibleSaut(ctx: HomeContext, versApps: Boolean, cle: String): String? {
        val depuis = if (versApps) ctx.rangeeFavoris else ctx.rangeeApps
        val origineDepuis = if (versApps) origineFavoris else origineApps
        val vers = if (versApps) ctx.rangeeApps else ctx.rangeeFavoris
        val origine = if (versApps) origineApps else origineFavoris
        val x = centre(depuis, origineDepuis, cle) ?: return null
        val infos = vers.layoutInfo
        fun gauche(i: LazyListItemInfo) = origine + i.offset - infos.viewportStartOffset
        fun milieu(i: LazyListItemInfo) = gauche(i) + i.size / 2f
        val ecranG = maxOf(origine, 0f)
        val ecranD = minOf(origine + infos.viewportSize.width, largeurFenetre)
        val candidats = infos.visibleItemsInfo.filter { (it.key as? String)?.let { k -> cibles.containsKey(k) } == true }
        val entiers = candidats.filter { gauche(it) >= ecranG - 1f && gauche(it) + it.size <= ecranD + 1f }
        val enPartie = candidats.filter { milieu(it) > ecranG && milieu(it) < ecranD }
        val s = dernierSaut
        if (s != null && s.first != versApps && s.second == cle) {
            enPartie.firstOrNull { it.key == s.third }?.let { return it.key as String }
        }
        return entiers.ifEmpty { enPartie }.minByOrNull { abs(milieu(it) - x) }?.key as? String
    }

    /** Saut ▼ / ▲ vers l'autre rangée ; false : impossible (la touche n'est pas consommée). */
    private fun sauter(ctx: HomeContext, versApps: Boolean, cle: String): Boolean {
        val cible = cibleSaut(ctx, versApps, cle) ?: return false
        if (!aller(cible)) return false
        dernierSaut = Triple(versApps, cible, cle)
        return true
    }

    fun pilulePlusProche(x: Float): String? =
        pilules.minByOrNull { abs((centres[it] ?: Float.MAX_VALUE / 4) - x) }

    // --- touches, par étage

    fun toucheFavoris(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) return retour(e, ctx)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle ?: return false
        return when (e.key) {
            Key.DirectionDown -> sauter(ctx, versApps = true, cle)
            Key.DirectionUp -> {
                val x = centre(ctx.rangeeFavoris, origineFavoris, cle) ?: return false
                aller(pilulePlusProche(x)) || true
            }
            Key.DirectionRight -> cle == dernierFavori(ctx)
            Key.DirectionLeft -> cle == premierFavori(ctx)
            else -> false
        }
    }

    fun toucheApps(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) return retour(e, ctx)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle ?: return false
        return when (e.key) {
            Key.DirectionUp -> sauter(ctx, versApps = false, cle)
            // Rien en dessous : on reste
            Key.DirectionDown -> true
            Key.DirectionRight -> cle == CLE_BIBLIO
            Key.DirectionLeft -> cle == ctx.ui.homeApps.firstOrNull()?.let { "app:" + it.packageName }
            else -> false
        }
    }

    fun toucheRaccourcis(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle ?: return false
        return when (e.key) {
            Key.DirectionUp -> true
            Key.DirectionDown -> aller("nba:$ongletNba") || allerFavori(ctx)
            Key.DirectionLeft -> cle == pilules.firstOrNull()
            // ▶ au bout des pilules : le bloc NBA, juste en dessous à droite
            Key.DirectionRight -> if (cle == pilules.lastOrNull()) aller("nba:$ongletNba") || true else false
            else -> false
        }
    }

    fun toucheNba(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) {
            if (e.type == KeyEventType.KeyUp) ctx.sortieNba()
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle ?: return false
        return when (e.key) {
            Key.DirectionUp -> {
                centres[cle]?.let { aller(pilulePlusProche(it)) }
                true
            }
            Key.DirectionDown -> allerFavori(ctx)
            Key.DirectionLeft -> {
                if (cle == "nba:1") aller("nba:0") else ctx.sortieNba()
                true
            }
            Key.DirectionRight -> {
                if (cle == "nba:0") aller("nba:1")
                true
            }
            else -> false
        }
    }

    /** Dernier favori sélectionné s'il est encore à l'écran, sinon le premier (sortie du NBA). */
    private fun allerFavori(ctx: HomeContext): Boolean {
        if (!aller(memFavori)) ctx.sortieNba()
        return true
    }

    /** Retour depuis l'index : premier favori (sauf s'il est déjà sélectionné). */
    private fun retour(e: KeyEvent, ctx: HomeContext): Boolean {
        if (focusCle == premierFavori(ctx) || focusCle == CLE_AIDE) return false
        if (e.type == KeyEventType.KeyUp) ctx.sortieNba()
        return true
    }

    private fun premierFavori(ctx: HomeContext): String? = ctx.ui.favorites.firstOrNull()?.let { "fav:" + it.packageName }
    private fun dernierFavori(ctx: HomeContext): String =
        ctx.ui.favorites.lastOrNull()?.let { "fav:" + it.packageName } ?: CLE_AIDE

    /** Demandeur de focus d'un élément, enregistré tant qu'il est composé. */
    @Composable
    fun cible(cle: String): Modifier = Modifier.focusRequester(demandeur(cle))

    /** Le même, sous forme de FocusRequester (point d'entrée de la zone de modules). */
    @Composable
    fun demandeur(cle: String): FocusRequester {
        val fr = remember { FocusRequester() }
        DisposableEffect(this, cle) {
            cibles[cle] = fr
            onDispose { if (cibles[cle] === fr) cibles.remove(cle) }
        }
        return fr
    }

    /** Zone de modules (autre que le journal NBA) : ▲ et ▼ que les modules n'ont pas consommés. */
    fun toucheZone(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when (e.key) {
            Key.DirectionUp -> {
                centres[CLE_ZONE]?.let { aller(pilulePlusProche(it)) }
                true
            }
            Key.DirectionDown -> allerFavori(ctx)
            else -> false
        }
    }

    companion object {
        const val CLE_BIBLIO = "bibliotheque"
        const val CLE_AIDE = "aide"
        const val CLE_ZONE = "zone"
        fun estFavori(cle: String?) = cle != null && (cle.startsWith("fav:") || cle == CLE_AIDE)
        fun estApp(cle: String?) = cle != null && (cle.startsWith("app:") || cle == CLE_BIBLIO)
    }
}

/**
 * Répartition des entrées : espacement égal sur toute la largeur (comme la maquette) quand
 * tout tient, avec un écart minimal de 8.u (et maximal, pour qu'une rangée de 2 ou 3 apps ne
 * s'éparpille pas) ; au-delà, la rangée défile avec l'écart minimal.
 */
private class UneRepartition(private val minimum: Dp, private val maximum: Dp) : Arrangement.Horizontal {
    override val spacing: Dp get() = minimum

    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
        val n = sizes.size
        val somme = sizes.sum()
        val ecartMin = minimum.toPx()
        val ecart = if (n > 1) ((totalSize - somme).toFloat() / (n - 1)).coerceIn(ecartMin, maximum.toPx()) else 0f
        var x = 0f
        for (i in 0 until n) {
            outPositions[i] = x.roundToInt()
            x += sizes[i] + ecart
        }
    }
}

/**
 * Défilement des rangées : l'entrée sélectionnée reste entièrement entre les marges de 48.u
 * (jamais de nom coupé au bord, place pour l'anneau et l'agrandissement) ; rien ne bouge si
 * elle y est déjà.
 */
@OptIn(ExperimentalFoundationApi::class)
private class UneDefilement(private val marge: Float) : BringIntoViewSpec {
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

/** Rangée défilante de « Une » : BringIntoViewSpec à marges (voir UneDefilement). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UneAvecMarges(contenu: @Composable () -> Unit) {
    val marge = with(LocalDensity.current) { 48.u.toPx() }
    val spec = remember(marge) { UneDefilement(marge) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { contenu() }
}

// ------------------------------------------------------------------ bande d'index

/** Bande sombre teintée (aplat 16 %, lueur de l'aplat en haut), libellés et deux rangées. */
@Composable
internal fun UneIndex(ctx: HomeContext, nav: UneNav, aplat: State<Color>, montrer: (UneSujet) -> Unit) {
    // Cible de ▲ / ▼ depuis l'élément sélectionné : marquée sur l'autre rangée
    val repereFavori by remember {
        derivedStateOf {
            val f = nav.focusCle
            when {
                UneNav.estApp(f) -> nav.cibleSaut(ctx, versApps = false, f!!)
                UneNav.estFavori(f) -> null
                else -> nav.memFavori
            }
        }
    }
    val repereApp by remember {
        derivedStateOf {
            val f = nav.focusCle
            if (UneNav.estFavori(f)) nav.cibleSaut(ctx, versApps = true, f!!) else null
        }
    }
    // Un seul étage « vivant » : l'autre rangée est à 70 %
    val alphaFavoris = animateFloatAsState(
        if (nav.focusCle == null || UneNav.estFavori(nav.focusCle)) 1f else 0.7f, tween(200), label = "alphaFavoris",
    )
    val alphaApps = animateFloatAsState(
        if (nav.focusCle == null || UneNav.estApp(nav.focusCle)) 1f else 0.7f, tween(200), label = "alphaApps",
    )

    Box(Modifier.fillMaxSize()) {
        val hauteurLueur = 55.u
        Spacer(
            Modifier
                .padding(top = 380.u)
                .fillMaxSize()
                .drawWithCache {
                    val h = hauteurLueur.toPx()
                    onDrawBehind {
                        val a = aplat.value
                        // Bande = aplat 16 % + #09090C 84 % (mélange sRGB, comme la maquette)
                        drawRect(
                            Color(
                                UneBandeBase.red * 0.84f + a.red * 0.16f,
                                UneBandeBase.green * 0.84f + a.green * 0.16f,
                                UneBandeBase.blue * 0.84f + a.blue * 0.16f,
                            )
                        )
                        drawRect(
                            Brush.verticalGradient(listOf(a.copy(alpha = 0.42f), a.copy(alpha = 0f)), endY = h),
                            size = Size(size.width, h),
                        )
                    }
                }
        )
        UneLibelle("Favoris", 399.u)
        UneRangeeFavoris(ctx, nav, repereFavori, alphaFavoris, montrer)
        UneLibelle("Mes apps", 462.u)
        UneRangeeApps(ctx, nav, repereApp, alphaApps, montrer)
    }
}

@Composable
private fun UneLibelle(texte: String, base: Dp) {
    Text(
        texte.uppercase(Locale.FRENCH), color = Color.White.copy(alpha = 0.62f), fontSize = 10.us,
        fontWeight = FontWeight.Bold, fontFamily = UnePolice, letterSpacing = 0.16.em, maxLines = 1,
        modifier = Modifier.uneLigne(56.u, base),
    )
}

@Composable
private fun UneRangeeFavoris(
    ctx: HomeContext,
    nav: UneNav,
    repere: String?,
    alpha: State<Float>,
    montrer: (UneSujet) -> Unit,
) {
    val ui = ctx.ui
    val ecartMin = 8.u
    val ecartMax = 72.u
    val repartition = remember(ecartMin, ecartMax) { UneRepartition(ecartMin, ecartMax) }
    UneAvecMarges {
        LazyRow(
            state = ctx.rangeeFavoris,
            modifier = Modifier
                .padding(top = 400.u)
                .fillMaxWidth()
                .height(50.u)
                .testTag("rangee:favoris")
                .graphicsLayer { this.alpha = alpha.value }
                .onGloballyPositioned {
                nav.origineFavoris = it.positionInRoot().x
                nav.largeurFenetre = it.findRootCoordinates().size.width.toFloat()
            }
                .onPreviewKeyEvent { nav.toucheFavoris(it, ctx) },
            contentPadding = PaddingValues(horizontal = 48.u),
            horizontalArrangement = repartition,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.loaded && ui.favorites.isEmpty()) {
                item(key = UneNav.CLE_AIDE) {
                    UneBouton(
                        onClick = {},
                        onFocus = {
                            nav.focusCle = UneNav.CLE_AIDE
                            nav.memFavori = UneNav.CLE_AIDE
                            ctx.sansSelection()
                            montrer(
                                UneSujet(
                                    cle = "aide", titre = "Favoris", suivant = null, rubrique = "Favoris",
                                    position = 0, total = 0, detail = "Maintiens OK sur une app pour l'ajouter",
                                    action = null, couleur = UneGraphite, icone = ProjoIcons.Accueil, ordre = 2000,
                                )
                            )
                        },
                        hauteur = 38.u,
                        rayon = 8.u,
                        padding = PaddingValues(horizontal = 12.u),
                        fond = Color.White.copy(alpha = 0.07f),
                        bordure = Color.White.copy(alpha = 0.14f),
                        repere = repere == UneNav.CLE_AIDE,
                        modifier = Modifier
                            .testTag("aide")
                            .focusRequester(ctx.premierFocus)
                            .then(nav.cible(UneNav.CLE_AIDE)),
                    ) {
                        Text(
                            "Aucun favori · maintiens OK sur une app de « Mes apps » pour l'ajouter",
                            fontSize = 14.us, fontWeight = FontWeight.SemiBold, fontFamily = UnePolice, maxLines = 1,
                        )
                    }
                }
            }
            itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
                val cle = "fav:" + app.packageName
                UneEntreeApp(
                    app = app,
                    ctx = ctx,
                    nav = nav,
                    cle = cle,
                    favori = true,
                    index = i,
                    total = ui.favorites.size,
                    suivant = ui.favorites.getOrNull(i + 1)?.label,
                    repere = repere == cle,
                    montrer = montrer,
                    modifier = Modifier
                        .testTag("favori:" + app.packageName)
                        .then(if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier)
                        .then(nav.cible(cle)),
                )
            }
        }
    }
}

@Composable
private fun UneRangeeApps(
    ctx: HomeContext,
    nav: UneNav,
    repere: String?,
    alpha: State<Float>,
    montrer: (UneSujet) -> Unit,
) {
    val ui = ctx.ui
    val ecartMin = 8.u
    val ecartMax = 72.u
    val repartition = remember(ecartMin, ecartMax) { UneRepartition(ecartMin, ecartMax) }
    val total = ui.homeApps.size + 1
    UneAvecMarges {
        LazyRow(
            state = ctx.rangeeApps,
            modifier = Modifier
                .padding(top = 463.u)
                .fillMaxWidth()
                .height(50.u)
                .testTag("rangee:apps")
                .graphicsLayer { this.alpha = alpha.value }
                .onGloballyPositioned { nav.origineApps = it.positionInRoot().x }
                .onPreviewKeyEvent { nav.toucheApps(it, ctx) },
            contentPadding = PaddingValues(horizontal = 48.u),
            horizontalArrangement = repartition,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { i, app ->
                val cle = "app:" + app.packageName
                UneEntreeApp(
                    app = app,
                    ctx = ctx,
                    nav = nav,
                    cle = cle,
                    favori = false,
                    index = i,
                    total = total,
                    suivant = ui.homeApps.getOrNull(i + 1)?.label ?: "Toutes les apps",
                    repere = repere == cle,
                    montrer = montrer,
                    modifier = Modifier
                        .testTag("app:" + app.packageName)
                        .then(nav.cible(cle)),
                )
            }
            // Bibliothèque : une entrée, pas une app (contour en tirets, pastille du nombre d'apps)
            item(key = UneNav.CLE_BIBLIO) {
                val nombre = ui.allApps.size
                UneBouton(
                    onClick = ctx.actions.openLibrary,
                    onFocus = {
                        nav.focusCle = UneNav.CLE_BIBLIO
                        ctx.sansSelection()
                        montrer(
                            UneSujet(
                                cle = UneNav.CLE_BIBLIO, titre = "Toutes les apps", suivant = null, rubrique = "Mes apps",
                                position = total, total = total,
                                detail = if (nombre > 1) "$nombre applications" else "$nombre application",
                                action = "Ouvrir", couleur = UneIndigo, icone = ProjoIcons.Apps, ordre = 3000 + total,
                            )
                        )
                    },
                    hauteur = 38.u,
                    rayon = 8.u,
                    padding = PaddingValues(start = 6.u, end = 9.u),
                    bordure = Color.White.copy(alpha = 0.3f),
                    pointille = true,
                    encre = Color.White.copy(alpha = 0.82f),
                    focusFond = UneIndigo,
                    focusEncre = Color.White,
                    repere = repere == UneNav.CLE_BIBLIO,
                    modifier = Modifier
                        .testTag("biblio")
                        .focusRequester(ctx.focusBiblio)
                        .then(nav.cible(UneNav.CLE_BIBLIO)),
                ) { focus ->
                    Box {
                        UnePastille(null, null, ProjoIcons.Apps, UneIndigo, 25.u, 6.u, lisere = false)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.u, y = (-5).u)
                                .height(13.u)
                                .widthIn(min = 17.u)
                                .background(Color.White, RoundedCornerShape(7.u))
                                .padding(horizontal = 4.u),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$nombre", color = UneEncreSombre, fontSize = 8.us, fontWeight = FontWeight.ExtraBold,
                                fontFamily = UnePolice, maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.u))
                    UneNom("Toutes les apps", focus)
                }
            }
        }
    }
}

/** Entrée d'une app dans l'index : icône 25.u + nom ; sélectionnée : bloc à la couleur de l'app. */
@Composable
private fun UneEntreeApp(
    app: AppInfo,
    ctx: HomeContext,
    nav: UneNav,
    cle: String,
    favori: Boolean,
    index: Int,
    total: Int,
    suivant: String?,
    repere: Boolean,
    montrer: (UneSujet) -> Unit,
    modifier: Modifier,
) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    val visuel = rememberAppVisual(paquet, ctx.icons)
    val icone = rememberAppIcon(paquet, ctx.icons)
    val couleur = uneCouleurApp(app, visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT)
    var focus by remember { mutableStateOf(false) }
    val surFocus = {
        nav.focusCle = cle
        if (favori) nav.memFavori = cle
        ctx.surFocus(app, couleur)
        montrer(
            UneSujet(
                cle = cle, titre = app.label, suivant = suivant,
                rubrique = if (favori) "Favori" else "Mes apps",
                position = index + 1, total = total, detail = uneRubrique(app), action = "Ouvrir",
                couleur = couleur, app = app, ordre = (if (favori) 2000 else 3000) + index,
            )
        )
    }
    // Visuel arrivé après la prise de focus : l'affiche prend la vraie couleur
    LaunchedEffect(couleur) { if (focus) surFocus() }
    UneBouton(
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        onFocus = surFocus,
        hauteur = 38.u,
        rayon = 8.u,
        padding = PaddingValues(start = 6.u, end = 9.u),
        encre = Color.White.copy(alpha = 0.9f),
        focusFond = couleur,
        focusEncre = uneEncreSur(couleur),
        repere = repere,
        modifier = modifier
            .uneToucheMenu { ctx.actions.openMenu(app) }
            .onFocusChanged { focus = it.isFocused },
    ) { f ->
        UnePastille(app, icone, null, UneCartouche, 25.u, 6.u, lisere = !f)
        Spacer(Modifier.width(6.u))
        UneNom(app.label, f)
    }
}

/** Nom d'une entrée : la place du gras (sélection) est réservée, la rangée ne bouge pas. */
@Composable
private fun UneNom(texte: String, focus: Boolean) {
    Box(contentAlignment = Alignment.CenterStart) {
        Text(
            texte, color = Color.Transparent, fontSize = 15.us, fontWeight = FontWeight.ExtraBold,
            fontFamily = UnePolice, letterSpacing = (-0.01).em, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 230.u),
        )
        Text(
            texte, fontSize = 15.us, fontWeight = if (focus) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontFamily = UnePolice, letterSpacing = (-0.01).em, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 230.u),
        )
    }
}
