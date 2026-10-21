package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.BuildConfig

/** Format d'une clé TMDB : clé v3 (32 caractères hexadécimaux) ou jeton v4 (« eyJ… »). */
fun cleTmdbValide(cle: String): Boolean {
    val c = cle.trim()
    return Regex("[0-9a-fA-F]{32}").matches(c) || (c.startsWith("eyJ") && c.length > 100 && Regex("[A-Za-z0-9._-]+").matches(c))
}

/** Résumé de la clé pour Réglages (jamais la clé elle-même). */
fun resumeCleTmdb(context: android.content.Context): String = when {
    StudioCle.saisie(context) != null -> "Clé saisie sur cet appareil · changer"
    BuildConfig.TMDB_KEY.isNotBlank() -> "Clé intégrée à l'app · en saisir une autre"
    else -> "Désactivées · saisir une clé TMDB (gratuite)"
}

/**
 * Saisie de la clé TMDB (design Studio), au clavier de la TV ou du téléphone : elle remplace
 * la clé intégrée à l'app. « Effacer » revient à la clé intégrée (ou au repli sans clé).
 */
@Composable
fun StudioCleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = LocalDesign.current.colors()
    var texte by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    val enregistrer: () -> Unit = {
        if (cleTmdbValide(texte)) {
            StudioCle.ecrire(context, texte)
            onDismiss()
        } else {
            erreur = "Format inconnu : clé API (32 caractères) ou jeton d'accès (eyJ…)"
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            for (essai in 1..4) {
                withFrameNanos {}
                if (runCatching { focus.requestFocus() }.isSuccess) break
            }
        }
        Column(Modifier.width(520.u).background(c.panneau, RoundedCornerShape(20.u)).padding(22.u)) {
            Text("Suggestions de films et séries", color = c.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.u))
            Text(
                "Clé gratuite sur themoviedb.org (Paramètres → API). ${resumeCleTmdb(context)}.",
                color = c.texteDiscret, fontSize = 12.us,
            )
            Spacer(Modifier.height(14.u))
            BasicTextField(
                value = texte,
                onValueChange = { texte = it.trim().take(400); erreur = null },
                singleLine = true,
                textStyle = TextStyle(color = c.texte, fontSize = 16.us),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { enregistrer() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(c.carte, RoundedCornerShape(10.u))
                    .padding(12.u),
            )
            erreur?.let {
                Spacer(Modifier.height(6.u))
                Text(it, color = c.accent, fontSize = 12.us)
            }
            Spacer(Modifier.height(16.u))
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                BoutonStudio("Enregistrer", accent = true, onClick = enregistrer)
                BoutonStudio("Effacer") { StudioCle.ecrire(context, null); onDismiss() }
                BoutonStudio("Annuler", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun BoutonStudio(texte: String, accent: Boolean = false, onClick: () -> Unit) {
    val c = LocalDesign.current.colors()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.u)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) c.accent.copy(alpha = 0.28f) else c.carte,
            contentColor = c.texte,
            focusedContainerColor = c.focusFond,
            focusedContentColor = c.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(texte, fontSize = 15.us, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.u, vertical = 10.u))
    }
}
