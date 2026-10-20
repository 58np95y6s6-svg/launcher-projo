package fr.projo.launcher.modules.appareil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Clé des données (rafraîchies toutes les 30 s tant que l'accueil est affiché). */
const val CLE_APPAREIL = "appareil:infos"

/**
 * Module État de l'appareil : Wi-Fi (nom et qualité du signal), adresse IP, stockage et
 * mémoire libres, température du processeur (si lisible, sinon la version d'Android) et
 * version d'iBiProjo. OK sur le module lance un test de débit léger (5 Mo au plus).
 */
object ModuleAppareil : Module(
    id = "appareil",
    nom = "État de l'appareil",
    description = "Wi-Fi, adresse IP, stockage, mémoire, température, débit",
    icone = IconeAppareil,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        val etat = ctx.rememberDonnees(CLE_APPAREIL, delai = { _: InfosAppareil -> 30_000L }, delaiErreur = 30_000L) {
            LecteurAppareil.lire(context)
        }
        val debit by TestDebit.flux.collectAsState()
        val scope = rememberCoroutineScope()
        val lancerTest: () -> Unit = { if (!TestDebit.enCours) scope.launch { TestDebit.mesurer() } }
        val style = ctx.style
        CarteModule(ctx, modifier.height(ctx.hauteur.u), onClick = lancerTest) {
            val infos = etat.valeur
            if (infos == null) {
                Text(
                    if (etat.erreur) "État indisponible" else "Lecture…", color = style.texteDiscret,
                    fontSize = 12.us, style = ligne(16.us), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val ligneDebit = texteDebit(debit, ctx.aLeFocus)
                when (ctx.taille) {
                    TailleModule.PETIT -> AppareilPetit(infos, ligneDebit)
                    else -> AppareilMoyen(infos, ligneDebit)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ textes

private fun texteDebit(e: EtatDebit, focus: Boolean): String = when (e) {
    is EtatDebit.EnCours -> "Test… " + (e.progression * 100).roundToInt() + " %"
    is EtatDebit.Resultat -> formaterDebit(e.mbps) + if (focus) " · OK" else ""
    EtatDebit.Erreur -> "Échec · OK"
    EtatDebit.Inactif -> if (focus) "OK pour tester" else "Non mesuré"
}

fun formaterDebit(mbps: Double): String =
    if (mbps >= 100) "${mbps.roundToInt()} Mb/s"
    else String.format(Locale.US, "%.1f", mbps).replace('.', ',') + " Mb/s"

/** 13 300 000 000 → « 12,4 Go » ; 432 000 000 → « 412 Mo ». */
fun formaterOctets(o: Long): String {
    if (o <= 0L) return "?"
    val go = o / (1024.0 * 1024.0 * 1024.0)
    return if (go >= 1) {
        (if (go >= 100) go.roundToInt().toString() else String.format(Locale.US, "%.1f", go).replace('.', ',')) + " Go"
    } else {
        "${(o / (1024.0 * 1024.0)).roundToInt()} Mo"
    }
}

private fun texteSignal(i: InfosAppareil): String = when (i.signal) {
    4 -> "excellent"
    3 -> "bon"
    2 -> "moyen"
    1, 0 -> "faible"
    else -> ""
}

private fun texteTemperature(t: Double) = "${t.roundToInt()} °C"

// ------------------------------------------------------------------ rendus

@Composable
private fun AppareilMoyen(i: InfosAppareil, debit: String) {
    val style = LocalStyleModule.current
    Column(Modifier.fillMaxSize().padding(horizontal = 10.u, vertical = 6.u), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "APPAREIL", color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
                fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1, modifier = Modifier.weight(1f),
            )
            Text(
                "iBiProjo " + i.version, color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Case("WI-FI", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i.wifi && i.signal >= 0) {
                        Barres(i.signal, Modifier.size(15.u, 11.u))
                        Spacer(Modifier.width(5.u))
                    }
                    Valeur(i.reseau + if (i.wifi && i.signal >= 0) " · " + texteSignal(i) else "")
                }
            }
            Spacer(Modifier.width(10.u))
            Case("ADRESSE IP", Modifier.width(112.u)) { Valeur(i.ip ?: "—") }
        }
        Row(Modifier.fillMaxWidth()) {
            Case("STOCKAGE LIBRE", Modifier.weight(1f)) {
                Valeur(formaterOctets(i.stockageLibre) + " / " + formaterOctets(i.stockageTotal))
            }
            Spacer(Modifier.width(10.u))
            Case("MÉMOIRE LIBRE", Modifier.width(112.u)) {
                Valeur(formaterOctets(i.memoireLibre) + " / " + formaterOctets(i.memoireTotale))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val t = i.temperature
            Case(if (t != null) "TEMPÉRATURE" else "SYSTÈME", Modifier.weight(1f)) {
                Valeur(if (t != null) texteTemperature(t) else i.android)
            }
            Spacer(Modifier.width(10.u))
            Case("DÉBIT", Modifier.width(112.u)) { Valeur(debit) }
        }
    }
}

@Composable
private fun AppareilPetit(i: InfosAppareil, debit: String) {
    val style = LocalStyleModule.current
    Column(Modifier.fillMaxSize().padding(horizontal = 7.u, vertical = 5.u), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "APPAREIL", color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
                fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1, modifier = Modifier.weight(1f),
            )
            Text(i.version, color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LignePetite(if (i.wifi) "Wi-Fi" else "Réseau") {
            if (i.wifi && i.signal >= 0) {
                Barres(i.signal, Modifier.size(13.u, 10.u))
                Spacer(Modifier.width(4.u))
            }
            Valeur(i.reseau, petit = true)
        }
        LignePetite("IP") { Valeur(i.ip ?: "—", petit = true) }
        LignePetite("Stock.") { Valeur(formaterOctets(i.stockageLibre) + " libres", petit = true) }
        LignePetite("RAM") { Valeur(formaterOctets(i.memoireLibre) + " libres", petit = true) }
        val t = i.temperature
        LignePetite(if (t != null) "Temp." else "Syst.") { Valeur(if (t != null) texteTemperature(t) else i.android, petit = true) }
        LignePetite("Débit") { Valeur(debit, petit = true) }
    }
}

@Composable
private fun Case(titre: String, modifier: Modifier, contenu: @Composable () -> Unit) {
    val style = LocalStyleModule.current
    Column(modifier) {
        Text(
            titre, color = style.texteDiscret, fontSize = 8.us, letterSpacing = 1.us, fontWeight = FontWeight.Bold,
            style = ligne(11.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.u))
        contenu()
    }
}

@Composable
private fun LignePetite(titre: String, contenu: @Composable () -> Unit) {
    val style = LocalStyleModule.current
    Row(Modifier.fillMaxWidth().height(15.u), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titre, color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us), maxLines = 1,
            modifier = Modifier.width(38.u),
        )
        contenu()
    }
}

@Composable
private fun Valeur(texte: String, petit: Boolean = false) {
    Text(
        texte, color = LocalStyleModule.current.texte, fontSize = (if (petit) 11 else 12).us,
        fontWeight = FontWeight.Medium, style = ligne((if (petit) 14 else 15).us), maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Barres de signal Wi-Fi (4), pleines selon [niveau] (0 à 4). */
@Composable
private fun Barres(niveau: Int, modifier: Modifier) {
    val style = LocalStyleModule.current
    val plein = style.texte
    val vide = style.filet
    Canvas(modifier) {
        val n = 4
        val ecart = size.width * 0.12f
        val l = (size.width - ecart * (n - 1)) / n
        for (k in 0 until n) {
            val h = size.height * (k + 1) / n
            drawRoundRect(
                if (k < niveau) plein else vide,
                topLeft = Offset(k * (l + ecart), size.height - h),
                size = Size(l, h),
                cornerRadius = CornerRadius(l / 3, l / 3),
            )
        }
    }
}

/** Icône du module : puce (24 × 24, blanc). */
val IconeAppareil: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M7,6H17A1,1 0 0,1 18,7V17A1,1 0 0,1 17,18H7A1,1 0 0,1 6,17V7A1,1 0 0,1 7,6Z M10,10H14V14H10Z " +
                "M9,3V6 M15,3V6 M9,18V21 M15,18V21 M3,9H6 M3,15H6 M18,9H21 M18,15H21"
        ).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
