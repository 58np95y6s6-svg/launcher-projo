package fr.projo.launcher.modules.bourse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.LigneListe
import fr.projo.launcher.ui.ListeDialog
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import java.util.Locale
import kotlin.math.abs

private const val MIN_COURS = 2
private const val MAX_COURS = 4
private const val CLE_COURS = "cours"
private const val CLE_DEVISE = "devise"

/** Délai entre deux chargements : 10 min (limites de débit de CoinGecko et Yahoo). */
private const val DELAI = 10 * 60_000L
private const val DELAI_ERREUR = 3 * 60_000L

/**
 * Module Crypto et bourse : 2 à 4 cours choisis dans ses réglages (cryptos via CoinGecko,
 * indices et actions via Yahoo Finance), avec le prix, la variation (vert / rouge) et une
 * mini-courbe. Devise des cryptos : euro par défaut (ou dollar). Données gardées 10 min.
 */
object ModuleBourse : Module(
    id = "bourse",
    nom = "Crypto et bourse",
    description = "Bitcoin, CAC 40, S&P 500… : prix, variation et courbe",
    icone = IconeBourse,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    fun choisis(r: ReglagesModule): List<Instrument> {
        val ids = r.liste(CLE_COURS, INSTRUMENTS_PAR_DEFAUT)
        val liste = ids.mapNotNull { id -> INSTRUMENTS.firstOrNull { it.id == id } }.distinctBy { it.id }.take(MAX_COURS)
        return if (liste.size >= MIN_COURS) liste
        else (liste + INSTRUMENTS_PAR_DEFAUT.mapNotNull { id -> INSTRUMENTS.firstOrNull { it.id == id } })
            .distinctBy { it.id }.take(maxOf(MIN_COURS, liste.size))
    }

    fun devise(r: ReglagesModule): String = if (r.texte(CLE_DEVISE) == "usd") "usd" else "eur"

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val instruments = choisis(ctx.reglages)
        val devise = devise(ctx.reglages)
        val idsCryptos = instruments.filter { it.source == SourceCours.CRYPTO }.map { it.code }
        val cryptos: EtatDonnees<CoursCryptos>? = if (idsCryptos.isEmpty()) null else key("cryptos") {
            ctx.rememberDonnees(BourseReseau.cleCryptos(idsCryptos, devise), delai = { _: CoursCryptos -> DELAI }, delaiErreur = DELAI_ERREUR) {
                BourseReseau.chargerCryptos(idsCryptos, devise)
            }
        }
        val lignes = instruments.map { ins ->
            if (ins.source == SourceCours.CRYPTO) {
                LigneCours(ins, cryptos?.valeur?.cours?.get(ins.code), cryptos?.chargement == true)
            } else {
                val etat = key(ins.id) {
                    ctx.rememberDonnees(BourseReseau.cleYahoo(ins.code), delai = { _: Cours -> DELAI }, delaiErreur = DELAI_ERREUR) {
                        BourseReseau.chargerYahoo(ins.code)
                    }
                }
                LigneCours(ins, etat.valeur, etat.chargement)
            }
        }
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            if (lignes.all { it.chargement }) {
                Text(
                    "Cours…", color = ctx.style.texteDiscret, fontSize = 12.us, style = ligne(16.us),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val petit = ctx.taille == TailleModule.PETIT
                Column(
                    Modifier.fillMaxSize().padding(horizontal = if (petit) 6.u else 8.u, vertical = 4.u),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    lignes.forEachIndexed { i, l ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.u).background(ctx.style.filet))
                        if (petit) LignePetite(l, lignes.size) else LigneMoyenne(l, lignes.size)
                    }
                }
            }
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun resumeReglages(reglages: ReglagesModule): String =
        choisis(reglages).joinToString(", ") { it.court } + if (devise(reglages) == "usd") " · \$" else ""

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        val choisis = choisis(reglages).map { it.id }
        val devise = devise(reglages)
        val lignes = buildList {
            for ((categorie, liste) in INSTRUMENTS.groupBy { it.categorie }) {
                add(LigneListe(categorie, section = true))
                for (ins in liste) {
                    add(LigneListe(ins.nom, if (ins.nom != ins.court) ins.court else null, coche = ins.id in choisis) {
                        val coche = ins.id in choisis
                        val nouveaux = if (coche) choisis - ins.id else choisis + ins.id
                        // Entre 2 et 4 cours : au-delà, la case ne change pas
                        if (nouveaux.size in MIN_COURS..MAX_COURS) reglages.mettreListe(CLE_COURS, nouveaux)
                    })
                }
            }
            add(LigneListe("DEVISE DES CRYPTOS", section = true))
            add(LigneListe("Euro (€)", coche = devise == "eur") { reglages.mettre(CLE_DEVISE, null) })
            add(LigneListe("Dollar (\$)", coche = devise == "usd") { reglages.mettre(CLE_DEVISE, "usd") })
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Crypto et bourse",
            sousTitre = "Coche de $MIN_COURS à $MAX_COURS cours (${choisis.size} choisis)",
            lignes = lignes,
            onDismiss = fermer,
        )
    }
}

/** Une ligne affichée : instrument et son cours (null : indisponible ou en chargement). */
private class LigneCours(val instrument: Instrument, val cours: Cours?, val chargement: Boolean)

// ------------------------------------------------------------------ formats

/** 98432.1 → « 98 432 », 142.35 → « 142,35 », 0.51234 → « 0,5123 » (espaces insécables). */
fun formaterNombre(v: Double): String {
    val decimales = when {
        abs(v) >= 1000 -> 0
        abs(v) >= 1 -> 2
        else -> 4
    }
    return String.format(Locale.US, "%,.${decimales}f", v)
        .replace(',', ' ').replace('.', ',')
}

fun formaterPrix(c: Cours, ins: Instrument): String {
    val n = formaterNombre(c.prix)
    if (ins.indice) return n
    return when (c.devise?.uppercase(Locale.ROOT)) {
        "EUR" -> "$n €"
        "USD" -> "$n \$"
        null, "" -> n
        else -> n + " " + c.devise
    }
}

/** « +2,14 % », « −0,80 % ». */
fun formaterVariation(v: Double): String {
    val signe = if (v >= 0.005) "+" else if (v <= -0.005) "−" else ""
    return signe + String.format(Locale.US, "%.2f", abs(v)).replace('.', ',') + " %"
}

/** Vert / rouge lisibles sur un fond clair (Pastel) comme sombre. */
private fun couleurVariation(style: NbaStyle, v: Double?): Color {
    val clair = style.texte.luminance() < 0.5f
    return when {
        v == null || abs(v) < 0.005 -> style.texteDiscret
        v > 0 -> if (clair) Color(0xFF16803C) else Color(0xFF4ADE80)
        else -> if (clair) Color(0xFFC62828) else Color(0xFFFF6B6B)
    }
}

// ------------------------------------------------------------------ rendus

@Composable
private fun LigneMoyenne(l: LigneCours, n: Int) {
    val style = LocalStyleModule.current
    val grand = n <= 2
    val c = l.cours
    Row(Modifier.fillMaxWidth().height((if (grand) 44 else 26).u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(74.u)) {
            Text(
                l.instrument.court, color = style.texte, fontSize = (if (grand) 15 else 13).us, fontWeight = FontWeight.Bold,
                style = ligne((if (grand) 19 else 16).us), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (grand) {
                Text(
                    l.instrument.nom, color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Courbe(c, Modifier.weight(1f).height((if (grand) 30 else 18).u).padding(horizontal = 6.u))
        Column(Modifier.width(104.u), horizontalAlignment = Alignment.End) {
            if (c == null) {
                Text(
                    if (l.chargement) "…" else "indisponible", color = style.texteDiscret, fontSize = 11.us,
                    style = ligne(14.us), maxLines = 1,
                )
            } else if (grand) {
                Text(
                    formaterPrix(c, l.instrument), color = style.texte, fontSize = 15.us, fontWeight = FontWeight.SemiBold,
                    style = ligne(19.us), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                )
                c.variation?.let {
                    Text(
                        formaterVariation(it), color = couleurVariation(style, it), fontSize = 12.us,
                        fontWeight = FontWeight.SemiBold, style = ligne(15.us), maxLines = 1, softWrap = false,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formaterPrix(c, l.instrument), color = style.texte, fontSize = 12.us, fontWeight = FontWeight.SemiBold,
                        style = ligne(15.us), maxLines = 1, softWrap = false,
                    )
                }
                c.variation?.let {
                    Text(
                        formaterVariation(it), color = couleurVariation(style, it), fontSize = 9.us,
                        fontWeight = FontWeight.SemiBold, style = ligne(11.us), maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun LignePetite(l: LigneCours, n: Int) {
    val style = LocalStyleModule.current
    val c = l.cours
    val hauteur = if (n <= 2) 44 else 25
    Column(Modifier.fillMaxWidth().height(hauteur.u), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                l.instrument.court, color = style.texte, fontSize = 11.us, fontWeight = FontWeight.Bold,
                style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            val v = c?.variation
            if (v != null) {
                Text(
                    formaterVariation(v), color = couleurVariation(style, v), fontSize = 10.us,
                    fontWeight = FontWeight.SemiBold, style = ligne(13.us), maxLines = 1, softWrap = false,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    c != null -> formaterPrix(c, l.instrument)
                    l.chargement -> "…"
                    else -> "indisponible"
                },
                color = style.texteDiscret, fontSize = 10.us, style = ligne(12.us), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Courbe(c, Modifier.width(40.u).height(10.u))
        }
    }
}

/** Mini-courbe : trait vert ou rouge selon la variation, ligne de départ en pointillés. */
@Composable
private fun Courbe(c: Cours?, modifier: Modifier) {
    val style = LocalStyleModule.current
    val points = c?.courbe.orEmpty()
    val couleur = couleurVariation(style, c?.variation ?: if (points.size >= 2) points.last() - points.first() else null)
    val pointilles = style.filet
    val epaisseur = 1.6f.u
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val ecart = (max - min).takeIf { it > 0.0 } ?: 1.0
        val l = size.width
        val h = size.height
        val trait = epaisseur.toPx()
        fun y(v: Double) = (trait / 2 + (h - trait) * (1 - (v - min) / ecart)).toFloat()
        drawLine(
            pointilles, Offset(0f, y(points.first())), Offset(l, y(points.first())), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
        )
        val chemin = Path()
        points.forEachIndexed { i, v ->
            val x = l * i / (points.size - 1)
            if (i == 0) chemin.moveTo(x, y(v)) else chemin.lineTo(x, y(v))
        }
        drawPath(chemin, couleur, style = Stroke(width = trait, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Icône du module : courbe montante dans un repère (24 × 24, blanc). */
val IconeBourse: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString("M3,3V21H21 M6,16L10,11L13,14L19,6 M15,6H19V10").toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
