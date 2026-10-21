package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.telecommande.CLE_SOURCE_TELECOMMANDE
import fr.projo.launcher.modules.telecommande.SourceTelecommande
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.remote.RemoteConfig
import fr.projo.launcher.remote.TelephoneAppaire
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/** Source factice du module Télécommande : état choisi par le test, appels notés. */
class SourceTelecommandeFactice(
    etat: EtatAppairage,
    telephones: List<TelephoneAppaire> = emptyList(),
    connectes: Set<String> = emptySet(),
) : SourceTelecommande {
    override val appairage = MutableStateFlow(etat)
    override val telephones = MutableStateFlow(telephones)
    override val connectes = MutableStateFlow(connectes)

    /** Modules affichés en ce moment (accueil au premier plan). */
    val affiches = mutableSetOf<Any>()
    var ouvertures = 0
    var fermetures = 0

    override fun afficher(cle: Any) { affiches += cle }
    override fun masquer(cle: Any) { affiches -= cle }
    override fun ouvrirDialogue() { ouvertures++ }
    override fun fermerDialogue() { fermetures++ }
    override fun nouveauCode() {}
}

object FacticesTelecommande {
    val url = RemoteConfig.urlAppairage(
        "ibiprojo-0123456789abcdef0123456789abcdef", "q2Xk9_LmT0aZr1bYwE3uVg", "YPXzIOhZAT92PqkIdIKfBw",
    )
    const val CODE = "K7QM-3XPA"

    fun telephones() = listOf(
        TelephoneAppaire("tel-1", "Pixel de Marie", "cle1", 1_760_000_000_000L),
        TelephoneAppaire("tel-2", "iPhone de Paul", "cle2", 1_760_100_000_000L),
        TelephoneAppaire("tel-3", "Tablette", "cle3", 1_760_200_000_000L),
    )

    fun source(etat: EtatAppairage = EtatAppairage.EnCours(url, CODE, System.currentTimeMillis() + 200_000)) =
        SourceTelecommandeFactice(etat, telephones(), setOf("tel-2"))

    fun hote(source: SourceTelecommande, vararg ids: String) = HoteModules(
        ids.toList(),
        mapOf("meteo.lieu" to "Lyon;45.764;4.8357"),
        DonneesModules(factices = { cle ->
            when {
                cle == CLE_SOURCE_TELECOMMANDE -> source
                cle.startsWith("meteo") -> ModulesFactices.meteo()
                else -> null
            }
        }),
    )
}

/**
 * Module Télécommande (QR code d'appairage sur l'accueil) : captures seul (MOYEN ; GRAND dans
 * Playlist) et avec la Météo (PETIT côte à côte ; MOYEN empilés dans Playlist), dans un
 * design clair (Pastel) et un foncé (Aurore). Contrôles : rien n'est coupé, QR code d'au moins
 * 90 u, décodé depuis la capture (et réduite comme vue de loin par un téléphone) : il donne
 * l'URL d'appairage. OK ouvre le dialogue en grand ; le module ne demande un secret que
 * pendant que l'accueil est au premier plan.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ModuleTelecommandeTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> =
            listOf(Design.PASTEL, Design.AURORE, Design.PLAYLIST).map { arrayOf<Any>(it.name) }
    }

    private fun preparer(source: SourceTelecommandeFactice, vararg ids: String) {
        hoteModules = FacticesTelecommande.hote(source, *ids)
        afficher()
        focusSur("module:telecommande")
    }

    @Test
    fun seul() {
        val source = FacticesTelecommande.source()
        preparer(source, "telecommande")
        val capture = capturer("q1_telecommande_seul")
        controler("module:telecommande")
        verifierQr(capture, "seul")
        assertEquals("le module demande un QR code valable (accueil affiché)", 1, source.affiches.size)
    }

    @Test
    fun avecLaMeteo() {
        val source = FacticesTelecommande.source()
        preparer(source, "telecommande", "meteo")
        val capture = capturer("q2_telecommande_meteo")
        controler("module:telecommande")
        verifierQr(capture, "avec la Météo")
    }

    @Test
    fun meteoPuisTelecommande() {
        val source = FacticesTelecommande.source()
        preparer(source, "meteo", "telecommande")
        val capture = capturer("q3_meteo_telecommande")
        controler("module:telecommande")
        verifierQr(capture, "2e module")
    }

    /** Juste après un appairage : « ✓ » et le nom du téléphone, sans QR code. */
    @Test
    fun appaire() {
        val source = FacticesTelecommande.source(EtatAppairage.Reussi("Pixel de Marie"))
        preparer(source, "telecommande", "meteo")
        controler("module:telecommande")
        if (bornesDe("telecommande:reussi") == null) fail("[${design.name}] « ✓ » attendu après un appairage")
        if (bornesDe("telecommande:qr") != null) fail("[${design.name}] pas de QR code après un appairage (secret consommé)")
        capturer("q4_telecommande_appaire")
    }

    /** OK sur le module : le dialogue d'appairage en grand, avec le même QR code. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun okOuvreLeDialogue() {
        val source = FacticesTelecommande.source()
        preparer(source, "telecommande", "meteo")
        appuyer(Key.DirectionCenter)
        assertEquals("OK : ouverture du dialogue", 1, source.ouvertures)
        if (compose.onAllNodes(hasTestTag("qr"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            fail("[${design.name}] OK sur le module : dialogue d'appairage (QR code « qr ») attendu")
        }
        if (compose.onAllNodes(hasTestTag("code"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            fail("[${design.name}] dialogue : code de secours attendu")
        }
        val fichier = File("build/captures/${design.name.lowercase()}_q5_telecommande_dialogue.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }

    /** Accueil caché (une appli au premier plan) : le module ne demande plus de secret ; au retour, si. */
    @Test
    fun accueilCache() {
        val source = FacticesTelecommande.source()
        preparer(source, "telecommande")
        assertEquals(1, source.affiches.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        avancer(500)
        assertTrue("accueil caché : plus de renouvellement", source.affiches.isEmpty())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        avancer(500)
        assertEquals("retour sur l'accueil", 1, source.affiches.size)
    }

    // ------------------------------------------------------------------ outils

    private fun focusSur(tag: String) {
        compose.onAllNodes(
            (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        avancer(1000)
        val f = focus()
        if (f == null || tag !in f.chaine) fail("[${design.name}] focus attendu dans « $tag », obtenu ${decrire(f)}")
    }

    /**
     * QR code de la capture : au moins 90 u de côté, décodé tel quel puis réduit à 70 % (vu de
     * loin : en PETIT, environ 2,5 pixels de caméra par module, la limite sûre de zxing) ; il
     * doit donner l'URL d'appairage.
     */
    private fun verifierQr(capture: File, cas: String) {
        val b = bornesDe("telecommande:qr") ?: return fail("[${design.name}] $cas : QR code introuvable")
        val cote = minOf(b.width, b.height) / pxParU
        println("[${design.name}] $cas : QR code ${cote.roundToInt()} u (${b.enU()})")
        if (cote < 90f) fail("[${design.name}] $cas : QR code trop petit (${cote.roundToInt()} u, 90 au moins)")
        val image = BitmapFactory.decodeFile(capture.path) ?: return fail("capture illisible : $capture")
        val m = (6 * pxParU).toInt()
        val x0 = (b.left.toInt() - m).coerceAtLeast(0)
        val y0 = (b.top.toInt() - m).coerceAtLeast(0)
        val x1 = (b.right.toInt() + m).coerceAtMost(image.width)
        val y1 = (b.bottom.toInt() + m).coerceAtMost(image.height)
        val morceau = Bitmap.createBitmap(image, x0, y0, x1 - x0, y1 - y0)
        assertEquals("[${design.name}] $cas : QR code décodé", FacticesTelecommande.url, decoder(morceau))
        val loin = Bitmap.createScaledBitmap(morceau, (morceau.width * 0.7f).toInt(), (morceau.height * 0.7f).toInt(), true)
        assertEquals("[${design.name}] $cas : QR code réduit à 70 % (vu de loin)", FacticesTelecommande.url, decoder(loin))
    }

    private fun decoder(image: Bitmap): String? {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (e: Exception) {
            null
        }
    }

    private fun SemanticsNode.bornesCompletes(): Rect {
        val c = layoutInfo.coordinates
        return c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
    }

    /** Rien n'est coupé : zone dans l'écran, module dans la zone, textes entiers et dans le module. */
    private fun controler(etiquette: String) {
        val racine = compose.onRoot().fetchSemanticsNode().size
        val zone = compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] zone de modules introuvable")
        val z = zone.bornesCompletes()
        if (z.left < 0f || z.top < 0f || z.right > racine.width + 0.5f || z.bottom > racine.height + 0.5f) {
            fail("[${design.name}] zone de modules hors de l'écran : ${z.enU()}")
        }
        val module = compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] $etiquette introuvable")
        val m = module.bornesCompletes()
        val tol = 1.5f
        if (m.left < z.left - tol || m.top < z.top - tol || m.right > z.right + tol || m.bottom > z.bottom + tol) {
            fail("[${design.name}] $etiquette (${m.enU()}) dépasse de la zone (${z.enU()})")
        }
        val erreurs = mutableListOf<String>()
        val textes = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(etiquette)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        for (n in textes) {
            val texte = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "?"
            val resultats = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultats)
            val r = resultats.firstOrNull()
            if (r != null) {
                val ellipse = (0 until r.lineCount).any { r.isLineEllipsized(it) }
                if (r.hasVisualOverflow || ellipse) {
                    erreurs += "« $texte » coupé (texte ${r.size.width} × ${r.size.height} px, " +
                        "contraintes ${r.layoutInput.constraints}, débord hauteur ${r.didOverflowHeight}, " +
                        "largeur ${r.didOverflowWidth}, points de suspension $ellipse)"
                }
            }
            val b = n.bornesCompletes()
            if (b.left < m.left - tol || b.right > m.right + tol || b.top < m.top - tol || b.bottom > m.bottom + tol) {
                erreurs += "« $texte » (${b.enU()}) hors du module (${m.enU()})"
            }
        }
        // Le cadre du QR code aussi
        bornesDe("telecommande:qr")?.let { q ->
            if (etiquette == "module:telecommande" &&
                (q.left < m.left - tol || q.right > m.right + tol || q.top < m.top - tol || q.bottom > m.bottom + tol)
            ) erreurs += "QR code (${q.enU()}) hors du module (${m.enU()})"
        }
        if (textes.isEmpty()) erreurs += "aucun texte trouvé"
        if (erreurs.isNotEmpty()) fail("[${design.name}] $etiquette : " + erreurs.joinToString(" ; "))
        println("[${design.name}] $etiquette : ${textes.size} textes entiers, module ${m.enU()}")
    }
}
