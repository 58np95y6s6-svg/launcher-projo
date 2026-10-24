package fr.projo.launcher.ambiance

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

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
    private val alea = java.util.Random(7)

    /** Onde du Visualizer (octets non signés, 128 = zéro) : léger bruit, et des sinus (Hz → amplitude). */
    private fun onde(vararg pics: Pair<Float, Int>): ByteArray {
        val b = ByteArray(taille)
        for (i in 0 until taille) {
            var v = (alea.nextInt(3) - 1).toDouble()
            for ((f, a) in pics) v += a * sin(2 * PI * f * i / frequence)
            b[i] = (128 + v.roundToInt()).coerceIn(0, 255).toByte()
        }
        return b
    }

    @Test
    fun bassesEnBasAigusEnHaut() {
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        // Après quelques images de bruit, un pic à 60 Hz : bandes basses au maximum
        repeat(30) { a.analyser(onde(), 1 / 30f) }
        a.analyser(onde(60f to 60, 90f to 60), 1 / 30f)
        assertTrue("basses : ${a.niveaux.toList()}", a.niveaux[0] > 0.8f || a.niveaux[1] > 0.8f)
        assertTrue("aigus calmes : ${a.niveaux.toList()}", a.niveaux[11] < 0.5f)
        // Pic à 12 kHz : dernières bandes
        val b = AnalyseSpectre()
        b.configurer(taille, frequence)
        repeat(30) { b.analyser(onde(), 1 / 30f) }
        b.analyser(onde(12_000f to 40, 13_000f to 40, 14_000f to 40), 1 / 30f)
        assertTrue("aigus : ${b.niveaux.toList()}", b.niveaux[10] > 0.8f || b.niveaux[11] > 0.8f)
    }

    @Test
    fun silenceEtAttaques() {
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        // Onde plate (128 partout) : silence
        a.analyser(ByteArray(taille) { 0x80.toByte() }, 1 / 30f)
        assertTrue(a.silence)
        assertEquals(0f, a.niveaux.max(), 0f)
        // Basse régulière, puis un coup de grosse caisse : une attaque
        val douce = onde(60f to 20)
        repeat(40) { a.analyser(douce, 1 / 30f); assertEquals(0f, a.attaque, 0f) }
        a.analyser(onde(60f to 120), 1 / 30f)
        assertTrue("attaque attendue : ${a.attaque}", a.attaque >= 0.6f)
        // Juste après : pas de deuxième attaque
        a.analyser(onde(60f to 125), 1 / 30f)
        assertEquals(0f, a.attaque, 0f)
    }

    /**
     * Morceau de synthèse : grosse caisse sur chaque temps, basse en contretemps, caisse
     * claire, charleston, nappe. L'onde est lue comme sur le Fire TV : 1024 points toutes
     * les ~33 ms (avec un peu d'irrégularité), échelle « telle que jouée ».
     */
    private class Morceau(val bpm: Float, val duree: Float, val coups: FloatArray, val basse: Boolean = true, val batterie: Boolean = true) {
        val n = (duree * 44_100).toInt()
        val x = FloatArray(n)
        val temps = ArrayList<Float>()

        init {
            val r = java.util.Random(3)
            val t = 60f / bpm
            var mesure = 0
            while (mesure * 4 * t < duree) {
                for (c in coups) {
                    val t0 = (mesure * 4 + c) * t
                    if (t0 < duree) { ajouterGrosseCaisse(t0); temps.add(t0) }
                }
                if (batterie) for (c in floatArrayOf(1f, 3f)) ajouter((mesure * 4 + c) * t, 0.2f) { s, u -> 0.35f * ((r.nextGaussian() * 0.7).toFloat() + sin(2 * PI * 190 * u).toFloat()) * exp(-u / 0.06f) }
                if (batterie) for (e in 0 until 8) ajouter((mesure * 4 + e / 2f) * t, 0.05f) { _, u -> 0.12f * r.nextGaussian().toFloat() * exp(-u / 0.015f) }
                if (basse) {
                    val notes = floatArrayOf(55f, 55f, 65f, 73f, 82f, 73f, 65f, 49f)
                    for (e in 0 until 8) ajouter((mesure * 4 + e / 2f + 0.25f) * t, t / 2) { _, u ->
                        0.3f * sin(2 * PI * notes[e] * u).toFloat() * minOf(1f, u / 0.01f) * exp(-u / 0.4f)
                    }
                }
                mesure++
            }
            for (i in 0 until n) {
                val u = i / 44_100.0
                x[i] += (0.15 * (sin(2 * PI * 220 * u) + sin(2 * PI * 277 * u) + sin(2 * PI * 330 * u))).toFloat()
            }
        }

        private fun ajouterGrosseCaisse(t0: Float) {
            val i0 = (t0 * 44_100).toInt()
            var phase = 0.0
            for (j in 0 until 22_050) {
                if (i0 + j >= n) break
                val u = j / 44_100.0
                phase += 2 * PI * (50 + 70 * kotlin.math.exp(-u / 0.03)) / 44_100
                x[i0 + j] += (0.9 * sin(phase) * kotlin.math.exp(-u / 0.18)).toFloat()
            }
        }

        private fun ajouter(t0: Float, longueur: Float, son: (Int, Float) -> Float) {
            val i0 = (t0 * 44_100).toInt()
            for (j in 0 until (longueur * 44_100).toInt()) {
                if (i0 + j >= n) break
                x[i0 + j] += son(j, j / 44_100f)
            }
        }

        /** Coups détectés (instants) et analyse finale. */
        fun ecouter(a: AnalyseSpectre): List<Float> {
            val crete = x.maxOf { abs(it) }
            val b = ByteArray(1024)
            val r = java.util.Random(11)
            val vus = ArrayList<Float>()
            var t = 0f
            while (t < duree - 0.05f) {
                val dt = 1 / 30f + (r.nextFloat() - 0.5f) * 0.008f
                t += dt
                val fin = (t * 44_100).toInt()
                if (fin < 1024) continue
                for (i in 0 until 1024) b[i] = (128 + (x[fin - 1024 + i] / crete * 113).roundToInt()).coerceIn(0, 255).toByte()
                a.analyser(b, dt)
                if (a.attaque > 0f) vus.add(t)
            }
            return vus
        }
    }

    private fun bilan(m: Morceau, vus: List<Float>): Pair<Float, Int> {
        val trouves = m.temps.count { k -> vus.any { it - k in -0.01f..0.09f } }
        val faux = vus.count { v -> m.temps.none { v - it in -0.01f..0.09f } }
        return trouves.toFloat() / m.temps.size to faux
    }

    @Test
    fun lesSursautsSuiventLaGrosseCaisse() {
        for (bpm in floatArrayOf(90f, 120f, 128f)) {
            val m = Morceau(bpm, 30f, floatArrayOf(0f, 1f, 2f, 3f))
            val a = AnalyseSpectre()
            a.configurer(taille, frequence)
            val vus = m.ecouter(a)
            val (part, faux) = bilan(m, vus)
            assertTrue("$bpm BPM : ${(part * 100).toInt()} % des coups suivis", part >= 0.75f)
            assertTrue("$bpm BPM : $faux sursauts hors des coups sur ${vus.size}", faux <= 6)
            assertEquals("$bpm BPM : tempo trouvé", bpm, 60f / a.periode, 4f)
        }
    }

    @Test
    fun grosseCaisseUnTempsSurDeux() {
        val m = Morceau(120f, 30f, floatArrayOf(0f, 2f))
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        val vus = m.ecouter(a)
        val (part, faux) = bilan(m, vus)
        assertTrue("${(part * 100).toInt()} % des coups suivis", part >= 0.75f)
        assertTrue("$faux sursauts hors des coups sur ${vus.size}", faux <= 10)
    }

    @Test
    fun nappeSeuleAucunSursaut() {
        // Son tenu (nappe d'accords) : aucun coup
        val m = Morceau(120f, 15f, floatArrayOf(), basse = false, batterie = false)
        val a = AnalyseSpectre()
        a.configurer(taille, frequence)
        val vus = m.ecouter(a)
        assertEquals("sursauts sur une nappe seule : $vus", 0, vus.size)
    }

    @Test
    fun retardDuNeonSurLeSon() {
        val r = RetardSon()
        val sortie = FloatArray(AnalyseSpectre.NB_BANDES)
        val niveaux = FloatArray(AnalyseSpectre.NB_BANDES)
        niveaux.fill(0.7f)
        r.ajouter(1.0f, niveaux, 0.9f)
        // 0,1 s de retard : rien avant 1,1 s
        assertEquals(-1f, r.extraire(1.05f, 0.1f, sortie), 0f)
        assertEquals(0f, sortie[0], 0f)
        assertEquals(0.9f, r.extraire(1.15f, 0.1f, sortie), 0f)
        assertEquals(0.7f, sortie[0], 0f)
        // Déjà sorti : plus rien
        assertEquals(-1f, r.extraire(1.2f, 0.1f, sortie), 0f)
        // Sans retard : tout de suite
        r.ajouter(2f, niveaux, 0f)
        assertEquals(0f, r.extraire(2f, 0f, sortie), 0f)
        // Plein : les plus anciennes mesures sont remplacées, sans erreur
        repeat(200) { r.ajouter(3f + it / 30f, niveaux, 0f) }
        assertEquals(0f, r.extraire(100f, 0.1f, sortie), 0f)
    }

    @Test
    fun reglageSynchroEnregistre() {
        val c = org.robolectric.RuntimeEnvironment.getApplication()
        ModeAmbiance.oublier()
        assertEquals(100, ModeAmbiance.decalageMs)
        ModeAmbiance.ecrireDecalage(c, 300)
        ModeAmbiance.oublier()
        ModeAmbiance.charger(c)
        assertEquals(300, ModeAmbiance.decalageMs)
        ModeAmbiance.oublier()
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
