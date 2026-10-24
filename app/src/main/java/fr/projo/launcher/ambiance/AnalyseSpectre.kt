package fr.projo.launcher.ambiance

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * Analyse de l'onde du Visualizer (octets non signés, 128 = zéro) : spectre calculé ici
 * (FFT en virgule flottante), énergie de 12 bandes de 40 Hz à 16 kHz (échelle
 * logarithmique) avec gain automatique par bande, et détection des coups (« sursauts »).
 *
 * Pourquoi l'onde et pas getFft : la FFT d'Android rend des octets « repliés » (une valeur
 * trop grande est divisée par 2 jusqu'à tenir) : une grosse caisse forte y paraît aussi
 * faible qu'une basse lointaine, et les sursauts tombaient au hasard.
 *
 * Coups : montée soudaine de l'énergie des basses (grosse caisse, 35-150 Hz) d'une image à
 * l'autre, au-dessus d'un seuil qui s'adapte à la musique. Le tempo est cherché en continu
 * (autocorrélation des 6 dernières secondes) : une fois trouvé, un coup sur le temps passe
 * facilement, un contretemps un peu moins, et un coup hors du rythme doit être très fort.
 * Aucune allocation par image. Rien n'est gardé : seulement des niveaux d'énergie.
 */
class AnalyseSpectre {

    /** Niveau de chaque bande (0 à 1) après le gain automatique. */
    val niveaux = FloatArray(NB_BANDES)

    /** Force du coup détecté à cette image (0 : aucun, sinon 0,6 à 1). */
    var attaque = 0f
        private set

    /** Onde plate (rien ne sort, ou son hors d'atteinte). */
    var silence = true
        private set

    /** Durée d'un temps (s) quand le tempo est trouvé, 0 sinon (journal et tests). */
    val periode: Float get() = if (confiance > 0f) duree else 0f

    private val debut = IntArray(NB_BANDES)
    private val fin = IntArray(NB_BANDES)
    private val db = FloatArray(NB_BANDES)
    private val reference = FloatArray(NB_BANDES) { REFERENCE_MIN }
    private var taille = 0

    // FFT : tableaux préalloués (taille de la capture, puissance de 2)
    private var re = FloatArray(0)
    private var im = FloatArray(0)
    private var cosinus = FloatArray(0)
    private var sinus = FloatArray(0)
    private var inverse = IntArray(0)
    private var basseDebut = 1
    private var basseFin = 2

    // Coups : énergie des basses, seuil adaptatif
    private var precedente = Float.NaN
    private var maxBasse = 0f
    private var moyenne = 0f
    private var ecart = 0f
    private var horloge = 0f
    private var dernierCoup = -10f
    private var dernierTemps = -10f

    // Tempo : historique des montées sur une grille régulière (1/30 s)
    private val historique = FloatArray(NB_HISTORIQUE)
    private val serie = FloatArray(NB_HISTORIQUE)
    private var caseCourante = -1
    private var duree = 0f
    private var confiance = 0f
    private var prochain = -1f
    private var dernierCalcul = 0f

    /** À appeler une fois : taille de la capture (octets) et fréquence d'échantillonnage (Hz). */
    fun configurer(tailleCapture: Int, frequenceHz: Int) {
        var n = 1
        while (n * 2 <= tailleCapture) n *= 2
        taille = n
        val moitie = n / 2
        val pas = frequenceHz.toFloat() / n
        for (b in 0 until NB_BANDES) {
            val f0 = F_MIN * (F_MAX / F_MIN).pow(b.toFloat() / NB_BANDES)
            val f1 = F_MIN * (F_MAX / F_MIN).pow((b + 1f) / NB_BANDES)
            val k0 = floor(f0 / pas).toInt().coerceIn(1, moitie - 1)
            val k1 = ceil(f1 / pas).toInt().coerceIn(k0 + 1, moitie)
            debut[b] = k0
            fin[b] = k1
        }
        basseDebut = floor(BASSE_MIN / pas).toInt().coerceIn(1, moitie - 1)
        basseFin = ceil(BASSE_MAX / pas).toInt().coerceIn(basseDebut + 1, moitie)
        if (re.size != n) {
            re = FloatArray(n)
            im = FloatArray(n)
            cosinus = FloatArray(moitie) { cos(2.0 * PI * it / n).toFloat() }
            sinus = FloatArray(moitie) { -sin(2.0 * PI * it / n).toFloat() }
            var bits = 0
            while ((1 shl bits) < n) bits++
            inverse = IntArray(n) { Integer.reverse(it) ushr (32 - bits) }
        }
        reinitialiser()
    }

    fun reinitialiser() {
        niveaux.fill(0f)
        reference.fill(REFERENCE_MIN)
        attaque = 0f
        silence = true
        precedente = Float.NaN
        maxBasse = 0f
        moyenne = 0f
        ecart = 0f
        horloge = 0f
        dernierCoup = -10f
        dernierTemps = -10f
        historique.fill(0f)
        caseCourante = -1
        duree = 0f
        confiance = 0f
        prochain = -1f
        dernierCalcul = 0f
    }

    fun analyser(onde: ByteArray, dt: Float) {
        attaque = 0f
        horloge += dt
        val n = taille
        if (n == 0 || onde.size < n) return
        // Onde centrée (128 = zéro ; la moyenne est retirée par sûreté)
        var somme = 0f
        for (i in 0 until n) somme += (onde[i].toInt() and 0xFF)
        val milieu = somme / n
        var crete = 0f
        for (i in 0 until n) {
            val v = (onde[i].toInt() and 0xFF) - milieu
            if (abs(v) > crete) crete = abs(v)
            re[inverse[i]] = v
            im[inverse[i]] = 0f
        }
        silence = crete <= 1.5f
        if (silence) {
            niveaux.fill(0f)
            precedente = Float.NaN
            avancerGrille(0f)
            return
        }
        fft(n)

        // Bandes : spectre fenêtré (Hann, obtenu par voisinage), amplitude d'un sinus ≈ A
        val echelle = 4f / n
        val baisse = BAISSE_REFERENCE * dt
        for (b in 0 until NB_BANDES) {
            var e = 0f
            for (k in debut[b] until fin[b]) {
                val hr = 0.5f * re[k] - 0.25f * (re[k - 1] + re[k + 1])
                val hi = 0.5f * im[k] - 0.25f * (im[k - 1] + im[k + 1])
                e += (hr * hr + hi * hi) * echelle * echelle
            }
            db[b] = 10f * log10(e / (fin[b] - debut[b]) + 0.001f)
            reference[b] = max(db[b], reference[b] - baisse).coerceAtLeast(REFERENCE_MIN)
            niveaux[b] = ((db[b] - (reference[b] - PLAGE)) / PLAGE).coerceIn(0f, 1f)
        }

        // Basses sur toute la fenêtre (sans pondération) : plus stable d'une image à l'autre
        var eb = 0f
        for (k in basseDebut until basseFin) eb += re[k] * re[k] + im[k] * im[k]
        val basse = 10f * log10(eb + 1f)
        maxBasse = max(basse, maxBasse - BAISSE_REFERENCE * dt)
        val prec = precedente
        precedente = basse
        if (prec.isNaN()) { avancerGrille(0f); return }
        val montee = max(0f, basse - prec)
        avancerGrille(montee)

        val seuil = moyenne + K_SEUIL * ecart + PLANCHER
        // Basse proche de son maximum récent, et pas un simple bruit
        val forte = basse > maxBasse - 12f && basse > BASSE_MIN_DB
        val depuis = horloge - dernierCoup
        val p = duree
        if (confiance > 0f && prochain > 0f) {
            var decale = horloge - prochain
            if (decale > p * 0.5f) {
                // Temps passé sans coup : on avance la grille
                prochain += p * floor(decale / p + 0.5f)
                decale = horloge - prochain
            }
            val surLeTemps = abs(decale) < FENETRE
            val contretemps = abs(abs(decale) - p * 0.5f) < FENETRE
            if (surLeTemps && forte && montee > seuil * 0.6f && horloge - dernierTemps > p * 0.5f && depuis > 0.12f) {
                frapper(montee, seuil)
                // La phase se recale sur le coup entendu
                prochain = horloge + p
                dernierTemps = horloge
            } else if (contretemps && forte && montee > seuil * 1.2f && depuis > max(ECART_MIN, p * 0.4f)) {
                frapper(montee, seuil)
            } else if (!surLeTemps && !contretemps && forte && montee > seuil * 1.8f && depuis > max(ECART_MIN, p * 0.45f)) {
                frapper(montee, seuil)
            }
        } else if (forte && montee > seuil && depuis > ECART_MIN) {
            frapper(montee, seuil)
        }

        // Seuil mis à jour APRÈS la décision (un coup ne relève pas son propre seuil)
        val k = 1f - exp(-dt / TAU_SEUIL)
        moyenne += (montee - moyenne) * k
        ecart += (abs(montee - moyenne) - ecart) * k
        if (horloge - dernierCalcul > 0.5f) {
            dernierCalcul = horloge
            estimerTempo()
        }
    }

    private fun frapper(montee: Float, seuil: Float) {
        attaque = (0.6f + 0.4f * (montee - seuil) / (seuil + 3f)).coerceIn(0.6f, 1f)
        dernierCoup = horloge
    }

    /** Range la montée de cette image dans la grille régulière (cases sautées : 0). */
    private fun avancerGrille(montee: Float) {
        val c = (horloge / GRILLE).toInt()
        if (c != caseCourante) {
            var j = caseCourante + 1
            if (c - j >= NB_HISTORIQUE) j = c - NB_HISTORIQUE + 1
            while (j <= c) { historique[j.mod(NB_HISTORIQUE)] = 0f; j++ }
            caseCourante = c
        }
        val i = c.mod(NB_HISTORIQUE)
        if (montee > historique[i]) historique[i] = montee
    }

    private fun correlation(decalage: Int): Float {
        val n = NB_HISTORIQUE
        var s = 0f
        for (i in 0 until n - decalage) s += serie[i + decalage] * serie[i]
        return s / (n - decalage) * n
    }

    /** Tempo (60 à 180 BPM) et position des temps, d'après l'historique. */
    private fun estimerTempo() {
        val n = NB_HISTORIQUE
        val c = caseCourante
        var total = 0f
        for (i in 0 until n) {
            serie[i] = historique[(c - n + 1 + i).mod(n)]
            total += serie[i]
        }
        if (total < 1e-6f) { confiance = 0f; return }
        val moy = total / n
        var r0 = 0f
        for (i in 0 until n) { serie[i] -= moy; r0 += serie[i] * serie[i] }
        if (r0 < 1e-6f) { confiance = 0f; return }
        var meilleur = 0f
        var lag = 0
        for (l in LAG_MIN..LAG_MAX) {
            var r = correlation(l)
            // Le double du temps renforce le vrai temps (et pas ses subdivisions)
            if (2 * l < n - 20) r += 0.5f * correlation(2 * l)
            // Légère préférence autour de 120 BPM
            val x = log2(l * GRILLE / 0.5f) / 0.9f
            val v = r / r0 * exp(-0.5f * x * x)
            if (v > meilleur) { meilleur = v; lag = l }
        }
        if (lag == 0 || meilleur < CONFIANCE_MIN) { confiance = 0f; return }
        // Affinage entre deux cases (parabole)
        val a = correlation(lag - 1)
        val b = correlation(lag)
        val d = correlation(lag + 1)
        val den = a - 2f * b + d
        val delta = if (den != 0f) ((a - d) / (2f * den)).coerceIn(-0.5f, 0.5f) else 0f
        val per = (lag + delta) * GRILLE
        duree = per
        confiance = meilleur
        // Phase : la position qui aligne le mieux les derniers coups sur la grille
        val pas = (per / GRILLE).roundToInt().coerceAtLeast(1)
        var meilleurePhase = 0
        var meilleureSomme = -1f
        for (ph in 0 until pas) {
            var s = 0f
            for (k in 0 until 6) s += historique[(c - ph - k * pas).mod(n)]
            if (s > meilleureSomme) { meilleureSomme = s; meilleurePhase = ph }
        }
        val dernier = (c - meilleurePhase) * GRILLE + GRILLE / 2f
        val ecartPhase = (prochain - dernier) / per
        if (prochain < 0f || abs(ecartPhase - ecartPhase.roundToInt()) > 0.25f) {
            prochain = dernier + per * ceil((horloge - dernier) / per + 0.01f)
        }
    }

    /** FFT complexe en place (re, im déjà rangés dans l'ordre inversé des bits). */
    private fun fft(n: Int) {
        var longueur = 2
        while (longueur <= n) {
            val moitie = longueur / 2
            val pas = n / longueur
            var debutBloc = 0
            while (debutBloc < n) {
                for (j in 0 until moitie) {
                    val c = cosinus[j * pas]
                    val s = sinus[j * pas]
                    val i1 = debutBloc + j
                    val i2 = i1 + moitie
                    val tr = re[i2] * c - im[i2] * s
                    val ti = re[i2] * s + im[i2] * c
                    re[i2] = re[i1] - tr
                    im[i2] = im[i1] - ti
                    re[i1] += tr
                    im[i1] += ti
                }
                debutBloc += longueur
            }
            longueur *= 2
        }
    }

    companion object {
        const val NB_BANDES = 12
        const val F_MIN = 40f
        const val F_MAX = 16_000f

        /** Plage utile sous le maximum récent de chaque bande (dB). */
        private const val PLAGE = 18f

        /** Le maximum récent redescend de 8 dB par seconde. */
        private const val BAISSE_REFERENCE = 8f

        /** Maximum de référence minimal (dB, amplitude d'un sinus ≈ 1) : le bruit reste éteint. */
        private const val REFERENCE_MIN = 0f

        /** Grosse caisse et basse. */
        private const val BASSE_MIN = 35f
        private const val BASSE_MAX = 150f

        /** Énergie des basses minimale pour un coup (dB ; bruit de quantification ≈ 35). */
        private const val BASSE_MIN_DB = 55f

        /** Seuil = moyenne récente des montées + K × écart moyen + plancher (dB). */
        private const val K_SEUIL = 2f
        private const val PLANCHER = 4f
        private const val TAU_SEUIL = 1.5f

        /** Jamais deux sursauts à moins de 0,2 s. */
        private const val ECART_MIN = 0.2f

        /** Tolérance autour du temps attendu (s). */
        private const val FENETRE = 0.07f

        /** Grille du tempo : 1/30 s, 192 cases (6,4 s), de 60 à 180 BPM. */
        private const val GRILLE = 1f / 30f
        private const val NB_HISTORIQUE = 192
        private const val LAG_MIN = 10
        private const val LAG_MAX = 30
        private const val CONFIANCE_MIN = 0.18f
    }
}

/**
 * Retard du néon sur le son. Le son analysé ici sort de la TV ou du projecteur un peu plus
 * tard (sortie HDMI, traitement du son) : sans retard, le néon s'allumerait juste AVANT le
 * temps fort entendu. Les mesures sont gardées [decalage] secondes avant d'être montrées.
 * Tableaux préalloués : aucune allocation par image.
 */
class RetardSon(private val capacite: Int = 64) {
    private val temps = FloatArray(capacite)
    private val attaques = FloatArray(capacite)
    private val niveaux = Array(capacite) { FloatArray(AnalyseSpectre.NB_BANDES) }
    private var tete = 0
    private var nb = 0

    fun vider() {
        nb = 0
    }

    /** Mesure prise à l'instant [t] (s). Plein : la plus ancienne est remplacée. */
    fun ajouter(t: Float, n: FloatArray, attaque: Float) {
        if (nb == capacite) nb--
        val i = tete
        temps[i] = t
        attaques[i] = attaque
        System.arraycopy(n, 0, niveaux[i], 0, AnalyseSpectre.NB_BANDES)
        tete = (tete + 1) % capacite
        nb++
    }

    /**
     * Mesures à montrer à l'instant [t] : celles prises avant t − [decalage]. Copie les
     * niveaux les plus récents dans [sortie] et rend la plus forte attaque (0 si aucune,
     * −1 si rien n'est encore dû : [sortie] n'est alors pas touchée).
     */
    fun extraire(t: Float, decalage: Float, sortie: FloatArray): Float {
        var att = -1f
        while (nb > 0) {
            val i = (tete - nb + capacite) % capacite
            if (temps[i] > t - decalage) break
            System.arraycopy(niveaux[i], 0, sortie, 0, AnalyseSpectre.NB_BANDES)
            att = max(att, attaques[i])
            nb--
        }
        return att
    }
}
