package fr.projo.launcher.liens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import fr.projo.launcher.data.PauseMusique

/**
 * Ouverture d'un lien sur la TV (Android), selon les règles de « Ouverture sur la TV » :
 * applis du genre du lien (Liens.candidats), puis les applis des pages web (réglage
 * `liens_pages`). Chaque appli reçoit `ACTION_VIEW` avec l'URL (et le type vidéo générique pour une
 * vidéo directe), limité à son paquet ; une appli qui ne déclare pas cette ouverture est sautée.
 * Jamais iBiTV. Rien n'est écrit dans le journal (ni l'URL, ni l'intention).
 */
object OuvertureLiens {

    /** Une appli qui sait ouvrir le lien : son nom affiché et l'intention à lui envoyer. */
    class Candidat(val paquet: String, val nom: String, val intention: Intent)

    /** Applis qui ouvrent une page https (paquet → nom), sans iBiTV. */
    fun navigateurs(context: Context): Map<String, String> {
        val pm = context.packageManager
        val sonde = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
        // MATCH_ALL : tous les navigateurs, pas seulement celui par défaut
        val drapeaux = PackageManager.MATCH_DEFAULT_ONLY or (if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0)
        val paquets = requete(pm, sonde, drapeaux).filter { it != context.packageName }.distinct()
        return paquets.associateWith { nom(pm, it) }
    }

    /** Applis des pages web dans l'ordre d'essai (choix [pages], puis l'ordre « auto »). */
    fun ordrePages(context: Context, pages: String): List<Pair<String, String>> {
        val n = navigateurs(context)
        return Liens.ordrePages(n, pages, context.packageName).map { it to (n[it] ?: it) }
    }

    /** Applis qui savent ouvrir [lien], dans l'ordre d'essai ; vide : aucune. */
    fun candidats(context: Context, lien: LienTv, pages: String): List<Candidat> {
        val pm = context.packageManager
        val soi = context.packageName
        val uri = Uri.parse(lien.url)
        val res = ArrayList<Candidat>()
        val vus = HashSet<String>()
        fun essayer(paquet: String, type: String?) {
            if (paquet == soi || paquet in vus) return
            val i = Intent(Intent.ACTION_VIEW)
            if (type != null) i.setDataAndType(uri, type) else i.data = uri
            i.setPackage(paquet).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (requete(pm, i, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                vus += paquet
                res += Candidat(paquet, nom(pm, paquet), i)
            }
        }
        val genre = Liens.genre(lien)
        for (p in Liens.candidats(genre)) essayer(p, if (genre == GenreLien.VIDEO) TYPE_VIDEO else null)
        if (genre == GenreLien.VIDEO) {
            // Puis toute appli qui ouvre `video/*` (par ordre alphabétique)
            val lecteurs = requete(pm, Intent(Intent.ACTION_VIEW).setDataAndType(uri, TYPE_VIDEO), PackageManager.MATCH_DEFAULT_ONLY)
                .distinct().sortedBy { nom(pm, it).lowercase() }
            for (p in lecteurs) essayer(p, TYPE_VIDEO)
        }
        // Page web : tout le reste, et repli quand aucune appli du genre ne prend le lien
        for (p in Liens.ordrePages(navigateurs(context), pages, soi)) essayer(p, null)
        return res
    }

    /**
     * Ouvre le lien dans le premier [candidats] qui accepte (la musique en arrière-plan se met
     * d'abord en pause, comme pour une appli vidéo) ; nom de l'appli ouverte, null si aucune.
     */
    fun lancer(context: Context, candidats: List<Candidat>): String? {
        if (candidats.isEmpty()) return null
        PauseMusique.pauser(context)
        for (c in candidats) {
            val ok = try {
                context.startActivity(c.intention)
                true
            } catch (e: Exception) {
                false
            }
            if (ok) return c.nom
        }
        return null
    }

    private const val TYPE_VIDEO = "video/*"

    @Suppress("DEPRECATION")
    private fun requete(pm: PackageManager, i: Intent, drapeaux: Int): List<String> = try {
        pm.queryIntentActivities(i, drapeaux).mapNotNull { it.activityInfo?.packageName }
    } catch (e: Exception) {
        emptyList()
    }

    fun nom(pm: PackageManager, paquet: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString()
    } catch (e: Exception) {
        paquet
    }
}
