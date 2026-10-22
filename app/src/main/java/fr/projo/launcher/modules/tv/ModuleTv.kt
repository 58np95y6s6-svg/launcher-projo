package fr.projo.launcher.modules.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.LigneListe
import fr.projo.launcher.ui.ListeDialog
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import fr.projo.launcher.ui.SurfaceCliquable

/** Hauteur de l'en-tête « CE SOIR À LA TV » (unités .u). */
private const val ENTETE = 16

/**
 * Module « Ce soir à la TV » : la première partie de soirée (vers 21 h) des grandes chaînes
 * françaises cochées dans ses réglages, d'après le guide XMLTV public de xmltvfr.fr (voir
 * [SoireeTv]). Une ligne par chaîne (pastille, heure, titre), à faire défiler avec ▲ ▼ :
 *  - MOYEN : 4 chaînes visibles ; PETIT : 2 (titre sur deux lignes) ; GRAND : toutes, avec le genre.
 */
object ModuleTv : Module(
    id = "tv",
    nom = "Ce soir à la TV",
    description = "La première partie de soirée des grandes chaînes",
    icone = IconeTv,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    private const val CLE_CHAINES = "chaines"

    fun chaines(r: ReglagesModule): List<ChaineTv> {
        val ids = r.liste(CLE_CHAINES, CHAINES_PAR_DEFAUT)
        return CHAINES_TV.filter { it.id in ids }.ifEmpty { CHAINES_TV.filter { it.id in CHAINES_PAR_DEFAUT } }
    }

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        // Soirée « du jour » jusqu'à 4 h du matin ; vérifiée toutes les 10 minutes
        var jour by remember { mutableStateOf(SoireeTv.jour(System.currentTimeMillis() - 4 * 3_600_000L)) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(10 * 60_000L)
                jour = SoireeTv.jour(System.currentTimeMillis() - 4 * 3_600_000L)
            }
        }
        val etat = ctx.rememberDonnees(
            "tv:$jour",
            delai = { s: SoireeTvDonnees -> if (s.programmes.isEmpty()) 2 * 3_600_000L else 6 * 3_600_000L },
            delaiErreur = 20 * 60_000L,
        ) { SoireeTv.charger(context, jour) }

        val chaines = chaines(ctx.reglages)
        val parChaine = etat.valeur?.programmes.orEmpty().associateBy { it.chaine }
        val lignes = chaines.mapNotNull { c -> parChaine[c.id]?.let { c to it } }
        val style = ctx.style

        Column(modifier.height(ctx.hauteur.u)) {
            Row(Modifier.fillMaxWidth().height(ENTETE.u).padding(horizontal = 6.u), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ctx.taille == TailleModule.PETIT) "CE SOIR" else "CE SOIR À LA TV",
                    color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
                    fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (ctx.taille != TailleModule.PETIT) {
                    Text(
                        SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(Date()).replace(".", "").lowercase(Locale.FRENCH),
                        color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1,
                    )
                }
            }
            if (lignes.isEmpty()) {
                val msg = when {
                    etat.chargement -> "Programme TV…"
                    etat.erreur && etat.valeur == null -> "Programme TV indisponible"
                    else -> "Pas de programme trouvé pour ce soir"
                }
                CarteModule(ctx, Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        msg, color = style.texteDiscret, fontSize = 11.us, style = ligne(14.us),
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(6.u),
                    )
                }
            } else {
                val hLigne = when (ctx.taille) {
                    TailleModule.PETIT -> 50
                    TailleModule.MOYEN -> 27
                    TailleModule.GRAND -> 38
                }
                val visibles = ((ctx.hauteur - ENTETE) / hLigne).toInt().coerceAtLeast(1)
                LazyColumn(Modifier.fillMaxWidth().height((hLigne * minOf(visibles, lignes.size)).u)) {
                    itemsIndexed(lignes, key = { _, l -> l.first.id }) { i, (c, p) ->
                        LigneProgramme(
                            c, p, ctx.taille, hLigne,
                            if (i == 0) Modifier.focusRequester(ctx.entree) else Modifier,
                        )
                    }
                }
            }
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun resumeReglages(reglages: ReglagesModule): String {
        val c = chaines(reglages)
        return if (c.size <= 3) c.joinToString(", ") { it.nom } else "${c.size} chaînes"
    }

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        val choisies = chaines(reglages).map { it.id }
        val lignes = buildList {
            add(LigneListe("CHAÎNES", section = true))
            for (c in CHAINES_TV) {
                add(LigneListe(c.nom, coche = c.id in choisies) {
                    val coche = c.id in choisies
                    val nouvelles = CHAINES_TV.map { it.id }.filter { if (it == c.id) !coche else it in choisies }
                    // Au moins une chaîne reste cochée
                    if (nouvelles.isNotEmpty()) reglages.mettreListe(CLE_CHAINES, nouvelles)
                })
            }
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Ce soir à la TV",
            sousTitre = "Coche les chaînes à afficher",
            lignes = lignes,
            onDismiss = fermer,
        )
    }
}

private fun heure(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

/** Une chaîne : pastille, heure et titre (et genre en GRAND). Focalisable pour défiler. */
@Composable
private fun LigneProgramme(c: ChaineTv, p: ProgrammeTv, taille: TailleModule, hauteur: Int, modifier: Modifier) {
    val style = LocalStyleModule.current
    val forme = RoundedCornerShape(9.u)
    SurfaceCliquable(
        onClick = {},
        modifier = modifier.fillMaxWidth().height(hauteur.u),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = style.texte,
            focusedContainerColor = style.focusFond,
            focusedContentColor = style.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        val discret = LocalContentColor.current.copy(alpha = 0.7f)
        when (taille) {
            TailleModule.PETIT -> Column(
                Modifier.fillMaxSize().padding(horizontal = 6.u),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(Modifier.height(18.u), verticalAlignment = Alignment.CenterVertically) {
                    Pastille(c)
                    Spacer(Modifier.width(6.u))
                    Text(heure(p.debut), fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 1)
                }
                Spacer(Modifier.height(2.u))
                Text(
                    p.titre, fontSize = 11.us, style = ligne(14.us), maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TailleModule.MOYEN -> Row(
                Modifier.fillMaxSize().padding(horizontal = 6.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pastille(c)
                Spacer(Modifier.width(7.u))
                Text(
                    heure(p.debut), fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us),
                    maxLines = 1, softWrap = false, modifier = Modifier.width(34.u),
                )
                Text(
                    p.titre, fontSize = 12.us, fontWeight = FontWeight.Medium, style = ligne(15.us),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            TailleModule.GRAND -> Row(
                Modifier.fillMaxSize().padding(horizontal = 6.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pastille(c)
                Spacer(Modifier.width(7.u))
                Text(
                    heure(p.debut), fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us),
                    maxLines = 1, softWrap = false, modifier = Modifier.width(34.u),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        p.titre, fontSize = 12.us, fontWeight = FontWeight.Medium, style = ligne(15.us),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val detail = listOfNotNull(p.genre, p.sousTitre).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            detail, color = discret, fontSize = 10.us, style = ligne(13.us),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Pastille de la chaîne : nom court cerclé, à la couleur du texte (suit le focus). */
@Composable
private fun Pastille(c: ChaineTv) {
    val couleur = LocalContentColor.current
    Box(
        Modifier
            .width(38.u)
            .height(17.u)
            .border(1.u, couleur.copy(alpha = 0.55f), RoundedCornerShape(5.u)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            c.court, fontSize = if (c.court.length > 4) 8.us else 9.us, fontWeight = FontWeight.Bold,
            style = ligne(11.us), maxLines = 1, softWrap = false,
        )
    }
}

/** Icône du module : un téléviseur avec ses antennes. */
private val IconeTv: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M4.5,7.5H19.5A1.5,1.5 0 0,1 21,9V18.5A1.5,1.5 0 0,1 19.5,20H4.5A1.5,1.5 0 0,1 3,18.5V9A1.5,1.5 0 0,1 4.5,7.5Z" +
                    " M8,3L12,7.2L16,3",
            ).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}
