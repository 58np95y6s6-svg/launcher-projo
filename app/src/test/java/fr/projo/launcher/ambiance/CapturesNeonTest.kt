package fr.projo.launcher.ambiance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import fr.projo.launcher.ui.ECRAN_TV
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Captures du néon (1920 × 1080) : les 4 bandes (Views) dessinées dans des images, posées
 * par-dessus une fausse app au centre, avec des spectres synthétiques (basses fortes, aigus
 * forts, silence, pochette avec et sans sursaut, Douce dans le noir, animation sans son).
 * Résultats dans app/build/captures/ambiance_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesNeonTest {

    private val w = 1920
    private val h = 1080
    private val contexte: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun avant() = ModeAmbiance.oublier()

    @After
    fun apres() = ModeAmbiance.oublier()

    // ------------------------------------------------------------------ scènes

    @Test
    fun arcEnCielBassesFortes() = capturer("arc_basses", ecranLecture()) { m ->
        m.teinte = Teinte.ARC_EN_CIEL
        spectre(m, basses = 1f, mediums = 0.55f, aigus = 0.2f)
        m.sursauter(1f)
        repeat(2) { m.avancer(1 / 60f) }
    }

    @Test
    fun arcEnCielAigusForts() = capturer("arc_aigus", ecranLecture()) { m ->
        m.teinte = Teinte.ARC_EN_CIEL
        spectre(m, basses = 0.15f, mediums = 0.5f, aigus = 1f)
    }

    @Test
    fun silence() = capturer("silence", ecranLecture()) { m ->
        m.teinte = Teinte.POCHETTE
        m.changerCouleur(couleurPochette(), immediat = true)
        spectre(m, basses = 0f, mediums = 0f, aigus = 0f, images = 90)
    }

    @Test
    fun pochetteSursaut() = capturer("pochette_sursaut", ecranLecture()) { m ->
        m.teinte = Teinte.POCHETTE
        m.changerCouleur(couleurPochette(), immediat = true)
        spectre(m, basses = 0.95f, mediums = 0.6f, aigus = 0.4f)
        m.sursauter(1f)
        m.avancer(1 / 60f)
    }

    @Test
    fun pochetteCalme() = capturer("pochette_calme", ecranLecture()) { m ->
        m.teinte = Teinte.POCHETTE
        m.changerCouleur(couleurPochette(), immediat = true)
        spectre(m, basses = 0.35f, mediums = 0.3f, aigus = 0.25f, images = 60)
    }

    /** Intensité Douce, pièce dans le noir (app au fond noir) : coup de basse. Ne doit pas éblouir. */
    @Test
    fun douceDansLeNoir() = capturer("douce_nuit", ecranNoir()) { m ->
        m.intensite = Intensite.DOUCE
        m.teinte = Teinte.POCHETTE
        m.changerCouleur(couleurPochette(), immediat = true)
        spectre(m, basses = 1f, mediums = 0.6f, aigus = 0.4f)
        m.sursauter(1f)
        m.avancer(1 / 60f)
    }

    /** Sans son ni pochette : animation lente à la couleur de l'app (rouge de YouTube). */
    @Test
    fun animationCouleurDeLApp() = capturer("animation_icone", ecranLecture()) { m ->
        m.teinte = Teinte.ICONE
        m.changerCouleur(LecteurPochette.couleurNeon(intArrayOf(0xFF0000), intArrayOf(1))!!, immediat = true)
        m.reagitAuSon = false
        repeat(150) { m.avancer(1 / 60f) }
    }

    // ------------------------------------------------------------------ outils

    /** Spectre synthétique : niveaux des basses, médiums et aigus, maintenus [images] images. */
    private fun spectre(m: MoteurNeon, basses: Float, mediums: Float, aigus: Float, images: Int = 40) {
        m.reagitAuSon = true
        for (b in 0 until AnalyseSpectre.NB_BANDES) {
            m.cibles[b] = when {
                b < 3 -> basses
                b < 8 -> mediums + 0.08f * ((b * 7) % 3 - 1)
                else -> aigus
            }.coerceIn(0f, 1f)
        }
        repeat(images) { m.avancer(1 / 60f) }
    }

    /** Couleur de la pochette de la fausse app, tirée de l'image comme sur l'appareil. */
    private fun couleurPochette(): Int = LecteurPochette.couleurDeLImage(pochette(256))!!

    private fun capturer(nom: String, fond: Bitmap, scene: (MoteurNeon) -> Unit) {
        val m = MoteurNeon()
        scene(m)
        val dessin = DessinNeon(w, h)
        dessin.preparer(m)
        val image = fond.copy(Bitmap.Config.ARGB_8888, true)
        val toile = Canvas(image)
        val p = dessin.profondeur
        val zones = arrayOf(Rect(0, 0, w, p), Rect(0, h - p, w, h), Rect(0, p, p, h - p), Rect(w - p, p, w, h - p))
        for (z in zones) {
            // Chaque bande est une fenêtre transparente posée sur l'app : dessin à part, puis composition
            val v = BandeNeon(contexte, dessin, z)
            v.measure(
                View.MeasureSpec.makeMeasureSpec(z.width(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(z.height(), View.MeasureSpec.EXACTLY),
            )
            v.layout(0, 0, z.width(), z.height())
            val couche = Bitmap.createBitmap(z.width(), z.height(), Bitmap.Config.ARGB_8888)
            v.draw(Canvas(couche))
            toile.drawBitmap(couche, z.left.toFloat(), z.top.toFloat(), null)
        }
        // Le centre de l'écran n'est jamais touché par le néon
        assertTrue(image.getPixel(w / 2, h / 2) == fond.getPixel(w / 2, h / 2))
        val fichier = File("build/captures/ambiance_$nom.png")
        fichier.parentFile?.mkdirs()
        FileOutputStream(fichier).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Fausse pochette : dégradé bleu → rose → corail, soleil jaune, bas nuit. */
    private fun pochette(t: Int): Bitmap {
        val b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, t.toFloat(), t * 0.4f, intArrayOf(0xFF2A44CC.toInt(), 0xFFB0407A.toInt(), 0xFFE6504A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, t.toFloat(), t.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFC45A.toInt()
        c.drawCircle(t * 0.52f, t * 0.47f, t * 0.27f, p)
        p.color = 0xFF1E143C.toInt()
        c.drawRect(0f, t * 0.62f, t.toFloat(), t.toFloat(), p)
        return b
    }

    /** Fausse app de musique : écran « lecture en cours » sombre, pochette, titre, commandes. */
    private fun ecranLecture(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.65f, 0xFF3C1E50.toInt(), 0xFF121212.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        c.drawBitmap(pochette(500), 220f, 240f, null)
        p.color = 0xFFB4B4B4.toInt()
        p.textSize = 26f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("LECTURE EN COURS", 800f, 384f, p)
        p.color = -1
        p.textSize = 84f
        c.drawText("Nuit d'été", 800f, 486f, p)
        p.color = 0xFFBEBEBE.toInt()
        p.textSize = 38f
        p.typeface = Typeface.DEFAULT
        c.drawText("Les Lueurs · Album Néons", 800f, 556f, p)
        // Commandes et progression
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFFDCDCDC.toInt()
        c.drawCircle(740f, 880f, 28f, p)
        c.drawCircle(1180f, 880f, 28f, p)
        p.style = Paint.Style.FILL
        p.color = -1
        c.drawCircle(960f, 880f, 60f, p)
        p.color = 0xFF000000.toInt()
        c.drawRect(942f, 856f, 954f, 904f, p)
        c.drawRect(966f, 856f, 978f, 904f, p)
        p.color = 0xFF505050.toInt()
        c.drawRoundRect(RectF(400f, 990f, 1520f, 1000f), 5f, 5f, p)
        p.color = 0xFF1ED760.toInt()
        c.drawRoundRect(RectF(400f, 990f, 826f, 1000f), 5f, 5f, p)
        p.color = 0xFFAAAAAA.toInt()
        p.textSize = 26f
        c.drawText("1:24", 290f, 1004f, p)
        c.drawText("3:41", 1550f, 1004f, p)
        return b
    }

    /** Pièce dans le noir : app au fond noir, petite pochette au centre. */
    private fun ecranNoir(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF000000.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(960f, 470f, 420f, 0x22FFFFFF, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        c.drawBitmap(pochette(300), 810f, 320f, null)
        return b
    }
}
