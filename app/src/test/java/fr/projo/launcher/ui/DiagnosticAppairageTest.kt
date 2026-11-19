package fr.projo.launcher.ui

import fr.projo.launcher.remote.DiagnosticTelecommande
import fr.projo.launcher.remote.EtatServeur
import fr.projo.launcher.remote.EtatTentative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ligne d'état de l'écran d'appairage : serveur de mise en relation, puis tentative d'un téléphone. */
class DiagnosticAppairageTest {

    private fun texte(serveur: EtatServeur, tentative: EtatTentative = EtatTentative.AUCUNE, depuis: Long = 0L, ouvertLe: Long = 0L) =
        texteDiagnostic(DiagnosticTelecommande(serveur, tentative, depuis), ouvertLe)

    @Test
    fun serveur() {
        assertEquals("● Prête : en attente du téléphone." to false, texte(EtatServeur.CONNECTE))
        assertEquals("Connexion au serveur de mise en relation…" to false, texte(EtatServeur.CONNEXION))
        assertTrue(texte(EtatServeur.INJOIGNABLE).second)
        assertTrue(texte(EtatServeur.INJOIGNABLE).first.contains("Internet de la TV"))
        assertTrue(texte(EtatServeur.ECRAN_ETEINT).second)
    }

    @Test
    fun tentativeDuTelephone() {
        assertEquals("Téléphone détecté, connexion en cours…" to false, texte(EtatServeur.CONNECTE, EtatTentative.EN_COURS, depuis = 10))
        val reseau = texte(EtatServeur.CONNECTE, EtatTentative.RESEAU, depuis = 10)
        assertTrue(reseau.second)
        assertTrue(reseau.first.contains("même réseau"))
        assertTrue(texte(EtatServeur.CONNECTE, EtatTentative.WEBRTC, depuis = 10).first.contains("WebRTC"))
        assertFalse(texte(EtatServeur.CONNECTE, EtatTentative.REUSSIE, depuis = 10).second)
    }

    /** Une tentative d'avant l'ouverture de l'écran n'est pas montrée. */
    @Test
    fun tentativeAncienneIgnoree() {
        assertEquals("● Prête : en attente du téléphone." to false, texte(EtatServeur.CONNECTE, EtatTentative.RESEAU, depuis = 5, ouvertLe = 10))
    }
}
