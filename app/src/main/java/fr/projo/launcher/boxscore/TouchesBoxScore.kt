package fr.projo.launcher.boxscore

import android.view.KeyEvent

/**
 * Touches de la télécommande de la TV pour le box score (reçues avant l'appli par le service
 * d'accessibilité), seulement quand elles sont actives : en format détails (◀ ▶ changent
 * d'équipe), et en format pari avec au moins deux combinés concernés (◀ ▶ ouvrent le combiné
 * précédent ou suivant) ; Retour cache alors le box score. L'appui et le relâchement d'une touche
 * gardée sont gardés tous les deux (l'appli ne voit ni l'un ni l'autre) ; toutes les autres
 * touches passent. Sinon (résumé, pari avec un seul combiné), rien n'est gardé.
 * Sans Android (codes et actions de KeyEvent seulement), testé sur la JVM.
 */
class TouchesBoxScore {

    enum class Commande { GAUCHE, DROITE, CACHER }

    /** Touches dont l'appui a été gardé : leur relâchement l'est aussi, quel que soit le format. */
    private val gardees = HashSet<Int>()

    /**
     * true : la touche est gardée. [actives] : le format affiché garde ses touches. [agir] reçoit
     * la commande au premier appui (pas aux répétitions d'une touche maintenue).
     */
    fun touche(code: Int, action: Int, repetition: Int, actives: Boolean, agir: (Commande) -> Unit): Boolean {
        val commande = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Commande.GAUCHE
            KeyEvent.KEYCODE_DPAD_RIGHT -> Commande.DROITE
            KeyEvent.KEYCODE_BACK -> Commande.CACHER
            else -> return false
        }
        return when (action) {
            KeyEvent.ACTION_DOWN -> when {
                // Touche maintenue : même chemin que le premier appui
                repetition > 0 -> code in gardees
                !actives -> {
                    gardees.remove(code)
                    false
                }
                else -> {
                    gardees += code
                    agir(commande)
                    true
                }
            }
            KeyEvent.ACTION_UP -> gardees.remove(code)
            else -> code in gardees
        }
    }
}
