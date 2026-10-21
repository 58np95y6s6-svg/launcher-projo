package fr.projo.launcher.modules.telecommande

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.u
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.remote.RemoteConfig
import fr.projo.launcher.remote.Telecommande
import fr.projo.launcher.remote.TelephoneAppaire
import fr.projo.launcher.ui.AppairageDialog
import fr.projo.launcher.ui.IconesTelecommande
import fr.projo.launcher.ui.LocalScale
import fr.projo.launcher.ui.QrCodeImage
import fr.projo.launcher.ui.u
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow

/** Clé de DonneesModules qui fournit au module une [SourceTelecommande] factice (tests). */
const val CLE_SOURCE_TELECOMMANDE = "telecommande:source"

/**
 * Correction d'erreur du QR code du module : L (voir QrCode.matrice). Sur un écran, rien à
 * reconstituer ; à 3 m, la taille des modules prime (version 7, 45 × 45 modules).
 */
val NIVEAU_QR_MODULE: ErrorCorrectionLevel = ErrorCorrectionLevel.L

/** Ce que le module lit et commande : la télécommande réelle, ou une source factice (tests). */
interface SourceTelecommande {
    val appairage: StateFlow<EtatAppairage>
    val telephones: StateFlow<List<TelephoneAppaire>>
    val connectes: StateFlow<Set<String>>

    /** Module affiché, accueil au premier plan : QR code valable, renouvelé à l'échéance. */
    fun afficher(cle: Any)

    /** Accueil caché ou module retiré : plus de renouvellement. */
    fun masquer(cle: Any)

    /** OK sur le module : dialogue d'appairage en grand. */
    fun ouvrirDialogue()
    fun fermerDialogue()

    /** « Nouveau code » du dialogue (code expiré). */
    fun nouveauCode()
}

/** La télécommande de l'appli (remote.Telecommande). */
object SourceTelecommandeReelle : SourceTelecommande {
    override val appairage: StateFlow<EtatAppairage> get() = Telecommande.etatAppairage
    override val telephones: StateFlow<List<TelephoneAppaire>> get() = Telecommande.telephones
    override val connectes: StateFlow<Set<String>> get() = Telecommande.connectes
    override fun afficher(cle: Any) = Telecommande.afficherQr(cle)
    override fun masquer(cle: Any) = Telecommande.masquerQr(cle)
    override fun ouvrirDialogue() = Telecommande.ouvrirAppairage()
    override fun fermerDialogue() = Telecommande.arreterAppairage()
    override fun nouveauCode() = Telecommande.commencerAppairage()
}

/**
 * Module Télécommande : le QR code d'appairage de la télécommande smartphone, directement sur
 * l'accueil (même URL que Réglages → « Appairer un téléphone »), le code de secours XXXX-XXXX
 * et, en MOYEN / GRAND, les téléphones appairés (pastille verte : connecté).
 *
 * Sécurité : le secret (5 minutes, usage unique) n'est créé et renouvelé que pendant que
 * l'accueil est au premier plan, écran allumé (voir remote.RenouvellementQr) ; jamais pendant
 * une confirmation « Autoriser / Refuser ». Tout appairage reste confirmé sur la TV, avec la
 * limite de tentatives : un QR code affiché en permanence ne suffit pas à piloter la TV.
 *
 * Lisibilité à 3 m : noir sur blanc dans un cadre blanc (zone de silence comprise) quel que
 * soit le design, environ 100 u en PETIT. OK : le dialogue d'appairage en grand.
 */
object ModuleTelecommande : Module(
    id = "telecommande",
    nom = "Télécommande",
    description = "QR code pour appairer un téléphone, téléphones appairés",
    icone = IconesTelecommande.Qr,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val source = remember(ctx.donnees) {
            ctx.donnees.factice(CLE_SOURCE_TELECOMMANDE) as? SourceTelecommande ?: SourceTelecommandeReelle
        }
        val etat by source.appairage.collectAsState()
        val telephones by source.telephones.collectAsState()
        val connectes by source.connectes.collectAsState()

        // Secret créé et renouvelé seulement pendant que l'accueil est au premier plan
        val cycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(source, cycle) {
            cycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val cle = Any()
                source.afficher(cle)
                try {
                    awaitCancellation()
                } finally {
                    source.masquer(cle)
                }
            }
        }

        var dialogue by remember { mutableStateOf(false) }
        val sourceActuelle by rememberUpdatedState(source)
        // Module retiré pendant que le dialogue est ouvert : le dialogue disparaît avec lui
        DisposableEffect(Unit) { onDispose { if (dialogue) sourceActuelle.fermerDialogue() } }

        val vue = Vue(etat, telephones, connectes)
        CarteModule(
            ctx,
            modifier.height(ctx.hauteur.u).testTag("telecommande:carte"),
            onClick = {
                dialogue = true
                source.ouvrirDialogue()
            },
        ) {
            when (ctx.taille) {
                TailleModule.PETIT -> TelecommandePetit(vue, ctx)
                TailleModule.MOYEN -> TelecommandeHorizontal(vue, ctx, k = 1f)
                TailleModule.GRAND ->
                    if (ctx.largeur - ctx.hauteur >= 160f) TelecommandeHorizontal(vue, ctx, k = 1.25f)
                    else TelecommandeGrandVertical(vue, ctx)
            }
        }
        if (dialogue) {
            AppairageDialog(
                etat = etat,
                onNouveauCode = { source.nouveauCode() },
                onFermer = {
                    dialogue = false
                    source.fermerDialogue()
                },
            )
        }
    }
}

/** Ce que le module affiche. */
private class Vue(
    val etat: EtatAppairage,
    val telephones: List<TelephoneAppaire>,
    val connectes: Set<String>,
) {
    /** Grande ligne : le code de secours, ou l'état. */
    val code: String
        get() = when (etat) {
            is EtatAppairage.EnCours -> etat.code
            is EtatAppairage.Reussi -> "Appairé"
            is EtatAppairage.Expire -> "Nouveau code…"
            EtatAppairage.Inactif -> "Préparation…"
        }

    /** Petite ligne sous le code. */
    val aide: String
        get() = when (etat) {
            is EtatAppairage.Reussi -> etat.nom
            is EtatAppairage.Expire -> "Après la confirmation"
            else -> "Scannez avec votre téléphone"
        }

    val codeLisible: Boolean get() = etat is EtatAppairage.EnCours
}

/** Texte en unités d'interface non entières (10.5f.us). */
private val Float.us: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).sp

private val VertConnecte = Color(0xFF3DDC84)
private val VertCoche = Color(0xFF1B8A4A)

// ------------------------------------------------------------------ éléments communs

/**
 * QR code dans un cadre blanc arrondi (côté [cote], unités .u) : noir sur blanc et zone de
 * silence quel que soit le design. Liseré discret pour le délimiter sur un design clair.
 */
@Composable
private fun CadreQr(etat: EtatAppairage, cote: Float) {
    val style = LocalStyleModule.current
    val forme = RoundedCornerShape(5.u)
    Box(
        Modifier
            .size(cote.u)
            .background(Color.White, forme)
            .border(1.u, style.filet, forme)
            .padding(1.5f.u),
        contentAlignment = Alignment.Center,
    ) {
        when (etat) {
            is EtatAppairage.EnCours -> QrCodeImage(etat.url, Modifier.fillMaxSize().testTag("telecommande:qr"), NIVEAU_QR_MODULE)
            is EtatAppairage.Reussi -> Text(
                "✓", color = VertCoche, fontSize = (cote * 0.42f).us, style = ligne((cote * 0.5f).us),
                modifier = Modifier.testTag("telecommande:reussi"),
            )
            else -> Text("…", color = Color(0xFF555555), fontSize = (cote * 0.2f).us, style = ligne((cote * 0.26f).us))
        }
    }
}

/** Une ligne de texte de hauteur fixe [hauteur] (jamais coupée en hauteur). */
@Composable
private fun Ligne(
    texte: String,
    taille: Float,
    hauteur: Float,
    couleur: Color,
    modifier: Modifier = Modifier,
    poids: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    espacement: Float = 0f,
    lignes: Int = 1,
    centre: Boolean = false,
) {
    Text(
        texte,
        color = couleur,
        fontSize = taille.us,
        fontWeight = poids,
        fontFamily = if (mono) FontFamily.Monospace else null,
        letterSpacing = espacement.us,
        style = ligne(hauteur.us),
        maxLines = lignes,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (centre) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

/** « TÉLÉCOMMANDE » et, quand le module a le focus (et la place), « OK : en grand ». */
@Composable
private fun Entete(ctx: ModuleContexte, k: Float, avecOk: Boolean) {
    val style = ctx.style
    Row(Modifier.fillMaxWidth().height((11f * k).u), verticalAlignment = Alignment.CenterVertically) {
        Ligne(
            "TÉLÉCOMMANDE", 8.5f * k, 11f * k, style.texteDiscret, Modifier.weight(1f),
            poids = FontWeight.Bold, espacement = 1.5f * k,
        )
        if (ctx.aLeFocus && avecOk) Ligne("OK : en grand", 8.5f * k, 11f * k, style.texteDiscret)
    }
}

/** Téléphones appairés sur [lignes] lignes au plus (pastille verte : connecté). */
@Composable
private fun ListeTelephones(v: Vue, lignes: Int, k: Float, avecEtat: Boolean) {
    val style = LocalStyleModule.current
    val h = 13f * k
    if (lignes <= 0) return
    if (v.telephones.isEmpty()) {
        Ligne("Aucun téléphone appairé", 10f * k, h, style.texteDiscret)
        return
    }
    // Connectés d'abord
    val tries = v.telephones.sortedByDescending { it.id in v.connectes }
    val visibles = if (tries.size <= lignes) tries else tries.take(lignes - 1)
    for (t in visibles) {
        val connecte = t.id in v.connectes
        Row(Modifier.fillMaxWidth().height(h.u).testTag("telecommande:tel"), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size((6f * k).u)
                    .then(
                        if (connecte) Modifier.background(VertConnecte, CircleShape)
                        else Modifier.border(1.u, style.texteDiscret, CircleShape)
                    ),
            )
            Spacer(Modifier.width((5f * k).u))
            Ligne(t.nom, 10f * k, h, style.texte, Modifier.weight(1f, fill = false))
            if (connecte && avecEtat) Ligne(" · connecté", 9f * k, h, style.texteDiscret)
        }
    }
    if (visibles.size < tries.size) {
        val reste = tries.size - visibles.size
        Ligne("+ $reste autre" + if (reste > 1) "s" else "", 9f * k, h, style.texteDiscret)
    }
}

// ------------------------------------------------------------------ PETIT

/** QR code au-dessus du code de secours et de « Scannez avec votre téléphone ». */
@Composable
private fun TelecommandePetit(v: Vue, ctx: ModuleContexte) {
    val style = ctx.style
    val margeH = 4f
    val margeV = 0.5f
    val hCode = 14f
    val hAide = 9.5f
    val ecart = 1f
    // 1 u de réserve : l'arrondi au pixel ne doit pas rogner la dernière ligne
    val reserve = 1f
    val cote = minOf(ctx.largeur - 2 * margeH, ctx.hauteur - 2 * margeV - hCode - hAide - ecart - reserve).coerceAtLeast(40f)
    Column(
        Modifier.fillMaxSize().padding(horizontal = margeH.u, vertical = margeV.u),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CadreQr(v.etat, cote)
        Spacer(Modifier.height(ecart.u))
        Ligne(
            v.code, if (v.codeLisible) 13.5f else 11f, hCode, style.texte, Modifier.testTag("telecommande:code"),
            poids = FontWeight.SemiBold, mono = v.codeLisible, espacement = if (v.codeLisible) 0.5f else 0f, centre = true,
        )
        Ligne(v.aide, 7.5f, hAide, style.texteDiscret, centre = true)
    }
}

// ------------------------------------------------------------------ MOYEN (et GRAND en largeur)

/**
 * QR code à gauche (toute la hauteur) ; à droite : « Scannez avec votre téléphone », le code
 * de secours, puis les téléphones appairés. [k] agrandit les textes (GRAND).
 */
@Composable
private fun TelecommandeHorizontal(v: Vue, ctx: ModuleContexte, k: Float) {
    val style = ctx.style
    val marge = 3f
    val ecart = 10f * k
    val colonneMin = 118f * k
    val cote = minOf(ctx.hauteur - 2 * marge, ctx.largeur - 2 * marge - ecart - colonneMin, 260f).coerceAtLeast(60f)
    val colonne = ctx.largeur - 2 * marge - ecart - cote
    val hauteur = ctx.hauteur - 2 * marge
    // Hauteurs des lignes de la colonne
    val lignesAide = if (colonne < 165f * k) 2 else 1
    val hAide = 14f * k
    val hCode = 21f * k
    val avecUrl = colonne >= 250f
    val fixe = 11f * k + 3f * k + hAide * lignesAide + 4f * k + 10f * k + hCode + (if (avecUrl) 24f * k else 0f)
    val separation = 8f * k
    val lignesTel = ((hauteur - fixe - separation) / (13f * k)).toInt().coerceIn(0, 8)
    Row(Modifier.fillMaxSize().padding(marge.u), verticalAlignment = Alignment.CenterVertically) {
        CadreQr(v.etat, cote)
        Spacer(Modifier.width(ecart.u))
        Column(Modifier.width(colonne.u), verticalArrangement = Arrangement.Center) {
            Entete(ctx, k, avecOk = colonne >= 165f * k)
            Spacer(Modifier.height((3f * k).u))
            Ligne(
                v.aide, 10.5f * k, hAide, style.texte, poids = FontWeight.Medium, lignes = lignesAide,
            )
            Spacer(Modifier.height((4f * k).u))
            Ligne(if (v.codeLisible) "Code de secours" else "Code", 8.5f * k, 10f * k, style.texteDiscret)
            Ligne(
                v.code, (if (v.codeLisible) 17f else 13f) * k, hCode, style.texte, Modifier.testTag("telecommande:code"),
                poids = FontWeight.SemiBold, mono = v.codeLisible, espacement = if (v.codeLisible) 1f * k else 0f,
            )
            if (avecUrl) {
                Ligne("à saisir sur", 8.5f * k, 11f * k, style.texteDiscret)
                Ligne(RemoteConfig.URL_PWA_COURTE, 9.5f * k, 13f * k, style.texte)
            }
            if (lignesTel > 0) {
                Spacer(Modifier.height((4f * k).u))
                Box(Modifier.fillMaxWidth().height(1.u).background(style.filet))
                Spacer(Modifier.height((3f * k).u))
                ListeTelephones(v, lignesTel, k, avecEtat = colonne >= 175f * k)
            }
        }
    }
}

// ------------------------------------------------------------------ GRAND (en hauteur)

/** Grand QR code centré, code de secours, adresse de la télécommande et téléphones appairés. */
@Composable
private fun TelecommandeGrandVertical(v: Vue, ctx: ModuleContexte) {
    val style = ctx.style
    val marge = 4f
    val k = 1.15f
    val hCode = 28f
    val fixe = 11f * k + 6f + 6f + hCode + 16f + 12f + 13f + 8f + 1f + 5f
    val ligneTel = 13f * k
    val cote = minOf(ctx.largeur - 2 * marge, ctx.hauteur - 2 * marge - fixe - 4 * ligneTel, 240f).coerceAtLeast(60f)
    val lignesTel = ((ctx.hauteur - 2 * marge - fixe - cote) / ligneTel).toInt().coerceIn(0, 8)
    Column(Modifier.fillMaxSize().padding(marge.u), horizontalAlignment = Alignment.CenterHorizontally) {
        Entete(ctx, k, avecOk = true)
        Spacer(Modifier.height(6.u))
        CadreQr(v.etat, cote)
        Spacer(Modifier.height(6.u))
        Ligne(
            v.code, if (v.codeLisible) 23f else 16f, hCode, style.texte, Modifier.testTag("telecommande:code"),
            poids = FontWeight.SemiBold, mono = v.codeLisible, espacement = if (v.codeLisible) 1.5f else 0f, centre = true,
        )
        Ligne(v.aide, 12f, 16f, style.texte, poids = FontWeight.Medium, centre = true)
        Ligne("ou saisissez le code sur", 9f, 12f, style.texteDiscret, centre = true)
        Ligne(RemoteConfig.URL_PWA_COURTE, 10f, 13f, style.texte, centre = true)
        if (lignesTel > 0) {
            Spacer(Modifier.height(8.u))
            Box(Modifier.fillMaxWidth().height(1.u).background(style.filet))
            Spacer(Modifier.height(5.u))
            Column(Modifier.fillMaxWidth()) { ListeTelephones(v, lignesTel, k, avecEtat = true) }
        }
    }
}
