package fr.projo.launcher.ambiance

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import fr.projo.launcher.MainActivity
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader

/**
 * Le néon à l'écran, porté par le service d'accessibilité déjà actif sur Fire TV
 * (RedirectionAccueil) : 4 fenêtres « TYPE_ACCESSIBILITY_OVERLAY » le long des bords
 * (aucune autorisation de plus), non focalisables, non touchables : le centre de l'écran
 * n'est pas couvert, la télécommande et le téléphone pilotent l'app comme avant.
 *
 * Affiché seulement tant que l'app concernée est au premier plan (voir [LogiqueCadre]) ;
 * caché aussitôt sinon, et tout est libéré (fenêtres, Visualizer, session média).
 */
class CadreAmbiance(private val service: AccessibilityService) {

    private val principal = Handler(Looper.getMainLooper())
    private val logique get() = ModeAmbiance.logique

    private val moteur = MoteurNeon()
    private val analyse = AnalyseSpectre()
    private val detecteur = DetecteurMuet()
    private var capture: CaptureSon? = null
    private var pochette: LecteurPochette? = null
    private var dessin: DessinNeon? = null
    private var bandes: Array<BandeNeon>? = null
    private var gestionnaire: WindowManager? = null
    private var audio: AudioManager? = null

    /** App autour de laquelle le néon est affiché (null : néon caché). */
    var affichePour: String? = null
        private set

    private var couleurPochette: Int? = null
    private var couleurIcone: Int? = null

    /** Réglage « Couleurs du néon », lu à l'allumage (Réglages ne s'ouvre pas pendant le néon). */
    private var reglageCouleurs = CouleursNeon.POCHETTE
    private var dernierInstant = 0L
    private var sauter = false
    private var musiqueActive = false
    private var prochaineLectureMusique = 0L

    // Son lu une image sur deux (~30 fois par seconde : chaque lecture passe par le serveur audio)
    private var lireSon = false
    private var dtSon = 0f
    private var dernierSilence = true
    private var corrections = 0
    private val position = IntArray(2)

    // Dernier état signalé à Réglages (diagnostic)
    private var sourceSignalee: SourceAmbiance? = null
    private var teinteSignalee: Teinte? = null
    private var sonSignale: CaptureSon.Etat? = null
    private var muetSignale = false
    private var pochetteSignalee: LecteurPochette.Etat? = null

    private val image = Choreographer.FrameCallback { sansPlantage { surImage(it) } }
    private val ecoute: () -> Unit = { sansPlantage { mettreAJour() } }

    /** Vérifications de l'app vraiment active après une fenêtre d'une autre app (volume…). */
    private var verificationsRestantes = 0
    private val verification = Runnable { sansPlantage { verifierPremierPlan() } }

    // Appui long sur ☰ (touche Menu)
    private var menuSuivi = false
    private var menuLong = false
    private val appuiLongMenu = Runnable {
        menuLong = true
        basculer()
    }

    fun attacher() {
        ModeAmbiance.charger(service)
        ModeAmbiance.surChangement = ecoute
    }

    /** Fenêtre au premier plan (événement TYPE_WINDOW_STATE_CHANGED). */
    fun fenetre(paquet: String, classe: String?, accueils: Set<String>) {
        sansPlantage { suivreFenetre(paquet, classe, accueils) }
    }

    private fun suivreFenetre(paquet: String, classe: String?, accueils: Set<String>) {
        dernieresAccueils = accueils
        val sorte = LogiqueCadre.classer(paquet, classe, service.packageName, MainActivity::class.java.name, accueils, clavierActuel())
        if (sorte != LogiqueCadre.Ecran.IGNORE) {
            logique.ecran(sorte, paquet)
            mettreAJour()
            if (sorte == LogiqueCadre.Ecran.APP) planifierVerifications()
        }
    }

    /**
     * Le service porte aussi l'ouverture automatique (Maison) et la télécommande smartphone :
     * une erreur du néon ne doit jamais l'arrêter. Néon caché en cas de problème.
     */
    private inline fun sansPlantage(bloc: () -> Unit) {
        try {
            bloc()
        } catch (e: Exception) {
            Log.w(TAG, "Mode Ambiance : erreur, néon caché", e)
            try { cacher() } catch (e2: Exception) { /* tant pis */ }
        }
    }

    private var dernieresAccueils: Set<String> = emptySet()

    /** Écran éteint, rallumé, économiseur d'écran. */
    fun ecran(action: String?) {
        sansPlantage { suivreEcran(action) }
    }

    private fun suivreEcran(action: String?) {
        val connu = when (action) {
            Intent.ACTION_SCREEN_OFF -> { logique.ecranAllume = false; true }
            Intent.ACTION_SCREEN_ON -> { logique.ecranAllume = true; planifierVerifications(); true }
            Intent.ACTION_DREAMING_STARTED -> { logique.enReve = true; true }
            Intent.ACTION_DREAMING_STOPPED -> { logique.enReve = false; planifierVerifications(); true }
            else -> false
        }
        if (connu) mettreAJour()
    }

    /**
     * Touche ☰ (Menu). Appui court : passe à l'app, comme avant (rien n'est retenu). Appui
     * long (0,7 s) dans une app : allume ou éteint le néon, et la suite de l'appui
     * (répétitions, relâchement) est gardée pour que l'app ne la traite pas.
     * Sur iBiProjo et l'accueil : la touche passe toujours sans changement.
     */
    fun toucheMenu(event: KeyEvent): Boolean = try {
        suivreMenu(event)
    } catch (e: Exception) {
        false
    }

    private fun suivreMenu(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
            principal.removeCallbacks(appuiLongMenu)
            menuLong = false
            menuSuivi = logique.basculable
            if (menuSuivi) principal.postDelayed(appuiLongMenu, DUREE_APPUI_LONG)
            false
        } else {
            menuSuivi
        }
        KeyEvent.ACTION_UP -> {
            principal.removeCallbacks(appuiLongMenu)
            val garder = menuSuivi && menuLong
            menuSuivi = false
            menuLong = false
            garder
        }
        else -> false
    }

    private fun basculer() {
        sansPlantage { allumerOuEteindre() }
    }

    private fun allumerOuEteindre() {
        val allume = logique.basculer(ModeAmbiance.auto) { ModeAmbiance.estMusique(service, it) } ?: return
        mettreAJour()
        try {
            Toast.makeText(
                service,
                if (allume) "Néon allumé · appui long sur ☰ pour l'éteindre" else "Néon éteint · appui long sur ☰ pour le rallumer",
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            // sans importance
        }
    }

    fun liberer() {
        try { cacher() } catch (e: Exception) { /* service arrêté */ }
        principal.removeCallbacksAndMessages(null)
        if (ModeAmbiance.surChangement === ecoute) ModeAmbiance.surChangement = null
        capture = null
        pochette = null
        dessin = null
    }

    // ------------------------------------------------------------------ affichage

    private fun mettreAJour() {
        val p = logique.premierPlan
        val doit = logique.visible(ModeAmbiance.auto) { ModeAmbiance.estMusique(service, it) }
        if (doit && p != null) {
            if (affichePour != p) {
                if (affichePour != null) cacher()
                afficher(p)
            }
        } else if (affichePour != null) {
            cacher()
        }
    }

    private fun afficher(paquet: String) {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val taille = Point()
        try {
            // Dimensions réelles de l'affichage (overscan compris) : le néon suit les vrais bords
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(taille)
        } catch (e: Exception) {
            return
        }
        if (taille.x < 200 || taille.y < 200) return
        val d = dessin?.takeIf { it.largeur == taille.x && it.hauteur == taille.y }
            ?: DessinNeon(taille.x, taille.y).also { dessin = it }
        val vues = ArrayList<BandeNeon>(4)
        try {
            for (z in zones(taille.x, taille.y, d.profondeur)) {
                val v = BandeNeon(service, d, z) { corrigerPosition(it) }
                v.alpha = 0f
                wm.addView(v, parametres(z))
                vues += v
            }
        } catch (e: Exception) {
            // Service pas encore relié, ou fenêtre refusée : pas de néon, rien de cassé
            Log.w(TAG, "Néon impossible à afficher", e)
            for (v in vues) retirer(wm, v)
            return
        }
        gestionnaire = wm
        bandes = vues.toTypedArray()
        affichePour = paquet
        ModeAmbiance.affichePour = paquet
        corrections = 0
        // Petit fondu à l'allumage
        for (v in vues) v.animate().alpha(1f).setDuration(450).start()

        moteur.intensite = ModeAmbiance.intensite
        reglageCouleurs = ModeAmbiance.couleurs
        moteur.reinitialiser()
        couleurPochette = null
        couleurIcone = ModeAmbiance.couleursIcones[paquet]
        moteur.changerCouleur(couleurIcone ?: MoteurNeon.COULEUR_PAR_DEFAUT, immediat = true)
        if (couleurIcone == null) chercherCouleurIcone(paquet)
        // Son en direct (si autorisé), sinon repli
        val c = capture ?: CaptureSon(service).also { capture = it }
        c.ouvrir()
        if (c.etat == CaptureSon.Etat.ACTIVE) analyse.configurer(c.taille, c.frequenceHz)
        detecteur.reinitialiser()
        lireSon = false
        dtSon = 0f
        dernierSilence = true
        if (audio == null) audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        prochaineLectureMusique = 0L
        // Pochette du morceau de cette app (accès aux notifications)
        if (reglageCouleurs == CouleursNeon.POCHETTE) {
            val lp = pochette ?: LecteurPochette(service) { couleur -> couleurPochette = couleur }.also { pochette = it }
            lp.demarrer(paquet)
        }
        dernierInstant = 0L
        sourceSignalee = null
        // Source affichée tout de suite dans Réglages (mise à jour ensuite à chaque changement)
        val teinte = teinteNeon(reglageCouleurs, false)
        signaler(sourceNeon(c.etat == CaptureSon.Etat.ACTIVE, teinte), teinte, c.etat)
        Choreographer.getInstance().postFrameCallback(image)
    }

    private fun cacher() {
        try { Choreographer.getInstance().removeFrameCallback(image) } catch (e: Exception) { /* fil sans Choreographer */ }
        val wm = gestionnaire
        bandes?.forEach { retirer(wm, it) }
        bandes = null
        capture?.fermer()
        pochette?.arreter()
        affichePour = null
        ModeAmbiance.affichePour = null
    }

    private fun retirer(wm: WindowManager?, v: View) {
        try {
            v.animate().cancel()
            wm?.removeViewImmediate(v)
        } catch (e: Exception) {
            // déjà retirée
        }
    }

    /** Les 4 bandes : haut et bas sur toute la largeur, côtés entre les deux. */
    private fun zones(w: Int, h: Int, p: Int): Array<Rect> = arrayOf(
        Rect(0, 0, w, p),
        Rect(0, h - p, w, h),
        Rect(0, p, p, h - p),
        Rect(w - p, p, w, h - p),
    )

    @Suppress("DEPRECATION")
    private fun parametres(z: Rect) = WindowManager.LayoutParams(
        z.width(), z.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            // Avec un overscan (wm overscan), le néon peut se dessiner dans la bordure noire
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = z.left
        y = z.top
        windowAnimations = 0
        title = "iBiProjo · néon"
    }

    /**
     * Fenêtre placée ailleurs que prévu (overscan : origine décalée par le système) : on
     * la recale sur les bords réels de l'écran.
     */
    private fun corrigerPosition(v: BandeNeon) {
        if (corrections >= 8 || bandes == null) return
        v.getLocationOnScreen(position)
        val dx = position[0] - v.zone.left
        val dy = position[1] - v.zone.top
        if (dx == 0 && dy == 0) return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        corrections++
        lp.x -= dx
        lp.y -= dy
        principal.post {
            try { if (v.isAttachedToWindow) gestionnaire?.updateViewLayout(v, lp) } catch (e: Exception) { /* fenêtre retirée */ }
        }
    }

    // ------------------------------------------------------------------ chaque image

    private fun surImage(nanos: Long) {
        val vues = bandes ?: return
        val d = dessin ?: return
        // Image suivante : au vsync qui suit (8 ms de délai, moins qu'un intervalle de 16,7 ms).
        // Sans délai, l'horloge des tests (Robolectric), avancée à chaque vsync, rendrait
        // l'image suivante aussitôt due : une boucle sans fin.
        Choreographer.getInstance().postFrameCallbackDelayed(image, DELAI_IMAGE)
        val c = capture
        val sonPossible = c != null && c.etat == CaptureSon.Etat.ACTIVE
        // Sans son en direct, 30 images par seconde suffisent (pulsation lente)
        if (!sonPossible || detecteur.muet) {
            sauter = !sauter
            if (sauter && dernierInstant != 0L) return
        }
        val dt = if (dernierInstant == 0L) 1f / 60f else ((nanos - dernierInstant) / 1e9f).coerceIn(0.001f, 0.1f)
        dernierInstant = nanos

        var attaque = 0f
        if (sonPossible) {
            dtSon += dt
            lireSon = !lireSon
            if (lireSon) {
                dernierSilence = if (c!!.lire()) {
                    analyse.analyser(c.fft, dtSon)
                    attaque = analyse.attaque
                    analyse.silence
                } else {
                    true
                }
                dtSon = 0f
            }
        }
        val maintenant = SystemClock.uptimeMillis()
        if (maintenant >= prochaineLectureMusique) {
            musiqueActive = try { audio?.isMusicActive == true } catch (e: Exception) { false }
            prochaineLectureMusique = maintenant + 500
        }
        if (sonPossible) detecteur.suivre(dernierSilence, musiqueActive, dt)
        val sonDirect = sonPossible && !detecteur.muet
        val teinte = teinteNeon(reglageCouleurs, couleurPochette != null)
        val source = sourceNeon(sonDirect, teinte)
        moteur.teinte = teinte
        moteur.reagitAuSon = sonDirect
        if (sonDirect) {
            System.arraycopy(analyse.niveaux, 0, moteur.cibles, 0, AnalyseSpectre.NB_BANDES)
            if (attaque > 0f) moteur.sursauter(attaque)
        }
        when (teinte) {
            Teinte.POCHETTE -> couleurPochette?.let { moteur.changerCouleur(it) }
            Teinte.ICONE -> moteur.changerCouleur(couleurIcone ?: MoteurNeon.COULEUR_PAR_DEFAUT)
            Teinte.ARC_EN_CIEL -> {}
        }
        moteur.avancer(dt)
        d.preparer(moteur)
        for (v in vues) v.invalidate()
        signaler(source, teinte, c?.etat)
    }

    /** Source affichée dans Réglages (seulement quand elle change). */
    private fun signaler(source: SourceAmbiance, teinte: Teinte, son: CaptureSon.Etat?) {
        val muet = detecteur.muet
        val etatPochette = pochette?.etat
        if (source == sourceSignalee && teinte == teinteSignalee && son == sonSignale && muet == muetSignale &&
            etatPochette == pochetteSignalee
        ) return
        sourceSignalee = source
        teinteSignalee = teinte
        sonSignale = son
        muetSignale = muet
        pochetteSignalee = etatPochette
        ModeAmbiance.source = source
        ModeAmbiance.diagnostic = texteDiagnostic(source, teinte, son, muet, etatPochette)
    }

    // ------------------------------------------------------------------ outils

    private fun planifierVerifications() {
        if (logique.demande == null && !ModeAmbiance.auto) return
        verificationsRestantes = DELAIS_VERIFICATION.size
        principal.removeCallbacks(verification)
        principal.postDelayed(verification, DELAIS_VERIFICATION[0])
    }

    /** App vraiment active (seulement son nom de paquet), après une fenêtre de passage. */
    private fun verifierPremierPlan() {
        val actif = try {
            val racine = service.rootInActiveWindow
            val p = racine?.packageName?.toString()
            @Suppress("DEPRECATION")
            racine?.recycle()
            p
        } catch (e: Exception) {
            null
        }
        if (actif != null && actif != logique.premierPlan) {
            val sorte = LogiqueCadre.classer(actif, null, service.packageName, MainActivity::class.java.name, dernieresAccueils, clavierActuel())
            if (sorte == LogiqueCadre.Ecran.APP) {
                logique.ecran(sorte, actif)
                mettreAJour()
            }
        }
        verificationsRestantes--
        if (verificationsRestantes > 0) {
            principal.postDelayed(verification, DELAIS_VERIFICATION[DELAIS_VERIFICATION.size - verificationsRestantes])
        }
    }

    /** Paquet du clavier actuel (ses fenêtres ne changent pas l'app au premier plan). */
    private fun clavierActuel(): String? = try {
        Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
    } catch (e: Exception) {
        null
    }

    /** Couleur de l'icône de l'app (hors du fil principal), gardée pour les fois suivantes. */
    private fun chercherCouleurIcone(paquet: String) {
        val appli = service.applicationContext
        val fil = Thread {
            val couleur = try {
                val v = IconLoader(appli).load(paquet, FormatVisuel.CARRE)
                LecteurPochette.couleurNeon(intArrayOf(v.color.toArgb()), intArrayOf(1))
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            if (couleur != null) {
                principal.post {
                    ModeAmbiance.couleursIcones[paquet] = couleur
                    if (affichePour == paquet) couleurIcone = couleur
                }
            }
        }
        fil.name = "ambiance-icone"
        fil.isDaemon = true
        fil.start()
    }

    companion object {
        private const val TAG = "Projo"

        /** Au-delà, l'appui sur ☰ est un appui long (comme Maison). */
        const val DUREE_APPUI_LONG = 700L

        private val DELAIS_VERIFICATION = longArrayOf(700L, 1_500L, 3_000L, 6_000L)

        /** Délai avant de redemander une image (ms). */
        private const val DELAI_IMAGE = 8L

        /** Texte du diagnostic : source, couleur, et pourquoi le son en direct manque. */
        fun texteDiagnostic(
            source: SourceAmbiance,
            teinte: Teinte,
            son: CaptureSon.Etat?,
            muet: Boolean,
            pochette: LecteurPochette.Etat?,
        ): String {
            val couleur = when (teinte) {
                Teinte.POCHETTE -> "couleur de la pochette"
                Teinte.ICONE -> "couleur de l'app"
                Teinte.ARC_EN_CIEL -> "arc-en-ciel"
            }
            val manque = when {
                son == CaptureSon.Etat.REFUSEE -> "micro non autorisé"
                son == CaptureSon.Etat.INDISPONIBLE -> "Visualizer indisponible"
                muet -> "son muet (lecture directe ?)"
                else -> null
            }
            val sansPochette = if (teinte == Teinte.ICONE && pochette == LecteurPochette.Etat.SANS_ACCES) {
                " · pochette : accès aux notifications à activer"
            } else {
                ""
            }
            return when (source) {
                SourceAmbiance.SON -> "son en direct · $couleur$sansPochette"
                SourceAmbiance.POCHETTE -> "couleurs de la pochette" + (manque?.let { " · $it" } ?: "")
                SourceAmbiance.ANIMATION -> "animation · $couleur" + (manque?.let { " · $it" } ?: "") + sansPochette
            }
        }
    }
}

/**
 * Une bande du néon (haut, bas, gauche, droite) : une View classique, dessinée par la carte
 * graphique, qui montre sa partie du néon (coordonnées de l'écran décalées de [zone]).
 */
class BandeNeon(
    context: Context,
    private val dessin: DessinNeon,
    val zone: Rect,
    private val surPlace: (BandeNeon) -> Unit = {},
) : View(context) {

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        isClickable = false
    }

    override fun onDraw(canvas: Canvas) {
        val n = canvas.save()
        canvas.translate(-zone.left.toFloat(), -zone.top.toFloat())
        dessin.dessiner(canvas)
        canvas.restoreToCount(n)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) surPlace(this)
    }
}
