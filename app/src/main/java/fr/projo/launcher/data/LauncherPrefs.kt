package fr.projo.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "projo")

/** Réglages enregistrés du launcher. */
data class PrefsState(
    /** Paquets favoris, dans l'ordre d'affichage. */
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Apps de la rangée « Mes apps », dans l'ordre d'affichage. */
    val homeApps: List<String> = emptyList(),
    val homeDefaultsApplied: Boolean = false,
    val defaultsApplied: Boolean = false,
    /** Action de « Sources » : null = automatique, sinon composant « paquet/classe ». */
    val sourcesComponent: String? = null,
    /** Logo remplacé : paquet de l'app → paquet dont on affiche le logo. */
    val iconOverrides: Map<String, String> = emptyMap(),
    /**
     * Design de l'écran d'accueil (identifiant, voir ui.Design). Lu par Design.from : un
     * identifiant inconnu (design supprimé) donne le design par défaut, « Verre ».
     */
    val design: String? = null,
    /** Modules affichés dans la zone de modules (1 ou 2 identifiants, dans l'ordre ; voir modules.RegistreModules). */
    val modules: List<String> = MODULES_PAR_DEFAUT,
    /** Réglages des modules : « idModule.clé » → valeur (voir modules.ReglagesModule). */
    val reglagesModules: Map<String, String> = emptyMap(),
)

/** Sans choix de l'utilisateur : le module Sport (NBA seule), comme l'ancien bandeau NBA. */
val MODULES_PAR_DEFAUT = listOf("sport")

/** Sauvegarde des préférences avec DataStore (conservées après redémarrage). */
class LauncherPrefs(private val context: Context) {

    private object Cles {
        val FAVORIS = stringPreferencesKey("favoris")
        val MASQUEES = stringSetPreferencesKey("masquees")
        val DEFAUTS = booleanPreferencesKey("favoris_par_defaut_appliques")
        val SOURCES = stringPreferencesKey("sources_composant")
        val LOGOS = stringSetPreferencesKey("logos_remplaces")
        val ACCUEIL = stringPreferencesKey("apps_accueil")
        val ACCUEIL_DEFAUTS = booleanPreferencesKey("apps_accueil_par_defaut_appliquees")
        val DESIGN = stringPreferencesKey("design")
        val MODULES = stringPreferencesKey("modules")
        val REGLAGES_MODULES = stringSetPreferencesKey("reglages_modules")
    }

    val state: Flow<PrefsState> = context.dataStore.data.map { p ->
        PrefsState(
            favorites = p[Cles.FAVORIS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            hidden = p[Cles.MASQUEES].orEmpty(),
            defaultsApplied = p[Cles.DEFAUTS] ?: false,
            sourcesComponent = p[Cles.SOURCES],
            homeApps = p[Cles.ACCUEIL]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            homeDefaultsApplied = p[Cles.ACCUEIL_DEFAUTS] ?: false,
            iconOverrides = p[Cles.LOGOS].orEmpty().mapNotNull { ligne ->
                ligne.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
            design = p[Cles.DESIGN],
            modules = p[Cles.MODULES]?.split('\n')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: MODULES_PAR_DEFAUT,
            reglagesModules = p[Cles.REGLAGES_MODULES].orEmpty().mapNotNull { ligne ->
                ligne.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
        )
    }

    /** Modules de la zone de modules (1 ou 2, dans l'ordre d'affichage). */
    suspend fun setModules(ids: List<String>) {
        context.dataStore.edit { it[Cles.MODULES] = ids.distinct().take(2).joinToString("\n") }
    }

    /** Réglage d'un module ; valeur = null : retour à la valeur par défaut du module. */
    suspend fun setReglageModule(idModule: String, cle: String, valeur: String?) {
        val prefixe = "$idModule.$cle="
        context.dataStore.edit { p ->
            val actuel = p[Cles.REGLAGES_MODULES].orEmpty().filterNot { it.startsWith(prefixe) }.toMutableSet()
            if (valeur != null) actuel += prefixe + valeur.replace('\n', ' ')
            p[Cles.REGLAGES_MODULES] = actuel
        }
    }

    suspend fun setDesign(id: String) {
        context.dataStore.edit { it[Cles.DESIGN] = id }
    }

    suspend fun setFavorites(list: List<String>) {
        context.dataStore.edit { it[Cles.FAVORIS] = list.distinct().joinToString("\n") }
    }

    suspend fun applyDefaultFavorites(list: List<String>) {
        context.dataStore.edit {
            it[Cles.FAVORIS] = list.distinct().joinToString("\n")
            it[Cles.DEFAUTS] = true
        }
    }

    suspend fun setHomeApps(list: List<String>) {
        context.dataStore.edit { it[Cles.ACCUEIL] = list.distinct().joinToString("\n") }
    }

    suspend fun applyDefaultHomeApps(list: List<String>) {
        context.dataStore.edit {
            it[Cles.ACCUEIL] = list.distinct().joinToString("\n")
            it[Cles.ACCUEIL_DEFAUTS] = true
        }
    }

    suspend fun setHidden(set: Set<String>) {
        context.dataStore.edit { it[Cles.MASQUEES] = set }
    }

    suspend fun setSourcesComponent(component: String?) {
        context.dataStore.edit {
            if (component == null) it.remove(Cles.SOURCES) else it[Cles.SOURCES] = component
        }
    }

    /** source = null : retour au logo d'origine ; source = paquet : logo de cette app. */
    suspend fun setIconOverride(pkg: String, source: String?) {
        context.dataStore.edit { p ->
            val actuel = p[Cles.LOGOS].orEmpty().filterNot { it.startsWith("$pkg=") }.toMutableSet()
            if (source != null) actuel += "$pkg=$source"
            p[Cles.LOGOS] = actuel
        }
    }
}
