package fr.projo.launcher.modules

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icônes des modules (24 × 24, vectorielles, blanches : teintées à l'affichage). */
object IconesModules {

    /** Ballon (Sport). */
    val Sport: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        trait("M12,3A9,9 0 1,1 11.99,3Z M12,3V21 M3,12H21 M5.6,5.6C9,9 9,15 5.6,18.4 M18.4,5.6C15,9 15,15 18.4,18.4")
    }.build()

    /** Soleil derrière un nuage (Météo). */
    val Meteo: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        trait("M9,8.2A3.6,3.6 0 0,1 15.2,6.4 M9,2.5V3.8 M3.6,8.6H4.9 M4.9,4.4L5.8,5.3 M13.1,4.4L12.2,5.3")
        addPath(
            pathData = PathParser().parsePathString(
                "M7,20A4,4 0 0,1 6.6,12.02A5.5,5.5 0 0,1 17.1,11.3A4.4,4.4 0 0,1 17.5,20Z"
            ).toNodes(),
            fill = SolidColor(Color.White),
        )
    }.build()

    private fun ImageVector.Builder.trait(chemin: String) {
        addPath(
            pathData = PathParser().parsePathString(chemin).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        )
    }
}
