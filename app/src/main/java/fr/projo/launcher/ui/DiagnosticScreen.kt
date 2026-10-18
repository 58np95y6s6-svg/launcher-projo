package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écran de l'étape 1 : vérifie que le launcher voit les apps et qu'il peut devenir
 * l'écran d'accueil. Il sera remplacé par le vrai design à l'étape 3.
 */
@Composable
fun DiagnosticScreen(
    repo: AppRepository,
    ourPackage: String,
    onOpenHomeSettings: () -> Unit,
) {
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var accueil by remember { mutableStateOf<String?>(null) }
    var candidats by remember { mutableStateOf<List<String>>(emptyList()) }

    // Recharge à chaque retour sur l'écran (ex. après avoir changé l'accueil par défaut)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val (a, h, c) = withContext(Dispatchers.IO) {
                Triple(repo.loadApps(), repo.currentHomePackage(), repo.homeCandidates())
            }
            apps = a; accueil = h; candidats = c
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D12))
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            Text("Projo — test étape 1", fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color.White)
            Spacer(Modifier.height(12.dp))

            val estAccueil = accueil == ourPackage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        estAccueil -> "✅ Projo est l'écran d'accueil par défaut"
                        accueil == null -> "⚠️ Aucun écran d'accueil par défaut choisi"
                        else -> "⚠️ Écran d'accueil actuel : $accueil"
                    },
                    fontSize = 18.sp,
                    color = if (estAccueil) Color(0xFF6EE7A0) else Color(0xFFFFC266),
                )
                Spacer(Modifier.width(24.dp))
                Button(onClick = onOpenHomeSettings) { Text("Choisir l'écran d'accueil") }
            }
            Text(
                "Accueils disponibles : ${candidats.joinToString()}",
                fontSize = 14.sp, color = Color(0xFF9AA5B4),
            )
            Spacer(Modifier.height(16.dp))

            val liste = apps
            Text(
                if (liste == null) "Lecture des apps…" else "${liste.size} apps détectées (OK pour ouvrir)",
                fontSize = 18.sp, color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            if (liste != null) {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(liste, key = { it.packageName }) { app ->
                        ListItem(
                            selected = false,
                            onClick = { repo.launch(app) },
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName) },
                            trailingContent = { if (app.isTvApp) Text("TV") },
                        )
                    }
                }
            }
        }
    }
}
