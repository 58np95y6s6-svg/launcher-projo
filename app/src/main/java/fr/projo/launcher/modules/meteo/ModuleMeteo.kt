package fr.projo.launcher.modules.meteo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.IconesModules
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
import fr.projo.launcher.ui.couleursLigne
import fr.projo.launcher.ui.fondDialogue
import fr.projo.launcher.ui.habillageDialogue
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import fr.projo.launcher.ui.LigneCliquable

/**
 * Module Météo (Open-Meteo, sans clé) : température, ressenti, ciel (icônes vectorielles),
 * pluie dans l'heure ; prévisions des 3 prochains jours en MOYEN et GRAND.
 * Lieu : position détectée par l'adresse IP au premier affichage (puis gardée), ou ville
 * choisie dans ses réglages (liste de grandes villes, ou recherche au clavier).
 */
object ModuleMeteo : Module(
    id = "meteo",
    nom = "Météo",
    description = "Température, ciel, pluie dans l'heure et 3 jours",
    icone = IconesModules.Meteo,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    /** « nom;lat;lon » de la ville choisie ; absent = position automatique. */
    private const val CLE_LIEU = "lieu"
    /** « nom;lat;lon » détecté par l'adresse IP (gardé : une seule détection). */
    private const val CLE_AUTO = "auto"

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val reglages = ctx.reglages
        val choisi = Lieu.decoder(reglages.texte(CLE_LIEU))
        val detecte = Lieu.decoder(reglages.texte(CLE_AUTO))
        val etat = ctx.rememberDonnees(
            if (choisi != null) "meteo:" + choisi.encoder() else "meteo:auto",
            delai = { _: MeteoDonnees -> 20 * 60_000L },
            delaiErreur = 3 * 60_000L,
        ) {
            val lieu = choisi ?: detecte ?: MeteoReseau.localiserParIp().also { reglages.mettre(CLE_AUTO, it.encoder()) }
            MeteoReseau.charger(lieu)
        }
        val style = ctx.style
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            val m = etat.valeur
            if (m == null) {
                Column(Modifier.align(Alignment.Center).padding(8.u), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (etat.erreur) "Météo indisponible" else "Météo…",
                        color = style.texteDiscret, fontSize = 12.us, style = ligne(16.us),
                        textAlign = TextAlign.Center,
                    )
                    if (etat.erreur && choisi == null && detecte == null) {
                        Text(
                            "Choisis une ville : Réglages → Modules", color = style.texteDiscret, fontSize = 10.us,
                            style = ligne(13.us), textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                when (ctx.taille) {
                    TailleModule.PETIT -> MeteoPetit(m, style)
                    TailleModule.MOYEN -> MeteoMoyen(m, style)
                    TailleModule.GRAND -> MeteoGrand(m, style)
                }
            }
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun titre(reglages: ReglagesModule): String {
        val lieu = Lieu.decoder(reglages.texte(CLE_LIEU)) ?: Lieu.decoder(reglages.texte(CLE_AUTO))
        return if (lieu != null) "Météo · ${lieu.nom}" else nom
    }

    override fun resumeReglages(reglages: ReglagesModule): String {
        val choisi = Lieu.decoder(reglages.texte(CLE_LIEU))
        if (choisi != null) return choisi.nom
        val detecte = Lieu.decoder(reglages.texte(CLE_AUTO))
        return "Position automatique" + (detecte?.let { " (${it.nom})" } ?: "")
    }

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        var recherche by remember { mutableStateOf(false) }
        if (recherche) {
            RechercheVille(
                onChoisir = { lieu ->
                    reglages.mettre(CLE_LIEU, lieu.encoder())
                    recherche = false
                },
                onRetour = { recherche = false },
            )
            return
        }
        val choisi = Lieu.decoder(reglages.texte(CLE_LIEU))
        val detecte = Lieu.decoder(reglages.texte(CLE_AUTO))
        val villes = if (choisi != null && VILLES.none { it.nom == choisi.nom }) listOf(choisi) + VILLES else VILLES
        val lignes = buildList {
            add(
                LigneListe(
                    "Position automatique",
                    detecte?.let { "Détectée : ${it.nom} · OK pour relancer" }
                        ?: "D'après la connexion internet",
                    coche = choisi == null,
                ) {
                    reglages.mettre(CLE_LIEU, null)
                    reglages.mettre(CLE_AUTO, null)
                    donnees.invalider("meteo:auto")
                },
            )
            add(LigneListe("Rechercher une ville…", "Avec le clavier de l'appareil") { recherche = true })
            add(LigneListe("GRANDES VILLES", section = true))
            villes.forEach { v -> add(LigneListe(v.nom, coche = choisi?.nom == v.nom) { reglages.mettre(CLE_LIEU, v.encoder()) }) }
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Météo",
            sousTitre = "Ville affichée par le module",
            lignes = lignes,
            onDismiss = fermer,
            focusInitial = lignes.indexOfFirst { it.coche == true }.coerceAtLeast(0),
        )
    }
}

// ------------------------------------------------------------------ rendus

private fun degres(t: Double): String {
    val r = t.roundToInt()
    return "${if (r == 0) 0 else r}°"
}

/** « Pluie vers 18:30 », « Pluie en cours » ou « Pas de pluie dans l'heure ». */
private fun textePluie(m: MeteoDonnees, court: Boolean): String {
    val debut = m.pluieDebut ?: return if (court) "Sec dans l'heure" else "Pas de pluie dans l'heure"
    if (debut <= System.currentTimeMillis() + 60_000L) return "Pluie en cours"
    return "Pluie vers " + SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(debut))
}

private fun couleurPluie(style: NbaStyle): Color =
    if (style.texte.luminance() < 0.5f) Color(0xFF1F6FC2) else Color(0xFF7CC0FF)

private fun jourCourt(t: Long): String =
    SimpleDateFormat("EEE", Locale.FRENCH).format(Date(t)).trimEnd('.').uppercase(Locale.FRENCH).take(3)

/** Bloc « maintenant » : ville, icône + température, ciel et ressenti, pluie. */
@Composable
private fun Maintenant(m: MeteoDonnees, style: NbaStyle, modifier: Modifier) {
    Column(modifier) {
        Text(
            "MÉTÉO · " + m.lieu.uppercase(Locale.FRENCH), color = style.texteDiscret, fontSize = 9.us,
            letterSpacing = 2.us, fontWeight = FontWeight.Bold, style = ligne(12.us),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.u))
        Row(Modifier.height(46.u), verticalAlignment = Alignment.CenterVertically) {
            IconeMeteo(m.code, m.jour, style.texte, Modifier.size(46.u))
            Spacer(Modifier.width(8.u))
            Text(degres(m.temperature), color = style.texte, fontSize = 34.us, fontWeight = FontWeight.Light, style = ligne(40.us), maxLines = 1)
        }
        Spacer(Modifier.height(4.u))
        Text(
            CielMeteo.de(m.code).libelle + " · ressenti " + degres(m.ressenti), color = style.texte,
            fontSize = 11.us, style = ligne(15.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            textePluie(m, court = false),
            color = if (m.pluieDebut != null) couleurPluie(style) else style.texteDiscret,
            fontSize = 11.us, fontWeight = if (m.pluieDebut != null) FontWeight.SemiBold else FontWeight.Normal,
            style = ligne(15.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Une ligne de prévision : jour, icône, max et min. */
@Composable
private fun LigneJour(j: MeteoJour, style: NbaStyle, hauteur: Int, avecPluie: Boolean) {
    Row(Modifier.fillMaxWidth().height(hauteur.u), verticalAlignment = Alignment.CenterVertically) {
        Text(jourCourt(j.date), color = style.texteDiscret, fontSize = 11.us, fontWeight = FontWeight.Bold, style = ligne(14.us), modifier = Modifier.width(32.u), maxLines = 1)
        IconeMeteo(j.code, true, style.texte, Modifier.size(22.u))
        if (avecPluie && j.probaPluie != null && j.probaPluie >= 20) {
            Spacer(Modifier.width(6.u))
            Text("${j.probaPluie} %", color = couleurPluie(style), fontSize = 10.us, style = ligne(13.us), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(degres(j.max), color = style.texte, fontSize = 12.us, fontWeight = FontWeight.SemiBold, style = ligne(15.us), maxLines = 1, softWrap = false)
        Spacer(Modifier.width(5.u))
        Text(degres(j.min), color = style.texteDiscret, fontSize = 12.us, style = ligne(15.us), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun MeteoMoyen(m: MeteoDonnees, style: NbaStyle) {
    Row(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalAlignment = Alignment.CenterVertically) {
        Maintenant(m, style, Modifier.weight(1f))
        if (m.jours.isNotEmpty()) {
            Spacer(Modifier.width(8.u))
            Spacer(Modifier.fillMaxHeight().padding(vertical = 6.u).width(1.u).background(style.filet))
            Spacer(Modifier.width(10.u))
            Column(Modifier.width(118.u), verticalArrangement = Arrangement.Center) {
                m.jours.take(3).forEach { LigneJour(it, style, 33, avecPluie = false) }
            }
        }
    }
}

@Composable
private fun MeteoGrand(m: MeteoDonnees, style: NbaStyle) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.u, vertical = 8.u)) {
        Maintenant(m, style, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.u))
        Spacer(Modifier.fillMaxWidth().height(1.u).background(style.filet))
        Spacer(Modifier.height(4.u))
        m.jours.take(3).forEach { LigneJour(it, style, 34, avecPluie = true) }
    }
}

@Composable
private fun MeteoPetit(m: MeteoDonnees, style: NbaStyle) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalArrangement = Arrangement.Center) {
        Text(
            m.lieu.uppercase(Locale.FRENCH), color = style.texteDiscret, fontSize = 9.us,
            letterSpacing = 2.us, fontWeight = FontWeight.Bold, style = ligne(12.us),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.u))
        Row(Modifier.height(40.u), verticalAlignment = Alignment.CenterVertically) {
            IconeMeteo(m.code, m.jour, style.texte, Modifier.size(40.u))
            Spacer(Modifier.width(6.u))
            Text(degres(m.temperature), color = style.texte, fontSize = 30.us, fontWeight = FontWeight.Light, style = ligne(34.us), maxLines = 1)
        }
        Spacer(Modifier.height(4.u))
        Text(
            CielMeteo.de(m.code).libelle, color = style.texte, fontSize = 11.us, style = ligne(14.us),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val ecart = abs(m.ressenti - m.temperature) >= 1.5
        Text(
            if (m.pluieDebut != null || !ecart) textePluie(m, court = true) else "Ressenti " + degres(m.ressenti),
            color = if (m.pluieDebut != null) couleurPluie(style) else style.texteDiscret,
            fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ recherche d'une ville

/**
 * Saisie d'un nom de ville au clavier de l'appareil, puis choix parmi les résultats ; aux
 * couleurs du design, comme les autres dialogues de Réglages (habillageDialogue).
 */
@Composable
private fun RechercheVille(onChoisir: (Lieu) -> Unit, onRetour: () -> Unit) {
    val h = habillageDialogue()
    var texte by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Pair<Lieu, String>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var champFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val champ = remember { FocusRequester() }
    val lancer = {
        val q = texte.trim()
        if (q.length >= 2) {
            message = "Recherche…"
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { MeteoReseau.rechercher(q) } }
                resultats = r.getOrDefault(emptyList())
                message = when {
                    r.isFailure -> "Recherche impossible (connexion ?)"
                    resultats.isEmpty() -> "Aucune ville trouvée"
                    else -> null
                }
            }
        }
    }
    Dialog(onDismissRequest = onRetour) {
        Column(
            Modifier
                .width(460.u)
                .fondDialogue(h)
                .padding(22.u)
        ) {
            Text("Rechercher une ville", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.u))
            Text("Tape le nom, puis « Rechercher » sur le clavier", color = h.discret, fontSize = 12.us)
            Spacer(Modifier.height(12.u))
            BasicTextField(
                value = texte,
                onValueChange = { texte = it },
                singleLine = true,
                textStyle = TextStyle(color = h.texte, fontSize = 18.us),
                cursorBrush = SolidColor(h.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { lancer() }, onDone = { lancer() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(champ)
                    .onFocusChanged { champFocus = it.isFocused }
                    .background(h.carte, RoundedCornerShape(12.u))
                    .border(2.u, if (champFocus) h.texte else h.texte.copy(alpha = 0.2f), RoundedCornerShape(12.u))
                    .padding(horizontal = 14.u, vertical = 10.u),
            )
            Spacer(Modifier.height(8.u))
            message?.let {
                Text(it, color = h.discret, fontSize = 12.us, modifier = Modifier.padding(4.u))
            }
            LazyColumn(Modifier.heightIn(max = 300.u), verticalArrangement = Arrangement.spacedBy(4.u)) {
                item(key = "chercher") {
                    LigneCliquable(
                        selected = false, onClick = { lancer() }, headlineContent = { Text("Rechercher « ${texte.trim()} »", fontSize = 15.us) },
                        colors = couleursLigne(h), shape = ListItemDefaults.shape(shape = h.formeLigne),
                    )
                }
                items(resultats, key = { "${it.first.lat},${it.first.lon}" }) { (lieu, detail) ->
                    LigneCliquable(
                        selected = false,
                        onClick = { onChoisir(lieu) },
                        headlineContent = { Text(lieu.nom, fontSize = 15.us) },
                        supportingContent = { Text(detail, fontSize = 12.us, maxLines = 1) },
                        colors = couleursLigne(h), shape = ListItemDefaults.shape(shape = h.formeLigne),
                    )
                }
                item(key = "retour") {
                    LigneCliquable(
                        selected = false, onClick = onRetour, headlineContent = { Text("Retour", fontSize = 15.us) },
                        colors = couleursLigne(h), shape = ListItemDefaults.shape(shape = h.formeLigne),
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            runCatching { champ.requestFocus() }
        }
    }
}
