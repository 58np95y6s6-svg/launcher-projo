package fr.projo.launcher.ambiance

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Intensité du néon (réglage). [lumiere] : gain global ; [sursaut] : force des sursauts. */
enum class Intensite(val id: String, val nom: String, val lumiere: Float, val sursaut: Float) {
    DOUCE("douce", "Douce", 0.5f, 0.45f),
    NORMALE("normale", "Normale", 0.82f, 0.85f),
    FORTE("forte", "Forte", 1f, 1f);

    companion object {
        fun de(id: String?): Intensite = entries.firstOrNull { it.id == id } ?: NORMALE
    }
}

/** Couleurs du néon (réglage). */
enum class CouleursNeon(val id: String, val nom: String) {
    /** Couleur de la pochette du morceau (repli : couleur de l'icône de l'app). */
    POCHETTE("pochette", "Pochette"),

    /** Spectre : basses rouges en bas, aigus bleus et violets en haut. */
    ARC_EN_CIEL("arc", "Arc-en-ciel");

    companion object {
        fun de(id: String?): CouleursNeon = entries.firstOrNull { it.id == id } ?: POCHETTE
    }
}

/** Teinte effectivement affichée. */
enum class Teinte { POCHETTE, ICONE, ARC_EN_CIEL }

/** Ce qui fait vivre le néon (affiché dans Réglages pour le diagnostic). */
enum class SourceAmbiance(val libelle: String) {
    SON("son en direct"),
    POCHETTE("couleurs de la pochette"),
    ANIMATION("animation"),
}

/** Teinte selon le réglage : pochette si on l'a, sinon couleur de l'icône de l'app. */
fun teinteNeon(reglage: CouleursNeon, pochette: Boolean): Teinte = when {
    reglage == CouleursNeon.ARC_EN_CIEL -> Teinte.ARC_EN_CIEL
    pochette -> Teinte.POCHETTE
    else -> Teinte.ICONE
}

/**
 * Source : (a) le son en direct (Visualizer) s'il marche ; (b) sinon, pulsation douce aux
 * couleurs de la pochette ; (c) sinon, animation lente (couleur de l'app ou arc-en-ciel).
 */
fun sourceNeon(sonDirect: Boolean, teinte: Teinte): SourceAmbiance = when {
    sonDirect -> SourceAmbiance.SON
    teinte == Teinte.POCHETTE -> SourceAmbiance.POCHETTE
    else -> SourceAmbiance.ANIMATION
}

/**
 * Son « muet » alors que de la musique joue : le Visualizer ne reçoit rien (son
 * « offload » ou « tunnel », traité hors d'Android). Après [delai] secondes de silence
 * pendant la lecture, on passe au repli ; dès que du son revient, on y retourne.
 * Musique en pause : silence normal, pas de repli.
 */
class DetecteurMuet(private val delai: Float = 3f) {
    var muet = false
        private set
    private var silencieux = 0f
    private var sonore = 0f

    fun suivre(silence: Boolean, musiqueActive: Boolean, dt: Float) {
        when {
            !silence -> { sonore += dt; silencieux = 0f }
            musiqueActive -> { silencieux += dt; sonore = 0f }
            else -> { silencieux = 0f; sonore = 0f }
        }
        if (!muet && silencieux >= delai) muet = true
        if (muet && sonore >= 0.25f) muet = false
    }

    fun reinitialiser() {
        muet = false
        silencieux = 0f
        sonore = 0f
    }
}

/**
 * Analyse d'une FFT du Visualizer (octets : [Re0, Re(n/2), Re1, Im1, Re2, Im2…]) :
 * énergie de 12 bandes de 40 Hz à 16 kHz (échelle logarithmique), gain automatique par
 * bande, et détection des attaques (« sursauts ») surtout dans les basses.
 * Aucune allocation par image. Rien n'est gardé : seulement des niveaux d'énergie.
 */
class AnalyseSpectre {

    /** Niveau de chaque bande (0 à 1) après le gain automatique. */
    val niveaux = FloatArray(NB_BANDES)

    /** Force de l'attaque détectée à cette image (0 : aucune, sinon 0,4 à 1). */
    var attaque = 0f
        private set

    /** FFT nulle (rien ne sort, ou son hors d'atteinte). */
    var silence = true
        private set

    private val debut = IntArray(NB_BANDES)
    private val fin = IntArray(NB_BANDES)
    private val db = FloatArray(NB_BANDES)
    private val reference = FloatArray(NB_BANDES) { REFERENCE_MIN }
    private var taille = 0
    private var moyBasse = Float.NaN
    private var moyMilieu = Float.NaN
    private var precBasse = 0f
    private var depuisAttaque = 1f

    /** À appeler une fois : taille de la capture (octets) et fréquence d'échantillonnage (Hz). */
    fun configurer(tailleCapture: Int, frequenceHz: Int) {
        taille = tailleCapture
        val moitie = tailleCapture / 2
        val pas = frequenceHz.toFloat() / tailleCapture
        for (b in 0 until NB_BANDES) {
            val f0 = F_MIN * (F_MAX / F_MIN).pow(b.toFloat() / NB_BANDES)
            val f1 = F_MIN * (F_MAX / F_MIN).pow((b + 1f) / NB_BANDES)
            val k0 = floor(f0 / pas).toInt().coerceIn(1, moitie - 1)
            val k1 = ceil(f1 / pas).toInt().coerceIn(k0 + 1, moitie)
            debut[b] = k0
            fin[b] = k1
        }
        reinitialiser()
    }

    fun reinitialiser() {
        niveaux.fill(0f)
        reference.fill(REFERENCE_MIN)
        attaque = 0f
        silence = true
        moyBasse = Float.NaN
        moyMilieu = Float.NaN
        depuisAttaque = 1f
    }

    fun analyser(fft: ByteArray, dt: Float) {
        attaque = 0f
        depuisAttaque += dt
        if (taille == 0 || fft.size < taille) return
        var maximum = 0
        for (b in 0 until NB_BANDES) {
            var somme = 0f
            for (k in debut[b] until fin[b]) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                val m2 = re * re + im * im
                if (m2 > maximum) maximum = m2
                somme += m2
            }
            db[b] = 10f * log10(somme / (fin[b] - debut[b]) + 0.001f)
        }
        silence = maximum <= 2
        val baisse = BAISSE_REFERENCE * dt
        for (b in 0 until NB_BANDES) {
            reference[b] = max(db[b], reference[b] - baisse).coerceAtLeast(REFERENCE_MIN)
            niveaux[b] = if (silence) 0f else ((db[b] - (reference[b] - PLAGE)) / PLAGE).coerceIn(0f, 1f)
        }
        if (silence) return

        // Attaques : énergie des basses au-dessus de sa moyenne glissante, et qui monte
        val basse = max(db[0], max(db[1], db[2]))
        var milieu = 0f
        for (b in 3..7) milieu += db[b]
        milieu /= 5f
        if (moyBasse.isNaN()) { moyBasse = basse; moyMilieu = milieu }
        val ecart = basse - moyBasse
        if (basse > SEUIL_BASSE && ecart > 3.5f && basse > precBasse + 0.5f && depuisAttaque > 0.14f) {
            attaque = ((ecart - 3.5f) / 6f + 0.6f).coerceIn(0.6f, 1f)
            depuisAttaque = 0f
        } else if (milieu > SEUIL_BASSE - 6f && milieu - moyMilieu > 4.5f && depuisAttaque > 0.14f) {
            // Caisse claire, accents : sursaut plus petit
            attaque = 0.4f
            depuisAttaque = 0f
        }
        val k = 1f - exp(-dt / 0.45f)
        moyBasse += (basse - moyBasse) * k
        moyMilieu += (milieu - moyMilieu) * k
        precBasse = basse
    }

    companion object {
        const val NB_BANDES = 12
        const val F_MIN = 40f
        const val F_MAX = 16_000f

        /** Plage utile sous le maximum récent de chaque bande (dB). */
        private const val PLAGE = 18f

        /** Le maximum récent redescend de 8 dB par seconde. */
        private const val BAISSE_REFERENCE = 8f

        /** Maximum de référence minimal : un bruit faible ne remplit pas le néon. */
        private const val REFERENCE_MIN = 24f

        private const val SEUIL_BASSE = 18f
    }
}
