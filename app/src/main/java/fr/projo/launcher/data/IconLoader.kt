package fr.projo.launcher.data

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Format d'image d'une app, choisi selon la forme de l'endroit qui l'affiche :
 * un logo n'est jamais coupé parce que l'image a déjà la bonne forme.
 */
enum class FormatVisuel(val largeur: Int, val hauteur: Int, /** Place réservée en mémoire (octets). */ val cache: Int) {
    /** 16:9 : tuiles larges (favoris, bibliothèque…). */
    BANNIERE(400, 225, 14 * 1024 * 1024),

    /** Carré plein cadre : tuiles carrées ou rondes. Logo entier, centré, dans le cercle central. */
    CARRE(256, 256, 7 * 1024 * 1024);

    val rapport: Float get() = largeur / hauteur.toFloat()

    companion object {
        /** Format le plus proche d'un cadre de cette largeur et de cette hauteur (n'importe quelle unité). */
        fun pour(largeur: Float, hauteur: Float): FormatVisuel {
            if (largeur <= 0f || hauteur <= 0f) return BANNIERE
            val r = largeur / hauteur
            return entries.minBy { abs(ln(it.rapport / r)) }
        }

        fun pour(largeur: Int, hauteur: Int): FormatVisuel = pour(largeur.toFloat(), hauteur.toFloat())
    }
}

/**
 * Visuel d'une app : une image prête à afficher (au format demandé), la couleur dominante
 * (pour la lueur du fond) et la couleur des bords de l'image (pour combler un cadre qui
 * n'aurait pas exactement sa forme).
 */
class AppVisual(val art: ImageBitmap?, val color: Color, val fond: Color = Color(0xFF101218))

/**
 * Fournit les visuels des apps, une seule fois par app et par format, puis les garde en cache.
 * Pour chaque format, dans l'ordre :
 *  1. l'illustration livrée avec Projo pour les apps connues (voir [Illustrations]) ;
 *  2. BANNIÈRE : la bannière TV officielle de l'app (android:banner), sinon une carte fabriquée
 *     (fond de l'icône prolongé, logo en grand) ;
 *     CARRÉ : l'icône adaptative plein cadre, sinon l'icône posée sur son fond prolongé ou sur un
 *     dégradé de sa couleur.
 *
 * Images petites (400 × 225 et 256 × 256 px, 32 bits par pixel pour des dégradés lisses) et
 * caches séparés et limités (14 + 7 Mo) : le projecteur n'a qu'1 Go de RAM.
 */
class IconLoader(private val context: Context) {

    private val caches: Map<FormatVisuel, LruCache<String, AppVisual>> = FormatVisuel.entries.associateWith { f ->
        object : LruCache<String, AppVisual>(f.cache) {
            override fun sizeOf(key: String, value: AppVisual): Int =
                value.art?.let { it.width * it.height * 4 } ?: 64
        }
    }

    fun cached(packageName: String, format: FormatVisuel = FormatVisuel.BANNIERE): AppVisual? =
        caches.getValue(format).get(packageName)

    /**
     * Visuel d'une app au format demandé. [packageName] : paquet dont on montre le logo
     * (préfixé par [LOGO_ORIGINAL] pour l'icône d'origine, sans illustration).
     * À appeler hors du thread principal.
     */
    fun load(packageName: String, format: FormatVisuel = FormatVisuel.BANNIERE): AppVisual {
        val cache = caches.getValue(format)
        cache.get(packageName)?.let { return it }
        // Tuiles propres à Projo : dessinées par l'interface, couleur fixe
        if (packageName.startsWith("projo:")) return AppVisual(null, COULEUR_HDMI)
        val visual = try {
            fabriquer(packageName, format)
        } catch (e: Exception) {
            AppVisual(null, COULEUR_PAR_DEFAUT)
        }
        cache.put(packageName, visual)
        return visual
    }

    private fun fabriquer(cle: String, format: FormatVisuel): AppVisual {
        val original = cle.startsWith(LOGO_ORIGINAL)
        val paquet = cle.removePrefix(LOGO_ORIGINAL)
        if (!original) illustration(paquet, format)?.let { return it }

        val w = format.largeur
        val h = format.hauteur
        val art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(art)
        val couleur = when (format) {
            // Bannière TV officielle de l'app si elle en a une, sinon carte fabriquée depuis l'icône
            FormatVisuel.BANNIERE -> banniereTv(paquet)?.let { dessinerBanniere(it, canvas, w, h) }
                ?: dessinerCarte(iconeHd(paquet), canvas, w, h)
            FormatVisuel.CARRE -> dessinerCarre(iconeHd(paquet), canvas, w)
        }
        return AppVisual(art.asImageBitmap(), couleur, Color(couleurDesBords(art)))
    }

    // ------------------------------------------------------------------ illustrations

    /** Fichiers présents dans assets/illustrations (lus une seule fois ; vide si aucun). */
    private val fichiers: Set<String> by lazy {
        try {
            context.assets.list(Illustrations.DOSSIER)?.toSet().orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Illustration trouvée pour chaque paquet déjà examiné (id, ou "" si aucune). */
    private val idsParPaquet = ConcurrentHashMap<String, String>()

    /** Règle d'illustration de l'app : par son paquet, sinon par son nom affiché. */
    private fun regle(paquet: String): Illustrations.Regle? {
        // Aucune image livrée : inutile de lire les noms des apps
        if (fichiers.isEmpty()) return null
        val id = idsParPaquet.getOrPut(paquet) {
            val r = Illustrations.parPaquet(paquet)
                ?: libelles(paquet).firstNotNullOfOrNull { Illustrations.parLibelle(it) }
            r?.id.orEmpty()
        }
        return if (id.isEmpty()) null else Illustrations.parId(id)
    }

    /** Noms de l'app : celui de l'application, puis ceux de ses écrans de lancement (TV et classique). */
    private fun libelles(paquet: String): List<String> {
        val pm = context.packageManager
        val noms = mutableListOf<String>()
        try {
            noms += pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString()
        } catch (e: Exception) {
            // App introuvable : pas de nom
        }
        for (lancer in listOf({ pm.getLeanbackLaunchIntentForPackage(paquet) }, { pm.getLaunchIntentForPackage(paquet) })) {
            try {
                val intent = lancer() ?: continue
                pm.resolveActivity(intent, 0)?.loadLabel(pm)?.toString()?.let { noms += it }
            } catch (e: Exception) {
                // Écran illisible : on passe au suivant
            }
        }
        return noms.distinct()
    }

    /**
     * Illustration de l'app au format demandé : l'image prévue pour ce format, sinon celle de
     * l'autre format, centrée en entier sur la couleur de ses bords. Null si l'app n'en a pas.
     */
    private fun illustration(paquet: String, format: FormatVisuel): AppVisual? {
        val regle = regle(paquet) ?: return null
        val banniere = Illustrations.fichierBanniere(regle.id)
        val icone = Illustrations.fichierIcone(regle.id)
        val ordre = if (format == FormatVisuel.CARRE) listOf(icone, banniere) else listOf(banniere, icone)
        val nom = ordre.firstOrNull { it in fichiers } ?: return null
        val art = decoder(nom, format.largeur, format.hauteur) ?: return null
        val couleur = regle.couleur?.let { Color(it) } ?: couleurImage(art)
        return AppVisual(art.asImageBitmap(), couleur, Color(couleurDesBords(art)))
    }

    /**
     * Décode une illustration et la met exactement à la taille w × h : elle remplit le cadre en
     * ne rognant pas plus de [ROGNAGE_MAX] (même règle que l'affichage) ; le reste éventuel est
     * comblé par la couleur de ses bords. Lecture réduite (inSampleSize) si l'image est grande.
     */
    private fun decoder(nom: String, w: Int, h: Int): Bitmap? {
        val chemin = Illustrations.DOSSIER + "/" + nom
        return try {
            val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(chemin).use { BitmapFactory.decodeStream(it, null, bornes) }
            val sw = bornes.outWidth
            val sh = bornes.outHeight
            if (sw <= 0 || sh <= 0) return null
            // Réduction à la lecture : la plus forte qui garde au moins la taille finale
            val e = echelleCadrage(sw.toFloat(), sh.toFloat(), w.toFloat(), h.toFloat())
            var reduction = 1
            while (reduction * 2 * e <= 1f) reduction *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = reduction
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val source = context.assets.open(chemin).use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            val art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(art)
            canvas.drawColor(couleurDesBords(source))
            val s = echelleCadrage(source.width.toFloat(), source.height.toFloat(), w.toFloat(), h.toFloat())
            val dw = source.width * s
            val dh = source.height * s
            val x = (w - dw) / 2f
            val y = (h - dh) / 2f
            canvas.drawBitmap(
                source, null, RectF(x, y, x + dw, y + dh),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
            )
            source.recycle()
            art
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Couleur de la lueur d'une illustration : sa couleur vive, sinon sa couleur dominante. */
    private fun couleurImage(image: Bitmap): Color {
        val p = Palette.from(image).maximumColorCount(12).generate()
        val s = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch
        return s?.let { Color(it.rgb) } ?: COULEUR_PAR_DEFAUT
    }

    // ------------------------------------------------------------------ images de l'app

    /**
     * Bannière 16:9 que l'app fournit pour les écrans d'accueil TV (android:banner) :
     * celle de l'activité TV d'abord, puis celle de l'app. Null si l'app n'en a pas.
     */
    private fun banniereTv(packageName: String): Drawable? = try {
        val pm = context.packageManager
        val app = pm.getApplicationInfo(packageName, 0)
        val activite = pm.getLeanbackLaunchIntentForPackage(packageName)?.let { pm.resolveActivity(it, 0)?.activityInfo }
        val id = activite?.banner?.takeIf { it != 0 } ?: app.banner
        if (id == 0) null else versionHd(pm.getResourcesForApplication(app), id)
    } catch (e: Exception) {
        null
    }

    /**
     * Icône de l'app dans sa plus haute résolution disponible : celle que donne Android
     * par défaut est prévue pour la densité de l'écran (souvent 96 px) et devient floue
     * une fois agrandie sur une carte.
     */
    private fun iconeHd(packageName: String): Drawable {
        val pm = context.packageManager
        return try {
            val app = pm.getApplicationInfo(packageName, 0)
            if (app.icon == 0) return pm.getApplicationIcon(app)
            versionHd(pm.getResourcesForApplication(app), app.icon) ?: pm.getApplicationIcon(app)
        } catch (e: Exception) {
            pm.getApplicationIcon(packageName)
        }
    }

    private fun versionHd(res: Resources, id: Int): Drawable? = try {
        res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (e: Exception) {
        null
    }

    /** Bannière TV recadrée pour remplir la carte ; renvoie sa couleur pour la lueur. */
    private fun dessinerBanniere(banniere: Drawable, canvas: Canvas, w: Int, h: Int): Color {
        val couleur = couleurVive(banniere) ?: couleurDominante(banniere, avecVive = false)
        // Fond sombre de sa couleur, au cas où la bannière a des zones transparentes
        canvas.drawColor(assombrir(argb(couleur), 0.6f))
        val bw = banniere.intrinsicWidth
        val bh = banniere.intrinsicHeight
        if (bw <= 0 || bh <= 0) {
            banniere.setBounds(0, 0, w, h)
        } else {
            // Remplit toute la carte en gardant les proportions (recadrage au centre)
            val echelle = max(w / bw.toFloat(), h / bh.toFloat())
            val dw = (bw * echelle).toInt()
            val dh = (bh * echelle).toInt()
            val x = (w - dw) / 2
            val y = (h - dh) / 2
            banniere.setBounds(x, y, x + dw, y + dh)
        }
        banniere.draw(canvas)
        return couleur
    }

    /** Carte 16:9 fabriquée depuis l'icône ; renvoie la couleur dominante. */
    private fun dessinerCarte(icone: Drawable, canvas: Canvas, w: Int, h: Int): Color {
        if (Build.VERSION.SDK_INT >= 26 && icone is AdaptiveIconDrawable && icone.background != null) {
            // 1. Fond de l'icône étiré pour couvrir toute la carte (recadré au centre)
            val fond = icone.background
            val cote = max(w, h)
            fond.setBounds((w - cote) / 2, (h - cote) / 2, (w + cote) / 2, (h + cote) / 2)
            fond.draw(canvas)
            val couleurFond = couleurDominante(fond, avecVive = false)
            // 2. Logo (calque avant) en grand au centre. Le calque fait 108 unités dont
            //    seules les 66 du centre sont garanties visibles : on le dimensionne en conséquence.
            icone.foreground?.let { poser(it, canvas, w, h, (h * 1.05f).toInt()) }
            return couleurVive(icone) ?: couleurFond
        }

        // Ancienne icône (la plupart des apps Fire TV) : on regarde d'abord le fond de l'icône
        val couleur = couleurVive(icone) ?: couleurDominante(icone, avecVive = false)
        val fondUni = couleurDuBord(icone)
        if (fondUni != null) {
            // Icône « pleine » (Netflix, Prime Video, YouTube…) : sa couleur de fond remplit
            // toute la carte et le logo s'affiche en grand → rendu « bannière », sans raccord visible
            canvas.drawColor(fondUni)
            poser(icone, canvas, w, h, (h * 0.94f).toInt())
            return couleur
        }

        // Icône détourée : dégradé doux de sa couleur + grande icône
        degrade(canvas, couleur, w, h)
        poser(icone, canvas, w, h, (h * 0.68f).toInt())
        return couleur
    }

    /**
     * Carré plein cadre fabriqué depuis l'icône ; renvoie la couleur dominante.
     * Le logo reste dans le cercle central : le carré peut être découpé en rond.
     */
    private fun dessinerCarre(icone: Drawable, canvas: Canvas, cote: Int): Color {
        if (Build.VERSION.SDK_INT >= 26 && icone is AdaptiveIconDrawable && icone.background != null) {
            // Comme sur un écran d'accueil : le calque fait 108 unités, dont ~72 visibles
            // (logo garanti dans le cercle central de 66)
            val e = (cote * 108f / 72f).toInt()
            val o = (cote - e) / 2
            icone.background.setBounds(o, o, o + e, o + e)
            icone.background.draw(canvas)
            icone.foreground?.let { it.setBounds(o, o, o + e, o + e); it.draw(canvas) }
            return couleurVive(icone) ?: couleurDominante(icone.background, avecVive = false)
        }

        val couleur = couleurVive(icone) ?: couleurDominante(icone, avecVive = false)
        val fondUni = couleurDuBord(icone)
        if (fondUni != null) {
            // Icône « pleine » : son fond prolongé jusqu'aux bords, l'icône entière au centre
            canvas.drawColor(fondUni)
            poser(icone, canvas, cote, cote, (cote * 0.80f).toInt())
            return couleur
        }
        // Icône détourée : dégradé de sa couleur, icône entière au centre
        degrade(canvas, couleur, cote, cote)
        poser(icone, canvas, cote, cote, (cote * 0.64f).toInt())
        return couleur
    }

    /** Dessine l'icône (carré de [taille]) au centre d'un cadre w × h. */
    private fun poser(icone: Drawable, canvas: Canvas, w: Int, h: Int, taille: Int) {
        val x = (w - taille) / 2
        val y = (h - taille) / 2
        icone.setBounds(x, y, x + taille, y + taille)
        icone.draw(canvas)
    }

    /** Dégradé doux (tramé, donc sans bandes) de la couleur, du clair en haut à gauche au sombre. */
    private fun degrade(canvas: Canvas, couleur: Color, w: Int, h: Int) {
        val c = argb(couleur)
        val paint = Paint().apply {
            isDither = true
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                assombrir(c, 0.15f), assombrir(c, 0.55f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /**
     * Couleur du fond d'une icône si son bord est opaque et d'une seule couleur (icône carrée
     * ou à coins arrondis), sinon null. On échantillonne le milieu des 4 côtés, loin des coins.
     */
    private fun couleurDuBord(icone: Drawable): Int? {
        val n = 96
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val anciennes = icone.copyBounds()
        icone.setBounds(0, 0, n, n)
        icone.draw(Canvas(b))
        icone.bounds = anciennes
        val bord = 3
        val points = mutableListOf<Int>()
        for (i in 0 until 7) {
            val t = 30 + i * 6 // de 30 % à 66 % de chaque côté
            points += b.getPixel(t, bord)
            points += b.getPixel(t, n - 1 - bord)
            points += b.getPixel(bord, t)
            points += b.getPixel(n - 1 - bord, t)
        }
        b.recycle()
        if (points.any { android.graphics.Color.alpha(it) < 245 }) return null
        val r = points.map { android.graphics.Color.red(it) }.average().toInt()
        val g = points.map { android.graphics.Color.green(it) }.average().toInt()
        val bl = points.map { android.graphics.Color.blue(it) }.average().toInt()
        val uni = points.all {
            abs(android.graphics.Color.red(it) - r) + abs(android.graphics.Color.green(it) - g) +
                abs(android.graphics.Color.blue(it) - bl) < 36
        }
        return if (uni) android.graphics.Color.rgb(r, g, bl) else null
    }

    /**
     * Couleur moyenne (opaque) du tour d'une image : sert à combler un cadre plus grand que
     * l'image. Les pixels transparents sont ignorés ; fond sombre si l'image n'a pas de bord opaque.
     */
    private fun couleurDesBords(image: Bitmap): Int {
        val w = image.width
        val h = image.height
        if (w < 4 || h < 4) return FOND_SOMBRE
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        fun ajouter(x: Int, y: Int) {
            val p = image.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 200) return
            r += android.graphics.Color.red(p)
            g += android.graphics.Color.green(p)
            b += android.graphics.Color.blue(p)
            n++
        }
        for (i in 0 until 12) {
            val x = 1 + (w - 3) * i / 11
            val y = 1 + (h - 3) * i / 11
            ajouter(x, 1)
            ajouter(x, h - 2)
            ajouter(1, y)
            ajouter(w - 2, y)
        }
        return if (n == 0) FOND_SOMBRE else android.graphics.Color.rgb(r / n, g / n, b / n)
    }

    /** Couleur vive de l'icône (pour une lueur colorée même si le fond est blanc ou noir). */
    private fun couleurVive(icone: Drawable): Color? {
        val petit = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        icone.setBounds(0, 0, 32, 32)
        icone.draw(Canvas(petit))
        val p = Palette.from(petit).maximumColorCount(12).generate()
        petit.recycle()
        val s = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: return null
        return Color(s.rgb)
    }

    private fun couleurDominante(d: Drawable, avecVive: Boolean): Color {
        val petit = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val anciennes = d.copyBounds()
        d.setBounds(0, 0, 24, 24)
        d.draw(Canvas(petit))
        d.bounds = anciennes
        val p = Palette.from(petit).maximumColorCount(8).generate()
        petit.recycle()
        val s = (if (avecVive) p.vibrantSwatch else null) ?: p.dominantSwatch ?: p.mutedSwatch
        return s?.let { Color(it.rgb) } ?: COULEUR_PAR_DEFAUT
    }

    private fun argb(c: Color): Int = android.graphics.Color.rgb(
        (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
    )

    private fun assombrir(argb: Int, part: Float): Int {
        val f = 1f - part
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(argb) * f).toInt(),
            (android.graphics.Color.green(argb) * f).toInt(),
            (android.graphics.Color.blue(argb) * f).toInt(),
        )
    }

    companion object {
        /**
         * Part maximale d'une image qu'on accepte de rogner pour remplir un cadre d'une autre
         * forme (8 % de sa largeur ou de sa hauteur, soit 4 % de chaque côté) ; au-delà, l'image
         * est réduite et le reste du cadre prend la couleur de ses bords.
         */
        const val ROGNAGE_MAX = 0.08f

        /** Préfixe du paquet quand l'utilisateur a choisi « Logo d'origine » : pas d'illustration. */
        const val LOGO_ORIGINAL = "original:"

        val COULEUR_PAR_DEFAUT = Color(0xFF4A5A78)
        val COULEUR_HDMI = Color(0xFF2A9D8F)
        private const val FOND_SOMBRE = 0xFF101218.toInt()

        /**
         * Échelle à appliquer à une image (sw × sh) pour un cadre (dw × dh) : elle remplit le
         * cadre, mais sans jamais rogner plus de [ROGNAGE_MAX] de sa largeur ou de sa hauteur.
         */
        fun echelleCadrage(sw: Float, sh: Float, dw: Float, dh: Float): Float {
            if (sw <= 0f || sh <= 0f) return 1f
            val remplir = max(dw / sw, dh / sh)
            val entier = min(dw / sw, dh / sh)
            return min(remplir, entier / (1f - ROGNAGE_MAX))
        }
    }
}
