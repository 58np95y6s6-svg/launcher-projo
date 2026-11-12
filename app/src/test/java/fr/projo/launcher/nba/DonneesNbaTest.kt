package fr.projo.launcher.nba

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * L'objet utilisé par l'affichage, branché sur un faux ESPN : démarrage (cache disque, sans
 * requête), suivi (rien sans affichage, chargement dès l'affichage), fiche, logos, codes ESPN.
 */
@RunWith(RobolectricTestRunner::class)
class DonneesNbaTest {

    private val faux = FauxEspn()

    @Before
    fun avant() {
        DonneesNba.oublier()
        DonneesNba.chargeur = faux
    }

    @After
    fun apres() {
        DonneesNba.oublier()
    }

    /** Attend [condition] (5 s au plus) : la boucle tourne sur ses propres fils. */
    private fun attendre(condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > limite) throw AssertionError("délai dépassé")
            Thread.sleep(10)
        }
    }

    @Test
    fun rienSansAffichage() {
        val app = RuntimeEnvironment.getApplication()
        File(app.noBackupFilesDir, "nba-carte").deleteRecursively()
        DonneesNba.demarrer(app)
        Thread.sleep(200)
        assertNull(DonneesNba.classement.value)
        assertTrue(faux.requetes.isEmpty())
    }

    @Test
    fun chargeDesLAffichagePuisSArrete() {
        val app = RuntimeEnvironment.getApplication()
        File(app.noBackupFilesDir, "nba-carte").deleteRecursively()
        DonneesNba.demarrer(app)
        DonneesNba.suivre(true)
        attendre { DonneesNba.classement.value != null }
        val c = DonneesNba.classement.value!!
        assertEquals(30, c.equipes.size)
        assertFalse(c.horsLigne)
        attendre { faux.nombre { it == RequeteEspn.Equipes } == 1 }
        DonneesNba.suivre(false)
        Thread.sleep(200)
        val requetes = faux.requetes.size
        Thread.sleep(200)
        assertEquals(requetes, faux.requetes.size)
        // Plus d'une requête au classement en dix minutes : non
        assertEquals(1, faux.nombre { it == RequeteEspn.Classement })

        // Redémarrage de l'appli : le classement revient du disque, sans requête
        DonneesNba.oublier()
        DonneesNba.chargeur = faux
        DonneesNba.demarrer(app)
        assertEquals(c, DonneesNba.classement.value)
        assertEquals(1, faux.nombre { it == RequeteEspn.Classement })
        // Affiché de nouveau : le classement a moins de 10 min, rien à charger
        DonneesNba.suivre(true)
        Thread.sleep(200)
        assertEquals(1, faux.nombre { it == RequeteEspn.Classement })
        DonneesNba.suivre(false)
    }

    @Test
    fun ficheEtLogos() {
        val f = runBlocking { DonneesNba.fiche("OKC") }
        assertNotNull(f)
        assertEquals(5, f!!.titulaires.size)
        assertNull(runBlocking { DonneesNba.fiche("XYZ") })
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/ny.png", DonneesNba.logo("NYK"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/wsh.png", DonneesNba.logo("WAS"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/bos.png", DonneesNba.logo("BOS"))
    }

    @Test
    fun codesEspn() {
        assertEquals(
            listOf("GS", "NY", "SA", "NO", "UTAH", "WSH", "OKC", "LAL"),
            listOf("GSW", "NYK", "SAS", "NOP", "UTA", "WAS", "OKC", "LAL").map { DonneesNba.codeEspn(it) },
        )
    }

    @Test
    fun classementImpose() {
        val c = ClassementNba(mapOf("OKC" to BilanNba("OKC", 8, 4, 7, 3, -2, "6-4")), 1L, horsLigne = true)
        DonneesNba.imposer(c)
        assertEquals(c, DonneesNba.classement.value)
    }
}
