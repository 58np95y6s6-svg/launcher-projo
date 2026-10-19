package fr.projo.launcher.modules.horloges

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Heure courante (ms), mise à jour au début de chaque minute (une seule recomposition par
 * minute, sans réseau). Sert aux horloges et à la position du soleil.
 */
@Composable
fun rememberMinute(): Long {
    val maintenant = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000L - t % 60_000L + 200L)
            maintenant.longValue = System.currentTimeMillis()
        }
    }
    return maintenant.longValue
}
