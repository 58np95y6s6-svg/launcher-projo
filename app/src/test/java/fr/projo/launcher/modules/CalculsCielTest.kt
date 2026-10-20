package fr.projo.launcher.modules

import fr.projo.launcher.modules.air.AirReseau
import fr.projo.launcher.modules.air.NiveauAir
import fr.projo.launcher.modules.air.TypePollen
import fr.projo.launcher.modules.horloges.ModuleHorloges
import fr.projo.launcher.modules.horloges.ecartTexte
import fr.projo.launcher.modules.meteo.Lieu
import fr.projo.launcher.modules.soleil.Astronomie
import fr.projo.launcher.modules.soleil.PhaseLune
import fr.projo.launcher.modules.soleil.Polaire
import fr.projo.launcher.modules.soleil.duree
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Calculs des modules Soleil et lune, Qualité de l'air et Horloges du monde, comparés à des
 * valeurs de référence (éphémérides publiées, éclipses de 2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalculsCielTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val paris: TimeZone = TimeZone.getTimeZone("Europe/Paris")

    private fun instant(a: Int, mois: Int, j: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(a, mois - 1, j, h, min) }.timeInMillis

    private fun proche(attendu: Long, obtenu: Long?, toleranceMin: Int, quoi: String) {
        assertTrue("$quoi : rien obtenu", obtenu != null)
        val ecart = abs(obtenu!! - attendu) / 60_000.0
        assertTrue("$quoi : écart de ${"%.1f".format(ecart)} min", ecart <= toleranceMin)
    }

    @Test
    fun leverEtCoucherAParis() {
        // Solstice d'été : lever 5 h 47, coucher 21 h 58 (heure d'été, UTC + 2)
        val ete = Astronomie.journee(instant(2026, 6, 21, 12), 48.8566, 2.3522, paris)
        proche(instant(2026, 6, 21, 3, 47), ete.lever, 3, "lever 21 juin")
        proche(instant(2026, 6, 21, 19, 58), ete.coucher, 3, "coucher 21 juin")
        assertTrue("durée du jour en juin", abs(ete.dureeJour - (16 * 60 + 11) * 60_000L) < 5 * 60_000L)
        // Solstice d'hiver : lever 8 h 41, coucher 16 h 56 (UTC + 1)
        val hiver = Astronomie.journee(instant(2026, 12, 21, 12), 48.8566, 2.3522, paris)
        proche(instant(2026, 12, 21, 7, 41), hiver.lever, 3, "lever 21 décembre")
        proche(instant(2026, 12, 21, 15, 56), hiver.coucher, 3, "coucher 21 décembre")
    }

    @Test
    fun jourEtNuitPolaires() {
        val nuit = Astronomie.journee(instant(2026, 12, 21, 12), 69.65, 18.96, utc)
        assertEquals(Polaire.NUIT, nuit.polaire)
        assertEquals(0L, nuit.dureeJour)
        val jour = Astronomie.journee(instant(2026, 6, 21, 12), 69.65, 18.96, utc)
        assertEquals(Polaire.JOUR, jour.polaire)
        assertNull(jour.lever)
        // Pôle : pas de plantage (latitude bornée)
        Astronomie.journee(instant(2026, 3, 20, 12), 90.0, 0.0, utc)
    }

    @Test
    fun hauteurDuSoleil() {
        // Paris, 21 juin vers 13 h 52 (midi solaire) : environ 64,6° ; minuit : sous l'horizon
        assertTrue(abs(Astronomie.hauteurSoleil(instant(2026, 6, 21, 11, 52), 48.8566, 2.3522) - 64.6) < 1.0)
        assertTrue(!Astronomie.faitJour(instant(2026, 6, 21, 23, 0), 48.8566, 2.3522))
    }

    @Test
    fun phasesDeLaLune() {
        // Éclipse totale de lune du 3 mars 2026 (pleine lune vers 11 h 38 UTC)
        val avant = Astronomie.lune(instant(2026, 2, 28), utc)
        assertEquals(PhaseLune.PLEINE, avant.prochaine)
        proche(instant(2026, 3, 3, 11, 38), avant.prochaineDate, 60, "pleine lune du 3 mars")
        assertTrue("croissante avant la pleine lune", avant.croissante)
        val pleine = Astronomie.lune(instant(2026, 3, 3, 11, 38), utc)
        assertEquals(PhaseLune.PLEINE, pleine.phase)
        assertTrue("éclairée à ${pleine.fraction}", pleine.fraction > 0.99)
        // Éclipse totale de soleil du 12 août 2026 (nouvelle lune vers 17 h 37 UTC)
        val aout = Astronomie.lune(instant(2026, 8, 10), utc)
        assertEquals(PhaseLune.NOUVELLE, aout.prochaine)
        proche(instant(2026, 8, 12, 17, 37), aout.prochaineDate, 60, "nouvelle lune du 12 août")
        assertEquals(PhaseLune.DERNIER_CROISSANT, aout.phase)
        // Entre premier quartier et pleine lune : gibbeuse croissante
        val gibbeuse = Astronomie.lune(instant(2026, 3, 1), utc)
        assertEquals(PhaseLune.GIBBEUSE_CROISSANTE, gibbeuse.phase)
    }

    @Test
    fun textes() {
        assertEquals("10 h 44", duree((10 * 60 + 44) * 60_000L))
        assertEquals("45 min", duree(45 * 60_000L))
        assertEquals("16 h", duree(16 * 3_600_000L))
        val defaut = TimeZone.getDefault()
        try {
            TimeZone.setDefault(paris)
            val hiver = instant(2026, 1, 15, 12)
            assertEquals("+4 h 30", ecartTexte(TimeZone.getTimeZone("Asia/Kolkata"), hiver))
            assertEquals("−6 h", ecartTexte(TimeZone.getTimeZone("America/New_York"), hiver))
            assertEquals("Même heure", ecartTexte(TimeZone.getTimeZone("Europe/Paris"), hiver))
        } finally {
            TimeZone.setDefault(defaut)
        }
    }

    @Test
    fun reglagesDesHorloges() {
        fun r(v: String?) = ReglagesModule("horloges", if (v == null) emptyMap() else mapOf("horloges.villes" to v)) { _, _ -> }
        assertEquals(ModuleHorloges.PAR_DEFAUT, ModuleHorloges.villes(r(null)).map { it.id })
        // Une seule ville connue : retour aux villes par défaut (2 au moins)
        assertEquals(ModuleHorloges.PAR_DEFAUT, ModuleHorloges.villes(r("tokyo,inconnue")).map { it.id })
        assertEquals(listOf("tokyo", "rio", "sydney", "paris"), ModuleHorloges.villes(r("tokyo,rio,sydney,paris,londres")).map { it.id })
    }

    @Test
    fun lectureQualiteDeLAir() {
        val lyon = Lieu("Lyon", 45.76, 4.84)
        val json = JSONObject(
            """
            {"current":{"time":"2026-10-19T14:00","european_aqi":47,"pm2_5":11.2,"european_aqi_pm2_5":22,
              "pm10":16,"european_aqi_pm10":8,"nitrogen_dioxide":18.4,"european_aqi_nitrogen_dioxide":9,
              "ozone":96,"european_aqi_ozone":47,"sulphur_dioxide":null,"european_aqi_sulphur_dioxide":null},
             "hourly":{"time":["2026-10-19T00:00","2026-10-19T01:00"],
              "grass_pollen":[3.1,31.0],"birch_pollen":[0,0],"ragweed_pollen":[null,23.5],
              "olive_pollen":[null,null],"alder_pollen":[0.0,0.2],"mugwort_pollen":[0,0]}}
            """.trimIndent()
        )
        val a = AirReseau.lire(json, lyon)
        assertEquals(47, a.indice)
        assertEquals(NiveauAir.DEGRADE, a.niveau)
        assertEquals("O₃", a.polluants.first().nom)
        assertEquals(4, a.polluants.size)
        assertEquals(listOf(TypePollen.AMBROISIE, TypePollen.GRAMINEES), a.pollens!!.map { it.type })

        // Hors saison : valeurs nulles ou à zéro
        val horsSaison = AirReseau.lire(JSONObject("""{"current":{"european_aqi":12},"hourly":{"grass_pollen":[0,0]}}"""), lyon)
        assertEquals(NiveauAir.BON, horsSaison.niveau)
        assertTrue(horsSaison.pollens!!.isEmpty())
        // Hors d'Europe : aucune donnée de pollen
        val tokyo = AirReseau.lire(JSONObject("""{"current":{"european_aqi":null,"european_aqi_pm2_5":30},"hourly":{"grass_pollen":[null,null]}}"""), Lieu("Tokyo", 35.68, 139.69))
        assertNull(tokyo.pollens)
        assertEquals(30, tokyo.indice)
        assertTrue(!tokyo.europe)
        // Réponse vide : rien ne plante
        val vide = AirReseau.lire(JSONObject("{}"), lyon)
        assertNull(vide.indice)
        assertNull(vide.pollens)
    }
}
