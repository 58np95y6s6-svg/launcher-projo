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
import fr.projo.launcher.ui.AppMenuDialog
import fr.projo.launcher.ui.DiagnosticDialog
import fr.projo.launcher.ui.HiddenAppsDialog
import fr.projo.launcher.ui.HomeActions
import fr.projo.launcher.ui.HomeScreen
import fr.projo.launcher.ui.LocalScale
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
            CompositionLocalProvider(LocalScale provides echelle) {
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

        HomeScreen(
            ui = ui,
            icons = vm.icons,
            wifiLabel = wifi,
            homePressCount = homePresses,
            actions = HomeActions(
                launch = { app ->
                    if (!vm.repo.launch(app.packageName)) toast("Impossible d'ouvrir ${app.label}")
                },
                openMenu = { menuApp = it },
                openHidden = { montrerMasquees = true },
                sources = { vm.openSources() },
                sourcesLong = { vm.loadSourceCandidates(); montrerSources = true },
                wifi = { vm.actions.openWifi() },
                bluetooth = { vm.actions.openBluetooth() },
                settings = { vm.actions.openSettings() },
                settingsLong = { montrerDiagnostic = true },
            ),
        )

        menuApp?.let { app ->
            val index = ui.favorites.indexOfFirst { it.packageName == app.packageName }
            AppMenuDialog(
                app = app,
                isFavorite = vm.isFavorite(app.packageName),
                favoriteIndex = index,
                favoriteCount = ui.favorites.size,
                onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                onMove = { vm.moveFavorite(app.packageName, it) },
                onHide = { vm.hide(app.packageName); toast("${app.label} masquée") },
                onInfo = { vm.actions.openAppInfo(app.packageName) },
                onDismiss = { menuApp = null },
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
