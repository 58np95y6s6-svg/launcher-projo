package fr.projo.launcher.boxscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Statut en français, horloges et minutes des deux sources, table des 30 équipes (couleurs lisibles). */
class StatutsEquipesTest {

    private fun statut(periode: Int, horloge: Double?, fin: Boolean = false, etat: EtatMatch = EtatMatch.EN_COURS) =
        Statuts.texte(etat, periode, horloge, fin)

    @Test
    fun statutsDeLaSpecification() {
        assertEquals("4e QT · 2:31", statut(4, 151.0))
        assertEquals("1er QT · 0:45.3", statut(1, 45.3))
        assertEquals("Fin du 3e QT", statut(3, null, fin = true))
        assertEquals("Fin du 1er QT", statut(1, null, fin = true))
        assertEquals("Mi-temps", statut(2, null, fin = true))
        assertEquals("Prol. · 1:12", statut(5, 72.0))
        assertEquals("2e prol. · 3:05", statut(6, 185.0))
        assertEquals("Fin de la prol.", statut(5, null, fin = true))
        assertEquals("Fin de la 2e prol.", statut(6, null, fin = true))
        assertEquals("Terminé", statut(4, 0.0, etat = EtatMatch.TERMINE))
        assertEquals("2e QT", statut(2, null))
        assertEquals("1er QT · 12:00", statut(0, 720.0))
    }

    @Test
    fun horloges() {
        assertEquals("2:31", Statuts.horloge(151.0))
        assertEquals("12:00", Statuts.horloge(720.0))
        assertEquals("1:00", Statuts.horloge(60.0))
        assertEquals("0:59.9", Statuts.horloge(59.99))
        assertEquals("0:45.3", Statuts.horloge(45.36))
        assertEquals("0:00.0", Statuts.horloge(0.0))
        assertEquals("0:00.0", Statuts.horloge(-1.0))
        assertEquals(151.0, Statuts.lireHorloge("PT02M31.00S")!!, 1e-9)
        assertEquals(45.3, Statuts.lireHorloge("PT00M45.30S")!!, 1e-9)
        assertEquals(151.0, Statuts.lireHorloge("2:31")!!, 1e-9)
        assertEquals(45.3, Statuts.lireHorloge("45.3")!!, 1e-9)
        assertEquals(0.0, Statuts.lireHorloge("0.0")!!, 1e-9)
        assertNull(Statuts.lireHorloge(""))
        assertNull(Statuts.lireHorloge("PT"))
        assertNull(Statuts.lireHorloge(null))
        assertNull(Statuts.lireHorloge("bientôt"))
    }

    @Test
    fun minutesJouees() {
        assertEquals(38, Statuts.minutes("PT38M12.00S"))
        assertEquals(0, Statuts.minutes("PT00M41.00S"))
        assertEquals(38, Statuts.minutes("38:12"))
        assertEquals(38, Statuts.minutes("38"))
        assertEquals(0, Statuts.minutes(""))
        assertEquals(0, Statuts.minutes(null))
        assertEquals(0, Statuts.minutes("--"))
    }

    // ------------------------------------------------------------------ équipes

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#").toLong(16)
        fun canal(c: Long): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal(v shr 16 and 0xFF) + 0.7152 * canal(v shr 8 and 0xFF) + 0.0722 * canal(v and 0xFF)
    }

    private fun contraste(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun trenteEquipesAuxCouleursLisibles() {
        val e = EquipesNba.toutes
        assertEquals(30, e.size)
        assertEquals(30, e.map { it.code }.toSet().size)
        val format = Regex("^#[0-9a-f]{6}$")
        for (q in e) {
            assertEquals(3, q.code.length)
            assertTrue(q.nom.isNotBlank())
            for (c in listOf(q.couleur, q.texte, q.accent)) assertTrue("${q.code} : $c", format.matches(c))
            // Chiffres colorés sur le verre sombre : 4,5:1 au moins
            assertTrue("${q.code} : accent ${contraste(q.accent, "#0c0e14")}", contraste(q.accent, "#0c0e14") >= 4.5)
            // Lettres du badge (gras, grandes) : 3:1 au moins
            assertTrue("${q.code} : badge ${contraste(q.texte, q.couleur)}", contraste(q.texte, q.couleur) >= 3.0)
        }
        assertEquals("Lakers", EquipesNba.de("LAL").nom)
        assertEquals("#6c3fb5", EquipesNba.de("LAL").couleur)
        assertEquals("#f2b631", EquipesNba.de("LAL").texte)
        assertEquals("#0d8a4f", EquipesNba.de("BOS").couleur)
        // Code inconnu : badge gris
        assertFalse(EquipesNba.connue("MEL"))
        assertEquals("#4a4f5c", EquipesNba.de("MEL").couleur)
        assertTrue(contraste(EquipesNba.de("MEL").accent, "#0c0e14") >= 4.5)
    }

    @Test
    fun codesEspn() {
        assertEquals(
            listOf("GSW", "NYK", "SAS", "NOP", "UTA", "WAS", "LAL", "BOS"),
            listOf("GS", "NY", "SA", "NO", "UTAH", "WSH", "LAL", "bos").map { EquipesNba.codeNba(it) },
        )
        assertTrue(listOf("GS", "NY", "SA", "NO", "UTAH", "WSH").all { EquipesNba.connue(EquipesNba.codeNba(it)) })
    }
}
