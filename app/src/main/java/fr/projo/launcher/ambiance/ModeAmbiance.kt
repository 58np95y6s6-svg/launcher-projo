package fr.projo.launcher.ambiance

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.projo.launcher.BuildConfig
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.Rubrique

/**
 * Mode Ambiance : un néon autour de l'écran, réactif à la musique, par-dessus l'app au
 * premier plan (dessiné par le service d'ouverture automatique, voir CadreAmbiance). Son
 * contour suit les bords de l'image : l'ambilight qui lit ces bords réagit aussi.
 *
 * Ici : les réglages (lus par Réglages et par le service), la demande « Ouvrir en mode
 * Ambiance », l'état affiché pour le diagnostic. Tout se passe dans le même processus.
 */
object ModeAmbiance {

    const val LIBELLE_MENU = "Ouvrir en mode Ambiance"

    private const val FICHIER = "ambiance"
    private const val CLE_INTENSITE = "intensite"
    private const val CLE_COULEURS = "couleurs"
    private const val CLE_AUTO = "auto_musique"
    private const val CLE_MICRO = "micro_demande"

    /** Quand afficher le néon (app au premier plan, demande en cours…). */
    val logique = LogiqueCadre(BuildConfig.APPLICATION_ID) { SystemClock.uptimeMillis() }

    private var lu = false

    var intensite by mutableStateOf(Intensite.NORMALE)
        private set
    var couleurs by mutableStateOf(CouleursNeon.POCHETTE)
        private set

    /** Néon automatique dès qu'une app de musique est au premier plan. */
    var auto by mutableStateOf(true)
        private set

    /** Source utilisée (dernière fois que le néon était allumé), null : pas encore allumé. */
    var source by mutableStateOf<SourceAmbiance?>(null)

    /** Détail de la source, pour le diagnostic (« son en direct · couleur de la pochette »…). */
    var diagnostic by mutableStateOf<String?>(null)

    /** App autour de laquelle le néon est affiché en ce moment (null : néon éteint). */
    @Volatile
    var affichePour: String? = null
        internal set

    /** Le service (CadreAmbiance) : prévenu quand une demande ou un réglage change. */
    var surChangement: (() -> Unit)? = null

    fun charger(context: Context) {
        if (lu) return
        lu = true
        val p = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        intensite = Intensite.de(p.getString(CLE_INTENSITE, null))
        couleurs = CouleursNeon.de(p.getString(CLE_COULEURS, null))
        auto = p.getBoolean(CLE_AUTO, true)
    }

    fun ecrireIntensite(context: Context, i: Intensite) {
        charger(context)
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString(CLE_INTENSITE, i.id).apply()
        intensite = i
        surChangement?.invoke()
    }

    fun ecrireCouleurs(context: Context, c: CouleursNeon) {
        charger(context)
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString(CLE_COULEURS, c.id).apply()
        couleurs = c
        surChangement?.invoke()
    }

    fun ecrireAuto(context: Context, actif: Boolean) {
        charger(context)
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putBoolean(CLE_AUTO, actif).apply()
        auto = actif
        surChangement?.invoke()
    }

    /**
     * « Ouvrir en mode Ambiance » : ouvre l'app [paquet] (par [ouvrir], AppRepository.launch)
     * et allume le néon tant qu'elle est au premier plan. false : l'app ne s'est pas ouverte.
     */
    fun ouvrir(context: Context, paquet: String, ouvrir: (String) -> Boolean): Boolean {
        charger(context)
        logique.demander(paquet)
        surChangement?.invoke()
        if (ouvrir(paquet)) return true
        logique.arreter()
        surChangement?.invoke()
        return false
    }

    // ------------------------------------------------------------------ apps de musique

    private val musiques = HashMap<String, Boolean>()

    /** App de la rubrique « Musique et audio » (Spotify, Deezer, radios…) : néon automatique. */
    fun estMusique(context: Context, paquet: String): Boolean = musiques.getOrPut(paquet) {
        AppCategories.rubrique(infoApp(context, paquet)) == Rubrique.MUSIQUE
    }

    /** Rubrique d'une app installée (nom, catégorie déclarée), comme dans la liste des apps. */
    fun infoApp(context: Context, paquet: String): AppInfo {
        val pm = context.packageManager
        val ai: ApplicationInfo? = try {
            pm.getApplicationInfo(paquet, 0)
        } catch (e: Exception) {
            null
        }
        val nom = ai?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }?.trim().orEmpty().ifEmpty { paquet }
        val categorie = if (ai != null && Build.VERSION.SDK_INT >= 26) ai.category else -1
        val systeme = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        return AppInfo(paquet, nom, false, categorie, systeme)
    }

    /** Couleur de l'icône de chaque app déjà vue (repli quand il n'y a pas de pochette). */
    val couleursIcones = HashMap<String, Int>()

    // ------------------------------------------------------------------ autorisations

    fun micAutorise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** L'explication du micro a déjà été montrée (on ne la remontre pas à chaque fois). */
    fun micDemande(context: Context): Boolean =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getBoolean(CLE_MICRO, false)

    fun noterMicDemande(context: Context) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putBoolean(CLE_MICRO, true).apply()
    }

    fun notificationsAutorisees(context: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (e: Exception) {
        false
    }

    /** Composant à autoriser pour la pochette (« paquet/classe complète »). */
    fun composantNotifications(context: Context): String =
        "${context.packageName}/${EcouteNotifications::class.java.name}"

    /** Commandes adb de secours (Mac, dans le dossier platform-tools). */
    fun commandesAdb(context: Context): List<String> = listOf(
        "./adb shell pm grant ${context.packageName} android.permission.RECORD_AUDIO",
        "./adb shell cmd notification allow_listener ${composantNotifications(context)}",
    )

    /** Tout oublier (tests : chaque test repart de zéro). */
    internal fun oublier() {
        lu = false
        intensite = Intensite.NORMALE
        couleurs = CouleursNeon.POCHETTE
        auto = true
        source = null
        diagnostic = null
        affichePour = null
        surChangement = null
        musiques.clear()
        couleursIcones.clear()
        logique.reinitialiser()
    }

    /** Résumé des réglages, pour la tuile de Réglages. */
    fun resume(): String = buildString {
        append(intensite.nom)
        append(" · ")
        append(couleurs.nom.lowercase())
        if (auto) append(" · auto. avec la musique")
        append(" · source : ")
        append(diagnostic ?: "pas encore allumé")
    }
}
