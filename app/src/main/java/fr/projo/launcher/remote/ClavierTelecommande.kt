package fr.projo.launcher.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Clavier iBiProjo : le texte tapé sur le téléphone arrive dans le champ actif de n'importe
 * quelle appli. Sur la TV, seul un bandeau discret « Saisie depuis le téléphone » s'affiche.
 *
 * - `text` : remplace le contenu du champ (voir [EditionTexte.remplacer]) ;
 * - `submit` : action de l'éditeur (Rechercher, OK…) ou Entrée ;
 * - flèches / OK / Retour : InputConnection.sendKeyEvent tant qu'un champ est lié ;
 * - focus : `focus_text` (type tiré d'EditorInfo) à l'arrivée sur un champ, `focus_lost` ensuite.
 *
 * Aucun téléphone connecté et l'utilisateur ouvre un champ à la télécommande : on rend la main
 * au clavier précédent (sinon il ne pourrait plus rien taper). Quand un téléphone se connecte,
 * le clavier iBiProjo est re-sélectionné si l'appli a la permission WRITE_SECURE_SETTINGS
 * (donnée une fois par adb, voir l'aide dans Réglages).
 */
class ClavierTelecommande : InputMethodService() {

    private val principal = Handler(Looper.getMainLooper())
    private var info: EditorInfo? = null

    /** Un champ texte est lié (EditorInfo d'une classe texte, nombre…). */
    @Volatile
    var lieAUnChamp = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Telecommande.demarrer(this)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt() }
        val bandeau = TextView(this).apply {
            text = getString(fr.projo.launcher.R.string.clavier_bandeau)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(Color.argb(0xD9, 0x16, 0x1A, 0x22))
            }
        }
        return FrameLayout(this).apply {
            setPadding(0, 0, 0, dp(18f))
            addView(bandeau, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
        }
    }

    /** Jamais de plein écran (« extract view ») : la TV n'affiche que le bandeau. */
    override fun onEvaluateFullscreenMode() = false

    /** Bandeau seulement quand un téléphone est là pour écrire. */
    override fun onEvaluateInputViewShown(): Boolean = lieAUnChamp && Telecommande.telephoneConnecte

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (lieAUnChamp && !Telecommande.telephoneConnecte && cederLaMain()) return false
        return super.onShowInputRequested(flags, configChange)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        info = attribute
        val type = EditionTexte.typeChamp(attribute)
        lieAUnChamp = type != null
        if (type == null) {
            Telecommande.champPerdu()
            return
        }
        val texte = if (type == Protocole.TypeChamp.MOT_DE_PASSE) "" else currentInputConnection?.let { EditionTexte.lire(it) } ?: ""
        Telecommande.champTexte(InfoChamp(type, texte, EditionTexte.actionChamp(attribute)))
    }

    override fun onFinishInput() {
        lieAUnChamp = false
        info = null
        Telecommande.champPerdu()
        super.onFinishInput()
    }

    // ------------------------------------------------------------------ commandes du téléphone

    fun remplacerTexte(s: String, curseur: Int?) = principal.post {
        if (!lieAUnChamp) return@post
        currentInputConnection?.let { EditionTexte.remplacer(it, s, curseur) }
    }

    fun valider() = principal.post {
        if (!lieAUnChamp) return@post
        currentInputConnection?.let { EditionTexte.valider(it, info) }
    }

    /** Touche envoyée au champ ; false si aucun champ n'est lié. */
    fun envoyerTouche(code: Int): Boolean {
        if (!lieAUnChamp) return false
        principal.post { currentInputConnection?.let { EditionTexte.touche(it, code) } }
        return true
    }

    /** Passe au clavier précédent ; true si c'est fait. */
    private fun cederLaMain(): Boolean {
        val fait = try {
            if (Build.VERSION.SDK_INT >= 28) {
                switchToPreviousInputMethod()
            } else {
                val token = window?.window?.attributes?.token ?: return false
                @Suppress("DEPRECATION")
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).switchToLastInputMethod(token)
            }
        } catch (e: Exception) {
            false
        }
        if (fait) prefs(this).edit().putBoolean(CLE_CEDE, true).apply()
        return fait
    }

    companion object {
        private const val CLE_CEDE = "clavier_cede"

        /** Clavier en service (null si un autre clavier est choisi). */
        @Volatile
        var instance: ClavierTelecommande? = null
            private set

        private fun prefs(context: Context) = context.getSharedPreferences("telecommande", Context.MODE_PRIVATE)

        /** Identifiant du clavier pour les réglages et adb : « fr.projo.launcher/.remote.ClavierTelecommande ». */
        fun identifiant(context: Context): String =
            ComponentName(context, ClavierTelecommande::class.java).flattenToShortString()

        /** Clavier activé dans les réglages d'Android. */
        fun estActive(context: Context): Boolean =
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.any { it.packageName == context.packageName }

        /** Clavier choisi comme clavier actuel. */
        fun estSelectionne(context: Context): Boolean {
            val actuel = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
            return ComponentName.unflattenFromString(actuel)?.packageName == context.packageName
        }

        fun peutSeReselectionner(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /** Téléphone connecté ou parti : affiche ou cache le bandeau. */
        fun rafraichir() {
            val c = instance ?: return
            c.principal.post { runCatching { c.updateInputViewShown() } }
        }

        /** Un téléphone vient de se connecter : reprend la place cédée au clavier précédent. */
        fun reprendreSiPossible(context: Context) {
            try {
                if (!prefs(context).getBoolean(CLE_CEDE, false)) return
                if (!estActive(context) || estSelectionne(context) || !peutSeReselectionner(context)) return
                Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, identifiant(context))
                prefs(context).edit().putBoolean(CLE_CEDE, false).apply()
            } catch (e: Exception) {
                // permission retirée entre-temps : rien
            }
        }
    }
}
