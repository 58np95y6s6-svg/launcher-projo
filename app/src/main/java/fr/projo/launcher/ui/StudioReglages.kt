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
import androidx.tv.material3.Text
import fr.projo.launcher.BuildConfig
import fr.projo.launcher.data.CleTmdb

/** Résumé de la clé pour Réglages (jamais la clé elle-même). */
fun resumeCleTmdb(context: android.content.Context): String = when {
    CleTmdb.saisie(context) != null -> "Clé saisie sur cet appareil · changer"
    BuildConfig.TMDB_KEY.isNotBlank() -> "Clé intégrée à l'app · en saisir une autre"
    else -> "Désactivées · saisir une clé TMDB (gratuite)"
}

/**
 * Saisie de la clé TMDB (design Studio), au clavier de la TV ou du téléphone : elle remplace
 * la clé intégrée à l'app. « Effacer » revient à la clé intégrée (ou au repli sans clé).
 * Habillage du panneau Réglages du design (habillageDialogue), comme les autres dialogues.
 */
@Composable
fun StudioCleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val h = habillageDialogue()
    var texte by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    val enregistrer: () -> Unit = {
        if (CleTmdb.valide(texte)) {
            CleTmdb.ecrire(context, texte)
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
        Column(Modifier.width(520.u).background(h.fond, h.forme).padding(22.u)) {
            Text("Suggestions de films et séries", color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.u))
            Text(
                "Clé gratuite sur themoviedb.org (Paramètres → API). ${resumeCleTmdb(context)}.",
                color = h.discret, fontSize = 12.us,
            )
            Spacer(Modifier.height(14.u))
            BasicTextField(
                value = texte,
                onValueChange = { texte = it.trim().take(400); erreur = null },
                singleLine = true,
                textStyle = TextStyle(color = h.texte, fontSize = 16.us),
                cursorBrush = SolidColor(h.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { enregistrer() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(h.carte, RoundedCornerShape(10.u))
                    .padding(12.u),
            )
            erreur?.let {
                Spacer(Modifier.height(6.u))
                Text(it, color = h.alerte, fontSize = 12.us)
            }
            Spacer(Modifier.height(16.u))
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                BoutonDialogue("Enregistrer", accent = true, onClick = enregistrer)
                BoutonDialogue("Effacer") { CleTmdb.ecrire(context, null); onDismiss() }
                BoutonDialogue("Annuler", onClick = onDismiss)
            }
        }
    }
}
