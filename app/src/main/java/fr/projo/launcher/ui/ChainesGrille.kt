package fr.projo.launcher.ui

import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo

/**
 * Design « Chaînes » : contenu et navigation de la grille paginée (sans Compose, testable seul).
 *
 * Les tuiles se suivent page après page (12 par page, 4 colonnes × 3 rangées) :
 * favoris, puis « Mes apps » (sans doublon des favoris), puis « Toutes les apps ».
 * S'il n'y a aucun favori, une carte d'aide prend la première place.
 */
internal sealed class TuileChaine {
    /** Étiquette de test, la même que dans les autres designs (favori:…, app:…, biblio, aide). */
    abstract val etiquette: String

    class App(val app: AppInfo, val favori: Boolean) : TuileChaine() {
        override val etiquette: String = (if (favori) "favori:" else "app:") + app.packageName
    }

    object Biblio : TuileChaine() {
        override val etiquette = "biblio"
    }

    object Aide : TuileChaine() {
        override val etiquette = "aide"
    }
}

/** Liste ordonnée des tuiles : [aide], favoris, « Mes apps », « Toutes les apps ». */
internal fun tuilesChaines(ui: HomeUi): List<TuileChaine> = buildList {
    if (ui.loaded && ui.favorites.isEmpty()) add(TuileChaine.Aide)
    ui.favorites.forEach { add(TuileChaine.App(it, favori = true)) }
    val favoris = ui.favorites.mapTo(HashSet()) { it.packageName }
    ui.homeApps.filter { it.packageName !in favoris }.forEach { add(TuileChaine.App(it, favori = false)) }
    add(TuileChaine.Biblio)
}

/** Flèche de la télécommande. */
enum class Sens { GAUCHE, DROITE, HAUT, BAS }

/**
 * Navigation déterministe dans la grille (indices globaux, toutes pages confondues) :
 *  - ◀ ▶ : tuile voisine dans la rangée ; au bord de la grille, page précédente / suivante,
 *    même rangée (sur une dernière page incomplète : sa dernière tuile) ; rien au-delà ;
 *  - ▼ : tuile du dessous ; si la rangée suivante est incomplète, sa dernière tuile ;
 *    s'il n'y a plus de rangée remplie dessous : la bande du bas ([BANDE]) ;
 *  - ▲ : tuile du dessus ; rien depuis la première rangée.
 * « Rien » = l'indice de départ (la touche est consommée, le focus ne bouge pas).
 */
internal object GrilleChaines {
    const val COLONNES = 4
    const val RANGEES = 3
    const val PAR_PAGE = COLONNES * RANGEES

    /** Résultat de [voisin] : le focus descend dans la bande du bas. */
    const val BANDE = -1

    fun pages(n: Int): Int = maxOf(1, (n + PAR_PAGE - 1) / PAR_PAGE)
    fun page(i: Int): Int = i / PAR_PAGE
    fun rangee(i: Int): Int = i % PAR_PAGE / COLONNES
    fun colonne(i: Int): Int = i % COLONNES

    fun voisin(i: Int, n: Int, sens: Sens): Int {
        val p = page(i)
        val r = rangee(i)
        val c = colonne(i)
        return when (sens) {
            Sens.DROITE -> when {
                c < COLONNES - 1 && i + 1 < n -> i + 1
                c == COLONNES - 1 && p < pages(n) - 1 -> minOf((p + 1) * PAR_PAGE + r * COLONNES, n - 1)
                else -> i
            }
            Sens.GAUCHE -> when {
                c > 0 -> i - 1
                p > 0 -> (p - 1) * PAR_PAGE + r * COLONNES + COLONNES - 1
                else -> i
            }
            Sens.BAS -> {
                val debutSuivante = p * PAR_PAGE + (r + 1) * COLONNES
                if (r < RANGEES - 1 && debutSuivante < n) minOf(i + COLONNES, n - 1) else BANDE
            }
            Sens.HAUT -> if (r > 0) i - COLONNES else i
        }
    }

    /**
     * ▲ depuis la bande : pour chaque colonne de la page [p], la tuile la plus basse ;
     * renvoie (colonne, indice) de chacune (colonnes vides exclues).
     */
    fun basDeColonnes(p: Int, n: Int): List<Pair<Int, Int>> = (0 until COLONNES).mapNotNull { c ->
        (RANGEES - 1 downTo 0).map { r -> p * PAR_PAGE + r * COLONNES + c }.firstOrNull { it < n }?.let { c to it }
    }
}
