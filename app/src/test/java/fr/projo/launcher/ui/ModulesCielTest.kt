package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.air.AirDonnees
import fr.projo.launcher.modules.air.Pollen
import fr.projo.launcher.modules.air.Polluant
import fr.projo.launcher.modules.air.TypePollen
import fr.projo.launcher.modules.sport.SportReseau
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Données factices des modules Soleil et lune, Qualité de l'air, Horloges du monde. */
object ModulesCielFactices {

    fun air(pollens: Boolean = true) = AirDonnees(
        lieu = "Lyon",
        indice = 47,
        polluants = listOf(
            Polluant("O₃", 96.0, 47),
            Polluant("PM2,5", 11.0, 22),
            Polluant("NO₂", 18.0, 9),
            Polluant("PM10", 16.0, 8),
        ),
        pollens = if (pollens) {
            listOf(Pollen(TypePollen.AMBROISIE, 23.0), Pollen(TypePollen.GRAMINEES, 31.0), Pollen(TypePollen.OLIVIER, 4.0))
                .sortedWith(compareByDescending<Pollen> { it.niveau }.thenByDescending { it.grains })
        } else emptyList(),
        europe = true,
    )

    fun donnees(air: AirDonnees) = DonneesModules(factices = { cle ->
        when {
            cle.startsWith("air:") -> air
            cle.startsWith("meteo") -> ModulesFactices.meteo()
            cle == "sport:nba" -> SportReseau.matchsNba(DonneesFactices.nba())
            else -> null
        }
    })

    /** Position de la Météo : Lyon (choisie dans ses réglages). */
    fun hote(vararg ids: String, reglages: Map<String, String> = emptyMap(), air: AirDonnees = air()) =
        HoteModules(ids.toList(), mapOf("meteo.lieu" to "Lyon;45.764;4.8357") + reglages, donnees(air))
}

/**
 * Modules Soleil et lune, Qualité de l'air et Horloges du monde : captures en MOYEN (module
 * seul) et en PETIT (deux modules côte à côte) dans 3 designs dont Pastel (clair), textes
 * jamais coupés, et sortie de la zone par ◀ et Retour.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ModulesCielTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> =
            listOf(Design.PASTEL, Design.AURORE, Design.GRAPHITE).map { arrayOf<Any>(it.name) }
    }

    @Test
    fun soleilMoyen() = montrer("b1_soleil_moyen", "soleil", ModulesCielFactices.hote("soleil"))

    @Test
    fun airMoyen() = montrer("b2_air_moyen", "air", ModulesCielFactices.hote("air"))

    /** 4 villes aux noms longs et écarts variés (dont une demi-heure). */
    @Test
    fun horlogesMoyen() = montrer(
        "b3_horloges_moyen", "horloges",
        ModulesCielFactices.hote("horloges", reglages = mapOf("horloges.villes" to "los-angeles,johannesburg,bombay,auckland")),
    )

    @Test
    fun soleilEtAirPetits() = montrer("b4_soleil_air_petits", "soleil", ModulesCielFactices.hote("soleil", "air"), "air")

    /** Horloges (3 villes par défaut) et qualité de l'air hors saison des pollens. */
    @Test
    fun horlogesEtAirHorsSaison() = montrer(
        "b5_horloges_air_petits", "horloges",
        ModulesCielFactices.hote("horloges", "air", air = ModulesCielFactices.air(pollens = false)), "air",
    )

    /** Air seul hors saison (MOYEN) et Soleil + Horloges (PETIT, 4 villes). */
    @Test
    fun airHorsSaisonMoyen() = montrer(
        "b6_air_hors_saison", "air", ModulesCielFactices.hote("air", air = ModulesCielFactices.air(pollens = false)),
    )

    @Test
    fun soleilEtHorlogesPetits() = montrer(
        "b7_soleil_horloges_petits", "horloges",
        ModulesCielFactices.hote(
            "soleil", "horloges",
            reglages = mapOf("horloges.villes" to "los-angeles,johannesburg,bombay,auckland"),
        ),
        "soleil",
    )

    /** ◀ passe du 2e module au 1er, puis quitte la zone ; Retour quitte la zone. */
    @Test
    fun navigationSoleilAir() {
        hoteModules = ModulesCielFactices.hote("soleil", "air")
        afficher()
        focusDans("module:air")
        appuyer(Key.DirectionLeft)
        attendreFocus("module:soleil", "◀ depuis Air")
        appuyer(Key.DirectionLeft)
        verifierSortie("◀ depuis Soleil (1er module)", gauche = true)
        focusDans("module:air")
        appuyer(Key.Back)
        verifierSortie("Retour depuis Air", gauche = false)
        focusDans("module:soleil")
        appuyer(Key.Back)
        verifierSortie("Retour depuis Soleil", gauche = false)
    }

    @Test
    fun navigationHorloges() {
        hoteModules = ModulesCielFactices.hote("horloges")
        afficher()
        focusDans("module:horloges")
        appuyer(Key.DirectionLeft)
        verifierSortie("◀ depuis Horloges", gauche = true)
        focusDans("module:horloges")
        appuyer(Key.Back)
        verifierSortie("Retour depuis Horloges", gauche = false)
    }

    // ------------------------------------------------------------------ outils

    /** Affiche la zone, met le focus dans [focusSur], vérifie les textes, capture l'écran et la zone. */
    private fun montrer(nom: String, focusSur: String, hote: HoteModules, vararg autres: String) {
        hoteModules = hote
        afficher()
        focusDans("module:$focusSur")
        verifierZoneVisible()
        val fichier = capturer(nom)
        zoomerZone(fichier, nom + "_zoom")
        (listOf(focusSur) + autres).forEach { verifierTextesEntiers("module:$it") }
    }

    /** Donne le focus au premier élément focalisable d'un module. */
    private fun focusDans(etiquette: String) {
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

    /** Le focus a quitté la zone : 1er favori (Graphite, ◀ : bouton Réglages). */
    private fun verifierSortie(etape: String, gauche: Boolean) {
        val f = focus()
        val attendu = if (gauche && design == Design.GRAPHITE) "rapide:Réglages" else favori(0)
        if (f?.etiquette != attendu || "nba" in f.chaine) {
            fail("[${design.name}] $etape : attendu $attendu, obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
        }
    }

    private fun noeudZone(): SemanticsNode? =
        compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

    /** La zone de modules tient entièrement dans l'écran. */
    private fun verifierZoneVisible() {
        val racine = compose.onRoot().fetchSemanticsNode().size
        val zone = noeudZone() ?: return fail("[${design.name}] zone de modules introuvable")
        val b = zone.boundsInRoot
        if (b.left < 0f || b.top < 0f || b.right > racine.width + 0.5f || b.bottom > racine.height + 0.5f) {
            fail("[${design.name}] zone de modules hors de l'écran : ${b.enU()}")
        }
    }

    /**
     * Aucun texte du module coupé : ni points de suspension, ni dépassement de sa boîte, ni
     * débordement hors du module.
     */
    private fun verifierTextesEntiers(etiquette: String) {
        val module = compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] $etiquette introuvable")
        val cadre = module.boundsInRoot
        val textes = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(etiquette)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (textes.isEmpty()) fail("[${design.name}] $etiquette : aucun texte trouvé")
        val defauts = mutableListOf<String>()
        for (n in textes) {
            val resultats = mutableListOf<TextLayoutResult>()
            n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultats)
            val r = resultats.firstOrNull() ?: continue
            val texte = r.layoutInput.text.text
            if (texte.isBlank()) continue
            val ellipse = (0 until r.lineCount).any { r.isLineEllipsized(it) }
            val b = n.boundsInRoot
            val dehors = b.left < cadre.left - 1f || b.right > cadre.right + 1f || b.top < cadre.top - 1f || b.bottom > cadre.bottom + 1f
            when {
                ellipse -> defauts += "« $texte » (points de suspension)"
                r.hasVisualOverflow -> defauts += "« $texte » (dépasse : ${r.size} pour ${r.layoutInput.constraints})"
                dehors -> defauts += "« $texte » (hors du module : ${b.enU()} / ${cadre.enU()})"
            }
        }
        println("[${design.name}] $etiquette : ${textes.size} textes vérifiés")
        if (defauts.isNotEmpty()) fail("[${design.name}] $etiquette : textes coupés : ${defauts.joinToString(" ; ")}")
    }

    /** Agrandissement ×2 de la zone de modules, d'après une capture. */
    private fun zoomerZone(source: File, nom: String) {
        val zone = noeudZone() ?: return
        val image: Bitmap = BitmapFactory.decodeFile(source.path) ?: return
        val m = 6 * pxParU
        val b: Rect = zone.boundsInRoot
        val x0 = (b.left - m).toInt().coerceIn(0, image.width - 1)
        val y0 = (b.top - m).toInt().coerceIn(0, image.height - 1)
        val x1 = (b.right + m).toInt().coerceIn(x0 + 1, image.width)
        val y1 = (b.bottom + m).toInt().coerceIn(y0 + 1, image.height)
        val morceau = Bitmap.createBitmap(image, x0, y0, x1 - x0, y1 - y0)
        val grand = Bitmap.createScaledBitmap(morceau, morceau.width * 2, morceau.height * 2, true)
        File(source.parentFile, "${design.name.lowercase()}_$nom.png").outputStream().use {
            grand.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
