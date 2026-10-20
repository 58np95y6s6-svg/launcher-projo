package fr.projo.launcher.remote

import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Champ texte factice : un InputConnection complet (celui d'Android, sur un Editable) qui
 * note les actions de l'éditeur et les touches reçues. [avecExtrait] : l'appli fournit
 * ExtractedText (la plupart des EditText) ou non (certains champs web / maison).
 */
class ChampFactice(texte: String, curseur: Int = texte.length, private val avecExtrait: Boolean = true) :
    BaseInputConnection(View(RuntimeEnvironment.getApplication()), true) {

    private val contenu = SpannableStringBuilder(texte).also { Selection.setSelection(it, curseur) }
    val actions = mutableListOf<Int>()
    val touches = mutableListOf<KeyEvent>()
    /** Modifications groupées de premier niveau (BaseInputConnection en ouvre aussi à l'intérieur). */
    var lots = 0
    private var profondeur = 0

    val texte: String get() = contenu.toString()
    val selection: Pair<Int, Int> get() = Selection.getSelectionStart(contenu) to Selection.getSelectionEnd(contenu)

    override fun getEditable() = contenu

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        if (!avecExtrait) return null
        return ExtractedText().apply {
            text = contenu.toString()
            startOffset = 0
            selectionStart = Selection.getSelectionStart(contenu)
            selectionEnd = Selection.getSelectionEnd(contenu)
        }
    }

    override fun beginBatchEdit(): Boolean { if (profondeur++ == 0) lots++; return true }
    override fun endBatchEdit(): Boolean { profondeur--; return profondeur > 0 }
    override fun performEditorAction(actionCode: Int): Boolean { actions += actionCode; return true }
    override fun sendKeyEvent(event: KeyEvent): Boolean { touches += event; return true }
}

/** Clavier iBiProjo : remplacement du texte, validation, type de champ. */
@RunWith(RobolectricTestRunner::class)
class ClavierTest {

    @Test
    fun remplacementCompletCurseurALaFin() {
        val champ = ChampFactice("bonjour")
        EditionTexte.remplacer(champ, "bonsoir à tous", null)
        assertEquals("bonsoir à tous", champ.texte)
        assertEquals(14 to 14, champ.selection)
        assertEquals(1, champ.lots) // une seule modification groupée
    }

    @Test
    fun curseurDemande() {
        val champ = ChampFactice("abc")
        EditionTexte.remplacer(champ, "abcdef", 2)
        assertEquals("abcdef", champ.texte)
        assertEquals(2 to 2, champ.selection)
        // Curseur hors du texte : ramené à la fin
        EditionTexte.remplacer(champ, "ab", 99)
        assertEquals("ab", champ.texte)
        assertEquals(2 to 2, champ.selection)
    }

    @Test
    fun remplacementAuMilieuEtEffacement() {
        val champ = ChampFactice("le chat dort", curseur = 3)
        EditionTexte.remplacer(champ, "le chien dort", null)
        assertEquals("le chien dort", champ.texte)
        EditionTexte.remplacer(champ, "", null)
        assertEquals("", champ.texte)
        assertEquals(0 to 0, champ.selection)
    }

    @Test
    fun texteIdentiqueCurseurSeulement() {
        val champ = ChampFactice("netflix", curseur = 0)
        EditionTexte.remplacer(champ, "netflix", 4)
        assertEquals("netflix", champ.texte)
        assertEquals(4 to 4, champ.selection)
    }

    /** Champ sans ExtractedText : on efface autour du curseur puis on écrit. */
    @Test
    fun champSansExtractedText() {
        val champ = ChampFactice("ancien texte", curseur = 6, avecExtrait = false)
        EditionTexte.remplacer(champ, "nouveau", null)
        assertEquals("nouveau", champ.texte)
        assertEquals(7 to 7, champ.selection)
        assertEquals("nouveau", EditionTexte.lire(champ))
    }

    @Test
    fun lectureDuChamp() {
        assertEquals("bonjour", EditionTexte.lire(ChampFactice("bonjour", curseur = 3)))
        assertEquals("bonjour", EditionTexte.lire(ChampFactice("bonjour", curseur = 3, avecExtrait = false)))
    }

    @Test
    fun validerAvecLActionDeLEditeur() {
        val champ = ChampFactice("chat")
        EditionTexte.valider(champ, EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH })
        assertEquals(listOf(EditorInfo.IME_ACTION_SEARCH), champ.actions)
        assertTrue(champ.touches.isEmpty())
    }

    @Test
    fun validerSansActionEnvoieEntree() {
        val champ = ChampFactice("chat")
        EditionTexte.valider(champ, EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED })
        EditionTexte.valider(champ, EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_ENTER_ACTION })
        assertTrue(champ.actions.isEmpty())
        assertEquals(4, champ.touches.size)
        assertTrue(champ.touches.all { it.keyCode == KeyEvent.KEYCODE_ENTER })
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), champ.touches.take(2).map { it.action })
    }

    @Test
    fun touchesDirectionnelles() {
        val champ = ChampFactice("abc")
        EditionTexte.touche(champ, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), champ.touches.map { it.action })
        assertTrue(champ.touches.all { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT })
    }

    @Test
    fun typesDeChamp() {
        fun type(inputType: Int, options: Int = 0) = EditionTexte.typeChamp(EditorInfo().apply { this.inputType = inputType; imeOptions = options })
        val texte = InputType.TYPE_CLASS_TEXT
        assertEquals("texte", type(texte))
        assertEquals("motdepasse", type(texte or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals("motdepasse", type(texte or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals("motdepasse", type(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertEquals("email", type(texte or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals("url", type(texte or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals("nombre", type(InputType.TYPE_CLASS_NUMBER))
        assertEquals("nombre", type(InputType.TYPE_CLASS_PHONE))
        assertEquals("recherche", type(texte, EditorInfo.IME_ACTION_SEARCH))
        assertNull(type(InputType.TYPE_NULL))
        assertNull(EditionTexte.typeChamp(null))
    }

    @Test
    fun actionsDeChamp() {
        fun action(o: Int) = EditionTexte.actionChamp(EditorInfo().apply { imeOptions = o })
        assertEquals("go", action(EditorInfo.IME_ACTION_GO))
        assertEquals("search", action(EditorInfo.IME_ACTION_SEARCH))
        assertEquals("send", action(EditorInfo.IME_ACTION_SEND))
        assertEquals("next", action(EditorInfo.IME_ACTION_NEXT))
        assertEquals("done", action(EditorInfo.IME_ACTION_DONE))
        assertEquals("done", action(EditorInfo.IME_ACTION_UNSPECIFIED))
    }
}
