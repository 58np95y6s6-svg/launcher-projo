package fr.projo.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
 * pastille ; fiche (Résumé, Effectif, indisponible, chargement) ; suivi du classement selon
 * l'affichage. Captures nba_*.png à comparer aux maquettes.
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
    ) = SourceNbaFactice(classement, t, chargementSansFin = chargementSansFin).also { sourceNba = it }

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
        return n.joinToString(" ; ") { it.config.getOrNull(SemanticsProperties.TestTag) ?: "(sans étiquette)" } +
            " | lancés $lances, menus $menus"
    }

    private fun attendreChoisie(code: String?, etape: String) {
        val c = choisie()
        if (c != code) fail("[NBA] $etape : pastille sélectionnée attendue $code, obtenue $c (focus ${decrire(focus())} ; ${etatDuFocus()})")
    }

    private fun attendreFocus(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || (f.etiquette != etiquette && etiquette !in f.chaine)) fail("[NBA] $etape : focus attendu sur $etiquette, obtenu ${decrire(f)} (${f?.chaine})")
    }

    private fun verifierTextes(vararg attendus: String) {
        for (s in attendus) if (!texte(s)) fail("[NBA] texte « $s » absent (${etatDuFocus()})")
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
        appuyer(Key.DirectionUp)
        attendreFocus("nba:carte", "▲ depuis le 1er favori")
        attendreChoisie("SAS", "▲ depuis le 1er favori")
        appuyer(Key.DirectionUp)
        attendreChoisie("DAL", "▲ depuis SAS")
        appuyer(Key.DirectionUp)
        attendreChoisie("OKC", "▲ depuis DAL")
        capturer("selection")
        appuyer(Key.DirectionRight)
        attendreChoisie("MEM", "▶ depuis OKC")
        appuyer(Key.DirectionLeft)
        attendreChoisie("IND", "◀ depuis MEM")
        for (attendue in listOf("MEM", "ATL", "NOP", "HOU")) {
            appuyer(Key.DirectionDown)
            attendreChoisie(attendue, "▼ vers $attendue")
        }
        // ▼ sous les pastilles du bas : retour au dock, sur la tuile de départ
        appuyer(Key.DirectionDown)
        attendreFocus(favori(0), "▼ depuis HOU")
        attendreChoisie(null, "retour au dock")
        // Retour depuis la carte : le dock aussi
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionUp)
        attendreFocus("nba:carte", "▲ depuis le 2e favori")
        appuyer(Key.Back)
        attendreFocus(favori(1), "Retour sur la carte")
        if (lances.isNotEmpty()) fail("[NBA] rien ne devait se lancer : $lances")
    }

    // ------------------------------------------------------------------ fiche

    /** OK : fiche (Résumé), ▶ Effectif, ◀ Résumé ; Retour la ferme (pastille toujours sélectionnée), puis le dock. */
    @Test
    fun ficheResumeEtEffectif() {
        source()
        afficher()
        repeat(3) { appuyer(Key.DirectionUp) }
        attendreChoisie("OKC", "▲ ▲ ▲")
        appuyer(Key.DirectionCenter)
        avancer(500)
        attendreFocus("nba:fiche", "OK sur OKC")
        verifierTextes(
            "Oklahoma City Thunder", "1er OUEST · 1er NORD-OUEST", "3 victoires de suite", "sur les 10 derniers",
            "RÉSUMÉ", "EFFECTIF", "DERNIER MATCH", "Défaite 108–118", "à Portland · mer. 11 nov.",
            "PROCHAIN MATCH", "sam. 14 nov. · 4:30", "à LA Clippers", "CINQ DE DÉPART", "S. Gilgeous-Alexander", "31,8",
            "BLESSURES", "Absent · ischios", "Incertain · cheville",
        )
        if (!present("nba:fiche:resume")) fail("[NBA] onglet Résumé absent")
        capturer("fiche_resume")
        appuyer(Key.DirectionRight)
        if (!present("nba:fiche:effectif") || present("nba:fiche:resume")) fail("[NBA] ▶ : onglet Effectif attendu")
        verifierTextes("#2", "S. Gilgeous-Alexander", "B. Fleming", "BLESSÉ")
        attendreFocus("nba:fiche", "▶ dans la fiche")
        capturer("fiche_effectif")
        appuyer(Key.DirectionLeft)
        if (!present("nba:fiche:resume")) fail("[NBA] ◀ : onglet Résumé attendu")
        appuyer(Key.Back)
        if (present("nba:fiche")) fail("[NBA] Retour : la fiche devait se fermer")
        attendreFocus("nba:carte", "Retour dans la fiche")
        attendreChoisie("OKC", "après la fiche")
        appuyer(Key.Back)
        attendreFocus(favori(0), "Retour sur la carte")
    }

    @Test
    fun ficheIndisponible() {
        source()
        afficher()
        appuyer(Key.DirectionUp)
        attendreChoisie("SAS", "▲")
        appuyer(Key.DirectionCenter)
        avancer(500)
        verifierTextes("San Antonio Spurs", "Fiche indisponible pour l'instant")
        appuyer(Key.Back)
        attendreChoisie("SAS", "Retour")
    }

    @Test
    fun ficheEnChargement() {
        source(chargementSansFin = true)
        afficher()
        appuyer(Key.DirectionUp)
        appuyer(Key.DirectionCenter)
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
        if (s.suivis.lastOrNull() != false) fail("[NBA] iBiTV au second plan : suivre(false) attendu : ${s.suivis}")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        avancer(500)
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
