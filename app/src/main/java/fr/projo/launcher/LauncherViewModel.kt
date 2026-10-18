package fr.projo.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.DefaultFavorites
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.LauncherPrefs
import fr.projo.launcher.data.NbaNight
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.data.PrefsState
import fr.projo.launcher.data.SourceCandidate
import fr.projo.launcher.system.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce qu'affiche l'écran d'accueil. */
data class HomeUi(
    val loaded: Boolean = false,
    val favorites: List<AppInfo> = emptyList(),
    /** Rangée « Mes apps » : seulement les apps choisies (hors favoris). */
    val homeApps: List<AppInfo> = emptyList(),
    /** Bibliothèque : toutes les apps visibles (et la tuile HDMI), par ordre alphabétique. */
    val allApps: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    val prefs: PrefsState = PrefsState(),
    /** Paquet dont on affiche le logo, pour les apps dont le logo est remplacé. */
    val iconSources: Map<String, String> = emptyMap(),
) {
    fun iconSourceOf(pkg: String): String = iconSources[pkg] ?: pkg
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val repo = AppRepository(app)
    val icons = IconLoader(app)
    val actions = SystemActions(app, repo)
    private val prefs = LauncherPrefs(app)

    private val apps = MutableStateFlow<List<AppInfo>?>(null)

    // ------------------------------------------------------------------ NBA
    val nba = NbaRepository(app)

    /** null = pas encore chargé ; NbaNight vide = aucun match ; erreur = réseau indisponible. */
    private val _nbaNight = MutableStateFlow<NbaNight?>(null)
    val nbaNight: StateFlow<NbaNight?> = _nbaNight
    private val _nbaErreur = MutableStateFlow(false)
    val nbaErreur: StateFlow<Boolean> = _nbaErreur

    /**
     * Met à jour les matchs tant que l'accueil est affiché (appelée depuis l'interface,
     * annulée quand on quitte l'accueil) : toutes les minutes s'il y a un match en cours,
     * toutes les 10 minutes sinon.
     */
    suspend fun nbaLoop() {
        while (true) {
            val nuit = try {
                withContext(Dispatchers.IO) { nba.fetchNight() }
            } catch (e: Exception) {
                null
            }
            if (nuit != null) {
                _nbaNight.value = nuit
                _nbaErreur.value = false
            } else {
                _nbaErreur.value = true
            }
            val enDirect = nuit?.games?.any { it.state == "in" } == true
            delay(if (enDirect) 60_000L else if (nuit == null) 120_000L else 600_000L)
        }
    }
    private val _sources = MutableStateFlow<List<SourceCandidate>>(emptyList())
    val sources: StateFlow<List<SourceCandidate>> = _sources

    val ui: StateFlow<HomeUi> = combine(apps, prefs.state) { liste, p ->
        if (liste == null) return@combine HomeUi(prefs = p)
        val parPaquet = liste.associateBy { it.packageName }
        val visibles = liste.filter { it.packageName !in p.hidden }
        val favoris = p.favorites.mapNotNull { parPaquet[it] }.filter { it.packageName !in p.hidden }
        HomeUi(
            loaded = true,
            favorites = favoris,
            homeApps = p.homeApps.mapNotNull { parPaquet[it] }
                .filter { it.packageName !in p.hidden && it !in favoris },
            allApps = visibles,
            hiddenApps = liste.filter { it.packageName in p.hidden },
            prefs = p,
            iconSources = logosRemplaces(liste, p),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

    init {
        reload()
    }

    /**
     * Logos remplacés : choix de l'utilisateur, plus une règle automatique :
     * SmartTube prend le logo de l'app YouTube si elle est installée.
     */
    private fun logosRemplaces(liste: List<AppInfo>, p: PrefsState): Map<String, String> {
        val res = mutableMapOf<String, String>()
        val youtube = liste.firstOrNull { DefaultFavorites.normaliser(it.label) == "youtube" }
        if (youtube != null) {
            liste.filter { DefaultFavorites.normaliser(it.label).startsWith("smarttube") }
                .forEach { res[it.packageName] = youtube.packageName }
        }
        val installes = liste.map { it.packageName }.toSet()
        p.iconOverrides.forEach { (pkg, source) ->
            if (source == pkg) res.remove(pkg) // « logo d'origine » choisi explicitement
            else if (source in installes) res[pkg] = source
        }
        return res
    }

    /** source = paquet dont on veut le logo ; source = pkg lui-même = logo d'origine. */
    fun setIconOverride(pkg: String, source: String) = viewModelScope.launch {
        prefs.setIconOverride(pkg, source)
    }

    /** Relit les apps installées (au démarrage et quand une app est installée/supprimée). */
    fun reload() {
        viewModelScope.launch {
            // Les vraies apps + la tuile « Source HDMI » propre à Projo
            val liste = (withContext(Dispatchers.IO) { repo.loadApps() } + AppInfo.HDMI)
                .sortedBy { it.label.lowercase() }
            apps.value = liste
            val p = prefs.state.first()
            if (!p.defaultsApplied) {
                prefs.applyDefaultFavorites(DefaultFavorites.find(liste))
            }
            if (!p.homeDefaultsApplied) {
                prefs.applyDefaultHomeApps(DefaultFavorites.findHomeApps(liste))
            }
        }
    }

    fun isFavorite(pkg: String) = pkg in ui.value.prefs.favorites

    fun toggleFavorite(pkg: String) = viewModelScope.launch {
        val fav = ui.value.prefs.favorites
        prefs.setFavorites(if (pkg in fav) fav - pkg else fav + pkg)
    }

    fun isOnHome(pkg: String) = pkg in ui.value.prefs.homeApps

    fun toggleHome(pkg: String) = viewModelScope.launch {
        val liste = ui.value.prefs.homeApps
        prefs.setHomeApps(if (pkg in liste) liste - pkg else liste + pkg)
    }

    /** Déplace une app d'une case (-1 = gauche, +1 = droite) dans sa rangée. */
    fun move(pkg: String, delta: Int) = viewModelScope.launch {
        val u = ui.value
        val dansFavoris = u.favorites.any { it.packageName == pkg }
        // On ne déplace que parmi les cartes visibles, pour que le mouvement se voie
        val visibles = (if (dansFavoris) u.favorites else u.homeApps).map { it.packageName }.toMutableList()
        val i = visibles.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in visibles.indices) return@launch
        visibles[i] = visibles[j].also { visibles[j] = visibles[i] }
        if (dansFavoris) {
            prefs.setFavorites(visibles + u.prefs.favorites.filter { it !in visibles })
        } else {
            prefs.setHomeApps(visibles + u.prefs.homeApps.filter { it !in visibles })
        }
    }

    fun hide(pkg: String) = viewModelScope.launch {
        prefs.setHidden(ui.value.prefs.hidden + pkg)
    }

    fun unhide(pkg: String) = viewModelScope.launch {
        prefs.setHidden(ui.value.prefs.hidden - pkg)
    }

    fun setSourcesComponent(component: String?) = viewModelScope.launch {
        prefs.setSourcesComponent(component)
    }

    /** Recherche (une fois) ce qui peut ouvrir l'entrée HDMI. */
    fun loadSourceCandidates() {
        viewModelScope.launch {
            val liste = apps.value ?: return@launch
            _sources.value = withContext(Dispatchers.IO) { repo.sourceCandidates(liste) }
        }
    }

    fun openSources() {
        viewModelScope.launch {
            val liste = apps.value.orEmpty()
            val candidats = _sources.value.ifEmpty {
                withContext(Dispatchers.IO) { repo.sourceCandidates(liste) }.also { _sources.value = it }
            }
            actions.openSources(ui.value.prefs.sourcesComponent, candidats)
        }
    }
}
