package fr.projo.launcher.ambiance

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ce qui change à chaque image : énergie de chaque bande (attaque rapide, retombée lente),
 * sursaut, temps, couleur de base (fondu quand le morceau change). Sans Android, aucune
 * allocation par image. Le dessin (DessinNeon) lit ces valeurs.
 */
class MoteurNeon {

    var intensite = Intensite.NORMALE
    var teinte = Teinte.ICONE

    /** true : [cibles] viennent du son en direct ; false : pulsation douce et régulière. */
    var reagitAuSon = false

    /** Niveaux mesurés des bandes (0 à 1), à remplir avant [avancer] quand [reagitAuSon]. */
    val cibles = FloatArray(AnalyseSpectre.NB_BANDES)

    /** Niveaux lissés des bandes (0 à 1). */
    val enveloppes = FloatArray(AnalyseSpectre.NB_BANDES)

    /** Sursaut en cours (0 à 1, déjà réduit selon l'intensité), retombe en ~0,3 s. */
    var sursaut = 0f
        private set

    /** Temps écoulé (s), pour les vagues et la pulsation. */
    var temps = 0f
        private set

    /** Couleur de base actuelle (0 à 1), fondu compris. Par défaut : vert Spotify. */
    var rouge = 0.11f
        private set
    var vert = 0.73f
        private set
    var bleu = 0.33f
        private set
    private var r0 = rouge
    private var v0 = vert
    private var b0 = bleu
    private var r1 = rouge
    private var v1 = vert
    private var b1 = bleu
    private var fondu = 1f
    private var cible = COULEUR_PAR_DEFAUT

    /** Nouvelle couleur de base (pochette, icône) : fondu doux de 1,5 s, ou immédiat. */
    fun changerCouleur(rgb: Int, immediat: Boolean = false) {
        if (rgb == cible && !immediat) return
        cible = rgb
        r0 = rouge; v0 = vert; b0 = bleu
        r1 = ((rgb shr 16) and 0xFF) / 255f
        v1 = ((rgb shr 8) and 0xFF) / 255f
        b1 = (rgb and 0xFF) / 255f
        fondu = if (immediat) 1f else 0f
        if (immediat) { rouge = r1; vert = v1; bleu = b1 }
    }

    /** Attaque détectée dans le son : sursaut immédiat (plus petit en « Douce »). */
    fun sursauter(force: Float) {
        val f = force.coerceIn(0f, 1f) * intensite.sursaut
        if (f > sursaut) sursaut = f
    }

    /** Remet l'animation au repos (néon qui se rallume). */
    fun reinitialiser() {
        cibles.fill(0f)
        enveloppes.fill(0f)
        sursaut = 0f
    }

    fun avancer(dt: Float) {
        val pas = dt.coerceIn(0f, 0.1f)
        temps += pas
        // Énergie : attaque rapide (25 ms), retombée lente (350 ms)
        val ka = 1f - exp(-pas / 0.025f)
        val kr = 1f - exp(-pas / 0.35f)
        val pulsation = if (reagitAuSon) 0f else pulsation()
        for (b in enveloppes.indices) {
            val x = if (reagitAuSon) cibles[b] else pulsation
            val e = enveloppes[b]
            enveloppes[b] = e + (x - e) * (if (x > e) ka else kr)
        }
        // Sursaut : retombée en ~0,3 s ; jamais de faux sursaut sans le son
        sursaut = if (reagitAuSon) sursaut * exp(-pas / 0.11f) else max(0f, sursaut - pas * 4f)
        if (fondu < 1f) {
            fondu = min(1f, fondu + pas / 1.5f)
            val f = fondu * fondu * (3f - 2f * fondu)
            rouge = r0 + (r1 - r0) * f
            vert = v0 + (v1 - v0) * f
            bleu = b0 + (b1 - b0) * f
        }
    }

    /** Pulsation douce et régulière (une respiration toutes les 4,5 s). */
    private fun pulsation(): Float = 0.3f + 0.24f * (0.5f + 0.5f * sin(2f * PI.toFloat() * temps / 4.5f))

    companion object {
        /** Vert Spotify : couleur tant qu'on n'a ni pochette ni icône. */
        const val COULEUR_PAR_DEFAUT = 0x1DB954
    }
}
