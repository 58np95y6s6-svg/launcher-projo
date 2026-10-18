package fr.projo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * L'interface est dessinée pour un écran de référence de 960 × 540 dp (téléviseur 1080p
 * standard). Ce facteur adapte toutes les tailles si l'appareil déclare une autre densité,
 * pour que le rendu soit identique quel que soit le projecteur.
 */
val LocalScale = compositionLocalOf { 1f }

/** Taille « unité d'interface » : 10.u = 10 dp sur un écran de référence. */
val Int.u: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).dp

/** Taille de texte mise à l'échelle de la même façon. */
val Int.us: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).sp
