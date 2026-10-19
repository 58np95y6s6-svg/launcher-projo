package fr.projo.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.system.NetworkInfo
import fr.projo.launcher.ui.DiagnosticDialog
import fr.projo.launcher.ui.Design
import fr.projo.launcher.ui.DesignDialog
import fr.projo.launcher.ui.LocalDesign
import fr.projo.launcher.ui.HiddenAppsDialog
import fr.projo.launcher.ui.IconChoiceDialog
import fr.projo.launcher.ui.HomeActions
import fr.projo.launcher.ui.HomeScreen
import fr.projo.launcher.ui.LibraryScreen
import fr.projo.launcher.ui.LocalScale
import fr.projo.launcher.ui.MenuAction
import fr.projo.launcher.ui.MenuDialog
import fr.projo.launcher.ui.SettingsActions
import fr.projo.launcher.ui.SettingsPanel
import fr.projo.launcher.ui.SourcesDialog
import kotlinx.coroutines.delay

/** Activité unique du launcher : c'est elle que le bouton Maison ouvre. */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    /** Appuis sur Maison pendant que le launcher est affiché (remet le focus au début). */
    private var homePresses by mutableIntStateOf(0)

    /** Recharge la liste quand une app est installée, mise à jour ou supprimée. */
    private val surChangementApps = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Le bouton Retour ne quitte jamais l'écran d'accueil.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* on reste sur l'accueil */ }
        })

        // Diffusions système : un récepteur « non exporté » les reçoit quand même
        ContextCompat.registerReceiver(this, surChangementApps, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            // Échelle : interface dessinée pour 960 dp de large
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            val etat by vm.ui.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides Design.from(etat.prefs.design)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Contenu()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Contenu() {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val sources by vm.sources.collectAsStateWithLifecycle()

        var menuApp by remember { mutableStateOf<AppInfo?>(null) }
        var montrerMasquees by remember { mutableStateOf(false) }
        var montrerSources by remember { mutableStateOf(false) }
        var montrerDiagnostic by remember { mutableStateOf(false) }
        var logoApp by remember { mutableStateOf<AppInfo?>(null) }
        var montrerBibliotheque by remember { mutableStateOf(false) }
        var montrerReglages by remember { mutableStateOf(false) }
        var montrerDesign by remember { mutableStateOf(false) }
        // Focus rendu à l'accueil quand un panneau se ferme (sinon plus rien n'est sélectionné)
        val focusReglages = remember { FocusRequester() }
        val focusBiblio = remember { FocusRequester() }
        var focusApres by remember { mutableStateOf<FocusRequester?>(null) }
        var retourFocus by remember { mutableIntStateOf(0) }
        var refocusApresMenu by remember { mutableStateOf(false) }
        LaunchedEffect(focusApres) {
            val cible = focusApres ?: return@LaunchedEffect
            // On attend que le panneau ait disparu avant de redonner le focus
            for (essai in 1..3) {
                withFrameNanos {}
                if (runCatching { cible.requestFocus() }.isSuccess) break
            }
            focusApres = null
        }
        val maj by vm.maj.collectAsStateWithLifecycle()
        val etatAppareil by vm.snapshot.collectAsStateWithLifecycle()

        // Mise à jour : vérifiée au retour sur l'accueil (au plus toutes les 6 h)
        val cycleMaj = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(cycleMaj) {
            cycleMaj.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.checkUpdate() }
        }
        // Panneau Réglages ouvert : état de l'appareil rafraîchi toutes les 3 s
        LaunchedEffect(montrerReglages) {
            while (montrerReglages) { vm.refreshDevice(); delay(3_000) }
        }
        // Bouton Maison : ferme la bibliothèque et revient à l'accueil
        LaunchedEffect(homePresses) { montrerBibliotheque = false; montrerReglages = false }

        // Ouvre une app, ou la source HDMI pour la tuile spéciale
        val lancer: (AppInfo) -> Unit = { app ->
            if (app.packageName == AppInfo.HDMI_ID) vm.openSources()
            else if (!vm.repo.launch(app.packageName)) toast("Impossible d'ouvrir ${app.label}")
        }

        // Nom du Wi-Fi : relu au retour sur l'accueil puis toutes les 30 s
        var wifi by remember { mutableStateOf("Wi-Fi") }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    wifi = NetworkInfo.wifiLabel(this@MainActivity)
                    delay(30_000)
                }
            }
        }

        // Matchs NBA : mis à jour seulement pendant que l'accueil est affiché
        val nbaDonnees by vm.nbaData.collectAsStateWithLifecycle()
        val nbaErreur by vm.nbaErreur.collectAsStateWithLifecycle()
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.nbaLoop() }
        }

        HomeScreen(
            ui = ui,
            icons = vm.icons,
            wifiLabel = wifi,
            homePressCount = homePresses,
            retourFocus = retourFocus,
            focusReglages = focusReglages,
            focusBiblio = focusBiblio,
            actions = HomeActions(
                launch = lancer,
                openMenu = { menuApp = it },
                openLibrary = { montrerBibliotheque = true },
                sources = { vm.openSources() },
                sourcesLong = { vm.loadSourceCandidates(); montrerSources = true },
                wifi = { vm.actions.openWifi() },
                bluetooth = { vm.actions.openBluetooth() },
                settings = { vm.refreshDevice(); montrerReglages = true },
                settingsLong = { vm.actions.openSettings() },
                update = { vm.installUpdate(); vm.refreshDevice(); montrerReglages = true },
            ),
            nbaData = nbaDonnees,
            nbaErreur = nbaErreur,
            nbaRepo = vm.nba,
            majDisponible = maj.info?.available == true,
            design = Design.from(ui.prefs.design),
        )

        if (montrerReglages) {
            SettingsPanel(
                snapshot = etatAppareil,
                maj = maj,
                version = vm.updates.versionAffichee,
                hiddenCount = ui.hiddenApps.size,
                isDefaultHome = vm.repo.currentHomePackage() == packageName,
                actions = SettingsActions(
                    volume = { vm.changeVolume(it) },
                    mute = { vm.toggleMute() },
                    wifi = { vm.actions.openWifi() },
                    bluetooth = { vm.actions.openBluetooth() },
                    storage = { vm.actions.openStorage() },
                    update = { vm.installUpdate() },
                    checkUpdate = { vm.checkUpdate(force = true) },
                    hiddenApps = { montrerMasquees = true },
                    sources = { vm.loadSourceCandidates(); montrerSources = true },
                    homeApp = { montrerDiagnostic = true },
                    language = { vm.actions.openLanguage() },
                    date = { vm.actions.openDate() },
                    sound = { vm.actions.openSound() },
                    allSettings = { vm.actions.openSettings() },
                    close = { montrerReglages = false; focusApres = focusReglages },
                    design = { montrerDesign = true },
                ),
            )
        }

        if (montrerBibliotheque) {
            LibraryScreen(
                ui = ui,
                icons = vm.icons,
                onLaunch = lancer,
                onMenu = { menuApp = it },
                onOpenHidden = { montrerMasquees = true },
                onClose = { montrerBibliotheque = false; focusApres = focusBiblio },
            )
        }

        menuApp?.let { app ->
            val pkg = app.packageName
            val favori = vm.isFavorite(pkg)
            val accueil = vm.isOnHome(pkg)
            // Position dans sa rangée, pour proposer « déplacer » seulement si possible
            val rangee = if (favori) ui.favorites else ui.homeApps
            val index = rangee.indexOfFirst { it.packageName == pkg }
            val actions = buildList {
                add(MenuAction(if (favori) "Retirer des favoris" else "Ajouter aux favoris") { if (favori) refocusApresMenu = true; vm.toggleFavorite(pkg) })
                if (!favori) {
                    add(MenuAction(if (accueil) "Retirer de l'accueil" else "Ajouter à l'accueil") { if (accueil) refocusApresMenu = true; vm.toggleHome(pkg) })
                }
                if (index > 0) add(MenuAction("Déplacer à gauche") { vm.move(pkg, -1) })
                if (index >= 0 && index < rangee.size - 1) add(MenuAction("Déplacer à droite") { vm.move(pkg, 1) })
                if (!app.isSpecial) {
                    add(MenuAction("Changer le logo…") { logoApp = app })
                    add(MenuAction("Masquer partout") { refocusApresMenu = true; vm.hide(pkg); toast("${app.label} masquée") })
                    add(MenuAction("Infos de l'app") { vm.actions.openAppInfo(pkg) })
                } else {
                    add(MenuAction("Choisir l'entrée à ouvrir…") { vm.loadSourceCandidates(); montrerSources = true })
                }
                add(MenuAction("Annuler") {})
            }
            MenuDialog(
                title = app.label,
                subtitle = if (app.isSpecial) null else pkg,
                actions = actions,
                onDismiss = {
                    menuApp = null
                    // La carte a disparu de l'accueil : on remet le focus sur le premier favori
                    if (refocusApresMenu && !montrerBibliotheque) retourFocus++
                    refocusApresMenu = false
                },
            )
        }
        logoApp?.let { app ->
            IconChoiceDialog(
                app = app,
                allApps = ui.allApps,
                onChoose = { vm.setIconOverride(app.packageName, it) },
                onDismiss = { logoApp = null },
            )
        }
        if (montrerDesign) {
            DesignDialog(
                current = Design.from(ui.prefs.design),
                onChoose = { vm.setDesign(it.id) },
                onDismiss = { montrerDesign = false },
            )
        }
        if (montrerMasquees) {
            HiddenAppsDialog(
                apps = ui.hiddenApps,
                onUnhide = { vm.unhide(it.packageName) },
                onDismiss = { montrerMasquees = false },
            )
        }
        if (montrerSources) {
            SourcesDialog(
                candidates = sources,
                allApps = ui.allApps,
                current = ui.prefs.sourcesComponent,
                onChoose = { vm.setSourcesComponent(it) },
                onDismiss = { montrerSources = false },
            )
        }
        if (montrerDiagnostic) {
            DiagnosticDialog(
                currentHome = vm.repo.currentHomePackage(),
                ourPackage = packageName,
                homeCandidates = vm.repo.homeCandidates(),
                version = BuildConfigVersion.nom(this),
                onChooseHome = { vm.actions.openHomeSettings() },
                onDismiss = { montrerDiagnostic = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Appui sur Maison alors qu'on est déjà sur l'accueil : retour au début
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onDestroy() {
        unregisterReceiver(surChangementApps)
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Numéro de version lu depuis le système (évite d'activer BuildConfig). */
object BuildConfigVersion {
    fun nom(context: Context): String = try {
        @Suppress("DEPRECATION")
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pi.versionName} (${pi.longVersionCodeCompat()})"
    } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
}
