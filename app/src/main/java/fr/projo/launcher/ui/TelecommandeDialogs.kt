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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.remote.QrCode
import fr.projo.launcher.remote.RemoteConfig
import fr.projo.launcher.remote.TelephoneAppaire
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Résumé de la télécommande pour le panneau Réglages. */
data class ResumeTelecommande(
    val telephones: Int = 0,
    val connectes: Int = 0,
    val clavierActive: Boolean = false,
    val clavierChoisi: Boolean = false,
    val navigationActive: Boolean = false,
)

/** Icônes Material de la télécommande. */
object IconesTelecommande {
    val Telephone = icone("M17,1.01L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-1.99 -2,-1.99zM17,19H7V5h10v14z")
    val Qr = icone("M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9zM19,19h2v2h-2zM13,13h2v2h-2zM15,15h2v2h-2zM13,17h2v2h-2zM15,19h2v2h-2zM17,17h2v2h-2zM17,13h2v2h-2zM19,15h2v2h-2z")
    val Clavier = icone("M20,5H4c-1.1,0 -1.99,0.9 -1.99,2L2,17c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM11,8h2v2h-2V8zM11,11h2v2h-2v-2zM8,8h2v2H8V8zM8,11h2v2H8v-2zM7,13H5v-2h2v2zM7,10H5V8h2v2zM16,17H8v-2h8v2zM16,13h-2v-2h2v2zM16,10h-2V8h2v2zM19,13h-2v-2h2v2zM19,10h-2V8h2v2z")

    private fun icone(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
            .build()
}

/** Premier focus d'un dialogue (la fenêtre peut n'être prête qu'après une image). */
@Composable
private fun FocusInitial(cible: FocusRequester, cle: Any? = Unit) {
    LaunchedEffect(cle) {
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { cible.requestFocus() }.isSuccess) break
        }
    }
}

/**
 * QR code noir sur fond blanc, avec sa zone de silence (4 modules), centré dans [modifier].
 * Bords des modules arrondis au pixel (nets, sans flou ni liseré entre deux modules), suites
 * de modules noirs d'une ligne réunies en un rectangle : le chemin est calculé une seule fois
 * par taille (drawWithCache), puis dessiné d'un coup à chaque image.
 */
@Composable
fun QrCodeImage(texte: String, modifier: Modifier = Modifier, niveau: ErrorCorrectionLevel = ErrorCorrectionLevel.M) {
    val modules = remember(texte, niveau) { QrCode.matrice(texte, niveau) }
    Spacer(
        modifier
            .background(Color.White)
            .drawWithCache {
                val marge = QrCode.ZONE_DE_SILENCE
                val n = modules.size + 2 * marge
                val cote = size.minDimension
                val pas = cote / n
                val x0 = (size.width - cote) / 2
                val y0 = (size.height - cote) / 2
                fun bord(origine: Float, i: Int) = (origine + (i + marge) * pas).roundToInt().toFloat()
                val chemin = Path()
                for (y in modules.indices) {
                    val ligne = modules[y]
                    var x = 0
                    while (x < ligne.size) {
                        if (!ligne[x]) { x++; continue }
                        val debut = x
                        while (x < ligne.size && ligne[x]) x++
                        chemin.addRect(Rect(bord(x0, debut), bord(y0, y), bord(x0, x), bord(y0, y + 1)))
                    }
                }
                onDrawBehind { drawPath(chemin, Color.Black) }
            },
    )
}

private fun minutesSecondes(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Réglages → « Appairer un téléphone » : grand QR code, adresse courte, code de secours
 * XXXX-XXXX et compte à rebours de 5 minutes.
 */
@Composable
fun AppairageDialog(
    etat: EtatAppairage,
    onNouveauCode: () -> Unit,
    onFermer: () -> Unit,
    horloge: () -> Long = { System.currentTimeMillis() },
) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onFermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        FocusInitial(focus, etat::class)
        Row(
            Modifier
                .width(820.u)
                .fondDialogue(h)
                .padding(28.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(320.u).background(Color.White, RoundedCornerShape(18.u))
                    // Liseré discret : le cadre blanc reste délimité sur un design clair
                    .border(1.dp, h.texte.copy(alpha = 0.10f), RoundedCornerShape(18.u)).padding(6.u),
                contentAlignment = Alignment.Center,
            ) {
                when (etat) {
                    is EtatAppairage.EnCours -> QrCodeImage(etat.url, Modifier.fillMaxSize().testTag("qr"))
                    is EtatAppairage.Reussi -> Text("✓", color = Color(0xFF1B8A4A), fontSize = 120.us)
                    is EtatAppairage.Inactif -> Text("…", color = Color(0xFF333333), fontSize = 40.us)
                    else -> Text("Code expiré", color = Color(0xFF333333), fontSize = 22.us, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(30.u))
            Column(Modifier.weight(1f)) {
                Text("Appairer un téléphone", color = h.texte, fontSize = 26.us, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.u))
                when (etat) {
                    is EtatAppairage.EnCours -> {
                        var maintenant by remember { mutableLongStateOf(horloge()) }
                        LaunchedEffect(etat) {
                            while (true) {
                                maintenant = horloge()
                                delay(1_000)
                            }
                        }
                        val restant = (etat.expire - maintenant).coerceAtLeast(0)
                        Text("1. Scanne le QR code avec l'appareil photo du téléphone.", color = h.texte, fontSize = 14.us)
                        Spacer(Modifier.height(10.u))
                        Text("2. Ou ouvre cette adresse sur le téléphone :", color = h.texte, fontSize = 14.us)
                        Text(RemoteConfig.URL_PWA_COURTE, color = h.accent, fontSize = 15.us, fontWeight = FontWeight.SemiBold)
                        Text("et saisis le code :", color = h.texte, fontSize = 14.us)
                        Spacer(Modifier.height(6.u))
                        Text(
                            etat.code, color = h.texte, fontSize = 40.us, fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace, letterSpacing = 4.us, modifier = Modifier.testTag("code"),
                        )
                        Spacer(Modifier.height(12.u))
                        Text("Valable encore ${minutesSecondes(restant)}", color = h.discret, fontSize = 12.us, modifier = Modifier.testTag("rebours"))
                        Spacer(Modifier.height(5.u))
                        Box(Modifier.fillMaxWidth().height(4.u).background(h.texte.copy(alpha = 0.14f), RoundedCornerShape(2.u))) {
                            Box(
                                Modifier
                                    .fillMaxWidth((restant.toFloat() / RemoteConfig.DUREE_SECRET_MS).coerceIn(0f, 1f))
                                    .height(4.u)
                                    .background(h.accent, RoundedCornerShape(2.u)),
                            )
                        }
                        Spacer(Modifier.height(10.u))
                        Text("Le téléphone et la TV doivent être sur le même Wi-Fi.", color = h.discret, fontSize = 11.us)
                        Spacer(Modifier.height(16.u))
                        BoutonDialogue("Fermer", Modifier.focusRequester(focus), onClick = onFermer)
                    }
                    is EtatAppairage.Reussi -> {
                        Text("${etat.nom} est appairé.", color = h.texte, fontSize = 18.us, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.u))
                        Text("Il pilote maintenant la TV, sans refaire cette étape.", color = h.discret, fontSize = 13.us)
                        Spacer(Modifier.height(20.u))
                        BoutonDialogue("Terminé", Modifier.focusRequester(focus), accent = true, onClick = onFermer)
                    }
                    is EtatAppairage.Inactif -> {
                        Text("Préparation du code…", color = h.texte, fontSize = 15.us)
                        Spacer(Modifier.height(20.u))
                        BoutonDialogue("Fermer", Modifier.focusRequester(focus), onClick = onFermer)
                    }
                    else -> {
                        Text("Le code a expiré au bout de 5 minutes.", color = h.texte, fontSize = 15.us)
                        Spacer(Modifier.height(20.u))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                            BoutonDialogue("Nouveau code", Modifier.focusRequester(focus), accent = true, onClick = onNouveauCode)
                            BoutonDialogue("Fermer", onClick = onFermer)
                        }
                    }
                }
            }
        }
    }
}

/** « Autoriser le téléphone de Marie ? » : nom tel que donné par le téléphone. */
fun questionAutorisation(nom: String): String {
    val n = nom.trim()
    return if (n.startsWith("téléphone ", ignoreCase = true)) "Autoriser le " + n.replaceFirstChar { it.lowercase() } + " ?"
    else "Autoriser « $n » ?"
}

/** Confirmation d'appairage, par-dessus n'importe quel écran d'iBiProjo. */
@Composable
fun ConfirmationAppairageDialog(nom: String, onAutoriser: () -> Unit, onRefuser: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onRefuser, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        FocusInitial(focus)
        Column(
            Modifier
                .width(520.u)
                .fondDialogue(h)
                .border(1.dp, h.accent.copy(alpha = 0.5f), h.forme)
                .padding(28.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.u).background(h.accentFond, RoundedCornerShape(28.u)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.tv.material3.Icon(IconesTelecommande.Telephone, contentDescription = null, tint = h.texte, modifier = Modifier.size(30.u))
            }
            Spacer(Modifier.height(14.u))
            Text(questionAutorisation(nom), color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.u))
            Text(
                "Ce téléphone pourra piloter la TV et y écrire du texte. " +
                    "Refuse si tu ne viens pas de scanner le code.",
                color = h.discret, fontSize = 13.us, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.u))
            Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                BoutonDialogue("Refuser", onClick = onRefuser)
                BoutonDialogue("Autoriser", Modifier.focusRequester(focus), accent = true, onClick = onAutoriser)
            }
        }
    }
}

/** Noms proposés pour renommer un téléphone sans clavier. */
val NOMS_PROPOSES = listOf(
    "Mon téléphone", "Téléphone du salon", "Téléphone de Papa", "Téléphone de Maman",
    "Téléphone des enfants", "Tablette", "Téléphone invité",
)

private fun dateCourte(ms: Long): String =
    if (ms <= 0) "?" else SimpleDateFormat("d MMM yyyy", Locale.FRENCH).format(Date(ms))

/**
 * Réglages → « Téléphones appairés » : liste, renommer (noms proposés ou clavier),
 * révoquer (le téléphone connecté reçoit `revoked`).
 */
@Composable
fun TelephonesDialog(
    telephones: List<TelephoneAppaire>,
    connectes: Set<String>,
    onRenommer: (id: String, nom: String) -> Unit,
    onRevoquer: (id: String) -> Unit,
    onAppairer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var etape by remember { mutableStateOf("liste") }
    val choisi = telephones.firstOrNull { etape.substringAfter(':') == it.id }
    when {
        etape.startsWith("menu:") && choisi != null -> ListeDialog(
            titre = choisi.nom,
            sousTitre = (if (choisi.id in connectes) "Connecté · " else "") + "appairé le ${dateCourte(choisi.date)}",
            lignes = listOf(
                LigneListe("Renommer", "Choisir un nom ou l'écrire") { etape = "nom:" + choisi.id },
                LigneListe("Révoquer", "Ce téléphone ne pourra plus piloter la TV") { etape = "revoquer:" + choisi.id },
                LigneListe("Retour") { etape = "liste" },
            ),
            onDismiss = { etape = "liste" },
        )
        etape.startsWith("nom:") && choisi != null -> ListeDialog(
            titre = "Renommer « ${choisi.nom} »",
            sousTitre = null,
            lignes = NOMS_PROPOSES.map { n -> LigneListe(n, coche = n == choisi.nom) { onRenommer(choisi.id, n); etape = "menu:" + choisi.id } } +
                LigneListe("Écrire un nom…", "Avec le clavier de la TV ou du téléphone") { etape = "clavier:" + choisi.id },
            onDismiss = { etape = "menu:" + choisi.id },
        )
        etape.startsWith("clavier:") && choisi != null -> SaisieNomDialog(
            initial = choisi.nom,
            onValider = { onRenommer(choisi.id, it); etape = "menu:" + choisi.id },
            onDismiss = { etape = "nom:" + choisi.id },
        )
        etape.startsWith("revoquer:") && choisi != null -> MenuDialog(
            title = "Révoquer « ${choisi.nom} » ?",
            subtitle = "Il faudra l'appairer de nouveau pour s'en servir.",
            actions = listOf(
                MenuAction("Révoquer") { onRevoquer(choisi.id) },
                MenuAction("Annuler") {},
            ),
            onDismiss = { etape = "liste" },
        )
        else -> ListeDialog(
            titre = "Téléphones appairés",
            sousTitre = if (telephones.isEmpty()) "Aucun téléphone pour l'instant" else "Choisis un téléphone pour le renommer ou le révoquer",
            lignes = telephones.map { t ->
                LigneListe(
                    t.nom,
                    (if (t.id in connectes) "● Connecté · " else "") + "appairé le ${dateCourte(t.date)}",
                ) { etape = "menu:" + t.id }
            } + LigneListe("Appairer un téléphone", "QR code à scanner") { onAppairer() } + LigneListe("Fermer") { onDismiss() },
            onDismiss = onDismiss,
        )
    }
}

/** Saisie d'un nom au clavier (clavier de la TV, ou du téléphone par le clavier iBiProjo). */
@Composable
private fun SaisieNomDialog(initial: String, onValider: (String) -> Unit, onDismiss: () -> Unit) {
    val h = habillageDialogue()
    var texte by remember { mutableStateOf(initial) }
    var champFocus by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        val focus = remember { FocusRequester() }
        FocusInitial(focus)
        Column(Modifier.width(460.u).fondDialogue(h).padding(22.u)) {
            Text("Nom du téléphone", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.u))
            BasicTextField(
                value = texte,
                onValueChange = { texte = it.take(40) },
                singleLine = true,
                textStyle = TextStyle(color = h.texte, fontSize = 18.us),
                cursorBrush = SolidColor(h.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (texte.isNotBlank()) onValider(texte) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { champFocus = it.isFocused }
                    .background(h.carte, RoundedCornerShape(10.u))
                    // Champ sélectionné à la télécommande : contour de l'encre du texte
                    .border(2.u, h.texte.copy(alpha = if (champFocus) 0.9f else 0.15f), RoundedCornerShape(10.u))
                    .padding(12.u),
            )
            Spacer(Modifier.height(16.u))
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                BoutonDialogue("Enregistrer", accent = true) { if (texte.isNotBlank()) onValider(texte) }
                BoutonDialogue("Annuler", onClick = onDismiss)
            }
        }
    }
}

/**
 * Commandes adb pour activer le clavier et la navigation sans passer par les menus.
 * [navigation] : service d'accessibilité qui porte la navigation (Fire TV : RedirectionAccueil).
 */
fun commandesAdb(
    paquet: String,
    autresServices: String?,
    navigation: String = "$paquet/.remote.NavigationTelecommande",
): List<String> {
    val clavier = "$paquet/.remote.ClavierTelecommande"
    val services = (autresServices?.split(':')?.filter { it.isNotBlank() && it != navigation }.orEmpty() + navigation).joinToString(":")
    return listOf(
        "adb shell ime enable $clavier",
        "adb shell ime set $clavier",
        "adb shell settings put secure enabled_accessibility_services $services",
        "adb shell settings put secure accessibility_enabled 1",
        "adb shell pm grant $paquet android.permission.WRITE_SECURE_SETTINGS",
    )
}

/**
 * Aide à l'activation unique du clavier iBiProjo et de la navigation (accessibilité),
 * comme l'aide de l'ouverture automatique : boutons vers les pages système, et commandes adb
 * (Fire TV, ou appareil sans ces pages).
 */
@Composable
fun AideTelecommandeDialog(
    resume: ResumeTelecommande,
    commandes: List<String>,
    onReglagesClavier: () -> Unit,
    onChoisirClavier: () -> Unit,
    onAccessibilite: () -> Unit,
    onDismiss: () -> Unit,
    /** Remarque propre à l'appareil (Fire TV : service commun, redémarrage après mise à jour). */
    note: String? = null,
) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        FocusInitial(focus)
        Row(
            Modifier
                .width(880.u)
                .fondDialogue(h)
                .padding(28.u),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clavier et navigation", color = h.texte, fontSize = 24.us, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(12.u))
                Etat(
                    fait = resume.clavierChoisi,
                    texte = when {
                        resume.clavierChoisi -> "Clavier iBiProjo : activé et choisi"
                        resume.clavierActive -> "Clavier iBiProjo : activé, à choisir"
                        else -> "Clavier iBiProjo : à activer"
                    },
                )
                Etat(fait = resume.navigationActive, texte = if (resume.navigationActive) "Navigation : activée" else "Navigation (accessibilité) : à activer")
                Spacer(Modifier.height(10.u))
                Text(
                    "À faire une seule fois. Le clavier écrit dans les applis le texte tapé sur le téléphone ; " +
                        "la navigation fait marcher les flèches et OK hors d'iBiProjo. Sans téléphone connecté, " +
                        "le clavier rend la main au clavier habituel.",
                    color = h.discret, fontSize = 12.us,
                )
                Spacer(Modifier.height(16.u))
                Column(verticalArrangement = Arrangement.spacedBy(8.u)) {
                    BoutonDialogue("Activer le clavier (page système)", Modifier.fillMaxWidth().focusRequester(focus), onClick = onReglagesClavier)
                    BoutonDialogue("Choisir le clavier", Modifier.fillMaxWidth(), onClick = onChoisirClavier)
                    BoutonDialogue("Activer la navigation (Accessibilité)", Modifier.fillMaxWidth(), onClick = onAccessibilite)
                    BoutonDialogue("Fermer", Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
            Spacer(Modifier.width(24.u))
            Column(Modifier.width(400.u)) {
                Text("Fire TV, ou appareil sans ces pages", color = h.texte, fontSize = 14.us, fontWeight = FontWeight.SemiBold)
                Text("Depuis un ordinateur avec adb (débogage ADB activé) :", color = h.discret, fontSize = 12.us)
                Spacer(Modifier.height(10.u))
                Column(
                    Modifier.fillMaxWidth().background(h.carte, RoundedCornerShape(12.u)).padding(12.u),
                    verticalArrangement = Arrangement.spacedBy(8.u),
                ) {
                    for (commande in commandes) {
                        Text(commande, color = h.texte, fontSize = 10.us, fontFamily = FontFamily.Monospace, lineHeight = 13.us)
                    }
                }
                Spacer(Modifier.height(8.u))
                Text(
                    "La dernière commande est facultative : elle permet au clavier iBiProjo de se re-choisir tout seul quand un téléphone se connecte.",
                    color = h.discret, fontSize = 11.us,
                )
                if (note != null) {
                    Spacer(Modifier.height(10.u))
                    Text(note, color = h.accent, fontSize = 12.us)
                }
            }
        }
    }
}

/** Ligne d'état : pastille verte (fait) ou orange (à faire). */
@Composable
private fun Etat(fait: Boolean, texte: String) {
    val h = habillageDialogue()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.u)) {
        Box(Modifier.size(10.u).background(if (fait) Color(0xFF3DDC84) else Color(0xFFFFB86B), RoundedCornerShape(5.u)))
        Spacer(Modifier.width(10.u))
        Text(texte, color = h.texte, fontSize = 14.us)
    }
}
