package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Salutation + grande horloge + date, mises à jour chaque minute pile. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = System.currentTimeMillis()
            // Réveil au début de la minute suivante (pas de rafraîchissement inutile)
            delay(60_000L - maintenant % 60_000L + 50L)
        }
    }
    val date = Date(maintenant)
    val heure = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
    val salutation = when (heure) {
        in 5..11 -> "BONJOUR"
        in 12..17 -> "BON APRÈS-MIDI"
        else -> "BONSOIR"
    }
    val fr = Locale.FRENCH
    val heureTexte = SimpleDateFormat("HH:mm", fr).format(date)
    val dateTexte = SimpleDateFormat("EEEE d MMMM", fr).format(date).uppercase(fr)
    Column(modifier) {
        Text(
            salutation, color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.us, letterSpacing = 4.us, fontWeight = FontWeight.Medium,
        )
        Text(
            heureTexte, color = Color.White,
            fontSize = 72.us, fontWeight = FontWeight.Thin, letterSpacing = (-1).us,
        )
        Text(
            dateTexte, color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.us, letterSpacing = 3.us, fontWeight = FontWeight.Medium,
        )
    }
}
