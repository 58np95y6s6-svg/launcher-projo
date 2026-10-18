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

/**
 * Ce que peut ouvrir la tuile « Source HDMI » : un écran d'app (component) ou une entrée
 * du système TV d'Android (tvInputId, ex. HDMI 2).
 */
data class SourceCandidate(
    val label: String,
    val component: ComponentName? = null,
    val tvInputId: String? = null,
) {
    /** Identifiant mémorisé : « tv:<entrée> » ou « paquet/classe ». */
    val id: String get() = tvInputId?.let { "tv:$it" } ?: component?.flattenToString().orEmpty()
}

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

    /**
     * Vrai Android TV / Google TV ? Sur les projecteurs « Android classique » (ex. COOLiD),
     * l'écran TV de certaines apps ne démarre pas : on préfère alors la version classique,
     * comme le fait l'accueil du fabricant.
     */
    private val estAndroidTv: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Ouvre une app (version TV d'abord sur un vrai Android TV, classique sinon). */
    fun launch(packageName: String): Boolean {
        val tv = pm.getLeanbackLaunchIntentForPackage(packageName)
        val classique = pm.getLaunchIntentForPackage(packageName)
        val essais = if (estAndroidTv) listOfNotNull(tv, classique) else listOfNotNull(classique, tv)
        return essais.any { start(it) }
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

        // Entrées déclarées au système TV d'Android (HDMI 1, HDMI 2…) : ouvertes directement
        try {
            val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? android.media.tv.TvInputManager
            tim?.tvInputList?.forEach { entree ->
                val nom = entree.loadLabel(context)?.toString()?.ifBlank { null } ?: entree.id
                val type = when (entree.type) {
                    android.media.tv.TvInputInfo.TYPE_HDMI -> "HDMI"
                    android.media.tv.TvInputInfo.TYPE_COMPONENT -> "Composante"
                    android.media.tv.TvInputInfo.TYPE_COMPOSITE -> "AV"
                    android.media.tv.TvInputInfo.TYPE_VGA -> "VGA"
                    android.media.tv.TvInputInfo.TYPE_TUNER -> "TNT"
                    else -> "Entrée"
                }
                res += SourceCandidate("Entrée TV · $nom ($type)", tvInputId = entree.id)
            }
        } catch (e: Exception) {
            Log.i(TAG, "Pas de système d'entrées TV : ${e.message}")
        }

        for (app in apps) {
            if (app.isSpecial) continue
            if (pertinent(app.label) || pertinent(app.packageName)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                    ?: pm.getLeanbackLaunchIntentForPackage(app.packageName)
                intent?.component?.let { res += SourceCandidate(app.label, component = it) }
            }
        }
        // Écrans exportés de TOUTES les apps installées, y compris les apps système cachées
        // (sur ces projecteurs, l'écran HDMI est souvent une app système sans icône).
        // On regarde le nom de l'app ET le nom de chaque écran.
        val motCleSysteme = Regex(
            "hdmi|source|signal|tvinput|passthrough|tvd|tvcenter|tvplayer|livetv|inputsource|avin|avinput|externalinput|awsource",
            RegexOption.IGNORE_CASE,
        )
        val pertinentSysteme = { t: String -> motCleSysteme.containsMatchIn(t) && !exclus.containsMatchIn(t) }
        val paquets = try {
            pm.getInstalledApplications(0).map { it.packageName }
        } catch (e: Exception) {
            homeCandidates()
        }.filter { it != context.packageName && it != "android" }
        for (pkg in paquets) {
            val paquetPertinent = pertinentSysteme(pkg)
            val activites = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            val appLabel by lazy {
                try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            }
            for (a in activites) {
                if (!a.exported || !a.enabled) continue
                val court = a.name.substringAfterLast('.')
                if (paquetPertinent || pertinentSysteme(court)) {
                    val cn = ComponentName(a.packageName, a.name)
                    if (res.none { it.component == cn }) res += SourceCandidate("$appLabel · $court", component = cn)
                }
            }
        }
        // Ce qui contient « hdmi » passe en premier, puis « source » / « signal »
        res.sortBy {
            val t = it.label + " " + it.id
            when {
                t.contains("hdmi", true) -> 0
                t.contains("source", true) || t.contains("signal", true) -> 1
                else -> 2
            }
        }
        Log.i(TAG, "Candidats « Sources » : ${res.size}")
        res.forEach { Log.i(TAG, "  ${it.label} → ${it.id}") }
        return res
    }

    companion object {
        const val TAG = "Projo"
    }
}
