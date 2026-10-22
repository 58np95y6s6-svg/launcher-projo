package fr.projo.launcher.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Musique en arrière-plan (Spotify…) : mise en pause quand on ouvre une app vidéo ou TV
 * depuis iBiProjo. Sans ça, Android laisse la musique continuer tant que l'app ouverte n'a
 * pas commencé à lire elle-même (par exemple sur l'accueil de YouTube).
 *
 * Comment : iBiProjo demande le son « pour de bon » (comme une vidéo qui démarre), le lecteur
 * en cours se met en pause, puis iBiProjo rend la main aussitôt. L'app ouverte reprend le son
 * quand elle lit quelque chose. Ouvrir une app de musique ne coupe rien.
 */
object PauseMusique {

    private const val TAG = "Projo"

    /** Avant d'ouvrir [paquet] : met la musique en pause si c'est une app vidéo ou TV. */
    fun avantOuverture(context: Context, paquet: String?) {
        if (paquet == null) return
        try {
            if (coupeLaMusique(context, paquet)) pauser(context)
        } catch (e: Exception) {
            Log.w(TAG, "Pause de la musique impossible", e)
        }
    }

    /** App vidéo ou TV (même classement que la bibliothèque « Toutes les apps »). */
    fun coupeLaMusique(context: Context, paquet: String): Boolean {
        val pm = context.packageManager
        val info = try {
            pm.getApplicationInfo(paquet, 0)
        } catch (e: Exception) {
            return false
        }
        val app = AppInfo(
            packageName = paquet,
            label = pm.getApplicationLabel(info).toString(),
            isTvApp = false,
            categorieSysteme = if (Build.VERSION.SDK_INT >= 26) info.category else -1,
        )
        return AppCategories.rubrique(app) == Rubrique.STREAMING || AppCategories.rubrique(app) == Rubrique.TV
    }

    /** Met en pause la musique en cours, s'il y en a. */
    fun pauser(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!am.isMusicActive) return
        val ecoute = AudioManager.OnAudioFocusChangeListener { }
        if (Build.VERSION.SDK_INT >= 26) {
            val demande = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                )
                .setOnAudioFocusChangeListener(ecoute)
                .build()
            if (am.requestAudioFocus(demande) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                am.abandonAudioFocusRequest(demande)
            }
        } else {
            @Suppress("DEPRECATION")
            if (am.requestAudioFocus(ecoute, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(ecoute)
            }
        }
    }
}
