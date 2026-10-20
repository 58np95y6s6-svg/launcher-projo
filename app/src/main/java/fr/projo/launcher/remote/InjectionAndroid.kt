package fr.projo.launcher.remote

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Exécute les commandes d'un téléphone authentifié (appelé sur le fil de la télécommande).
 *
 * Touches de navigation, dans l'ordre :
 * 1. clavier iBiProjo lié à un champ texte → InputConnection.sendKeyEvent ;
 * 2. iBiProjo au premier plan → injection dans sa propre fenêtre (permise sans droit
 *    particulier quand la fenêtre visée appartient à l'appli) ;
 * 3. service d'accessibilité actif → actions globales, focus voisin, clic ;
 * 4. sinon rien (repli silencieux).
 * Volume : AudioManager. Lecture/pause : touche multimédia envoyée par AudioManager.
 */
class InjectionAndroid(private val context: Context) : Injecteur {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val instrumentation by lazy { Instrumentation() }
    private var muet = false

    override fun touche(k: String) {
        try {
            when (k) {
                "vol+" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "vol-" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> couperLeSon()
                "playpause" -> multimedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "home" -> accueil()
                else -> naviguer(k)
            }
        } catch (e: Exception) {
            // repli silencieux
        }
    }

    override fun texte(s: String, curseur: Int?) {
        ClavierTelecommande.instance?.remplacerTexte(s, curseur)
    }

    override fun valider() {
        val clavier = ClavierTelecommande.instance
        if (clavier != null && clavier.lieAUnChamp) clavier.valider()
        else injecterDansIBiProjo(KeyEvent.KEYCODE_ENTER)
    }

    private fun naviguer(k: String) {
        val code = when (k) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
            "back" -> KeyEvent.KEYCODE_BACK
            "menu" -> KeyEvent.KEYCODE_MENU
            else -> return
        }
        val clavier = ClavierTelecommande.instance
        if (clavier != null && clavier.lieAUnChamp && clavier.envoyerTouche(code)) return
        if (injecterDansIBiProjo(code)) return
        HoteNavigation.executer(k)
    }

    /** Injection dans la fenêtre d'iBiProjo, seulement si elle est au premier plan. */
    private fun injecterDansIBiProjo(code: Int): Boolean {
        if (Telecommande.activiteAuPremierPlan == null) return false
        return try {
            instrumentation.sendKeyDownUpSync(code)
            true
        } catch (e: Exception) {
            // Fenêtre d'une autre appli au premier plan (SecurityException) : on passe au suivant
            false
        }
    }

    private fun accueil() {
        if (HoteNavigation.executer("home")) return
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun couperLeSon() {
        if (Build.VERSION.SDK_INT >= 23) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        } else {
            muet = !muet
            @Suppress("DEPRECATION")
            audio.setStreamMute(AudioManager.STREAM_MUSIC, muet)
        }
    }

    private fun multimedia(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
