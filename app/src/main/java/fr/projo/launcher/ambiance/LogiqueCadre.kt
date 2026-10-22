package fr.projo.launcher.ambiance

/**
 * Quand afficher le néon : la logique seule, sans Android (testable).
 *
 * Le néon s'affiche autour de l'app au premier plan :
 *  - automatiquement si c'est une app de musique (réglage « Automatique avec les apps de
 *    musique ») ;
 *  - ou si on l'a demandé pour CETTE app : « Ouvrir en mode Ambiance » (menu d'app
 *    d'iBiProjo) ou appui long sur ☰ dans l'app.
 * Jamais sur l'accueil (iBiProjo, accueil Amazon), écran éteint ou économiseur d'écran.
 * Maison termine la demande : en revenant dans l'app sans passer par le mode, pas de néon
 * (sauf app de musique en automatique).
 */
class LogiqueCadre(
    /** Paquet d'iBiProjo (son écran principal est l'accueil). */
    private val monPaquet: String,
    private val horloge: () -> Long,
) {
    /** Sorte d'écran annoncé par le service d'accessibilité. */
    enum class Ecran {
        /** Accueil : iBiProjo ou l'accueil du fabricant. */
        ACCUEIL,

        /** Une app (son paquet). */
        APP,

        /** Fenêtre de passage, sans changement d'app : clavier, volume, Alexa… */
        IGNORE,
    }

    /** App au premier plan ; null : accueil ou inconnu. */
    var premierPlan: String? = null
        private set

    var ecranAllume = true
    var enReve = false

    /** App pour laquelle le mode a été demandé (menu ou ☰), null : aucune. */
    var demande: String? = null
        private set
    private var debutDemande = 0L
    private var demandeVue = false

    /** Néon éteint à la main (☰) pour cette app, même si c'est une app de musique. */
    private var eteintPour: String? = null

    /** Demande en cours : l'app a été vue, ou elle est en train de s'ouvrir (30 s au plus). */
    val demandeActive: Boolean
        get() = demande != null && (demandeVue || horloge() - debutDemande < ATTENTE_OUVERTURE)

    /** « Ouvrir en mode Ambiance » : le néon s'allumera quand [paquet] sera au premier plan. */
    fun demander(paquet: String) {
        demande = paquet
        debutDemande = horloge()
        demandeVue = paquet == premierPlan
        if (eteintPour == paquet) eteintPour = null
    }

    fun arreter() {
        demande = null
        demandeVue = false
    }

    /** Retour à l'état de départ (tests). */
    fun reinitialiser() {
        arreter()
        premierPlan = null
        eteintPour = null
        ecranAllume = true
        enReve = false
    }

    /** Nouvel écran au premier plan. */
    fun ecran(sorte: Ecran, paquet: String?) {
        when (sorte) {
            Ecran.IGNORE -> return
            Ecran.ACCUEIL -> {
                premierPlan = null
                // Maison après avoir vu l'app : la demande est finie. Avant (l'app s'ouvre
                // depuis iBiProjo, dont le menu se ferme), elle reste.
                if (demandeVue) arreter()
                eteintPour = null
            }
            Ecran.APP -> {
                premierPlan = paquet
                if (paquet != null && paquet == demande && demandeActive) demandeVue = true
            }
        }
    }

    /**
     * Le néon doit-il être affiché ? [auto] : réglage « Automatique avec les apps de musique »,
     * [musique] : l'app est-elle une app de musique.
     */
    fun visible(auto: Boolean, musique: (String) -> Boolean): Boolean {
        val p = premierPlan ?: return false
        if (!ecranAllume || enReve || p == monPaquet || p == eteintPour) return false
        if (p == demande && demandeActive) return true
        return auto && musique(p)
    }

    /** L'appui long sur ☰ peut-il allumer ou éteindre le néon ? (une vraie app au premier plan) */
    val basculable: Boolean
        get() = premierPlan.let { it != null && it != monPaquet } && ecranAllume && !enReve

    /**
     * Appui long sur ☰ : allume ou éteint le néon pour l'app au premier plan.
     * Renvoie le nouvel état (true = allumé), ou null si rien à faire (pas d'app).
     */
    fun basculer(auto: Boolean, musique: (String) -> Boolean): Boolean? {
        if (!basculable) return null
        val p = premierPlan ?: return null
        return if (visible(auto, musique)) {
            if (demande == p) arreter()
            eteintPour = p
            false
        } else {
            eteintPour = null
            demande = p
            debutDemande = horloge()
            demandeVue = true
            true
        }
    }

    companion object {
        /** Une app demandée qui ne s'ouvre pas en 30 s : demande oubliée. */
        const val ATTENTE_OUVERTURE = 30_000L

        /** Fenêtres de passage du système (volume, Alexa…) : l'app en dessous reste au premier plan. */
        val PASSAGERS = setOf("android", "com.android.systemui", "com.amazon.vizzini")

        /**
         * Sorte d'un écran annoncé (événement « fenêtre au premier plan »).
         * [classeAccueil] : écran principal d'iBiProjo ; [accueils] : accueils du fabricant ;
         * [clavier] : paquet du clavier actuel.
         */
        fun classer(
            paquet: String,
            classe: String?,
            monPaquet: String,
            classeAccueil: String,
            accueils: Set<String>,
            clavier: String?,
        ): Ecran = when {
            // Claviers (le nôtre compris, même paquet qu'iBiProjo) : l'app reste au premier plan
            classe != null && classe.startsWith("android.inputmethodservice.") -> Ecran.IGNORE
            paquet == clavier -> Ecran.IGNORE
            paquet == monPaquet -> if (classe == classeAccueil) Ecran.ACCUEIL else Ecran.IGNORE
            paquet in accueils -> Ecran.ACCUEIL
            paquet in PASSAGERS -> Ecran.IGNORE
            else -> Ecran.APP
        }
    }
}
