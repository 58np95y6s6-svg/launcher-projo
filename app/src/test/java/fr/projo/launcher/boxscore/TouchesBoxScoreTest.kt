package fr.projo.launcher.boxscore

import android.view.KeyEvent
import fr.projo.launcher.boxscore.TouchesBoxScore.Commande
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Touches de la télécommande de la TV : gardées seulement en format détails (◀ ▶ Retour),
 * appui et relâchement ensemble ; tout le reste passe à l'appli.
 */
class TouchesBoxScoreTest {

    private val t = TouchesBoxScore()
    private val commandes = mutableListOf<Commande>()

    private fun appui(code: Int, details: Boolean, repetition: Int = 0) =
        t.touche(code, KeyEvent.ACTION_DOWN, repetition, details) { commandes += it }

    private fun relache(code: Int, details: Boolean) = t.touche(code, KeyEvent.ACTION_UP, 0, details) { commandes += it }

    @Test
    fun detailsFlechesEtRetourGardees() {
        assertTrue(appui(KeyEvent.KEYCODE_DPAD_RIGHT, details = true))
        assertTrue(relache(KeyEvent.KEYCODE_DPAD_RIGHT, details = true))
        assertTrue(appui(KeyEvent.KEYCODE_DPAD_LEFT, details = true))
        // Touche maintenue : répétitions gardées, sans nouvelle commande
        assertTrue(appui(KeyEvent.KEYCODE_DPAD_LEFT, details = true, repetition = 1))
        assertTrue(relache(KeyEvent.KEYCODE_DPAD_LEFT, details = true))
        assertTrue(appui(KeyEvent.KEYCODE_BACK, details = true))
        // Le box score vient d'être caché : le relâchement de Retour est gardé quand même
        assertTrue(relache(KeyEvent.KEYCODE_BACK, details = false))
        assertEquals(listOf(Commande.EQUIPE_DOM, Commande.EQUIPE_EXT, Commande.CACHER), commandes)
    }

    @Test
    fun lesAutresTouchesPassent() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertFalse(appui(code, details = true))
            assertFalse(relache(code, details = true))
        }
        assertTrue(commandes.isEmpty())
    }

    @Test
    fun resumeRienNEstGarde() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK)) {
            assertFalse(appui(code, details = false))
            assertFalse(appui(code, details = false, repetition = 2))
            assertFalse(relache(code, details = false))
        }
        assertTrue(commandes.isEmpty())
    }

    @Test
    fun appuiCommenceAvantLesDetailsPasseEnEntier() {
        // Appui en résumé, relâchement après le passage en détails : passe à l'appli
        assertFalse(appui(KeyEvent.KEYCODE_BACK, details = false))
        assertFalse(appui(KeyEvent.KEYCODE_BACK, details = true, repetition = 1))
        assertFalse(relache(KeyEvent.KEYCODE_BACK, details = true))
        assertTrue(commandes.isEmpty())
    }
}
