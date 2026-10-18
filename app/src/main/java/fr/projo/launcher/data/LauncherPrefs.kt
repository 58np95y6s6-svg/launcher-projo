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
    val defaultsApplied: Boolean = false,
    /** Action de « Sources » : null = automatique, sinon composant « paquet/classe ». */
    val sourcesComponent: String? = null,
    /** Logo remplacé : paquet de l'app → paquet dont on affiche le logo. */
    val iconOverrides: Map<String, String> = emptyMap(),
)

/** Sauvegarde des préférences avec DataStore (conservées après redémarrage). */
class LauncherPrefs(private val context: Context) {

    private object Cles {
        val FAVORIS = stringPreferencesKey("favoris")
        val MASQUEES = stringSetPreferencesKey("masquees")
        val DEFAUTS = booleanPreferencesKey("favoris_par_defaut_appliques")
        val SOURCES = stringPreferencesKey("sources_composant")
        val LOGOS = stringSetPreferencesKey("logos_remplaces")
    }

    val state: Flow<PrefsState> = context.dataStore.data.map { p ->
        PrefsState(
            favorites = p[Cles.FAVORIS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            hidden = p[Cles.MASQUEES].orEmpty(),
            defaultsApplied = p[Cles.DEFAUTS] ?: false,
            sourcesComponent = p[Cles.SOURCES],
            iconOverrides = p[Cles.LOGOS].orEmpty().mapNotNull { ligne ->
                ligne.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
        )
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
