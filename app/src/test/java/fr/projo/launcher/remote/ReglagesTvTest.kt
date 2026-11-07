package fr.projo.launcher.remote

import fr.projo.launcher.ambiance.CouleursNeon
import fr.projo.launcher.ambiance.Intensite
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.DefilementGalerie
import fr.projo.launcher.ui.Design
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** État de la TV pour les tests (valeurs par défaut d'iBiTV, volume 5 sur 15). */
fun etatTv(maj: EtatMaj? = EtatMaj("1.4", verifiee = true)) = EtatTv(
    volume = 5,
    volumeMax = 15,
    muet = false,
    design = Design.VERRE,
    nouveautes = true,
    ambianceAuto = true,
    intensite = Intensite.NORMALE,
    couleurs = CouleursNeon.POCHETTE,
    decalageMs = 100,
    decalages = listOf(0, 100, 200, 300, 400),
    decalageConseille = 100,
    maj = maj,
)

/**
 * Pilotage depuis le téléphone, partie sans Android : description des réglages, validation de
 * `reglage`, ordre des applis, regroupement des renvois de `reglages`.
 */
class ReglagesTvTest {

    private fun items(e: EtatTv = etatTv()) = ReglagesTv.decrire(e)
    private inline fun <reified T : ReglageTv> List<ReglageTv>.de(cle: String): T = first { it.cle == cle } as T

    // ------------------------------------------------------------------ description

    @Test
    fun reglagesDansLOrdreDeLaSpecification() {
        val r = items()
        assertEquals(
            listOf(
                "volume", "muet", "design", "nouveautes", "ambiance_auto", "ambiance_intensite", "ambiance_couleurs", "ambiance_decalage",
                "liens_seulement_vpn", "liens_pages", "liens_raccourci", "liens_code", "maj", "systeme",
            ),
            r.map { it.cle },
        )
        assertEquals(
            listOf(
                "curseur", "interrupteur", "choix", "interrupteur", "interrupteur", "choix", "choix", "choix",
                "interrupteur", "choix", "interrupteur", "boutons", "boutons", "boutons",
            ),
            r.map { it.type },
        )
        // Les réglages d'un même groupe se suivent
        val groupes = r.map { it.groupe }
        assertEquals(listOf("Son", "Affichage", "Mode Ambiance", "Envoyer sur la TV", "Mises à jour", "Ouvrir sur la TV"), groupes.distinct())
        groupes.distinct().forEach { g ->
            val indices = groupes.indices.filter { groupes[it] == g }
            assertEquals("groupe $g contigu", indices.last() - indices.first() + 1, indices.size)
        }
        assertTrue(r.all { it.titre.isNotBlank() && it.actif })
    }

    @Test
    fun volumeEntreZeroEtLeMaximumDuFluxMusique() {
        val v = items().de<ReglageTv.Curseur>("volume")
        assertEquals(0, v.min)
        assertEquals(15, v.max)
        assertEquals(5, v.valeur)
        // Valeur lue hors bornes : ramenée dedans ; maximum nul : au moins 1
        val bizarre = items(etatTv().copy(volume = 40, volumeMax = 0)).de<ReglageTv.Curseur>("volume")
        assertEquals(1, bizarre.max)
        assertEquals(1, bizarre.valeur)
        val muet = items(etatTv().copy(muet = true))
        assertTrue(muet.de<ReglageTv.Interrupteur>("muet").valeur)
        assertEquals("Son coupé", muet.de<ReglageTv.Curseur>("volume").detail)
    }

    @Test
    fun designsProposesEtDesignActuel() {
        val d = items(etatTv().copy(design = Design.STUDIO)).de<ReglageTv.Choix>("design")
        assertEquals(Design.entries.map { it.id }, d.options.map { it.id })
        assertEquals(Design.entries.map { it.nom }, d.options.map { it.nom })
        assertEquals("studio", d.valeur)
    }

    /** Galerie seulement : « Défilement des affiches », juste après les affiches des nouveautés. */
    @Test
    fun defilementDesAffichesAvecGalerieSeulement() {
        assertFalse(items().any { it.cle == ReglagesTv.GALERIE_DEFILEMENT })
        val r = items(etatTv().copy(design = Design.GALERIE, defilementGalerie = 15))
        val cles = r.map { it.cle }
        assertEquals(cles.indexOf("nouveautes") + 1, cles.indexOf(ReglagesTv.GALERIE_DEFILEMENT))
        val d = r.de<ReglageTv.Choix>(ReglagesTv.GALERIE_DEFILEMENT)
        assertEquals("Affichage", d.groupe)
        assertEquals("15", d.valeur)
        assertEquals(listOf("0", "5", "8", "15"), d.options.map { it.id })
        assertEquals(listOf("Désactivé", "Toutes les 5 s", "Toutes les 8 s", "Toutes les 15 s"), d.options.map { it.nom })
        assertEquals(Changement(ReglagesTv.GALERIE_DEFILEMENT, "0"), ReglagesTv.valider(r, ReglagesTv.GALERIE_DEFILEMENT, "0", null))
        assertEquals(Changement(ReglagesTv.GALERIE_DEFILEMENT, "8"), ReglagesTv.valider(r, ReglagesTv.GALERIE_DEFILEMENT, 8, null))
        assertNull(ReglagesTv.valider(r, ReglagesTv.GALERIE_DEFILEMENT, "7", null))
        // Un autre design : le réglage n'existe pas, le changement est ignoré
        assertNull(ReglagesTv.valider(items(), ReglagesTv.GALERIE_DEFILEMENT, "5", null))
        // Tuile de la TV : chaque OK passe au choix suivant, en boucle
        assertEquals(listOf(15, 0, 5, 8), listOf(8, 15, 0, 5).map { DefilementGalerie.suivant(it) })
    }

    @Test
    fun modeAmbiance() {
        val r = items(etatTv().copy(intensite = Intensite.FORTE, couleurs = CouleursNeon.ARC_EN_CIEL, ambianceAuto = false))
        assertFalse(r.de<ReglageTv.Interrupteur>("ambiance_auto").valeur)
        val i = r.de<ReglageTv.Choix>("ambiance_intensite")
        assertEquals(listOf("douce", "normale", "forte"), i.options.map { it.id })
        assertEquals(listOf("Douce", "Normale", "Forte"), i.options.map { it.nom })
        assertEquals("forte", i.valeur)
        val c = r.de<ReglageTv.Choix>("ambiance_couleurs")
        assertEquals(listOf("pochette", "arc"), c.options.map { it.id })
        assertEquals(listOf("Pochette", "Arc-en-ciel"), c.options.map { it.nom })
        assertEquals("arc", c.valeur)
        val d = r.de<ReglageTv.Choix>("ambiance_decalage")
        assertEquals(listOf("0", "100", "200", "300", "400"), d.options.map { it.id })
        assertEquals("100", d.valeur)
        assertEquals("Aucun retard", d.options[0].nom)
        assertEquals("Retard de 0,1 s (conseillé)", d.options[1].nom)
        assertEquals("Retard de 0,4 s", d.options[4].nom)
        // Valeur ancienne hors de la liste : ajoutée pour rester sélectionnée
        val ancien = items(etatTv().copy(decalageMs = 150)).de<ReglageTv.Choix>("ambiance_decalage")
        assertEquals(listOf("0", "100", "150", "200", "300", "400"), ancien.options.map { it.id })
        assertEquals("150", ancien.valeur)
    }

    @Test
    fun boutonInstallerSeulementSiUneVersionEstDisponible() {
        val aJour = items(etatTv(EtatMaj("1.4", verifiee = true))).de<ReglageTv.Boutons>("maj")
        assertEquals(listOf("verifier"), aJour.boutons.map { it.id })
        assertTrue(aJour.boutons.single().principal)
        assertEquals("Version 1.4 · à jour", aJour.detail)
        assertNull(aJour.progression)

        val dispo = items(etatTv(EtatMaj("1.4", nouvelleVersion = "1.5", verifiee = true))).de<ReglageTv.Boutons>("maj")
        assertEquals(listOf("verifier", "installer"), dispo.boutons.map { it.id })
        assertTrue(dispo.boutons[1].principal)
        assertFalse(dispo.boutons[0].principal)
        assertEquals("Version 1.4 · nouvelle version 1.5", dispo.detail)
        assertTrue(dispo.actif)

        val enCours = items(etatTv(EtatMaj("1.4", telechargement = 0.42f, nouvelleVersion = "1.5", verifiee = true))).de<ReglageTv.Boutons>("maj")
        assertEquals(0.42f, enCours.progression!!, 0.0001f)
        assertEquals("Version 1.4 · téléchargement… 42 %", enCours.detail)

        val verification = items(etatTv(EtatMaj("1.4", verification = true))).de<ReglageTv.Boutons>("maj")
        assertEquals("Version 1.4 · vérification…", verification.detail)
        val erreur = items(etatTv(EtatMaj("1.4", message = "Vérification impossible (connexion ?)"))).de<ReglageTv.Boutons>("maj")
        assertEquals("Version 1.4 · Vérification impossible (connexion ?)", erreur.detail)
    }

    @Test
    fun majGriseeSansEcranDAccueil() {
        val m = items(etatTv(maj = null)).de<ReglageTv.Boutons>("maj")
        assertFalse(m.actif)
        assertEquals("Ouvre iBiTV sur la TV", m.detail)
        assertTrue(m.boutons.none { it.id == "installer" })
        // Rien n'est accepté sur un réglage grisé
        assertNull(ReglagesTv.valider(items(etatTv(maj = null)), "maj", null, "verifier"))
    }

    /** Groupe « Envoyer sur la TV » : valeurs par défaut, applis des pages web, raccourci iOS et son code. */
    @Test
    fun envoyerSurLaTv() {
        // Par défaut : seulement avec le VPN, pages en automatique, raccourci coupé
        val defaut = items()
        assertTrue(defaut.de<ReglageTv.Interrupteur>(ReglagesTv.LIENS_SEULEMENT_VPN).valeur)
        assertFalse(defaut.de<ReglageTv.Interrupteur>(ReglagesTv.LIENS_RACCOURCI).valeur)
        val pagesSeules = defaut.de<ReglageTv.Choix>(ReglagesTv.LIENS_PAGES)
        assertEquals("auto", pagesSeules.valeur)
        assertEquals(listOf("auto"), pagesSeules.options.map { it.id })
        assertEquals("Aucune appli de la TV n'ouvre les pages web", pagesSeules.options[0].detail)
        assertEquals(listOf("nouveau"), defaut.de<ReglageTv.Boutons>(ReglagesTv.LIENS_CODE).boutons.map { it.id })
        assertEquals("Nouveau code", defaut.de<ReglageTv.Boutons>(ReglagesTv.LIENS_CODE).boutons[0].nom)
        assertTrue(defaut.filter { it.cle.startsWith("liens_") }.all { it.groupe == "Envoyer sur la TV" })

        val navigateurs = listOf(OptionReglage("net.vypn.app", "VYPN"), OptionReglage("com.amazon.cloud9", "Silk"))
        val e = etatTv().copy(envoi = EtatEnvoi(seulementVpn = false, pages = "com.amazon.cloud9", navigateurs = navigateurs, raccourci = true))
        val r = items(e)
        assertFalse(r.de<ReglageTv.Interrupteur>(ReglagesTv.LIENS_SEULEMENT_VPN).valeur)
        assertTrue(r.de<ReglageTv.Interrupteur>(ReglagesTv.LIENS_RACCOURCI).valeur)
        val pages = r.de<ReglageTv.Choix>(ReglagesTv.LIENS_PAGES)
        assertEquals("com.amazon.cloud9", pages.valeur)
        assertEquals(listOf("auto", "net.vypn.app", "com.amazon.cloud9"), pages.options.map { it.id })
        assertEquals(listOf("Automatique", "VYPN", "Silk"), pages.options.map { it.nom })
        assertEquals("Aujourd'hui : VYPN", pages.options[0].detail)
        // Choix enregistré qui n'est plus installé : automatique
        val parti = items(e.copy(envoi = e.envoi.copy(pages = "org.desinstalle"))).de<ReglageTv.Choix>(ReglagesTv.LIENS_PAGES)
        assertEquals("auto", parti.valeur)

        // Validation : interrupteurs, choix parmi les applis installées, bouton « nouveau »
        assertEquals(Changement(ReglagesTv.LIENS_SEULEMENT_VPN, true), ReglagesTv.valider(r, ReglagesTv.LIENS_SEULEMENT_VPN, true, null))
        assertEquals(Changement(ReglagesTv.LIENS_RACCOURCI, false), ReglagesTv.valider(r, ReglagesTv.LIENS_RACCOURCI, false, null))
        assertNull(ReglagesTv.valider(r, ReglagesTv.LIENS_RACCOURCI, "oui", null))
        assertEquals(Changement(ReglagesTv.LIENS_PAGES, "net.vypn.app"), ReglagesTv.valider(r, ReglagesTv.LIENS_PAGES, "net.vypn.app", null))
        assertEquals(Changement(ReglagesTv.LIENS_PAGES, "auto"), ReglagesTv.valider(r, ReglagesTv.LIENS_PAGES, "auto", null))
        assertNull(ReglagesTv.valider(r, ReglagesTv.LIENS_PAGES, "com.android.settings", null))
        assertEquals(Changement(ReglagesTv.LIENS_CODE, "nouveau"), ReglagesTv.valider(r, ReglagesTv.LIENS_CODE, null, "nouveau"))
        assertNull(ReglagesTv.valider(r, ReglagesTv.LIENS_CODE, null, "afficher"))
    }

    @Test
    fun pagesSystemeSurLaTv() {
        val s = items().de<ReglageTv.Boutons>("systeme")
        assertEquals(listOf("wifi", "bluetooth", "reglages"), s.boutons.map { it.id })
    }

    // ------------------------------------------------------------------ validation de `reglage`

    @Test
    fun curseurArrondiEtRameneDansSesBornes() {
        val r = items()
        assertEquals(Changement("volume", 7), ReglagesTv.valider(r, "volume", 7, null))
        assertEquals(Changement("volume", 15), ReglagesTv.valider(r, "volume", 999, null))
        assertEquals(Changement("volume", 0), ReglagesTv.valider(r, "volume", -3, null))
        assertEquals(Changement("volume", 4), ReglagesTv.valider(r, "volume", 3.6, null))
        assertEquals(Changement("volume", 15), ReglagesTv.valider(r, "volume", Long.MAX_VALUE, null))
        assertNull(ReglagesTv.valider(r, "volume", Double.NaN, null))
        assertNull(ReglagesTv.valider(r, "volume", "7", null))
        assertNull(ReglagesTv.valider(r, "volume", true, null))
        assertNull(ReglagesTv.valider(r, "volume", null, null))
    }

    @Test
    fun interrupteurChoixEtBoutons() {
        val r = items()
        assertEquals(Changement("muet", true), ReglagesTv.valider(r, "muet", true, null))
        assertNull(ReglagesTv.valider(r, "muet", 1, null))
        assertNull(ReglagesTv.valider(r, "muet", "true", null))

        assertEquals(Changement("design", "pastel"), ReglagesTv.valider(r, "design", "pastel", null))
        assertNull(ReglagesTv.valider(r, "design", "neon", null))
        assertNull(ReglagesTv.valider(r, "design", null, "pastel"))
        // Identifiant numérique envoyé comme un nombre : accepté
        assertEquals(Changement("ambiance_decalage", "200"), ReglagesTv.valider(r, "ambiance_decalage", 200, null))
        assertEquals(Changement("ambiance_decalage", "200"), ReglagesTv.valider(r, "ambiance_decalage", "200", null))
        assertNull(ReglagesTv.valider(r, "ambiance_decalage", 250, null))

        assertEquals(Changement("maj", "verifier"), ReglagesTv.valider(r, "maj", null, "verifier"))
        // Pas de nouvelle version : pas de bouton « installer »
        assertNull(ReglagesTv.valider(r, "maj", null, "installer"))
        val dispo = items(etatTv(EtatMaj("1.4", nouvelleVersion = "1.5")))
        assertEquals(Changement("maj", "installer"), ReglagesTv.valider(dispo, "maj", null, "installer"))
        assertEquals(Changement("systeme", "wifi"), ReglagesTv.valider(r, "systeme", null, "wifi"))
        assertNull(ReglagesTv.valider(r, "systeme", "wifi", null))
        assertNull(ReglagesTv.valider(r, "systeme", null, "formater"))
    }

    @Test
    fun cleInconnueIgnoree() {
        val r = items()
        assertNull(ReglagesTv.valider(r, "formater", true, null))
        assertNull(ReglagesTv.valider(r, null, true, null))
        assertNull(ReglagesTv.valider(r, 3, true, null))
        assertNull(ReglagesTv.valider(emptyList(), "volume", 3, null))
    }

    // ------------------------------------------------------------------ liste des applis

    @Test
    fun favorisDAbordPuisLesAutresParOrdreAlphabetique() {
        val netflix = AppInfo("com.netflix.ninja", "Netflix", true)
        val youtube = AppInfo("com.google.android.youtube.tv", "YouTube", true)
        val arte = AppInfo("tv.arte.plus7", "arte", true)
        val spotify = AppInfo("com.spotify.tv.android", "Spotify", true)
        val ibitv = AppInfo("fr.projo.launcher", "iBiTV", false)
        val majs = mapOf(netflix.packageName to 11L, youtube.packageName to 22L)
        val liste = ListeApplis.construire(
            favoris = listOf(youtube, netflix),
            toutes = listOf(spotify, netflix, AppInfo.HDMI, arte, youtube, ibitv),
            paquetIbitv = "fr.projo.launcher",
        ) { majs[it] ?: 0L }
        assertEquals(
            listOf("com.google.android.youtube.tv", "com.netflix.ninja", "tv.arte.plus7", "com.spotify.tv.android"),
            liste.map { it.id },
        )
        assertEquals(listOf(true, true, false, false), liste.map { it.favori })
        assertEquals(AppliTv("com.google.android.youtube.tv", "YouTube", true, 22L), liste[0])
        assertEquals(0L, liste[3].maj)
    }

    // ------------------------------------------------------------------ regroupement

    @Test
    fun auPlusUnRenvoiToutesLes300ms() {
        var temps = 10_000L
        val taches = mutableListOf<Pair<Long, () -> Unit>>()
        var envois = 0
        val r = Regroupement(300, { temps }, { delai, tache -> taches += (temps + delai) to tache }) { envois++ }
        fun avancer(jusqua: Long) {
            while (true) {
                val prochaine = taches.filter { it.first <= jusqua }.minByOrNull { it.first } ?: break
                taches.remove(prochaine)
                temps = prochaine.first
                prochaine.second()
            }
            temps = jusqua
        }

        // Premier signal après un calme : tout de suite
        r.signaler()
        assertEquals(10_000L, taches.single().first)
        avancer(10_000)
        assertEquals(1, envois)

        // Trois signaux rapprochés : un seul envoi, 300 ms après le précédent
        avancer(10_050)
        r.signaler()
        r.signaler()
        avancer(10_200)
        r.signaler()
        assertEquals(1, taches.size)
        assertEquals(10_300L, taches.single().first)
        avancer(10_299)
        assertEquals(1, envois)
        avancer(10_300)
        assertEquals(2, envois)

        // Longtemps après : de nouveau tout de suite
        avancer(20_000)
        r.signaler()
        assertEquals(20_000L, taches.single().first)
        avancer(20_000)
        assertEquals(3, envois)
    }
}
