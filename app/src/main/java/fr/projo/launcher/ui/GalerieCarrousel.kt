package fr.projo.launcher.ui

/*
 * Carrousel du design « Galerie » : logique pure (sans Compose), testée par GalerieCarrouselTest.
 *
 * Chaque titre occupe une « place » de la composition selon son décalage par rapport au titre du
 * centre (0) ; ▶ fait glisser chaque titre vers la place précédente (le +1 vient au centre, le
 * centre rétrécit et monte en haut à gauche…). Mesures en px de l'écran de référence
 * 1920 × 1080 (maquette, objet PLACES).
 */

/** Une place de la composition : position, forme et filtre (classes CSS de la maquette). */
data class PlaceGalerie(
    val l: Float,
    val t: Float,
    val w: Float,
    val h: Float,
    val rayon: Float = 26f,
    /** Opacité (0 : place cachée, hors de l'écran). */
    val alpha: Float = 1f,
    /** 1 : affiche (portrait) à la place de l'image 16:9. */
    val affiche: Float = 0f,
    /** Flou imité : part de l'image adoucie dessinée sous l'image nette (0 → 1). */
    val flou: Float = 0f,
    val saturation: Float = 1f,
    /** 1 : noir et blanc. */
    val gris: Float = 0f,
    val contraste: Float = 1f,
    /** 1 : titre du centre (grande ombre, halo, voile et texte). */
    val heros: Float = 0f,
) {
    /** Position intermédiaire entre cette place et [b] ([p] : 0 → 1, déjà passé par la courbe). */
    fun vers(b: PlaceGalerie, p: Float): PlaceGalerie {
        if (p <= 0f) return this
        if (p >= 1f) return b
        fun m(x: Float, y: Float) = x + (y - x) * p
        return PlaceGalerie(
            m(l, b.l), m(t, b.t), m(w, b.w), m(h, b.h), m(rayon, b.rayon), m(alpha, b.alpha), m(affiche, b.affiche),
            m(flou, b.flou), m(saturation, b.saturation), m(gris, b.gris), m(contraste, b.contraste), m(heros, b.heros),
        )
    }
}

object CarrouselGalerie {
    /** Places extrêmes : cachées, hors de l'écran (sortie à gauche, entrée à droite). */
    const val MIN = -5
    const val MAX = 3

    /** Durée d'un glissement (ms) et sa courbe : cubic-bezier(.62,.04,.24,1). */
    const val DUREE = 900
    val COURBE = CourbeBezier(0.62f, 0.04f, 0.24f, 1f)

    /** Courbe « ease » des fondus (contenu du titre, synopsis). */
    val DOUCE = CourbeBezier(0.25f, 0.1f, 0.25f, 1f)

    /** Places par décalage (maquette PLACES). */
    val PLACES: Map<Int, PlaceGalerie> = mapOf(
        // Sortie à gauche (cachée), puis affiche coupée par le bord
        -5 to PlaceGalerie(-330f, 238f, 176f, 310f, alpha = 0f, affiche = 1f),
        -4 to PlaceGalerie(-76f, 238f, 176f, 310f, affiche = 1f),
        // En bas : noir et blanc, puis à gauche
        -3 to PlaceGalerie(378f, 528f, 200f, 206f, gris = 1f, contraste = 1.1f),
        -2 to PlaceGalerie(112f, 528f, 250f, 206f),
        // En haut, flou
        -1 to PlaceGalerie(300f, 196f, 276f, 232f, flou = 1f, saturation = 1.3f),
        // Au centre : l'image reste un peu adoucie, le titre se détache dessus
        0 to PlaceGalerie(620f, 150f, 940f, 560f, rayon = 34f, flou = 0.3f, saturation = 1.35f, contraste = 1.06f, heros = 1f),
        // À droite, coupée par le bord, puis plus loin en bas, puis pas encore entrée
        1 to PlaceGalerie(1600f, 236f, 420f, 300f, flou = 1f, saturation = 1.3f),
        2 to PlaceGalerie(1640f, 560f, 230f, 168f, flou = 1f, saturation = 1.2f),
        3 to PlaceGalerie(1990f, 520f, 230f, 168f, alpha = 0f, flou = 1f, saturation = 1.2f),
    )

    /** Décalages des places visibles à l'écran (toutes sauf les deux cachées). */
    val VISIBLES: IntRange = MIN + 1 until MAX

    fun place(o: Int): PlaceGalerie = PLACES.getValue(o.coerceIn(MIN, MAX))

    /** Place cachée (hors de l'écran) : un titre qui y passe d'un bord à l'autre ne se voit pas. */
    fun cachee(o: Int): Boolean = o <= MIN || o >= MAX

    /**
     * Décalage du titre [i] par rapport au titre du centre [courant], dans une liste de [n] titres
     * qui boucle : entre -n/2 et n/2 (comme decalage() de la maquette).
     */
    fun decalage(i: Int, courant: Int, n: Int): Int {
        if (n <= 0) return 0
        var o = ((i - courant) % n + n) % n
        if (o * 2 > n) o -= n
        return o
    }

    /** Titre au centre après [k] pas (▶ : +1, ◀ : -1), la liste bouclant. */
    fun pas(courant: Int, k: Int, n: Int): Int = if (n <= 0) 0 else ((courant + k) % n + n) % n

    /** Avancement du glissement [ms] après son départ (0 → 1, avec la courbe). */
    fun avancement(ms: Float): Float = COURBE.transform((ms / DUREE).coerceIn(0f, 1f))

    /** Fondu du contenu du titre qui arrive au centre (0,45 s après 0,55 s). */
    fun apparitionContenu(ms: Float): Float = DOUCE.transform(((ms - 550f) / 450f).coerceIn(0f, 1f))

    /** Fondu du synopsis et du libellé « Regarder sur … » (0,5 s après 0,45 s). */
    fun apparitionSynopsis(ms: Float): Float = DOUCE.transform(((ms - 450f) / 500f).coerceIn(0f, 1f))

    /** Disparition du contenu du titre qui quitte le centre (0,35 s). */
    fun disparition(ms: Float): Float = 1f - DOUCE.transform((ms / 350f).coerceIn(0f, 1f))

    /** Durée après laquelle toutes les animations d'un glissement sont finies (ms). */
    const val FIN = 1000f
}

/** Trajet d'un titre pendant un glissement : de [depart] à [arrivee]. Un fantôme sort par un bord. */
class TrajetGalerie(val index: Int, val depart: PlaceGalerie, val arrivee: PlaceGalerie, val fantome: Boolean = false) {
    fun a(p: Float): PlaceGalerie = depart.vers(arrivee, p)
}

/**
 * Glissements du carrousel : trajet de chaque titre, de sa position actuelle (un glissement en
 * cours est repris là où il en est) vers sa nouvelle place. Un titre qui fait le tour (fin de
 * liste) ne traverse jamais l'écran : entre deux places cachées il saute sans se voir ; sinon il
 * sort par un bord (fantôme, qui s'efface) et rentre par l'autre.
 */
class AnimationGalerie {
    var trajets: List<TrajetGalerie> = emptyList()
        private set
    var courant = 0
        private set
    var n = 0
        private set

    /** Liste de [n] titres, [courant] au centre, sans animation. */
    fun installer(n: Int, courant: Int) {
        this.n = n.coerceAtLeast(0)
        this.courant = if (n <= 0) 0 else courant.coerceIn(0, n - 1)
        trajets = (0 until this.n).map { i ->
            val p = CarrouselGalerie.place(CarrouselGalerie.decalage(i, this.courant, this.n))
            TrajetGalerie(i, p, p)
        }
    }

    /** Index des titres dont un trajet (ou un fantôme) passe par une place visible. */
    fun visibles(p: Float): Set<Int> = trajets.filter { it.a(p).alpha > 0f || it.depart.alpha > 0f || it.arrivee.alpha > 0f }
        .map { it.index }.toSet()

    /**
     * Glisse de [k] titres (▶ : +1). [p] : avancement du glissement en cours (1 s'il est fini).
     * Renvoie false si rien ne bouge (liste de moins de deux titres).
     */
    fun deplacer(k: Int, p: Float): Boolean {
        if (n <= 1 || k % n == 0) return false
        val ancien = courant
        val nouveau = CarrouselGalerie.pas(ancien, k, n)
        val ici = trajets.filter { !it.fantome }.associate { it.index to it.a(p) }
        val res = mutableListOf<TrajetGalerie>()
        // Fantômes encore en route : ils continuent vers leur bord depuis là où ils sont
        if (p < 1f) for (t in trajets) if (t.fantome) res += TrajetGalerie(t.index, t.a(p), t.arrivee, fantome = true)
        val sortie = CarrouselGalerie.place(if (k > 0) CarrouselGalerie.MIN else CarrouselGalerie.MAX)
        val entree = CarrouselGalerie.place(if (k > 0) CarrouselGalerie.MAX else CarrouselGalerie.MIN)
        for (i in 0 until n) {
            val o1 = CarrouselGalerie.decalage(i, ancien, n)
            val o2 = CarrouselGalerie.decalage(i, nouveau, n)
            val cible = CarrouselGalerie.place(o2)
            val depart = ici[i] ?: CarrouselGalerie.place(o1)
            when {
                // Glissement normal vers la place voisine (ou plus loin, en cas de saut de plusieurs titres)
                o2 == o1 - k -> res += TrajetGalerie(i, depart, cible)
                // Tour complet entre deux places cachées : saut invisible
                CarrouselGalerie.cachee(o1) && CarrouselGalerie.cachee(o2) && depart.alpha <= 0f -> res += TrajetGalerie(i, cible, cible)
                else -> {
                    // Sortie par un bord (fantôme) et entrée par l'autre
                    if (depart.alpha > 0f) res += TrajetGalerie(i, depart, sortie, fantome = true)
                    res += TrajetGalerie(i, if (CarrouselGalerie.cachee(o2)) cible else entree, cible)
                }
            }
        }
        trajets = res
        courant = nouveau
        return true
    }

    /** Fin du glissement : les fantômes disparaissent, chaque titre est à sa place. */
    fun terminer() {
        trajets = trajets.filter { !it.fantome }.map { TrajetGalerie(it.index, it.arrivee, it.arrivee) }
    }
}

/** Courbe de Bézier cubique de la CSS (cubic-bezier(x1, y1, x2, y2)) : avancement selon le temps (0 → 1). */
class CourbeBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    private fun bezier(a: Float, b: Float, s: Float): Float {
        val r = 1f - s
        return 3f * r * r * s * a + 3f * r * s * s * b + s * s * s
    }

    fun transform(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        // x(s) croît avec s (x1, x2 entre 0 et 1) : recherche par dichotomie
        var bas = 0f
        var haut = 1f
        var s = t
        repeat(24) {
            s = (bas + haut) / 2f
            if (bezier(x1, x2, s) < t) bas = s else haut = s
        }
        return bezier(y1, y2, s)
    }
}
