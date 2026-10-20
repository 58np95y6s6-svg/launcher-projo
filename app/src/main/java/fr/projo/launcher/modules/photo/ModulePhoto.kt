package fr.projo.launcher.modules.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.LocalScale
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Module « Photo du jour » : la photo astronomique du jour de la NASA (APOD) en fond du module,
 * avec son titre sur un voile aux couleurs du design. Image décodée à la taille du module
 * (voir [PhotoReseau]) ; une seule photo téléchargée par jour.
 */
object ModulePhoto : Module(
    id = "photo",
    nom = "Photo du jour",
    description = "La photo astronomique du jour de la NASA",
    icone = IconePhoto,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN, TailleModule.GRAND),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        val etat = ctx.rememberDonnees(
            "photo:apod",
            // La NASA publie vers 6 h (heure de Paris) : on revérifie toutes les 3 h
            delai = { p: PhotoDuJour -> if (p.ancienne || p.jour != PhotoReseau.jour()) 3_600_000L else 3 * 3_600_000L },
            delaiErreur = 15 * 60_000L,
        ) { PhotoReseau.charger(context) }
        val style = ctx.style
        val photo = etat.valeur

        // Taille du module en pixels : l'image n'est jamais décodée plus grande
        val pxParU = LocalDensity.current.density * LocalScale.current
        val largeurPx = (ctx.largeur * pxParU).toInt().coerceAtLeast(1)
        val hauteurPx = (ctx.hauteur * pxParU).toInt().coerceAtLeast(1)
        val image by produceState<ImageBitmap?>(null, photo?.fichier, largeurPx, hauteurPx) {
            val f = photo?.fichier
            value = if (f == null) null else withContext(Dispatchers.IO) {
                runCatching { PhotoReseau.decoder(File(f), largeurPx, hauteurPx)?.asImageBitmap() }.getOrNull()
            }
        }
        val forme = RoundedCornerShape(10.u)
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            val img = image
            if (photo == null || img == null) {
                Text(
                    when {
                        photo != null || etat.chargement -> "Photo du jour…"
                        else -> "Photo du jour indisponible"
                    },
                    color = style.texteDiscret, fontSize = 11.us, style = ligne(14.us), textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.u),
                )
            } else Box(Modifier.fillMaxSize().padding(2.u).clip(forme)) {
                Image(
                    img, contentDescription = photo.titre, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Voile clair (design clair) ou sombre derrière le texte, d'après la couleur du texte
                val voile = if (style.texte.luminance() > 0.5f) Color.Black else Color.White
                val petit = ctx.taille == TailleModule.PETIT
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (petit) 72.u else 64.u)
                        .background(Brush.verticalGradient(listOf(voile.copy(alpha = 0f), voile.copy(alpha = 0.55f), voile.copy(alpha = 0.82f)))),
                )
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(start = 8.u, end = 8.u, bottom = 6.u),
                ) {
                    val etiquette = when {
                        photo.ancienne -> "PHOTO D'HIER · NASA"
                        photo.video -> "VIDÉO DU JOUR · NASA"
                        else -> "PHOTO DU JOUR · NASA"
                    }
                    Text(
                        etiquette, color = style.texteDiscret, fontSize = 8.us, letterSpacing = 1.us,
                        fontWeight = FontWeight.Bold, style = ligne(11.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.u))
                    Text(
                        photo.titre, color = style.texte,
                        fontSize = if (petit) 11.us else 13.us, fontWeight = FontWeight.SemiBold,
                        style = ligne(if (petit) 14.us else 16.us),
                        maxLines = if (ctx.taille == TailleModule.MOYEN) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Icône du module : une photo avec une étoile et des montagnes. */
private val IconePhoto: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M5,4H19A2,2 0 0,1 21,6V18A2,2 0 0,1 19,20H5A2,2 0 0,1 3,18V6A2,2 0 0,1 5,4Z" +
                    " M3.5,17L9,11.5L13,15.5L15.5,13L20.5,18 M16,7.2V9.8 M14.7,8.5H17.3",
            ).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}
