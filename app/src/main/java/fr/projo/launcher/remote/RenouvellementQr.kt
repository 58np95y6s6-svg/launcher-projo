package fr.projo.launcher.remote

/**
 * QR code d'appairage affiché en permanence par le module « Télécommande » de l'accueil :
 * décide QUAND renouveler le secret de 5 minutes.
 *
 * - Un nouveau secret seulement si au moins un module est affiché ([afficher] : accueil au
 *   premier plan) et que l'écran est allumé. Accueil caché ou écran éteint : rien n'est créé.
 * - À l'échéance du secret (ou dès qu'il est consommé), un nouveau ; mais jamais pendant une
 *   confirmation « Autoriser / Refuser » (voir [GestionAppairage.renouvelerSiEchu]) : on
 *   attend sa fin. Le QR code exposé en permanence reste sûr : tout appairage doit être
 *   confirmé sur la TV, avec la limite de tentatives.
 * - Après un appairage ([appaire]) : « ✓ » affiché [RemoteConfig.PAUSE_APRES_APPAIRAGE_MS], et
 *   tant que le dialogue en grand est ouvert, puis nouveau secret.
 *
 * Sans fil d'exécution ni Android : Telecommande l'appelle sur son fil, les tests avec une
 * horloge simulée (la même que celle de [appairage]).
 */
class RenouvellementQr(
    private val appairage: GestionAppairage,
    private val horloge: () -> Long = { System.currentTimeMillis() },
) {
    /** Modules affichés (une clé par module, posée tant que l'accueil est au premier plan). */
    private val affichages = HashSet<Any>()

    var ecranAllume = true

    /** Dialogue d'appairage ouvert (Réglages, ou OK sur le module). */
    var dialogueOuvert = false

    /** « ✓ <nom> est appairé » affiché jusqu'à cette heure (null : pas d'appairage récent). */
    private var reussiteJusqua: Long? = null

    /** Le QR code doit rester valable : module affiché et écran allumé. */
    val actif: Boolean get() = ecranAllume && affichages.isNotEmpty()

    /** Un module s'affiche ; rend true si c'est nouveau. */
    fun afficher(cle: Any): Boolean = affichages.add(cle)

    /** Un module disparaît (accueil caché, module retiré) ; rend true s'il était affiché. */
    fun masquer(cle: Any): Boolean = affichages.remove(cle)

    /** Un téléphone vient d'être appairé (son secret est consommé). */
    fun appaire() {
        reussiteJusqua = horloge() + RemoteConfig.PAUSE_APRES_APPAIRAGE_MS
    }

    /** « ✓ appairé » encore à l'écran : pas de nouveau secret. */
    private val reussiteAffichee: Boolean
        get() = reussiteJusqua?.let { dialogueOuvert || horloge() < it } ?: false

    /**
     * À appeler à chaque événement (module affiché, échéance, fin de confirmation, dialogue
     * fermé, écran rallumé…). Rend le nouveau secret à afficher, ou null si rien ne change.
     */
    fun maintenir(): SecretAppairage? {
        if (!actif || reussiteAffichee) return null
        val nouveau = appairage.renouvelerSiEchu() ?: return null
        reussiteJusqua = null
        return nouveau
    }

    /**
     * Quand rappeler [maintenir] (même horloge) : échéance du secret, ou fin du « ✓ ». null :
     * rien à attendre sur l'horloge (module caché, écran éteint, secret expiré pendant une
     * confirmation, dialogue « ✓ » ouvert) : la fin de l'événement rappelle [maintenir].
     */
    fun prochainRappel(): Long? {
        if (!actif) return null
        if (reussiteAffichee) return if (dialogueOuvert) null else reussiteJusqua
        return appairage.actif()?.expire
    }
}
