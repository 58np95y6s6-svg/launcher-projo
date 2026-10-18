package fr.projo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/** Une app installée qu'on peut lancer. */
data class AppInfo(
    val packageName: String,
    val label: String,
    /** true si l'app déclare une version TV (catégorie LEANBACK_LAUNCHER). */
    val isTvApp: Boolean,
) {
    /** Tuile propre à Projo (ex. « Source HDMI »), pas une vraie app. */
    val isSpecial: Boolean get() = packageName.startsWith("projo:")

    companion object {
        const val HDMI_ID = "projo:hdmi"
        val HDMI = AppInfo(HDMI_ID, "Source HDMI", isTvApp = true)
    }
}

/** Une activité candidate pour « Sources » (app ou écran d'une app). */
data class SourceCandidate(val label: String, val component: ComponentName)

/** Lit la liste des apps installées via le PackageManager. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Toutes les apps lançables, triées par nom, sans le launcher lui-même.
     * Apps TV (LEANBACK_LAUNCHER) d'abord, complétées par les apps classiques (LAUNCHER) :
     * sur un projecteur Android « non TV », la plupart sont classiques.
     * À appeler hors du thread principal.
     */
    fun loadApps(): List<AppInfo> {
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val classiques = query(Intent.CATEGORY_LAUNCHER)

        val parPaquet = LinkedHashMap<String, AppInfo>()
        for (ri in tv) ajouter(parPaquet, ri, isTv = true)
        for (ri in classiques) ajouter(parPaquet, ri, isTv = false)

        val apps = parPaquet.values.sortedBy { it.label.lowercase() }
        Log.i(TAG, "${apps.size} apps trouvées (${tv.size} TV, ${classiques.size} classiques)")
        apps.forEach { Log.i(TAG, "  ${it.label} → ${it.packageName}${if (it.isTvApp) " [TV]" else ""}") }
        return apps
    }

    private fun query(categorie: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(categorie)
        return try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Lecture des apps impossible ($categorie)", e)
            emptyList()
        }
    }

    private fun ajouter(map: MutableMap<String, AppInfo>, ri: ResolveInfo, isTv: Boolean) {
        val pkg = ri.activityInfo?.packageName ?: return
        if (pkg == context.packageName || map.containsKey(pkg)) return
        val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
        map[pkg] = AppInfo(pkg, label, isTv)
    }

    /** Ouvre une app : version TV si elle existe, sinon version classique. */
    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return start(intent)
    }

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Impossible d'ouvrir $intent", e)
        false
    }

    /** Paquet de l'écran d'accueil utilisé par le bouton Maison (null si aucun choix). */
    fun currentHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName
        // « android » = boîte de choix système : aucun accueil par défaut n'est défini
        return if (pkg == "android") null else pkg
    }

    /** Tous les écrans d'accueil disponibles sur l'appareil. */
    fun homeCandidates(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    /**
     * Cherche ce qui pourrait ouvrir l'entrée HDMI : apps dont le nom évoque une source,
     * et écrans exportés des autres launchers (ex. accueil du fabricant) dont le nom de classe
     * contient « hdmi », « source » ou « input ». Tout est affiché dans les logs.
     */
    fun sourceCandidates(apps: List<AppInfo>): List<SourceCandidate> {
        // Mots qui évoquent une entrée vidéo… mais pas les claviers (« input method ») ni les langues
        val motCle = Regex("hdmi|source|signal|entr[ée]e|passthrough|tvinput|av ?in", RegexOption.IGNORE_CASE)
        val exclus = Regex("method|keyboard|clavier|lang|locale|saisie|setting|password|voice", RegexOption.IGNORE_CASE)
        val pertinent = { texte: String -> motCle.containsMatchIn(texte) && !exclus.containsMatchIn(texte) }
        val res = mutableListOf<SourceCandidate>()

        for (app in apps) {
            if (app.isSpecial) continue
            if (pertinent(app.label) || pertinent(app.packageName)) {
                val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                    ?: pm.getLaunchIntentForPackage(app.packageName)
                intent?.component?.let { res += SourceCandidate(app.label, it) }
            }
        }
        // Écrans exportés des autres écrans d'accueil (ex. COOLiD) et des apps système
        val paquets = (homeCandidates() + apps.filterNot { it.isSpecial }.map { it.packageName }).distinct()
            .filter { it != context.packageName && it != "android" }
        for (pkg in paquets) {
            val activites = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            for (a in activites) {
                if (!a.exported) continue
                val court = a.name.substringAfterLast('.')
                if (pertinent(court)) {
                    val cn = ComponentName(a.packageName, a.name)
                    if (res.none { it.component == cn }) {
                        val appLabel = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) { pkg }
                        res += SourceCandidate("$appLabel · $court", cn)
                    }
                }
            }
        }
        // Ce qui contient « hdmi » passe en premier
        res.sortBy { if (it.label.contains("hdmi", true) || it.component.className.contains("hdmi", true)) 0 else 1 }
        Log.i(TAG, "Candidats « Sources » : ${res.size}")
        res.forEach { Log.i(TAG, "  ${it.label} → ${it.component.flattenToShortString()}") }
        return res
    }

    companion object {
        const val TAG = "Projo"
    }
}
