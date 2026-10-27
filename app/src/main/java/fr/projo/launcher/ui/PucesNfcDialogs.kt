package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.puces.PucesNfc

/** Paquet installé et ouvrable (version TV ou classique). */
private fun ouvrable(contexte: android.content.Context, paquet: String): Boolean = try {
    val pm = contexte.packageManager
    pm.getLeanbackLaunchIntentForPackage(paquet) != null || pm.getLaunchIntentForPackage(paquet) != null
} catch (e: Exception) {
    false
}

/**
 * Réglages → Puces NFC : activer l'écoute, une ligne par app (OK : QR code de son lien),
 * aide pour écrire une puce, nouveau code (confirmé par un second OK).
 */
@Composable
fun PucesNfcDialog(
    apps: List<AppInfo>,
    ip: String?,
    onApp: (AppInfo, String) -> Unit,
    onAide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contexte = LocalContext.current
    remember { PucesNfc.charger(contexte) }
    val actif = PucesNfc.actif
    val ecoute = PucesNfc.ecoute
    var confirmer by remember { mutableStateOf(false) }
    val soi = contexte.packageName
    val proposees = remember(apps) {
        PucesNfc.appsProposees(apps, soi).map { it to PucesNfc.adressePour(it.packageName, soi) { p -> ouvrable(contexte, p) } }
    }
    val adresse = if (ip != null) "Adresse du Fire TV : $ip · à réserver dans ta box" else "Fire TV sans réseau : connecte-le au Wi-Fi"
    ListeDialog(
        titre = "Puces NFC",
        sousTitre = "Touche une puce avec ton téléphone : l'app s'ouvre sur la TV.\n$adresse",
        lignes = buildList {
            add(
                LigneListe(
                    "Lancement par puce NFC",
                    when {
                        !actif -> "Désactivé · OK pour activer"
                        ecoute -> "Activé ✓ · le Fire TV écoute sur le Wi-Fi"
                        else -> "Activé · démarrage de l'écoute…"
                    },
                    coche = actif,
                ) { PucesNfc.activer(contexte, !actif) },
            )
            if (actif) {
                add(LigneListe("UNE PUCE PAR APP · OK : SON LIEN EN QR CODE", section = true))
                for ((app, a) in proposees) {
                    add(LigneListe(app.label, "Lien …/o/$a") { onApp(app, a) })
                }
            }
            add(LigneListe("AIDE", section = true))
            add(LigneListe("Écrire une puce", "iPhone et Android, avec l'app gratuite NFC Tools", onClick = onAide))
            if (actif) {
                add(
                    LigneListe(
                        "Nouveau code",
                        if (confirmer) "OK encore pour confirmer : les puces déjà écrites ne marcheront plus"
                        else "Si une puce est perdue : les anciennes puces ne marcheront plus",
                    ) {
                        if (confirmer) {
                            PucesNfc.nouveauCode(contexte)
                            confirmer = false
                        } else {
                            confirmer = true
                        }
                    },
                )
            }
            add(LigneListe("Fermer", onClick = onDismiss))
        },
        onDismiss = onDismiss,
    )
}

/** Lien d'une app : grand QR code (test et copie sur le téléphone), étapes pour écrire la puce. */
@Composable
fun LienPuceDialog(app: String, lien: String, onDismiss: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        Row(
            Modifier
                .width(840.u)
                .fondDialogue(h)
                .padding(28.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(300.u).background(Color.White, RoundedCornerShape(18.u))
                    .border(1.dp, h.texte.copy(alpha = 0.10f), RoundedCornerShape(18.u)).padding(6.u),
                contentAlignment = Alignment.Center,
            ) {
                QrCodeImage(lien, Modifier.fillMaxSize().testTag("qr-puce"))
            }
            Spacer(Modifier.width(30.u))
            Column(Modifier.weight(1f)) {
                Text("Puce « $app »", color = h.texte, fontSize = 26.us, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.u))
                Text("1. Scanne ce QR code avec l'appareil photo du téléphone : $app s'ouvre sur la TV (c'est le test).", color = h.texte, fontSize = 14.us)
                Spacer(Modifier.height(8.u))
                Text("2. Sur la page du téléphone, touche « Lien de cette puce » puis « Copier le lien ».", color = h.texte, fontSize = 14.us)
                Spacer(Modifier.height(8.u))
                Text("3. Dans NFC Tools : Écrire → Ajouter un enregistrement → URL → colle le lien → Écrire, puis approche la puce.", color = h.texte, fontSize = 14.us)
                Spacer(Modifier.height(12.u))
                Text(
                    lien, color = h.discret, fontSize = 11.us, fontFamily = FontFamily.Monospace, lineHeight = 14.us,
                    modifier = Modifier.fillMaxWidth().background(h.carte, RoundedCornerShape(10.u)).padding(10.u).testTag("lien-puce"),
                )
                Spacer(Modifier.height(10.u))
                Text("Le téléphone doit être sur le Wi-Fi de la maison.", color = h.discret, fontSize = 11.us)
                Spacer(Modifier.height(16.u))
                BoutonDialogue("Fermer", Modifier.focusRequester(focus), onClick = onDismiss)
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

/** Aide : écrire une puce, et la rendre « silencieuse » sur iPhone avec Raccourcis. */
@Composable
fun AidePucesDialog(ip: String?, onDismiss: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        Column(
            Modifier
                .width(800.u)
                .fondDialogue(h)
                .padding(26.u),
        ) {
            Text("Puces NFC · aide", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.u))
            Column(
                Modifier.fillMaxWidth().background(h.carte, RoundedCornerShape(12.u)).padding(14.u),
                verticalArrangement = Arrangement.spacedBy(8.u),
            ) {
                Text("Écrire une puce (iPhone et Android)", color = h.texte, fontSize = 15.us, fontWeight = FontWeight.Medium)
                Text(
                    "Installe NFC Tools (gratuit). Dans Réglages → Puces NFC, choisis l'app, scanne le QR code et copie le lien. " +
                        "Puis NFC Tools → Écrire → Ajouter un enregistrement → URL → colle le lien → Écrire, et approche la puce. " +
                        "Une puce NTAG213 suffit.",
                    color = h.texte, fontSize = 13.us, lineHeight = 18.us,
                )
                Text("Android", color = h.texte, fontSize = 15.us, fontWeight = FontWeight.Medium)
                Text(
                    "Téléphone déverrouillé, touche la puce : le lien s'ouvre tout seul et l'app démarre sur la TV.",
                    color = h.texte, fontSize = 13.us, lineHeight = 18.us,
                )
                Text("iPhone", color = h.texte, fontSize = 15.us, fontWeight = FontWeight.Medium)
                Text(
                    "Touche la puce : une notification propose d'ouvrir le lien, touche-la. Pour que ce soit direct : app Raccourcis → " +
                        "Automatisation → + → NFC → scanne la puce → action « Obtenir le contenu de l'URL » avec le lien → " +
                        "« Exécuter immédiatement ».",
                    color = h.texte, fontSize = 13.us, lineHeight = 18.us,
                )
            }
            Spacer(Modifier.height(10.u))
            Text(
                "Le téléphone doit être sur le Wi-Fi de la maison, et le Fire TV allumé ou en veille légère (il se réveille). " +
                    "Réserve l'adresse du Fire TV" + (ip?.let { " ($it)" } ?: "") + " dans ta box (« bail DHCP statique ») : " +
                    "si elle change, les puces ne le trouvent plus. Sans le code de la puce, rien ne s'ouvre.",
                color = h.discret, fontSize = 11.us, lineHeight = 15.us,
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
