package fr.projo.launcher.nba

/**
 * Rythme du classement (docs/design-nba.md, « Données »), sans Android ni réseau : rien tant que
 * le design NBA n'est pas affiché ; affiché, chargement tout de suite si le dernier classement a
 * plus de 10 min (ou s'il n'y en a pas), puis toutes les 10 min. Après un échec, nouvel essai
 * dans 1 min, puis 2, 4, 8… sans dépasser 10 min. Heures en ms (epoch) ; une heure qui recule
 * (horloge remise à l'heure) compte comme un classement périmé.
 */
internal class PlanClassement(
    private val periodeMs: Long = PERIODE_MS,
    private val repriseMs: Long = REPRISE_MS,
) {
    companion object {
        const val PERIODE_MS = 10 * 60_000L
        const val REPRISE_MS = 60_000L
    }

    /** Ce qu'il faut faire à un instant donné. */
    sealed class Etape {
        /** Rien : le design NBA n'est pas affiché. */
        data object Arret : Etape()
        data object Charger : Etape()
        data class Attendre(val ms: Long) : Etape()
    }

    @get:Synchronized @set:Synchronized
    var actif = false

    private var echecs = 0
    private var dernierEssai: Long? = null

    /** Étape à l'instant [t], [misAJour] : heure du dernier classement connu (null : aucun). */
    @Synchronized
    fun etape(t: Long, misAJour: Long?): Etape {
        if (!actif) return Etape.Arret
        val parAge = if (misAJour == null || misAJour > t) t else misAJour + periodeMs
        val essai = dernierEssai
        val parEchec = if (echecs > 0 && essai != null && essai <= t) essai + attenteApresEchecs(echecs) else Long.MIN_VALUE
        val echeance = maxOf(parAge, parEchec)
        return if (echeance <= t) Etape.Charger else Etape.Attendre(echeance - t)
    }

    @Synchronized
    fun reussite(t: Long) {
        echecs = 0
        dernierEssai = t
    }

    @Synchronized
    fun echec(t: Long) {
        echecs++
        dernierEssai = t
    }

    /** Attente avant un nouvel essai après [n] échecs de suite (n ≥ 1). */
    fun attenteApresEchecs(n: Int): Long = (repriseMs shl (n - 1).coerceIn(0, 20)).coerceAtMost(periodeMs)
}

/** Mémoire à durée de vie : une valeur plus vieille que [dureeMs] (ou datée du futur) est oubliée. */
internal class CacheTemporise<K : Any, V : Any>(private val dureeMs: Long, private val horloge: () -> Long) {

    private class Entree<V>(val t: Long, val valeur: V)

    private val entrees = HashMap<K, Entree<V>>()

    @Synchronized
    fun lire(cle: K): V? {
        val e = entrees[cle] ?: return null
        if (frais(e, horloge())) return e.valeur
        entrees.remove(cle)
        return null
    }

    @Synchronized
    fun mettre(cle: K, valeur: V) {
        val t = horloge()
        entrees.values.removeAll { !frais(it, t) }
        entrees[cle] = Entree(t, valeur)
    }

    @Synchronized
    fun vider() = entrees.clear()

    private fun frais(e: Entree<V>, t: Long): Boolean = t - e.t in 0 until dureeMs
}
