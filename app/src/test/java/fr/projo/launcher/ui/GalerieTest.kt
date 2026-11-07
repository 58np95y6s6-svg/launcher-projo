package fr.projo.launcher.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.projo.launcher.HomeUi
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.DefilementGalerie
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Outils communs aux tests de Galerie : titre au centre, textes, Réglages par-dessus, écran affiché. */
abstract class BancGalerie : BancAccueil("GALERIE") {

    init {
        // Titres TMDB factices (images en dégradé), sans réseau
        sourceStudio = TmdbFactice.source()
    }

    @Before
    fun viderMaListe() {
        RuntimeEnvironment.getApplication().getSharedPreferences(MaListe.FICHIER, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Clé du titre au centre (« prog:tv:101 », « une:com.netflix.ninja »), ou null. */
    protected fun heros(): String? {
        val m = SemanticsMatcher("titre au centre") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("galerie:heros:") == true }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.TestTag)?.removePrefix("galerie:heros:")
    }

    protected fun attendreHeros(cle: String, contexte: String) {
        val h = heros()
        if (h != cle) fail("[GALERIE] $contexte : titre au centre attendu $cle, obtenu $h")
    }

    protected fun attendre(etiquette: String, contexte: String) {
        val f = focus()
        if (f?.etiquette != etiquette) fail("[GALERIE] $contexte : focus attendu sur $etiquette, obtenu ${decrire(f)}")
    }

    protected fun textePresent(t: String) =
        compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun present(etiquette: String) =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Appui puis fin des animations du carrousel (0,8 s, fondus compris). */
    protected fun appuyerEtAttendre(k: Key) {
        appuyer(k)
        avancer(400)
    }

    /** Haut du dock en unités .u : ≈ 457 sur l'écran 1, ≈ 55 sur l'écran 2 (modules). */
    protected fun ecran(): Int = if ((bornesDe("rangee:favoris")?.top ?: 0f) / pxParU < 200f) 2 else 1

    /** Une appli sait ouvrir les recherches YouTube (bande-annonce). */
    protected fun installerLecteurYoutube() {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.teamsmart.videomanager.tv"
                name = "com.teamsmart.videomanager.tv.Lecteur"
                applicationInfo = ApplicationInfo().apply { packageName = "com.teamsmart.videomanager.tv" }
            }
        }
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(BandeAnnonceGalerie.sonde(), info)
    }

    protected fun ouvrirReglages() {
        val panneau: @androidx.compose.runtime.Composable () -> Unit = {
            SettingsPanel(
                snapshot = DeviceSnapshot(
                    volume = 10, volumeMax = 15, muet = false, reseau = "Maison-5G", enLigne = true,
                    signal = 4, bluetoothActif = true, appareilsBluetooth = listOf("Casque"),
                    stockageLibre = 3_000_000_000L, stockageTotal = 8_000_000_000L,
                ),
                maj = LauncherViewModel.MajEtat(info = UpdateInfo("v2.4", "", true)),
                version = "2.3",
                hiddenCount = 3,
                isDefaultHome = true,
                actions = SettingsActions(
                    volume = {}, mute = {}, wifi = {}, bluetooth = {}, storage = {}, update = {},
                    checkUpdate = {}, hiddenApps = {}, sources = {}, homeApp = {}, language = {},
                    date = {}, sound = {}, allSettings = {}, close = {},
                ),
            )
        }
        compose.runOnIdle {
            pardessus.value = panneau
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        avancer(1500)
    }
}

/**
 * Galerie : titres TMDB (factices) dans le carrousel, glissements (◀ ▶ sur la carte, flèches,
 * tuiles, bouclage), onglets, « Ma liste », grille « agrandir », Regarder et Bande-annonce,
 * navigation entre les étages, écran 2 (modules), Maison, repli sans clé, pilule météo.
 *
 * Titres factices (TmdbFactice, services Netflix, Prime Video, Canal+) : tendances d'abord
 * (tv:101, movie:202), puis les services en alternance — 8 titres.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class GalerieTest : BancGalerie() {

    private val ordre = listOf("tv:101", "movie:202", "tv:302", "tv:402", "movie:301", "movie:401", "tv:104", "movie:203")

    @Test
    fun accueilEtTitreAuCentre() {
        afficher()
        avancer(500)
        attendreHeros("prog:tv:101", "démarrage")
        for (t in listOf("Pour vous", "Films", "Séries", "Ma liste", "Regarder sur Netflix", "Ajouter à ma liste", "Titres et images : TMDB", "N°1 cette semaine")) {
            if (!textePresent(t)) fail("[GALERIE] texte « $t » absent de l'accueil")
        }
        if (ecran() != 1) fail("[GALERIE] l'accueil doit montrer l'écran 1")
        // Places de la maquette : carte du centre et tuiles occupées (8 titres : -3 … +2)
        val carte = bornesDe("galerie:carte") ?: return fail("[GALERIE] carte du centre absente")
        if (kotlin.math.abs(carte.left / pxParU - 310f) > 1.5f || kotlin.math.abs(carte.width / pxParU - 470f) > 1.5f) {
            fail("[GALERIE] carte du centre mal placée : ${carte.enU()} (attendu x 310…780 u)")
        }
        for (o in listOf(-3, -2, -1, 1, 2)) if (!present("galerie:place:$o")) fail("[GALERIE] tuile de la place $o absente")
        if (present("galerie:place:-4")) fail("[GALERIE] 8 titres : la place -4 doit rester vide")
        // Le dock tient dans l'écran, sous le panneau
        val dock = bornesDe("rangee:favoris")!!
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        if (dock.bottom > hauteur + 0.5f) fail("[GALERIE] dock coupé en bas : ${dock.enU()}")
    }

    /** ◀ ▶ sur la carte : le carrousel glisse, le focus reste sur la carte ; bouclage dans les deux sens. */
    @Test
    fun glissementsSurLaCarte() {
        afficher()
        donnerFocus("galerie:carte")
        appuyerEtAttendre(Key.DirectionRight)
        attendre("galerie:carte", "▶ sur la carte")
        attendreHeros("prog:movie:202", "▶ sur la carte")
        if (!textePresent("N°2 cette semaine")) fail("[GALERIE] ▶ : rang « N°2 cette semaine » absent")
        appuyerEtAttendre(Key.DirectionLeft)
        appuyerEtAttendre(Key.DirectionLeft)
        // Bouclage : avant le premier, le dernier
        attendreHeros("prog:" + ordre.last(), "◀ ◀ (bouclage)")
        repeat(ordre.size) { appuyer(Key.DirectionRight) }
        avancer(1200)
        attendreHeros("prog:" + ordre.last(), "tour complet")
        appuyerEtAttendre(Key.DirectionRight)
        attendreHeros("prog:tv:101", "retour au premier")
    }

    /** Appuis rapprochés (pendant le glissement) : chaque appui compte. */
    @Test
    fun appuisRapproches() {
        afficher()
        donnerFocus("galerie:carte")
        repeat(3) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            avancer(150)
        }
        avancer(1200)
        attendreHeros("prog:" + ordre[3], "▶ ▶ ▶ rapprochés")
    }

    /** Flèches (OK) et tuiles (OK : la tuile vient au centre, le focus passe sur la carte). */
    @Test
    fun flechesEtTuiles() {
        afficher()
        donnerFocus("galerie:suivant")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:movie:202", "OK sur ▶")
        attendre("galerie:suivant", "OK sur ▶")
        donnerFocus("galerie:precedent")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:tv:101", "OK sur ◀")
        donnerFocus("galerie:place:2")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:tv:302", "OK sur la tuile +2")
        attendre("galerie:carte", "après le choix d'une tuile")
        donnerFocus("galerie:place:-3")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:" + ordre[7], "OK sur la tuile -3")
    }

    /** Étages : ▲ ▼ vers l'élément choisi ou le plus proche, retour au départ ; ◀ ▶ dans l'ordre. */
    @Test
    fun navigationEntreLesEtages() {
        afficher()
        // Dock → actions → galerie → onglets, et retour
        appuyer(Key.DirectionUp)
        val action = focus()?.etiquette.orEmpty()
        if (action !in listOf("galerie:ajout", "galerie:agrandir", "galerie:regarder", "galerie:bande-annonce")) {
            fail("[GALERIE] 1er favori puis ▲ : attendu une action, obtenu ${decrire(focus())}")
        }
        appuyer(Key.DirectionDown)
        attendre(favori(0), "▲ ▼ depuis le 1er favori")
        donnerFocus("galerie:carte")
        appuyer(Key.DirectionUp)
        attendre("onglet:pourvous", "carte puis ▲ (onglet actif)")
        appuyer(Key.DirectionDown)
        attendre("galerie:carte", "onglet puis ▼")
        appuyer(Key.DirectionDown)
        attendre("galerie:regarder", "carte puis ▼")
        appuyer(Key.DirectionUp)
        attendre("galerie:carte", "Regarder puis ▲")
        // ◀ ▶ dans l'étage des actions
        donnerFocus("galerie:regarder")
        appuyer(Key.DirectionLeft)
        attendre("galerie:agrandir", "Regarder puis ◀")
        appuyer(Key.DirectionLeft)
        attendre("galerie:ajout", "agrandir puis ◀")
        appuyer(Key.DirectionLeft)
        attendre("galerie:ajout", "◀ au bout des actions")
        // ◀ ▶ dans la galerie : ordre du carrousel (tuiles, flèche, carte, flèche, tuiles)
        donnerFocus("galerie:precedent")
        appuyer(Key.DirectionLeft)
        attendre("galerie:place:-1", "◀ depuis la flèche")
        appuyer(Key.DirectionLeft)
        attendre("galerie:place:-2", "◀ depuis la tuile -1")
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionRight)
        attendre("galerie:precedent", "▶ ▶ vers la flèche")
        appuyer(Key.DirectionRight)
        attendre("galerie:carte", "▶ depuis la flèche ◀")
        attendreHeros("prog:tv:101", "aucun glissement en arrivant sur la carte")
        donnerFocus("galerie:suivant")
        appuyer(Key.DirectionRight)
        attendre("galerie:place:1", "▶ depuis la flèche ▶")
        // En-tête : onglets puis Réglages ; ▲ en haut : on reste
        donnerFocus("onglet:pourvous")
        repeat(3) { appuyer(Key.DirectionRight) }
        attendre("onglet:maliste", "▶ ▶ ▶ sur les onglets")
        appuyer(Key.DirectionRight)
        attendre("rapide:Réglages", "▶ vers Réglages")
        appuyer(Key.DirectionRight)
        attendre("rapide:Réglages", "▶ au bout de l'en-tête")
        appuyer(Key.DirectionUp)
        attendre("rapide:Réglages", "▲ en haut")
        if (lances.isNotEmpty()) fail("[GALERIE] la navigation a lancé $lances")
    }

    /** Onglets : OK choisit (Films, Séries filtrent ; Ma liste vide : message). */
    @Test
    fun onglets() {
        afficher()
        donnerFocus("onglet:films")
        // Le focus seul ne change pas d'onglet
        attendreHeros("prog:tv:101", "focus sur Films")
        appuyerEtAttendre(Key.DirectionCenter)
        if (heros()?.startsWith("prog:movie:") != true) fail("[GALERIE] onglet Films : titre au centre ${heros()}")
        donnerFocus("onglet:series")
        appuyerEtAttendre(Key.DirectionCenter)
        if (heros()?.startsWith("prog:tv:") != true) fail("[GALERIE] onglet Séries : titre au centre ${heros()}")
        appuyer(Key.DirectionDown)
        attendre("galerie:carte", "onglet puis ▼")
        donnerFocus("onglet:maliste")
        appuyerEtAttendre(Key.DirectionCenter)
        if (!present("galerie:liste-vide") || !textePresent("Ajoutez des titres avec « Ajouter à ma liste »")) {
            fail("[GALERIE] Ma liste vide : message absent")
        }
        if (present("galerie:carte") || present("galerie:regarder")) fail("[GALERIE] Ma liste vide : carte ou boutons affichés")
        // ▼ depuis l'onglet : rien d'autre à atteindre que le dock
        appuyer(Key.DirectionDown)
        val f = focus()?.etiquette.orEmpty()
        if (!f.startsWith("favori:") && !f.startsWith("app:")) fail("[GALERIE] Ma liste vide puis ▼ : ${decrire(focus())}")
        // Pour vous : retour sur le titre qui était au centre
        donnerFocus("onglet:pourvous")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:tv:101", "retour sur Pour vous")
    }

    /** « Ajouter à ma liste » : gardé sur l'appareil, onglet Ma liste, « Retirer de ma liste ». */
    @Test
    fun maListe() {
        afficher()
        donnerFocus("galerie:ajout")
        appuyer(Key.DirectionCenter)
        if (!textePresent("Retirer de ma liste")) fail("[GALERIE] après l'ajout : « Retirer de ma liste » attendu")
        if (MaListe.de(compose.activity).titres.map { it.id } != listOf("tv:101")) fail("[GALERIE] Ma liste non enregistrée")
        // 2e titre
        donnerFocus("galerie:carte")
        appuyerEtAttendre(Key.DirectionRight)
        donnerFocus("galerie:ajout")
        appuyer(Key.DirectionCenter)
        // Onglet Ma liste : le plus récent d'abord
        donnerFocus("onglet:maliste")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:movie:202", "onglet Ma liste")
        if (!textePresent("Dans ma liste")) fail("[GALERIE] Ma liste : « Dans ma liste » absent")
        // Retirer les deux : la liste se vide, le focus revient sur l'onglet
        donnerFocus("galerie:ajout")
        appuyerEtAttendre(Key.DirectionCenter)
        attendreHeros("prog:tv:101", "après le retrait du 1er")
        attendre("galerie:ajout", "après le retrait du 1er")
        appuyerEtAttendre(Key.DirectionCenter)
        if (!present("galerie:liste-vide")) fail("[GALERIE] Ma liste vidée : message attendu")
        attendre("onglet:maliste", "Ma liste vidée")
        if (MaListe.de(compose.activity).titres.isNotEmpty()) fail("[GALERIE] Ma liste : retraits non enregistrés")
    }

    /** « Agrandir » : grille de tous les titres ; OK : le titre passe au centre ; Retour : fermeture. */
    @Test
    fun grilleAgrandir() {
        afficher()
        donnerFocus("galerie:agrandir")
        appuyerEtAttendre(Key.DirectionCenter)
        if (!present("galerie:grille")) fail("[GALERIE] agrandir : grille absente")
        attendre("grille:prog:tv:101", "ouverture de la grille")
        for (t in ordre) if (!present("grille:prog:$t")) fail("[GALERIE] grille : $t absent")
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionRight)
        attendre("grille:prog:tv:302", "▶ ▶ dans la grille")
        appuyerEtAttendre(Key.DirectionCenter)
        if (present("galerie:grille")) fail("[GALERIE] OK : la grille doit se fermer")
        attendreHeros("prog:tv:302", "titre choisi dans la grille")
        attendre("galerie:carte", "après le choix dans la grille")
        // Retour : fermeture, focus sur « agrandir »
        donnerFocus("galerie:agrandir")
        appuyerEtAttendre(Key.DirectionCenter)
        appuyerEtAttendre(Key.Back)
        if (present("galerie:grille")) fail("[GALERIE] Retour : la grille doit se fermer")
        attendre("galerie:agrandir", "Retour dans la grille")
        attendreHeros("prog:tv:302", "Retour ne change pas le titre")
    }

    /** « Regarder sur Netflix » ouvre le service ; « Bande-annonce » ouvre la recherche YouTube du titre. */
    @Test
    fun regarderEtBandeAnnonce() {
        installerLecteurYoutube()
        afficher()
        donnerFocus("galerie:regarder")
        appuyer(Key.DirectionCenter)
        if (lances != listOf("com.netflix.ninja")) fail("[GALERIE] Regarder : lancés $lances")
        donnerFocus("galerie:bande-annonce")
        appuyer(Key.DirectionCenter)
        val i = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity ?: shadowOf(compose.activity).nextStartedActivity
        val adresse = i?.dataString.orEmpty()
        if (!adresse.startsWith("https://www.youtube.com/results?search_query=") || !adresse.contains("Rivages")) {
            fail("[GALERIE] Bande-annonce : intention $i")
        }
        // OK sur la carte : comme « Regarder »
        donnerFocus("galerie:carte")
        appuyer(Key.DirectionCenter)
        if (lances != listOf("com.netflix.ninja", "com.netflix.ninja")) fail("[GALERIE] OK sur la carte : lancés $lances")
    }

    /** Sans appli capable d'ouvrir YouTube : pas de bouton « Bande-annonce ». */
    @Test
    fun sansLecteurPasDeBandeAnnonce() {
        afficher()
        if (present("galerie:bande-annonce")) fail("[GALERIE] « Bande-annonce » affiché sans appli pour l'ouvrir")
    }

    /** ▼ depuis le dock : écran 2 (modules) ; Maison : retour à l'écran 1 sur le 1er favori. */
    @Test
    fun ecran2EtMaison() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        appuyer(Key.DirectionDown)
        if (focus()?.chaine?.contains("nba") != true) fail("[GALERIE] dock puis ▼ : attendu les modules, obtenu ${decrire(focus())}")
        avancer(500)
        if (ecran() != 2) fail("[GALERIE] modules : écran 2 attendu (dock à ${bornesDe("rangee:favoris")?.enU()})")
        val zone = bornesDe("nba")!!
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        if (zone.top < 0f || zone.bottom > hauteur + 0.5f) fail("[GALERIE] modules hors de l'écran : ${zone.enU()}")
        capturer("g8_ecran2_modules")
        appuyer(Key.DirectionUp)
        attendre(favori(0), "modules puis ▲")
        compose.runOnIdle {
            appuisMaison.intValue++
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        avancer(1200)
        attendre(favori(0), "Maison")
        if (ecran() != 1) fail("[GALERIE] Maison : écran 1 attendu")
    }

    /** Pilule météo : seulement avec une donnée du module Météo. */
    @Test
    fun piluleMeteo() {
        hoteModules = ModulesFactices.hote("meteo")
        afficher()
        if (!present("galerie:meteo") || !textePresent("Lyon")) fail("[GALERIE] météo du module : pilule absente")
    }

    @Test
    fun pasDeMeteoSansModule() {
        hoteModules = ModulesFactices.hote("sport")
        afficher()
        if (present("galerie:meteo")) fail("[GALERIE] pilule météo sans module Météo")
    }

    /**
     * Pastille VPN (Envoyer sur la TV) : verte « VPN » et l'appli VPN quand un VPN est actif, à
     * gauche de Réglages sur la même ligne ; dans l'étage du haut (▶ ◀ entre Ma liste, elle et
     * Réglages) ; OK ouvre l'appli VPN.
     */
    @Test
    fun piluleVpn() {
        var ouvertures = 0
        vpnTv = EtatVpnTv(actif = true, garde = true, appli = "VYPN") { ouvertures++ }
        afficher()
        if (!present("galerie:vpn") || !textePresent("VYPN")) fail("[GALERIE] VPN actif : pastille verte « VPN · VYPN » absente")
        val pilule = bornesDe("galerie:vpn")!!
        val reglages = bornesDe("rapide:Réglages")!!
        if (pilule.right > reglages.left || kotlin.math.abs(pilule.center.y - reglages.center.y) > 3f * pxParU) {
            fail("[GALERIE] pastille VPN ${pilule.enU()} : attendue à gauche de Réglages ${reglages.enU()}, sur la même ligne")
        }
        donnerFocus("onglet:maliste")
        appuyer(Key.DirectionRight)
        attendre("galerie:vpn", "▶ depuis Ma liste")
        appuyer(Key.DirectionRight)
        attendre("rapide:Réglages", "▶ depuis la pastille VPN")
        appuyer(Key.DirectionLeft)
        attendre("galerie:vpn", "◀ depuis Réglages")
        appuyer(Key.DirectionCenter)
        if (ouvertures != 1) fail("[GALERIE] OK sur la pastille VPN : l'appli VPN devait s'ouvrir (ouvertures : $ouvertures)")
        if (lances.isNotEmpty() || rapides.isNotEmpty()) fail("[GALERIE] OK sur la pastille VPN : rien d'autre ($lances, $rapides)")
    }

    /** VPN coupé avec « Seulement avec le VPN » : pastille rouge « VPN coupé ». */
    @Test
    fun piluleVpnCoupe() {
        vpnTv = EtatVpnTv(actif = false, garde = true, appli = "VYPN") {}
        afficher()
        if (!present("galerie:vpn") || !textePresent("coupé")) fail("[GALERIE] VPN coupé : pastille « VPN coupé » absente")
        if (textePresent("VYPN")) fail("[GALERIE] VPN coupé : l'appli VPN ne s'affiche pas")
    }

    /** VPN coupé sans le garde-fou, ou pas d'état (aperçus) : pas de pastille. */
    @Test
    fun pasDePiluleVpn() {
        vpnTv = EtatVpnTv(actif = false, garde = false, appli = "VYPN") {}
        afficher()
        if (present("galerie:vpn")) fail("[GALERIE] VPN coupé sans « Seulement avec le VPN » : pas de pastille")
    }

    /** Réglages : la pastille reçoit le focus de retour du panneau ; OK ouvre le panneau. */
    @Test
    fun reglages() {
        afficher()
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        attendre("rapide:Réglages", "focusReglages")
        appuyer(Key.DirectionCenter)
        if (rapides != listOf("reglages")) fail("[GALERIE] OK sur Réglages : actions $rapides")
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(800)
        attendre("biblio", "focusBiblio")
        appuyer(Key.DirectionCenter)
        if (rapides != listOf("reglages", "bibliotheque")) fail("[GALERIE] OK sur Toutes les applis : actions $rapides")
    }

    /** Dock : ▶ d'appli en appli jusqu'à « Toutes les applis » ; Menu et appui long ouvrent le menu. */
    @Test
    fun dock() {
        afficher()
        val n = ui.favorites.size + ui.homeApps.size
        repeat(n + 2) { appuyer(Key.DirectionRight) }
        attendre("biblio", "▶ jusqu'au bout du dock")
        donnerFocus(app(1))
        appuyer(Key.Menu)
        if (menus != listOf(ui.homeApps[1].packageName)) fail("[GALERIE] Menu dans le dock : $menus")
        appuyer(Key.DirectionCenter)
        if (lances != listOf(ui.homeApps[1].packageName)) fail("[GALERIE] OK dans le dock : $lances")
    }

    /**
     * Loupe du dock (tailles lues à la mise en page) : sélectionnée 128 px de maquette (64 u) et
     * posée 12 px plus haut, voisines 104 px (52 u), les autres 92 px (46 u) ; elle suit le focus.
     */
    @Test
    fun loupeDuDock() {
        afficher()
        avancer(400)
        val f = ui.favorites.indices.map { favori(it) }
        if (f.size < 4) fail("[GALERIE] loupe : 4 favoris attendus dans les données factices, ${f.size} trouvés")
        fun cote(e: String) = (bornesDe(e)?.width ?: 0f) / pxParU
        fun bas(e: String) = (bornesDe(e)?.bottom ?: 0f) / pxParU
        fun verifier(attendus: List<Float>, contexte: String) {
            for ((i, a) in attendus.withIndex()) {
                val c = cote(f[i])
                if (kotlin.math.abs(c - a) > 1f) fail("[GALERIE] loupe ($contexte) : favori $i de $c u, attendu $a u")
            }
        }
        verifier(listOf(64f, 52f, 46f, 46f), "1er favori")
        val ecart = bas(f[3]) - bas(f[0])
        if (kotlin.math.abs(ecart - 6f) > 1f) fail("[GALERIE] loupe : icône sélectionnée pas posée 6 u plus haut ($ecart u)")
        appuyer(Key.DirectionRight)
        verifier(listOf(52f, 64f, 52f, 46f), "après ▶")
    }

    /**
     * Défilement automatique (5 s) : titre suivant quand on ne touche à rien, le focus restant
     * dans le dock ; chaque appui relance l'attente ; en pause sur la carte ; désactivé : plus rien.
     */
    @Test
    fun defilementAutomatique() {
        val appli = RuntimeEnvironment.getApplication()
        DefilementGalerie.ecrire(appli, 5)
        afficher()
        avancer(500)
        // Appui dans le dock : point de départ connu (appuyer avance déjà de 0,8 s)
        appuyer(Key.DirectionRight)
        val dansLeDock = focus()?.etiquette
        avancer(3_500)
        attendreHeros("prog:tv:101", "4,3 s sans rien toucher")
        avancer(1_800)
        attendreHeros("prog:movie:202", "6,1 s sans rien toucher")
        if (focus()?.etiquette != dansLeDock) fail("[GALERIE] défilement : le focus a quitté le dock (${decrire(focus())})")
        avancer(5_000)
        attendreHeros("prog:tv:302", "deuxième défilement")
        // Un appui relance l'attente
        appuyer(Key.DirectionLeft)
        avancer(3_500)
        attendreHeros("prog:tv:302", "4,3 s après un appui")
        avancer(1_500)
        attendreHeros("prog:tv:402", "5,8 s après un appui")
        // Focus sur la carte : on lit, rien ne bouge
        donnerFocus("galerie:carte")
        avancer(16_000)
        attendreHeros("prog:tv:402", "focus sur la carte")
        // Désactivé : rien ne bouge non plus depuis le dock
        compose.runOnIdle { DefilementGalerie.ecrire(appli, 0) }
        donnerFocus(favori(0))
        avancer(16_000)
        attendreHeros("prog:tv:402", "défilement désactivé")
    }
}

/** Sans clé TMDB ou en panne : les apps à la une (repli de Studio), jamais d'écran cassé. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class GalerieRepliTest(private val cas: String) : BancGalerie() {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cas(): List<Array<Any>> = listOf("sans clé", "panne", "sans favori").map { arrayOf<Any>(it) }
    }

    init {
        sourceStudio = when (cas) {
            "sans clé" -> TmdbFactice.source(cle = null)
            "panne" -> TmdbFactice.source(panne = true)
            else -> TmdbFactice.source()
        }
    }

    override fun preparer(u: HomeUi): HomeUi =
        if (cas == "sans favori") u.copy(favorites = emptyList(), prefs = u.prefs.copy(favorites = emptyList())) else u

    @Test
    fun repli() {
        afficher()
        avancer(500)
        if (cas == "sans favori") {
            attendreHeros("prog:tv:101", "sans favori")
            appuyer(Key.DirectionUp)
            if (focus()?.etiquette?.startsWith("galerie:") != true) fail("[GALERIE] carte d'aide puis ▲ : ${decrire(focus())}")
            capturer("g9_sans_favori")
            return
        }
        attendreHeros("une:com.netflix.ninja", cas)
        if (!textePresent("Ouvrir Netflix")) fail("[GALERIE] $cas : « Ouvrir Netflix » absent")
        if (present("galerie:ajout")) fail("[GALERIE] $cas : « Ajouter à ma liste » sur une app")
        if (textePresent("Titres et images : TMDB")) fail("[GALERIE] $cas : attribution TMDB sans titre TMDB")
        val message = if (cas == "sans clé") "Activez les suggestions" else "Suggestions indisponibles"
        if (!textePresent(message)) fail("[GALERIE] $cas : message « $message » absent")
        donnerFocus("galerie:regarder")
        appuyer(Key.DirectionCenter)
        if (lances != listOf("com.netflix.ninja")) fail("[GALERIE] $cas : Ouvrir : lancés $lances")
        donnerFocus("galerie:carte")
        appuyerEtAttendre(Key.DirectionRight)
        attendreHeros("une:com.amazon.amazonvideo.livingroom", "$cas, ▶")
        if (cas == "sans clé") capturer("g7_repli_sans_cle")
    }
}
