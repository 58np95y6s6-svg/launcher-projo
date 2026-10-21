package fr.projo.launcher.modules.sport

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaNight
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.data.NbaTeam
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.IconesModules
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.ui.LigneListe
import fr.projo.launcher.ui.ListeDialog
import fr.projo.launcher.ui.tempsDeJeu
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val LIGNES_VISIBLES = 3
private const val HAUTEUR_LIGNE = 34
private const val HAUTEUR_LIGNE_PETITE = 36

/**
 * Module Sport : scores et horaires des compétitions cochées dans ses réglages (NBA, football,
 * rugby), d'après les tableaux des scores publics d'ESPN.
 *  - NBA seule (par défaut) : l'ancien bandeau NBA à l'identique (« Cette nuit » / « Résultats ») ;
 *  - plusieurs compétitions : un onglet par compétition qui a des matchs (les autres ne prennent
 *    pas de place), liste des matchs du moment ;
 *  - PETIT : une seule liste (en direct, puis à venir, puis résultats).
 * Une compétition dont la source ne répond pas est masquée (note discrète « n indispo. »).
 */
object ModuleSport : Module(
    id = "sport",
    nom = "Sport",
    description = "Scores et horaires : NBA, football, rugby",
    icone = IconesModules.Sport,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    private const val CLE_COMPETITIONS = "competitions"

    /** true si la zone n'affiche que le Sport, avec la NBA seule (réglage par défaut). */
    fun nbaSeule(hote: HoteModules): Boolean =
        hote.modules.singleOrNull() == this && competitions(hote.reglagesDe(id)).map { it.id } == listOf("nba")

    /** Matchs NBA du cache des modules (pour un bandeau dessiné hors de la zone, ex. « À la une »). */
    @Composable
    fun rememberNba(donnees: DonneesModules): EtatDonnees<MatchsCompetition> {
        val context = LocalContext.current
        val nba = COMPETITIONS[0]
        return rememberDonnees(donnees, SportReseau.cle(nba), delai = { m: MatchsCompetition -> SportReseau.delai(nba, m) }) {
            SportReseau.charger(context, nba)
        }
    }

    override fun titre(reglages: ReglagesModule): String =
        competitions(reglages).singleOrNull()?.nom ?: nom

    fun competitions(r: ReglagesModule): List<Competition> {
        val ids = r.liste(CLE_COMPETITIONS, COMPETITIONS_PAR_DEFAUT)
        return COMPETITIONS.filter { it.id in ids }.ifEmpty { listOf(COMPETITIONS[0]) }
    }

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val context = LocalContext.current
        val comps = competitions(ctx.reglages)
        val etats = comps.map { c ->
            key(c.id) {
                ctx.rememberDonnees(SportReseau.cle(c), delai = { m: MatchsCompetition -> SportReseau.delai(c, m) }) {
                    SportReseau.charger(context, c)
                }
            }
        }
        val repo = remember { SportReseau.depot(context) }
        val lignes = if (ctx.taille == TailleModule.GRAND) {
            ((ctx.hauteur - 25) / HAUTEUR_LIGNE).toInt().coerceAtLeast(LIGNES_VISIBLES)
        } else LIGNES_VISIBLES
        when {
            ctx.taille == TailleModule.PETIT -> SportPetit(comps, etats, ctx, repo, modifier)
            comps.size == 1 && comps[0].id == "nba" -> NbaSeul(etats[0], ctx, repo, lignes, modifier)
            else -> SportOnglets(comps, etats, ctx, repo, lignes, modifier)
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun resumeReglages(reglages: ReglagesModule): String =
        competitions(reglages).joinToString(", ") { it.nom }

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        val choisies = competitions(reglages).map { it.id }
        val lignes = buildList {
            for ((sport, comps) in COMPETITIONS.groupBy { it.sport }) {
                add(LigneListe(sport.uppercase(Locale.FRENCH), section = true))
                for (c in comps) {
                    add(LigneListe(c.nom, coche = c.id in choisies) {
                        val coche = c.id in choisies
                        val nouvelles = COMPETITIONS.map { it.id }
                            .filter { if (it == c.id) !coche else it in choisies }
                        // Au moins une compétition reste cochée
                        if (nouvelles.isNotEmpty()) reglages.mettreListe(CLE_COMPETITIONS, nouvelles)
                    })
                }
            }
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Sport",
            sousTitre = "Coche les compétitions à afficher (au moins une)",
            lignes = lignes,
            onDismiss = fermer,
        )
    }
}

// ------------------------------------------------------------------ NBA seule (bandeau d'origine)

/**
 * Bandeau NBA d'origine, deux onglets :
 *  - « Cette nuit » : matchs de la nuit, du plus tôt au plus tard ; en cours : point rouge,
 *    score et temps de jeu ;
 *  - « Résultats » : scores finaux de la nuit passée.
 * 3 matchs visibles, les autres en faisant défiler avec les flèches.
 */
@Composable
private fun NbaSeul(
    etat: EtatDonnees<MatchsCompetition>,
    ctx: ModuleContexte,
    repo: NbaRepository,
    lignes: Int,
    modifier: Modifier,
) {
    val style = ctx.style
    val data = etat.valeur?.nba
    val erreur = etat.erreur
    var onglet by remember { mutableIntStateOf(0) }
    var resultatsFocus by remember { mutableStateOf(false) }
    Column(
        modifier
            // ◀ n'importe où dans le bandeau : on en sort (seul « RÉSULTATS » → « CETTE NUIT » reste interne)
            .onPreviewKeyEvent {
                if (it.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyDown) {
                    if (resultatsFocus) runCatching { ctx.entree.requestFocus() } else ctx.sortir()
                }
                true
            }
    ) {
        val nuit = data?.tonight
        val libelleNuit = if (nuit == null || nuit.isTonight) "CETTE NUIT" else NbaRepository.libelleJour(nuit.day)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NBA", color = style.texteDiscret, fontSize = 9.us,
                letterSpacing = 2.us, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.u, end = 8.u),
            )
            Onglet(libelleNuit, onglet == 0, Modifier.focusRequester(ctx.entree)) { onglet = 0 }
            Spacer(Modifier.width(4.u))
            Onglet("RÉSULTATS", onglet == 1, Modifier.onFocusChanged { resultatsFocus = it.isFocused }) { onglet = 1 }
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
                Message(msg)
            }
            else -> {
                val etatListe = rememberLazyListState()
                // « Cette nuit » : on montre d'abord les matchs en cours ou à venir
                LaunchedEffect(affichee?.day, onglet) {
                    val i = if (onglet == 0) jeux.indexOfFirst { it.state != "post" } else 0
                    etatListe.scrollToItem(maxOf(0, i))
                }
                LazyColumn(
                    state = etatListe,
                    modifier = Modifier.height((HAUTEUR_LIGNE * minOf(lignes, jeux.size)).u),
                ) {
                    items(jeux, key = { it.id }) { g -> LigneMatch(g, repo, avecJour = false) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ plusieurs compétitions

/** Un onglet par compétition qui a des matchs ; la liste de la compétition choisie dessous. */
@Composable
private fun SportOnglets(
    comps: List<Competition>,
    etats: List<EtatDonnees<MatchsCompetition>>,
    ctx: ModuleContexte,
    repo: NbaRepository,
    lignes: Int,
    modifier: Modifier,
) {
    val style = ctx.style
    val avecMatchs = comps.indices.filter { etats[it].valeur?.jeux?.isNotEmpty() == true }
    val enPanne = comps.indices.count { etats[it].erreur && etats[it].valeur == null }
    // Toujours au moins un onglet : la zone reste atteignable à la télécommande
    val onglets = avecMatchs.ifEmpty { comps.indices.toList() }
    var choisi by remember { mutableStateOf(comps.first().id) }
    val actif = onglets.firstOrNull { comps[it].id == choisi } ?: onglets.first()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(20.u), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.u)) {
                itemsIndexed(onglets, key = { _, i -> comps[i].id }) { k, i ->
                    Onglet(
                        comps[i].court, i == actif,
                        if (k == 0) Modifier.focusRequester(ctx.entree) else Modifier,
                    ) { choisi = comps[i].id }
                }
            }
            if (enPanne > 0 && avecMatchs.isNotEmpty()) {
                Text(
                    "$enPanne indispo.", color = style.texteDiscret, fontSize = 9.us, maxLines = 1,
                    modifier = Modifier.padding(start = 6.u, end = 4.u),
                )
            }
        }
        Spacer(Modifier.height(5.u))
        val c = comps[actif]
        val etat = etats[actif]
        val jeux = etat.valeur?.jeux.orEmpty()
        if (jeux.isEmpty()) {
            Message(
                when {
                    etats.all { it.chargement } || (etat.chargement && avecMatchs.isEmpty()) -> "Chargement des matchs…"
                    enPanne == comps.size -> "Matchs indisponibles (pas de connexion)"
                    etat.erreur && etat.valeur == null -> "${c.nom} : source indisponible"
                    else -> "Aucun match prévu cette semaine"
                }
            )
        } else {
            val etatListe = rememberLazyListState()
            // D'abord les matchs en cours ou à venir
            LaunchedEffect(c.id, jeux.size) {
                etatListe.scrollToItem(maxOf(0, jeux.indexOfFirst { it.state != "post" }))
            }
            LazyColumn(
                state = etatListe,
                modifier = Modifier.height((HAUTEUR_LIGNE * minOf(lignes, jeux.size)).u),
            ) {
                items(jeux, key = { it.id }) { g -> LigneMatch(g, repo, avecJour = c.id != "nba") }
            }
        }
    }
}

// ------------------------------------------------------------------ PETIT

private class MatchDe(val c: Competition, val g: NbaGame)

/** Petite tuile : une seule liste, en direct d'abord, puis à venir, puis derniers résultats. */
@Composable
private fun SportPetit(
    comps: List<Competition>,
    etats: List<EtatDonnees<MatchsCompetition>>,
    ctx: ModuleContexte,
    repo: NbaRepository,
    modifier: Modifier,
) {
    val style = ctx.style
    val tous = comps.indices.flatMap { i -> etats[i].valeur?.jeux.orEmpty().map { MatchDe(comps[i], it) } }
    val liste = (
        tous.filter { it.g.state == "in" } +
            tous.filter { it.g.state == "pre" }.sortedBy { it.g.start } +
            tous.filter { it.g.state == "post" }.sortedByDescending { it.g.start }
        ).take(15)
    val enPanne = comps.indices.count { etats[it].erreur && etats[it].valeur == null }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(16.u).padding(horizontal = 6.u), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (comps.size == 1) comps[0].court else "SPORT", color = style.texteDiscret,
                fontSize = 9.us, letterSpacing = 2.us, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (enPanne > 0 && liste.isNotEmpty()) {
                Text("$enPanne indispo.", color = style.texteDiscret, fontSize = 8.us, maxLines = 1)
            }
        }
        Spacer(Modifier.height(2.u))
        if (liste.isEmpty()) {
            CarteModule(ctx, Modifier.fillMaxWidth().height(64.u)) {
                Text(
                    when {
                        etats.any { it.chargement } -> "Chargement…"
                        enPanne == comps.size -> "Matchs indisponibles"
                        else -> "Aucun match cette semaine"
                    },
                    color = style.texteDiscret, fontSize = 11.us, style = ligne(14.us),
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(6.u),
                )
            }
        } else {
            // Nombre entier de lignes : aucune ligne coupée en bas, quelle que soit la hauteur
            val lignes = ((ctx.hauteur - 18f) / HAUTEUR_LIGNE_PETITE).toInt().coerceAtLeast(1)
            LazyColumn(Modifier.fillMaxWidth().height((HAUTEUR_LIGNE_PETITE * minOf(lignes, liste.size)).u)) {
                itemsIndexed(liste, key = { _, m -> m.c.id + ":" + m.g.id }) { i, m ->
                    LignePetite(m, repo, comps.size > 1, if (i == 0) Modifier.focusRequester(ctx.entree) else Modifier)
                }
            }
        }
    }
}

/** Ligne de la petite tuile : équipes et score, puis l'état (compétition et heure). */
@Composable
private fun LignePetite(m: MatchDe, repo: NbaRepository, avecCompetition: Boolean, modifier: Modifier) {
    val style = LocalStyleModule.current
    val g = m.g
    val forme = RoundedCornerShape(9.u)
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth().height(HAUTEUR_LIGNE_PETITE.u),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (g.state == "post") style.texte.copy(alpha = style.texte.alpha * 0.6f) else style.texte,
            focusedContainerColor = style.focusFond,
            focusedContentColor = style.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 6.u),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(g.away, repo, 14)
                Spacer(Modifier.width(4.u))
                val reporte = estReporte(g)
                val score = if (g.state == "pre" || reporte) "–" else "${g.away.score ?: ""} – ${g.home.score ?: ""}"
                Text(
                    "${g.away.abbr.take(4)}  $score  ${g.home.abbr.take(4)}",
                    fontSize = 12.us, fontWeight = FontWeight.Bold, style = ligne(15.us),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.u))
                Logo(g.home, repo, 14)
            }
            val enDirect = g.state == "in"
            val etat = buildString {
                if (avecCompetition) append(m.c.court).append(" · ")
                append(
                    when (g.state) {
                        "in" -> if (m.c.id == "nba") tempsDeJeu(g.detail) else detailAutre(g.detail)
                        "post" -> finAutre(g, m.c.id == "nba")
                        else -> heureMatch(g.start, avecJour = m.c.id != "nba")
                    }
                )
            }
            Text(
                (if (enDirect) "● " else "") + etat,
                color = if (enDirect) Color(0xFFE5383B) else Color.Unspecified,
                fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ éléments communs

@Composable
private fun Message(msg: String) {
    Text(
        msg, color = LocalStyleModule.current.texteDiscret, fontSize = 11.us,
        modifier = Modifier.padding(horizontal = 6.u, vertical = 10.u),
    )
}

/** Onglet : se sélectionne dès qu'on passe dessus (comme les onglets d'Android TV). */
@Composable
private fun Onglet(texte: String, actif: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val style = LocalStyleModule.current
    Surface(
        onClick = onSelect,
        modifier = modifier.height(20.u).onFocusChanged { if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.u)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (actif) style.ongletActif else Color.Transparent,
            contentColor = if (actif) style.texte else style.texteDiscret,
            focusedContainerColor = style.focusFond,
            focusedContentColor = style.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.height(20.u).padding(horizontal = 9.u), contentAlignment = Alignment.Center) {
            Text(texte, fontSize = 9.us, letterSpacing = 1.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LigneMatch(g: NbaGame, repo: NbaRepository, avecJour: Boolean) {
    val style = LocalStyleModule.current
    val forme = RoundedCornerShape(9.u)
    // Ligne focalisable : c'est ce qui permet de faire défiler la liste à la télécommande
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().height(HAUTEUR_LIGNE.u),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (g.state == "post") style.texte.copy(alpha = style.texte.alpha * 0.6f) else style.texte,
            focusedContainerColor = style.focusFond,
            focusedContentColor = style.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(horizontal = 7.u).fillMaxWidth().height(HAUTEUR_LIGNE.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Equipe(g.away, repo)
            Score(g)
            Equipe(g.home, repo)
            Spacer(Modifier.weight(1f))
            Etat(g, avecJour)
        }
    }
}

@Composable
private fun Equipe(t: NbaTeam, repo: NbaRepository) {
    Row(Modifier.width(74.u), verticalAlignment = Alignment.CenterVertically) {
        Logo(t, repo, 23)
        Spacer(Modifier.width(6.u))
        Text(t.abbr.take(4), fontSize = 13.us, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Logo(t: NbaTeam, repo: NbaRepository, taille: Int) {
    val url = t.logoUrl
    val logo by produceState(initialValue = url?.let { repo.cachedLogo(it) }, url) {
        if (value == null && url != null) value = withContext(Dispatchers.IO) { repo.loadLogo(url) }
    }
    Box(
        Modifier.size(taille.u).clip(CircleShape).background(LocalStyleModule.current.pastilleFond),
        contentAlignment = Alignment.Center,
    ) {
        val l = logo
        if (l != null) {
            Image(l, contentDescription = t.abbr, modifier = Modifier.size((taille * 19 / 23).u))
        } else {
            Text(t.abbr.take(1), color = Color(0xFF0B0D12), fontSize = (taille * 10 / 23).us, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Score(g: NbaGame) {
    Row(Modifier.width(80.u), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (g.state == "pre" || estReporte(g)) {
            Text("–", fontSize = 13.us)
        } else {
            Text(g.away.score ?: "", fontSize = 13.us, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.u), textAlign = TextAlign.Center, maxLines = 1)
            Text("–", fontSize = 13.us, modifier = Modifier.padding(horizontal = 2.u))
            Text(g.home.score ?: "", fontSize = 13.us, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.u), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun Etat(g: NbaGame, avecJour: Boolean) {
    when (g.state) {
        "in" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.u).clip(CircleShape).background(Color(0xFFFF3B30)))
            Spacer(Modifier.width(5.u))
            Text(
                if (avecJour) detailAutre(g.detail) else tempsDeJeu(g.detail),
                color = Color(0xFFE5383B), fontSize = 11.us, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
        "post" -> Text(finAutre(g, nba = !avecJour), fontSize = 11.us, fontWeight = FontWeight.Medium, maxLines = 1)
        else -> {
            val texte = heureMatch(g.start, avecJour)
            Text(
                texte, fontSize = if (texte.length > 5) 11.us else 13.us,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }
}

// ------------------------------------------------------------------ textes

/** « 20:30 » aujourd'hui ; « sam. 20:30 » un autre jour (si [avecJour]). */
private fun heureMatch(debut: Long, avecJour: Boolean): String {
    val heure = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(debut))
    if (!avecJour) return heure
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = debut }
    val memeJour = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return if (memeJour) heure else SimpleDateFormat("EEE", Locale.FRENCH).format(Date(debut)).trimEnd('.') + " " + heure
}

private fun estReporte(g: NbaGame): Boolean =
    g.detail.contains("Postponed", ignoreCase = true) || g.detail.contains("Canceled", ignoreCase = true) ||
        g.detail.contains("Cancelled", ignoreCase = true)

/** Match terminé : « Final », « Final (prol.) », « Reporté »… */
private fun finAutre(g: NbaGame, nba: Boolean): String {
    val d = g.detail
    return when {
        d.contains("Postponed", ignoreCase = true) -> "Reporté"
        d.contains("Cancel", ignoreCase = true) -> "Annulé"
        nba -> if (d.contains("OT")) "Final (prol.)" else "Final"
        d.contains("Pen", ignoreCase = true) -> "Final (t.a.b.)"
        d.contains("AET", ignoreCase = true) -> "Final (a.p.)"
        else -> "Final"
    }
}

/** Football, rugby : « 67' », « HT » → « Mi-temps ». */
private fun detailAutre(s: String): String = when {
    s.equals("HT", ignoreCase = true) || s.contains("half", ignoreCase = true) -> "Mi-temps"
    s.isBlank() -> "En cours"
    else -> s.replace("ET", "Prol.").take(9)
}
