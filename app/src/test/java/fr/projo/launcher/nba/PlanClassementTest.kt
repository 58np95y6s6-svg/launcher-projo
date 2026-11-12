package fr.projo.launcher.nba

import fr.projo.launcher.nba.PlanClassement.Etape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rythme du classement et caches à durée de vie, sans Android ni réseau (horloge imposée). */
class PlanClassementTest {

    private val minute = 60_000L
    private val plan = PlanClassement()

    @Test
    fun rienTantQueLeDesignNestPasAffiche() {
        assertEquals(Etape.Arret, plan.etape(0L, null))
        assertEquals(Etape.Arret, plan.etape(100 * minute, 0L))
    }

    @Test
    fun toutDeSuiteSiPerimeOuAbsent() {
        plan.actif = true
        assertEquals(Etape.Charger, plan.etape(5 * minute, null))
        assertEquals(Etape.Charger, plan.etape(20 * minute, 10 * minute))
        assertEquals(Etape.Charger, plan.etape(20 * minute, 5 * minute))
        assertEquals(Etape.Attendre(minute), plan.etape(20 * minute, 11 * minute))
        // Classement daté du futur (horloge remise à l'heure) : périmé
        assertEquals(Etape.Charger, plan.etape(20 * minute, 30 * minute))
    }

    @Test
    fun toutesLesDixMinutes() {
        plan.actif = true
        var t = 0L
        var maj: Long? = null
        val charges = ArrayList<Long>()
        // Une heure affichée, chargements tous réussis
        while (t <= 60 * minute) {
            when (val e = plan.etape(t, maj)) {
                Etape.Charger -> {
                    charges += t / minute
                    maj = t
                    plan.reussite(t)
                }
                is Etape.Attendre -> t += e.ms
                Etape.Arret -> throw AssertionError("arrêt inattendu")
            }
        }
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L, 50L, 60L), charges)
    }

    @Test
    fun reprisesApresEchecs() {
        assertEquals(minute, plan.attenteApresEchecs(1))
        assertEquals(2 * minute, plan.attenteApresEchecs(2))
        assertEquals(8 * minute, plan.attenteApresEchecs(4))
        assertEquals(10 * minute, plan.attenteApresEchecs(5))
        assertEquals(10 * minute, plan.attenteApresEchecs(500))

        plan.actif = true
        plan.echec(0L)
        assertEquals(Etape.Attendre(minute), plan.etape(0L, null))
        // Masqué puis réaffiché aussitôt : pas de nouvel essai avant la minute
        plan.actif = false
        plan.actif = true
        assertEquals(Etape.Attendre(30_000L), plan.etape(30_000L, null))
        assertEquals(Etape.Charger, plan.etape(minute, null))
        plan.reussite(minute)
        assertEquals(Etape.Attendre(10 * minute), plan.etape(minute, minute))
    }

    @Test
    fun cacheTemporise() {
        var t = 1_000L
        val cache = CacheTemporise<String, String>(30 * minute) { t }
        assertNull(cache.lire("OKC"))
        cache.mettre("OKC", "calendrier")
        t += 30 * minute - 1
        assertEquals("calendrier", cache.lire("OKC"))
        t += 1
        assertNull(cache.lire("OKC"))
        // Daté du futur : oublié
        cache.mettre("BOS", "calendrier")
        t -= minute
        assertNull(cache.lire("BOS"))
        cache.mettre("LAL", "calendrier")
        cache.vider()
        assertNull(cache.lire("LAL"))
    }
}
