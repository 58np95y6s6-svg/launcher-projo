package fr.projo.launcher.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaNight
import fr.projo.launcher.data.NbaTeam
import fr.projo.launcher.data.PrefsState
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Données factices des tests : apps « installées » dans le PackageManager de Robolectric,
 * avec des icônes dessinées ici (couleurs franches façon Netflix, Prime Video…),
 * une bannière TV pour YouTube, et des matchs NBA sans aucun accès réseau.
 */
object DonneesFactices {

    /** Une app factice : paquet, nom, icône et éventuelle bannière TV (android:banner). */
    class Faux(
        val paquet: String,
        val nom: String,
        val icone: (Resources) -> Drawable,
        val banniere: ((Resources) -> Drawable)? = null,
    )

    val favoris = listOf(
        // Bannière blanche comme la vraie (carte blanche sur fond teinté de rouge en Aurore)
        Faux("com.netflix.ninja", "Netflix", pleine(0xFFE50914, "N", BLANC), banniere = banniereTexte("NETFLIX", 0xFFE50914.toInt())),
        Faux("com.amazon.amazonvideo.livingroom", "Prime Video", pleine(0xFF00A8E1, "prime", BLANC)),
        Faux("com.canal.android.canal", "Canal+", pleine(0xFF000000, "CANAL+", BLANC)),
        Faux("com.teamsmart.videomanager.tv", "SmartTube", detouree(0xFFE53935)),
        Faux("tv.twitch.android.app", "Twitch", pleine(0xFF9146FF, "T", BLANC), banniere = banniereTexte("twitch", 0xFF9146FF.toInt())),
        Faux("com.google.android.youtube.tv", "YouTube", lecture(0xFFFF0000), banniere = banniereYoutube()),
    )

    val mesApps = listOf(
        Faux("com.disney.disneyplus", "Disney+", adaptative(0xFF0B1A4B, 0xFF1E5DB8, "D+", BLANC)),
        Faux("com.spotify.tv.android", "Spotify", disque(0xFF1DB954)),
        Faux("com.plexapp.android", "Plex", adaptative(0xFF1F1F1F, 0xFF2B2B2B, "P", 0xFFE5A00D.toInt())),
        Faux("org.videolan.vlc", "VLC", cone()),
        Faux("org.xbmc.kodi", "Kodi", pleine(0xFF17B2E7, "K", BLANC)),
        Faux("tv.molotov.app", "Molotov", pleine(0xFF1A1A1A, "M", 0xFFFF6F00.toInt())),
        Faux("tv.arte.plus7", "Arte", pleine(0xFFFA481C, "arte", BLANC)),
        Faux("fr.francetv.pluzz", "France TV", pleine(0xFF1B1464, "france.tv", BLANC)),
    )

    /** Apps installées mais ni en favori ni dans « Mes apps » (seulement dans « Toutes les apps »). */
    private val autres = listOf(
        Faux("com.deezer.android.tv", "Deezer", pleine(0xFFA238FF, "D", BLANC)),
        Faux("com.crunchyroll.crunchyroid", "Crunchyroll", pleine(0xFFF47521, "C", BLANC)),
        Faux("com.android.vending", "Play Store", lecture(0xFF01875F)),
    )

    private val toutes = favoris + mesApps + autres

    /** Paquets dont l'accueil affiche le visuel (préparés avant l'affichage). */
    val paquets: List<String> = toutes.map { it.paquet }

    /** Installe les apps factices (à refaire à chaque test : Robolectric remet tout à zéro). */
    fun installer(contexte: Context) {
        val pm = shadowOf(contexte.packageManager)
        val res = contexte.resources
        for (f in toutes) {
            val info = ApplicationInfo().apply {
                packageName = f.paquet
                nonLocalizedLabel = f.nom
                enabled = true
                if (f.banniere != null) banner = ID_BANNIERE
            }
            pm.installPackage(PackageInfo().apply { packageName = f.paquet; applicationInfo = info })
            pm.setApplicationIcon(f.paquet, f.icone(res))
            f.banniere?.let { ShadowPackageManager.resources[f.paquet] = RessourcesBanniere(res, it(res)) }
        }
    }

    /** Accueil chargé : 6 favoris, « Mes apps » = Source HDMI + 8 apps. */
    fun ui(design: Design): HomeUi {
        val favs = favoris.map { AppInfo(it.paquet, it.nom, isTvApp = true) }
        val apps = listOf(AppInfo.HDMI) + mesApps.map { AppInfo(it.paquet, it.nom, isTvApp = true) }
        val tout = toutes.map { AppInfo(it.paquet, it.nom, isTvApp = true) } + AppInfo.HDMI
        return HomeUi(
            loaded = true,
            favorites = favs,
            homeApps = apps,
            allApps = tout.sortedBy { it.label.lowercase() },
            prefs = PrefsState(
                favorites = favs.map { it.packageName },
                homeApps = apps.map { it.packageName },
                defaultsApplied = true,
                homeDefaultsApplied = true,
                design = design.id,
            ),
        )
    }

    /** Matchs NBA de la nuit (un en cours, les autres à venir) et un résultat : pas de logo, pas de réseau. */
    fun nba(): NbaData {
        val t = System.currentTimeMillis()
        val jour = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))
        val nuit = NbaNight(
            jour, true,
            listOf(
                NbaGame("1", t - 3_600_000, NbaTeam("LAL", null, "87"), NbaTeam("BOS", null, "91"), "in", "Q3 4:12"),
                NbaGame("2", t + 3_600_000, NbaTeam("GSW", null, null), NbaTeam("NYK", null, null), "pre", ""),
                NbaGame("3", t + 7_200_000, NbaTeam("MIA", null, null), NbaTeam("DEN", null, null), "pre", ""),
                NbaGame("4", t + 9_000_000, NbaTeam("PHX", null, null), NbaTeam("DAL", null, null), "pre", ""),
            ),
        )
        val resultats = NbaNight(
            jour, true,
            listOf(NbaGame("5", t - 86_400_000, NbaTeam("CHI", null, "110"), NbaTeam("MIL", null, "104"), "post", "Final")),
        )
        return NbaData(nuit, resultats)
    }

    // ------------------------------------------------------------------ dessins des icônes

    private const val TAILLE = 192
    private const val ID_BANNIERE = 0x7f7f0001
    /** Blanc opaque (constante : utilisée dès l'initialisation des listes plus haut). */
    private const val BLANC: Int = -1

    private fun icone(dessin: (Canvas) -> Unit): (Resources) -> Drawable = { res ->
        val b = Bitmap.createBitmap(TAILLE, TAILLE, Bitmap.Config.ARGB_8888)
        dessin(Canvas(b))
        BitmapDrawable(res, b)
    }

    private fun peinture(couleur: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleur }

    /** Texte centré en gras, réduit pour tenir dans [largeurMax]. */
    private fun texte(c: Canvas, t: String, couleur: Int, cx: Float, cy: Float, largeurMax: Float, taille: Float = 120f) {
        val p = peinture(couleur).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = taille
        }
        val w = p.measureText(t)
        if (w > largeurMax) p.textSize = taille * largeurMax / w
        val fm = p.fontMetrics
        c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    /** Triangle « lecture » centré en ([cx], [cy]), de demi-taille [r]. */
    private fun triangle(c: Canvas, cx: Float, cy: Float, r: Float, couleur: Int) {
        val chemin = Path().apply {
            moveTo(cx - r * 0.55f, cy - r * 0.65f)
            lineTo(cx + r * 0.75f, cy)
            lineTo(cx - r * 0.55f, cy + r * 0.65f)
            close()
        }
        c.drawPath(chemin, peinture(couleur))
    }

    /** Icône carrée pleine (fond uni + texte), comme Netflix ou Prime Video. */
    private fun pleine(fond: Long, t: String, encre: Int) = icone { c ->
        c.drawColor(fond.toInt())
        texte(c, t, encre, TAILLE / 2f, TAILLE / 2f, TAILLE * 0.78f)
    }

    /** Icône pleine avec un triangle blanc (YouTube, Play Store). */
    private fun lecture(fond: Long) = icone { c ->
        c.drawColor(fond.toInt())
        triangle(c, TAILLE / 2f, TAILLE / 2f, 52f, BLANC)
    }

    /** Icône détourée (fond transparent) : rectangle arrondi rouge et triangle (SmartTube). */
    private fun detouree(couleur: Long) = icone { c ->
        c.drawRoundRect(RectF(22f, 46f, 170f, 146f), 28f, 28f, peinture(couleur.toInt()))
        triangle(c, 98f, 96f, 34f, BLANC)
    }

    /** Disque vert et trois arcs noirs (Spotify), fond transparent. */
    private fun disque(couleur: Long) = icone { c ->
        c.drawCircle(96f, 96f, 84f, peinture(couleur.toInt()))
        val trait = peinture(android.graphics.Color.BLACK).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        for ((i, y) in listOf(70f, 98f, 124f).withIndex()) {
            trait.strokeWidth = 16f - i * 3f
            c.drawArc(RectF(46f + i * 8, y - 10, 146f - i * 8, y + 40), 200f, 140f, false, trait)
        }
    }

    /** Cône orange rayé de blanc (VLC), fond transparent. */
    private fun cone() = icone { c ->
        val orange = peinture(0xFFFF8800.toInt())
        val chemin = Path().apply { moveTo(96f, 18f); lineTo(150f, 160f); lineTo(42f, 160f); close() }
        c.drawPath(chemin, orange)
        c.drawRect(RectF(64f, 76f, 128f, 92f), peinture(BLANC))
        c.drawRect(RectF(54f, 116f, 138f, 132f), peinture(BLANC))
        c.drawRoundRect(RectF(26f, 152f, 166f, 176f), 10f, 10f, orange)
    }

    /** Icône adaptative (Android 8+) : fond en dégradé + logo sur un calque séparé. */
    private fun adaptative(haut: Long, bas: Long, t: String, encre: Int): (Resources) -> Drawable = { res ->
        val n = 216 // calque de 108 unités
        val fond = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Canvas(fond).drawPaint(
            Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, n.toFloat(), haut.toInt(), bas.toInt(), android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        val logo = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        texte(Canvas(logo), t, encre, n / 2f, n / 2f, n * 0.5f, taille = 90f)
        AdaptiveIconDrawable(BitmapDrawable(res, fond), BitmapDrawable(res, logo))
    }

    /** Bannière TV 16:9 de YouTube : fond blanc, logo rouge et nom. */
    private fun banniereYoutube(): (Resources) -> Drawable = { res ->
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(BLANC)
        c.drawRoundRect(RectF(40f, 62f, 112f, 118f), 16f, 16f, peinture(0xFFFF0000.toInt()))
        triangle(c, 77f, 90f, 18f, BLANC)
        texte(c, "YouTube", 0xFF212121.toInt(), 204f, 90f, 170f, taille = 44f)
        BitmapDrawable(res, b)
    }

    /** Bannière TV 16:9 sur fond blanc avec le nom de l'app en couleur (Netflix, Twitch). */
    private fun banniereTexte(t: String, encre: Int): (Resources) -> Drawable = { res ->
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(BLANC)
        texte(c, t, encre, 160f, 90f, 240f, taille = 64f)
        BitmapDrawable(res, b)
    }

    /** Ressources d'une app factice : ne contiennent que sa bannière. */
    @Suppress("DEPRECATION")
    private class RessourcesBanniere(base: Resources, private val banniere: Drawable) :
        Resources(base.assets, base.displayMetrics, base.configuration) {
        override fun getDrawableForDensity(id: Int, density: Int, theme: Resources.Theme?): Drawable? =
            if (id == ID_BANNIERE) banniere else null
    }
}
