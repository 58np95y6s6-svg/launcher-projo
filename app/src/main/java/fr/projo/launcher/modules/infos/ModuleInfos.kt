package fr.projo.launcher.modules.infos

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.LigneListe
import fr.projo.launcher.ui.ListeDialog
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ENTETE = 16
/** Nombre de titres gardés pour l'alternance. */
private const val TITRES_MAX = 12

/**
 * Module « Infos » : les derniers titres des flux RSS cochés dans ses réglages (franceinfo,
 * Le Monde, L'Équipe, 20 Minutes), du plus récent au plus ancien, qui alternent en fondu :
 *  - MOYEN : 3 titres à la fois ; PETIT : 1 titre ; GRAND : autant que la hauteur le permet.
 * Source et heure en discret. OK passe aux titres suivants. Flux relus toutes les 20 minutes.
 */
object ModuleInfos : Module(
    id = "infos",
    nom = "Infos",
    description = "Les derniers titres : franceinfo, Le Monde, L'Équipe…",
    icone = IconeInfos,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    private const val CLE_SOURCES = "sources"

    fun sources(r: ReglagesModule): List<SourceInfos> {
        val ids = r.liste(CLE_SOURCES, SOURCES_INFOS_PAR_DEFAUT)
        return SOURCES_INFOS.filter { it.id in ids }.ifEmpty { listOf(SOURCES_INFOS[0]) }
    }

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val sources = sources(ctx.reglages)
        val etats: List<EtatDonnees<TitresSource>> = sources.map { s ->
            key(s.id) {
                ctx.rememberDonnees("infos:${s.id}", delai = { _: TitresSource -> 20 * 60_000L }, delaiErreur = 5 * 60_000L) {
                    InfosReseau.charger(s)
                }
            }
        }
        val titres = fusionner(etats.mapNotNull { it.valeur })
        val style = ctx.style
        val parPage = when (ctx.taille) {
            TailleModule.PETIT -> 1
            TailleModule.MOYEN -> 3
            TailleModule.GRAND -> ((ctx.hauteur - ENTETE) / 40f).toInt().coerceAtLeast(3)
        }
        val pages = if (titres.isEmpty()) 1 else (titres.size + parPage - 1) / parPage
        var compteur by rememberSaveable { mutableIntStateOf(0) }
        val page = compteur % pages

        // Alternance douce, seulement pendant que l'accueil est affiché
        val cycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(pages, cycle) {
            if (pages <= 1) return@LaunchedEffect
            cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(if (parPage == 1) 9_000L else 12_000L)
                    compteur += 1
                }
            }
        }

        CarteModule(ctx, modifier.height(ctx.hauteur.u), onClick = { compteur += 1 }) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(ENTETE.u).padding(horizontal = 6.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "INFOS", color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
                        fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (pages > 1) {
                        Text(
                            "${page + 1}/$pages", color = style.texteDiscret, fontSize = 9.us,
                            style = ligne(12.us), maxLines = 1,
                        )
                    }
                }
                if (titres.isEmpty()) {
                    val msg = when {
                        etats.any { it.chargement } -> "Infos…"
                        else -> "Infos indisponibles"
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = ENTETE.u), contentAlignment = Alignment.Center) {
                        Text(
                            msg, color = style.texteDiscret, fontSize = 11.us, style = ligne(14.us),
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                } else {
                    Crossfade(targetState = page, animationSpec = tween(700), label = "infos") { p ->
                        val morceau = titres.drop(p * parPage).take(parPage)
                        if (morceau.isEmpty()) {
                            Spacer(Modifier.fillMaxSize())
                        } else if (parPage == 1) {
                            TitreSeul(morceau.first(), style)
                        } else {
                            val h = (ctx.hauteur - ENTETE - 4) / parPage
                            Column(Modifier.fillMaxWidth().padding(top = 2.u)) {
                                morceau.forEach { LigneTitre(it, style, h) }
                            }
                        }
                    }
                }
            }
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun resumeReglages(reglages: ReglagesModule): String = sources(reglages).joinToString(", ") { it.nom }

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        val choisies = sources(reglages).map { it.id }
        val lignes = buildList {
            add(LigneListe("SOURCES", section = true))
            for (s in SOURCES_INFOS) {
                add(LigneListe(s.nom, coche = s.id in choisies) {
                    val coche = s.id in choisies
                    val nouvelles = SOURCES_INFOS.map { it.id }.filter { if (it == s.id) !coche else it in choisies }
                    // Au moins une source reste cochée
                    if (nouvelles.isNotEmpty()) reglages.mettreListe(CLE_SOURCES, nouvelles)
                })
            }
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Infos",
            sousTitre = "Coche les sources des titres",
            lignes = lignes,
            onDismiss = fermer,
        )
    }

    /** Titres de toutes les sources, du plus récent au plus ancien (sans date : à la fin). */
    fun fusionner(listes: List<TitresSource>): List<TitreInfo> =
        listes.flatMap { it.titres }
            .distinctBy { it.titre.lowercase(Locale.FRENCH) }
            .sortedByDescending { it.date ?: Long.MIN_VALUE }
            .take(TITRES_MAX)
}

private fun nomSource(id: String): String = SOURCES_INFOS.firstOrNull { it.id == id }?.court ?: id

/** « 18:05 » aujourd'hui, « hier », sinon « lun. ». */
private fun quand(t: Long?): String? {
    if (t == null) return null
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = t }
    val jours = (a.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 366 + a.get(Calendar.DAY_OF_YEAR) - b.get(Calendar.DAY_OF_YEAR)
    return when (jours) {
        0 -> SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))
        1 -> "hier"
        else -> SimpleDateFormat("EEE", Locale.FRENCH).format(Date(t))
    }
}

/** MOYEN / GRAND : heure et source dans la marge, titre sur deux lignes. */
@Composable
private fun LigneTitre(t: TitreInfo, style: NbaStyle, hauteur: Float) {
    Row(Modifier.fillMaxWidth().height(hauteur.u).padding(horizontal = 6.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(44.u)) {
            quand(t.date)?.let {
                Text(it, color = style.texteDiscret, fontSize = 10.us, fontWeight = FontWeight.SemiBold, style = ligne(13.us), maxLines = 1)
            }
            Text(
                nomSource(t.source), color = style.texteDiscret, fontSize = 8.us, style = ligne(11.us),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.u))
        Text(
            t.titre, color = style.texte, fontSize = 12.us, fontWeight = FontWeight.Medium, style = ligne(15.us),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

/** PETIT : un seul titre, sur quatre lignes au plus, source et heure dessous. */
@Composable
private fun TitreSeul(t: TitreInfo, style: NbaStyle) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.u, vertical = 4.u), verticalArrangement = Arrangement.Center) {
        Text(
            t.titre, color = style.texte, fontSize = 12.us, fontWeight = FontWeight.Medium, style = ligne(15.us),
            maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.u))
        Text(
            listOfNotNull(nomSource(t.source), quand(t.date)).joinToString(" · "),
            color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Icône du module : un journal plié. */
private val IconeInfos: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M5,4H17V18A2,2 0 0,0 19,20H6A2,2 0 0,1 4,18V5A1,1 0 0,1 5,4Z M17,9H20V18A2,2 0 0,1 18,20" +
                    " M7.5,8H13.5 M7.5,11.5H13.5 M7.5,15H11",
            ).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}
