package fr.projo.launcher.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaTeam
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.meteo.MeteoDonnees
import fr.projo.launcher.modules.meteo.MeteoJour
import fr.projo.launcher.modules.meteo.ModuleMeteo
import fr.projo.launcher.modules.sport.MatchsCompetition
import fr.projo.launcher.modules.sport.ModuleSport
import fr.projo.launcher.modules.sport.SportReseau
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Données factices des modules : aucun accès réseau. */
object ModulesFactices {
    private const val H = 3_600_000L

    fun meteo(): MeteoDonnees {
        val t = System.currentTimeMillis()
        return MeteoDonnees(
            lieu = "Lyon", temperature = 14.2, ressenti = 11.6, code = 2, jour = true,
            pluieDebut = t + 35 * 60_000L,
            jours = listOf(
                MeteoJour(t + 24 * H, 61, 8.0, 15.4, 70),
                MeteoJour(t + 48 * H, 3, 6.6, 12.8, 10),
                MeteoJour(t + 72 * H, 0, 4.2, 17.1, 0),
            ),
        )
    }

    fun ligue1(): MatchsCompetition {
        val t = System.currentTimeMillis()
        return MatchsCompetition(
            listOf(
                NbaGame("l1a", t - 26 * H, NbaTeam("PSG", null, "2"), NbaTeam("OM", null, "1"), "post", "FT"),
                NbaGame("l1b", t - H, NbaTeam("OL", null, "1"), NbaTeam("LOSC", null, "1"), "in", "67'"),
                NbaGame("l1c", t + 26 * H, NbaTeam("RCL", null, null), NbaTeam("ASM", null, null), "pre", ""),
            ),
        )
    }

    fun top14(): MatchsCompetition {
        val t = System.currentTimeMillis()
        return MatchsCompetition(
            listOf(
                NbaGame("t1", t + 2 * H, NbaTeam("ST", null, null), NbaTeam("TOUL", null, null), "pre", ""),
                NbaGame("t2", t + 50 * H, NbaTeam("RCT", null, null), NbaTeam("UBB", null, null), "pre", ""),
            ),
        )
    }

    /** Données des modules ; la Ligue des champions simule une source en panne. */
    fun donnees() = DonneesModules(factices = { cle ->
        when {
            cle == "sport:nba" -> SportReseau.matchsNba(DonneesFactices.nba())
            cle == "sport:fra.1" -> ligue1()
            cle == "sport:top14" -> top14()
            cle == "sport:uefa.champions" -> EtatDonnees(null, erreur = true, date = System.currentTimeMillis())
            cle.startsWith("meteo") -> meteo()
            else -> null
        }
    })

    /** Ville de la météo : Lyon (choisie dans les réglages). */
    fun hote(vararg ids: String, reglages: Map<String, String> = emptyMap()) =
        HoteModules(ids.toList(), mapOf("meteo.lieu" to "Lyon;45.764;4.8357") + reglages, donnees())
}

/**
 * Zone de modules dans chaque design : Météo seule, Météo + Sport (deux modules côte à côte),
 * Sport avec plusieurs compétitions, et navigation ◀ ▶ entre deux modules.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ModulesTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    @Test
    fun meteoSeule() {
        hoteModules = ModulesFactices.hote("meteo")
        afficher()
        focusDans("module:meteo")
        verifierZoneVisible()
        capturer("m1_meteo_seule")
        // Graphite : le grand titre nomme le module sélectionné
        if (design == Design.GRAPHITE &&
            compose.onAllNodes(hasText("Météo · Lyon"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        ) {
            fail("[GRAPHITE] titre « Météo · Lyon » attendu quand la Météo est sélectionnée")
        }
    }

    @Test
    fun meteoEtSport() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        focusDans("module:sport")
        verifierZoneVisible()
        capturer("m2_meteo_sport")
    }

    @Test
    fun sportPlusieursCompetitions() {
        hoteModules = ModulesFactices.hote(
            "sport", reglages = mapOf("sport.competitions" to "nba,fra.1,uefa.champions,top14"),
        )
        afficher()
        focusDans("module:sport")
        appuyer(Key.DirectionRight)
        verifierZoneVisible()
        capturer("m3_sport_plusieurs")
    }

    /**
     * Deux modules côte à côte : ◀ / ▶ passent de l'un à l'autre (Playlist : empilés, ▲ / ▼) ;
     * ◀ sur le 1er revient aux favoris (Graphite : à Réglages, d'où l'on entre dans la zone par ▶).
     */
    @Test
    fun navigationDeuxModules() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        focusDans("module:sport")
        if (design == Design.PLAYLIST) {
            // Playlist : panneau de droite étroit et haut, les deux modules y sont EMPILÉS ;
            // ▲ / ▼ passent de l'un à l'autre (◀ quitte la zone, vérifié plus bas)
            appuyer(Key.DirectionUp)
            attendreFocus("module:meteo", "▲ depuis Sport")
            appuyer(Key.DirectionDown)
            attendreFocus("module:sport", "▼ depuis Météo")
        } else {
            appuyer(Key.DirectionLeft)
            attendreFocus("module:meteo", "◀ depuis Sport")
            appuyer(Key.DirectionRight)
            attendreFocus("module:sport", "▶ depuis Météo")
        }
        focusDans("module:meteo")
        appuyer(Key.DirectionLeft)
        val f = focus()
        // Néon : le panneau des modules se referme sur le bouton Modules qui l'a ouvert
        val attendu = when (design) {
            Design.GRAPHITE -> "rapide:Réglages"
            Design.NEON -> "rapide:Modules"
            else -> favori(0)
        }
        if (f?.etiquette != attendu) {
            fail("[${design.name}] ◀ depuis Météo (1er module) : attendu $attendu, obtenu ${decrire(f)}")
        }
        if (design == Design.GRAPHITE) {
            // ▶ depuis Réglages : retour dans la zone, sur le 1er module
            appuyer(Key.DirectionRight)
            attendreFocus("module:meteo", "Réglages puis ▶")
        }
    }

    // ------------------------------------------------------------------ outils

    /** Donne le focus au premier élément focalisable d'un module. */
    private fun focusDans(etiquette: String) {
        // Chaînes : la zone de modules s'ouvre en grand par le bouton enveloppe
        ouvrirZoneModules()
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag(etiquette)) and SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        avancer(1000)
        attendreFocus(etiquette, "préparation")
    }

    private fun attendreFocus(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || etiquette !in f.chaine) {
            fail("[${design.name}] $etape : focus attendu dans « $etiquette », obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
        }
    }

    /** La zone de modules tient entièrement dans l'écran. */
    private fun verifierZoneVisible() {
        val racine = compose.onRoot().fetchSemanticsNode().size
        val zone = compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] zone de modules introuvable")
        val b = zone.boundsInRoot
        if (b.left < 0f || b.top < 0f || b.right > racine.width + 0.5f || b.bottom > racine.height + 0.5f) {
            fail("[${design.name}] zone de modules hors de l'écran : ${b.enU()}")
        }
        println("[${design.name}] zone de modules : ${b.enU()}")
    }
}

/** Captures des dialogues : Réglages → Modules, réglages du Sport et de la Météo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesModulesDialogTest : BancAccueil("AURORE") {

    @OptIn(ExperimentalRoborazziApi::class)
    private fun dialogue(nom: String, contenu: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) { contenu() }
            }
        }
        avancer(1500)
        val fichier = File("build/captures/dialogue_$nom.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }

    @Test
    fun modules() {
        val hote = ModulesFactices.hote("meteo", "sport")
        dialogue("1_modules") { ModulesDialog(hote, onChoisir = {}, onDismiss = {}) }
    }

    @Test
    fun reglagesSport() {
        val hote = ModulesFactices.hote("sport", reglages = mapOf("sport.competitions" to "nba,fra.1,top14"))
        dialogue("2_sport") { ModuleSport.Reglages(hote.reglagesDe("sport"), hote.donnees) {} }
    }

    @Test
    fun reglagesMeteo() {
        val hote = ModulesFactices.hote("meteo", reglages = mapOf("meteo.auto" to "Lyon;45.76;4.84"))
        dialogue("3_meteo") { ModuleMeteo.Reglages(hote.reglagesDe("meteo"), hote.donnees) {} }
    }
}
