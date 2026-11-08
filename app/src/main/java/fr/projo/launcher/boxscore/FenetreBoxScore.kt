package fr.projo.launcher.boxscore

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.projo.launcher.ui.LocalScale

/**
 * La fenêtre du box score : une seule fenêtre « TYPE_ACCESSIBILITY_OVERLAY » du service
 * d'accessibilité relié (aucune autorisation de plus), ni focalisable ni touchable, en haut à
 * droite, à la taille du contenu. Elle n'existe que pendant l'affichage : retirée (composition
 * comprise) dès que le box score est caché. Fil principal.
 */
internal class FenetreBoxScore(private val service: AccessibilityService) {

    private var vue: ComposeView? = null
    private var proprietaire: ProprietaireFenetre? = null

    /** Affiche la fenêtre (rien si elle l'est déjà) ; false si le système la refuse. */
    fun afficher(): Boolean {
        if (vue != null) return true
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val p = ProprietaireFenetre()
        val v = ComposeView(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            isClickable = false
            setViewTreeLifecycleOwner(p)
            setViewTreeSavedStateRegistryOwner(p)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val etat by BoxScoreNba.vue.collectAsState()
                // Même échelle que l'accueil (écran de référence 960 × 540 dp) ; taille des textes du système ignorée
                val echelle = LocalConfiguration.current.screenWidthDp / 960f
                CompositionLocalProvider(
                    LocalScale provides echelle,
                    LocalDensity provides Density(LocalDensity.current.density, 1f),
                ) {
                    EcranBoxScore(etat)
                }
            }
        }
        p.demarrer()
        return try {
            wm.addView(v, parametres())
            vue = v
            proprietaire = p
            true
        } catch (e: Exception) {
            Log.w("Projo.Nba", "nba : fenêtre refusée")
            p.arreter()
            false
        }
    }

    /** Retire la fenêtre et sa composition. */
    fun cacher() {
        val v = vue ?: return
        vue = null
        try {
            (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeViewImmediate(v)
        } catch (e: Exception) {
            // déjà retirée (service arrêté)
        }
        try {
            v.disposeComposition()
        } catch (e: Exception) {
            // déjà libérée
        }
        proprietaire?.arreter()
        proprietaire = null
    }

    private fun parametres() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 0
        y = 0
        windowAnimations = 0
        title = "iBiTV · box score NBA"
    }
}

/**
 * Cycle de vie et état sauvegardé d'une ComposeView hors d'une activité : « reprise » pendant
 * que la fenêtre est affichée, « détruit » quand elle est retirée.
 */
private class ProprietaireFenetre : LifecycleOwner, SavedStateRegistryOwner {
    private val registre = LifecycleRegistry(this)
    private val sauvegarde = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registre
    override val savedStateRegistry: SavedStateRegistry get() = sauvegarde.savedStateRegistry

    fun demarrer() {
        sauvegarde.performRestore(null)
        registre.currentState = Lifecycle.State.RESUMED
    }

    fun arreter() {
        if (registre.currentState.isAtLeast(Lifecycle.State.CREATED)) registre.currentState = Lifecycle.State.DESTROYED
    }
}
