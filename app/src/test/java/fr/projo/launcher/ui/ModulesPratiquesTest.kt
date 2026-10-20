package fr.projo.launcher.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.appareil.CLE_APPAREIL
import fr.projo.launcher.modules.appareil.InfosAppareil
import fr.projo.launcher.modules.appareil.LecteurAppareil
import fr.projo.launcher.modules.appareil.TestDebit
import fr.projo.launcher.modules.appareil.formaterOctets
import fr.projo.launcher.modules.bourse.BourseReseau
import fr.projo.launcher.modules.bourse.Cours
import fr.projo.launcher.modules.bourse.CoursCryptos
import fr.projo.launcher.modules.bourse.INSTRUMENTS
import fr.projo.launcher.modules.bourse.ModuleBourse
import fr.projo.launcher.modules.bourse.formaterPrix
import fr.projo.launcher.modules.bourse.formaterVariation
import fr.projo.launcher.modules.minuteur.AlarmeActivity
import fr.projo.launcher.modules.minuteur.EtatMinuteur
import fr.projo.launcher.modules.minuteur.Minuteur
import fr.projo.launcher.modules.minuteur.MinuteurReceiver
import fr.projo.launcher.modules.minuteur.ModeMinuteur
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/** Données factices des modules Minuteur, Crypto et bourse, État de l'appareil (aucun réseau). */
object FacticesPratiques {

    /** Courbe régulière de [n] points autour de [base], montante ou descendante. */
    fun courbe(base: Double, hausse: Boolean, n: Int = 40): List<Double> =
        List(n) { i ->
            val t = i.toDouble() / (n - 1)
            base * (1 + (if (hausse) 1 else -1) * 0.02 * t + 0.004 * sin(i * 0.9))
        }

    fun cryptos() = CoursCryptos(
        mapOf(
            "bitcoin" to Cours(98432.17, 2.14, courbe(96300.0, true), "EUR"),
            "ethereum" to Cours(3412.58, -1.32, courbe(3460.0, false), "EUR"),
            "solana" to Cours(142.35, 0.0, courbe(142.0, true), "EUR"),
        ),
    )

    fun yahoo(symbole: String): Cours? = when (symbole) {
        "^FCHI" -> Cours(7512.34, 0.42, courbe(7480.0, true), "EUR")
        "^GSPC" -> Cours(5871.2, -0.65, courbe(5910.0, false), "USD")
        "MC.PA" -> Cours(612.4, 1.05, courbe(606.0, true), "EUR")
        else -> null
    }

    fun appareil(temperature: Double? = 52.4) = InfosAppareil(
        reseau = "Freebox-5G", wifi = true, enLigne = true, signal = 3, rssi = -58,
        ip = "192.168.1.23",
        stockageLibre = 13_314_398_618L, stockageTotal = 34_359_738_368L,
        memoireLibre = 432_013_312L, memoireTotale = 1_073_741_824L,
        temperature = temperature, version = "2.4.0", android = "Android 11",
    )

    fun donnees(sansTemperature: Boolean = false) = DonneesModules(factices = { cle ->
        when {
            cle.startsWith("bourse:crypto:") -> cryptos()
            cle.startsWith("bourse:yahoo:") -> yahoo(cle.removePrefix("bourse:yahoo:"))
            cle == CLE_APPAREIL -> appareil(if (sansTemperature) null else 52.4)
            cle.startsWith("meteo") -> ModulesFactices.meteo()
            else -> null
        }
    })

    fun hote(vararg ids: String, reglages: Map<String, String> = emptyMap(), sansTemperature: Boolean = false) =
        HoteModules(ids.toList(), reglages, donnees(sansTemperature))
}

/**
 * Modules Minuteur et réveil, Crypto et bourse, État de l'appareil : captures en MOYEN (module
 * seul) et en PETIT (deux modules côte à côte) dans deux designs dont Pastel (clair), contrôle
 * que rien n'est coupé, et navigation à la télécommande (le minuteur ne piège pas le focus).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ModulesPratiquesTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = listOf(Design.PASTEL, Design.AURORE).map { arrayOf<Any>(it.name) }
    }

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun remiseAZero() {
        Minuteur.oublier()
        Minuteur.annuler(app)
        TestDebit.reinitialiser()
    }

    // ------------------------------------------------------------------ captures

    @Test
    fun minuteurMoyen() {
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:afficheur")
        controler("module:minuteur")
        capturer("p1_minuteur_moyen")
    }

    @Test
    fun minuteurEnCoursMoyen() {
        Minuteur.demarrer(app, 25)
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:bouton1")
        controler("module:minuteur")
        capturer("p2_minuteur_en_cours_moyen")
    }

    /** Réveil : « Réveil à une heure… » ouvre le réglage heure : minutes, puis ▲ règle l'heure. */
    @Test
    fun reveilMoyen() {
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:bascule")
        appuyer(Key.DirectionCenter)
        attendreFocus("minuteur:heures", "OK sur « Réveil à une heure… »")
        appuyer(Key.DirectionUp)
        assertEquals("▲ sur l'heure du réveil", 8 * 60, Minuteur.etat(app).heureReveil)
        controler("module:minuteur")
        capturer("p3_reveil_moyen")
        appuyer(Key.DirectionCenter)
        val e = Minuteur.etat(app)
        assertEquals(ModeMinuteur.EN_COURS, e.mode)
        assertTrue("réveil armé", e.reveil)
        avancer(500)
        capturer("p3b_reveil_arme_moyen")
    }

    @Test
    fun bourseMoyen() {
        hoteModules = FacticesPratiques.hote("bourse")
        afficher()
        focusDans("module:bourse")
        controler("module:bourse")
        capturer("p4_bourse_moyen")
    }

    @Test
    fun bourseTroisCoursMoyen() {
        hoteModules = FacticesPratiques.hote("bourse", reglages = mapOf("bourse.cours" to "c:bitcoin,y:MC.PA"))
        afficher()
        controler("module:bourse")
        capturer("p4b_bourse_deux_cours_moyen")
    }

    @Test
    fun appareilMoyen() {
        hoteModules = FacticesPratiques.hote("appareil")
        afficher()
        focusDans("module:appareil")
        controler("module:appareil")
        capturer("p5_appareil_moyen")
    }

    @Test
    fun minuteurEtBoursePetits() {
        Minuteur.demarrer(app, 25)
        hoteModules = FacticesPratiques.hote("minuteur", "bourse")
        afficher()
        focusSur("minuteur:afficheur")
        controler("module:minuteur")
        controler("module:bourse")
        capturer("p6_minuteur_bourse_petits")
    }

    @Test
    fun appareilEtMinuteurPetits() {
        hoteModules = FacticesPratiques.hote("appareil", "minuteur", sansTemperature = true)
        afficher()
        focusSur("minuteur:prereglage:15")
        controler("module:appareil")
        controler("module:minuteur")
        capturer("p7_appareil_minuteur_petits")
    }

    @Test
    fun minuteurSonnePetit() {
        Minuteur.demarrer(app, 10)
        Minuteur.echeance(app, forcer = true)
        hoteModules = FacticesPratiques.hote("bourse", "minuteur")
        afficher()
        focusSur("minuteur:bouton1")
        controler("module:minuteur")
        capturer("p8_bourse_minuteur_sonne_petits")
        // « Arrêter »
        appuyer(Key.DirectionCenter)
        assertEquals(ModeMinuteur.ARRET, Minuteur.etat(app).mode)
        attendreFocus("module:minuteur", "après « Arrêter »")
    }

    // ------------------------------------------------------------------ navigation

    /** ▲▼ règlent la durée sans bouger le focus ; ▶ / ◀ dans le module ; ◀ sur l'afficheur sort. */
    @Test
    fun minuteurNePiegePasLeFocus() {
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:afficheur")
        appuyer(Key.DirectionUp)
        attendreFocus("minuteur:afficheur", "▲ sur l'afficheur")
        assertEquals("▲ : 15 → 20 min", 20, Minuteur.etat(app).minutesChoisies)
        appuyer(Key.DirectionDown)
        appuyer(Key.DirectionDown)
        assertEquals("▼▼ : 20 → 10 min", 10, Minuteur.etat(app).minutesChoisies)
        appuyer(Key.DirectionRight)
        val f = focus()
        if (f == null || "module:minuteur" !in f.chaine || "minuteur:afficheur" in f.chaine) {
            fail("[${design.name}] ▶ depuis l'afficheur : attendu un préréglage, obtenu ${decrire(f)} (${f?.chaine})")
        }
        appuyer(Key.DirectionLeft)
        attendreFocus("minuteur:afficheur", "◀ depuis le premier préréglage")
        appuyer(Key.DirectionLeft)
        attendreFavori("◀ sur l'afficheur (élément le plus à gauche)")
    }

    @Test
    fun retourQuitteLaZone() {
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:prereglage:60")
        appuyer(Key.Back)
        attendreFavori("Retour depuis le préréglage « 1 h »")
    }

    /** Minuteur en cours, focus sur « Annuler » (tout à droite) : quelques ◀ suffisent à sortir. */
    @Test
    fun minuteurEnCoursSortieParLaGauche() {
        Minuteur.demarrer(app, 25)
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:bouton3")
        for (i in 1..3) {
            appuyer(Key.DirectionLeft)
            if (focus()?.etiquette == favori(0)) return
        }
        fail("[${design.name}] 3 × ◀ depuis « Annuler » : toujours ${decrire(focus())} (${focus()?.chaine})")
    }

    /** Préréglage « 5 » : démarre (alarme programmée), OK sur l'afficheur = pause, puis Annuler. */
    @Test
    fun minuteurDemarrePauseAnnule() {
        hoteModules = FacticesPratiques.hote("minuteur")
        afficher()
        focusSur("minuteur:prereglage:5")
        appuyer(Key.DirectionCenter)
        var e = Minuteur.etat(app)
        assertEquals(ModeMinuteur.EN_COURS, e.mode)
        assertEquals(5, e.minutesChoisies)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertNotNull("échéance programmée dans AlarmManager", shadowOf(am).nextScheduledAlarm)
        attendreFocus("minuteur:afficheur", "après le démarrage")
        appuyer(Key.DirectionCenter)
        e = Minuteur.etat(app)
        assertEquals(ModeMinuteur.PAUSE, e.mode)
        assertTrue("temps restant gardé", e.restant in 4 * 60_000L..5 * 60_000L)
        focusSur("minuteur:bouton3")
        appuyer(Key.DirectionCenter)
        assertEquals(ModeMinuteur.ARRET, Minuteur.etat(app).mode)
        assertNull("échéance annulée", shadowOf(am).nextScheduledAlarm)
        attendreFocus("minuteur:afficheur", "après « Annuler »")
    }

    /** Minuteur à droite (PETIT) : ◀ va au module de gauche, Retour sort de la zone. */
    @Test
    fun minuteurSecondModule() {
        hoteModules = FacticesPratiques.hote("bourse", "minuteur")
        afficher()
        focusSur("minuteur:afficheur")
        appuyer(Key.DirectionLeft)
        attendreFocus("module:bourse", "◀ depuis l'afficheur du 2e module")
        focusSur("minuteur:prereglage:30")
        appuyer(Key.Back)
        attendreFavori("Retour depuis un préréglage du 2e module")
    }

    // ------------------------------------------------------------------ outils

    /** Donne le focus à l'élément [tag] (ou à son premier descendant focalisable). */
    private fun focusSur(tag: String) {
        compose.onAllNodes(
            (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        avancer(1000)
        attendreFocus(tag, "préparation")
    }

    private fun focusDans(etiquette: String) = focusSur(etiquette)

    private fun attendreFocus(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || etiquette !in f.chaine) {
            fail("[${design.name}] $etape : focus attendu dans « $etiquette », obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
        }
    }

    private fun attendreFavori(etape: String) {
        val f = focus()
        if (f?.etiquette != favori(0)) fail("[${design.name}] $etape : attendu ${favori(0)}, obtenu ${decrire(f)} (${f?.chaine})")
    }

    private fun SemanticsNode.bornesCompletes(): Rect {
        val c = layoutInfo.coordinates
        return c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
    }

    /**
     * Rien n'est coupé : zone dans l'écran, module dans la zone, et chaque texte du module
     * entier (ni points de suspension, ni débordement) et à l'intérieur du module.
     */
    private fun controler(etiquette: String) {
        val racine = compose.onRoot().fetchSemanticsNode().size
        val zone = compose.onAllNodes(hasTestTag("nba"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] zone de modules introuvable")
        val z = zone.bornesCompletes()
        if (z.left < 0f || z.top < 0f || z.right > racine.width + 0.5f || z.bottom > racine.height + 0.5f) {
            fail("[${design.name}] zone de modules hors de l'écran : ${z.enU()}")
        }
        val module = compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return fail("[${design.name}] $etiquette introuvable")
        val m = module.bornesCompletes()
        val tol = 1.5f
        if (m.left < z.left - tol || m.top < z.top - tol || m.right > z.right + tol || m.bottom > z.bottom + tol) {
            fail("[${design.name}] $etiquette (${m.enU()}) dépasse de la zone (${z.enU()})")
        }
        val erreurs = mutableListOf<String>()
        val textes = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(etiquette)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        for (n in textes) {
            val texte = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "?"
            val resultats = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultats)
            val r = resultats.firstOrNull()
            if (r != null) {
                val ellipse = (0 until r.lineCount).any { r.isLineEllipsized(it) }
                if (r.hasVisualOverflow || ellipse) erreurs += "« $texte » coupé"
            }
            val b = n.bornesCompletes()
            if (b.left < m.left - tol || b.right > m.right + tol || b.top < m.top - tol || b.bottom > m.bottom + tol) {
                erreurs += "« $texte » (${b.enU()}) hors du module (${m.enU()})"
            }
        }
        if (textes.isEmpty()) erreurs += "aucun texte trouvé"
        if (erreurs.isNotEmpty()) fail("[${design.name}] $etiquette : " + erreurs.joinToString(" ; "))
        println("[${design.name}] $etiquette : ${textes.size} textes entiers, module ${m.enU()}")
    }
}

/** Minuteur sans interface : persistance, échéance par le récepteur, notifications, alarme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class MinuteurTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun remiseAZero() {
        Minuteur.oublier()
        Minuteur.annuler(app)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun survitALaFermeture() {
        Minuteur.demarrer(app, 10)
        val fin = Minuteur.etat(app).fin
        Minuteur.oublier()
        val e = Minuteur.etat(app)
        assertEquals(ModeMinuteur.EN_COURS, e.mode)
        assertEquals(fin, e.fin)
        Minuteur.pause(app)
        Minuteur.oublier()
        assertEquals(ModeMinuteur.PAUSE, Minuteur.etat(app).mode)
    }

    @Test
    fun echeanceParLeRecepteur() {
        Minuteur.demarrer(app, 10)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("notification de décompte", shadowOf(nm).allNotifications.any { it.channelId == Minuteur.CANAL_DECOMPTE })
        // Échéance passée : le récepteur (AlarmManager) déclenche l'alarme
        Minuteur.ecrire(app, Minuteur.etat(app).copy(fin = System.currentTimeMillis() - 1000L))
        MinuteurReceiver().onReceive(app, Intent(Minuteur.ACTION_ECHEANCE))
        assertEquals(ModeMinuteur.SONNE, Minuteur.etat(app).mode)
        val ecran = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals("écran d'alarme ouvert", AlarmeActivity::class.java.name, ecran?.component?.className)
        val alarme = shadowOf(nm).allNotifications.firstOrNull { it.channelId == Minuteur.CANAL_ALARME }
        assertNotNull("notification d'alarme", alarme)
        assertNotNull("plein écran", alarme!!.fullScreenIntent)
        // « Arrêter » depuis la notification
        MinuteurReceiver().onReceive(app, Intent(Minuteur.ACTION_ARRETER))
        assertEquals(ModeMinuteur.ARRET, Minuteur.etat(app).mode)
        assertTrue("notification d'alarme retirée", shadowOf(nm).allNotifications.none { it.channelId == Minuteur.CANAL_ALARME })
    }

    @Test
    fun annuleNeSonnePas() {
        Minuteur.demarrer(app, 1)
        Minuteur.annuler(app)
        MinuteurReceiver().onReceive(app, Intent(Minuteur.ACTION_ECHEANCE))
        assertEquals(ModeMinuteur.ARRET, Minuteur.etat(app).mode)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun echeanceAvanceeReprogrammee() {
        Minuteur.demarrer(app, 10)
        MinuteurReceiver().onReceive(app, Intent(Minuteur.ACTION_ECHEANCE))
        assertEquals(ModeMinuteur.EN_COURS, Minuteur.etat(app).mode)
    }

    @Test
    fun reveilProchaineOccurrence() {
        val maintenant = java.util.Calendar.getInstance().apply {
            set(2026, 9, 20, 22, 15, 0)
        }.timeInMillis
        val fin = Minuteur.prochaineOccurrence(7 * 60 + 30, maintenant)
        val c = java.util.Calendar.getInstance().apply { timeInMillis = fin }
        assertEquals(21, c.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(7, c.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(java.util.Calendar.MINUTE))
        assertEquals("12:34", Minuteur.formaterDuree(754_000L))
        assertEquals("1:02:03", Minuteur.formaterDuree(3_723_000L))
    }

    /** Écran d'alarme plein écran (capture), fermé par « Arrêter ». */
    @Test
    fun ecranAlarme() {
        Minuteur.demarrer(app, 15)
        Minuteur.echeance(app, forcer = true)
        val controleur = Robolectric.buildActivity(AlarmeActivity::class.java).setup()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        val fichier = File("build/captures/alarme_plein_ecran.png")
        fichier.parentFile?.mkdirs()
        controleur.get().window.decorView.captureRoboImage(fichier.path)
        Minuteur.arreter(app)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        assertTrue("écran d'alarme fermé après « Arrêter »", controleur.get().isFinishing)
    }
}

/** Formats et lecture des réponses (CoinGecko, Yahoo), sans réseau. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BourseAppareilTest {

    @Test
    fun yahoo() {
        val json = JSONObject(
            """{"chart":{"result":[{"meta":{"currency":"EUR","symbol":"^FCHI","regularMarketPrice":7512.34,
            "chartPreviousClose":7480.0},"timestamp":[1,2,3,4],"indicators":{"quote":[{"close":[7481.2,null,7500.5,7512.34]}]}}],"error":null}}""",
        )
        val c = BourseReseau.lireYahoo(json)
        assertEquals(7512.34, c.prix, 0.001)
        assertEquals(0.4323, c.variation!!, 0.001)
        assertEquals(listOf(7481.2, 7500.5, 7512.34, 7512.34), c.courbe)
        assertEquals("EUR", c.devise)
    }

    @Test
    fun coingecko() {
        val prix = BourseReseau.lirePrixCryptos(
            JSONObject("""{"bitcoin":{"eur":98432.17,"eur_24h_change":2.14},"ethereum":{"eur":3412.5}}"""),
            listOf("bitcoin", "ethereum", "solana"), "eur",
        )
        assertEquals(setOf("bitcoin", "ethereum"), prix.keys)
        assertEquals(2.14, prix["bitcoin"]!!.variation!!, 0.0001)
        assertNull(prix["ethereum"]!!.variation)
        val points = BourseReseau.lirePoints(org.json.JSONArray("[[1,10.0],[2,11.5],[3,null]]"))
        assertEquals(listOf(10.0, 11.5), points)
    }

    @Test
    fun formats() {
        val btc = INSTRUMENTS.first { it.id == "c:bitcoin" }
        val cac = INSTRUMENTS.first { it.id == "y:^FCHI" }
        assertEquals("98 432 €", formaterPrix(Cours(98432.17, null, emptyList(), "EUR"), btc))
        assertEquals("7 512", formaterPrix(Cours(7512.34, null, emptyList(), "EUR"), cac))
        assertEquals("0,5123 \$", formaterPrix(Cours(0.51234, null, emptyList(), "USD"), btc))
        assertEquals("+2,14 %", formaterVariation(2.14))
        assertEquals("−1,30 %", formaterVariation(-1.3))
        assertEquals("412 Mo", formaterOctets(432_013_312L))
        assertEquals("12,4 Go", formaterOctets(13_314_398_618L))
        // Réglages : 2 à 4 cours, ids inconnus ignorés
        val hote = FacticesPratiques.hote("bourse", reglages = mapOf("bourse.cours" to "c:bitcoin,x:inconnu"))
        assertEquals(2, ModuleBourse.choisis(hote.reglagesDe("bourse")).size)
    }

    @Test
    fun temperature() {
        val base = kotlin.io.path.createTempDirectory("thermal").toFile()
        File(base, "thermal_zone0").apply { mkdirs(); File(this, "type").writeText("battery\n"); File(this, "temp").writeText("31000\n") }
        File(base, "thermal_zone1").apply { mkdirs(); File(this, "type").writeText("cpu-thermal\n"); File(this, "temp").writeText("52400\n") }
        File(base, "thermal_zone2").apply { mkdirs(); File(this, "type").writeText("soc\n") }
        assertEquals(52.4, LecteurAppareil.lireTemperature(base)!!, 0.01)
        assertNull(LecteurAppareil.lireTemperature(File(base, "absent")))
        // Lecture réelle : jamais d'exception
        LecteurAppareil.lire(RuntimeEnvironment.getApplication())
    }
}
