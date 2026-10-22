package fr.projo.launcher.ambiance

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Choix de la source du néon et repli : son en direct (Visualizer), sinon couleurs de la
 * pochette (pulsation), sinon animation. Analyse du spectre, détection du son « muet »,
 * couleur tirée d'une pochette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceAmbianceTest {

    // ------------------------------------------------------------------ choix de la source

    @Test
    fun sonEnDirectEnPremier() {
        assertEquals(SourceAmbiance.SON, sourceNeon(sonDirect = true, teinte = Teinte.POCHETTE))
        assertEquals(SourceAmbiance.SON, sourceNeon(sonDirect = true, teinte = Teinte.ARC_EN_CIEL))
    }

    @Test
    fun repliSurLaPochettePuisSurLAnimation() {
        // Sans son en direct, avec la pochette : pulsation aux couleurs de la pochette
        assertEquals(SourceAmbiance.POCHETTE, sourceNeon(false, teinteNeon(CouleursNeon.POCHETTE, pochette = true)))
        // Sans son ni pochette : animation à la couleur de l'app
        assertEquals(Teinte.ICONE, teinteNeon(CouleursNeon.POCHETTE, pochette = false))
        assertEquals(SourceAmbiance.ANIMATION, sourceNeon(false, teinteNeon(CouleursNeon.POCHETTE, pochette = false)))
        // Arc-en-ciel choisi : jamais la pochette
        assertEquals(Teinte.ARC_EN_CIEL, teinteNeon(CouleursNeon.ARC_EN_CIEL, pochette = true))
        assertEquals(SourceAmbiance.ANIMATION, sourceNeon(false, Teinte.ARC_EN_CIEL))
    }

    @Test
    fun sonMuetPendantLaLecturePasseAuRepli() {
        val d = DetecteurMuet()
        // Musique en pause : le silence est normal, pas de repli
        repeat(300) { d.suivre(silence = true, musiqueActive = false, dt = 1 / 60f) }
        assertFalse(d.muet)
        // De la musique joue mais le Visualizer ne reçoit rien (son « offload ») : repli après 3 s
        repeat(120) { d.suivre(silence = true, musiqueActive = true, dt = 1 / 60f) }
        assertFalse("pas avant 3 s", d.muet)
        repeat(80) { d.suivre(silence = true, musiqueActive = true, dt = 1 / 60f) }
        assertTrue(d.muet)
        // Le son revient : retour au son en direct
        repeat(20) { d.suivre(silence = false, musiqueActive = true, dt = 1 / 60f) }
        assertFalse(d.muet)
    }

    @Test
    fun diagnosticLisible() {
        assertEquals(
            "son en direct · couleur de la pochette",
            CadreAmbiance.texteDiagnostic(SourceAmbiance.SON, Teinte.POCHETTE, CaptureSon.Etat.ACTIVE, false, LecteurPochette.Etat.OK),
        )
        assertEquals(
            "couleurs de la pochette · son muet (lecture directe ?)",
            CadreAmbiance.texteDiagnostic(SourceAmbiance.POCHETTE, Teinte.POCHETTE, CaptureSon.Etat.ACTIVE, true, LecteurPochette.Etat.OK),
        )
        assertEquals(
            "animation · couleur de l'app · micro non autorisé · pochette : accès aux notifications à activer",
            CadreAmbiance.texteDiagnostic(SourceAmbiance.ANIMATION, Teinte.ICONE, CaptureSon.Etat.REFUSEE, false, LecteurPochette.Etat.SANS_ACCES),
        )
    }

    // ------------------------------------------------------------------ analyse du spectre

    private val taille = 1024
    private val frequence = 44_100

    /** FFT factice : un léger bruit partout, et un pic par fréquence donnée (Hz → amplitude). */
    private fun fft(vararg pics: Pair<Float, Int>): ByteArray {
        val b = ByteArray(taille)
        for (k in 1 until taille / 2) b[2 * k] = 3
        for ((f, a) in pics) {
            val k = (f / (frequence.toFloat() / taille)).toInt().coerceIn(1, taille / 2 - 1)
            b[2 * k] = a.toByte()
            b[2 * k + 1] = (a / 2).toByte()
        }
        return b
    }

    @Test
    fun bassesEnBasAigusEnHaut() {
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        // Après quelques images de bruit, un pic à 60 Hz : bandes basses au maximum
        repeat(30) { a.analyser(fft(), 1 / 60f) }
        a.analyser(fft(60f to 120, 90f to 120), 1 / 60f)
        assertTrue("basses : ${a.niveaux.toList()}", a.niveaux[0] > 0.8f || a.niveaux[1] > 0.8f)
        assertTrue("aigus calmes : ${a.niveaux.toList()}", a.niveaux[11] < 0.5f)
        // Pic à 12 kHz : dernières bandes
        val b = AnalyseSpectre()
        b.configurer(taille, frequence)
        repeat(30) { b.analyser(fft(), 1 / 60f) }
        b.analyser(fft(12_000f to 125, 13_000f to 125, 14_000f to 125), 1 / 60f)
        assertTrue("aigus : ${b.niveaux.toList()}", b.niveaux[10] > 0.8f || b.niveaux[11] > 0.8f)
    }

    @Test
    fun silenceEtAttaques() {
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        a.analyser(ByteArray(taille), 1 / 60f)
        assertTrue(a.silence)
        assertEquals(0f, a.niveaux.max(), 0f)
        // Basse régulière, puis un coup de grosse caisse : une attaque
        repeat(40) { a.analyser(fft(60f to 20), 1 / 60f) }
        assertEquals(0f, a.attaque, 0f)
        a.analyser(fft(60f to 120), 1 / 60f)
        assertTrue("attaque attendue : ${a.attaque}", a.attaque >= 0.6f)
        // Juste après : pas de deuxième attaque
        a.analyser(fft(60f to 125), 1 / 60f)
        assertEquals(0f, a.attaque, 0f)
    }

    // ------------------------------------------------------------------ moteur

    @Test
    fun attaqueRapideRetombeeLenteEtPasDeFauxSursaut() {
        val m = MoteurNeon()
        m.reagitAuSon = true
        m.cibles.fill(1f)
        repeat(6) { m.avancer(1 / 60f) }
        assertTrue("attaque rapide : ${m.enveloppes[0]}", m.enveloppes[0] > 0.95f)
        m.cibles.fill(0f)
        repeat(6) { m.avancer(1 / 60f) }
        assertTrue("retombée lente : ${m.enveloppes[0]}", m.enveloppes[0] > 0.6f)
        m.sursauter(1f)
        assertTrue(m.sursaut > 0.8f)
        repeat(20) { m.avancer(1 / 60f) }
        assertTrue("retombée du sursaut en ~0,3 s : ${m.sursaut}", m.sursaut < 0.1f)
        // Sans le son : pulsation, aucun sursaut
        val p = MoteurNeon()
        p.reagitAuSon = false
        repeat(300) { p.avancer(1 / 60f) }
        assertEquals(0f, p.sursaut, 0f)
        assertTrue(p.enveloppes[0] in 0.25f..0.6f)
    }

    @Test
    fun sursautsPlusPetitsEnDouce() {
        val m = MoteurNeon()
        m.intensite = Intensite.DOUCE
        m.sursauter(1f)
        assertTrue(m.sursaut <= 0.5f)
    }

    // ------------------------------------------------------------------ couleur de la pochette

    @Test
    fun couleurDominanteSaturee() {
        val rose = Color.rgb(214, 70, 140)
        val bleuNuit = Color.rgb(30, 20, 60)
        val gris = Color.rgb(120, 120, 120)
        val c = LecteurPochette.couleurNeon(intArrayOf(bleuNuit, rose, gris), intArrayOf(100, 60, 80))!!
        val hsv = FloatArray(3)
        Color.colorToHSV(c or (0xFF shl 24), hsv)
        assertTrue("teinte rose attendue : ${Integer.toHexString(c)}", hsv[0] in 300f..345f)
        assertTrue("néon lumineux", hsv[2] >= 0.9f)
        // Pochette en noir et blanc : blanc doux
        assertEquals(0xFFF2E6, LecteurPochette.couleurNeon(intArrayOf(gris, Color.BLACK), intArrayOf(100, 90)))
    }

    @Test
    fun emballageDesCouleurs() {
        assertEquals(0, DessinNeon.emballer(0f, 0f, 0f))
        // Lumière faible : opacité faible (n'efface pas l'app sous le halo)
        val c = DessinNeon.emballer(0.2f, 0.1f, 0f)
        assertEquals(51, c ushr 24)
        assertEquals(255, (c shr 16) and 0xFF)
        // Trop fort : saturé, sans erreur
        assertEquals(255, DessinNeon.emballer(3f, 2f, 1f) ushr 24)
    }
}
