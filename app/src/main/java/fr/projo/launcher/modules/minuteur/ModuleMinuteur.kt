package fr.projo.launcher.modules.minuteur

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import fr.projo.launcher.modules.LocalStyleModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import fr.projo.launcher.ui.SurfaceCliquable
import fr.projo.launcher.ui.cliquableSansFocus

/** Préréglages proposés (minutes). */
private val PREREGLAGES = listOf(5, 10, 15, 30, 45, 60)

/** Pas de ▲▼ sur la durée : 1 min jusqu'à 10, puis 5 min jusqu'à 1 h, puis 15 min. */
private fun pasPlus(m: Int): Int = when {
    m < 10 -> m + 1
    m < 60 -> (m / 5 + 1) * 5
    else -> (m / 15 + 1) * 15
}.coerceAtMost(Minuteur.MINUTES_MAX)

private fun pasMoins(m: Int): Int = when {
    m <= 10 -> m - 1
    m <= 60 -> ((m - 1) / 5) * 5
    else -> ((m - 1) / 15) * 15
}.coerceAtLeast(1)

private fun heureMinutes(minutesDuJour: Int) = String.format(Locale.FRENCH, "%02d:%02d", minutesDuJour / 60, minutesDuJour % 60)

private fun heure(t: Long) = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

/** Afficheur au repos : « 15:00 », « 1:30:00 ». */
private fun texteReglage(minutes: Int) = Minuteur.formaterDuree(minutes * 60_000L)

/**
 * Module Minuteur et réveil (interactif) :
 *  - à l'arrêt : afficheur de la durée (▲▼ pour l'ajuster, OK pour démarrer), préréglages
 *    (OK démarre aussitôt) et « Réveil » (heure et minutes au ▲▼, OK pour l'armer) ;
 *  - en cours : décompte, OK sur l'afficheur = pause / reprise, boutons Pause, +1 min, Annuler ;
 *  - à l'échéance : « Arrêter », « +5 min », « Relancer ».
 * Le minuteur continue hors de Projo (AlarmManager + notification) : voir [Minuteur].
 * Navigation : ◀ sur l'afficheur (élément le plus à gauche) et Retour quittent la zone ;
 * ▲▼ sur l'afficheur règlent la valeur au lieu de déplacer le focus.
 */
object ModuleMinuteur : Module(
    id = "minuteur",
    nom = "Minuteur et réveil",
    description = "Compte à rebours ou réveil, avec alarme même hors de Projo",
    icone = IconeMinuteur,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val context = LocalContext.current
        val etat by remember(context) { Minuteur.flux(context) }.collectAsState()
        var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
        // Réglage de l'heure du réveil (à l'arrêt) au lieu de la durée
        var reglageReveil by remember { mutableStateOf(false) }
        // Changement de disposition voulu par l'utilisateur : le focus revient sur l'afficheur
        var refocus by remember { mutableIntStateOf(0) }
        val demanderNotifications = rememberDemandeNotifications()

        // Échéance perdue (redémarrage) ou passée pendant que Projo était fermé
        LaunchedEffect(Unit) { runCatching { Minuteur.verifier(context) } }
        // Décompte à la seconde ; l'afficheur déclenche lui-même l'alarme s'il est à l'écran
        LaunchedEffect(etat.mode, etat.fin) {
            maintenant = System.currentTimeMillis()
            while (etat.mode == ModeMinuteur.EN_COURS) {
                val t = System.currentTimeMillis()
                maintenant = t
                if (t >= etat.fin) {
                    runCatching { Minuteur.echeance(context) }
                    break
                }
                delay((1000L - t % 1000L + 5L).coerceIn(50L, 1000L))
            }
        }
        LaunchedEffect(refocus) {
            if (refocus > 0) {
                withFrameNanos {}
                runCatching { ctx.entree.requestFocus() }
            }
        }

        val a = Actions(context, etat, demanderNotifications) { refocus++ }
        val vue = Vue(etat, maintenant, reglageReveil && etat.mode == ModeMinuteur.ARRET)
        val basculerReveil: () -> Unit = { reglageReveil = !reglageReveil; refocus++ }
        when (ctx.taille) {
            TailleModule.PETIT -> MinuteurPetit(ctx, vue, a, basculerReveil, modifier.height(ctx.hauteur.u))
            else -> MinuteurMoyen(ctx, vue, a, basculerReveil, modifier.height(ctx.hauteur.u))
        }
    }
}

/** Ce que l'interface affiche, calculé une fois. */
private class Vue(val etat: EtatMinuteur, maintenant: Long, val reglageReveil: Boolean) {
    val mode = etat.mode
    val restant = etat.restantA(maintenant)

    /** Texte de l'afficheur. */
    val afficheur: String = when (mode) {
        ModeMinuteur.ARRET -> texteReglage(etat.minutesChoisies)
        ModeMinuteur.EN_COURS -> if (etat.reveil) heure(etat.fin) else Minuteur.formaterDuree(restant)
        ModeMinuteur.PAUSE -> Minuteur.formaterDuree(restant)
        ModeMinuteur.SONNE -> if (etat.reveil) heure(etat.fin) else "00:00"
    }

    /** Titre du module. */
    val titre: String = when {
        mode == ModeMinuteur.SONNE -> if (etat.reveil) "RÉVEIL !" else "TEMPS ÉCOULÉ"
        mode == ModeMinuteur.PAUSE -> "EN PAUSE"
        mode == ModeMinuteur.EN_COURS && etat.reveil -> "RÉVEIL"
        mode == ModeMinuteur.EN_COURS -> "MINUTEUR"
        reglageReveil -> "RÉVEIL"
        else -> "MINUTEUR"
    }

    /** Ligne d'aide ou d'information sous l'afficheur. */
    fun aide(maintenant: Long): String = when (mode) {
        ModeMinuteur.ARRET -> if (reglageReveil) Minuteur.formaterDans(Minuteur.prochaineOccurrence(etat.heureReveil, maintenant) - maintenant)
        else "▲▼ régler · OK lancer"
        ModeMinuteur.EN_COURS -> if (etat.reveil) Minuteur.formaterDans(etat.fin - maintenant) else "Fin à " + heure(etat.fin)
        ModeMinuteur.PAUSE -> "OK pour reprendre"
        ModeMinuteur.SONNE -> "OK pour arrêter"
    }

    /** Avancement du décompte (0 → 1), null s'il n'y en a pas. */
    val progression: Float? = when {
        etat.reveil -> null
        mode == ModeMinuteur.EN_COURS || mode == ModeMinuteur.PAUSE ->
            if (etat.duree > 0) (1f - restant.toFloat() / etat.duree).coerceIn(0f, 1f) else null
        else -> null
    }

    /** ▲▼ sur l'afficheur : réglage de la durée, ou ±1 min pendant le décompte. */
    val ajustable = mode == ModeMinuteur.ARRET || (!etat.reveil && (mode == ModeMinuteur.EN_COURS || mode == ModeMinuteur.PAUSE))
}

/** Actions de l'interface (préférences + AlarmManager : rapides). */
private class Actions(
    private val context: Context,
    private val etat: EtatMinuteur,
    private val demanderNotifications: () -> Unit,
    private val refocus: () -> Unit,
) {
    fun lancer(minutes: Int) {
        Minuteur.demarrer(context, minutes)
        demanderNotifications()
        refocus()
    }

    /** OK sur l'afficheur. */
    fun afficheur() {
        when (etat.mode) {
            ModeMinuteur.ARRET -> lancer(etat.minutesChoisies)
            ModeMinuteur.EN_COURS -> if (!etat.reveil) Minuteur.pause(context)
            ModeMinuteur.PAUSE -> Minuteur.reprendre(context)
            ModeMinuteur.SONNE -> { Minuteur.arreter(context); refocus() }
        }
    }

    fun plus() {
        if (etat.mode == ModeMinuteur.ARRET) Minuteur.regler(context, pasPlus(etat.minutesChoisies))
        else Minuteur.ajouter(context, 60_000L)
    }

    fun moins() {
        if (etat.mode == ModeMinuteur.ARRET) Minuteur.regler(context, pasMoins(etat.minutesChoisies))
        else Minuteur.ajouter(context, -60_000L)
    }

    fun heureReveil(delta: Int) = Minuteur.reglerReveil(context, etat.heureReveil + delta)

    /** Minutes du réveil par pas de 5, sans changer l'heure. */
    fun minuteReveil(delta: Int) {
        val h = etat.heureReveil / 60
        val m = ((etat.heureReveil % 60) / 5 * 5 + delta + 60) % 60
        Minuteur.reglerReveil(context, h * 60 + m)
    }

    fun armerReveil() {
        Minuteur.armerReveil(context, etat.heureReveil)
        demanderNotifications()
        refocus()
    }

    fun annuler() {
        Minuteur.annuler(context)
        refocus()
    }

    fun pauseReprise() {
        if (etat.mode == ModeMinuteur.PAUSE) Minuteur.reprendre(context) else Minuteur.pause(context)
    }

    fun arreter() {
        Minuteur.arreter(context)
        refocus()
    }

    fun repeter() = Minuteur.repeter(context, 5)

    fun relancer() = Minuteur.relancer(context)

    /** Réveil armé : on revient au réglage de son heure. */
    fun modifierReveil() {
        Minuteur.annuler(context)
        refocus()
    }
}

/** Android 13+ : demande l'autorisation des notifications (décompte, alarme) au premier lancement. */
@Composable
private fun rememberDemandeNotifications(): () -> Unit {
    if (Build.VERSION.SDK_INT < 33) return remember { {} }
    val context = LocalContext.current
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        if (accordee) Minuteur.rafraichirNotification(context)
    }
    return remember(lanceur, context) {
        {
            val accordee = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!accordee) runCatching { lanceur.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}

// ------------------------------------------------------------------ MOYEN

@Composable
private fun MinuteurMoyen(
    ctx: ModuleContexte,
    vue: Vue,
    a: Actions,
    basculerReveil: () -> Unit,
    modifier: Modifier,
) {
    val style = ctx.style
    Row(modifier.fillMaxWidth().padding(horizontal = 4.u, vertical = 3.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(138.u).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Titre(vue.titre, petit = false)
            Spacer(Modifier.height(4.u))
            Cadran(ctx, vue, a, hauteur = 58, petit = false)
            Spacer(Modifier.height(5.u))
            Aide(vue, petit = false)
        }
        Spacer(Modifier.width(7.u))
        Box(Modifier.fillMaxHeight().padding(vertical = 8.u).width(1.u).background(style.filet))
        Spacer(Modifier.width(8.u))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            when {
                vue.mode == ModeMinuteur.ARRET && vue.reglageReveil -> {
                    Touche("Activer le réveil", { a.armerReveil() }, Modifier.fillMaxWidth().height(28.u), tag = "minuteur:activer")
                    Spacer(Modifier.height(6.u))
                    Touche("Minuteur", basculerReveil, Modifier.fillMaxWidth().height(28.u), tag = "minuteur:bascule")
                }
                vue.mode == ModeMinuteur.ARRET -> {
                    GrillePrereglages(vue, a, colonnes = 3, hauteur = 26, petit = false)
                    Spacer(Modifier.height(5.u))
                    Touche("Réveil à une heure…", basculerReveil, Modifier.fillMaxWidth().height(26.u), tag = "minuteur:bascule")
                }
                else -> BoutonsEnCours(vue, a, petit = false)
            }
        }
    }
}

// ------------------------------------------------------------------ PETIT

@Composable
private fun MinuteurPetit(
    ctx: ModuleContexte,
    vue: Vue,
    a: Actions,
    basculerReveil: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 3.u, vertical = 2.u), verticalArrangement = Arrangement.Center) {
        Titre(vue.titre, petit = true)
        Spacer(Modifier.height(3.u))
        Cadran(ctx, vue, a, hauteur = 42, petit = true)
        Spacer(Modifier.height(5.u))
        when {
            vue.mode == ModeMinuteur.ARRET && vue.reglageReveil -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.u)) {
                    Touche("Activer", { a.armerReveil() }, Modifier.weight(1f).height(24.u), petit = true, tag = "minuteur:activer")
                    Touche("Minuteur", basculerReveil, Modifier.weight(1f).height(24.u), petit = true, tag = "minuteur:bascule")
                }
                Spacer(Modifier.height(4.u))
                Aide(vue, petit = true)
            }
            vue.mode == ModeMinuteur.ARRET -> {
                // 5 · 10 · 15 · 30 / 45 · 1 h · Réveil
                val ligne1 = PREREGLAGES.take(4)
                val ligne2 = PREREGLAGES.drop(4)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
                    ligne1.forEach { m -> Prereglage(m, vue, a, Modifier.weight(1f).height(23.u), petit = true) }
                }
                Spacer(Modifier.height(3.u))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
                    ligne2.forEach { m -> Prereglage(m, vue, a, Modifier.weight(1f).height(23.u), petit = true) }
                    Touche("Réveil", basculerReveil, Modifier.weight(2f).height(23.u), petit = true, tag = "minuteur:bascule")
                }
            }
            else -> BoutonsEnCours(vue, a, petit = true)
        }
    }
}

// ------------------------------------------------------------------ éléments

@Composable
private fun Titre(texte: String, petit: Boolean) {
    val style = LocalStyleModule.current
    Text(
        texte, color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
        fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.u),
    )
}

@Composable
private fun Aide(vue: Vue, petit: Boolean) {
    val style = LocalStyleModule.current
    Text(
        vue.aide(System.currentTimeMillis()), color = style.texteDiscret, fontSize = (if (petit) 10 else 11).us,
        style = ligne(14.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.u),
    )
}

/** Afficheur (ou heure + minutes du réveil) : premier élément focalisable du module. */
@Composable
private fun Cadran(ctx: ModuleContexte, vue: Vue, a: Actions, hauteur: Int, petit: Boolean) {
    if (vue.mode == ModeMinuteur.ARRET && vue.reglageReveil) {
        val h = vue.etat.heureReveil
        Row(Modifier.fillMaxWidth().height(hauteur.u), verticalAlignment = Alignment.CenterVertically) {
            Afficheur(
                String.format(Locale.FRENCH, "%02d", h / 60), petit, ajustable = true,
                onClick = { a.armerReveil() }, onPlus = { a.heureReveil(60) }, onMoins = { a.heureReveil(-60) },
                modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(ctx.entree).testTag("minuteur:heures"),
            )
            Text(
                ":", color = LocalStyleModule.current.texte, fontSize = (if (petit) 24 else 28).us,
                style = ligne(32.us), modifier = Modifier.padding(horizontal = 2.u),
            )
            Afficheur(
                String.format(Locale.FRENCH, "%02d", h % 60), petit, ajustable = true,
                onClick = { a.armerReveil() }, onPlus = { a.minuteReveil(5) }, onMoins = { a.minuteReveil(-5) },
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("minuteur:minutes"),
            )
        }
    } else {
        Afficheur(
            vue.afficheur, petit, ajustable = vue.ajustable, progression = vue.progression,
            alerte = vue.mode == ModeMinuteur.SONNE,
            onClick = { a.afficheur() }, onPlus = { a.plus() }, onMoins = { a.moins() },
            modifier = Modifier.fillMaxWidth().height(hauteur.u).focusRequester(ctx.entree).testTag("minuteur:afficheur"),
        )
    }
}

/**
 * Grand chiffre focalisable. ▲▼ (si [ajustable]) appellent [onPlus] / [onMoins] et ne
 * déplacent pas le focus ; ◀ ▶ et Retour restent à la zone.
 */
@Composable
private fun Afficheur(
    texte: String,
    petit: Boolean,
    ajustable: Boolean,
    onClick: () -> Unit,
    onPlus: () -> Unit,
    onMoins: () -> Unit,
    modifier: Modifier,
    progression: Float? = null,
    alerte: Boolean = false,
) {
    val style = LocalStyleModule.current
    val forme = RoundedCornerShape(10.u)
    val taille = when {
        texte.length <= 2 -> if (petit) 26 else 30
        texte.length <= 5 -> if (petit) 30 else 36
        else -> if (petit) 24 else 27
    }
    SurfaceCliquable(
        onClick = onClick,
        modifier = modifier.onPreviewKeyEvent {
            if (!ajustable) return@onPreviewKeyEvent false
            when (it.key) {
                Key.DirectionUp -> { if (it.type == KeyEventType.KeyDown) onPlus(); true }
                Key.DirectionDown -> { if (it.type == KeyEventType.KeyDown) onMoins(); true }
                else -> false
            }
        },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (alerte) style.ongletActif else Color.Transparent,
            contentColor = style.texte,
            focusedContainerColor = style.ongletActif,
            focusedContentColor = style.texte,
            pressedContainerColor = style.ongletActif,
            pressedContentColor = style.texte,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, style.filet), shape = forme),
            focusedBorder = Border(BorderStroke(2.u, style.focusFond.copy(alpha = 0.85f)), shape = forme),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                texte, fontSize = taille.us, fontWeight = FontWeight.Light, style = ligne((taille + 4).us),
                maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.Center),
            )
            if (ajustable) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 5.u),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Pointeur : les flèches dessinées règlent comme ▲ ▼ à la télécommande
                    Text(
                        "▲", color = style.texteDiscret, fontSize = 7.us, style = ligne(9.us), maxLines = 1,
                        modifier = Modifier.cliquableSansFocus(onPlus),
                    )
                    Text(
                        "▼", color = style.texteDiscret, fontSize = 7.us, style = ligne(9.us), maxLines = 1,
                        modifier = Modifier.cliquableSansFocus(onMoins),
                    )
                }
            }
            if (progression != null) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.u, vertical = 5.u)
                        .fillMaxWidth().height(3.u).clip(RoundedCornerShape(2.u)).background(style.filet),
                ) {
                    Box(Modifier.fillMaxWidth(progression).fillMaxHeight().background(style.texte))
                }
            }
        }
    }
}

/** Bouton texte du module : discret au repos, couleurs de focus du design. */
@Composable
private fun Touche(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier,
    petit: Boolean = false,
    actif: Boolean = false,
    tag: String? = null,
) {
    val style = LocalStyleModule.current
    val forme = RoundedCornerShape(8.u)
    SurfaceCliquable(
        onClick = onClick,
        modifier = if (tag != null) modifier.testTag(tag) else modifier,
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = style.ongletActif,
            contentColor = style.texte,
            focusedContainerColor = style.focusFond,
            focusedContentColor = style.focusTexte,
            pressedContainerColor = style.focusFond,
            pressedContentColor = style.focusTexte,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (actif) Border(BorderStroke(1.5f.u, style.texteDiscret), shape = forme) else Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 2.u), contentAlignment = Alignment.Center) {
            Text(
                texte, fontSize = (if (petit) 10 else 11).us, fontWeight = FontWeight.SemiBold,
                style = ligne(14.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Prereglage(minutes: Int, vue: Vue, a: Actions, modifier: Modifier, petit: Boolean) {
    val texte = when {
        minutes >= 60 -> "${minutes / 60} h"
        petit -> "$minutes"
        else -> "$minutes min"
    }
    Touche(
        texte, { a.lancer(minutes) }, modifier, petit = petit,
        actif = vue.etat.minutesChoisies == minutes, tag = "minuteur:prereglage:$minutes",
    )
}

@Composable
private fun ColumnScope.GrillePrereglages(vue: Vue, a: Actions, colonnes: Int, hauteur: Int, petit: Boolean) {
    PREREGLAGES.chunked(colonnes).forEachIndexed { i, rangee ->
        if (i > 0) Spacer(Modifier.height(5.u))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.u)) {
            rangee.forEach { m -> Prereglage(m, vue, a, Modifier.weight(1f).height(hauteur.u), petit) }
        }
    }
}

/**
 * Boutons pendant le décompte et à l'échéance. Mêmes emplacements dans les deux cas (le focus
 * reste en place quand l'alarme se déclenche) :
 *  1. Pause / Reprendre / Annuler (réveil) / Arrêter ;
 *  2. +1 min / Modifier (réveil) / +5 min ;
 *  3. Annuler / Relancer (absent pour un réveil armé).
 */
@Composable
private fun BoutonsEnCours(vue: Vue, a: Actions, petit: Boolean) {
    val e = vue.etat
    val sonne = vue.mode == ModeMinuteur.SONNE
    val t1: String
    val f1: () -> Unit
    when {
        sonne -> { t1 = "Arrêter"; f1 = { a.arreter() } }
        e.reveil -> { t1 = "Annuler"; f1 = { a.annuler() } }
        vue.mode == ModeMinuteur.PAUSE -> { t1 = "Reprendre"; f1 = { a.pauseReprise() } }
        else -> { t1 = "Pause"; f1 = { a.pauseReprise() } }
    }
    val t2: String
    val f2: () -> Unit
    when {
        sonne -> { t2 = "+5 min"; f2 = { a.repeter() } }
        e.reveil -> { t2 = "Modifier"; f2 = { a.modifierReveil() } }
        else -> { t2 = "+1 min"; f2 = { a.plus() } }
    }
    val t3: String? = when {
        sonne -> if (e.reveil) "Demain" else "Relancer"
        e.reveil -> null
        else -> "Annuler"
    }
    val f3: () -> Unit = { if (sonne) a.relancer() else a.annuler() }
    val h = if (petit) 23 else 28
    if (petit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            Touche(t1, f1, Modifier.weight(1f).height(h.u), petit = true, tag = "minuteur:bouton1")
            Touche(t2, f2, Modifier.weight(1f).height(h.u), petit = true, tag = "minuteur:bouton2")
        }
        if (t3 != null) {
            Spacer(Modifier.height(3.u))
            Touche(t3, f3, Modifier.fillMaxWidth().height(h.u), petit = true, tag = "minuteur:bouton3")
        }
    } else {
        Touche(t1, f1, Modifier.fillMaxWidth().height(h.u), tag = "minuteur:bouton1")
        Spacer(Modifier.height(5.u))
        Touche(t2, f2, Modifier.fillMaxWidth().height(h.u), tag = "minuteur:bouton2")
        if (t3 != null) {
            Spacer(Modifier.height(5.u))
            Touche(t3, f3, Modifier.fillMaxWidth().height(h.u), tag = "minuteur:bouton3")
        }
    }
}

/** Icône du module : chronomètre (24 × 24, blanc : teinté à l'affichage). */
val IconeMinuteur: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M12,5A8,8 0 1,1 11.99,5Z M12,9V13.2L14.6,15 M9.5,2.5H14.5 M18.6,5.4L20,6.8"
        ).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
