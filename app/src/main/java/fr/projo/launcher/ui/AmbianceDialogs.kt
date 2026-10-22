package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import fr.projo.launcher.ambiance.CouleursNeon
import fr.projo.launcher.ambiance.Intensite
import fr.projo.launcher.ambiance.ModeAmbiance

/** Autorisations du mode Ambiance, relues à l'ouverture du dialogue et au retour d'une page système. */
class EtatAmbiance(
    /** Son en direct : autorisation « micro » (RECORD_AUDIO). */
    val micro: Boolean,
    /** Couleur de la pochette : accès aux notifications. */
    val notifications: Boolean,
    /** Service « Ouverture automatique » actif (c'est lui qui dessine le néon). */
    val service: Boolean,
)

/**
 * Réglages → Mode Ambiance : intensité, couleurs du néon, allumage automatique avec les apps
 * de musique, état des autorisations et source utilisée (diagnostic).
 */
@Composable
fun AmbianceDialog(
    etat: EtatAmbiance,
    onMicro: () -> Unit,
    onNotifications: () -> Unit,
    onService: () -> Unit,
    onAide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contexte = LocalContext.current
    remember { ModeAmbiance.charger(contexte) }
    val intensite = ModeAmbiance.intensite
    val couleurs = ModeAmbiance.couleurs
    val auto = ModeAmbiance.auto
    val source = ModeAmbiance.diagnostic ?: "pas encore allumé"
    ListeDialog(
        titre = "Mode Ambiance",
        sousTitre = "Un néon autour de l'écran, réactif à la musique, que l'ambilight reprend.\nSource : $source",
        lignes = buildList {
            add(LigneListe("INTENSITÉ", section = true))
            for (i in Intensite.entries) {
                val detail = when (i) {
                    Intensite.DOUCE -> "Pour la nuit : lumière et sursauts réduits"
                    Intensite.NORMALE -> "Pour le salon"
                    Intensite.FORTE -> "Lumière et sursauts au maximum"
                }
                add(LigneListe(i.nom, detail, coche = i == intensite) { ModeAmbiance.ecrireIntensite(contexte, i) })
            }
            add(LigneListe("COULEURS DU NÉON", section = true))
            add(
                LigneListe("Pochette", "Couleur de la jaquette du morceau (sinon de l'app), plus claire ou plus sombre", coche = couleurs == CouleursNeon.POCHETTE) {
                    ModeAmbiance.ecrireCouleurs(contexte, CouleursNeon.POCHETTE)
                },
            )
            add(
                LigneListe("Arc-en-ciel", "Basses rouges en bas, médiums sur les côtés, aigus bleus en haut", coche = couleurs == CouleursNeon.ARC_EN_CIEL) {
                    ModeAmbiance.ecrireCouleurs(contexte, CouleursNeon.ARC_EN_CIEL)
                },
            )
            add(LigneListe("ALLUMAGE", section = true))
            add(
                LigneListe("Automatique avec les apps de musique", "Spotify, Deezer, radios… Jamais avec les apps vidéo", coche = auto) {
                    ModeAmbiance.ecrireAuto(contexte, !auto)
                },
            )
            add(LigneListe("Dans les autres apps", "Menu de l'app → « Ouvrir en mode Ambiance », ou appui long sur ☰ dans l'app", onClick = onAide))
            add(LigneListe("ÉTAT", section = true))
            add(
                LigneListe(
                    if (etat.service) "Néon : service actif ✓" else "Néon : « Ouverture automatique » à activer",
                    "Le néon est dessiné par ce service d'accessibilité",
                    onClick = onService,
                ),
            )
            add(
                LigneListe(
                    if (etat.micro) "Son en direct : autorisé ✓" else "Son en direct : à autoriser",
                    "Fait réagir le néon au son ; rien n'est enregistré ni envoyé",
                    onClick = onMicro,
                ),
            )
            add(
                LigneListe(
                    if (etat.notifications) "Pochette : accès aux notifications ✓" else "Pochette : accès aux notifications à activer",
                    "Pour prendre la couleur de la jaquette ; aucune notification n'est lue",
                    onClick = onNotifications,
                ),
            )
            add(LigneListe("Aide et commandes adb", "Si une page d'autorisation manque sur le Fire TV", onClick = onAide))
            add(LigneListe("Fermer", onClick = onDismiss))
        },
        onDismiss = onDismiss,
        focusInitial = 1 + intensite.ordinal,
    )
}

/**
 * Avant la fenêtre d'Android « Autoriser à enregistrer de l'audio ? » (premier lancement du
 * mode Ambiance) : à quoi elle sert, et ce qui n'est pas fait.
 */
@Composable
fun ExplicationMicroDialog(onContinuer: () -> Unit, onPlusTard: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onPlusTard, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        Column(
            Modifier
                .width(560.u)
                .fondDialogue(h)
                .padding(26.u),
        ) {
            Text("Faire réagir le néon au son", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.u))
            Text(
                "Pour suivre la musique, iBiProjo lit le spectre du son qui sort du Fire TV (le « Visualizer » d'Android).",
                color = h.texte, fontSize = 14.us,
            )
            Spacer(Modifier.height(8.u))
            Text(
                "Android va demander l'autorisation d'enregistrer de l'audio : elle ne sert qu'à faire réagir les LED au son ; " +
                    "rien n'est enregistré ni envoyé.",
                color = h.texte, fontSize = 14.us, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.u))
            Text(
                "Sans elle, le néon prend la couleur de la pochette et pulse doucement, sans suivre le rythme.",
                color = h.discret, fontSize = 12.us,
            )
            Spacer(Modifier.height(18.u))
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                BoutonDialogue("Continuer", Modifier.focusRequester(focus), accent = true, onClick = onContinuer)
                BoutonDialogue("Plus tard", onClick = onPlusTard)
            }
        }
        LaunchedEffect(Unit) {
            for (essai in 1..4) {
                withFrameNanos {}
                if (runCatching { focus.requestFocus() }.isSuccess) break
            }
        }
    }
}

/** Aide du mode Ambiance : comment l'allumer, et commandes adb si une page manque. */
@Composable
fun AideAmbianceDialog(commandes: List<String>, onDismiss: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        Column(
            Modifier
                .width(780.u)
                .fondDialogue(h)
                .padding(26.u),
        ) {
            Text("Mode Ambiance · aide", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.u))
            Text(
                "Le néon s'allume tout seul avec les apps de musique. Pour une autre app : menu de sa tuile → " +
                    "« Ouvrir en mode Ambiance », ou appui long sur ☰ dans l'app (encore une fois pour l'éteindre).",
                color = h.texte, fontSize = 13.us,
            )
            Spacer(Modifier.height(10.u))
            Text(
                "Si le Fire TV n'a pas la page d'autorisation : depuis un ordinateur (Mac : dossier platform-tools, " +
                    "« Débogage ADB » activé), tape :",
                color = h.discret, fontSize = 12.us,
            )
            Spacer(Modifier.height(8.u))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(h.carte, RoundedCornerShape(12.u))
                    .padding(12.u),
                verticalArrangement = Arrangement.spacedBy(8.u),
            ) {
                for (c in commandes) {
                    Text(c, color = h.texte, fontSize = 11.us, fontFamily = FontFamily.Monospace, lineHeight = 14.us)
                }
            }
            Spacer(Modifier.height(8.u))
            Text(
                "Son en direct : seulement le spectre du son, rien n'est enregistré ni envoyé. " +
                    "Pochette : seule la couleur de la jaquette est lue, aucune notification.",
                color = h.discret, fontSize = 11.us,
            )
            Spacer(Modifier.height(16.u))
            BoutonDialogue("Fermer", Modifier.focusRequester(focus), onClick = onDismiss)
        }
        LaunchedEffect(Unit) {
            for (essai in 1..4) {
                withFrameNanos {}
                if (runCatching { focus.requestFocus() }.isSuccess) break
            }
        }
    }
}
