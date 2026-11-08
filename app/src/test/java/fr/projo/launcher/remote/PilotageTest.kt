package fr.projo.launcher.remote

import fr.projo.launcher.ui.Design
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pilotage de test : applis et réglages en mémoire, rappels immédiats. */
class PilotageFactice : Pilotage {
    var liste = listOf(
        AppliTv("com.netflix.ninja", "Netflix", favori = true, maj = 11L),
        AppliTv("tv.arte.plus7", "arte", favori = false, maj = 22L),
        AppliTv("com.spotify.tv.android", "Spotify", favori = false, maj = 33L),
    )
    var etat = etatTv()
    var ouvertureReussie = true
    val ouvertes = mutableListOf<String>()
    val appliques = mutableListOf<Changement>()

    override fun applis(rappel: (List<AppliTv>) -> Unit) = rappel(liste)

    override fun images(applis: List<AppliTv>, encoreVoulu: () -> Boolean, rappel: (ImageAppli) -> Unit) {
        for (a in applis) {
            if (!encoreVoulu()) return
            rappel(ImageAppli(a.id, a.maj, ImageAppli.ICONE, "data:image/webp;base64,AAAA"))
        }
    }

    override fun ouvrir(id: String, rappel: (Boolean) -> Unit) {
        ouvertes += id
        rappel(ouvertureReussie)
    }

    override fun reglages(rappel: (List<ReglageTv>) -> Unit) = rappel(ReglagesTv.decrire(etat))

    override fun appliquer(changement: Changement, rappel: () -> Unit) {
        appliques += changement
        when (changement.cle) {
            ReglagesTv.VOLUME -> etat = etat.copy(volume = changement.valeur as Int)
            ReglagesTv.DESIGN -> etat = etat.copy(design = Design.from(changement.valeur as String))
        }
        rappel()
    }
}

/** Hôte de test avec (ou sans) pilotage, journal noté. */
class HotePilote(val base: HoteFactice, override val pilotage: Pilotage?) : HoteSession by base {
    val journaux = mutableListOf<String>()
    override fun journal(evenement: String) {
        journaux += evenement
    }
}

/**
 * Applis et réglages depuis le téléphone (SessionTelecommande) : annonce dans `hello`, rien
 * avant l'authentification, validation de tout ce qui arrive, abonnement aux réglages.
 */
@RunWith(RobolectricTestRunner::class)
class PilotageTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var base: HoteFactice
    private val pilotage = PilotageFactice()
    private val envoyes = mutableListOf<JSONObject>()
    private var fermee = false

    @Before
    fun preparer() {
        base = HoteFactice(dossier.root)
        base.registre.ajouter(TelephoneAppaire("tel-1", "Téléphone de Marie", Vecteurs.TEL_SPKI, 1L))
    }

    private fun session(avecPilotage: Boolean = true): Pair<SessionTelecommande, HotePilote> {
        val hote = HotePilote(base, if (avecPilotage) pilotage else null)
        envoyes.clear()
        fermee = false
        return SessionTelecommande(hote, Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, envoyer = { envoyes += JSONObject(it) }, fermer = { fermee = true }) to hote
    }

    private fun hello(appareil: String = "tel-1", cle: String? = null) =
        JSONObject().put("v", 1).put("t", "hello").put("appareil", appareil).put("nonce", Vecteurs.NONCE_TEL).put("sig", Vecteurs.SIG_TEL)
            .apply { if (cle != null) put("cle", cle) }.toString()

    private fun msg(t: String, vararg champs: Pair<String, Any?>) =
        JSONObject().put("v", 1).put("t", t).apply { champs.forEach { put(it.first, it.second) } }.toString()

    /** Session authentifiée, messages envoyés jusque-là oubliés. */
    private fun connectee(): Pair<SessionTelecommande, HotePilote> {
        val (s, hote) = session()
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        envoyes.clear()
        return s to hote
    }

    private fun types() = envoyes.map { it.getString("t") }

    private fun toutLePilotage(s: SessionTelecommande) {
        s.recevoir(msg("apps_liste"))
        s.recevoir(msg("apps_images", "ids" to JSONArray().put("com.netflix.ninja")))
        s.recevoir(msg("app_ouvrir", "id" to "com.netflix.ninja"))
        s.recevoir(msg("reglages_liste"))
        s.recevoir(msg("reglage", "cle" to "volume", "valeur" to 9))
    }

    // ------------------------------------------------------------------ annonce et authentification

    @Test
    fun fonctionsAnnonceesSeulementAvecLePilotage() {
        val (s, _) = session()
        s.recevoir(hello())
        val f = envoyes.single().getJSONArray("fonctions")
        assertEquals(listOf("apps", "reglages"), (0 until f.length()).map { f.getString(it) })

        val (sans, _) = session(avecPilotage = false)
        sans.recevoir(hello())
        assertEquals("hello", envoyes.single().getString("t"))
        assertFalse(envoyes.single().has("fonctions"))
        // Sans pilotage, ces messages sont ignorés
        toutLePilotage(sans)
        assertEquals(1, envoyes.size)
    }

    /** Thème de la télécommande : `design` dans `hello` si la TV le donne, absent sinon. */
    @Test
    fun designAnnonceDansHello() {
        val (sans, _) = session()
        sans.recevoir(hello())
        assertFalse(envoyes.single().has("design"))
        envoyes.clear()
        val hote = object : HoteSession by HotePilote(base, pilotage) {
            override val design: String = "galerie"
        }
        val s = SessionTelecommande(hote, Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, envoyer = { envoyes += JSONObject(it) }, fermer = {})
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        assertEquals("galerie", envoyes.single().getString("design"))
        // Changement de design : message `design`
        val d = JSONObject(Protocole.design("verre"))
        assertEquals(1, d.getInt("v"))
        assertEquals("design", d.getString("t"))
        assertEquals("verre", d.getString("id"))
    }

    @Test
    fun rienAvantLAuthentification() {
        val (s, _) = session()
        toutLePilotage(s)
        assertTrue(envoyes.isEmpty())
        assertTrue(pilotage.ouvertes.isEmpty())
        assertTrue(pilotage.appliques.isEmpty())
        assertFalse(s.abonneReglages)
    }

    @Test
    fun rienPendantUnAppairage() {
        val (s, _) = session()
        // Téléphone inconnu avec sa clé : seulement pair_request et ping
        s.recevoir(hello(appareil = "tel-2", cle = Vecteurs.TEL_SPKI))
        assertEquals(SessionTelecommande.Etat.CLE_VERIFIEE, s.etat)
        assertTrue(envoyes.single().has("fonctions"))
        envoyes.clear()
        toutLePilotage(s)
        assertTrue(envoyes.isEmpty())
        assertTrue(pilotage.ouvertes.isEmpty())
        assertTrue(pilotage.appliques.isEmpty())
    }

    // ------------------------------------------------------------------ applis

    @Test
    fun listeDesApplis() {
        val (s, hote) = connectee()
        s.recevoir(msg("apps_liste"))
        val m = envoyes.single()
        assertEquals("apps", m.getString("t"))
        assertEquals(1, m.getInt("v"))
        val l = m.getJSONArray("liste")
        assertEquals(3, l.length())
        val premiere = l.getJSONObject(0)
        assertEquals("com.netflix.ninja", premiere.getString("id"))
        assertEquals("Netflix", premiere.getString("nom"))
        assertTrue(premiere.getBoolean("favori"))
        assertEquals(11L, premiere.getLong("maj"))
        assertFalse(l.getJSONObject(1).getBoolean("favori"))
        // Journal : le type d'événement, jamais la liste
        assertTrue(hote.journaux.none { it.contains("netflix", ignoreCase = true) })
    }

    @Test
    fun listeTropLongueCoupeeSousLaLimiteDeLaPwa() {
        pilotage.liste = (1..2_000).map { AppliTv("com.exemple.appli$it", "Appli au nom assez long numéro $it", false, 1_700_000_000_000L) }
        val (s, _) = connectee()
        s.recevoir(msg("apps_liste"))
        val brut = envoyes.single().toString()
        assertTrue(brut.length <= Protocole.ENVOI_MAX)
        val l = envoyes.single().getJSONArray("liste")
        assertTrue(l.length() in 100 until 2_000)
        // Les premières (favoris, ordre de l'accueil) sont gardées
        assertEquals("com.exemple.appli1", l.getJSONObject(0).getString("id"))
    }

    @Test
    fun imagesDansLOrdreDemandeIdentifiantsInconnusIgnores() {
        val (s, _) = connectee()
        val ids = JSONArray().put("com.spotify.tv.android").put("com.inconnue").put(42).put("com.netflix.ninja").put("com.spotify.tv.android")
        s.recevoir(msg("apps_images", "ids" to ids))
        assertEquals(listOf("app_image", "app_image"), types())
        assertEquals(listOf("com.spotify.tv.android", "com.netflix.ninja"), envoyes.map { it.getString("id") })
        val img = envoyes[0]
        assertEquals(33L, img.getLong("maj"))
        assertEquals("icone", img.getString("forme"))
        assertTrue(img.getString("img").startsWith("data:image/"))
        // `ids` absent ou qui n'est pas une liste : rien
        envoyes.clear()
        s.recevoir(msg("apps_images"))
        s.recevoir(msg("apps_images", "ids" to "com.netflix.ninja"))
        assertTrue(envoyes.isEmpty())
    }

    @Test
    fun auPlus100Images() {
        pilotage.liste = (1..150).map { AppliTv("com.exemple.appli$it", "Appli $it", false, it.toLong()) }
        val (s, _) = connectee()
        val ids = JSONArray()
        pilotage.liste.forEach { ids.put(it.id) }
        s.recevoir(msg("apps_images", "ids" to ids))
        assertEquals(100, envoyes.size)
        assertEquals((1..100).map { "com.exemple.appli$it" }, envoyes.map { it.getString("id") })
    }

    @Test
    fun ouvrirUneAppliDeLaListe() {
        val (s, hote) = connectee()
        s.recevoir(msg("app_ouvrir", "id" to "tv.arte.plus7"))
        assertEquals(listOf("tv.arte.plus7"), pilotage.ouvertes)
        val r = envoyes.single()
        assertEquals("app_ouverte", r.getString("t"))
        assertEquals("tv.arte.plus7", r.getString("id"))
        assertTrue(r.getBoolean("ok"))
        assertTrue(hote.journaux.contains("appli ouverte"))

        // L'appli n'a pas pu s'ouvrir
        envoyes.clear()
        pilotage.ouvertureReussie = false
        s.recevoir(msg("app_ouvrir", "id" to "tv.arte.plus7"))
        assertFalse(envoyes.single().getBoolean("ok"))
    }

    @Test
    fun appliInconnueRefusee() {
        val (s, _) = connectee()
        s.recevoir(msg("app_ouvrir", "id" to "com.android.settings"))
        assertTrue(pilotage.ouvertes.isEmpty())
        val r = envoyes.single()
        assertEquals("app_ouverte", r.getString("t"))
        assertEquals("com.android.settings", r.getString("id"))
        assertFalse(r.getBoolean("ok"))
        // Identifiant absent, vide ou pas une chaîne : ignoré
        envoyes.clear()
        s.recevoir(msg("app_ouvrir"))
        s.recevoir(msg("app_ouvrir", "id" to ""))
        s.recevoir(msg("app_ouvrir", "id" to 12))
        s.recevoir(msg("app_ouvrir", "id" to "x".repeat(300)))
        assertTrue(envoyes.isEmpty())
        assertTrue(pilotage.ouvertes.isEmpty())
    }

    // ------------------------------------------------------------------ réglages

    private fun item(m: JSONObject, cle: String): JSONObject {
        val items = m.getJSONArray("items")
        for (i in 0 until items.length()) if (items.getJSONObject(i).getString("cle") == cle) return items.getJSONObject(i)
        throw AssertionError("réglage $cle absent")
    }

    @Test
    fun reglagesListeEnvoieLEtatEtAbonne() {
        val (s, _) = connectee()
        // Pas abonné : les changements vus par la TV ne lui sont pas envoyés
        s.envoyerReglagesSiAbonne(Protocole.reglages(ReglagesTv.decrire(pilotage.etat)))
        assertTrue(envoyes.isEmpty())

        s.recevoir(msg("reglages_liste"))
        assertTrue(s.abonneReglages)
        val m = envoyes.single()
        assertEquals("reglages", m.getString("t"))
        val volume = item(m, "volume")
        assertEquals("curseur", volume.getString("type"))
        assertEquals("Son", volume.getString("groupe"))
        assertEquals(5, volume.getInt("valeur"))
        assertEquals(0, volume.getInt("min"))
        assertEquals(15, volume.getInt("max"))
        assertFalse(volume.has("actif"))
        assertFalse(item(m, "muet").getBoolean("valeur"))
        val design = item(m, "design")
        assertEquals("choix", design.getString("type"))
        assertEquals("verre", design.getString("valeur"))
        val options = design.getJSONArray("options")
        assertEquals(Design.entries.map { it.id }, (0 until options.length()).map { options.getJSONObject(it).getString("id") })
        val maj = item(m, "maj")
        assertEquals("boutons", maj.getString("type"))
        assertEquals("verifier", maj.getJSONArray("boutons").getJSONObject(0).getString("id"))
        assertFalse(maj.has("progression"))

        envoyes.clear()
        s.envoyerReglagesSiAbonne(Protocole.reglages(ReglagesTv.decrire(pilotage.etat)))
        assertEquals(listOf("reglages"), types())
    }

    @Test
    fun abonnementPropreACetteConnexion() {
        val (s, _) = connectee()
        s.recevoir(msg("reglages_liste"))
        assertTrue(s.abonneReglages)
        s.terminer()
        // Reconnexion : pas abonné tant qu'il n'a pas redemandé
        val (s2, _) = connectee()
        assertFalse(s2.abonneReglages)
        s2.envoyerReglagesSiAbonne(Protocole.reglages(ReglagesTv.decrire(pilotage.etat)))
        assertTrue(envoyes.isEmpty())
    }

    @Test
    fun reglageAppliquePuisEtatRenvoye() {
        val (s, hote) = connectee()
        s.recevoir(msg("reglage", "cle" to "volume", "valeur" to 9))
        assertEquals(listOf(Changement("volume", 9)), pilotage.appliques)
        assertEquals(9, item(envoyes.single(), "volume").getInt("valeur"))
        assertTrue(hote.journaux.contains("réglage volume"))
        // Le journal ne contient pas la valeur
        assertTrue(hote.journaux.none { it.contains("9") })

        envoyes.clear()
        s.recevoir(msg("reglage", "cle" to "volume", "valeur" to 999))
        assertEquals(Changement("volume", 15), pilotage.appliques.last())
        assertEquals(15, item(envoyes.single(), "volume").getInt("valeur"))

        envoyes.clear()
        s.recevoir(msg("reglage", "cle" to "design", "valeur" to "chaines"))
        assertEquals(Changement("design", "chaines"), pilotage.appliques.last())
        assertEquals("chaines", item(envoyes.single(), "design").getString("valeur"))

        envoyes.clear()
        s.recevoir(msg("reglage", "cle" to "systeme", "bouton" to "wifi"))
        assertEquals(Changement("systeme", "wifi"), pilotage.appliques.last())
        assertEquals(listOf("reglages"), types())
    }

    @Test
    fun reglageInconnuIgnoreMaisEtatRenvoye() {
        val (s, hote) = connectee()
        val refuses = listOf(
            msg("reglage", "cle" to "formater", "valeur" to true),
            msg("reglage", "valeur" to 3),
            msg("reglage", "cle" to "volume", "valeur" to "fort"),
            msg("reglage", "cle" to "muet", "valeur" to 1),
            msg("reglage", "cle" to "design", "valeur" to "neon"),
            msg("reglage", "cle" to "maj", "bouton" to "installer"), // pas de nouvelle version
            msg("reglage", "cle" to "systeme", "bouton" to "formater"),
            msg("reglage", "cle" to "systeme", "valeur" to "wifi"),
        )
        for (r in refuses) s.recevoir(r)
        assertTrue(pilotage.appliques.isEmpty())
        // Un `reglages` par demande, pour que le téléphone se recale
        assertEquals(List(refuses.size) { "reglages" }, types())
        assertTrue(hote.journaux.contains("réglage ignoré"))
        assertTrue(hote.journaux.none { it.contains("formater") })
    }

    @Test
    fun majGriseeSansEcranDAccueil() {
        pilotage.etat = etatTv(maj = null)
        val (s, _) = connectee()
        s.recevoir(msg("reglages_liste"))
        val maj = item(envoyes.single(), "maj")
        assertFalse(maj.getBoolean("actif"))
        assertEquals("Ouvre iBiTV sur la TV", maj.getString("detail"))
        envoyes.clear()
        s.recevoir(msg("reglage", "cle" to "maj", "bouton" to "verifier"))
        assertTrue(pilotage.appliques.isEmpty())
        assertEquals(listOf("reglages"), types())
    }

    @Test
    fun installerQuandUneVersionEstDisponible() {
        pilotage.etat = etatTv(EtatMaj("1.4", telechargement = 0.5f, nouvelleVersion = "1.5", verifiee = true))
        val (s, _) = connectee()
        s.recevoir(msg("reglages_liste"))
        val maj = item(envoyes.single(), "maj")
        val boutons = maj.getJSONArray("boutons")
        assertEquals(listOf("verifier", "installer"), (0 until boutons.length()).map { boutons.getJSONObject(it).getString("id") })
        assertTrue(boutons.getJSONObject(1).getBoolean("principal"))
        assertEquals(0.5, maj.getDouble("progression"), 0.001)
        s.recevoir(msg("reglage", "cle" to "maj", "bouton" to "installer"))
        assertEquals(Changement("maj", "installer"), pilotage.appliques.single())
    }

    @Test
    fun plusRienApresLaFermeture() {
        val (s, _) = connectee()
        s.recevoir(msg("reglages_liste"))
        s.terminer()
        envoyes.clear()
        toutLePilotage(s)
        s.envoyerReglagesSiAbonne(Protocole.reglages(ReglagesTv.decrire(pilotage.etat)))
        assertTrue(envoyes.isEmpty())
        assertTrue(pilotage.ouvertes.isEmpty())
        assertNull(envoyes.firstOrNull())
    }
}
