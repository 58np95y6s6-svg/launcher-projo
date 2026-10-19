package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaNight
import fr.projo.launcher.data.NbaRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Bandeau NBA du design Graphite (maquette t8, px de la maquette / 2 = unités) :
 * panneau à filets 398 × 194, en-tête de 46 (pastille ballon, « NBA », sélecteur segmenté
 * « Cette nuit / Résultats »), puis 3 lignes de 49 : pastille d'équipe en couleur, score en
 * chiffres à chasse fixe, pastille d'équipe, heure (ou pastille rouge « ● QT4 3:12 » en direct).
 */

private const val NBA_G_LARGEUR = 398
private const val NBA_G_ENTETE = 46
private const val NBA_G_LIGNE = 49
private const val NBA_G_LIGNES = 3

private val NbaGEncre = Color(0xFFF4F5F7)
private val NbaGEncre2 = Color(0xFFA3A7B2)
private val NbaGEncre3 = Color(0xFF6C707C)
private val NbaGFilet = Color.White.copy(alpha = 0.075f)
private val NbaGRouge = Color(0xFFEF4444)
private val NbaGOrange = Color(0xFFF97316)

/** Couleurs officielles des équipes (abréviations ESPN et variantes courantes). */
private val COULEURS_EQUIPES: Map<String, Long> = mapOf(
    "ATL" to 0xFFE03A3E, "BOS" to 0xFF007A33, "BKN" to 0xFF3A3D45, "BRK" to 0xFF3A3D45,
    "CHA" to 0xFF1D1160, "CHO" to 0xFF1D1160, "CHI" to 0xFFCE1141, "CLE" to 0xFF860038,
    "DAL" to 0xFF00538C, "DEN" to 0xFF0E2240, "DET" to 0xFFC8102E, "GS" to 0xFF1D428A,
    "GSW" to 0xFF1D428A, "HOU" to 0xFFCE1141, "IND" to 0xFF002D62, "LAC" to 0xFFC8102E,
    "LAL" to 0xFF552583, "MEM" to 0xFF5D76A9, "MIA" to 0xFF98002E, "MIL" to 0xFF00471B,
    "MIN" to 0xFF0C2340, "NO" to 0xFF0C2340, "NOP" to 0xFF0C2340, "NY" to 0xFFF58426,
    "NYK" to 0xFFF58426, "OKC" to 0xFF007AC1, "ORL" to 0xFF0077C0, "PHI" to 0xFF006BB6,
    "PHX" to 0xFF1D1160, "PHO" to 0xFF1D1160, "POR" to 0xFFE03A3E, "SAC" to 0xFF5A2D81,
    "SA" to 0xFF5F6A72, "SAS" to 0xFF5F6A72, "TOR" to 0xFFCE1141, "UTAH" to 0xFF002B5C,
    "UTA" to 0xFF002B5C, "WSH" to 0xFF002B5C, "WAS" to 0xFF002B5C,
)

/** Couleur de la pastille d'une équipe ; les couleurs très sombres sont éclaircies (fond graphite). */
private fun couleurEquipe(abbr: String): Color {
    val c = COULEURS_EQUIPES[abbr.uppercase(Locale.ROOT)]?.let { Color(it) } ?: Color(0xFF4A5060)
    return if (c.luminance() < 0.03f) lerp(c, Color.White, 0.16f) else c
}

/** Anneau net violet → cyan (sans halo) des éléments sélectionnés du bandeau. */
private val NbaGAnneau = Brush.sweepGradient(
    0f to Color(0xFF7C5CFF), 0.153f to Color(0xFF5B6CFF), 0.306f to Color(0xFF3B82F6),
    0.458f to Color(0xFF22D3EE), 0.514f to Color(0xFFD9FBFF), 0.569f to Color(0xFF22D3EE),
    0.708f to Color(0xFF3B82F6), 0.861f to Color(0xFF8B5CF6), 1f to Color(0xFF7C5CFF),
)

/** Fond clair + anneau dégradé dessinés derrière l'élément sélectionné ([debord] : dépassement horizontal). */
private fun Modifier.nbaGSelection(actif: Boolean, rayon: Dp, debord: Dp, epaisseur: Dp): Modifier =
    drawBehind {
        if (!actif) return@drawBehind
        val d = debord.toPx()
        val e = epaisseur.toPx()
        val r = rayon.toPx()
        val coin = CornerRadius(r)
        val origine = Offset(-d, 0f)
        val taille = Size(size.width + 2 * d, size.height)
        drawRoundRect(Color.White.copy(alpha = 0.08f), origine, taille, coin)
        drawRoundRect(
            brush = NbaGAnneau,
            topLeft = Offset(origine.x + e / 2f, e / 2f),
            size = Size(taille.width - e, taille.height - e),
            cornerRadius = CornerRadius(r - e / 2f),
            style = Stroke(e),
        )
    }

@Composable
internal fun NbaGraphite(
    data: NbaData?,
    erreur: Boolean,
    modifier: Modifier,
    onExit: () -> Unit,
    style: NbaStyle,
    entree: FocusRequester?,
) {
    val forme = RoundedCornerShape(11.u)
    var onglet by remember { mutableIntStateOf(0) }
    val focusNuit = remember { FocusRequester() }
    var resultatsFocus by remember { mutableStateOf(false) }
    val un = graphiteUnPixel()
    val margeFilet = 40.u
    Column(
        modifier
            // Retour : on quitte le bandeau
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) { onExit(); true }
                else it.key == Key.Back
            }
            // ◀ n'importe où dans le bandeau : on en sort (seul « Résultats » → « Cette nuit » reste interne)
            .onPreviewKeyEvent {
                if (it.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyDown) {
                    if (resultatsFocus) runCatching { focusNuit.requestFocus() } else onExit()
                }
                true
            }
            .width(NBA_G_LARGEUR.u)
            .height((NBA_G_ENTETE + NBA_G_LIGNE * NBA_G_LIGNES).u + un * 2)
            .graphitePanneau(11.u)
            // Filet violet → cyan sur le bord haut (de 40 à largeur − 40)
            .drawWithCache {
                val m = margeFilet.toPx()
                val filet = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0xFFC4B5FD).copy(alpha = 0.9f),
                    0.7f to Color(0xFF67E8F9).copy(alpha = 0.8f),
                    1f to Color.Transparent,
                    startX = m, endX = size.width - m,
                )
                onDrawWithContent {
                    drawContent()
                    drawLine(filet, Offset(m, 0.5f), Offset(size.width - m, 0.5f), strokeWidth = 1f)
                }
            }
            .padding(horizontal = 16.u)
    ) {
        val nuit = data?.tonight
        val libelleNuit = if (nuit == null || nuit.isTonight) "Cette nuit"
        else NbaRepository.libelleJour(nuit.day).lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

        // --- En-tête : pastille ballon + NBA, sélecteur segmenté à droite
        Row(
            Modifier
                .fillMaxWidth()
                .height(NBA_G_ENTETE.u)
                .drawBehind {
                    drawLine(NbaGFilet, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val formeBallon = RoundedCornerShape((11.u / 2))
            Box(
                Modifier
                    .size(20.u)
                    .background(NbaGOrange.copy(alpha = 0.14f), formeBallon)
                    .border(un, NbaGOrange.copy(alpha = 0.35f), formeBallon),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GraphiteIcones.Ballon, contentDescription = null, tint = Color(0xFFFB923C), modifier = Modifier.size(11.u))
            }
            Spacer(Modifier.width(7.u))
            Text("NBA", color = NbaGEncre, fontSize = 13.us, fontWeight = FontWeight.SemiBold, letterSpacing = (1.us / 10))
            Spacer(Modifier.weight(1f))
            val formeSeg = RoundedCornerShape((13.u / 2))
            Row(
                Modifier
                    .background(Color.Black.copy(alpha = 0.25f), formeSeg)
                    .border(un, NbaGFilet, formeSeg)
                    .padding(2.u)
            ) {
                NbaGOnglet(
                    libelleNuit, onglet == 0,
                    Modifier.focusRequester(focusNuit).then(entree?.let { Modifier.focusRequester(it) } ?: Modifier),
                ) { onglet = 0 }
                NbaGOnglet("Résultats", onglet == 1, Modifier.onFocusChanged { resultatsFocus = it.isFocused }) { onglet = 1 }
            }
        }

        val affichee: NbaNight? = if (onglet == 0) nuit else data?.results
        val jeux = affichee?.games.orEmpty()
        if (jeux.isEmpty()) {
            val msg = when {
                data == null && !erreur -> "Chargement des matchs…"
                data == null -> "Matchs indisponibles (pas de connexion)"
                onglet == 1 -> "Pas de résultat récent"
                else -> "Aucun match NBA cette semaine"
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(msg, color = NbaGEncre3, fontSize = 13.us)
            }
        } else {
            val etat = rememberLazyListState()
            // « Cette nuit » : on montre d'abord les matchs en cours ou à venir
            LaunchedEffect(affichee?.day, onglet) {
                val i = if (onglet == 0) jeux.indexOfFirst { it.state != "post" } else 0
                etat.scrollToItem(maxOf(0, i))
            }
            LazyColumn(state = etat, modifier = Modifier.fillMaxWidth().height((NBA_G_LIGNE * NBA_G_LIGNES).u)) {
                itemsIndexed(jeux, key = { _, g -> g.id }) { i, g ->
                    NbaGLigne(g, derniere = i == jeux.lastIndex)
                }
            }
        }
    }
}

/** Segment du sélecteur : se sélectionne dès qu'on passe dessus (comme les onglets d'Android TV). */
@Composable
private fun NbaGOnglet(texte: String, actif: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focus by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape((9.u / 2))
    val un = graphiteUnPixel()
    Box(
        modifier
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusable()
            .height(23.u)
            .then(
                if (actif) Modifier
                    .background(Color.White.copy(alpha = 0.09f), forme)
                    .border(un, Color.White.copy(alpha = 0.12f), forme)
                else Modifier
            )
            .nbaGSelection(focus, (9.u / 2), 0.u, (3.u / 2))
            .padding(horizontal = 11.u),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = if (actif || focus) NbaGEncre else NbaGEncre3,
            fontSize = (21.us / 2), fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

/** Ligne d'un match : focalisable (c'est ce qui fait défiler la liste à la télécommande). */
@Composable
private fun NbaGLigne(g: NbaGame, derniere: Boolean) {
    var focus by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(NBA_G_LIGNE.u)
            .onFocusChanged { focus = it.isFocused }
            .focusable()
            .drawBehind {
                if (!derniere) drawLine(NbaGFilet, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(41.u)
                .nbaGSelection(focus, 9.u, 6.u, (3.u / 2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbaGEquipe(g.away.abbr)
            // Score (ou tiret) centré dans l'espace libre
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NbaGScore(g) }
            NbaGEquipe(g.home.abbr)
            Box(Modifier.width(115.u), contentAlignment = Alignment.CenterEnd) { NbaGEtat(g) }
        }
    }
}

/** Pastille d'équipe : 42 × 23, couleur de l'équipe, reflet clair en haut, liseré sombre en bas. */
@Composable
private fun NbaGEquipe(abbr: String) {
    val couleur = couleurEquipe(abbr)
    val rayon = 11.u / 2
    val forme = RoundedCornerShape(rayon)
    Box(
        Modifier
            .size(42.u, 23.u)
            .background(couleur, forme)
            .drawBehind {
                val r = rayon.toPx()
                drawLine(Color.White.copy(alpha = 0.22f), Offset(r, 0.5f), Offset(size.width - r, 0.5f), 1f)
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(r, size.height - 0.5f), Offset(size.width - r, size.height - 0.5f), 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(abbr, color = Color.White, fontSize = 10.us, fontWeight = FontWeight.Bold, letterSpacing = (3.us / 10), maxLines = 1)
    }
}

@Composable
private fun NbaGScore(g: NbaGame) {
    if (g.state == "pre") {
        Text("–", color = NbaGEncre3, fontSize = 15.us)
        return
    }
    val fini = g.state == "post"
    Row(verticalAlignment = Alignment.CenterVertically) {
        val c = if (fini) NbaGEncre.copy(alpha = 0.8f) else NbaGEncre
        Text(g.away.score ?: "", color = c, fontSize = 18.us, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("–", color = NbaGEncre3, fontSize = 18.us, modifier = Modifier.padding(horizontal = 5.u))
        Text(g.home.score ?: "", color = c, fontSize = 18.us, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun NbaGEtat(g: NbaGame) {
    when (g.state) {
        // En direct : pastille rouge « ● QT4 3:12 »
        "in" -> {
            val forme = RoundedCornerShape(5.u)
            Row(
                Modifier
                    .height(22.u)
                    .background(NbaGRouge.copy(alpha = 0.12f), forme)
                    .border(graphiteUnPixel(), NbaGRouge.copy(alpha = 0.35f), forme)
                    .padding(horizontal = 8.u),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((11.u / 2)),
            ) {
                val aureole = 2.u
                Box(
                    Modifier
                        .size(5.u)
                        .drawBehind {
                            drawCircle(NbaGRouge.copy(alpha = 0.22f), radius = size.minDimension / 2f + aureole.toPx())
                            drawCircle(NbaGRouge)
                        }
                )
                Text(
                    tempsDeJeu(g.detail), color = Color(0xFFFECACA), fontSize = 11.us,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1,
                )
            }
        }
        "post" -> Text(
            if (g.detail.contains("OT")) "Final (prol.)" else "Final",
            color = NbaGEncre2, fontSize = 13.us, fontFamily = FontFamily.Monospace, maxLines = 1,
        )
        else -> Text(
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(g.start)),
            color = NbaGEncre2, fontSize = 13.us, fontFamily = FontFamily.Monospace, maxLines = 1,
        )
    }
}
