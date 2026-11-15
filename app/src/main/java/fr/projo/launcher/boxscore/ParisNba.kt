package fr.projo.launcher.boxscore

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/*
 * Paris suivis (docs/telecommande.md, « Paris suivis ») : lecture du texte d'un pari copié depuis
 * une appli de paris (Betclic, Winamax, Unibet, PMU…) ou depuis la capture d'un ticket (texte en
 * direct de l'iPhone), en lignes que la TV sait suivre. Sans Android, testé sur la JVM.
 */

/** Statistique d'un joueur suivie par une ligne ; [unite] : fin du libellé (« + de 19,5 pts »). */
enum class StatPari(val unite: String) {
    POINTS("pts"),
    REBONDS("reb"),
    PASSES("pd"),
    TROIS("paniers à 3 pts"),
    POINTS_REBONDS_PASSES("pts + reb + pd"),
    POINTS_REBONDS("pts + reb"),
    POINTS_PASSES("pts + pd"),
    REBONDS_PASSES("reb + pd");

    /** Valeur de la statistique pour [j]. */
    fun de(j: JoueurNba): Int = when (this) {
        POINTS -> j.points
        REBONDS -> j.rebonds
        PASSES -> j.passes
        TROIS -> j.troisReussis
        POINTS_REBONDS_PASSES -> j.points + j.rebonds + j.passes
        POINTS_REBONDS -> j.points + j.rebonds
        POINTS_PASSES -> j.points + j.passes
        REBONDS_PASSES -> j.rebonds + j.passes
    }
}

/** « Plus de N » ou « moins de N » ; [texte] : forme du libellé. */
enum class Sens(val texte: String) { PLUS("+ de"), MOINS("- de") }

/** Une ligne lue dans le texte d'un pari. */
sealed class LignePari {
    /** Identité de la ligne : la même ligne n'est pas ajoutée deux fois. */
    abstract val cle: String

    /**
     * Statistique d'un joueur (« Tyrese Maxey + de 19,5 points »). [contexte] : codes des équipes
     * du match d'après le ticket (« Sixers 29 8 37 / Pacers 35 4 39 »), vide si inconnu ; le box
     * score l'emporte toujours.
     */
    data class Joueur(val nom: String, val stat: StatPari, val sens: Sens, val seuil: Double, val contexte: Set<String> = emptySet()) : LignePari() {
        override val cle: String get() = "joueur|${NomsJoueurs.forme(nom)}|$stat|$sens|$seuil"
    }

    /** Vainqueur du match (prolongations comprises), ou du temps réglementaire seulement ([reglementaire]). */
    data class Vainqueur(val equipe: String, val reglementaire: Boolean = false) : LignePari() {
        override val cle: String get() = "vainqueur|$equipe|$reglementaire"
    }

    /** Total de points du match ; [contexte] : codes des équipes du match d'après le ticket. */
    data class TotalMatch(val sens: Sens, val seuil: Double, val contexte: Set<String> = emptySet()) : LignePari() {
        override val cle: String get() = "total|${contexte.sorted()}|$sens|$seuil"
    }

    /** Total de points d'une équipe. */
    data class TotalEquipe(val equipe: String, val sens: Sens, val seuil: Double) : LignePari() {
        override val cle: String get() = "equipe|$equipe|$sens|$seuil"
    }

    /** Pari reconnu que la TV ne sait pas suivre : le texte lu. */
    data class NonSuivie(val texte: String) : LignePari() {
        override val cle: String get() = "non_suivi|" + sansAccents(texte).replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}

/** « 19,5 », « 20 » : nombre en français (virgule décimale, sans zéro inutile). */
fun texteNombre(x: Double): String =
    BigDecimal.valueOf(x).stripTrailingZeros().toPlainString().replace('.', ',')

/** Minuscules sans accents (« Dončić » → « doncic »), lettres particulières simplifiées. */
internal fun sansAccents(s: String): String {
    val d = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val b = StringBuilder(d.length)
    for (c in d) {
        when (c) {
            'ø' -> b.append('o')
            'ł' -> b.append('l')
            'đ', 'ð' -> b.append('d')
            'ß' -> b.append("ss")
            'æ' -> b.append("ae")
            'œ' -> b.append("oe")
            'ı' -> b.append('i')
            'þ' -> b.append("th")
            else -> b.append(c)
        }
    }
    return b.toString()
}

/**
 * Noms des joueurs : « Tyrese Maxey » (ticket) ↔ « T. Maxey » (box score, `nameI` NBA ou
 * `shortName` ESPN), accents, tirets, apostrophes et suffixes (« Jr. », « III ») ignorés.
 */
object NomsJoueurs {
    private val SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv")

    /**
     * Mots du nom, en minuscules sans accents ni ponctuation (« Caldwell-Pope » → « caldwellpope »,
     * « O'Neale » → « oneale »), sans suffixe final.
     */
    fun mots(nom: String): List<String> {
        val m = nom.trim().split(Regex("\\s+")).map { sansAccents(it).replace(Regex("[^a-z0-9]"), "") }.filter { it.isNotEmpty() }
        val sans = m.dropLastWhile { it in SUFFIXES }
        return sans.ifEmpty { m }
    }

    fun forme(nom: String): String = mots(nom).joinToString(" ")

    private fun famille(mots: List<String>): String = if (mots.size >= 2) mots.drop(1).joinToString("") else mots.firstOrNull().orEmpty()

    /**
     * Joueur de [joueurs] désigné par [nom] : nom complet identique, sinon initiale du prénom et
     * nom de famille, sinon nom de famille seul s'il est unique ; null si aucun ou plusieurs.
     */
    fun trouver(nom: String, joueurs: List<JoueurNba>): JoueurNba? {
        val m = mots(nom)
        if (m.isEmpty()) return null
        val fam = famille(m)
        if (m.size >= 2) {
            val prenom = m.first()
            if (prenom.length > 1) {
                unique(joueurs.filter { j -> j.nomComplet.isNotEmpty() && mots(j.nomComplet).let { it.size >= 2 && it.first() == prenom && famille(it) == fam } })?.let { return it }
            }
            unique(joueurs.filter { j -> mots(j.nom).let { it.size >= 2 && it.first().first() == prenom.first() && famille(it) == fam } })?.let { return it }
        }
        return unique(joueurs.filter { j -> famille(mots(j.nom)) == fam })
    }

    private fun unique(l: List<JoueurNba>): JoueurNba? = l.distinct().singleOrNull()

    /** « Tyrese Maxey » → « T. Maxey » (forme du box score) ; un nom déjà court reste tel quel. */
    fun court(nom: String): String {
        val m = nom.trim().split(Regex("\\s+"))
        if (m.size < 2) return nom.trim()
        val premier = m.first()
        if (premier.endsWith(".") && premier.length <= 4) return m.joinToString(" ")
        return premier.first().uppercaseChar() + ". " + m.drop(1).joinToString(" ")
    }
}

/**
 * Lecture du texte d'un pari (docs/telecommande.md, « Lignes suivies »). Le texte vient d'un
 * ticket : sélection et type de pari sur une ou deux lignes (« Tyrese Maxey + de 19,5 » puis
 * « Nombre de points du joueur (paliers) » chez Betclic ; l'inverse ailleurs), au milieu des
 * cotes, mises, gains, en-têtes, scores en direct et paris d'autres sports, tous ignorés.
 *
 * Méthode : chaque ligne est analysée (seuil, statistique, équipes NBA, nom de joueur, mots d'un
 * type de pari, bruit) ; chaque ligne de type de pari est associée à sa voisine (avant ou après,
 * le sens qui associe le plus de lignes sur ce ticket) ; une ligne complète à elle seule
 * (« Tyrese Maxey - Points : Plus de 19,5 ») suffit. Le match d'une ligne de joueur ou d'un total
 * vient de la ligne de match la plus proche (« Sixers 29 8 37 / Pacers 35 4 39 »,
 * « Philadelphia 76ers - Indiana Pacers ») ; une ligne de match d'un autre sport (« France 20:45
 * Turquie ») écarte la ligne.
 */
object ParisNba {
    /** `nba_paris_ajouter.texte` : longueur lue au plus. */
    const val TEXTE_MAX = 4000

    private const val LIGNES_TEXTE_MAX = 200

    /** Lignes de pari rendues au plus (autant que la TV en garde). */
    const val LIGNES_MAX = 20

    /** Libellé d'une ligne non suivie : longueur maximale. */
    const val NON_SUIVIE_MAX = 90

    /** Lignes de pari reconnues dans [texte], dans l'ordre du ticket, sans doublon. */
    fun lire(texte: String): List<LignePari> {
        val lignes = joindre(nettoyer(limiter(texte)))
        if (lignes.isEmpty()) return emptyList()
        val an = lignes.mapIndexed { i, l -> analyser(i, l) }
        val indications = indications(an)
        val avant = apparier(an, Orientation.AVANT)
        val apres = apparier(an, Orientation.APRES)
        // Le sens qui associe le plus de lignes (à égalité : sélection puis type de pari, comme Betclic)
        val (orientation, choisi) = if (apres.score > avant.score) Orientation.APRES to apres else Orientation.AVANT to avant
        val sortie = choisi.jambes(an)
            .sortedBy { it.lignes.min() }
            .mapNotNull { contextualiser(it, an, indications, orientation) }
            .distinctBy { it.cle }
        return sortie.take(LIGNES_MAX)
    }

    /** Texte trop long : coupé à [TEXTE_MAX] caractères, sans garder la dernière ligne entamée. */
    fun limiter(texte: String): String {
        if (texte.length <= TEXTE_MAX) return texte
        val t = texte.take(TEXTE_MAX)
        val fin = maxOf(t.lastIndexOf('\n'), t.lastIndexOf('\r'))
        return if (fin > 0) t.substring(0, fin) else t
    }

    /** Lignes non vides, espaces, tirets, signes et apostrophes typographiques ramenés à l'ASCII. */
    private fun nettoyer(texte: String): List<String> {
        val t = texte
            .replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u200A\\u2002\\u2003\\t]"), " ")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("[\\u2212\\u2013\\u2014\\u2010\\u2011\\u2012\\uFE63\\uFF0D]"), "-")
            .replace(Regex("[\\uFF0B\\uFE62]"), "+")
            .replace(Regex("[\\u2018\\u2019\\u02BC\\u00B4`]"), "'")
        return t.split(Regex("\\r\\n|\\r|\\n|\\u2028|\\u2029"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .take(LIGNES_TEXTE_MAX)
    }

    private val FIN_SENS = Regex("""(?:[+-]\s*(?:de|d')|\bplus(?:\s+(?:de|d'))?|\bmoins(?:\s+(?:de|d'))?)\s*$""")
    private val DEBUT_NOMBRE = Regex("""^\d""")

    /** Texte en direct coupé après « + de » : la ligne suivante (le nombre) est rattachée. */
    private fun joindre(lignes: List<String>): List<String> {
        val r = ArrayList<String>()
        var i = 0
        while (i < lignes.size) {
            var l = lignes[i]
            if (i + 1 < lignes.size && FIN_SENS.containsMatchIn(sansAccents(l)) && DEBUT_NOMBRE.containsMatchIn(lignes[i + 1])) {
                l = l + " " + lignes[i + 1]
                i++
            }
            r += l
            i++
        }
        return r
    }

    // ------------------------------------------------------------------ analyse d'une ligne

    private class Analyse(
        val index: Int,
        val brut: String,
        val bruit: Boolean,
        val sens: Sens?,
        val seuil: Double?,
        val stat: StatPari?,
        val vainqueur: Boolean,
        val reglementaire: Boolean,
        val total: Boolean,
        val duMatch: Boolean,
        val motEquipe: Boolean,
        val motJoueur: Boolean,
        val inconnu: Boolean,
        val basket: Boolean,
        val marche: Boolean,
        val equipes: List<String>,
        val nom: String?,
        val score: Boolean,
        /** Match désigné par la ligne : codes NBA, ou ensemble vide pour un match d'un autre sport. */
        val rencontre: Set<String>?,
    ) {
        /** Ligne de match ou de score seulement : contexte, jamais une sélection. */
        val contexteSeul: Boolean get() = (score || rencontre != null) && !marche && sens == null

        /** Ligne d'un type de pari, sans seuil : attend sa sélection sur une ligne voisine. */
        val typeSeul: Boolean get() = marche && sens == null && !bruit
    }

    private const val NB = """(\d{1,3}(?:[.,]\d{1,2})?)"""
    private val R_AU_MOINS = Regex("""\b(?:au\s+moins|minimum)\s+(?:de\s+)?(\d{1,3})(?![.,]\d)""")
    private val R_OU_PLUS = Regex("""(?<![\d.,])(\d{1,3})\s+(?:[a-z]+\s+){0,3}?(?:ou|et)\s+plus\b""")
    private val R_OU_MOINS = Regex("""(?<![\d.,])(\d{1,3})\s+(?:[a-z]+\s+){0,3}?(?:ou|et)\s+moins\b""")
    private val R_PLUS = Regex("""(?:\+\s*(?:de|d)\b|\bplus(?:\s+(?:de|d|que)\b)?|\bover\b|\bau[- ]dessus\s+de\b)\s*$NB""")
    private val R_MOINS = Regex("""(?:(?<![\w+])-\s*(?:de|d)\b|(?<!au\s)\bmoins(?:\s+(?:de|d|que)\b)?|\bunder\b|\ben[- ]dessous\s+de\b)\s*$NB""")
    private val R_PALIER = Regex("""(?<![\d.,])(\d{1,3})\s?\+(?!\s*(?:de|d)\b)""")

    private val R_TROIS = Regex(
        """(?:paniers?|tirs?|shoots?)\s+(?:reussis\s+)?(?:a\s+)?(?:3|trois)[\s-]*(?:points?|pts)(?:\s+reussis)?|\ba\s+(?:3|trois)[\s-]*(?:points?|pts)\b|\b(?:3|trois)[\s-]*(?:points?|pts)\s+(?:reussis|marques|inscrits)|\b3[\s-]*pts\b|\btrois[\s-]+points\b|\b3pts?\b""",
    )
    private val R_POINTS = Regex("""\bpoints?\b|\bpts\b""")
    private val R_REBONDS = Regex("""\brebonds?\b|\breb\b|\brbds?\b""")
    private val R_PASSES = Regex("""\bpasses?\b|\bpd\b|\bassists?\b""")

    private val R_VAINQUEUR = Regex("""\bvainqueur|\bface a face\b|\bresultat\b|\bgagnant\b|\bvictoire\b|\bmoneyline\b|\bqui (?:va )?gagner""")
    private val R_REGLEMENTAIRE = Regex("""\bt\.?\s*reg\b|\btemps regl|\bsans (?:les )?prolongations?\b|\b90 ?min""")
    private val R_TOTAL = Regex("""\btotal""")
    private val R_MATCH = Regex("""\bmatch\b|\brencontre\b""")
    private val R_EQUIPE = Regex("""\bequipe\b""")
    private val R_JOUEUR = Regex("""\bjoueur|\bjoueuse""")
    private val R_INCONNU = Regex(
        """handicap|\bmi[- ]?temps|\bquart[- ]?temps|\b(?:1er|1re|2e|3e|4e|premier|premiere|deuxieme|troisieme|quatrieme)\s+(?:qt|quart|periode)|\bperiode\b|\binterceptions?\b|\bcontres\b|\bballes? perdues?|double[- ]double|triple[- ]double|\bpremier (?:panier|marqueur)|\bpremiere equipe|\blancers?[- ]francs?|\becart\b|\bmarge\b|\bfautes?\b|\bminutes\b|\bexact|\bentre\b|\btranche|\bimpair\b|\bpair\b|\bmvp\b|\bmeilleur marqueur|\bcourse a\b|\bdunks?\b|\bnul\b""",
    )
    private val R_BASKET = Regex("""\bpoints?\b|\bpts\b|\brebonds?\b|\bpasses?\b|\bpanier|\binterceptions?\b|\bcontres\b|double[- ]double|triple[- ]double|lancers?[- ]francs?|\bquart[- ]?temps|\bnba\b|\bbasket""")
    private val R_MARCHE = Regex("""\bnombre\b|\bpaliers?\b|\bprolongations?\b|\bcumul""")
    private val R_AUTRE_SPORT = Regex("""\bbuts?\b|\bbuteur|\bjeux\b|\bsets?\b|\bcorners?\b|\bcartons?\b|\bessais?\b|\baces?\b|\btie[- ]?break""")

    private val R_ARGENT = Regex(
        """€|\beur\b|\beuros?\b|\bmises?\b|\bgains?\b|\bcotes?\b|\bcombine|\bmycombi|\bsimple\b|\bmultiple\b|\bsysteme\b|freebet|pari gratuit|\bbonus\b|\bboost|cash ?out|encaisser|\bticket\b|\breference\b|\bref\b|n°|\bpari (?:place|valide|en cours|gagne|perdu|rembourse)|\bpartager\b|\bvalider\b|\bparier\b|\bcopier\b|\bselections?\b|\bretrait\b|\bsolde\b""",
    )
    private val R_STATUT = Regex(
        """fin (?:du|de la) (?:match|\d(?:er|re|e)\s+(?:qt|quart[- ]temps)|prol\S*)|\d{1,2}[:h]\d{2}(?::\d{2})?|\d{1,2}/\d{1,2}(?:/\d{2,4})?|\bq[1-4]\b|\bqt\s?[1-4]?\b|\b[1-4](?:er|re|e)\b|\bmi[- ]?temps\b|\bmt\b|\bot\b|\bprol\b\.?|\btermine\b|\bfin\b|\bgagnee?s?\b|\bperdue?s?\b|\ben cours\b|\bremboursee?s?\b|\bannulee?s?\b|\bvalidee?s?\b|\ben direct\b|\blive\b|\bdirect\b|\ba venir\b|\bdemain\b|\baujourd ?'?hui\b|\bhier\b|\b(?:lun|mar|mer|jeu|ven|sam|dim)\.?(?=\s|$)|\b\d{1,2}\s+(?:janv|fevr|mars|avr|mai|juin|juil|aout|sept|oct|nov|dec)[a-z]*\.?|[•·|\-\s.,:()@]""",
    )
    private val R_SPORT = Regex("""^(?:basket|basketball|nba|football|foot|soccer|tennis|rugby|hockey|handball|volley|volleyball|baseball|mma|boxe)\b""")
    private val R_LETTRE = Regex("""\p{L}""")

    /** Mots courants des tickets, jamais un nom de joueur (comparés sans accents). */
    private val MOTS_CONNUS = setOf(
        "nombre", "total", "totale", "points", "point", "pts", "pt", "rebonds", "rebond", "reb", "passes", "passe", "decisives",
        "decisive", "pd", "paniers", "panier", "tirs", "tir", "reussis", "plus", "moins", "de", "du", "des", "la", "le", "les",
        "et", "ou", "au", "aux", "a", "en", "sur", "par", "pour", "avec", "sans", "vainqueur", "vainqueurs", "match", "joueur",
        "joueuse", "equipe", "paliers", "palier", "prolongations", "prolongation", "incluses", "inclus", "comprises", "compris",
        "resultat", "face", "cote", "cotes", "mise", "gains", "gain", "combine", "mycombi", "handicap", "mi", "temps", "quart",
        "interceptions", "contres", "double", "triple", "premier", "premiere", "marque", "marquera", "over", "under", "oui", "non",
        "nul", "live", "direct", "reg", "regl", "reglementaire", "exact", "exactement", "entre", "minimum", "maximum", "victoire",
        "gagnant", "ecart", "marge", "score", "scores", "periode", "buteur", "buts", "but", "vs", "contre", "simple", "pari",
        "paris", "nba", "basket", "basketball", "selection", "selections", "termine", "fin", "cumul", "type", "statistique",
        "statistiques", "the", "of", "at", "va", "qui", "gagner", "marques", "inscrits", "lancers", "francs", "fautes", "minutes",
    )

    private enum class Orientation { AVANT, APRES }

    private fun analyser(index: Int, brut: String): Analyse {
        val bas = sansAccents(brut).replace('\'', ' ')
        val mots = brut.split(' ').filter { it.isNotEmpty() }
        val eq = equipes(mots)
        val lettres = R_LETTRE.containsMatchIn(bas)
        // Seuil et sens
        var sens: Sens? = null
        var seuil: Double? = null
        fun prendre(r: Regex, s: Sens, decalage: Double, entier: Boolean): Boolean {
            val m = r.find(bas) ?: return false
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return false
            if (entier && v != Math.floor(v)) return false
            val x = v + decalage
            if (x <= 0.0 || x >= 400.0) return false
            sens = s
            seuil = x
            return true
        }
        prendre(R_AU_MOINS, Sens.PLUS, -0.5, true) ||
            prendre(R_OU_PLUS, Sens.PLUS, -0.5, true) ||
            prendre(R_OU_MOINS, Sens.MOINS, 0.5, true) ||
            prendre(R_PLUS, Sens.PLUS, 0.0, false) ||
            prendre(R_MOINS, Sens.MOINS, 0.0, false) ||
            prendre(R_PALIER, Sens.PLUS, -0.5, true)

        // Statistique (paniers à 3 points d'abord : « 3 points » n'est pas une ligne de points)
        val trois = R_TROIS.containsMatchIn(bas)
        val reste = R_TROIS.replace(bas, " ")
        val p = R_POINTS.containsMatchIn(reste)
        val r = R_REBONDS.containsMatchIn(reste)
        val a = R_PASSES.containsMatchIn(reste)
        val stat = when {
            trois -> StatPari.TROIS
            p && r && a -> StatPari.POINTS_REBONDS_PASSES
            p && r -> StatPari.POINTS_REBONDS
            p && a -> StatPari.POINTS_PASSES
            r && a -> StatPari.REBONDS_PASSES
            p -> StatPari.POINTS
            r -> StatPari.REBONDS
            a -> StatPari.PASSES
            else -> null
        }
        val vainqueur = R_VAINQUEUR.containsMatchIn(bas)
        val total = R_TOTAL.containsMatchIn(bas)
        val inconnu = R_INCONNU.containsMatchIn(bas)
        val motJoueur = R_JOUEUR.containsMatchIn(bas)
        val marche = stat != null || vainqueur || total || inconnu || motJoueur || R_MARCHE.containsMatchIn(bas)

        // Bruit : cotes, mises, en-têtes, heure et statut du match, nom du sport
        val bruit = when {
            !lettres -> !R_PALIER.containsMatchIn(bas)
            R_ARGENT.containsMatchIn(bas) -> true
            sens == null && eq.codes.isEmpty() && R_STATUT.replace(bas, "").isEmpty() -> true
            sens == null && eq.codes.isEmpty() && !marche && R_SPORT.containsMatchIn(bas) && mots.size <= 5 -> true
            else -> false
        }

        // Ligne de score : une équipe et des nombres entiers (« Sixers 29 8 37 »)
        val autres = mots.filterIndexed { i, _ -> i !in eq.couverts }
        val score = eq.codes.size == 1 && autres.isNotEmpty() && autres.all { it.matches(Regex("""\d{1,3}""")) }

        // Nom de joueur : hors parenthèses et hors noms d'équipes
        val sansParentheses = brut.replace(Regex("""\([^)]*\)"""), " | ")
        val mots2 = sansParentheses.split(' ').filter { it.isNotEmpty() }
        val nom = if (bruit || score) null else nom(mots2, equipes(mots2).couverts)

        val rencontre: Set<String>? = when {
            eq.codes.size >= 2 -> eq.codes.take(2).toSet()
            eq.codes.isEmpty() && !marche && sens == null && !bruit && R_RENCONTRE.matches(brut) -> emptySet()
            else -> null
        }

        return Analyse(
            index = index,
            brut = brut,
            bruit = bruit,
            sens = if (bruit) null else sens,
            seuil = if (bruit) null else seuil,
            stat = stat,
            vainqueur = vainqueur,
            reglementaire = R_REGLEMENTAIRE.containsMatchIn(sansAccents(brut)),
            total = total,
            duMatch = R_MATCH.containsMatchIn(bas),
            motEquipe = R_EQUIPE.containsMatchIn(bas),
            motJoueur = motJoueur,
            inconnu = inconnu,
            basket = R_BASKET.containsMatchIn(bas),
            marche = marche,
            equipes = eq.codes,
            nom = nom,
            score = score,
            rencontre = rencontre,
        )
    }

    /** Match d'un autre sport : « France 20:45 Turquie », « Real Madrid - FC Barcelone ». */
    private val R_RENCONTRE = Regex(
        """^\p{L}[\p{L}'. ]{0,40}?\s+(?:-|vs\.?|v|@|contre|\d{1,2}[:h]\d{2}|\d{1,3}\s*-\s*\d{1,3})\s+\p{L}[\p{L}'. ]{0,40}$""",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------------ équipes

    private class Alias(val mots: List<String>, val code: String, val seul: Boolean)

    /**
     * Noms des 30 équipes : nom anglais complet, surnom, ville en anglais et en français.
     * [Alias.seul] : mot qui peut aussi être un nom de joueur ou un mot courant (« Orlando
     * Robinson », « P.J. Washington ») : reconnu seulement s'il n'est pas collé à un autre nom.
     */
    private val ALIAS: List<Alias> = buildList {
        fun e(code: String, vararg noms: String) = noms.forEach { add(Alias(it.split(' '), code, false)) }
        fun s(code: String, vararg noms: String) = noms.forEach { add(Alias(it.split(' '), code, true)) }
        e("ATL", "atlanta hawks", "hawks"); s("ATL", "atlanta")
        e("BOS", "boston celtics", "celtics"); s("BOS", "boston")
        e("BKN", "brooklyn nets"); s("BKN", "nets", "brooklyn")
        e("CHA", "charlotte hornets", "hornets"); s("CHA", "charlotte")
        e("CHI", "chicago bulls", "bulls"); s("CHI", "chicago")
        e("CLE", "cleveland cavaliers", "cavaliers", "cavs"); s("CLE", "cleveland")
        e("DAL", "dallas mavericks", "mavericks", "mavs"); s("DAL", "dallas")
        e("DEN", "denver nuggets", "nuggets"); s("DEN", "denver")
        e("DET", "detroit pistons", "pistons"); s("DET", "detroit")
        e("GSW", "golden state warriors", "golden state", "warriors")
        e("HOU", "houston rockets", "rockets"); s("HOU", "houston")
        e("IND", "indiana pacers", "pacers"); s("IND", "indiana")
        e("LAC", "los angeles clippers", "la clippers", "clippers")
        e("LAL", "los angeles lakers", "la lakers", "lakers")
        e("MEM", "memphis grizzlies", "grizzlies", "grizz"); s("MEM", "memphis")
        e("MIA", "miami heat"); s("MIA", "heat", "miami")
        e("MIL", "milwaukee bucks", "bucks"); s("MIL", "milwaukee")
        e("MIN", "minnesota timberwolves", "timberwolves", "wolves"); s("MIN", "minnesota")
        e(
            "NOP", "new orleans pelicans", "nouvelle orleans pelicans", "la nouvelle orleans pelicans", "pelicans",
            "la nouvelle orleans", "nouvelle orleans", "new orleans", "nola",
        )
        e("NYK", "new york knicks", "knicks", "new york")
        e("OKC", "oklahoma city thunder", "oklahoma city"); s("OKC", "thunder", "oklahoma")
        e("ORL", "orlando magic"); s("ORL", "magic", "orlando")
        e("PHI", "philadelphia 76ers", "philadelphie 76ers", "philadelphia sixers", "philadelphie sixers", "76ers", "sixers", "philly")
        s("PHI", "philadelphia", "philadelphie")
        e("PHX", "phoenix suns"); s("PHX", "suns", "phoenix")
        e("POR", "portland trail blazers", "portland trailblazers", "trail blazers", "trailblazers", "blazers"); s("POR", "portland")
        e("SAC", "sacramento kings"); s("SAC", "kings", "sacramento")
        e("SAS", "san antonio spurs", "san antonio"); s("SAS", "spurs")
        e("TOR", "toronto raptors", "raptors"); s("TOR", "toronto")
        e("UTA", "utah jazz"); s("UTA", "jazz", "utah")
        e("WAS", "washington wizards", "wizards"); s("WAS", "washington")
    }.sortedByDescending { it.mots.size }

    private class Equipes(val codes: List<String>, val couverts: Set<Int>)

    /** Forme d'un mot pour les équipes : « L.A. » → « la », « Nouvelle-Orléans » → « nouvelle orleans ». */
    private fun formeMots(mot: String): List<String> =
        sansAccents(mot).replace(Regex("[.']"), "").replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    /** Équipes NBA nommées dans les [mots] d'une ligne (codes dans l'ordre), et les mots qu'elles couvrent. */
    private fun equipes(mots: List<String>): Equipes {
        val plat = ArrayList<Pair<String, Int>>()
        mots.forEachIndexed { i, m -> formeMots(m).forEach { plat += it to i } }
        val pris = BooleanArray(plat.size)
        val trouves = ArrayList<Pair<Int, String>>()
        val couverts = HashSet<Int>()
        for (al in ALIAS) {
            val n = al.mots.size
            var p = 0
            while (p + n <= plat.size) {
                val ok = (0 until n).all { k -> !pris[p + k] && plat[p + k].first == al.mots[k] } && (!al.seul || isole(plat, p, n))
                if (ok) {
                    for (k in 0 until n) {
                        pris[p + k] = true
                        couverts += plat[p + k].second
                    }
                    trouves += p to al.code
                    p += n
                } else {
                    p++
                }
            }
        }
        return Equipes(trouves.sortedBy { it.first }.map { it.second }.distinct(), couverts)
    }

    /** Le mot n'est pas collé à un autre nom (« Orlando Robinson ») : voisins absents, nombres ou mots connus. */
    private fun isole(plat: List<Pair<String, Int>>, p: Int, n: Int): Boolean {
        fun libre(i: Int): Boolean {
            val m = plat.getOrNull(i)?.first ?: return true
            return m.length < 2 || m.any { it.isDigit() } || m in MOTS_CONNUS || ALIAS.any { a -> a.mots.size == 1 && a.mots[0] == m }
        }
        return libre(p - 1) && libre(p + n)
    }

    // ------------------------------------------------------------------ noms de joueurs

    private val NOMINAL = Regex("""^\p{Lu}[\p{L}'.\-]*$""")
    private val INITIALES = Regex("""^(?:\p{Lu}\.){1,3}$""")
    private val SUFFIXE = Regex("""^(?:jr|sr|ii|iii|iv)$""")

    /** Premier groupe de 2 à 5 mots qui ressemblent à un nom (« Tyrese Maxey », « T. Maxey »), hors mots de ticket. */
    private fun nom(mots: List<String>, couverts: Set<Int>): String? {
        val groupe = ArrayList<String>()
        fun valide(): String? {
            val g = groupe.toList()
            groupe.clear()
            if (g.size !in 2..5) return null
            // Au moins un vrai mot (pas seulement des initiales)
            if (g.none { it.count { c -> c.isLetter() } >= 2 && !INITIALES.matches(it) }) return null
            return g.joinToString(" ")
        }
        for ((i, brut) in mots.withIndex()) {
            val m = brut.trim(':', ',', ';', '•', '·', '|', '"', '«', '»', '!', '?', '/', '(', ')', '[', ']')
            val cle = sansAccents(m).replace(Regex("[^a-z0-9]"), "")
            val suffixe = SUFFIXE.matches(cle)
            val nominal = i !in couverts && m.isNotEmpty() && cle.isNotEmpty() && (
                INITIALES.matches(m) ||
                    (NOMINAL.matches(m) && cle !in MOTS_CONNUS && !(m.length == 3 && m.all { it.isUpperCase() } && EquipesNba.connue(m)))
                )
            if (nominal && (!suffixe || groupe.size >= 2)) {
                groupe += m
                if (brut.endsWith(':') || brut.endsWith(',') || brut.endsWith(';')) valide()?.let { return it }
            } else {
                valide()?.let { return it }
            }
        }
        return valide()
    }

    // ------------------------------------------------------------------ association des lignes

    private sealed class Fusion {
        data class Ligne(val ligne: LignePari) : Fusion()

        /** Pari complet d'un autre sport (football…) : ses lignes sont prises, rien n'est gardé. */
        data object Ignoree : Fusion()
        data object Incomplete : Fusion()
    }

    private class Jambe(val lignes: List<Int>, val ligne: LignePari?)

    private class Appariement(val jambes: List<Jambe>, val score: Int, val restes: List<Int>) {
        /** Jambes gardées, et les lignes restées seules qui ressemblent à un pari NBA (non suivies). */
        fun jambes(an: List<Analyse>): List<Jambe> =
            jambes.filter { it.ligne != null } + restes.map { an[it] }.filter { a ->
                a.sens != null && (a.nom != null || a.equipes.isNotEmpty())
            }.map { Jambe(listOf(it.index), LignePari.NonSuivie(it.brut.take(NON_SUIVIE_MAX))) }
    }

    /**
     * Associe chaque ligne de type de pari à sa sélection voisine (avant ou après selon
     * [orientation]), puis garde les lignes complètes à elles seules. Score : lignes associées.
     */
    private fun apparier(an: List<Analyse>, orientation: Orientation): Appariement {
        val pris = BooleanArray(an.size)
        val jambes = ArrayList<Jambe>()
        var score = 0
        val inverse = if (orientation == Orientation.AVANT) Orientation.APRES else Orientation.AVANT
        for (a in an) {
            if (pris[a.index] || !a.typeSeul) continue
            // Type de pari et sélection sur la même ligne (« Vainqueur : Philadelphie 76ers ») : rien à associer
            val seule = fusion(a, emptyList(), solo = true)
            if (seule is Fusion.Ligne && seule.ligne !is LignePari.NonSuivie) {
                pris[a.index] = true
                jambes += Jambe(listOf(a.index), seule.ligne)
                score += 2
                continue
            }
            val j = voisine(an, pris, a.index, orientation, null)
            var f: Fusion = Fusion.Incomplete
            var lignes = listOf(a.index)
            if (j != null) {
                f = fusion(a, listOf(an[j]), solo = false)
                lignes = listOf(a.index, j)
                if (f == Fusion.Incomplete) {
                    val k = voisine(an, pris, a.index, inverse, j)
                    if (k != null) {
                        val f2 = fusion(a, listOf(an[j], an[k]), solo = false)
                        if (f2 != Fusion.Incomplete) {
                            f = f2
                            lignes = listOf(a.index, j, k)
                        }
                    }
                }
            }
            when (f) {
                is Fusion.Ligne -> {
                    lignes.forEach { pris[it] = true }
                    jambes += Jambe(lignes, f.ligne)
                    if (f.ligne !is LignePari.NonSuivie) score += 2 else score++
                }
                Fusion.Ignoree -> {
                    lignes.forEach { pris[it] = true }
                    jambes += Jambe(lignes, null)
                    score++
                }
                Fusion.Incomplete -> {
                    // Type de pari NBA sans sélection lisible : non suivi, s'il nomme un joueur ou une équipe
                    val sel = j?.let { an[it] }
                    val pertinent = (a.nom != null || sel?.nom != null) && (a.basket || sel?.basket == true) ||
                        a.equipes.isNotEmpty() || (sel?.equipes?.isNotEmpty() == true && sel.sens != null)
                    if (pertinent) {
                        lignes.forEach { pris[it] = true }
                        jambes += Jambe(lignes, nonSuivie(lignes.sorted().map { an[it] }))
                    }
                }
            }
        }
        // Seconde chance : type de pari resté seul, sélection libre de l'autre côté
        for (a in an) {
            if (pris[a.index] || !a.typeSeul) continue
            val k = voisine(an, pris, a.index, inverse, null) ?: continue
            val f = fusion(a, listOf(an[k]), solo = false)
            if (f is Fusion.Ligne && f.ligne !is LignePari.NonSuivie) {
                pris[a.index] = true
                pris[k] = true
                jambes += Jambe(listOf(a.index, k), f.ligne)
                score++
            }
        }
        // Lignes complètes à elles seules (« Tyrese Maxey - Points : Plus de 19,5 »)
        for (a in an) {
            if (pris[a.index] || a.bruit || a.contexteSeul) continue
            val f = fusion(a, emptyList(), solo = true)
            if (f is Fusion.Ligne && f.ligne !is LignePari.NonSuivie) {
                pris[a.index] = true
                jambes += Jambe(listOf(a.index), f.ligne)
                score += 2
            }
        }
        // Joueur seul sur sa ligne, sélection à côté sans type de pari (« Tyrese Maxey » / « Plus de 19,5 points »)
        for (a in an) {
            if (pris[a.index] || a.sens == null || a.stat == null || a.nom != null || a.equipes.isNotEmpty() || a.bruit) continue
            val b = listOfNotNull(voisine(an, pris, a.index, orientation, null), voisine(an, pris, a.index, inverse, null))
                .map { an[it] }
                .firstOrNull { it.nom != null && it.sens == null && it.equipes.isEmpty() } ?: continue
            val f = fusion(a, listOf(b), solo = false)
            if (f is Fusion.Ligne && f.ligne is LignePari.Joueur) {
                pris[a.index] = true
                pris[b.index] = true
                jambes += Jambe(listOf(a.index, b.index), f.ligne)
                score += 2
            }
        }
        val restes = an.filter { !pris[it.index] && !it.bruit && !it.contexteSeul }.map { it.index }
        return Appariement(jambes, score, restes)
    }

    /** Ligne voisine qui peut être la sélection (ni bruit, ni ligne de match, ni autre type de pari, ni déjà prise). */
    private fun voisine(an: List<Analyse>, pris: BooleanArray, i: Int, o: Orientation, exclure: Int?): Int? {
        val pas = if (o == Orientation.AVANT) -1 else 1
        var j = i + pas
        while (j in an.indices) {
            val b = an[j]
            if (b.bruit || b.contexteSeul) {
                j += pas
                continue
            }
            if (pris[j] || j == exclure || b.typeSeul) return null
            return j
        }
        return null
    }

    private fun nonSuivie(parts: List<Analyse>): LignePari.NonSuivie {
        val t = parts.joinToString(" · ") { it.brut }
        return LignePari.NonSuivie(if (t.length <= NON_SUIVIE_MAX) t else t.take(NON_SUIVIE_MAX - 1).trimEnd() + "…")
    }

    /**
     * Ce que disent ensemble la ligne [m] et ses voisines [autres] : une ligne suivie, un pari
     * d'un autre sport, ou rien d'assez précis. [solo] : la ligne seule doit nommer explicitement
     * son type (total du match…).
     */
    private fun fusion(m: Analyse, autres: List<Analyse>, solo: Boolean): Fusion {
        val toutes = listOf(m) + autres
        val noms = toutes.mapNotNull { it.nom }.distinctBy { NomsJoueurs.forme(it) }
        if (noms.size > 1) return Fusion.Incomplete
        val seuils = toutes.filter { it.seuil != null }.map { it.sens to it.seuil }.distinct()
        if (seuils.size > 1) return Fusion.Incomplete
        val nom = noms.firstOrNull()
        val sens = seuils.firstOrNull()?.first
        val seuil = seuils.firstOrNull()?.second
        val stat = m.stat ?: autres.firstNotNullOfOrNull { it.stat }
        val equipes = toutes.flatMap { it.equipes }.distinct()
        val basket = toutes.any { it.basket }
        val nba = equipes.isNotEmpty() || (nom != null && basket)
        val parts = toutes.sortedBy { it.index }
        val total = toutes.any { it.total }
        val duMatch = toutes.any { it.duMatch }
        val autreSport = toutes.any { R_AUTRE_SPORT.containsMatchIn(sansAccents(it.brut)) }

        if (toutes.any { it.inconnu }) return if (nba && !autreSport) Fusion.Ligne(nonSuivie(parts)) else Fusion.Ignoree
        if (m.vainqueur && sens == null) {
            // L'équipe choisie : celle de la sélection, sinon celle de la ligne du type de pari
            val choix = autres.map { it.equipes }.firstOrNull { it.isNotEmpty() } ?: m.equipes
            return when {
                choix.size == 1 -> Fusion.Ligne(LignePari.Vainqueur(choix[0], toutes.any { it.reglementaire }))
                equipes.isEmpty() -> if (nom != null && basket && !autreSport) Fusion.Ligne(nonSuivie(parts)) else Fusion.Ignoree
                else -> Fusion.Incomplete
            }
        }
        if (sens == null || seuil == null) return Fusion.Incomplete
        if (autreSport) return Fusion.Ignoree
        if (m.vainqueur) return if (nba) Fusion.Ligne(nonSuivie(parts)) else Fusion.Ignoree
        if (nom != null) {
            if (stat != null) return Fusion.Ligne(LignePari.Joueur(nom, stat, sens, seuil, equipes.toSet()))
            return if (basket || toutes.any { it.motJoueur }) Fusion.Incomplete else Fusion.Ignoree
        }
        if (stat != null && stat != StatPari.POINTS) return if (equipes.isNotEmpty()) Fusion.Ligne(nonSuivie(parts)) else Fusion.Incomplete
        if (toutes.any { it.motJoueur }) return Fusion.Incomplete
        val points = stat == StatPari.POINTS || total
        if (!points) return Fusion.Incomplete
        val motEquipe = toutes.any { it.motEquipe }
        if (equipes.size == 1 && (motEquipe || !duMatch || seuil < 150.0) && (!solo || stat == StatPari.POINTS || total)) {
            return Fusion.Ligne(LignePari.TotalEquipe(equipes[0], sens, seuil))
        }
        if (equipes.size <= 2 && !motEquipe) {
            val explicite = total || duMatch || equipes.size == 2
            if (explicite || (!solo && seuil >= 150.0)) return Fusion.Ligne(LignePari.TotalMatch(sens, seuil, equipes.toSet()))
        }
        return Fusion.Incomplete
    }

    // ------------------------------------------------------------------ match de chaque ligne

    /** Lignes de match : ligne à deux équipes NBA, deux lignes de score (« Sixers 29 8 37 / Pacers 35 4 39 »), match d'un autre sport. */
    private fun indications(an: List<Analyse>): List<Pair<Int, Set<String>>> {
        val r = ArrayList<Pair<Int, Set<String>>>()
        var i = 0
        while (i < an.size) {
            val a = an[i]
            if (a.rencontre != null) {
                r += i to a.rencontre
            } else if (a.score) {
                var j = i + 1
                while (j < an.size && an[j].bruit) j++
                val b = an.getOrNull(j)
                if (b != null && b.score && b.equipes != a.equipes) {
                    r += i to (a.equipes + b.equipes).toSet()
                    i = j
                } else {
                    r += i to a.equipes.toSet()
                }
            }
            i++
        }
        return r
    }

    /**
     * Match d'une ligne de joueur ou d'un total : ses propres équipes, sinon la ligne de match la
     * plus proche du côté attendu (après la jambe quand la sélection précède le type de pari, comme
     * chez Betclic ; avant sinon), à défaut de l'autre côté. Ligne de match d'un autre sport du côté
     * attendu : la ligne est écartée (null) ; de l'autre côté : match inconnu.
     */
    private fun contextualiser(j: Jambe, an: List<Analyse>, indications: List<Pair<Int, Set<String>>>, o: Orientation): LignePari? {
        val l = j.ligne ?: return null
        val propres = j.lignes.flatMap { an[it].equipes }.distinct()
        val debut = j.lignes.min()
        val fin = j.lignes.max()
        val apres = indications.firstOrNull { it.first > fin }?.second
        val avant = indications.lastOrNull { it.first < debut }?.second
        val attendu = if (o == Orientation.AVANT) apres else avant
        val autre = if (o == Orientation.AVANT) avant else apres
        // null : aucun match ; vide : match d'un autre sport (ligne écartée si sans équipe NBA)
        val c: Set<String>? = attendu ?: autre?.takeIf { it.isNotEmpty() }
        if (c != null && c.isEmpty() && propres.isEmpty()) return null
        return when (l) {
            is LignePari.Joueur -> if (propres.isEmpty() && !c.isNullOrEmpty()) l.copy(contexte = c) else l
            is LignePari.TotalMatch -> when {
                propres.size >= 2 || c.isNullOrEmpty() -> l
                propres.isEmpty() || propres.all { it in c } -> l.copy(contexte = c)
                else -> l
            }
            else -> l
        }
    }
}
