package fr.projo.launcher.modules.minuteur

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.NotificationManagerCompat
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.ui.LocalScale
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran plein écran de l'alarme, ouvert par-dessus l'app en cours à l'échéance du minuteur
 * ou du réveil : « Arrêter » (sélectionné d'office) et « +5 min ». Retour arrête aussi.
 * Le son vient de la notification (canal d'alarme) ; si les notifications de Projo sont
 * désactivées, l'écran joue lui-même la sonnerie. Arrêt automatique après 5 minutes.
 */
class AlarmeActivity : ComponentActivity() {

    private var secours: SonnerieSecours? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Plus rien ne sonne (déjà arrêtée depuis la notification…) : rien à afficher
        if (Minuteur.etat(this).mode != ModeMinuteur.SONNE) {
            finish()
            return
        }
        if (!notificationsActives()) secours = SonnerieSecours(this).also { it.demarrer() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Minuteur.arreter(this@AlarmeActivity)
            }
        })

        setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    EcranAlarme(this)
                }
            }
        }
    }

    private fun notificationsActives(): Boolean = try {
        NotificationManagerCompat.from(this).areNotificationsEnabled()
    } catch (e: Exception) {
        false
    }

    override fun onDestroy() {
        secours?.arreter()
        secours = null
        super.onDestroy()
    }
}

@Composable
private fun EcranAlarme(activite: ComponentActivity) {
    val context = activite as Context
    val etat by remember { Minuteur.flux(context) }.collectAsState()
    // Arrêtée (ici, depuis la notification ou le module) : on ferme
    LaunchedEffect(etat.mode) {
        if (etat.mode != ModeMinuteur.SONNE) activite.finish()
    }
    LaunchedEffect(Unit) {
        delay(Minuteur.DUREE_SONNERIE)
        Minuteur.arreter(context)
    }
    val arreter = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { arreter.requestFocus() }
    }
    val heure = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(if (etat.fin > 0L) etat.fin else System.currentTimeMillis()))
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0D12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (etat.reveil) "RÉVEIL" else "MINUTEUR",
                color = Color.White.copy(alpha = 0.6f), fontSize = 16.us, letterSpacing = 4.us,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.u))
            Text(
                if (etat.reveil) heure else "Temps écoulé",
                color = Color.White, fontSize = 64.us, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            Spacer(Modifier.height(8.u))
            Text(
                if (etat.reveil) "Il est l'heure" else "Minuteur de " + Minuteur.formaterMinutes((etat.duree / 60_000L).toInt().coerceAtLeast(1)) + " terminé à " + heure,
                color = Color.White.copy(alpha = 0.75f), fontSize = 18.us, maxLines = 1,
            )
            Spacer(Modifier.height(36.u))
            Row(horizontalArrangement = Arrangement.spacedBy(16.u)) {
                BoutonAlarme("Arrêter", Modifier.focusRequester(arreter).testTag("alarme:arreter")) {
                    Minuteur.arreter(context)
                }
                BoutonAlarme("+5 min", Modifier.testTag("alarme:repeter")) {
                    Minuteur.repeter(context, 5)
                }
            }
        }
    }
}

@Composable
private fun BoutonAlarme(texte: String, modifier: Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(30.u)
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.u).width(220.u),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B0D12),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.3f)), shape = forme),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.u), contentAlignment = Alignment.Center) {
            Text(texte, fontSize = 22.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Sonnerie jouée par l'écran d'alarme quand les notifications sont désactivées : sonnerie
 * d'alarme de l'appareil en boucle, ou bips (ToneGenerator) si elle est illisible.
 */
private class SonnerieSecours(private val context: Context) {
    private var lecteur: MediaPlayer? = null
    private var bips: ToneGenerator? = null
    private val main = Handler(Looper.getMainLooper())
    private val bip = object : Runnable {
        override fun run() {
            runCatching { bips?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800) }
            main.postDelayed(this, 1500L)
        }
    }

    fun demarrer() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: error("pas de sonnerie")
            lecteur = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("ProjoMinuteur", "Sonnerie illisible : bips", e)
            runCatching { lecteur?.release() }
            lecteur = null
            try {
                bips = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                main.post(bip)
            } catch (ex: Exception) {
                Log.w("ProjoMinuteur", "Bips impossibles", ex)
            }
        }
    }

    fun arreter() {
        main.removeCallbacks(bip)
        runCatching { lecteur?.stop() }
        runCatching { lecteur?.release() }
        lecteur = null
        runCatching { bips?.release() }
        bips = null
    }
}
