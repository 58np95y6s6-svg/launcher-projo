package fr.projo.launcher.ui

import android.content.Context
import fr.projo.launcher.data.GenreTmdb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** « Ma liste » de Galerie : ajout, retrait, ordre, persistance sur l'appareil, données illisibles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MaListeTest {

    private val contexte: Context get() = RuntimeEnvironment.getApplication()

    private fun titre(id: String, nom: String = "Titre $id", genre: GenreTmdb = GenreTmdb.FILM) = StudioProgramme(
        id = id, titre = nom, genre = genre, genres = listOf("Drame", "Mystère"), paquet = "com.netflix.ninja", service = "Netflix",
        image = "https://image.tmdb.org/t/p/w780/$id.jpg", affiche = "https://image.tmdb.org/t/p/w342/$id.jpg",
        resume = "Un résumé « avec » des guillemets.", sortie = "2026-10-03", note = 7.3, rang = 4,
    )

    @Before
    fun avant() {
        contexte.getSharedPreferences(MaListe.FICHIER, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun ajoutRetraitEtOrdre() {
        val l = MaListe.de(contexte)
        assertTrue(l.titres.isEmpty())
        l.ajouter(titre("movie:1"))
        l.ajouter(titre("tv:2", genre = GenreTmdb.SERIE))
        l.ajouter(titre("movie:1"))
        // Le plus récent en tête, sans doublon
        assertEquals(listOf("tv:2", "movie:1"), l.titres.map { it.id })
        assertTrue(l.contient("movie:1"))
        l.retirer("movie:1")
        assertFalse(l.contient("movie:1"))
        assertEquals(listOf("tv:2"), l.titres.map { it.id })
        // Basculer : ajoute puis retire
        assertTrue(l.basculer(titre("movie:3")))
        assertFalse(l.basculer(titre("movie:3")))
        assertEquals(listOf("tv:2"), l.titres.map { it.id })
        l.retirer("inconnu")
        assertEquals(1, l.titres.size)
    }

    /** Gardée sur l'appareil : une nouvelle instance (redémarrage) relit la même liste, champs compris. */
    @Test
    fun persistance() {
        val a = MaListe.de(contexte)
        val t = titre("tv:42", "Les Rivages de verre", GenreTmdb.SERIE)
        a.ajouter(titre("movie:7"))
        a.ajouter(t)
        val b = MaListe.de(contexte)
        assertEquals(listOf("tv:42", "movie:7"), b.titres.map { it.id })
        val lu = b.titres.first()
        // Tout sauf le rang des tendances (il change chaque semaine)
        assertEquals(t.copy(rang = null), lu)
        b.retirer("tv:42")
        assertEquals(listOf("movie:7"), MaListe.de(contexte).titres.map { it.id })
    }

    @Test
    fun encodageSansPerte() {
        val l = listOf(
            titre("movie:1"),
            titre("tv:2", genre = GenreTmdb.SERIE).copy(affiche = null, image = null, sortie = null, note = null, resume = "", genres = emptyList()),
        )
        val relu = MaListe.decoder(MaListe.encoder(l))
        assertEquals(l.map { it.copy(rang = null) }, relu)
        assertNull(relu[1].affiche)
        assertNull(relu[1].note)
    }

    /** Données abîmées : liste vide ou titres invalides ignorés, jamais d'erreur. */
    @Test
    fun donneesIllisibles() {
        assertTrue(MaListe.decoder(null).isEmpty())
        assertTrue(MaListe.decoder("").isEmpty())
        assertTrue(MaListe.decoder("pas du json").isEmpty())
        assertTrue(MaListe.decoder("{\"id\":\"x\"}").isEmpty())
        val l = MaListe.decoder("""[{"id":"movie:1","titre":"Un","paquet":"p"},{"titre":"sans id"},42,{"id":"movie:1","titre":"Doublon","paquet":"p"}]""")
        assertEquals(listOf("movie:1"), l.map { it.id })
        assertEquals(GenreTmdb.FILM, l[0].genre)
        contexte.getSharedPreferences(MaListe.FICHIER, Context.MODE_PRIVATE).edit().putString(MaListe.CLE, "{abîmé").commit()
        assertTrue(MaListe.de(contexte).titres.isEmpty())
    }

    @Test
    fun auPlusCentTitres() {
        val l = MaListe.de(contexte)
        repeat(MaListe.MAX + 5) { l.ajouter(titre("movie:$it")) }
        assertEquals(MaListe.MAX, l.titres.size)
        assertEquals("movie:${MaListe.MAX + 4}", l.titres.first().id)
    }
}
