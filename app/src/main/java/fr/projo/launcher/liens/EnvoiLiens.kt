package fr.projo.launcher.liens

/** D'où vient un lien (journal, bandeau, garde-fou). */
enum class SourceLien(val journal: String) {
    TELEPHONE("téléphone"),
    RACCOURCI("raccourci"),
}

/** Réponse à une demande d'ouverture (`lien_ouvert`, réponse HTTP du raccourci). */
sealed class ResultatLien {
    /** Le lien s'ouvre dans [appli] (nom affiché). */
    data class Ouvert(val appli: String) : ResultatLien()

    /** Rien ne s'ouvre (pour l'instant) : [raison] du protocole (`vpn_coupe`, `attente_vpn`…). */
    data class Refuse(val raison: String) : ResultatLien()

    /** Raccourci iOS, VPN coupé : la TV pose la question à l'écran (garde-fou). */
    data object Question : ResultatLien()

    companion object {
        const val VPN_COUPE = "vpn_coupe"
        const val ATTENTE_VPN = "attente_vpn"
        const val DELAI_VPN = "delai_vpn"
        const val PAS_D_APPLI_VPN = "pas_d_appli_vpn"
        const val REMPLACE = "remplace"
        const val INVALIDE = "invalide"
        const val AUCUNE_APPLI = "aucune_appli"
    }
}

/** Garde-fou à l'écran : « Le VPN de la TV est coupé » pour un lien du raccourci. */
data class QuestionVpn(
    val lien: LienTv,
    /** « l'iPhone » (déjà avec son article, voir Liens.expediteur). */
    val expediteur: String,
    /** Appli VPN à ouvrir (« Allumer le VPN et ouvrir »), null : bouton absent. */
    val appliVpn: String?,
)

/** Réponse au garde-fou. */
enum class ChoixQuestion { ALLUMER_VPN, SANS_VPN, ANNULER }

/** Ce que la plateforme (Android, ou un banc de test) fournit à [EnvoiLiens]. Un seul fil. */
interface PlateformeLiens {
    /** Un VPN est actif sur la TV (réseau de type VPN, quelle que soit l'appli). */
    fun vpnActif(): Boolean

    /** Réglage « Seulement avec le VPN ». */
    fun seulementVpn(): Boolean

    /** Nom de l'appli VPN que la TV sait ouvrir, ou null. */
    fun appliVpn(): String?

    /** Ouvre l'appli VPN ; false si c'est impossible. */
    fun ouvrirAppliVpn(): Boolean

    /**
     * Ouvre [lien] dans la bonne appli (bandeau d'abord si iBiTV est à l'écran). [rappel] (sur ce
     * même fil) : nom de l'appli qui s'ouvre, null si aucune ne sait ouvrir ce lien.
     */
    fun ouvrir(lien: LienTv, expediteur: String, rappel: (String?) -> Unit)

    /** Affiche le garde-fou (iBiTV passe au premier plan) ; null : le retire. */
    fun montrerQuestion(q: QuestionVpn?)

    /** Exécute [tache] dans [delaiMs] ms sur ce même fil ; rend de quoi l'annuler. */
    fun planifier(delaiMs: Long, tache: () -> Unit): () -> Unit

    /** Types d'événements seulement : jamais l'URL, le titre ni le code. */
    fun journal(evenement: String)
}

/**
 * Déroulé d'un lien envoyé sur la TV (docs/telecommande.md, « lien_ouvrir ») :
 *  - VPN coupé avec « Seulement avec le VPN » : `vpn_coupe` (téléphone) ou garde-fou à l'écran
 *    (raccourci iOS), sauf `sansVpn` ;
 *  - `vpnDabord` (ou « Allumer le VPN et ouvrir ») : l'appli VPN s'ouvre, `attente_vpn`, puis le
 *    lien s'ouvre dès qu'un VPN s'allume ([surVpn]) ou `delai_vpn` après 60 s. Une seule attente
 *    à la fois : la nouvelle remplace l'ancienne (`remplace`).
 *
 * [envoyer] reçoit un lien déjà vérifié ; son [rappel] peut être appelé deux fois (`attente_vpn`
 * puis le résultat). Tout se passe sur un seul fil (celui de [PlateformeLiens.planifier]).
 */
class EnvoiLiens(private val p: PlateformeLiens) {

    private class Attente(
        val lien: LienTv,
        val expediteur: String,
        val rappel: ((ResultatLien) -> Unit)?,
        var annuler: () -> Unit = {},
    )

    private var attente: Attente? = null

    /** Garde-fou affiché, ou null. */
    var question: QuestionVpn? = null
        private set

    /** Un lien attend qu'un VPN s'allume. */
    val enAttente: Boolean get() = attente != null

    /**
     * Ouvre [lien] (déjà vérifié), envoyé par [expediteur] (« le téléphone de Marie »,
     * « l'iPhone »). `vpnDabord` l'emporte sur `sansVpn` si les deux sont demandés.
     */
    fun envoyer(
        lien: LienTv,
        source: SourceLien,
        expediteur: String,
        sansVpn: Boolean = false,
        vpnDabord: Boolean = false,
        rappel: (ResultatLien) -> Unit,
    ) {
        if (!p.vpnActif()) {
            if (vpnDabord) {
                attendreVpn(lien, expediteur, rappel)
                return
            }
            if (p.seulementVpn() && !sansVpn) {
                if (source == SourceLien.RACCOURCI) {
                    // Une seule question à la fois : la plus récente
                    val q = QuestionVpn(lien, expediteur, p.appliVpn())
                    question = q
                    p.journal("garde-fou affiché")
                    p.montrerQuestion(q)
                    rappel(ResultatLien.Question)
                } else {
                    refuser(rappel, ResultatLien.VPN_COUPE)
                }
                return
            }
        }
        ouvrirMaintenant(lien, expediteur, rappel)
    }

    /** Le VPN s'allume ou se coupe : le lien en attente s'ouvre dès qu'il s'allume. */
    fun surVpn(actif: Boolean) {
        if (!actif) return
        val a = attente ?: return
        attente = null
        a.annuler()
        ouvrirMaintenant(a.lien, a.expediteur, a.rappel ?: { _: ResultatLien -> })
    }

    /** Réponse au garde-fou affiché. */
    fun repondre(choix: ChoixQuestion) {
        val q = question ?: return
        question = null
        p.montrerQuestion(null)
        when (choix) {
            // VPN allumé entre-temps : on ouvre tout de suite
            ChoixQuestion.ALLUMER_VPN -> if (p.vpnActif()) ouvrirMaintenant(q.lien, q.expediteur) {} else attendreVpn(q.lien, q.expediteur, null)
            ChoixQuestion.SANS_VPN -> ouvrirMaintenant(q.lien, q.expediteur) {}
            ChoixQuestion.ANNULER -> p.journal("lien refusé annule")
        }
    }

    private fun attendreVpn(lien: LienTv, expediteur: String, rappel: ((ResultatLien) -> Unit)?) {
        if (p.appliVpn() == null) {
            refuser(rappel, ResultatLien.PAS_D_APPLI_VPN)
            return
        }
        // Une seule attente à la fois : l'ancienne est remplacée
        attente?.let { ancienne ->
            attente = null
            ancienne.annuler()
            refuser(ancienne.rappel, ResultatLien.REMPLACE)
        }
        if (!p.ouvrirAppliVpn()) {
            refuser(rappel, ResultatLien.PAS_D_APPLI_VPN)
            return
        }
        val a = Attente(lien, expediteur, rappel)
        attente = a
        a.annuler = p.planifier(Liens.ATTENTE_VPN_MS) {
            if (attente === a) {
                attente = null
                refuser(a.rappel, ResultatLien.DELAI_VPN)
            }
        }
        refuser(rappel, ResultatLien.ATTENTE_VPN)
    }

    private fun ouvrirMaintenant(lien: LienTv, expediteur: String, rappel: (ResultatLien) -> Unit) {
        p.ouvrir(lien, expediteur) { appli ->
            if (appli != null) {
                p.journal("lien ouvert")
                rappel(ResultatLien.Ouvert(appli))
            } else {
                refuser(rappel, ResultatLien.AUCUNE_APPLI)
            }
        }
    }

    /** Refus (ou attente) : journal du type, puis réponse s'il y a quelqu'un à qui répondre. */
    private fun refuser(rappel: ((ResultatLien) -> Unit)?, raison: String) {
        p.journal("lien refusé $raison")
        rappel?.invoke(ResultatLien.Refuse(raison))
    }
}
