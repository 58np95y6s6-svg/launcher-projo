package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.infos.InfosReseau
import fr.projo.launcher.modules.infos.ModuleInfos
import fr.projo.launcher.modules.infos.TitreInfo
import fr.projo.launcher.modules.infos.TitresSource
import fr.projo.launcher.modules.outils.TexteFlux
import fr.projo.launcher.modules.photo.PhotoDuJour
import fr.projo.launcher.modules.photo.PhotoReseau
import fr.projo.launcher.modules.tv.ModuleTv
import fr.projo.launcher.modules.tv.ProgrammeTv
import fr.projo.launcher.modules.tv.SoireeTv
import fr.projo.launcher.modules.tv.SoireeTvDonnees
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Données factices des modules Ce soir à la TV, Infos et Photo du jour (aucun réseau). */
object FacticesSoireeInfosPhoto {
    private const val M = 60_000L

    private fun ceSoir(h: Int, min: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun soiree(): SoireeTvDonnees {
        fun p(c: String, h: Int, m: Int, duree: Int, titre: String, genre: String, sous: String? = null) =
            ProgrammeTv(c, titre, sous, ceSoir(h, m), ceSoir(h, m) + duree * M, genre)
        return SoireeTvDonnees(
            SoireeTv.jour(),
            listOf(
                p("tf1", 21, 10, 175, "Le Seigneur des anneaux : la communauté de l'anneau", "Film", "Fantastique"),
                p("france2", 21, 10, 120, "Envoyé spécial", "Magazine", "Les nouveaux visages de la précarité"),
                p("france3", 21, 10, 95, "Meurtres à Collioure", "Téléfilm", "Policier"),
                p("canalplus", 21, 0, 120, "Ligue des champions : PSG / Bayern Munich", "Football"),
                p("france5", 20, 55, 90, "La grande librairie", "Magazine littéraire"),
                p("m6", 21, 10, 130, "Zone interdite", "Magazine", "Vacances : les nouvelles arnaques"),
                p("arte", 20, 55, 110, "Les Enfants du paradis", "Film", "Drame"),
                p("tmc", 21, 25, 50, "Les Experts : Miami", "Série"),
            ),
        )
    }

    fun infos(source: String): TitresSource {
        val t = System.currentTimeMillis()
        val titres = when (source) {
            "franceinfo" -> listOf(
                "Budget 2027 : le gouvernement présente un projet de loi de finances sous haute tension à l'Assemblée",
                "Tempête dans l'Ouest : 40 000 foyers toujours privés d'électricité",
                "Prix des carburants : le gazole repasse sous 1,60 € le litre",
                "JO d'hiver 2030 : les sites des Alpes françaises dévoilés",
            )
            else -> listOf(
                "Climat : les engagements de la COP jugés « insuffisants » par les scientifiques",
                "L'intelligence artificielle bouscule les métiers de la traduction",
                "Exposition : le Louvre consacre une rétrospective inédite à Georges de La Tour",
            )
        }
        return TitresSource(source, titres.mapIndexed { i, s -> TitreInfo(s, source, t - (i * 37 + if (source == "franceinfo") 5 else 20) * M) })
    }

    /** Fausse photo astronomique (nébuleuse et étoiles), enregistrée comme le ferait PhotoReseau. */
    fun photo(): PhotoDuJour {
        val f = File(RuntimeEnvironment.getApplication().cacheDir, "photo_factice.jpg")
        if (!f.isFile) {
            val b = Bitmap.createBitmap(1024, 682, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1024f, 682f, 0xFF050818.toInt(), 0xFF1B0B2E.toInt(), Shader.TileMode.CLAMP) })
            c.drawCircle(560f, 330f, 300f, Paint().apply {
                shader = RadialGradient(560f, 330f, 300f, intArrayOf(0xFFE0507A.toInt(), 0x99A23C9E.toInt(), 0x00000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            })
            c.drawCircle(420f, 260f, 180f, Paint().apply {
                shader = RadialGradient(420f, 260f, 180f, intArrayOf(0xAA4FA8FF.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
            })
            val etoile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            var graine = 7L
            repeat(260) {
                graine = (graine * 6364136223846793005L + 1442695040888963407L)
                val x = ((graine ushr 33) % 1024).toFloat()
                val y = ((graine ushr 13) % 682).toFloat()
                c.drawCircle(x, y, if (it % 23 == 0) 2.6f else 1.1f, etoile)
            }
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return PhotoDuJour("La nébuleuse de la Rosette, pétales de gaz et d'étoiles naissantes", PhotoReseau.jour(), f.path)
    }

    /** Données de tous les modules ; [erreur] : toutes les sources en panne. */
    fun donnees(erreur: Boolean = false) = DonneesModules(factices = { cle ->
        if (erreur && (cle.startsWith("tv:") || cle.startsWith("infos:") || cle.startsWith("photo:"))) {
            EtatDonnees(null, erreur = true, date = System.currentTimeMillis())
        } else when {
            cle.startsWith("tv:") -> soiree()
            cle.startsWith("infos:") -> infos(cle.removePrefix("infos:"))
            cle.startsWith("photo:") -> photo()
            cle.startsWith("meteo") -> ModulesFactices.meteo()
            else -> null
        }
    })

    fun hote(vararg ids: String, erreur: Boolean = false, reglages: Map<String, String> = emptyMap()) =
        HoteModules(ids.toList(), reglages, donnees(erreur))
}

/**
 * Modules Ce soir à la TV, Infos et Photo du jour dans deux designs (Verre, sombre, et Pastel,
 * clair) : seuls (taille MOYEN) et à deux (taille PETIT), états d'erreur, et navigation.
 * Chaque capture est accompagnée d'un agrandissement ×2 de la zone de modules (« …_zone »).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ModulesSoireeInfosPhotoTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = listOf(Design.VERRE, Design.PASTEL).map { arrayOf<Any>(it.name) }
    }

    @Test
    fun tvMoyen() = scene("ma1_tv_moyen", "tv")

    @Test
    fun infosMoyen() = scene("ma2_infos_moyen", "infos")

    @Test
    fun photoMoyen() = scene("ma3_photo_moyen", "photo")

    @Test
    fun tvEtInfosPetit() = scene("ma4_tv_infos_petit", "tv", "infos", focus = "infos")

    @Test
    fun photoEtTvPetit() = scene("ma5_photo_tv_petit", "photo", "tv", focus = "photo")

    @Test
    fun erreurs() {
        hoteModules = FacticesSoireeInfosPhoto.hote("tv", "photo", erreur = true)
        afficher()
        focusDans("module:photo")
        capturerZone("ma6_erreurs_petit")
    }

    @Test
    fun erreurInfosMoyen() {
        hoteModules = FacticesSoireeInfosPhoto.hote("infos", erreur = true)
        afficher()
        focusDans("module:infos")
        capturerZone("ma7_infos_erreur_moyen")
    }

    /** Chaque module seul : ◀ puis Retour reviennent au 1er favori. */
    @Test
    fun navigationTv() = sortiesSeul("tv")

    @Test
    fun navigationInfos() = sortiesSeul("infos")

    @Test
    fun navigationPhoto() = sortiesSeul("photo")

    /** À deux : ◀ passe du 2e module au 1er, puis ◀ revient au 1er favori. */
    @Test
    fun navigationDeuxModules() {
        hoteModules = FacticesSoireeInfosPhoto.hote("tv", "infos")
        afficher()
        focusDans("module:infos")
        appuyer(Key.DirectionLeft)
        attendreFocus("module:tv", "◀ depuis Infos (2e module)")
        appuyer(Key.DirectionLeft)
        verifierFavori("◀ depuis Ce soir à la TV (1er module)")
        focusDans("module:infos")
        appuyer(Key.Back)
        verifierFavori("Retour depuis Infos (2e module)")
    }

    /** ▼ fait défiler les chaînes du module TV sans en sortir. */
    @Test
    fun defilementTv() {
        hoteModules = FacticesSoireeInfosPhoto.hote("tv")
        afficher()
        focusDans("module:tv")
        repeat(5) { appuyer(Key.DirectionDown) }
        attendreFocus("module:tv", "▼ ×5 dans la liste des chaînes")
        capturerZone("ma8_tv_defilement")
    }

    // ------------------------------------------------------------------ outils

    private fun scene(nom: String, vararg ids: String, focus: String = ids.first()) {
        hoteModules = FacticesSoireeInfosPhoto.hote(*ids)
        afficher()
        focusDans("module:$focus")
        if ("photo" in ids) attendrePhoto()
        capturerZone(nom)
    }

    /** L'image est décodée hors du thread principal : on attend qu'elle soit affichée. */
    private fun attendrePhoto() {
        val titre = FacticesSoireeInfosPhoto.photo().titre
        repeat(60) {
            if (compose.onAllNodes(hasContentDescription(titre), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
            avancer(50)
        }
        fail("[${design.name}] photo du jour jamais affichée")
    }

    private fun sortiesSeul(id: String) {
        hoteModules = FacticesSoireeInfosPhoto.hote(id)
        afficher()
        focusDans("module:$id")
        appuyer(Key.DirectionLeft)
        verifierFavori("◀ depuis $id")
        focusDans("module:$id")
        appuyer(Key.Back)
        verifierFavori("Retour depuis $id")
    }

    private fun verifierFavori(etape: String) {
        val f = focus()
        if (f?.etiquette != favori(0)) fail("[${design.name}] $etape : attendu ${favori(0)}, obtenu ${decrire(f)}")
    }

    private fun focusDans(etiquette: String) {
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag(etiquette)) and SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        avancer(1000)
        attendreFocus(etiquette, "préparation")
    }

    private fun attendreFocus(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || etiquette !in f.chaine) {
            fail("[${design.name}] $etape : focus attendu dans « $etiquette », obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
        }
    }

    /** Capture de l'écran, puis agrandissement ×2 de la zone de modules (doit tenir dans l'écran). */
    private fun capturerZone(nom: String) {
        val source = capturer(nom)
        val zone = compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] zone de modules introuvable")
        val b = zone.boundsInRoot
        val racine = compose.onRoot().fetchSemanticsNode().size
        if (b.left < 0f || b.top < 0f || b.right > racine.width + 0.5f || b.bottom > racine.height + 0.5f) {
            fail("[${design.name}] zone de modules hors de l'écran : ${b.enU()}")
        }
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("capture illisible : $source")
        val m = (12 * pxParU).toInt()
        val x0 = (b.left.toInt() - m).coerceIn(0, image.width - 1)
        val y0 = (b.top.toInt() - m).coerceIn(0, image.height - 1)
        val x1 = (b.right.toInt() + m).coerceIn(x0 + 1, image.width)
        val y1 = (b.bottom.toInt() + m).coerceIn(y0 + 1, image.height)
        val morceau = Bitmap.createBitmap(image, x0, y0, x1 - x0, y1 - y0)
        val grand = Bitmap.createScaledBitmap(morceau, morceau.width * 2, morceau.height * 2, true)
        File(source.parentFile, "${design.name.lowercase()}_${nom}_zone.png").outputStream().use {
            grand.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("[${design.name}] $nom : zone ${b.enU()}")
    }
}

/** Captures des réglages des trois modules. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesReglagesSoireeInfosTest : BancAccueil("VERRE") {

    @OptIn(ExperimentalRoborazziApi::class)
    private fun dialogue(nom: String, contenu: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) { contenu() }
            }
        }
        avancer(1500)
        val fichier = File("build/captures/dialogue_$nom.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }

    @Test
    fun reglagesTv() {
        val hote = FacticesSoireeInfosPhoto.hote("tv", reglages = mapOf("tv.chaines" to "tf1,france2,arte,m6"))
        dialogue("a1_tv") { ModuleTv.Reglages(hote.reglagesDe("tv"), hote.donnees) {} }
    }

    @Test
    fun reglagesInfos() {
        val hote = FacticesSoireeInfosPhoto.hote("infos")
        dialogue("a2_infos") { ModuleInfos.Reglages(hote.reglagesDe("infos"), hote.donnees) {} }
    }
}

/** Lecture des sources (XMLTV, RSS, page APOD) sur des échantillons : aucun réseau. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LectureSourcesTest {

    private fun heure(jour: String, hhmm: String): String {
        val f = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
        return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).format(f.parse(jour + hhmm)!!)
    }

    @Test
    fun xmltvSoiree() {
        val j = "20261019"
        val lendemain = "20261020"
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tv SYSTEM "xmltv.dtd">
<tv generator-info-name="XML TV Fr">
  <channel id="TF1.fr"><display-name>TF1</display-name><icon src="https://exemple.fr/tf1.png"/></channel>
  <channel id="France2.fr"><display-name>France 2</display-name></channel>
  <channel id="France3Alpes.fr"><display-name>France 3 Alpes</display-name></channel>
  <channel id="C4.fr"><display-name>Canal+</display-name></channel>
  <programme start="${heure(j, "2000")}" stop="${heure(j, "2040")}" channel="TF1.fr"><title lang="fr">Le 20H</title></programme>
  <programme start="${heure(j, "2045")}" stop="${heure(j, "2055")}" channel="TF1.fr"><title lang="fr">C'est Canteloup</title></programme>
  <programme start="${heure(j, "2110")}" stop="${heure(j, "2300")}" channel="TF1.fr">
    <title lang="fr">Tom &amp; Jerry &#8211; le film</title><sub-title lang="fr">Inédit</sub-title>
    <desc lang="fr">Une longue description <b>ignorée</b>.</desc>
    <category lang="en">Movie</category><category lang="fr">film d'animation</category>
    <credits><director>X</director></credits>
  </programme>
  <programme start="${heure(j, "2030")}" stop="${heure(j, "2250")}" channel="France2.fr"><title lang="fr"><![CDATA[Envoyé spécial]]></title><category lang="fr">Magazine</category></programme>
  <programme start="${heure(j, "2110")}" stop="${heure(j, "2300")}" channel="France3Alpes.fr"><title>Régional</title></programme>
  <programme start="${heure(j, "2105")}" stop="${heure(j, "2300")}" channel="C4.fr"><title>Ligue des champions</title></programme>
  <programme start="${heure(lendemain, "2110")}" stop="${heure(lendemain, "2300")}" channel="TF1.fr"><title>Demain</title></programme>
</tv>"""
        val s = SoireeTv.lire(xml.byteInputStream(), j)
        val parChaine = s.programmes.associateBy { it.chaine }
        assertEquals(setOf("tf1", "france2", "canalplus"), parChaine.keys)
        val tf1 = parChaine.getValue("tf1")
        assertEquals("Tom & Jerry – le film", tf1.titre)
        assertEquals("Inédit", tf1.sousTitre)
        assertEquals("Film d'animation", tf1.genre)
        assertEquals("Envoyé spécial", parChaine.getValue("france2").titre)
        assertEquals("Ligue des champions", parChaine.getValue("canalplus").titre)
        assertNull("France 3 Alpes n'est pas France 3", parChaine["france3"])
        // Ordre des chaînes : celui de la TNT
        assertEquals(listOf("tf1", "france2", "canalplus"), s.programmes.map { it.chaine })
    }

    @Test
    fun rssNettoye() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>franceinfo - Titres</title>
<item><title><![CDATA[Retraites : l&#039;Assemblée adopte le texte]]></title><pubDate>Mon, 19 Oct 2026 18:05:00 +0200</pubDate></item>
<item><title>Météo &amp;eacute;t&amp;eacute; : &lt;b&gt;chaleur&lt;/b&gt;   record</title><dc:date>2026-10-19T15:30:00Z</dc:date></item>
<item><title>Sans date</title></item>
</channel></rss>"""
        val t = InfosReseau.lire(xml.byteInputStream(), "franceinfo")
        assertEquals(listOf("Retraites\u00A0: l'Assemblée adopte le texte", "Météo été\u00A0: chaleur record", "Sans date"), t.map { it.titre })
        assertEquals(SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).parse("2026-10-19 16:05 +0000")!!.time, t[0].date)
        assertEquals(SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).parse("2026-10-19 15:30 +0000")!!.time, t[1].date)
        assertNull(t[2].date)
    }

    @Test
    fun atom() {
        val xml = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Flux</title>
<entry><title type="html">Premier &amp;amp; titre</title><updated>2026-10-19T08:00:00.000+02:00</updated></entry></feed>"""
        val t = InfosReseau.lire(xml.byteInputStream(), "x")
        assertEquals("Premier & titre", t.single().titre)
        assertEquals(SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).parse("2026-10-19 06:00 +0000")!!.time, t.single().date)
    }

    @Test
    fun pageApod() {
        val html = """<html><head><title> APOD: 2026 October 19 - The Rosette Nebula
</title></head><body><center><h1> Astronomy Picture of the Day </h1>
<a href="image/2610/Rosette_Hubble_4000.jpg">
<IMG SRC="image/2610/Rosette_Hubble_1024.jpg" alt="Nebula" style="max-width:100%"></a></center>
<center><b> The Rosette Nebula </b> <br><b> Image Credit: </b> X</center></body></html>"""
        val a = PhotoReseau.lirePage(html, "https://apod.nasa.gov/apod/astropix.html")
        assertEquals("The Rosette Nebula", a.titre)
        assertEquals("https://apod.nasa.gov/apod/image/2610/Rosette_Hubble_1024.jpg", a.image)
        assertTrue(!a.video)

        val video = """<html><head><title>APOD: 2026 October 18 – A Total Eclipse</title></head><body><center>
<iframe width="960" height="540" src="https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0" frameborder="0"></iframe></center></body></html>"""
        val v = PhotoReseau.lirePage(video, "https://apod.nasa.gov/apod/astropix.html")
        assertEquals("A Total Eclipse", v.titre)
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", v.image)
        assertTrue(v.video)

        val api = PhotoReseau.lireApi(JSONObject("""{"title":"Vimeo","media_type":"video","url":"https://player.vimeo.com/video/1"}"""))
        assertNull("vidéo sans miniature : la veille sera prise", api.image)
        val apiImage = PhotoReseau.lireApi(JSONObject("""{"title":"M31","media_type":"image","url":"http://apod.nasa.gov/a.jpg"}"""))
        assertEquals("https://apod.nasa.gov/a.jpg", apiImage.image)
    }

    @Test
    fun texte() {
        assertEquals("L'été «\u00A0chaud\u00A0» – 3\u00A0€", TexteFlux.nettoyer("L&#x27;&eacute;t&eacute; &laquo;&nbsp;chaud&nbsp;&raquo; &ndash; 3&nbsp;&euro;"))
        assertNotNull(SoireeTv.normaliser("Canal+").takeIf { it == "canalplus" })
        assertEquals("france2", SoireeTv.normaliser("France2.fr"))
    }
}
