package fr.projo.launcher.remote

import fr.projo.launcher.ambiance.CouleursNeon
import fr.projo.launcher.ambiance.Intensite
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.ui.Design
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Pilotage depuis le téléphone : applis et réglages (docs/telecommande.md, « Pilotage depuis le
 * téléphone »). Ici la partie sans Android, testée sur la JVM : ce que la TV met à disposition
 * (interface Pilotage), la description des réglages, la validation de `reglage`, l'ordre des
 * applis et le regroupement des renvois de `reglages`. Implémentation Android : PilotageLauncher.
 */

/** Une appli de la TV (`apps.liste`). [maj] : dernière mise à jour de l'appli (ms), version de son image. */
data class AppliTv(val id: String, val nom: String, val favori: Boolean, val maj: Long)

/** Image d'une appli (`app_image`) : [img] est une URL data: de 48 Ko au plus. */
data class ImageAppli(val id: String, val maj: Long, val forme: String, val img: String) {
    companion object {
        const val BANNIERE = "banniere"
        const val ICONE = "icone"
    }
}

/** Option d'un réglage `choix`. */
data class OptionReglage(val id: String, val nom: String, val detail: String? = null)

/** Bouton d'un réglage `boutons`. */
data class BoutonReglage(val id: String, val nom: String, val principal: Boolean = false)

/** Un réglage de la TV (`reglages.items`), selon son type. */
sealed class ReglageTv {
    abstract val cle: String
    abstract val groupe: String
    abstract val titre: String
    abstract val detail: String?

    /** false : affiché grisé, non modifiable. */
    abstract val actif: Boolean

    /** `type` du protocole. */
    val type: String
        get() = when (this) {
            is Curseur -> "curseur"
            is Interrupteur -> "interrupteur"
            is Choix -> "choix"
            is Boutons -> "boutons"
        }

    data class Curseur(
        override val cle: String,
        override val groupe: String,
        override val titre: String,
        val valeur: Int,
        val min: Int,
        val max: Int,
        override val detail: String? = null,
        override val actif: Boolean = true,
    ) : ReglageTv()

    data class Interrupteur(
        override val cle: String,
        override val groupe: String,
        override val titre: String,
        val valeur: Boolean,
        override val detail: String? = null,
        override val actif: Boolean = true,
    ) : ReglageTv()

    data class Choix(
        override val cle: String,
        override val groupe: String,
        override val titre: String,
        /** `id` de l'option choisie. */
        val valeur: String,
        val options: List<OptionReglage>,
        override val detail: String? = null,
        override val actif: Boolean = true,
    ) : ReglageTv()

    data class Boutons(
        override val cle: String,
        override val groupe: String,
        override val titre: String,
        val boutons: List<BoutonReglage>,
        /** 0..1 pendant une opération longue (téléchargement), sinon null. */
        val progression: Float? = null,
        override val detail: String? = null,
        override val actif: Boolean = true,
    ) : ReglageTv()
}

/**
 * Changement demandé par le téléphone, déjà validé contre la description : [valeur] est un Int
 * (curseur, déjà ramené dans min..max), un Boolean (interrupteur), l'`id` d'une option (choix)
 * ou l'`id` d'un bouton (boutons).
 */
data class Changement(val cle: String, val valeur: Any)

/**
 * Ce que la TV met à disposition du téléphone authentifié. Toutes les fonctions sont appelées
 * sur le fil de la télécommande, et chaque [rappel] y est exécuté aussi (plus tard, ou tout de
 * suite dans un banc de test).
 */
interface Pilotage {
    /** Applis qu'on peut ouvrir, dans l'ordre de l'accueil (voir [ListeApplis]). */
    fun applis(rappel: (List<AppliTv>) -> Unit)

    /**
     * Images de [applis] (déjà vérifiées), une à une et dans l'ordre ; une appli sans image
     * possible est sautée. S'arrête dès que [encoreVoulu] rend false (téléphone parti).
     */
    fun images(applis: List<AppliTv>, encoreVoulu: () -> Boolean, rappel: (ImageAppli) -> Unit)

    /** Ouvre l'appli [id] (déjà vérifiée) comme sa tuile de l'accueil ; [rappel] : ouverte ou non. */
    fun ouvrir(id: String, rappel: (Boolean) -> Unit)

    /** État complet des réglages, dans l'ordre d'affichage (voir [ReglagesTv.decrire]). */
    fun reglages(rappel: (List<ReglageTv>) -> Unit)

    /** Applique un changement validé ; [rappel] une fois appliqué (ou lancé, pour un bouton). */
    fun appliquer(changement: Changement, rappel: () -> Unit)
}

/** État de la mise à jour d'iBiTV, lu dans le ViewModel de l'accueil. */
data class EtatMaj(
    /** Version installée, telle qu'affichée sur la TV. */
    val versionInstallee: String,
    val verification: Boolean = false,
    /** Progression du téléchargement (0..1), null sinon. */
    val telechargement: Float? = null,
    val message: String? = null,
    /** Nom de la version disponible si elle est plus récente, null sinon. */
    val nouvelleVersion: String? = null,
    /** Une vérification a déjà abouti. */
    val verifiee: Boolean = false,
)

/** Valeurs courantes des réglages de la TV (lues sur le fil principal). */
data class EtatTv(
    val volume: Int,
    val volumeMax: Int,
    val muet: Boolean,
    val design: Design,
    val nouveautes: Boolean,
    val ambianceAuto: Boolean,
    val intensite: Intensite,
    val couleurs: CouleursNeon,
    val decalageMs: Int,
    /** Retards proposés (ModeAmbiance.decalagesPossibles) et celui conseillé. */
    val decalages: List<Int>,
    val decalageConseille: Int,
    /** null : écran d'accueil pas encore créé (pas de ViewModel) → `maj` grisé. */
    val maj: EtatMaj?,
)

/** Réglages de la TV (version 1) : description envoyée au téléphone, validation de `reglage`. */
object ReglagesTv {
    const val VOLUME = "volume"
    const val MUET = "muet"
    const val DESIGN = "design"
    const val NOUVEAUTES = "nouveautes"
    const val AMBIANCE_AUTO = "ambiance_auto"
    const val AMBIANCE_INTENSITE = "ambiance_intensite"
    const val AMBIANCE_COULEURS = "ambiance_couleurs"
    const val AMBIANCE_DECALAGE = "ambiance_decalage"
    const val MAJ = "maj"
    const val SYSTEME = "systeme"

    const val BOUTON_VERIFIER = "verifier"
    const val BOUTON_INSTALLER = "installer"
    const val BOUTON_WIFI = "wifi"
    const val BOUTON_BLUETOOTH = "bluetooth"
    const val BOUTON_REGLAGES = "reglages"

    const val GROUPE_SON = "Son"
    const val GROUPE_AFFICHAGE = "Affichage"
    const val GROUPE_AMBIANCE = "Mode Ambiance"
    const val GROUPE_MAJ = "Mises à jour"
    const val GROUPE_SYSTEME = "Ouvrir sur la TV"

    /** Tous les réglages, dans l'ordre d'affichage (même ordre que la table de la spécification). */
    fun decrire(e: EtatTv): List<ReglageTv> {
        val max = e.volumeMax.coerceAtLeast(1)
        val decalages = (e.decalages + e.decalageMs).distinct().sorted()
        return listOf(
            ReglageTv.Curseur(
                VOLUME, GROUPE_SON, "Volume",
                valeur = e.volume.coerceIn(0, max), min = 0, max = max,
                detail = if (e.muet) "Son coupé" else null,
            ),
            ReglageTv.Interrupteur(MUET, GROUPE_SON, "Couper le son", e.muet),
            ReglageTv.Choix(
                DESIGN, GROUPE_AFFICHAGE, "Design de l'accueil",
                valeur = e.design.id,
                options = Design.entries.map { OptionReglage(it.id, it.nom, it.resume) },
            ),
            ReglageTv.Interrupteur(
                NOUVEAUTES, GROUPE_AFFICHAGE, "Affiches des nouveautés", e.nouveautes,
                detail = "Photos des films et séries récents en fond de l'accueil",
            ),
            ReglageTv.Interrupteur(
                AMBIANCE_AUTO, GROUPE_AMBIANCE, "Automatique avec les apps de musique", e.ambianceAuto,
                detail = "Spotify, Deezer, radios… Jamais avec les apps vidéo",
            ),
            ReglageTv.Choix(
                AMBIANCE_INTENSITE, GROUPE_AMBIANCE, "Intensité",
                valeur = e.intensite.id,
                options = Intensite.entries.map { OptionReglage(it.id, it.nom, detailIntensite(it)) },
            ),
            ReglageTv.Choix(
                AMBIANCE_COULEURS, GROUPE_AMBIANCE, "Couleurs du néon",
                valeur = e.couleurs.id,
                options = CouleursNeon.entries.map { OptionReglage(it.id, it.nom, detailCouleurs(it)) },
            ),
            ReglageTv.Choix(
                AMBIANCE_DECALAGE, GROUPE_AMBIANCE, "Synchro avec le son",
                valeur = e.decalageMs.toString(),
                options = decalages.map { OptionReglage(it.toString(), nomDecalage(it, e.decalageConseille), detailDecalage(it, e.decalageConseille)) },
                detail = "Retard du néon sur le son joué",
            ),
            reglageMaj(e.maj),
            ReglageTv.Boutons(
                SYSTEME, GROUPE_SYSTEME, "Pages système",
                boutons = listOf(
                    BoutonReglage(BOUTON_WIFI, "Wi-Fi"),
                    BoutonReglage(BOUTON_BLUETOOTH, "Bluetooth"),
                    BoutonReglage(BOUTON_REGLAGES, "Paramètres"),
                ),
                detail = "S'ouvre sur l'écran de la TV",
            ),
        )
    }

    /** `maj` : version installée et état ; `installer` seulement si une version plus récente existe. */
    private fun reglageMaj(m: EtatMaj?): ReglageTv.Boutons {
        if (m == null) {
            // Pas d'écran d'accueil créé : rien à vérifier ni installer d'ici
            return ReglageTv.Boutons(
                MAJ, GROUPE_MAJ, "iBiTV",
                boutons = listOf(BoutonReglage(BOUTON_VERIFIER, "Rechercher")),
                detail = "Ouvre iBiTV sur la TV",
                actif = false,
            )
        }
        val t = m.telechargement
        val nouvelle = m.nouvelleVersion
        val etat = when {
            t != null -> "téléchargement… ${(t.coerceIn(0f, 1f) * 100).roundToInt()} %"
            m.verification -> "vérification…"
            m.message != null -> m.message
            nouvelle != null -> "nouvelle version $nouvelle"
            m.verifiee -> "à jour"
            else -> "pas encore vérifiée"
        }
        val boutons = buildList {
            add(BoutonReglage(BOUTON_VERIFIER, "Rechercher", principal = nouvelle == null))
            if (nouvelle != null) add(BoutonReglage(BOUTON_INSTALLER, "Installer $nouvelle", principal = true))
        }
        return ReglageTv.Boutons(
            MAJ, GROUPE_MAJ, "iBiTV",
            boutons = boutons,
            progression = t?.coerceIn(0f, 1f),
            detail = "Version ${m.versionInstallee} · $etat",
        )
    }

    // Libellés repris du dialogue Mode Ambiance de la TV (ui/AmbianceDialogs.kt)

    private fun detailIntensite(i: Intensite) = when (i) {
        Intensite.DOUCE -> "Pour la nuit : lumière et sursauts réduits"
        Intensite.NORMALE -> "Pour le salon"
        Intensite.FORTE -> "Lumière et sursauts au maximum"
    }

    private fun detailCouleurs(c: CouleursNeon) = when (c) {
        CouleursNeon.POCHETTE -> "Couleur de la jaquette du morceau (sinon de l'app)"
        CouleursNeon.ARC_EN_CIEL -> "Basses rouges en bas, médiums sur les côtés, aigus bleus en haut"
    }

    /** « Aucun retard », « Retard de 0,1 s (conseillé) »… comme sur la TV. */
    fun nomDecalage(ms: Int, conseille: Int): String {
        if (ms == 0) return "Aucun retard"
        // 100 → « 0,1 », 150 → « 0,15 » (valeur ancienne hors de la liste)
        val secondes = (ms / 1_000.0).toString().replace('.', ',')
        return "Retard de $secondes s" + (if (ms == conseille) " (conseillé)" else "")
    }

    private fun detailDecalage(ms: Int, conseille: Int) = when (ms) {
        0 -> "Le néon réagit dès que le son est joué"
        conseille -> "Compense le retard habituel de la sortie HDMI"
        else -> "Si le néon s'allume encore avant le coup entendu"
    }

    /**
     * `reglage {cle, valeur | bouton}` validé contre [items] (l'état actuel) ; null si la clé, le
     * type ou la valeur est inconnu, ou si le réglage est grisé. Curseur : nombre arrondi puis
     * ramené dans min..max ; interrupteur : booléen ; choix : `id` d'une option (un entier est
     * accepté pour un `id` numérique) ; boutons : `id` d'un bouton présent.
     */
    fun valider(items: List<ReglageTv>, cle: Any?, valeur: Any?, bouton: Any?): Changement? {
        if (cle !is String) return null
        val r = items.firstOrNull { it.cle == cle } ?: return null
        if (!r.actif) return null
        return when (r) {
            is ReglageTv.Curseur -> {
                val v = entier(valeur)
                if (v == null || r.max < r.min) null else Changement(cle, v.coerceIn(r.min.toLong(), r.max.toLong()).toInt())
            }
            is ReglageTv.Interrupteur -> if (valeur is Boolean) Changement(cle, valeur) else null
            is ReglageTv.Choix -> {
                val id = idOption(valeur)
                if (id != null && r.options.any { it.id == id }) Changement(cle, id) else null
            }
            is ReglageTv.Boutons -> {
                if (bouton is String && r.boutons.any { it.id == bouton }) Changement(cle, bouton) else null
            }
        }
    }

    private fun entier(v: Any?): Long? = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Number -> v.toDouble().let { d -> if (d.isNaN() || d.isInfinite()) null else d.roundToLong() }
        else -> null
    }

    private fun idOption(v: Any?): String? = when (v) {
        is String -> v
        is Int, is Long -> v.toString()
        else -> null
    }
}

/** Liste des applis envoyée au téléphone. */
object ListeApplis {
    /**
     * Les favoris dans l'ordre de l'accueil, puis les autres par ordre alphabétique ; ni les tuiles
     * propres à iBiTV (« Source HDMI »), ni iBiTV ([paquetIbitv]), ni doublon. [favoris] et
     * [toutes] sont déjà sans les applis masquées (HomeUi.favorites et HomeUi.allApps).
     */
    fun construire(favoris: List<AppInfo>, toutes: List<AppInfo>, paquetIbitv: String, maj: (String) -> Long): List<AppliTv> {
        val vus = HashSet<String>()
        val res = ArrayList<AppliTv>()
        fun ajouter(a: AppInfo, favori: Boolean) {
            if (a.isSpecial || a.packageName == paquetIbitv || !vus.add(a.packageName)) return
            res += AppliTv(a.packageName, a.label, favori, maj(a.packageName))
        }
        favoris.forEach { ajouter(it, favori = true) }
        toutes.sortedBy { it.label.lowercase() }.forEach { ajouter(it, favori = false) }
        return res
    }
}

/**
 * Regroupe des signaux rapprochés : [action] au plus une fois toutes les [intervalleMs]. Le
 * premier signal après un calme part tout de suite (au prochain tour du fil) ; ceux qui arrivent
 * pendant l'attente n'en font qu'un, à la fin de l'intervalle. À utiliser sur un seul fil :
 * [planifier] (délai en ms, tâche) exécute la tâche plus tard sur ce même fil.
 */
class Regroupement(
    private val intervalleMs: Long,
    private val horloge: () -> Long,
    private val planifier: (Long, () -> Unit) -> Unit,
    private val action: () -> Unit,
) {
    private var dernier: Long? = null
    private var prevu = false

    fun signaler() {
        if (prevu) return
        prevu = true
        val d = dernier
        val attente = if (d == null) 0L else (d + intervalleMs - horloge()).coerceAtLeast(0L)
        planifier(attente) {
            prevu = false
            dernier = horloge()
            action()
        }
    }
}
