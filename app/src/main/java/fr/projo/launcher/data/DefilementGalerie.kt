package fr.projo.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Réglage « Défilement des affiches » du design Galerie : quand personne ne touche à la
 * télécommande, la galerie passe seule au titre suivant toutes les [secondes] secondes (0 : jamais).
 * Enregistré sur l'appareil, 8 s par défaut. Changé depuis Réglages ou depuis le téléphone.
 */
object DefilementGalerie {
    /** Intervalles proposés, en secondes (0 : désactivé). */
    val possibles = listOf(0, 5, 8, 15)
    const val DEFAUT = 8

    private const val FICHIER = "galerie"
    private const val CLE = "defilement"
    private var lu = false

    /** Valeur courante (lue au premier appel de [charger]). */
    var secondes by mutableIntStateOf(DEFAUT)
        private set

    fun charger(context: Context) {
        if (lu) return
        lu = true
        val v = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getInt(CLE, DEFAUT)
        secondes = if (v in possibles) v else DEFAUT
    }

    /** Valeur hors de [possibles] : ignorée. */
    fun ecrire(context: Context, valeur: Int) {
        if (valeur !in possibles) return
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putInt(CLE, valeur).apply()
        lu = true
        secondes = valeur
    }

    /** Choix suivant (tuile des Réglages : chaque OK passe au suivant, en boucle). */
    fun suivant(valeur: Int): Int = possibles[(possibles.indexOf(valeur) + 1).mod(possibles.size)]

    fun nom(valeur: Int): String = if (valeur == 0) "Désactivé" else "Toutes les $valeur s"

    fun detail(valeur: Int): String = when (valeur) {
        0 -> "Les affiches ne changent qu'avec ◀ ▶"
        5 -> "Rapide"
        8 -> "Conseillé"
        else -> "Lent"
    }
}
