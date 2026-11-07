package fr.projo.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.projo.launcher.liens.BandeauLien
import fr.projo.launcher.liens.EnvoiTv
import fr.projo.launcher.liens.Liens
import fr.projo.launcher.liens.QuestionVpn
import kotlin.math.abs

/*
 * « Envoyer sur la TV » à l'écran (docs/telecommande.md) : réglages (Réglages → Envoyer sur la
 * TV), bandeau avant l'ouverture d'un lien, garde-fou « Le VPN de la TV est coupé » et état du
 * VPN pour la pastille de Galerie. Galerie a son propre dessin (maquette validée) ; les autres
 * designs prennent l'habillage de leurs dialogues (habillageDialogue).
 */

/** VPN de la TV, pour la pastille de l'en-tête de Galerie. */
class EtatVpnTv(
    /** Un VPN est actif. */
    val actif: Boolean,
    /** « Seulement avec le VPN » est activé (pastille rouge quand le VPN est coupé). */
    val garde: Boolean,
    /** Appli VPN connue (petit texte de la pastille verte), ou null. */
    val appli: String?,
    /** OK sur la pastille : ouvre l'appli VPN. */
    val ouvrir: () -> Unit,
) {
    /** Pastille visible : VPN actif, ou coupé avec le garde-fou. */
    val visible: Boolean get() = actif || garde
}

/** Fourni par MainActivity ; null (tests, aperçus) : pas de pastille. */
val LocalVpnTv = compositionLocalOf<EtatVpnTv?> { null }

/** Premier focus d'un dialogue (la fenêtre peut n'être prête qu'après une image). */
@Composable
private fun PremierFocus(cible: FocusRequester) {
    LaunchedEffect(Unit) {
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { cible.requestFocus() }.isSuccess) break
        }
    }
}

// ------------------------------------------------------------------ réglages

/**
 * Réglages → « Envoyer sur la TV » : seulement avec le VPN, état du VPN (OK : son appli),
 * appli des pages web, bouton Partager de l'iPhone (adresse du raccourci) et nouveau code
 * (confirmé par un second OK). Mêmes réglages que le groupe de la télécommande.
 */
@Composable
fun EnvoyerTvDialog(
    /** Applis qui ouvrent les pages web (paquet, nom), dans l'ordre « auto ». */
    navigateurs: List<Pair<String, String>>,
    /** Appli VPN connue installée, ou null. */
    appliVpn: String?,
    /** Adresse de la TV sur le réseau local, ou null. */
    ip: String?,
    onOuvrirVpn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contexte = LocalContext.current
    remember { EnvoiTv.charger(contexte) }
    val seulementVpn = EnvoiTv.seulementVpn
    val vpn = EnvoiTv.vpnActif
    val raccourci = EnvoiTv.raccourci
    val code = EnvoiTv.code
    val pages = EnvoiTv.pages.takeIf { p -> navigateurs.any { it.first == p } } ?: Liens.PAGES_AUTO
    var confirmer by remember { mutableStateOf(false) }
    ListeDialog(
        titre = "Envoyer sur la TV",
        sousTitre = "Un lien envoyé depuis le téléphone (onglet « Envoyer » de la télécommande) s'ouvre ici, " +
            "dans son appli : YouTube, Netflix, vidéo… sinon le navigateur.",
        lignes = buildList {
            add(LigneListe("VPN DE LA TV", section = true))
            add(
                LigneListe(
                    "Seulement avec le VPN",
                    "Un lien ne s'ouvre que si un VPN est actif sur la TV (VYPN, Proton VPN, WireGuard…)",
                    coche = seulementVpn,
                ) { EnvoiTv.ecrireSeulementVpn(contexte, !seulementVpn) },
            )
            add(
                LigneListe(
                    if (vpn) "VPN actif ✓" else "VPN coupé",
                    if (appliVpn != null) "OK : ouvrir $appliVpn" else "Aucune appli VPN connue sur la TV",
                    onClick = onOuvrirVpn,
                ),
            )
            add(LigneListe("PAGES WEB", section = true))
            add(
                LigneListe(
                    "Automatique",
                    navigateurs.firstOrNull()?.let { "Aujourd'hui : ${it.second}" } ?: "Aucune appli de la TV n'ouvre les pages web",
                    coche = pages == Liens.PAGES_AUTO,
                ) { EnvoiTv.ecrirePages(contexte, Liens.PAGES_AUTO) },
            )
            for ((paquet, nom) in navigateurs) {
                add(LigneListe(nom, coche = pages == paquet) { EnvoiTv.ecrirePages(contexte, paquet) })
            }
            add(LigneListe("BOUTON PARTAGER DE L'IPHONE", section = true))
            add(
                LigneListe(
                    "Envoyer depuis l'iPhone",
                    when {
                        !raccourci -> "Désactivé · un raccourci iOS envoie la page de Safari sur la TV"
                        ip == null || code.isEmpty() -> "Activé · TV sans adresse sur le réseau local"
                        else -> "Adresse du raccourci : " + EnvoiTv.adresseRaccourci(ip, code)
                    },
                    coche = raccourci,
                ) { EnvoiTv.ecrireRaccourci(contexte, !raccourci) },
            )
            if (raccourci) {
                add(
                    LigneListe(
                        "Nouveau code",
                        if (confirmer) "OK encore pour confirmer : l'ancien raccourci ne marchera plus"
                        else "Si l'adresse a circulé : l'ancien raccourci ne marchera plus",
                    ) {
                        if (confirmer) {
                            EnvoiTv.nouveauCode(contexte)
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

// ------------------------------------------------------------------ bandeau

/** Initiale du site, sur une pastille de couleur (« a » pour arte.tv). */
private fun initiale(hote: String): String =
    hote.removePrefix("www.").firstOrNull { it.isLetterOrDigit() }?.lowercase() ?: "•"

private val COULEURS_SITES = listOf(
    Color(0xFFFF6B00), Color(0xFF7C5CFF), Color(0xFF1F8FFF), Color(0xFFE5484D), Color(0xFF12A594), Color(0xFFD9468F),
)

private fun couleurSite(hote: String): Color = COULEURS_SITES[abs(hote.removePrefix("www.").hashCode()) % COULEURS_SITES.size]

/**
 * Bandeau affiché environ 1 s avant l'ouverture d'un lien (iBiTV à l'écran) : « <titre ou hôte>
 * · envoyé par … — Ouverture dans <appli>… », sous les onglets, avec la barre qui avance.
 */
@Composable
fun BandeauLienTv(b: BandeauLien) {
    val progression = remember(b) { Animatable(0f) }
    LaunchedEffect(b) { progression.animateTo(1f, tween(Liens.BANDEAU_MS.toInt(), easing = LinearEasing)) }
    val galerie = LocalDesign.current == Design.GALERIE
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (galerie) {
            BandeauGalerie(b, Modifier.padding(top = 132.mq)) { progression.value }
        } else {
            BandeauHabille(b, Modifier.padding(top = 26.u)) { progression.value }
        }
    }
}

@Composable
private fun BandeauGalerie(b: BandeauLien, modifier: Modifier, progression: () -> Float) {
    val k = pxMaquette()
    Box(
        modifier
            .testTag("bandeau-lien")
            .height(76.mq)
            .widthIn(max = 1600.mq)
            .drawBehind {
                OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 50f * k, 22f * k, Color(0xFF14161C).copy(alpha = 0.3f))
            }
            .background(Color(0xFF1D1E22).copy(alpha = 0.92f), CircleShape),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 14.mq, end = 30.mq), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.mq).background(couleurSite(b.hote), RoundedCornerShape(14.mq)), contentAlignment = Alignment.Center) {
                TexteGalerie(initiale(b.hote), 24f, Color.White, poids = FontWeight.Bold, interligne = 28f)
            }
            Spacer(Modifier.width(18.mq))
            TexteGalerie(b.texte, 22f, Color.White, Modifier.widthIn(max = 620.mq), poids = FontWeight.Bold, interligne = 28f)
            TexteGalerie(" · envoyé par ${b.expediteur}", 22f, Color.White.copy(alpha = 0.62f), Modifier.widthIn(max = 460.mq), interligne = 28f)
            Spacer(Modifier.width(18.mq))
            TexteGalerie("Ouverture dans ${b.appli}…", 22f, Color.White.copy(alpha = 0.62f), Modifier.widthIn(max = 380.mq), interligne = 28f)
        }
        Box(Modifier.matchParentSize().padding(start = 36.mq, end = 36.mq, bottom = 9.mq), contentAlignment = Alignment.BottomCenter) {
            Spacer(
                Modifier.fillMaxWidth().height(3.mq).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(2.mq))
                    .drawBehind { barre(progression(), Color(0xFF7C5CFF), Color(0xFF22D3EE)) },
            )
        }
    }
}

@Composable
private fun BandeauHabille(b: BandeauLien, modifier: Modifier, progression: () -> Float) {
    val h = habillageDialogue()
    Column(
        modifier
            .testTag("bandeau-lien")
            .widthIn(min = 360.u, max = 760.u)
            .fondDialogue(h)
            .padding(start = 16.u, end = 22.u, top = 12.u, bottom = 10.u),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.u).background(h.accentFond, RoundedCornerShape(10.u)), contentAlignment = Alignment.Center) {
                Icon(ProjoIcons.EnvoyerTv, contentDescription = null, tint = h.texte, modifier = Modifier.size(20.u))
            }
            Spacer(Modifier.width(12.u))
            Column {
                Text(b.texte, color = h.texte, fontSize = 15.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "Envoyé par ${b.expediteur} — Ouverture dans ${b.appli}…",
                    color = h.discret, fontSize = 12.us, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.u))
        Spacer(
            Modifier.fillMaxWidth().height(2.u).background(h.texte.copy(alpha = 0.12f), RoundedCornerShape(1.u))
                .drawBehind { barre(progression(), h.accent, h.accent) },
        )
    }
}

/** Barre de progression : la partie faite, en dégradé. */
private fun DrawScope.barre(fait: Float, debut: Color, fin: Color) {
    val w = size.width * fait.coerceIn(0f, 1f)
    if (w <= 0f) return
    drawRect(Brush.horizontalGradient(listOf(debut, fin), startX = 0f, endX = size.width), size = Size(w, size.height))
}

// ------------------------------------------------------------------ garde-fou

/**
 * Garde-fou (lien du raccourci iOS, « Seulement avec le VPN », VPN coupé) : « Le VPN de la TV
 * est coupé », puis « Allumer le VPN et ouvrir » (seulement si une appli VPN est connue),
 * « Ouvrir sans VPN », « Annuler ». Par-dessus n'importe quel écran d'iBiTV.
 */
@Composable
fun GardeFouVpnDialog(
    q: QuestionVpn,
    /** null : pas d'appli VPN connue, bouton absent. */
    onAllumer: (() -> Unit)?,
    onSansVpn: () -> Unit,
    onAnnuler: () -> Unit,
) {
    if (LocalDesign.current == Design.GALERIE) GardeFouGalerie(q, onAllumer, onSansVpn, onAnnuler)
    else GardeFouHabille(q, onAllumer, onSansVpn, onAnnuler)
}

/** « « Les grands documentaires » (arte.tv), envoyé depuis l'iPhone, ne s'ouvrira qu'avec le VPN. » */
private fun texteGardeFou(q: QuestionVpn, gras: SpanStyle) = buildAnnotatedString {
    val titre = q.lien.titre
    if (titre != null) {
        withStyle(gras) { append("« $titre »") }
        append(" (${q.lien.hoteAffiche})")
    } else {
        withStyle(gras) { append(q.lien.hoteAffiche) }
    }
    append(", envoyé depuis ${q.expediteur}, ne s'ouvrira qu'avec le VPN.")
}

private const val AIDE_GARDE_FOU = "Réglages → Envoyer sur la TV → « Seulement avec le VPN »"

/** Bouclier barré (VPN coupé), au trait, dans un carré de 24. */
private fun DrawScope.bouclierBarre(couleur: Color) {
    pictogramme(couleur, 2f) {
        moveTo(12f, 3f)
        lineTo(19f, 6f)
        lineTo(19f, 12f)
        cubicTo(19f, 16.5f, 16f, 19.8f, 12f, 21f)
        cubicTo(8f, 19.8f, 5f, 16.5f, 5f, 12f)
        lineTo(5f, 6f)
        close()
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }
}

private val RougeVpn = Color(0xFFEF4444)

@Composable
private fun GardeFouGalerie(q: QuestionVpn, onAllumer: (() -> Unit)?, onSansVpn: () -> Unit, onAnnuler: () -> Unit) {
    Dialog(onDismissRequest = onAnnuler, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val k = pxMaquette()
        val focus = remember { FocusRequester() }
        PremierFocus(focus)
        Column(
            Modifier
                .testTag("garde-fou")
                .width(880.mq)
                .verreGalerie(k, 40f, fond = { w, h ->
                    degradeCss(
                        160f, w, h,
                        0f to Color.White.copy(alpha = 0.96f), 0.55f to Color(0xFFF1F2F5).copy(alpha = 0.94f), 1f to Color(0xFFF6F7F9).copy(alpha = 0.95f),
                    )
                }, lisere = Color.White.copy(alpha = 0.75f))
                .padding(start = 56.mq, end = 56.mq, top = 52.mq, bottom = 50.mq),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(
                Modifier.size(92.mq).background(RougeVpn.copy(alpha = 0.12f), RoundedCornerShape(28.mq)).padding(22.mq)
                    .drawBehind { bouclierBarre(RougeVpn) },
            )
            Spacer(Modifier.height(26.mq))
            TexteGalerie(
                "Le VPN de la TV est coupé", 58f, Color(0xFF1F2026), serif = true, poids = FontWeight.Normal,
                interligne = 61f, lignes = 2, centre = true,
            )
            Spacer(Modifier.height(18.mq))
            Text(
                texteGardeFou(q, SpanStyle(color = Color(0xFF2B2D33), fontWeight = FontWeight.Bold)),
                modifier = Modifier.widthIn(max = 700.mq),
                color = Color(0xFF4A4D56),
                fontSize = 23f.mqs,
                fontFamily = GalerieSans,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = TextStyle(lineHeight = 34f.mqs),
            )
            Spacer(Modifier.height(38.mq))
            Row(horizontalArrangement = Arrangement.spacedBy(18.mq)) {
                if (onAllumer != null) {
                    BoutonGardeFou("Allumer le VPN et ouvrir", encre = true, k = k, modifier = Modifier.focusRequester(focus), onClick = onAllumer)
                    BoutonGardeFou("Ouvrir sans VPN", encre = false, k = k, onClick = onSansVpn)
                } else {
                    BoutonGardeFou("Ouvrir sans VPN", encre = true, k = k, modifier = Modifier.focusRequester(focus), onClick = onSansVpn)
                }
                BoutonGardeFou("Annuler", encre = false, k = k, onClick = onAnnuler)
            }
            Spacer(Modifier.height(26.mq))
            TexteGalerie(AIDE_GARDE_FOU, 18f, Color(0xFF6C6F78), centre = true, interligne = 24f)
        }
    }
}

/** Bouton du garde-fou de Galerie : pilule d'encre (principal) ou claire ; anneau quand il est sélectionné. */
@Composable
private fun BoutonGardeFou(texte: String, encre: Boolean, k: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    val fond = if (encre) Color(0xFF1D1E22) else Color.White.copy(alpha = 0.7f)
    val encreTexte = if (encre) Color.White else GalerieEncre
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier
            .testTag("garde-fou:$texte")
            .height(66.mq)
            .drawBehind { anneauGalerie(k, size.height / 2f, t) }
            .onFocusChanged { focus = it.hasFocus }
            .then(if (encre) Modifier else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond, contentColor = encreTexte,
            focusedContainerColor = if (encre) fond else Color.White, focusedContentColor = encreTexte,
            pressedContainerColor = fond, pressedContentColor = encreTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 34.mq), contentAlignment = Alignment.Center) {
            TexteGalerie(texte, 22f, encreTexte, poids = FontWeight.Bold, interligne = 28f)
        }
    }
}

@Composable
private fun GardeFouHabille(q: QuestionVpn, onAllumer: (() -> Unit)?, onSansVpn: () -> Unit, onAnnuler: () -> Unit) {
    val h = habillageDialogue()
    Dialog(onDismissRequest = onAnnuler, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        PremierFocus(focus)
        Column(
            Modifier
                .testTag("garde-fou")
                .width(580.u)
                .fondDialogue(h)
                .border(1.dp, h.alerte.copy(alpha = 0.5f), h.forme)
                .padding(28.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(
                Modifier.size(56.u).background(h.alerte.copy(alpha = 0.16f), RoundedCornerShape(16.u)).padding(13.u)
                    .drawBehind { bouclierBarre(h.alerte) },
            )
            Spacer(Modifier.height(14.u))
            Text("Le VPN de la TV est coupé", color = h.texte, fontSize = 24.us, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.u))
            Text(
                texteGardeFou(q, SpanStyle(color = h.texte, fontWeight = FontWeight.SemiBold)),
                color = h.discret, fontSize = 14.us, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.u))
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                if (onAllumer != null) {
                    BoutonDialogue("Allumer le VPN et ouvrir", Modifier.focusRequester(focus).testTag("garde-fou:Allumer le VPN et ouvrir"), accent = true, onClick = onAllumer)
                    BoutonDialogue("Ouvrir sans VPN", Modifier.testTag("garde-fou:Ouvrir sans VPN"), onClick = onSansVpn)
                } else {
                    BoutonDialogue("Ouvrir sans VPN", Modifier.focusRequester(focus).testTag("garde-fou:Ouvrir sans VPN"), accent = true, onClick = onSansVpn)
                }
                BoutonDialogue("Annuler", Modifier.testTag("garde-fou:Annuler"), onClick = onAnnuler)
            }
            Spacer(Modifier.height(14.u))
            Text(AIDE_GARDE_FOU, color = h.discret, fontSize = 11.us, textAlign = TextAlign.Center)
        }
    }
}
