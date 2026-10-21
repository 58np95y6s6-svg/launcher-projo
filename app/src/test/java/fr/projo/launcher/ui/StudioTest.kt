package fr.projo.launcher.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import fr.projo.launcher.HomeUi
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.ClientTmdb
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Designs à deux rangées (favoris, puis « Mes apps ») : tous sauf Studio, dont l'unique barre
 * des apps (favoris puis « Mes apps ») est suivie, sur l'écran 2, des entrées / raccourcis et
 * des modules. Les tests de NavigationTest propres aux deux rangées ne valent donc pas pour
 * Studio : leurs équivalents (▼ / ▲ vers l'élément visible le plus aligné de la bande voisine,
 * allers-retours, rangées longues, sans favori…) sont dans StudioTest.
 */
val Design.deuxRangees: Boolean get() = this != Design.STUDIO

fun designsDeuxRangees(): List<Array<Any>> = Design.entries.filter { it.deuxRangees }.map { arrayOf<Any>(it.name) }

/** Source factice sans suggestions TMDB (repli), avec éventuellement une app « récente ». */
class SourceStudioFactice(private val recente: String? = null) : StudioSource {
    override val synchrone = true
    override fun suggestions(apps: List<AppInfo>) = StudioResultat(emptyList(), StudioEtat.OK)
    override fun plusRecente(paquets: Collection<String>) = recente?.takeIf { it in paquets }
    override fun image(adresse: String, largeur: Int) = null
}

/**
 * Réponses TMDB factices (JSON), titres INVENTÉS pour les tests uniquement : l'app n'affiche
 * jamais que de vraies données TMDB.
 */
object TmdbFactice {
    private fun e(type: String, id: Int, titre: String, vararg genres: Int) =
        """{"media_type":"$type","id":$id,"${if (type == "movie") "title" else "name"}":"$titre","backdrop_path":"/$id.jpg","genre_ids":[${genres.joinToString(",")}]}"""

    private fun r(vararg el: String) = """{"results":[${el.joinToString(",")}]}"""

    private val services = r(
        """{"provider_id":8,"provider_name":"Netflix","display_priorities":{"FR":1}}""",
        """{"provider_id":119,"provider_name":"Amazon Prime Video","display_priorities":{"FR":2}}""",
        """{"provider_id":381,"provider_name":"Canal+","display_priorities":{"FR":3}}""",
        """{"provider_id":236,"provider_name":"France TV","display_priorities":{"FR":4}}""",
        """{"provider_id":234,"provider_name":"Arte","display_priorities":{"FR":5}}""",
    )

    /** Adresses demandées (pour vérifier les paramètres). */
    val demandes = mutableListOf<String>()

    fun lire(url: String, bearer: String?): String? {
        demandes += url
        val chemin = url.removePrefix(ClientTmdb.BASE).substringBefore('?')
        val fournisseurs = Regex("with_watch_providers=([^&]+)").find(url)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        return when {
            chemin.startsWith("watch/providers/") -> services
            chemin == "trending/all/week" -> r(e("tv", 101, "Les Rivages de verre", 10765), e("movie", 202, "Minuit à Valparaiso", 53), e("movie", 999, "Hors service", 18))
            chemin == "discover/movie" && fournisseurs == "8" -> r(e("movie", 202, "Minuit à Valparaiso", 53), e("movie", 203, "Le Dernier Phare", 18))
            chemin == "discover/tv" && fournisseurs == "8" -> r(e("tv", 101, "Les Rivages de verre", 10765), e("tv", 104, "Brigade Nord", 80))
            chemin == "discover/movie" && fournisseurs == "119" -> r(e("movie", 301, "Opération Saphir", 28))
            chemin == "discover/tv" && fournisseurs == "119" -> r(e("tv", 302, "Les Gardiens du col", 9648))
            chemin == "discover/tv" && fournisseurs == "381" -> r(e("tv", 402, "Station Polaire", 18))
            chemin == "discover/movie" && fournisseurs == "381" -> r(e("movie", 401, "Le Bal des lucioles", 35))
            else -> r()
        }
    }

    /** Image factice : dégradé coloré d'après l'adresse (PNG). */
    fun octets(url: String): ByteArray {
        val b = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val h = (url.hashCode() and 0xFFFFFF)
        val p = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 320f, 180f, (0xFF000000.toInt() or h), (0xFF101828.toInt()), android.graphics.Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, 320f, 180f, p)
        val sortie = java.io.ByteArrayOutputStream()
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, sortie)
        return sortie.toByteArray()
    }

    fun source(cle: String? = "0123456789abcdef0123456789abcdef", panne: Boolean = false): StudioSource =
        object : StudioSourceTmdb({ cle }, { u, b -> if (panne) null else lire(u, b) }, { u -> if (panne) null else octets(u) }) {
            override val synchrone = true
        }
}

/** Outils communs aux tests de Studio : bandes, écran affiché, sauts vérifiés. */
abstract class BancStudio : BancAccueil("STUDIO") {

    enum class Bande { HAUT, SUGGESTIONS, DOCK, RACCOURCIS, MODULES }

    protected fun bandeDe(etiquette: String): Bande? = when {
        etiquette.startsWith("app:projo:") -> Bande.RACCOURCIS
        etiquette.startsWith("rapide:") || etiquette.startsWith("onglet:") -> Bande.HAUT
        etiquette.startsWith("suggestion:") -> Bande.SUGGESTIONS
        etiquette.startsWith("favori:") || etiquette.startsWith("app:") || etiquette == "aide" -> Bande.DOCK
        etiquette == "biblio" || etiquette.startsWith("raccourci:") -> Bande.RACCOURCIS
        etiquette == "nba" -> Bande.MODULES
        else -> null
    }

    /** Éléments d'une bande au moins à moitié visibles horizontalement (étiquette, position). */
    protected fun elements(b: Bande): List<Pair<String, Rect>> {
        val largeur = largeurEcran()
        val m = SemanticsMatcher("bande $b") { n ->
            val t = n.config.getOrNull(SemanticsProperties.TestTag)
            t != null && bandeDe(t) == b
        }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes()
            .map { n ->
                // Position réelle, même hors de l'écran (l'écran 2 est sous le bord au départ)
                val c = n.layoutInfo.coordinates
                n.config[SemanticsProperties.TestTag] to c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
            }
            .filter { (_, r) -> r.center.x > 0f && r.center.x < largeur }
            .distinctBy { it.first + it.second.left }
            .sortedBy { it.second.left }
    }

    /** Haut de la barre des apps, en unités .u (≈ 422 sur l'écran 1, ≈ 24 sur l'écran 2). */
    protected fun hautDuDock(): Float = (bornesDe("rangee:favoris")?.top ?: Float.NaN) / pxParU

    protected fun ecran(): Int = if (hautDuDock() < 120f) 2 else 1

    protected fun verifierEcran(attendu: Int, contexte: String) {
        if (ecran() != attendu) fail("[STUDIO] $contexte : écran $attendu attendu, barre des apps à y = ${hautDuDock()} u")
    }

    /**
     * ▼ / ▲ depuis l'élément sélectionné : arrivée dans la bande [vers], sur l'élément visible
     * (avant l'appui) le plus aligné horizontalement.
     */
    protected fun sauter(touche: Key, vers: Bande, nom: String): Cible {
        val avant = focus() ?: throw AssertionError("[STUDIO] $nom : aucun focus")
        val candidats = elements(vers)
        appuyer(touche)
        val apres = focus()
        val fleche = if (touche == Key.DirectionDown) "▼" else "▲"
        if (apres == null || bandeDe(apres.etiquette) != vers) {
            throw AssertionError("[STUDIO] $nom ${decrire(avant)} $fleche : attendu la bande $vers, obtenu ${decrire(apres)}")
        }
        // Depuis un module : position de son panneau (la navigation part du panneau entier)
        val x = if (bandeDe(avant.etiquette) == Bande.MODULES) {
            elements(Bande.MODULES).firstOrNull { it.second.contains(avant.bornes.center) }?.second?.center?.x ?: avant.bornes.center.x
        } else avant.bornes.center.x
        val meilleur = candidats.minByOrNull { abs(it.second.center.x - x) }
            ?: throw AssertionError("[STUDIO] $nom : aucun élément visible dans $vers")
        // Modules : le focus est DANS une zone (« nba ») ; on compare la zone qui le contient
        val choisi = if (vers == Bande.MODULES) candidats.firstOrNull { it.second.contains(apres.bornes.center) }
        else candidats.firstOrNull { it.first == apres.etiquette }
        if (choisi == null || abs(choisi.second.center.x - x) > abs(meilleur.second.center.x - x) + 2f * pxParU) {
            fail(
                "[STUDIO] $nom ${decrire(avant)} $fleche : obtenu ${decrire(apres)}, attendu le plus aligné " +
                    "« ${meilleur.first} » (${meilleur.second.enU()}). Visibles : ${candidats.joinToString { it.first }}",
            )
        }
        return apres
    }

    protected fun attendre(etiquette: String, contexte: String) {
        val f = focus()
        if (f?.etiquette != etiquette) fail("[STUDIO] $contexte : attendu $etiquette, obtenu ${decrire(f)}")
    }

    /** Étiquettes de la barre des apps, dans l'ordre. */
    protected fun etiquettesDock(): List<String> =
        (if (ui.favorites.isEmpty()) listOf("aide") else ui.favorites.map { "favori:" + it.packageName }) +
            ui.homeApps.filter { !it.isSpecial && ui.favorites.none { f -> f.packageName == it.packageName } }
                .map { "app:" + it.packageName }

    /** Écrit un état lu par l'accueil et le signale aussitôt à Compose (horloge du test arrêtée). */
    protected fun changerEtat(ecriture: () -> Unit) {
        compose.runOnIdle {
            ecriture()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        avancer(50)
    }

    protected fun textePresent(t: String) =
        compose.onAllNodes(hasText(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun ouvrirReglages() {
        val panneau: @androidx.compose.runtime.Composable () -> Unit = {
            SettingsPanel(
                snapshot = DeviceSnapshot(
                    volume = 10, volumeMax = 15, muet = false, reseau = "Maison-5G", enLigne = true,
                    signal = 4, bluetoothActif = true, appareilsBluetooth = listOf("Casque"),
                    stockageLibre = 3_000_000_000L, stockageTotal = 8_000_000_000L,
                ),
                maj = LauncherViewModel.MajEtat(info = UpdateInfo("v2.4", "", true)),
                version = "2.3",
                hiddenCount = 3,
                isDefaultHome = true,
                actions = SettingsActions(
                    volume = {}, mute = {}, wifi = {}, bluetooth = {}, storage = {}, update = {},
                    checkUpdate = {}, hiddenApps = {}, sources = {}, homeApp = {}, language = {},
                    date = {}, sound = {}, allSettings = {}, close = {},
                ),
            )
        }
        changerEtat { pardessus.value = panneau }
        avancer(1500)
    }
}

/** Écrans 1 et 2, navigation entre les bandes, Maison, OK, Menu, retour des panneaux. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class StudioTest : BancStudio() {

    /** Écran 1 au démarrage : suggestions et barre des apps visibles, écran 2 caché sous le bord. */
    @Test
    fun ecran1AuDemarrage() {
        afficher()
        verifierEcran(1, "démarrage")
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        val dock = bornesDe("rangee:favoris")!!
        if (dock.bottom > hauteur + 0.5f) fail("[STUDIO] barre des apps coupée en bas : ${dock.enU()}")
        val grande = bornesDe("suggestion:" + ui.favorites[0].packageName)
            ?: return fail("[STUDIO] grande suggestion (1er favori) absente")
        if (grande.top < 0f || grande.bottom > dock.top) fail("[STUDIO] grande suggestion mal placée : ${grande.enU()}")
        val raccourcis = bornesDe("studio:raccourcis")!!
        if (raccourcis.top < hauteur) fail("[STUDIO] entrées / raccourcis visibles sur l'écran 1 : ${raccourcis.enU()}")
        capturer("s1_ecran1")
    }

    /** ▼ depuis la barre : écran 2 (barre en haut, raccourcis et modules visibles), puis retour. */
    @Test
    fun passageEcran1Ecran2() {
        afficher()
        sauter(Key.DirectionDown, Bande.RACCOURCIS, "1er favori")
        verifierEcran(2, "▼ depuis la barre")
        val dock = hautDuDock()
        // Verre du dock à 24 u du haut, rangée des tuiles centrée dedans (4,5 u plus bas)
        if (abs(dock - 28.5f) > 3f) fail("[STUDIO] écran 2 : barre des apps attendue à y = 28,5 u, obtenu $dock")
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        val zone = compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        if (zone.top < 0f || zone.bottom > hauteur + 0.5f) fail("[STUDIO] écran 2 : modules hors de l'écran (${zone.enU()})")
        capturer("s2_ecran2")
        // ▲ : retour sur le favori de départ, la barre reste en haut (écran 2)
        appuyer(Key.DirectionUp)
        attendre(favori(0), "raccourci puis ▲")
        verifierEcran(2, "retour dans la barre")
        // ▲ : suggestions, écran 1
        sauter(Key.DirectionUp, Bande.SUGGESTIONS, "1er favori (écran 2)")
        verifierEcran(1, "▲ vers les suggestions")
        // ▼ : retour sur le favori, ▼ ▼ : raccourcis puis modules
        appuyer(Key.DirectionDown)
        attendre(favori(0), "suggestion puis ▼")
        appuyer(Key.DirectionDown)
        sauter(Key.DirectionDown, Bande.MODULES, "raccourci")
        verifierEcran(2, "modules")
        // ▼ dans les modules : on y reste
        appuyer(Key.DirectionDown)
        if (bandeDe(focus()?.etiquette.orEmpty()) != Bande.MODULES) fail("[STUDIO] modules puis ▼ : sorti vers ${decrire(focus())}")
    }

    /** Chaque tuile visible de la barre : ▼ (raccourci le plus aligné) puis ▲ (retour sur la tuile). */
    @Test
    fun basPuisRetourDepuisChaqueTuile() {
        afficher()
        val tuiles = etiquettesDock()
        for ((i, t) in tuiles.withIndex()) {
            if (i > 0) {
                appuyer(Key.DirectionRight)
                attendre(t, "▶ dans la barre")
            }
            sauter(Key.DirectionDown, Bande.RACCOURCIS, t)
            appuyer(Key.DirectionUp)
            attendre(t, "$t ▼ ▲")
        }
        // ▶ au bout de la barre : on reste sur la dernière tuile
        appuyer(Key.DirectionRight)
        attendre(tuiles.last(), "▶ sur la dernière tuile")
    }

    /** Chaque entrée / raccourci : ▲ (tuile visible la plus alignée) puis ▼ (retour) ; ▼ vers les modules. */
    @Test
    fun hautEtBasDepuisChaqueRaccourci() {
        afficher()
        val raccourcis = elements(Bande.RACCOURCIS).map { it.first }
        if (raccourcis.size < 5) fail("[STUDIO] entrées / raccourcis attendus (HDMI + 4), obtenus $raccourcis")
        for (r in raccourcis) {
            donnerFocus(r)
            verifierEcran(2, "$r sélectionné")
            sauter(Key.DirectionUp, Bande.DOCK, r)
            appuyer(Key.DirectionDown)
            attendre(r, "$r ▲ ▼")
            sauter(Key.DirectionDown, Bande.MODULES, r)
            appuyer(Key.DirectionUp)
            attendre(r, "$r ▼ ▲")
        }
    }

    /** ◀ / ▶ ne quittent pas une bande. */
    @Test
    fun gaucheDroiteRestentDansLaBande() {
        afficher()
        appuyer(Key.DirectionLeft)
        attendre(favori(0), "1er favori puis ◀")
        donnerFocus("biblio")
        repeat(6) { appuyer(Key.DirectionRight) }
        attendre("raccourci:Bluetooth", "▶ au bout des raccourcis")
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionRight)
        attendre("rapide:Réglages", "▶ sur la dernière pastille")
    }

    /** En-tête : ▲ depuis la grande suggestion, puis ▼ retour ; ▲ en haut : on reste. */
    @Test
    fun enTeteEtSuggestions() {
        afficher()
        sauter(Key.DirectionUp, Bande.SUGGESTIONS, "1er favori")
        val s = focus()!!.etiquette
        sauter(Key.DirectionUp, Bande.HAUT, s)
        val h = focus()!!.etiquette
        appuyer(Key.DirectionUp)
        attendre(h, "▲ dans l'en-tête")
        appuyer(Key.DirectionDown)
        attendre(s, "en-tête ▲ ▼")
        // Le long des suggestions : ▼ vers la tuile la plus alignée
        appuyer(Key.DirectionRight)
        sauter(Key.DirectionDown, Bande.DOCK, "2e suggestion")
    }

    /** Maison : retour à l'écran 1, sur la 1re app de la barre (même depuis les modules). */
    @Test
    fun maisonRevientEnHaut() {
        afficher()
        val notes = mutableListOf<String>()
        fun maison(depart: String) {
            donnerFocus(depart)
            notes += "[$depart] avant : écran ${ecran()}"
            changerEtat { pressionsMaison.intValue++ }
            for (pas in listOf(16L, 100L, 1000L)) {
                avancer(pas)
                notes += "+$pas ms : écran ${ecran()} focus ${decrire(focus())}"
            }
            if (focus()?.etiquette != favori(0) || ecran() != 1) fail("[STUDIO] Maison depuis $depart : ${notes.joinToString(" | ")}")
        }
        maison(app(1))
        maison("biblio")
        maison("nba")
    }

    /** OK lance l'app (barre et suggestions) ; appui long / Menu ouvre son menu. */
    @Test
    fun okEtMenu() {
        afficher()
        appuyer(Key.DirectionCenter)
        appuyer(Key.Menu)
        donnerFocus(app(1))
        appuyer(Key.Menu)
        appuyer(Key.DirectionUp)
        appuyer(Key.DirectionCenter)
        val sugg = focus()!!.etiquette.removePrefix("suggestion:")
        val attendusLances = listOf(ui.favorites[0].packageName, sugg)
        if (lances != attendusLances) fail("[STUDIO] OK : lancés $lances, attendu $attendusLances")
        val attendusMenus = listOf(ui.favorites[0].packageName, ui.homeApps[1].packageName)
        if (menus != attendusMenus) fail("[STUDIO] Menu : $menus, attendu $attendusMenus")
    }

    /** Fermeture des panneaux : Réglages (pastille ou raccourci utilisé) et « Toutes les apps ». */
    @Test
    fun retourDuFocusApresLesPanneaux() {
        afficher()
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        attendre("rapide:Réglages", "focusReglages")
        verifierEcran(1, "pastille Réglages")
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(800)
        attendre("biblio", "focusBiblio")
        verifierEcran(2, "« Toutes les apps »")
        sauter(Key.DirectionUp, Bande.DOCK, "« Toutes les apps »")
        // Réglages ouvert depuis le raccourci : le focus y revient
        donnerFocus("raccourci:Réglages")
        appuyer(Key.DirectionCenter)
        donnerFocus(favori(2))
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        attendre("raccourci:Réglages", "focusReglages après le raccourci")
    }

    /** Sortie des modules : ◀ et Retour reviennent à la 1re app de la barre. */
    @Test
    fun sortieDesModules() {
        afficher()
        allerAuFavori(3)
        donnerFocus("nba")
        appuyer(Key.DirectionLeft)
        attendre(favori(0), "module puis ◀")
        donnerFocus("nba")
        appuyer(Key.Back)
        attendre(favori(0), "module puis Retour")
    }

    /** Réglages ouvert par-dessus l'écran 1. */
    @Test
    fun captureReglages() {
        afficher()
        ouvrirReglages()
        capturer("s5_reglages")
    }
}

/** Modules : un seul (grand panneau pleine largeur), deux (côte à côte), taille GRAND / MOYEN. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class StudioModulesTest : BancStudio() {

    /** Panneaux de verre des modules (un par module). */
    private fun zones(): List<Rect> =
        compose.onAllNodes(hasTestTag("studio:panneau"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }

    @Test
    fun unModule() {
        hoteModules = ModulesFactices.hote("meteo")
        afficher()
        donnerFocus("nba")
        val z = zones()
        if (z.size != 1) fail("[STUDIO] 1 module : 1 panneau attendu, ${z.size} obtenus")
        // Pleine largeur (880 u) et taille GRAND (Météo la propose) : plus de 240 u de haut
        if (z[0].width / pxParU < 860f || z[0].height / pxParU < 240f) fail("[STUDIO] 1 module : panneau ${z[0].enU()}, attendu ~880 × 250+")
        capturer("s3_un_module")
    }

    @Test
    fun deuxModules() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        donnerFocus("biblio")
        val z = zones()
        if (z.size != 2) fail("[STUDIO] 2 modules : 2 panneaux attendus, ${z.size} obtenus")
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        for (r in z) if (r.bottom > hauteur + 0.5f || r.height / pxParU < 240f) fail("[STUDIO] panneau ${r.enU()} (GRAND attendu, dans l'écran)")
        // ▼ depuis « Toutes les apps » (à gauche des raccourcis) puis ◀ ▶ entre les panneaux
        sauter(Key.DirectionDown, Bande.MODULES, "« Toutes les apps »")
        capturer("s4_deux_modules")
        donnerFocus("raccourci:Bluetooth")
        sauter(Key.DirectionDown, Bande.MODULES, "Bluetooth")
        val droite = focus()!!.bornes.center.x
        appuyer(Key.DirectionLeft)
        val gauche = focus()
        if (gauche == null || gauche.bornes.center.x >= droite || bandeDe(gauche.etiquette) != Bande.MODULES) {
            fail("[STUDIO] 2e panneau puis ◀ : attendu le 1er panneau, obtenu ${decrire(gauche)}")
        }
        sauter(Key.DirectionUp, Bande.RACCOURCIS, "1er panneau")
    }

    /** Modules sans taille GRAND (Horloges, Bourse) : panneaux MOYEN, moins hauts. */
    @Test
    fun deuxModulesMoyens() {
        hoteModules = ModulesFactices.hote("horloges", "bourse")
        afficher()
        donnerFocus("nba")
        for (r in zones()) {
            val h = r.height / pxParU
            if (h > 160f || h < 120f) fail("[STUDIO] modules MOYEN : panneau de $h u de haut, attendu ~150")
        }
        capturer("s4b_deux_modules_moyens")
    }
}

/** Suggestions : TMDB (factice), onglets, sans clé, panne réseau, repli sur les apps à la une. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class StudioSuggestionsTest : BancStudio() {

    private fun suggestions(): List<String> = elements(Bande.SUGGESTIONS).map { it.first }

    @Test
    fun suggestionsTmdb() {
        sourceStudio = TmdbFactice.source()
        afficher()
        avancer(500)
        val s = suggestions()
        // Tendances de la semaine d'abord (disponibles sur Netflix, installé)
        if (s.take(2) != listOf("suggestion:tv:101", "suggestion:movie:202")) fail("[STUDIO] TMDB : ordre $s")
        if ("suggestion:movie:999" in s) fail("[STUDIO] TMDB : titre tendance hors des services installés affiché")
        if (!textePresent("Série · Science-fiction · Netflix")) fail("[STUDIO] sous-titre « Série · Science-fiction · Netflix » absent")
        if (!textePresent("Données : TMDB")) fail("[STUDIO] attribution TMDB absente")
        if (textePresent("Activez les suggestions dans Réglages")) fail("[STUDIO] message « Activez… » affiché avec une clé")
        // Paramètres TMDB : région FR, français, services installés
        val d = TmdbFactice.demandes.firstOrNull { "discover/movie" in it } ?: ""
        for (p in listOf("watch_region=FR", "language=fr-FR", "with_watch_providers=8", "api_key=")) {
            if (TmdbFactice.demandes.none { "discover" in it && p in it }) fail("[STUDIO] paramètre $p absent : $d")
        }
        capturer("s0_ecran1_suggestions_tmdb")
        // OK sur la grande affiche : ouvre le service (Netflix)
        appuyer(Key.DirectionUp)
        attendre("suggestion:tv:101", "▲ depuis la barre")
        appuyer(Key.DirectionCenter)
        if (lances != listOf(ui.favorites[0].packageName)) fail("[STUDIO] OK sur l'affiche : lancés $lances")
    }

    @Test
    fun ongletsFiltrent() {
        sourceStudio = TmdbFactice.source()
        afficher()
        avancer(500)
        appuyer(Key.DirectionUp)
        sauter(Key.DirectionUp, Bande.HAUT, "grande suggestion")
        if (focus()?.etiquette?.startsWith("onglet:") != true) fail("[STUDIO] ▲ ▲ : attendu un onglet, obtenu ${decrire(focus())}")
        donnerFocus("onglet:films")
        val films = suggestions()
        if (films.isEmpty() || films.any { !it.startsWith("suggestion:movie:") }) fail("[STUDIO] onglet Films : $films")
        donnerFocus("onglet:series")
        val series = suggestions()
        if (series.isEmpty() || series.any { !it.startsWith("suggestion:tv:") }) fail("[STUDIO] onglet Séries : $series")
        capturer("s0b_onglet_series")
        appuyer(Key.DirectionDown)
        if (focus()?.etiquette?.startsWith("suggestion:tv:") != true) fail("[STUDIO] onglet puis ▼ : ${decrire(focus())}")
        donnerFocus("onglet:explorer")
        val tout = suggestions()
        if (tout.none { it.startsWith("suggestion:movie:") } || tout.none { it.startsWith("suggestion:tv:") }) fail("[STUDIO] Explorer : $tout")
    }

    @Test
    fun sansCle() {
        sourceStudio = TmdbFactice.source(cle = null)
        afficher()
        avancer(500)
        if (!textePresent("Activez les suggestions dans Réglages")) fail("[STUDIO] sans clé : message « Activez les suggestions dans Réglages » absent")
        if (suggestions().firstOrNull() != "suggestion:" + ui.favorites[0].packageName) fail("[STUDIO] sans clé : repli attendu, ${suggestions()}")
        if (textePresent("Explorer")) fail("[STUDIO] sans clé : onglets affichés")
    }

    @Test
    fun panneReseau() {
        sourceStudio = TmdbFactice.source(panne = true)
        afficher()
        avancer(500)
        if (suggestions().firstOrNull() != "suggestion:" + ui.favorites[0].packageName) fail("[STUDIO] panne : repli attendu, ${suggestions()}")
        if (textePresent("Activez les suggestions dans Réglages")) fail("[STUDIO] panne : le message de clé ne doit pas s'afficher")
    }

    @Test
    fun repliSansHistorique() {
        sourceStudio = SourceStudioFactice()
        afficher()
        val s = elements(Bande.SUGGESTIONS)
        val premiere = s.firstOrNull() ?: return fail("[STUDIO] aucune suggestion")
        if (premiere.first != "suggestion:" + ui.favorites[0].packageName) fail("[STUDIO] grande carte : ${premiere.first}, attendu le 1er favori")
        if (s.size > 1 && premiere.second.width < 1.5f * s[1].second.width) fail("[STUDIO] la 1re carte doit être la grande")
        if (!textePresent("Films et séries")) fail("[STUDIO] phrase de rubrique « Films et séries » absente")
        if (textePresent("Explorer")) fail("[STUDIO] onglets affichés sans vraies suggestions")
        if (!textePresent("Pour vous")) fail("[STUDIO] titre « Pour vous » absent")
    }

    @Test
    fun repliAppLaPlusRecente() {
        sourceStudio = SourceStudioFactice(recente = ui.favorites[2].packageName)
        afficher()
        avancer(500)
        val premiere = suggestions().firstOrNull()
        if (premiere != "suggestion:" + ui.favorites[2].packageName) fail("[STUDIO] app récente : grande carte $premiere")
        if (!textePresent("Reprendre sur ${ui.favorites[2].label}")) fail("[STUDIO] « Reprendre sur ${ui.favorites[2].label} » absent")
    }

    @Test
    fun formatsDeCle() {
        if (!CleTmdb.valide("0123456789abcdef0123456789ABCDEF")) fail("clé v3 refusée")
        if (!CleTmdb.valide("eyJ" + "a".repeat(150) + ".b.c")) fail("jeton v4 refusé")
        if (CleTmdb.valide("1234") || CleTmdb.valide("eyJcourt")) fail("clé invalide acceptée")
        // Jeton v4 : en-tête Authorization, pas de paramètre api_key
        var entete: String? = null
        var adresse = ""
        ClientTmdb("eyJ" + "x".repeat(150)) { u, b -> adresse = u; entete = b; null }.listeServices()
        if (entete == null || "api_key" in adresse) fail("jeton v4 : en-tête $entete, adresse $adresse")
    }

    /** Client partagé (data/Tmdb.kt) : dernières sorties d'un service (Netflix : 8). */
    @Test
    fun dernieresSorties() {
        TmdbFactice.demandes.clear()
        val r = ClientTmdb("0123456789abcdef0123456789abcdef", TmdbFactice::lire).dernieresSorties(listOf(8), max = 3)
            ?: return fail("dernières sorties : null")
        if (r.isEmpty() || r.size > 3) fail("dernières sorties : $r")
        if (r.any { !it.image.startsWith(ClientTmdb.IMAGES) }) fail("dernières sorties : images ${r.map { it.image }}")
        val d = TmdbFactice.demandes.joinToString(" ")
        for (p in listOf("primary_release_date.desc", "first_air_date.desc", "with_watch_providers=8", "watch_region=FR")) {
            if (p !in d) fail("dernières sorties : paramètre $p absent ($d)")
        }
    }
}

/** Variantes : sans favori, Fire TV (aucune entrée), 30 apps dans la barre. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class StudioVariantesTest(private val variante: String) : BancStudio() {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cas(): List<Array<Any>> = listOf("sans favori", "fire tv", "30 apps", "1 app").map { arrayOf<Any>(it) }
    }

    override fun preparer(u: HomeUi): HomeUi = when (variante) {
        "sans favori" -> u.copy(favorites = emptyList(), prefs = u.prefs.copy(favorites = emptyList()))
        "fire tv" -> u.copy(
            homeApps = u.homeApps.filter { !it.isSpecial }, allApps = u.allApps.filter { !it.isSpecial },
        )
        "30 apps" -> u.copy(
            homeApps = u.homeApps + (1..24).map { AppInfo("fr.test.appli$it", "Appli $it", isTvApp = true) },
        )
        else -> u.copy(homeApps = u.homeApps.take(2))
    }

    @Test
    fun navigation() {
        afficher()
        val tuiles = etiquettesDock()
        val voulus = setOf(0, 1, tuiles.size / 2, tuiles.lastIndex)
        for ((i, t) in tuiles.withIndex()) {
            if (i > 0) {
                appuyer(Key.DirectionRight)
                attendre(t, "[$variante] ▶ dans la barre")
            }
            if (i !in voulus) continue
            sauter(Key.DirectionDown, Bande.RACCOURCIS, "[$variante] $t")
            appuyer(Key.DirectionUp)
            attendre(t, "[$variante] $t ▼ ▲")
        }
        if (variante == "fire tv") {
            if (compose.onAllNodes(hasText("Entrées"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                fail("[STUDIO] Fire TV : la section « Entrées » doit disparaître")
            }
            val biblio = bornesDe("biblio")!!
            if (biblio.left / pxParU > 60f) fail("[STUDIO] Fire TV : raccourcis attendus à gauche, « Toutes les apps » en ${biblio.enU()}")
            donnerFocus("biblio")
            capturer("s6_firetv_ecran2")
        }
        if (variante == "sans favori") capturer("s7_sans_favori")
    }
}
