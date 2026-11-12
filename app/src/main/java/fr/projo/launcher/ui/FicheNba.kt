package fr.projo.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.FicheEquipe
import fr.projo.launcher.nba.JoueurNba
import fr.projo.launcher.nba.MatchEquipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Design « NBA » : la fiche d'une équipe, panneau de verre sombre à droite (680 de large, hauteur
 * selon le contenu). En-tête (logo, nom, bilan, rangs, série, 10 derniers) puis deux onglets
 * changés avec ◀ ▶ : Résumé (dernier et prochain match, cinq de départ, blessures) et Effectif.
 * Retour la ferme. Squelette discret pendant le chargement ; « Fiche indisponible pour
 * l'instant » en cas d'échec.
 */

private val VertNba = Color(0xFF4ADE80)
private val RougeNba = Color(0xFFFF6B6B)
private val EncreNba = Color(0xFF0A1534)

private const val F_GAUCHE = 1176f
private const val F_HAUT = 150f
private const val F_LARGEUR = 680f

/** État de la fiche. */
private sealed interface EtatFiche {
    object Chargement : EtatFiche
    object Erreur : EtatFiche
    class Prete(val fiche: FicheEquipe) : EtatFiche
}

/**
 * Fiche de l'équipe [code] ([bilan] : son bilan au classement, s'il est connu). [fermer] :
 * Retour (true : le focus revient sur la carte) ou focus parti ailleurs (false).
 */
@Composable
internal fun FicheNba(
    code: String,
    bilan: BilanNba?,
    source: SourceNba,
    logos: Map<String, ImageBitmap>,
    k: Float,
    focus: FocusRequester,
    fermer: (Boolean) -> Unit,
) {
    val etat by produceState<EtatFiche>(EtatFiche.Chargement, code, source) {
        val f = runCatching {
            if (source.synchrone) source.fiche(code) else withContext(Dispatchers.IO) { source.fiche(code) }
        }.getOrNull()
        value = if (f != null) EtatFiche.Prete(f) else EtatFiche.Erreur
    }
    var onglet by remember(code) { mutableIntStateOf(0) }
    var aEuLeFocus by remember(code) { mutableStateOf(false) }
    // Apparition : glisse un peu depuis la droite et se dévoile (une fois, à l'ouverture)
    val entree = remember(code) { Animatable(if (source.synchrone) 1f else 0f) }
    LaunchedEffect(code) { entree.animateTo(1f, tween(220)) }
    Box(
        Modifier
            .offset { IntOffset((F_GAUCHE * k).roundToInt(), (F_HAUT * k).roundToInt()) }
            .width(F_LARGEUR.mq)
            .graphicsLayer {
                alpha = entree.value
                translationX = (1f - entree.value) * 40f * k
            }
            .verreNba(k, 26f, opacite = 0.97f)
            .testTag("nba:fiche")
            .focusRequester(focus)
            .onFocusChanged {
                if (it.hasFocus) aEuLeFocus = true else if (aEuLeFocus) fermer(false)
            }
            .onKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) onglet = if (e.key == Key.DirectionLeft) 0 else 1
                        true
                    }
                    Key.Back, Key.Escape -> {
                        if (e.type == KeyEventType.KeyUp) fermer(true)
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(Modifier.padding(horizontal = 32.mq, vertical = 28.mq)) {
            val f = (etat as? EtatFiche.Prete)?.fiche
            EnTeteFiche(code, f?.nomComplet ?: TextesNba.nomComplet(code), bilan, logos[code], k)
            Onglets(onglet, k)
            when (val e = etat) {
                EtatFiche.Chargement -> Squelette(k)
                EtatFiche.Erreur -> Box(Modifier.fillMaxWidth().height(140.mq).testTag("nba:fiche:erreur"), contentAlignment = Alignment.Center) {
                    TexteNba("Fiche indisponible pour l'instant", 21f, Color.White.copy(alpha = 0.6f), poids = FontWeight.SemiBold)
                }
                is EtatFiche.Prete -> if (onglet == 0) Resume(e.fiche, logos, k) else Effectif(e.fiche, k)
            }
        }
    }
}

/** Disque blanc cerclé de la couleur de l'équipe, son logo dedans (ou son code en attendant). */
@Composable
private fun DisqueEquipe(code: String, logo: ImageBitmap?, diametre: Float, anneau: Float, k: Float) {
    Box(
        Modifier.size(diametre.mq).drawBehind {
            val r = size.minDimension / 2f
            drawCircle(Color.White, r)
            drawCircle(FormePastille.anneau(code), r - anneau * k / 2f, style = Stroke(anneau * k))
            if (logo != null) dessinerLogo(logo, center, diametre * 0.7f * k)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (logo == null) TexteNba(code, diametre * 0.32f, FormePastille.lettres(code), titre = true)
    }
}

/** Rangs de la fiche, « er »/« e » en exposant : « 6e OUEST · 2e NORD-OUEST ». */
private fun rangsFiche(b: BilanNba, exposants: TextUnit): AnnotatedString = buildAnnotatedString {
    val d = DivisionNba.de(b.code) ?: return@buildAnnotatedString
    val exposant = SpanStyle(fontSize = exposants, baselineShift = BaselineShift(0.55f))
    fun rang(n: Int) {
        val (chiffre, suffixe) = TextesNba.rang(n)
        append(chiffre)
        withStyle(exposant) { append(suffixe) }
    }
    rang(b.rangConference)
    append(" " + TextesNba.conference(d.conference))
    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) { append(" · ") }
    rang(b.rangDivision)
    append(" " + d.nom.uppercase(Locale.FRENCH))
}

@Composable
private fun EnTeteFiche(code: String, nom: String, bilan: BilanNba?, logo: ImageBitmap?, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DisqueEquipe(code, logo, 104f, 5f, k)
        Spacer(Modifier.width(24.mq))
        Column {
            TexteNba(nom, 36f, Color.White, titre = true)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.mq)) {
                TexteNba(TextesNba.bilan(bilan), 30f, Color.White, Modifier.alignByBaseline(), titre = true)
                if (bilan != null) {
                    Spacer(Modifier.width(14.mq))
                    TexteNba(rangsFiche(bilan, 13f.mqs), 19f, Color.White.copy(alpha = 0.72f), Modifier.alignByBaseline(), espace = 1.2f)
                }
            }
            val details = listOfNotNull(bilan?.let { TextesNba.serie(it.serie) }, bilan?.let { TextesNba.dixDerniers(it.dixDerniers) })
            if (details.isNotEmpty()) {
                TexteNba(details.joinToString(" · "), 18f, Color.White.copy(alpha = 0.6f), Modifier.padding(top = 4.mq), poids = FontWeight.SemiBold)
            }
        }
    }
}

/** Onglets « Résumé » et « Effectif » (◀ ▶), puis un filet. */
@Composable
private fun Onglets(onglet: Int, k: Float) {
    Row(Modifier.padding(top = 24.mq).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((i, titre) in listOf("Résumé", "Effectif").withIndex()) {
            val actif = i == onglet
            Box(
                Modifier
                    .padding(end = 10.mq)
                    .drawBehind { if (actif) drawRoundRect(Color.White, cornerRadius = CornerRadius(size.height / 2f)) }
                    .testTag(if (i == 0) "nba:onglet:resume" else "nba:onglet:effectif")
                    .padding(horizontal = 22.mq, vertical = 7.mq),
            ) {
                TexteNba(titre.uppercase(Locale.FRENCH), 18f, if (actif) EncreNba else Color.White.copy(alpha = 0.55f), titre = true, poids = FontWeight.SemiBold, espace = 1.4f)
            }
        }
        Spacer(Modifier.weight(1f))
        TexteNba("◀ ▶", 16f, Color.White.copy(alpha = 0.35f), poids = FontWeight.SemiBold, espace = 2f)
    }
    Spacer(
        Modifier.padding(vertical = 18.mq).fillMaxWidth().height(1.mq)
            .drawBehind { drawRect(Color.White.copy(alpha = 0.08f)) },
    )
}

/** Intitulé d'une partie de la fiche. */
@Composable
private fun Intitule(texte: String, modifier: Modifier = Modifier) {
    TexteNba(texte, 14f, Color.White.copy(alpha = 0.5f), modifier.padding(bottom = 10.mq), poids = FontWeight.ExtraBold, espace = 3.4f)
}

// ------------------------------------------------------------------ Résumé

@Composable
private fun Resume(f: FicheEquipe, logos: Map<String, ImageBitmap>, k: Float) {
    Column(Modifier.testTag("nba:fiche:resume")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.mq)) {
            BlocMatch("DERNIER MATCH", f.dernier, dernier = true, logos, k)
            BlocMatch("PROCHAIN MATCH", f.prochain, dernier = false, logos, k)
        }
        Intitule("CINQ DE DÉPART", Modifier.padding(top = 24.mq))
        if (f.titulaires.isEmpty()) {
            TexteNba("Pas encore connu", 18f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.mq)) {
                Spacer(Modifier.weight(1f))
                for (c in listOf("MIN", "PTS", "REB", "PD")) {
                    Box(Modifier.width(64.mq), contentAlignment = Alignment.CenterEnd) {
                        TexteNba(c, 14f, Color.White.copy(alpha = 0.45f), espace = 1.4f)
                    }
                }
            }
            for (j in f.titulaires.take(5)) LigneTitulaire(j)
        }
        Intitule("BLESSURES", Modifier.padding(top = 22.mq))
        if (f.blessures.isEmpty()) {
            TexteNba("Aucun blessé", 18f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
        } else {
            for (b in f.blessures.take(4)) {
                Row(Modifier.height(30.mq), verticalAlignment = Alignment.CenterVertically) {
                    TexteNba(b.nom, 19f, Color.White, Modifier.alignByBaseline())
                    TexteNba(
                        "  " + listOfNotNull(b.statut, b.zone).joinToString(" · "), 18f,
                        if (b.statut == "Absent" || b.statut == "Suspendu") RougeNba else OrNba,
                        Modifier.alignByBaseline(), poids = FontWeight.SemiBold,
                    )
                }
            }
            if (f.blessures.size > 4) {
                val n = f.blessures.size - 4
                TexteNba("et $n autre" + if (n > 1) "s" else "", 17f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
            }
        }
    }
}

/** Dernier ou prochain match : intitulé, logo de l'adversaire et deux lignes. */
@Composable
private fun RowScope.BlocMatch(titre: String, m: MatchEquipe?, dernier: Boolean, logos: Map<String, ImageBitmap>, k: Float) {
    Column(Modifier.weight(1f)) {
        Intitule(titre)
        if (m == null) {
            TexteNba(if (dernier) "Aucun pour l'instant" else "Pas de match prévu", 18f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DisqueEquipe(m.adversaire, logos[m.adversaire], 48f, 3f, k)
            Spacer(Modifier.width(14.mq))
            Column {
                val ligne1: AnnotatedString = if (dernier) {
                    buildAnnotatedString {
                        when (m.victoire) {
                            true -> withStyle(SpanStyle(color = VertNba)) { append("Victoire") }
                            false -> withStyle(SpanStyle(color = RougeNba)) { append("Défaite") }
                            null -> append("Terminé")
                        }
                        if (m.score != null && m.scoreAdversaire != null) append(" ${m.score}–${m.scoreAdversaire}")
                    }
                } else {
                    AnnotatedString(TextesNba.jour(m.debut) + " · " + TextesNba.heure(m.debut))
                }
                TexteNba(ligne1, 21f, Color.White, titre = true, poids = FontWeight.SemiBold)
                val ligne2 = if (dernier) TextesNba.lieu(m) + " · " + TextesNba.jour(m.debut) else TextesNba.lieu(m)
                TexteNba(ligne2, 17f, Color.White.copy(alpha = 0.7f), poids = FontWeight.SemiBold)
            }
        }
    }
}

/** Titulaire : nom, poste, moyennes de la saison (MIN, PTS, REB, PD). */
@Composable
private fun LigneTitulaire(j: JoueurNba) {
    Row(
        Modifier.fillMaxWidth().height(36.mq).drawBehind { drawRect(Color.White.copy(alpha = 0.06f), size = Size(size.width, 1f)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TexteNba(j.nom, 19f, Color.White, Modifier.alignByBaseline())
            j.poste?.let { TexteNba("  $it", 15f, Color.White.copy(alpha = 0.5f), Modifier.alignByBaseline(), poids = FontWeight.SemiBold) }
        }
        for (v in listOf(j.minutes, j.points, j.rebonds, j.passes)) {
            Box(Modifier.width(64.mq), contentAlignment = Alignment.CenterEnd) {
                TexteNba(TextesNba.moyenne(v), 19f, Color.White, titre = true, poids = FontWeight.SemiBold)
            }
        }
    }
}

// ------------------------------------------------------------------ Effectif

@Composable
private fun Effectif(f: FicheEquipe, k: Float) {
    val joueurs = f.effectif
    if (joueurs.isEmpty()) {
        TexteNba("Effectif indisponible pour l'instant", 19f, Color.White.copy(alpha = 0.6f), Modifier.testTag("nba:fiche:effectif"), poids = FontWeight.SemiBold)
        return
    }
    val moitie = (joueurs.size + 1) / 2
    Row(Modifier.fillMaxWidth().testTag("nba:fiche:effectif"), horizontalArrangement = Arrangement.spacedBy(28.mq)) {
        for (colonne in listOf(joueurs.take(moitie), joueurs.drop(moitie))) {
            Column(Modifier.weight(1f)) { for (j in colonne) LigneJoueur(j, k) }
        }
    }
}

@Composable
private fun LigneJoueur(j: JoueurNba, k: Float) {
    Row(
        Modifier.fillMaxWidth().height(36.mq).drawBehind { drawRect(Color.White.copy(alpha = 0.06f), size = Size(size.width, 1f)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(42.mq)) {
            TexteNba(j.numero?.let { "#$it" } ?: "", 17f, Color.White.copy(alpha = 0.45f), titre = true, poids = FontWeight.SemiBold)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TexteNba(j.nom, 19f, Color.White, Modifier.alignByBaseline().weight(1f, fill = false))
            j.poste?.let { TexteNba("  $it", 15f, Color.White.copy(alpha = 0.5f), Modifier.alignByBaseline(), poids = FontWeight.SemiBold) }
        }
        if (j.blesse) {
            Box(
                Modifier.drawBehind { drawRoundRect(RougeNba.copy(alpha = 0.16f), cornerRadius = CornerRadius(6f * k)) }
                    .padding(horizontal = 7.mq, vertical = 2.mq),
            ) {
                TexteNba("BLESSÉ", 13f, RougeNba, espace = 1f)
            }
        }
    }
}

// ------------------------------------------------------------------ chargement

/** Squelette discret pendant le chargement (formes de Résumé). */
@Composable
private fun Squelette(k: Float) {
    val gris = Color.White.copy(alpha = 0.06f)
    @Composable
    fun barre(largeur: Float, hauteur: Float, haut: Float = 0f) {
        Spacer(
            Modifier.padding(top = haut.mq).width(largeur.mq).height(hauteur.mq)
                .drawBehind { drawRoundRect(gris, cornerRadius = CornerRadius(8f * k)) },
        )
    }
    Column(Modifier.testTag("nba:fiche:chargement")) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mq)) {
            for (i in 0..1) {
                Column {
                    barre(120f, 14f)
                    Row(Modifier.padding(top = 12.mq), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(48.mq).drawBehind { drawCircle(gris) })
                        Column(Modifier.padding(start = 14.mq)) {
                            barre(160f, 20f)
                            barre(200f, 16f, 8f)
                        }
                    }
                }
            }
        }
        barre(150f, 14f, 30f)
        for (i in 0 until 5) barre(616f, 26f, 10f)
    }
}
