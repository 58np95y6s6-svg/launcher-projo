package fr.projo.launcher.ui

import androidx.compose.ui.graphics.Color
import fr.projo.launcher.boxscore.EquipesNba
import fr.projo.launcher.data.AmbianceNba
import fr.projo.launcher.nba.ConferenceNba
import fr.projo.launcher.nba.DivisionNba
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Design « NBA » : les trois ambiances de la carte (docs/design-nba.md, objet PAL de la maquette).
 * Leaders (par défaut) : chaque île aux couleurs de l'équipe en tête de sa division ; Parquet :
 * bois clair à l'Est, foncé à l'Ouest ; Nuit d'arène : sarcelle à l'Est, violet à l'Ouest.
 */

/** Fond de l'écran : dégradé radial elliptique (tailles en fraction de l'écran, comme en CSS). */
class FondNba(val rx: Float, val ry: Float, val cx: Float, val cy: Float, val arrets: List<Pair<Float, Color>>)

/** Couleurs de la carte et de l'écran pour une ambiance (et, en Leaders, ses leaders). */
class PaletteNba(
    val ambiance: AmbianceNba,
    /** Remplissage de chaque île. */
    val fonds: Map<DivisionNba, Color>,
    /** Opacité des planches (0 : aucune). */
    val planche: Float,
    val lisere: Color,
    /** Force du reflet clair en haut à gauche des îles. */
    val eclat: Float,
    /** Frontières des États (couleur, opacité comprise). */
    val etats: Color,
    val noms: Color,
    val monuments: Color,
    val opaciteMonuments: Float,
    /** Texte « découpé » d'un monument (Hollywood). */
    val texteMonuments: Color,
    /** Liseré intérieur de chaque conférence (null : aucun, ambiance Leaders). */
    val conferences: Map<ConferenceNba, Color>?,
    /** Pastille de conférence du top 3. */
    val pastillesConference: Map<ConferenceNba, Color>,
    /** Ronds centraux et logos des leaders en filigrane. */
    val filigrane: Boolean,
    val fond: FondNba,
    /** Barre bicolore du titre (haut, bas). */
    val barre: Pair<Color, Color>,
    val sousTitre: String,
) {
    companion object {
        /** Monuments posés sur l'océan, nom posé hors de son île : couleur claire. */
        val CLAIR = Color(0xFFE8E2D4)

        /** Îles sans classement (premier démarrage hors ligne) en ambiance Leaders. */
        val ARDOISE = Color(0xFF3E475A)

        /**
         * Palette de l'[ambiance] ; [leaders] : code de l'équipe en tête de chaque division
         * (Leaders seulement ; une division sans leader connu reste gris ardoise).
         */
        fun de(ambiance: AmbianceNba, leaders: Map<DivisionNba, String>): PaletteNba = when (ambiance) {
            AmbianceNba.LEADERS -> {
                val couleurs = RegleLeadersNba.fonds(leaders)
                PaletteNba(
                    ambiance,
                    fonds = DivisionNba.entries.associateWith { d -> couleurs[d]?.let { Color(0xFF000000 or it.toLong()) } ?: ARDOISE },
                    planche = 0.05f, lisere = Color.White.copy(alpha = 0.55f), eclat = 0.14f,
                    etats = Color.White.copy(alpha = 0.16f), noms = Color.White.copy(alpha = 0.62f),
                    monuments = Color.White, opaciteMonuments = 0.3f, texteMonuments = Color(0xFF0A1534),
                    conferences = null,
                    pastillesConference = mapOf(ConferenceNba.EST to Color.White, ConferenceNba.OUEST to Color.White),
                    filigrane = true,
                    fond = FondNba(1.1f, 0.85f, 0.5f, 0.42f, listOf(0f to Color(0xFF18213A), 0.5f to Color(0xFF0D1324), 1f to Color(0xFF06080F))),
                    barre = Color.White to Color.White.copy(alpha = 0.4f),
                    sousTitre = "chaque division aux couleurs de son leader",
                )
            }
            AmbianceNba.PARQUET -> PaletteNba(
                ambiance,
                fonds = mapOf(
                    DivisionNba.ATLANTIQUE to Color(0xFFE7BB82), DivisionNba.CENTRALE to Color(0xFFDDAC72),
                    DivisionNba.SUD_EST to Color(0xFFEFC794), DivisionNba.NORD_OUEST to Color(0xFFB77A45),
                    DivisionNba.PACIFIQUE to Color(0xFFA96D3C), DivisionNba.SUD_OUEST to Color(0xFFC0854F),
                ),
                planche = 0.07f, lisere = Color(0xFF3B2412).copy(alpha = 0.45f), eclat = 0.22f,
                etats = Color(0xFF3B2412).copy(alpha = 0.22f), noms = Color(0xFF1B2238).copy(alpha = 0.62f),
                monuments = Color(0xFF1B2238), opaciteMonuments = 0.26f, texteMonuments = Color(0xFFE7BB82),
                conferences = mapOf(ConferenceNba.EST to Color(0xFFF2A03D), ConferenceNba.OUEST to Color(0xFFE8E2D4)),
                pastillesConference = mapOf(ConferenceNba.EST to Color(0xFFF2A03D), ConferenceNba.OUEST to Color(0xFFE8E2D4)),
                filigrane = false,
                fond = FondNba(0.9f, 0.75f, 0.5f, 0.4f, listOf(0f to Color(0xFF1D2233), 0.55f to Color(0xFF11141F), 1f to Color(0xFF07080D))),
                barre = Color(0xFFF2A03D) to Color(0xFFE8E2D4),
                sousTitre = "conférences et divisions",
            )
            AmbianceNba.NUIT -> PaletteNba(
                ambiance,
                fonds = mapOf(
                    DivisionNba.ATLANTIQUE to Color(0xFF1D5D6A), DivisionNba.CENTRALE to Color(0xFF236D7C),
                    DivisionNba.SUD_EST to Color(0xFF174F5A), DivisionNba.NORD_OUEST to Color(0xFF43317A),
                    DivisionNba.PACIFIQUE to Color(0xFF4F3A8D), DivisionNba.SUD_OUEST to Color(0xFF382868),
                ),
                planche = 0f, lisere = Color.White.copy(alpha = 0.5f), eclat = 0.10f,
                etats = Color.White.copy(alpha = 0.14f), noms = Color.White.copy(alpha = 0.55f),
                monuments = Color.White, opaciteMonuments = 0.3f, texteMonuments = Color(0xFF0A1534),
                conferences = mapOf(ConferenceNba.EST to Color(0xFF5EE0FF), ConferenceNba.OUEST to Color(0xFFC3A6FF)),
                pastillesConference = mapOf(ConferenceNba.EST to Color(0xFF5EE0FF), ConferenceNba.OUEST to Color(0xFFC3A6FF)),
                filigrane = false,
                fond = FondNba(1.2f, 0.9f, 0.5f, 0.45f, listOf(0f to Color(0xFF10204A), 0.45f to Color(0xFF0A1534), 1f to Color(0xFF050A1C))),
                barre = Color(0xFF5EE0FF) to Color(0xFFC3A6FF),
                sousTitre = "conférences et divisions",
            )
        }
    }
}

/**
 * Ambiance Leaders : couleur de chaque île d'après l'équipe en tête de sa division (règle de la
 * maquette, au pixel près). Couleur de l'équipe ; trop claire (luminance > 0,45) : mélangée à
 * #0b1226 (55 %). Si une île voisine déjà coloriée a une teinte trop proche (écart < 28° et écart
 * de luminance ≤ 0,12), l'accent de l'équipe assombri (45 % de noir). Puis 22 % de #0b1226.
 * Couleurs en 0xRRGGBB, arrondies à chaque étape comme dans la maquette.
 */
object RegleLeadersNba {
    private const val NUIT = 0x0B1226
    private const val NOIR = 0x000000

    /** Divisions qui se touchent sur la carte. */
    val VOISINES: List<Pair<DivisionNba, DivisionNba>> = listOf(
        DivisionNba.ATLANTIQUE to DivisionNba.CENTRALE, DivisionNba.ATLANTIQUE to DivisionNba.SUD_EST,
        DivisionNba.CENTRALE to DivisionNba.SUD_EST, DivisionNba.CENTRALE to DivisionNba.NORD_OUEST,
        DivisionNba.CENTRALE to DivisionNba.SUD_OUEST, DivisionNba.SUD_EST to DivisionNba.SUD_OUEST,
        DivisionNba.NORD_OUEST to DivisionNba.PACIFIQUE, DivisionNba.NORD_OUEST to DivisionNba.SUD_OUEST,
        DivisionNba.PACIFIQUE to DivisionNba.SUD_OUEST,
    )

    /**
     * Couleur (0xRRGGBB) de chaque division dont le leader est connu ([leaders] : code NBA de
     * l'équipe en tête), dans l'ordre des divisions (Atlantique, Centrale, Sud-Est, Nord-Ouest,
     * Pacifique, Sud-Ouest) : une île n'est comparée qu'à ses voisines déjà coloriées.
     */
    fun fonds(leaders: Map<DivisionNba, String>): Map<DivisionNba, Int> {
        val fond = LinkedHashMap<DivisionNba, Int>()
        for (d in DivisionNba.entries) {
            val code = leaders[d] ?: continue
            val e = EquipesNba.de(code)
            val choix = listOf(hex(e.couleur), melange(hex(e.accent), NOIR, 0.45))
                .map { if (luminance(it) > 0.45) melange(it, NUIT, 0.55) else it }
            val voisins = VOISINES.filter { it.first == d || it.second == d }
                .mapNotNull { fond[if (it.first == d) it.second else it.first] }
            val libre = choix.firstOrNull { x ->
                voisins.all { v -> ecartTeinte(x, v) > 28.0 || abs(luminance(x) - luminance(v)) > 0.12 }
            } ?: choix[0]
            fond[d] = melange(libre, NUIT, 0.22)
        }
        return fond
    }

    /** « #rrggbb » → 0xRRGGBB. */
    fun hex(s: String): Int = s.removePrefix("#").toInt(16)

    /** 0xRRGGBB → « #rrggbb ». */
    fun texte(c: Int): String = "#" + (c and 0xFFFFFF).toString(16).padStart(6, '0')

    private fun canal(c: Int, i: Int): Int = (c shr (16 - 8 * i)) and 0xFF

    /** [a] mêlé à [b] dans la proportion [t] (0 : a, 1 : b), canal par canal, arrondi. */
    fun melange(a: Int, b: Int, t: Double): Int {
        var r = 0
        for (i in 0..2) {
            val v = canal(a, i).toDouble()
            val w = canal(b, i).toDouble()
            val m = (v + (w - v) * t).roundToInt().coerceIn(0, 255)
            r = (r shl 8) or m
        }
        return r
    }

    /** Luminance relative (sRGB). */
    fun luminance(c: Int): Double {
        val l = DoubleArray(3) { i ->
            val v = canal(c, i) / 255.0
            if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2]
    }

    /** Teinte en degrés (0–360), −1 pour un gris. */
    fun teinte(c: Int): Double {
        val r = canal(c, 0) / 255.0
        val g = canal(c, 1) / 255.0
        val b = canal(c, 2) / 255.0
        val haut = max(r, max(g, b))
        val bas = min(r, min(g, b))
        val d = haut - bas
        if (d == 0.0) return -1.0
        val h = when (haut) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        return (h * 60 + 360) % 360
    }

    /** Écart de teinte (0–180°) ; un gris face à une couleur : 180. */
    fun ecartTeinte(a: Int, b: Int): Double {
        val x = teinte(a)
        val y = teinte(b)
        if (x < 0 || y < 0) return if (x == y) 0.0 else 180.0
        val d = abs(x - y)
        return min(d, 360 - d)
    }
}
