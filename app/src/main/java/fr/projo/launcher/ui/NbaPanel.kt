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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
 * Bandeau « NBA · CETTE NUIT » (sous les pastilles) : matchs de la nuit, du plus tôt au plus
 * tard, 3 visibles, les autres en faisant défiler avec les flèches.
 * En cours : point rouge, score et temps de jeu. Terminé : score final, ligne atténuée.
 */
@Composable
fun NbaPanel(night: NbaNight?, erreur: Boolean, repo: NbaRepository, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(14.u)
    Column(
        modifier
            .width(340.u)
            .clip(forme)
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.u, Color.White.copy(alpha = 0.11f)), forme)
            .padding(start = 10.u, end = 10.u, top = 10.u, bottom = 8.u)
    ) {
        val titre = when {
            night == null || night.isTonight -> "NBA · CETTE NUIT"
            else -> "NBA · " + NbaRepository.libelleJour(night.day)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.u), verticalAlignment = Alignment.CenterVertically) {
            Text(
                titre, color = Color.White.copy(alpha = 0.62f),
                fontSize = 9.us, letterSpacing = 2.us, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            val n = night?.games?.size ?: 0
            if (n > 0) {
                Text(
                    if (n > 1) "$n matchs" else "1 match",
                    color = Color.White.copy(alpha = 0.45f), fontSize = 9.us,
                )
            }
        }
        Spacer(Modifier.height(5.u))

        val jeux = night?.games.orEmpty()
        when {
            jeux.isEmpty() -> {
                val msg = when {
                    night == null && !erreur -> "Chargement des matchs…"
                    night == null -> "Matchs indisponibles (pas de connexion)"
                    else -> "Aucun match NBA cette semaine"
                }
                Text(
                    msg, color = Color.White.copy(alpha = 0.5f), fontSize = 11.us,
                    modifier = Modifier.padding(horizontal = 4.u, vertical = 10.u),
                )
            }
            else -> {
                val etat = rememberLazyListState()
                // Au chargement : on montre d'abord les matchs en cours ou à venir
                LaunchedEffect(night?.day) {
                    val i = jeux.indexOfFirst { it.state != "post" }
                    if (i > 0) etat.scrollToItem(i)
                }
                LazyColumn(
                    state = etat,
                    modifier = Modifier.height((37 * minOf(LIGNES_VISIBLES, jeux.size)).u),
                    verticalArrangement = Arrangement.spacedBy(0.u),
                ) {
                    items(jeux, key = { it.id }) { g -> LigneMatch(g, repo) }
                }
            }
        }
    }
}

@Composable
private fun LigneMatch(g: NbaGame, repo: NbaRepository) {
    val forme = RoundedCornerShape(9.u)
    // Ligne focalisable : c'est ce qui permet de faire défiler la liste à la télécommande
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().height(37.u),
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
            Modifier.padding(horizontal = 7.u).fillMaxWidth().height(37.u),
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
