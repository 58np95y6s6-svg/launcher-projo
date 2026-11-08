package fr.projo.launcher.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.ambiance.CouleursNeon
import fr.projo.launcher.ambiance.Intensite
import fr.projo.launcher.ambiance.ModeAmbiance
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.LauncherPrefs
import fr.projo.launcher.data.PrefsState
import fr.projo.launcher.data.DefilementGalerie
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.liens.EnvoiTv
import fr.projo.launcher.liens.OuvertureLiens
import fr.projo.launcher.system.DeviceStatus
import fr.projo.launcher.system.SystemActions
import fr.projo.launcher.ui.Design
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pilotage (applis et réglages) côté Android, pour la télécommande (voir Pilotage).
 *
 * Fils d'exécution :
 *  - les appels arrivent sur le fil de la télécommande, et chaque rappel y repart par [retour] ;
 *  - lectures lourdes (liste des applis, images) sur un fil à part, « pilotage » ;
 *  - lecture des réglages et toutes les écritures (états Compose, ViewModel, ouverture d'une
 *    appli ou d'une page système) sur le fil principal.
 *
 * Le ViewModel de l'accueil est prêté par MainActivity ([attacher] / [detacher]) : sans lui,
 * `maj` est grisé et la liste des applis est relue directement.
 *
 * Changements vus par la TV (volume, design, Mode Ambiance, nouveautés, progression d'une mise
 * à jour) : [surChangement], que la télécommande regroupe (au plus toutes les 300 ms). Rien ne
 * tourne en boucle : récepteur du volume, flux existants, écoute démarrée à la première demande.
 */
class PilotageLauncher(
    context: Context,
    /** Exécute sur le fil de la télécommande. */
    private val retour: (() -> Unit) -> Unit,
    /** Un réglage a changé (sur la TV ou depuis le téléphone). N'importe quel fil. */
    private val surChangement: () -> Unit,
) : Pilotage {

    private val app: Context = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    /** Fil des lectures lourdes (créé au premier usage). */
    private val travail: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pilotage").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { LauncherPrefs(app) }
    private val audio by lazy { app.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val appareil by lazy { DeviceStatus(app) }
    private val repo by lazy { AppRepository(app) }
    private val systeme by lazy { SystemActions(app, repo) }

    /** Préférences de l'accueil (design, favoris, masquées), suivies dès la première demande. */
    @Volatile
    private var prefsCourantes: PrefsState? = null

    /** ViewModel de l'écran d'accueil (MainActivity créée), sinon null. */
    @Volatile
    private var accueil: WeakReference<LauncherViewModel>? = null
    private var suiviAccueil: Job? = null

    /** Écoute des changements démarrée (fil principal). */
    private var ecoute = false

    /** Images déjà fabriquées, clé « paquet@maj » (fil « pilotage »). */
    private val cacheImages = object : LruCache<String, ImageAppli>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageAppli): Int = value.img.length * 2
    }

    // ------------------------------------------------------------------ écran d'accueil

    /** MainActivity créée (fil principal). */
    fun attacher(vm: LauncherViewModel) {
        accueil = WeakReference(vm)
        suiviAccueil?.cancel()
        // Progression d'une mise à jour, volume relu par le panneau Réglages de la TV
        suiviAccueil = scope.launch {
            launch { vm.maj.collect { surChangement() } }
            launch { vm.snapshot.map { it?.volume to it?.muet }.distinctUntilChanged().collect { surChangement() } }
        }
    }

    /** MainActivity détruite (fil principal) ; ignoré si un autre accueil a pris sa place. */
    fun detacher(vm: LauncherViewModel) {
        if (accueil?.get() !== vm) return
        accueil = null
        suiviAccueil?.cancel()
        suiviAccueil = null
        surChangement()
    }

    private fun modele(): LauncherViewModel? = accueil?.get()

    // ------------------------------------------------------------------ applis

    override fun applis(rappel: (List<AppliTv>) -> Unit) {
        principal.post { ecouter() }
        travail.execute {
            val liste: List<AppliTv> = try {
                lireApplis()
            } catch (e: Exception) {
                Log.w(TAG, "Liste des applis illisible")
                emptyList()
            }
            retour { rappel(liste) }
        }
    }

    /** Fil « pilotage ». Liste de l'accueil si elle est chargée, sinon relue directement. */
    private fun lireApplis(): List<AppliTv> {
        val ui = modele()?.ui?.value
        val favoris: List<AppInfo>
        val toutes: List<AppInfo>
        if (ui != null && ui.loaded) {
            favoris = ui.favorites
            toutes = ui.allApps
        } else {
            val p = prefsCourantes ?: runBlocking { prefs.state.first() }
            val reelles = repo.loadApps(journal = false)
            val parPaquet = reelles.associateBy { it.packageName }
            favoris = p.favorites.mapNotNull { parPaquet[it] }.filter { it.packageName !in p.hidden }
            toutes = reelles.filter { it.packageName !in p.hidden }
        }
        val pm = app.packageManager
        return ListeApplis.construire(favoris, toutes, app.packageName) { derniereMaj(pm, it) }
    }

    @Suppress("DEPRECATION")
    private fun derniereMaj(pm: PackageManager, paquet: String): Long = try {
        pm.getPackageInfo(paquet, 0).lastUpdateTime
    } catch (e: Exception) {
        0L
    }

    override fun images(applis: List<AppliTv>, encoreVoulu: () -> Boolean, rappel: (ImageAppli) -> Unit) {
        // Une image à la fois : une autre demande (ouvrir une appli) passe entre deux images
        fun suivante(i: Int) {
            if (i >= applis.size) return
            val a = applis[i]
            travail.execute {
                val faite = image(a)
                retour {
                    // Téléphone parti : on s'arrête là
                    if (encoreVoulu()) {
                        if (faite != null) rappel(faite)
                        suivante(i + 1)
                    }
                }
            }
        }
        suivante(0)
    }

    /** Fil « pilotage ». Null : appli introuvable ou image impossible sous 48 Ko. */
    private fun image(a: AppliTv): ImageAppli? {
        val cle = a.id + "@" + a.maj
        cacheImages.get(cle)?.let { return it }
        val faite = try {
            ImagesApplis.fabriquer(app, a.id)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        val (forme, url) = faite ?: return null
        return ImageAppli(a.id, a.maj, forme, url).also { cacheImages.put(cle, it) }
    }

    override fun ouvrir(id: String, rappel: (Boolean) -> Unit) {
        principal.post {
            // Comme la tuile de l'accueil : version TV d'abord, pause de la musique si besoin
            val ok = try {
                (modele()?.repo ?: repo).launch(id)
            } catch (e: Exception) {
                false
            }
            retour { rappel(ok) }
        }
    }

    // ------------------------------------------------------------------ réglages

    override fun reglages(rappel: (List<ReglageTv>) -> Unit) {
        principal.post {
            ecouter()
            val items: List<ReglageTv>? = try {
                ReglagesTv.decrire(lireEtat())
            } catch (e: Exception) {
                Log.w(TAG, "Réglages illisibles")
                null
            }
            if (items != null) retour { rappel(items) }
        }
    }

    /** Fil principal. */
    private fun lireEtat(): EtatTv {
        ReglageNouveautes.charger(app)
        DefilementGalerie.charger(app)
        ModeAmbiance.charger(app)
        EnvoiTv.charger(app)
        val vm = modele()
        val p = prefsCourantes ?: vm?.ui?.value?.prefs
        val m = vm?.maj?.value
        val maj = if (vm == null || m == null) null else EtatMaj(
            versionInstallee = vm.updates.versionAffichee,
            verification = m.verification,
            telechargement = m.telechargement,
            message = m.message,
            nouvelleVersion = m.info?.takeIf { it.available }?.remoteName,
            verifiee = m.info != null,
        )
        return EtatTv(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            volumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            muet = (vm?.device ?: appareil).estMuet(),
            design = Design.from(p?.design),
            nouveautes = ReglageNouveautes.actif,
            defilementGalerie = DefilementGalerie.secondes,
            ambianceAuto = ModeAmbiance.auto,
            intensite = ModeAmbiance.intensite,
            couleurs = ModeAmbiance.couleurs,
            decalageMs = ModeAmbiance.decalageMs,
            decalages = ModeAmbiance.decalagesPossibles,
            decalageConseille = ModeAmbiance.DECALAGE_CONSEILLE,
            maj = maj,
            envoi = EtatEnvoi(
                seulementVpn = EnvoiTv.seulementVpn,
                pages = EnvoiTv.pages,
                navigateurs = OuvertureLiens.choixPages(app).map { (paquet, nom) -> OptionReglage(paquet, nom) },
                raccourci = EnvoiTv.raccourci,
            ),
        )
    }

    override fun appliquer(changement: Changement, rappel: () -> Unit) {
        principal.post {
            ecouter()
            try {
                appliquerIci(changement)
            } catch (e: Exception) {
                Log.w(TAG, "Réglage impossible : ${changement.cle}")
            }
            surChangement()
            retour(rappel)
        }
    }

    /** Fil principal. La valeur a déjà été validée contre la description (type, bornes, options). */
    private fun appliquerIci(c: Changement) {
        val v = c.valeur
        val vm = modele()
        when (c.cle) {
            ReglagesTv.VOLUME -> if (v is Int) {
                (vm?.device ?: appareil).setVolume(v)
                vm?.refreshDevice()
            }
            ReglagesTv.MUET -> if (v is Boolean) {
                val d = vm?.device ?: appareil
                if (d.estMuet() != v) {
                    d.toggleMute()
                    vm?.refreshDevice()
                }
            }
            ReglagesTv.DESIGN -> if (v is String) {
                // Même préférence que Réglages → Design (l'accueil suit le même DataStore)
                prefsCourantes = prefsCourantes?.copy(design = v)
                scope.launch { prefs.setDesign(v) }
            }
            ReglagesTv.NOUVEAUTES -> if (v is Boolean) ReglageNouveautes.ecrire(app, v)
            ReglagesTv.GALERIE_DEFILEMENT -> (v as? String)?.toIntOrNull()?.let { DefilementGalerie.ecrire(app, it) }
            ReglagesTv.AMBIANCE_AUTO -> if (v is Boolean) ModeAmbiance.ecrireAuto(app, v)
            ReglagesTv.AMBIANCE_INTENSITE -> if (v is String) ModeAmbiance.ecrireIntensite(app, Intensite.de(v))
            ReglagesTv.AMBIANCE_COULEURS -> if (v is String) ModeAmbiance.ecrireCouleurs(app, CouleursNeon.de(v))
            ReglagesTv.AMBIANCE_DECALAGE -> (v as? String)?.toIntOrNull()?.let { ModeAmbiance.ecrireDecalage(app, it) }
            ReglagesTv.MAJ -> when (v) {
                // Android demande ensuite de confirmer l'installation sur l'écran de la TV
                ReglagesTv.BOUTON_VERIFIER -> vm?.checkUpdate(force = true)
                ReglagesTv.BOUTON_INSTALLER -> vm?.installUpdate()
            }
            ReglagesTv.LIENS_SEULEMENT_VPN -> if (v is Boolean) EnvoiTv.ecrireSeulementVpn(app, v)
            ReglagesTv.LIENS_PAGES -> if (v is String) EnvoiTv.ecrirePages(app, v)
            ReglagesTv.LIENS_RACCOURCI -> if (v is Boolean) EnvoiTv.ecrireRaccourci(app, v)
            ReglagesTv.LIENS_CODE -> if (v == ReglagesTv.BOUTON_NOUVEAU_CODE) EnvoiTv.nouveauCode(app)
            ReglagesTv.SYSTEME -> when (v) {
                ReglagesTv.BOUTON_WIFI -> systeme.openWifi()
                ReglagesTv.BOUTON_BLUETOOTH -> systeme.openBluetooth()
                ReglagesTv.BOUTON_REGLAGES -> systeme.openSettings()
            }
        }
    }

    // ------------------------------------------------------------------ changements vus par la TV

    /** Volume réglé à la télécommande de la TV, son coupé (diffusions système). */
    private val surVolume = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = surChangement()
    }

    /** Fil principal ; une seule fois, à la première demande d'un téléphone. */
    private fun ecouter() {
        if (ecoute) return
        ecoute = true
        try {
            ContextCompat.registerReceiver(app, surVolume, IntentFilter().apply {
                addAction(ACTION_VOLUME)
                addAction(ACTION_MUET)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Log.w(TAG, "Suivi du volume indisponible")
        }
        // Design changé sur la TV (ou ailleurs) ; favoris et masquées pour la liste de secours
        scope.launch {
            prefs.state.collect { p ->
                val avant = prefsCourantes
                prefsCourantes = p
                if (avant == null || avant.design != p.design) surChangement()
            }
        }
        // Réglages gardés en mémoire (états Compose) : changés depuis les dialogues de la TV
        ReglageNouveautes.charger(app)
        DefilementGalerie.charger(app)
        ModeAmbiance.charger(app)
        scope.launch {
            snapshotFlow {
                listOf<Any>(
                    ReglageNouveautes.actif,
                    DefilementGalerie.secondes,
                    ModeAmbiance.auto,
                    ModeAmbiance.intensite,
                    ModeAmbiance.couleurs,
                    ModeAmbiance.decalageMs,
                )
            }.collect { surChangement() }
        }
    }

    private companion object {
        const val TAG = "Projo.Pilotage"

        /** Diffusions système (constantes cachées d'AudioManager). */
        const val ACTION_VOLUME = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_MUET = "android.media.STREAM_MUTE_CHANGED_ACTION"
    }
}
