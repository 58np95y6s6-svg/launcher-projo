package fr.projo.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import fr.projo.launcher.data.AmbianceCarteNba
import fr.projo.launcher.data.AmbianceNba
import fr.projo.launcher.nba.ClassementNba
import fr.projo.launcher.nba.DivisionNba
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * Design « NBA » (données factices de la maquette, heure de Paris) : accueil dans les trois
 * ambiances, sans classement, hors ligne ; navigation du dock à la carte et de pastille en
 * pastille ; survol d'une division (caméra, panneau, fiche demandée une fois l'équipe choisie) ;
 * fiche (Résumé, Effectif, indisponible, chargement) ; suivi du classement selon l'affichage.
 * Captures nba_*.png à comparer aux maquettes.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NbaTest : BancAccueil("NBA") {

    private val paris = TimeZone.getTimeZone("Europe/Paris")
    private var fuseauAvant: TimeZone? = null

    /** 12 novembre 2026, 21:47 à Paris (heure de la maquette). */
    private val t = NbaFactice.heureMaquette(paris)

    @Before
    fun preparer() {
        fuseauAvant = TimeZone.getDefault()
        TimeZone.setDefault(paris)
        ambiance(AmbianceNba.LEADERS)
    }

    @After
    fun remettre() {
        fuseauAvant?.let { TimeZone.setDefault(it) }
        ambiance(AmbianceNba.LEADERS)
    }

    private fun ambiance(a: AmbianceNba) = AmbianceCarteNba.ecrire(RuntimeEnvironment.getApplication(), a)

    private fun source(
        classement: ClassementNba? = NbaFactice.classement(t - 4 * 60_000L),
        chargementSansFin: Boolean = false,
        fiches: Map<String, fr.projo.launcher.nba.FicheEquipe> = mapOf("OKC" to NbaFactice.ficheOkc),
    ) = SourceNbaFactice(classement, t, fiches = fiches, chargementSansFin = chargementSansFin).also { sourceNba = it }

    /**
     * Appui sur une touche, puis l'état changé par la carte ou la fiche (sans animation ni
     * changement de focus) signalé à Compose et recomposé (horloge du test avancée à la main).
     */
    private fun touche(k: Key) {
        appuyer(k)
        appliquer()
    }

    private fun appliquer() {
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        avancer(200)
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        avancer(200)
    }

    private fun texte(s: String) =
        compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun nombre(s: String) =
        compose.onAllNodes(hasText(s), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun present(etiquette: String) =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Code de la pastille sélectionnée, ou null. */
    private fun choisie(): String? {
        val m = SemanticsMatcher("pastille sélectionnée") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nba:pastille:") == true &&
                n.config.getOrNull(SemanticsProperties.Selected) == true
        }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.TestTag)?.removePrefix("nba:pastille:")
    }

    /** Tous les éléments focalisés et leurs étiquettes (diagnostic des échecs). */
    private fun etatDuFocus(): String {
        val n = compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        val vue = compose.activity.window.decorView.findFocus()
        return n.joinToString(" ; ") { it.config.getOrNull(SemanticsProperties.TestTag) ?: "(sans étiquette)" } +
            " | lancés $lances, menus $menus | vue Android focalisée : ${vue?.javaClass?.simpleName} (${vue?.isFocused})"
    }

    private fun attendreChoisie(code: String?, etape: String) {
        val c = choisie()
        if (c != code) fail("[NBA] $etape : pastille sélectionnée attendue $code, obtenue $c (focus ${decrire(focus())} ; ${etatDuFocus()})")
    }

    /** Une pastille est sélectionnée : la carte lit les touches (le focus reste sur le dock). */
    private fun attendreCarte(etape: String) {
        if (!present("nba:carte")) fail("[NBA] $etape : sélection sur la carte attendue (${etatDuFocus()})")
    }

    private fun attendreFocus(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || (f.etiquette != etiquette && etiquette !in f.chaine)) fail("[NBA] $etape : focus attendu sur $etiquette, obtenu ${decrire(f)} (${f?.chaine})")
    }

    private fun verifierTextes(vararg attendus: String) {
        for (s in attendus) if (!texte(s)) fail("[NBA] texte « $s » absent (${etatDuFocus()})")
    }

    /** Division cadrée par la caméra (« sudouest »…), « carte » : la carte entière. */
    private fun camera(): String? {
        val m = SemanticsMatcher("caméra") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nba:camera:") == true }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.TestTag)?.removePrefix("nba:camera:")
    }

    private fun attendreCamera(division: String, etape: String) {
        val c = camera()
        if (c != division) fail("[NBA] $etape : caméra attendue sur $division, obtenue sur $c (pastille ${choisie()})")
    }

    /** Touches enchaînées sans attendre (moins que la pause avant la demande de la fiche). */
    private fun rafale(touches: List<Key>) {
        for (k in touches) {
            compose.onRoot().performKeyInput { pressKey(k) }
            compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            avancer(100)
        }
    }

    // ------------------------------------------------------------------ accueil

    @Test
    fun accueilLeaders() {
        source()
        afficher()
        avancer(1000)
        verifierTextes(
            "CLASSEMENT NBA", "SAISON 2026-27 · CHAQUE DIVISION AUX COULEURS DE SON LEADER", "21:47", "JEUDI 12 NOVEMBRE",
            "TOP 3 DES CONFÉRENCES", "OUEST", "EST", "12–1", "11–2", "BILANS À JOUR", "IL Y A 4 MIN",
            "1er OUEST · 1er DIV.", "13e EST · 4e DIV.",
        )
        for (c in NbaFactice.BILANS.keys) if (!present("nba:pastille:$c")) fail("[NBA] pastille $c absente")
        if (!present("rapide:Réglages") || !present("biblio")) fail("[NBA] Réglages ou « Toutes les apps » absents du dock")
        if (choisie() != null) fail("[NBA] aucune pastille ne doit être sélectionnée au départ (${choisie()})")
        capturer("leaders")
    }

    @Test
    fun accueilParquet() {
        ambiance(AmbianceNba.PARQUET)
        source()
        afficher()
        avancer(1000)
        verifierTextes("SAISON 2026-27 · CONFÉRENCES ET DIVISIONS", "12–1")
        capturer("parquet")
    }

    @Test
    fun accueilNuit() {
        ambiance(AmbianceNba.NUIT)
        source()
        afficher()
        avancer(1000)
        verifierTextes("SAISON 2026-27 · CONFÉRENCES ET DIVISIONS", "12–1")
        capturer("nuit")
    }

    /** Premier démarrage hors ligne : « –– » partout, pas de rangs ni de top 3, rien en bas à droite. */
    @Test
    fun sansClassement() {
        source(classement = null)
        afficher()
        avancer(1000)
        verifierTextes("Classement en cours de chargement", "CLASSEMENT NBA")
        if (nombre("––") != 30) fail("[NBA] sans classement : 30 « –– » attendus, obtenu ${nombre("––")}")
        if (texte("DIV.") || texte("BILANS")) fail("[NBA] sans classement : ni rangs ni état des bilans")
        capturer("sans_classement")
    }

    @Test
    fun horsLigne() {
        source(classement = NbaFactice.classement(t - 35 * 60_000L, horsLigne = true))
        afficher()
        avancer(500)
        verifierTextes("HORS LIGNE", "BILANS DE 21:12", "12–1")
    }

    // ------------------------------------------------------------------ navigation

    /** ▲ depuis le dock : pastille la plus proche au-dessus ; ◀ ▲ ▶ ▼ ; ▼ en bas et Retour : le dock. */
    @Test
    fun navigationSurLaCarte() {
        source()
        afficher()
        touche(Key.DirectionUp)
        attendreCarte("▲ depuis le 1er favori")
        // Le focus reste sur la tuile d'où l'on est monté
        attendreFocus(favori(0), "▲ depuis le 1er favori")
        attendreChoisie("SAS", "▲ depuis le 1er favori")
        touche(Key.DirectionUp)
        attendreChoisie("DAL", "▲ depuis SAS")
        touche(Key.DirectionUp)
        attendreChoisie("OKC", "▲ depuis DAL")
        capturer("selection")
        touche(Key.DirectionRight)
        attendreChoisie("MEM", "▶ depuis OKC")
        touche(Key.DirectionLeft)
        attendreChoisie("IND", "◀ depuis MEM")
        for (attendue in listOf("MEM", "ATL", "NOP", "HOU")) {
            touche(Key.DirectionDown)
            attendreChoisie(attendue, "▼ vers $attendue")
        }
        // ▼ sous les pastilles du bas : retour au dock, sur la tuile de départ
        touche(Key.DirectionDown)
        attendreFocus(favori(0), "▼ depuis HOU")
        attendreChoisie(null, "retour au dock")
        if (present("nba:carte")) fail("[NBA] ▼ depuis HOU : la carte ne devait plus lire les touches")
        // Retour depuis la carte : le dock aussi
        touche(Key.DirectionRight)
        touche(Key.DirectionUp)
        attendreCarte("▲ depuis le 2e favori")
        attendreChoisie("SAS", "▲ depuis le 2e favori")
        touche(Key.Back)
        attendreFocus(favori(1), "Retour sur la carte")
        attendreChoisie(null, "Retour sur la carte")
        if (lances.isNotEmpty()) fail("[NBA] rien ne devait se lancer : $lances")
    }

    // ------------------------------------------------------------------ survol

    /**
     * ▲ depuis le dock : survol du Sud-Ouest (caméra, panneau : en-tête, 3 derniers matchs,
     * prochain, classement de la division) ; ▶ ◀ ▲ dans la division : la caméra ne bouge pas ;
     * ▲ vers OKC : le Nord-Ouest ; OK : la fiche à la place du panneau ; Retour : le survol ;
     * Retour : la carte entière, sans sélection.
     */
    @Test
    fun survolDuneDivision() {
        source(fiches = NbaFactice.fiches)
        afficher()
        attendreCamera("carte", "accueil")
        if (present("nba:survol")) fail("[NBA] accueil : pas de panneau de survol")
        touche(Key.DirectionUp)
        avancer(600)
        attendreChoisie("SAS", "▲ depuis le 1er favori")
        attendreCamera("sudouest", "▲ depuis le 1er favori")
        attendreFocus(favori(0), "survol")
        if (!present("nba:survol")) fail("[NBA] ▲ : panneau de survol attendu (${etatDuFocus()})")
        if (!present("nba:levee:sudouest")) fail("[NBA] ▲ : Sud-Ouest soulevé attendu")
        verifierTextes(
            "San Antonio Spurs", "6–7", "11e OUEST · 4e SUD-OUEST", "Défaite au dernier match",
            "meilleur marqueur : V. Wembanyama, 30,3 pts · 11,7 reb",
            "3 DERNIERS MATCHS", "119–124", "à Chicago", "jeu. 12 nov.", "122–96", "à Phoenix", "mar. 10 nov.",
            "125–103", "à Portland", "dim. 8 nov.",
            "PROCHAIN MATCH", "sam. 14 nov. · 3:30", "contre Golden State",
            "DIVISION SUD-OUEST", "ÉCART", "HOU", "9–4", "NOP", "3–10",
            "fiche complète", "équipe voisine", "carte entière",
        )
        if (!present("nba:survol:matchs")) fail("[NBA] survol : les 3 derniers matchs attendus")
        capturer("survol")
        // Dans la division : la caméra reste
        touche(Key.DirectionRight)
        attendreChoisie("HOU", "▶ depuis SAS")
        attendreCamera("sudouest", "▶ depuis SAS")
        verifierTextes("Houston Rockets", "Matchs indisponibles pour l'instant")
        touche(Key.DirectionLeft)
        attendreChoisie("SAS", "◀ depuis HOU")
        touche(Key.DirectionUp)
        attendreChoisie("DAL", "▲ depuis SAS")
        attendreCamera("sudouest", "▲ depuis SAS")
        // Vers une autre division : la caméra la suit
        touche(Key.DirectionUp)
        attendreChoisie("OKC", "▲ depuis DAL")
        attendreCamera("nordouest", "▲ depuis DAL")
        verifierTextes("Oklahoma City Thunder", "DIVISION NORD-OUEST", "108–118", "contre Denver")
        // OK : la fiche complète remplace le panneau (la caméra reste)
        touche(Key.DirectionCenter)
        if (!present("nba:fiche")) fail("[NBA] OK en survol : fiche attendue (${etatDuFocus()})")
        if (present("nba:survol")) fail("[NBA] OK en survol : le panneau de survol devait s'effacer")
        attendreCamera("nordouest", "fiche ouverte")
        verifierTextes("CINQ DE DÉPART", "Défaite 108–118")
        // Retour : de nouveau le survol
        touche(Key.Back)
        if (present("nba:fiche")) fail("[NBA] Retour dans la fiche : elle devait se fermer")
        if (!present("nba:survol")) fail("[NBA] Retour dans la fiche : le survol attendu")
        attendreChoisie("OKC", "Retour dans la fiche")
        attendreCamera("nordouest", "Retour dans la fiche")
        // Retour : la carte entière, plus de sélection
        touche(Key.Back)
        attendreChoisie(null, "Retour en survol")
        attendreCamera("carte", "Retour en survol")
        attendreFocus(favori(0), "Retour en survol")
        if (present("nba:survol") || present("nba:carte")) fail("[NBA] Retour en survol : ni panneau ni sélection")
        if (present("nba:levee:nordouest")) fail("[NBA] Retour en survol : le Nord-Ouest devait être reposé")
        if (lances.isNotEmpty()) fail("[NBA] rien ne devait se lancer : $lances")
    }

    /** ▼ sous les pastilles du bas et Maison : la carte entière aussi. */
    @Test
    fun survolQuitteParLeBasEtMaison() {
        source(fiches = NbaFactice.fiches)
        afficher()
        touche(Key.DirectionUp)
        attendreCamera("sudouest", "▲")
        touche(Key.DirectionDown)
        attendreChoisie(null, "▼ depuis SAS")
        attendreCamera("carte", "▼ depuis SAS")
        touche(Key.DirectionUp)
        attendreCamera("sudouest", "▲ de nouveau")
        appuisMaison.intValue++
        appliquer()
        avancer(600)
        attendreChoisie(null, "Maison")
        attendreCamera("carte", "Maison")
        if (present("nba:survol")) fail("[NBA] Maison : plus de panneau de survol")
    }

    /** Flèches enchaînées : la fiche n'est demandée que pour l'équipe où l'on s'arrête. */
    @Test
    fun survolFicheDemandeeALArret() {
        val s = source(fiches = NbaFactice.fiches)
        afficher()
        rafale(List(3) { Key.DirectionUp })
        appliquer()
        avancer(800)
        attendreChoisie("OKC", "▲ ▲ ▲ enchaînés")
        attendreCamera("nordouest", "▲ ▲ ▲ enchaînés")
        if (s.demandes != listOf("OKC")) fail("[NBA] flèches enchaînées : fiche demandée pour OKC seulement, obtenu ${s.demandes}")
        verifierTextes("Oklahoma City Thunder", "108–118")
        // OK : la fiche déjà lue sert telle quelle
        touche(Key.DirectionCenter)
        verifierTextes("CINQ DE DÉPART")
        if (s.demandes != listOf("OKC")) fail("[NBA] OK : la fiche ne devait pas être redemandée (${s.demandes})")
    }

    /** Survol d'une division de l'Est (Boston, Atlantique), par les flèches. */
    @Test
    fun survolEst() {
        source(fiches = NbaFactice.fiches)
        afficher()
        touche(Key.DirectionUp)
        for ((sens, attendue) in listOf(
            Key.DirectionUp to "DAL", Key.DirectionRight to "ATL", Key.DirectionRight to "CHA", Key.DirectionRight to "WAS",
            Key.DirectionRight to "BKN", Key.DirectionUp to "NYK", Key.DirectionRight to "BOS",
        )) {
            touche(sens)
            attendreChoisie(attendue, "vers $attendue")
        }
        avancer(1000)
        attendreCamera("atlantique", "BOS")
        verifierTextes("Boston Celtics", "10–3", "DIVISION ATLANTIQUE", "108–112", "à New York", "contre Miami", "meilleur marqueur : J. Brown")
        capturer("survol_est")
    }

    /** Survol en ambiance Parquet (Oklahoma City, Nord-Ouest). */
    @Test
    fun survolParquet() {
        ambiance(AmbianceNba.PARQUET)
        source(fiches = NbaFactice.fiches)
        afficher()
        repeat(3) { touche(Key.DirectionUp) }
        avancer(1000)
        attendreChoisie("OKC", "▲ ▲ ▲")
        attendreCamera("nordouest", "▲ ▲ ▲")
        verifierTextes("Oklahoma City Thunder", "DIVISION NORD-OUEST", "contre Memphis")
        capturer("survol_parquet")
    }

    // ------------------------------------------------------------------ fiche

    /** OK : fiche (Résumé), ▶ Effectif, ◀ Résumé ; Retour la ferme (pastille toujours sélectionnée), puis le dock. */
    @Test
    fun ficheResumeEtEffectif() {
        source()
        afficher()
        repeat(3) { touche(Key.DirectionUp) }
        attendreChoisie("OKC", "▲ ▲ ▲")
        touche(Key.DirectionCenter)
        avancer(500)
        if (!present("nba:fiche")) fail("[NBA] OK sur OKC : fiche attendue (${etatDuFocus()})")
        verifierTextes(
            "Oklahoma City Thunder", "1er OUEST · 1er NORD-OUEST", "3 victoires de suite", "sur les 10 derniers",
            "RÉSUMÉ", "EFFECTIF", "DERNIER MATCH", "Défaite 108–118", "à Portland · mer. 11 nov.",
            "PROCHAIN MATCH", "sam. 14 nov. · 4:30", "à LA Clippers", "CINQ DE DÉPART", "S. Gilgeous-Alexander", "31,8",
            "BLESSURES", "Absent · ischios", "Incertain · cheville",
        )
        if (!present("nba:fiche:resume")) fail("[NBA] onglet Résumé absent")
        capturer("fiche_resume")
        touche(Key.DirectionRight)
        if (!present("nba:fiche:effectif") || present("nba:fiche:resume")) fail("[NBA] ▶ : onglet Effectif attendu")
        verifierTextes("#2", "S. Gilgeous-Alexander", "B. Fleming", "BLESSÉ")
        capturer("fiche_effectif")
        touche(Key.DirectionLeft)
        if (!present("nba:fiche:resume")) fail("[NBA] ◀ : onglet Résumé attendu")
        touche(Key.Back)
        if (present("nba:fiche")) fail("[NBA] Retour : la fiche devait se fermer")
        attendreCarte("Retour dans la fiche")
        attendreChoisie("OKC", "après la fiche")
        touche(Key.Back)
        attendreFocus(favori(0), "Retour sur la carte")
    }

    /** Toucher une pastille ouvre sa fiche ; ▶ puis Retour à la télécommande. */
    @Test
    fun ficheAuToucher() {
        source()
        afficher()
        compose.onAllNodes(hasTestTag("nba:pastille:OKC"), useUnmergedTree = true).onFirst()
            .performTouchInput { click() }
        avancer(800)
        appliquer()
        verifierTextes("Oklahoma City Thunder", "Défaite 108–118")
        attendreChoisie("OKC", "toucher OKC")
        touche(Key.DirectionRight)
        if (!present("nba:fiche:effectif")) fail("[NBA] ▶ après le toucher : onglet Effectif attendu (${etatDuFocus()})")
        touche(Key.Back)
        if (present("nba:fiche")) fail("[NBA] Retour après le toucher : la fiche devait se fermer (${etatDuFocus()})")
    }

    @Test
    fun ficheIndisponible() {
        source()
        afficher()
        touche(Key.DirectionUp)
        attendreChoisie("SAS", "▲")
        touche(Key.DirectionCenter)
        avancer(500)
        verifierTextes("San Antonio Spurs", "Fiche indisponible pour l'instant")
        touche(Key.Back)
        attendreChoisie("SAS", "Retour")
    }

    @Test
    fun ficheEnChargement() {
        source(chargementSansFin = true)
        afficher()
        touche(Key.DirectionUp)
        touche(Key.DirectionCenter)
        avancer(500)
        if (!present("nba:fiche:chargement")) fail("[NBA] squelette de chargement absent (${etatDuFocus()})")
        verifierTextes("San Antonio Spurs", "6–7")
    }

    /** Classement suivi tant que le design est affiché et iBiTV au premier plan. */
    @Test
    fun suiviDuClassement() {
        val s = source()
        afficher()
        if (s.suivis.firstOrNull() != true) fail("[NBA] suivre(true) attendu à l'affichage : ${s.suivis}")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        avancer(500)
        appliquer()
        if (s.suivis.lastOrNull() != false) fail("[NBA] iBiTV au second plan : suivre(false) attendu : ${s.suivis}")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        avancer(500)
        appliquer()
        if (s.suivis.lastOrNull() != true) fail("[NBA] retour au premier plan : suivre(true) attendu : ${s.suivis}")
    }
}

/** Carte des divisions : lecture de carte_nba.json et dessin dans chaque ambiance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CarteNbaTest {

    private val carte by lazy { CarteNba.charger { lireCarteNba(RuntimeEnvironment.getApplication()) } }

    @Test
    fun lecture() {
        assertEquals(DivisionNba.entries.toSet(), carte.iles.map { it.division }.toSet())
        assertEquals(30, carte.pastilles.size)
        assertEquals(NbaFactice.BILANS.keys, carte.pastilles.map { it.code }.toSet())
        // New York et Los Angeles : une seule capsule à deux lignes chacune
        assertEquals(28, carte.capsules.size)
        assertEquals(listOf("NYK", "BKN"), carte.capsules.first { it.size == 2 && it[0].groupe == "NY" }.map { it.code })
        assertEquals(listOf("LAL", "LAC"), carte.capsules.first { it.size == 2 && it[0].groupe == "LA" }.map { it.code })
        assertEquals(17, carte.monuments.size)
        assertTrue(carte.monuments.first { it.id == "goldengate" }.enMer)
        assertTrue(carte.monuments.first { it.id == "hollywood" }.parties.any { it is PartieMonument.Texte })
        assertEquals(6, carte.noms.size)
        assertTrue(carte.noms.first { it.division == DivisionNba.CENTRALE }.horsIle)
        assertEquals(6, carte.ronds.size)
        assertEquals(30, carte.villes.size)
        // Le dessin couvre les îles, les monuments et les pastilles, dans l'écran
        val c = carte.cadre
        assertTrue("cadre $c", c.left > 0f && c.top > 0f && c.right < 1920f && c.bottom < 1080f && c.width > 1400f)
    }

    /** Couleur d'un point de l'île Nord-Ouest (480, 260) dans l'image de la carte. */
    private fun pointNordOuest(ambiance: AmbianceNba, leaders: Map<DivisionNba, String>): Triple<Int, Int, Int> {
        val mesure = TextMeasurer(createFontFamilyResolver(RuntimeEnvironment.getApplication()), Density(1f), LayoutDirection.Ltr, 0)
        val image = rendreCarteNba(carte, PaletteNba.de(ambiance, leaders), emptyMap(), mesure, 0.5f)
        assertEquals(kotlin.math.ceil(carte.cadre.width * 0.5f).toInt(), image.image.width)
        val p = image.image.asAndroidBitmap().getPixel(((480f - image.cadre.left) * 0.5f).toInt(), ((260f - image.cadre.top) * 0.5f).toInt())
        return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
    }

    /** Caméra du survol sur la vraie carte : chaque division (île, 5 pastilles, villes) soulevée tient dans la zone. */
    @Test
    fun cameraDesDivisions() {
        val zone = CameraNba.ZONE
        for (d in DivisionNba.entries) {
            val cadre = CameraNba.cadre(carte, d)
            for (p in carte.pastilles.filter { DivisionNba.de(it.code) == d }) {
                assertTrue("$d : pastille ${p.code} hors du cadre", cadre.contains(FormePastille.centre(p)))
            }
            assertTrue("$d : île hors du cadre", carte.iles.first { it.division == d }.bornes.let { cadre.contains(it.topLeft) && cadre.contains(it.bottomRight) })
            val cam = CameraNba.division(carte, d)
            assertTrue("$d : échelle ${cam.echelle}", cam.echelle > 1.2f && cam.echelle <= CameraNba.ECHELLE_MAX)
            val (e, dec) = cam.soulevee(cadre.center, 1f)
            val haut = cadre.topLeft * e + dec
            val bas = cadre.bottomRight * e + dec
            assertTrue("$d soulevée : $haut – $bas hors de $zone", haut.x >= zone.left - 0.5f && haut.y >= zone.top - 0.5f && bas.x <= zone.right + 0.5f && bas.y <= zone.bottom + 0.5f)
        }
        // Petite division : agrandie au maximum ; la plus grande (Nord-Ouest) un peu moins
        assertEquals(CameraNba.ECHELLE_MAX, CameraNba.division(carte, DivisionNba.ATLANTIQUE).echelle, 1e-4f)
        assertTrue(CameraNba.division(carte, DivisionNba.NORD_OUEST).echelle < 1.45f)
    }

    /** Division soulevée : île nette à l'échelle de la caméra (couleur de son leader, transparente autour), ombre au quart. */
    @Test
    fun divisionSoulevee() {
        val mesure = TextMeasurer(createFontFamilyResolver(RuntimeEnvironment.getApplication()), Density(1f), LayoutDirection.Ltr, 0)
        val leaders = DivisionNba.entries.associateWith { d -> NbaFactice.classement(0L).equipes.values.first { it.rangDivision == 1 && DivisionNba.de(it.code) == d }.code }
        val palette = PaletteNba.de(AmbianceNba.LEADERS, leaders)
        val im = rendreLevee(carte, DivisionNba.SUD_OUEST, palette, emptyMap(), mesure, 1f, Unit)
        val zoom = CameraNba.division(carte, DivisionNba.SUD_OUEST).echelle
        assertEquals(zoom * CameraNba.AGRANDI, im.ile.echelle, 1e-4f)
        assertEquals(kotlin.math.ceil(im.ile.cadre.width * im.ile.echelle).toInt(), im.ile.image.width)
        val bitmap = im.ile.image.asAndroidBitmap()
        fun point(x: Float, y: Float) = bitmap.getPixel(((x - im.ile.cadre.left) * im.ile.echelle).toInt(), ((y - im.ile.cadre.top) * im.ile.echelle).toInt())
        // Milieu de l'île (Texas) : le rouge de Houston, leader ; coin de l'image : transparent
        val p = point(700f, 620f)
        val (r, g, b) = Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        assertTrue("Sud-Ouest rouge de Houston ($r, $g, $b)", r > g + 60 && r > b + 40)
        assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
        // Ombre : petite image, plus large que l'île (flou), noire
        assertTrue(im.ombre.image.width < im.ile.image.width / 2)
        assertTrue(im.ombre.cadre.width > im.ile.cadre.width)
    }

    @Test
    fun dessinDesAmbiances() {
        val leaders = DivisionNba.entries.associateWith { d -> NbaFactice.classement(0L).equipes.values.first { it.rangDivision == 1 && DivisionNba.de(it.code) == d }.code }
        val (r1, _, b1) = pointNordOuest(AmbianceNba.LEADERS, leaders)
        assertTrue("Leaders : Nord-Ouest bleu du Thunder ($r1, $b1)", b1 > r1 + 60)
        val (r2, g2, b2) = pointNordOuest(AmbianceNba.PARQUET, emptyMap())
        assertTrue("Parquet : bois ($r2, $g2, $b2)", r2 > g2 && g2 > b2 && r2 > 120)
        val (r3, g3, b3) = pointNordOuest(AmbianceNba.NUIT, emptyMap())
        assertTrue("Nuit d'arène : violet ($r3, $g3, $b3)", b3 > r3 && r3 > g3)
        val (r4, g4, b4) = pointNordOuest(AmbianceNba.LEADERS, emptyMap())
        assertTrue("Sans classement : gris ardoise ($r4, $g4, $b4)", kotlin.math.abs(r4 - g4) < 20 && b4 > r4)
    }
}
