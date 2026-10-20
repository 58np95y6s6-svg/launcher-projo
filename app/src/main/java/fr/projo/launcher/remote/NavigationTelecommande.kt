package fr.projo.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

/**
 * Navigation hors des champs texte, par un service d'accessibilité.
 *
 * Le rôle « navigation » est indépendant du service qui le porte : tout service
 * d'accessibilité d'iBiProjo peut s'y attacher ([attacher] / [detacher]).
 * - Projecteur (et toute branche sans Fire TV) : [NavigationTelecommande] ci-dessous.
 * - Fire TV : le service d'ouverture automatique (system/RedirectionAccueil) hérite de
 *   [NavigationTelecommande] et ajoute son rôle « redirection » : un seul service à activer.
 *   Voir docs/telecommande.md, « Fusion avec la branche Fire TV ».
 */
object HoteNavigation {

    @Volatile
    private var service: WeakReference<AccessibilityService>? = null

    fun attacher(s: AccessibilityService) {
        service = WeakReference(s)
    }

    fun detacher(s: AccessibilityService) {
        if (service?.get() === s) service = null
    }

    val actif: Boolean get() = service?.get() != null

    /** Exécute une touche du téléphone ; false si rien n'a pu être fait (repli silencieux). */
    fun executer(k: String): Boolean {
        val s = service?.get() ?: return false
        return try {
            when (k) {
                "back" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "up" -> deplacer(s, Direction.HAUT)
                "down" -> deplacer(s, Direction.BAS)
                "left" -> deplacer(s, Direction.GAUCHE)
                "right" -> deplacer(s, Direction.DROITE)
                "ok" -> cliquer(s)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Service d'accessibilité d'iBiProjo activé dans les réglages (quel qu'il soit). */
    fun estActive(context: Context): Boolean {
        val liste = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return liste.split(':').any { it.startsWith(context.packageName + "/") }
    }

    private fun boite(n: AccessibilityNodeInfo): Boite {
        val r = Rect()
        n.getBoundsInScreen(r)
        return Boite(r.left, r.top, r.right, r.bottom)
    }

    /** Éléments focusables visibles de la fenêtre active (parcours limité). */
    private fun focusables(racine: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val resultat = mutableListOf<AccessibilityNodeInfo>()
        val pile = ArrayDeque<AccessibilityNodeInfo>()
        pile.addLast(racine)
        var vus = 0
        while (pile.isNotEmpty() && vus < 600) {
            val n = pile.removeLast()
            vus++
            if (n.isFocusable && n.isVisibleToUser && n.isEnabled) resultat += n
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { pile.addLast(it) }
        }
        return resultat
    }

    private fun deplacer(s: AccessibilityService, direction: Direction): Boolean {
        val racine = s.rootInActiveWindow ?: return false
        val actuel = racine.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (actuel == null) {
            // Rien n'a le focus : premier élément de l'écran
            val candidats = focusables(racine)
            val i = GeometrieFocus.premier(candidats.map { boite(it) })
            return i >= 0 && candidats[i].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        // 1. Recherche de l'appli elle-même (vues classiques : listes, grilles…)
        val sens = when (direction) {
            Direction.HAUT -> View.FOCUS_UP
            Direction.BAS -> View.FOCUS_DOWN
            Direction.GAUCHE -> View.FOCUS_LEFT
            Direction.DROITE -> View.FOCUS_RIGHT
        }
        val suivant = try { actuel.focusSearch(sens) } catch (e: Exception) { null }
        if (suivant != null && suivant != actuel && suivant.isVisibleToUser &&
            suivant.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        ) return true
        // 2. Repli géométrique (Compose, WebView…)
        val depuis = boite(actuel)
        val candidats = focusables(racine).filter { it != actuel }
        val i = GeometrieFocus.voisin(depuis, candidats.map { boite(it) }, direction)
        return i >= 0 && candidats[i].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun cliquer(s: AccessibilityService): Boolean {
        val racine = s.rootInActiveWindow ?: return false
        var n: AccessibilityNodeInfo? = racine.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        // Élément focalisé non cliquable : son parent cliquable le plus proche
        var etapes = 0
        while (n != null && etapes < 5) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            etapes++
        }
        return false
    }
}

/**
 * Service d'accessibilité « iBiProjo · télécommande » : navigation hors des champs texte.
 * Il ne réagit qu'aux commandes du téléphone ; il ne lit l'écran que pour trouver l'élément
 * voisin à sélectionner et n'en garde ni n'envoie rien.
 *
 * Classe ouverte : sur Fire TV, le service d'ouverture automatique en hérite (rôle
 * « redirection » en plus du rôle « navigation »).
 */
open class NavigationTelecommande : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        HoteNavigation.attacher(this)
        Telecommande.demarrer(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        HoteNavigation.detacher(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        HoteNavigation.detacher(this)
        super.onDestroy()
    }
}
