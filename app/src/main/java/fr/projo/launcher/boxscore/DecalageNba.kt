package fr.projo.launcher.boxscore

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Réglage `nba_decalage` : retard des scores et des stats du box score (secondes), pour suivre la
 * vidéo sans spoiler. Enregistré sur l'appareil, aucun par défaut. Changé depuis le téléphone.
 */
object DecalageNba {
    /** Retards proposés, en secondes. */
    val possibles = listOf(0, 15, 30, 45, 60)
    const val DEFAUT = 0

    private const val FICHIER = "nba"
    private const val CLE = "decalage_s"
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

    /** Valeur hors de [possibles] : ignorée. Fil principal. */
    fun ecrire(context: Context, valeur: Int) {
        if (valeur !in possibles) return
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putInt(CLE, valeur).apply()
        lu = true
        secondes = valeur
        BoxScoreNba.reglerDecalage(valeur)
    }

    /** « Aucun », « 15 s »… */
    fun nom(valeur: Int): String = if (valeur == 0) "Aucun" else "$valeur s"
}
