package fr.projo.launcher.modules.meteo

import androidx.compose.runtime.Composable
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.rememberDonnees

/**
 * Position réglée dans la Météo, partagée par les autres modules (Soleil et lune, Qualité de
 * l'air…) : ville choisie dans les réglages de la Météo, sinon position détectée par l'adresse
 * IP (détectée une seule fois, puis gardée dans les réglages de la Météo).
 * Un seul réglage de position pour tous les modules : on le change dans Réglages · Météo.
 */
object PositionMeteo {
    // Mêmes clés que ModuleMeteo (CLE_LIEU, CLE_AUTO)
    private const val CLE_LIEU = "lieu"
    private const val CLE_AUTO = "auto"

    /** Lieu connu (choisi, sinon détecté) ; null si la Météo n'a encore rien détecté. */
    fun lieu(reglagesMeteo: ReglagesModule): Lieu? =
        Lieu.decoder(reglagesMeteo.texte(CLE_LIEU)) ?: Lieu.decoder(reglagesMeteo.texte(CLE_AUTO))

    /** Garde la position détectée par IP (comme le fait la Météo). */
    fun memoriser(reglagesMeteo: ReglagesModule, lieu: Lieu) = reglagesMeteo.mettre(CLE_AUTO, lieu.encoder())
}

/**
 * Position de la Météo pour le module [ctx] : valeur immédiate si elle est connue, sinon
 * détection par l'adresse IP (réseau, une fois). États : chargement, erreur, valeur.
 */
@Composable
fun rememberPositionMeteo(ctx: ModuleContexte): EtatDonnees<Lieu> {
    val reglages = LocalHoteModules.current?.reglagesDe(ModuleMeteo.id)
    val connu = reglages?.let { PositionMeteo.lieu(it) }
    if (connu != null) return EtatDonnees(connu)
    return ctx.rememberDonnees(
        "position:ip",
        delai = { _: Lieu -> 24 * 3_600_000L },
        delaiErreur = 5 * 60_000L,
    ) {
        MeteoReseau.localiserParIp().also { lieu -> reglages?.let { PositionMeteo.memoriser(it, lieu) } }
    }
}
