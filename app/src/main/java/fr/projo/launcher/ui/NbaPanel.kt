package fr.projo.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaNight
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.data.NbaTeam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LIGNES_VISIBLES = 3

/**
 * Bandeau NBA (sous les pastilles), deux onglets :
 *  - « Cette nuit » : matchs de la nuit, du plus tôt au plus tard ; en cours : point rouge,
 *    score et temps de jeu ;
 *  - « Résultats » : scores finaux de la nuit passée.
 * 3 matchs visibles, les autres en faisant défiler avec les flèches.
 */
@Composable
fun NbaPanel(data: NbaData?, erreur: Boolean, repo: NbaRepository, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(14.u)
    var onglet by remember { mutableIntStateOf(0) }
    Column(
        modifier
            .width(340.u)
            .clip(forme)
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.u, Color.White.copy(alpha = 0.11f)), forme)
            .padding(start = 8.u, end = 8.u, top = 8.u, bottom = 8.u)
    ) {
        val nuit = data?.tonight
        val libelleNuit = if (nuit == null || nuit.isTonight) "CETTE NUIT" else NbaRepository.libelleJour(nuit.day)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NBA", color = Color.White.copy(alpha = 0.62f), fontSize = 9.us,
                letterSpacing = 2.us, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.u, end = 8.u),
            )
            Onglet(libelleNuit, onglet == 0) { onglet = 0 }
            Spacer(Modifier.width(4.u))
            Onglet("RÉSULTATS", onglet == 1) { onglet = 1 }
        }
        Spacer(Modifier.height(5.u))

        val affichee: NbaNight? = if (onglet == 0) nuit else data?.results
        val jeux = affichee?.games.orEmpty()
        when {
            jeux.isEmpty() -> {
                val msg = when {
                    data == null && !erreur -> "Chargement des matchs…"
                    data == null -> "Matchs indisponibles (pas de connexion)"
                    onglet == 1 -> "Pas de résultat récent"
                    else -> "Aucun match NBA cette semaine"
                }
                Text(
                    msg, color = Color.White.copy(alpha = 0.5f), fontSize = 11.us,
                    modifier = Modifier.padding(horizontal = 6.u, vertical = 10.u),
                )
            }
            else -> {
                val etat = rememberLazyListState()
                // « Cette nuit » : on montre d'abord les matchs en cours ou à venir
                LaunchedEffect(affichee?.day, onglet) {
                    val i = if (onglet == 0) jeux.indexOfFirst { it.state != "post" } else 0
                    etat.scrollToItem(maxOf(0, i))
                }
                LazyColumn(
                    state = etat,
                    modifier = Modifier.height((34 * minOf(LIGNES_VISIBLES, jeux.size)).u),
                ) {
                    items(jeux, key = { it.id }) { g -> LigneMatch(g, repo) }
                }
            }
        }
    }
}

/** Onglet : se sélectionne dès qu'on passe dessus (comme les onglets d'Android TV). */
@Composable
private fun Onglet(texte: String, actif: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.height(20.u).onFocusChanged { if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.u)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (actif) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = Color.White.copy(alpha = if (actif) 0.95f else 0.5f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B0D12),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.height(20.u).padding(horizontal = 9.u), contentAlignment = Alignment.Center) {
            Text(texte, fontSize = 9.us, letterSpacing = 1.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LigneMatch(g: NbaGame, repo: NbaRepository) {
    val forme = RoundedCornerShape(9.u)
    // Ligne focalisable : c'est ce qui permet de faire défiler la liste à la télécommande
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().height(34.u),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = if (g.state == "post") 0.55f else 1f),
            focusedContainerColor = Color.White.copy(alpha = 0.95f),
            focusedContentColor = Color(0xFF0B0D12),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(horizontal = 7.u).fillMaxWidth().height(34.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Equipe(g.away, repo)
            Score(g)
            Equipe(g.home, repo)
            Spacer(Modifier.weight(1f))
            Etat(g)
        }
    }
}

@Composable
private fun Equipe(t: NbaTeam, repo: NbaRepository) {
    Row(Modifier.width(74.u), verticalAlignment = Alignment.CenterVertically) {
        Logo(t, repo)
        Spacer(Modifier.width(6.u))
        Text(t.abbr, fontSize = 13.us, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Logo(t: NbaTeam, repo: NbaRepository) {
    val url = t.logoUrl
    val logo by produceState(initialValue = url?.let { repo.cachedLogo(it) }, url) {
        if (value == null && url != null) value = withContext(Dispatchers.IO) { repo.loadLogo(url) }
    }
    Box(
        Modifier.size(23.u).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        val l = logo
        if (l != null) {
            Image(l, contentDescription = t.abbr, modifier = Modifier.size(19.u))
        } else {
            Text(t.abbr.take(1), color = Color(0xFF0B0D12), fontSize = 10.us, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Score(g: NbaGame) {
    Row(Modifier.width(80.u), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (g.state == "pre") {
            Text("–", fontSize = 13.us)
        } else {
            Text(g.away.score ?: "", fontSize = 13.us, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.u), textAlign = TextAlign.Center)
            Text("–", fontSize = 13.us, modifier = Modifier.padding(horizontal = 2.u))
            Text(g.home.score ?: "", fontSize = 13.us, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.u), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Etat(g: NbaGame) {
    when (g.state) {
        "in" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.u).clip(CircleShape).background(Color(0xFFFF3B30)))
            Spacer(Modifier.width(5.u))
            Text(tempsDeJeu(g.detail), color = Color(0xFFE5383B), fontSize = 11.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        "post" -> Text(if (g.detail.contains("OT")) "Final (prol.)" else "Final", fontSize = 11.us, fontWeight = FontWeight.Medium)
        else -> Text(
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(g.start)),
            fontSize = 13.us, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** « Q3 4:12 » → « QT3 4:12 », « Halftime » → « Mi-temps ». */
private fun tempsDeJeu(s: String): String = when {
    s.contains("half", ignoreCase = true) -> "Mi-temps"
    s.startsWith("End of", ignoreCase = true) -> "Fin " + s.substringAfter("of ").replace("1st", "QT1")
        .replace("2nd", "QT2").replace("3rd", "QT3").replace("4th", "QT4")
    else -> s.replace(Regex("^(\\d)(st|nd|rd|th) Quarter"), "QT$1").replace(Regex("^Q(\\d)"), "QT$1").replace("OT", "Prol.")
}
