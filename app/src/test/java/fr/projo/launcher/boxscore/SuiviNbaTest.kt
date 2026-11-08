package fr.projo.launcher.boxscore

import fr.projo.launcher.boxscore.SuiviNba.Etape
import fr.projo.launcher.boxscore.SuiviNba.Reponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Match de test : en cours (4e QT) sauf indication contraire. */
fun matchNba(
    ext: String,
    dom: String,
    debut: Long,
    scoreExt: Int = 90,
    scoreDom: Int = 92,
    etat: EtatMatch = EtatMatch.EN_COURS,
    horloge: Double? = 151.0,
) = MatchNba(
    id = "$ext-$dom",
    idSource = "00225$debut",
    debut = debut,
    ext = EquipeMatch(ext, EquipesNba.de(ext).nom, scoreExt, listOf(20, 20)),
    dom = EquipeMatch(dom, EquipesNba.de(dom).nom, scoreDom, listOf(21, 21)),
    etat = etat,
    periode = 4,
    horloge = horloge,
    finDePeriode = false,
)

/**
 * Suivi des scores, sans Android ni réseau : rythme des requêtes (rien sans abonné ni
 * affichage), repli NBA → ESPN, décalage, choix du match, format affiché, état `nba`.
 */
class SuiviNbaTest {

    private val lalBos = matchNba("LAL", "BOS", 2_000)
    private val gswNyk = matchNba("GSW", "NYK", 1_000)
    private val miaDen = matchNba("MIA", "DEN", 3_000)
    private val fini = matchNba("SAS", "UTA", 500, etat = EtatMatch.TERMINE)
    private val aVenir = matchNba("POR", "WAS", 9_000, etat = EtatMatch.A_VENIR)
    private val tableau = listOf(lalBos, gswNyk, miaDen, fini, aVenir)

    private fun suivi(accessibilite: Boolean = true) = SuiviNba().apply { this.accessibilite = accessibilite }

    /** Exécute les requêtes dues à [t] avec les réponses données (une étape « Attendre » arrête). */
    private fun SuiviNba.servir(
        t: Long,
        tab: (SourceNba) -> Reponse<List<MatchNba>> = { Reponse.Ok(tableau) },
        box: (Etape.Box) -> Reponse<BoxScore> = { Reponse.Ok(BoxScore.depuisMatch(it.match)) },
    ): List<Etape> {
        val faites = mutableListOf<Etape>()
        repeat(10) {
            when (val e = prochaine(t)) {
                is Etape.Tableau -> { faites += e; tableauRecu(t, e.source, tab(e.source)) }
                is Etape.Box -> { faites += e; boxRecu(t, e, box(e)) }
                else -> return faites
            }
        }
        throw AssertionError("requêtes sans fin : $faites")
    }

    // ------------------------------------------------------------------ rythme

    @Test
    fun rienSansAbonneNiAffichage() {
        val s = suivi()
        assertEquals(Etape.Arret, s.prochaine(0))
        assertTrue(s.etat().matchs.isEmpty())
        assertNull(s.etat().choisi)
        // Afficher sans match : reste caché
        assertEquals(Affichage.CACHE, s.afficher(Affichage.RESUME, null, 0))
        assertEquals(Etape.Arret, s.prochaine(0))
    }

    @Test
    fun tableauToutesLes30sPourUnAbonne() {
        val s = suivi()
        s.abonnement(true, 0)
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA)), s.servir(0))
        assertEquals(Etape.Attendre(30_000), s.prochaine(1_000))
        assertTrue(s.servir(29_999).isEmpty())
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA)), s.servir(30_000))
        // Plus d'abonné : plus rien
        s.abonnement(false, 31_000)
        assertEquals(Etape.Arret, s.prochaine(70_000))
    }

    @Test
    fun boxScoreToutesLes15sSeulementSIlEstAffiche() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        assertEquals("GSW-NYK", s.etat().choisi)
        assertEquals(Affichage.RESUME, s.afficher(Affichage.RESUME, null, 1_000))
        val faites = s.servir(1_000)
        assertEquals(1, faites.size)
        val b = faites.single() as Etape.Box
        assertEquals("GSW-NYK", b.match.id)
        assertEquals(SourceNba.NBA, b.source)
        assertEquals(Etape.Attendre(16_000), s.prochaine(2_000))
        assertTrue(s.servir(16_000).single() is Etape.Box)
        assertEquals(Etape.Tableau(SourceNba.NBA), s.servir(30_000).first())
        // Caché, plus d'abonné : rien ; caché avec un abonné : le tableau seulement
        s.cacher(31_000)
        assertEquals(Etape.Attendre(60_000), s.prochaine(32_000))
        s.abonnement(false, 33_000)
        assertEquals(Etape.Arret, s.prochaine(33_000))
    }

    @Test
    fun boxScoreAfficheSansAbonneChargeAussiLeTableau() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.DETAILS, "LAL-BOS", 0)
        s.abonnement(false, 0)
        val faites = s.servir(0)
        assertEquals(listOf("LAL-BOS"), faites.filterIsInstance<Etape.Box>().map { it.match.id })
        assertTrue(s.servir(30_000).any { it is Etape.Tableau })
    }

    // ------------------------------------------------------------------ sources

    @Test
    fun repliSurEspnApresDeuxEchecsPuisRetourANba() {
        val s = suivi()
        s.abonnement(true, 0)
        val espn = listOf(lalBos)
        val reponses: (SourceNba) -> Reponse<List<MatchNba>> = { src -> if (src == SourceNba.NBA) Reponse.Echec(403) else Reponse.Ok(espn) }
        // 1er échec : on attend le tour suivant
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA)), s.servir(0, reponses))
        assertEquals(SourceNba.NBA, s.source)
        assertNull(s.etat().erreur)
        // 2e échec : ESPN tout de suite
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA), Etape.Tableau(SourceNba.ESPN)), s.servir(30_000, reponses))
        assertEquals(SourceNba.ESPN, s.source)
        assertEquals(listOf("LAL-BOS"), s.etat().matchs.map { it.id })
        // ESPN tant que les 10 minutes ne sont pas passées
        assertEquals(listOf(Etape.Tableau(SourceNba.ESPN)), s.servir(60_000, reponses))
        // Après 10 minutes : la source NBA est réessayée ; ratée → ESPN aussitôt
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA), Etape.Tableau(SourceNba.ESPN)), s.servir(630_000, reponses))
        assertEquals(SourceNba.ESPN, s.source)
        // ESPN échoue aussi : erreur « source »
        s.servir(660_000, tab = { Reponse.Echec() })
        assertEquals(EtatNba.ERREUR_SOURCE, s.etat().erreur)
        // Retour de la source NBA (10 minutes après le dernier essai)
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA)), s.servir(1_230_000))
        assertEquals(SourceNba.NBA, s.source)
        assertNull(s.etat().erreur)
    }

    @Test
    fun boxScoreRepliEtPasEncoreCommence() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.RESUME, "LAL-BOS", 0)
        // 403 avant l'entre-deux : pas un échec, l'en-tête du tableau s'affiche
        s.servir(0, box = { Reponse.PasCommence })
        assertEquals(SourceNba.NBA, s.source)
        val v = s.vueTv()
        assertEquals("LAL-BOS", v.box!!.match.id)
        assertTrue(v.box!!.ext.joueurs.isEmpty())
        assertNull(v.attente)
        // Box score NBA en erreur, puis tableau NBA en erreur : 2 échecs de suite, repli sur ESPN
        // (tableau ESPN d'abord : identifiant du match chez ESPN), puis box score ESPN
        s.servir(15_000, box = { Reponse.Echec(500) })
        assertEquals(SourceNba.NBA, s.source)
        val faites = s.servir(
            30_000,
            tab = { src -> if (src == SourceNba.NBA) Reponse.Echec(503) else Reponse.Ok(tableau) },
            box = { if (it.source == SourceNba.NBA) Reponse.Echec(500) else Reponse.Ok(BoxScore.depuisMatch(it.match)) },
        )
        assertEquals(SourceNba.ESPN, s.source)
        assertEquals(listOf(Etape.Tableau(SourceNba.NBA), Etape.Tableau(SourceNba.ESPN)), faites.filterIsInstance<Etape.Tableau>())
        assertEquals(SourceNba.ESPN, (faites.last() as Etape.Box).source)
        // Un succès entre deux échecs remet le compte à zéro
        val s2 = suivi()
        s2.abonnement(true, 0)
        s2.servir(0, tab = { Reponse.Echec() })
        s2.servir(30_000)
        s2.servir(60_000, tab = { Reponse.Echec() })
        assertEquals(SourceNba.NBA, s2.source)
    }

    // ------------------------------------------------------------------ décalage

    @Test
    fun decalageMontreLaReponseArriveeIlYAAuMoinsNSecondes() {
        val s = suivi()
        s.decalage = 30
        s.abonnement(true, 0)
        s.servir(0, tab = { Reponse.Ok(listOf(lalBos.copy(ext = lalBos.ext.copy(score = 10)))) })
        // Rien d'assez ancien : `nba` garde les dernières valeurs (aucune ici)
        assertTrue(s.etat().matchs.isEmpty())
        assertEquals(30_000L, s.prochaineMaturite())
        s.avancer(29_999)
        assertTrue(s.etat().matchs.isEmpty())
        s.servir(30_000, tab = { Reponse.Ok(listOf(lalBos.copy(ext = lalBos.ext.copy(score = 20)))) })
        // À 30 s : la réponse de 0 s (pas celle de 30 s)
        assertEquals(10, s.etat().matchs.single().ext.score)
        s.avancer(59_999)
        assertEquals(10, s.etat().matchs.single().ext.score)
        s.avancer(60_000)
        assertEquals(20, s.etat().matchs.single().ext.score)
        // Sans décalage : la plus récente tout de suite
        s.servir(90_000, tab = { Reponse.Ok(listOf(lalBos.copy(ext = lalBos.ext.copy(score = 30)))) })
        s.decalage = 0
        s.avancer(90_000)
        assertEquals(30, s.etat().matchs.single().ext.score)
        // Décalage rallongé : on ne revient pas en arrière, on attend la suite
        s.decalage = 60
        s.servir(120_000, tab = { Reponse.Ok(listOf(lalBos.copy(ext = lalBos.ext.copy(score = 40)))) })
        s.avancer(150_000)
        assertEquals(30, s.etat().matchs.single().ext.score)
        s.avancer(180_000)
        assertEquals(40, s.etat().matchs.single().ext.score)
        assertEquals(60, s.etat().decalage)
    }

    @Test
    fun synchronisationAvecLaVideoAuDebutDuSuivi() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.decalage = 15
        s.afficher(Affichage.RESUME, "LAL-BOS", 0)
        s.servir(0)
        // Le tableau était déjà montré ; le box score commence : attente
        assertEquals(VueTv(Affichage.RESUME, null, SuiviNba.SYNCHRONISATION, 0), s.vueTv())
        s.avancer(15_000)
        assertNotNull(s.vueTv().box)
        assertNull(s.vueTv().attente)
        // Caché puis réaffiché : le suivi repart de zéro
        s.cacher(16_000)
        assertEquals(VueTv.CACHEE, s.vueTv())
        s.afficher(Affichage.DETAILS, null, 20_000)
        assertEquals(SuiviNba.SYNCHRONISATION, s.vueTv().attente)
        // Sans décalage : « Chargement… » jusqu'à la première réponse
        s.decalage = 0
        assertEquals(SuiviNba.CHARGEMENT, s.vueTv().attente)
        s.servir(20_000)
        assertNull(s.vueTv().attente)
    }

    @Test
    fun boxScorePlusFraisQueLeTableauPourLeMatchChoisi() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.RESUME, "LAL-BOS", 0)
        s.servir(0)
        val plusTard = lalBos.copy(ext = lalBos.ext.copy(score = 99), horloge = 100.0)
        s.servir(15_000, box = { Reponse.Ok(BoxScore.depuisMatch(plusTard)) })
        val m = s.etat().matchs.first { it.id == "LAL-BOS" }
        assertEquals(99, m.ext.score)
        assertEquals("4e QT · 1:40", m.statut)
        assertEquals(90, s.etat().matchs.first { it.id == "GSW-NYK" }.ext.score)
    }

    // ------------------------------------------------------------------ choix et format

    @Test
    fun listeEtChoixDuMatch() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        val e = s.etat()
        // Matchs en cours seulement, par heure de début
        assertEquals(listOf("GSW-NYK", "LAL-BOS", "MIA-DEN"), e.matchs.map { it.id })
        assertEquals("GSW-NYK", e.choisi)
        val m = e.matchs[1]
        assertEquals(EquipeEtat("LAL", "Lakers", 90, "#6c3fb5", "#f2b631"), m.ext)
        assertEquals(EquipeEtat("BOS", "Celtics", 92, "#0d8a4f", "#ffffff"), m.dom)
        assertTrue(m.direct)
        assertEquals("4e QT · 2:31", m.statut)
        // Choix du téléphone ; identifiant inconnu (ou match terminé, hors de la liste) : ignoré
        assertTrue(s.choisir("LAL-BOS", 1_000))
        assertFalse(s.choisir("XXX-YYY", 1_000))
        assertFalse(s.choisir("SAS-UTA", 1_000))
        assertEquals("LAL-BOS", s.etat().choisi)
        assertEquals(Affichage.CACHE, s.etat().affichage)
    }

    @Test
    fun afficherAvecUnIdentifiantInconnuGardeLeChoixEtAppliqueLeFormat() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.choisir("LAL-BOS", 0)
        assertEquals(Affichage.DETAILS, s.afficher(Affichage.DETAILS, "XXX-YYY", 0))
        assertEquals("LAL-BOS", s.etat().choisi)
        assertEquals(Affichage.DETAILS, s.etat().affichage)
        assertEquals(Affichage.RESUME, s.afficher(Affichage.RESUME, "MIA-DEN", 0))
        assertEquals("MIA-DEN", s.etat().choisi)
        assertEquals(Affichage.CACHE, s.afficher(Affichage.CACHE, null, 0))
        assertEquals("MIA-DEN", s.etat().choisi)
    }

    @Test
    fun leMatchChoisiQuiSeTermineResteTantQuIlEstAffiche() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.DETAILS, "LAL-BOS", 0)
        val termine = tableau.map { if (it.id == "LAL-BOS") it.copy(etat = EtatMatch.TERMINE) else it }
        s.servir(30_000, tab = { Reponse.Ok(termine) })
        val e = s.etat()
        assertEquals("LAL-BOS", e.choisi)
        assertEquals("Terminé", e.matchs.first { it.id == "LAL-BOS" }.statut)
        assertFalse(e.matchs.first { it.id == "LAL-BOS" }.direct)
        assertEquals(Affichage.DETAILS, e.affichage)
        // Caché : il quitte la liste, le premier match en cours est choisi
        s.cacher(31_000)
        assertEquals(listOf("GSW-NYK", "MIA-DEN"), s.etat().matchs.map { it.id })
        assertEquals("GSW-NYK", s.etat().choisi)
    }

    @Test
    fun plusAucunMatchLeBoxScoreSeCache() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.RESUME, null, 0)
        s.servir(30_000, tab = { Reponse.Ok(listOf(aVenir)) })
        assertEquals(Affichage.CACHE, s.etat().affichage)
        assertNull(s.etat().choisi)
        assertTrue(s.etat().matchs.isEmpty())
    }

    @Test
    fun quinzeMatchsAuPlus() {
        val s = suivi()
        s.abonnement(true, 0)
        val beaucoup = (0 until 20).map { matchNba("A${'A' + it}", "B${'A' + it}", it.toLong()) }
        s.servir(0, tab = { Reponse.Ok(beaucoup) })
        assertEquals(SuiviNba.MATCHS_MAX, s.etat().matchs.size)
        assertEquals("AA-BA", s.etat().matchs.first().id)
    }

    @Test
    fun sansAccessibiliteRienNeSAffiche() {
        val s = suivi(accessibilite = false)
        s.abonnement(true, 0)
        s.servir(0)
        assertEquals(EtatNba.ERREUR_ACCESSIBILITE, s.etat().erreur)
        assertEquals(Affichage.CACHE, s.afficher(Affichage.RESUME, "LAL-BOS", 0))
        // Le choix demandé est quand même retenu
        assertEquals("LAL-BOS", s.etat().choisi)
        s.accessibilite = true
        assertNull(s.etat().erreur)
        assertEquals(Affichage.RESUME, s.afficher(Affichage.RESUME, null, 0))
    }

    @Test
    fun ongletDesDetails() {
        val s = suivi()
        s.abonnement(true, 0)
        s.servir(0)
        s.afficher(Affichage.DETAILS, "LAL-BOS", 0)
        s.servir(0)
        assertEquals(0, s.vueTv().equipe)
        s.changerEquipe(1)
        assertEquals(1, s.vueTv().equipe)
        s.changerEquipe(7)
        assertEquals(1, s.vueTv().equipe)
        // Autre match : onglet de l'équipe à l'extérieur
        s.choisir("MIA-DEN", 0)
        assertEquals(0, s.vueTv().equipe)
    }
}
