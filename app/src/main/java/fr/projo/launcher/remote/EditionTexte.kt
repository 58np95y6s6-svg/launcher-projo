package fr.projo.launcher.remote

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * Édition du champ actif par le clavier iBiProjo, séparée de l'IME pour être testée avec un
 * InputConnection factice.
 */
object EditionTexte {

    private const val LECTURE_MAX = 10_000

    /** Texte actuel du champ (ExtractedText si l'appli le fournit, sinon avant + sélection + après). */
    fun lire(ic: InputConnection): String? {
        ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = LECTURE_MAX }, 0)?.text?.let { return it.toString() }
        val avant = ic.getTextBeforeCursor(LECTURE_MAX, 0) ?: return null
        val selection = ic.getSelectedText(0) ?: ""
        val apres = ic.getTextAfterCursor(LECTURE_MAX, 0) ?: ""
        return "$avant$selection$apres"
    }

    /**
     * Remplace tout le contenu du champ par [nouveau] et place le curseur en [curseur]
     * (fin du texte par défaut). Seule la partie qui change est réécrite (préfixe et suffixe
     * communs gardés) : les applis qui cherchent à chaque lettre voient une seule modification.
     */
    fun remplacer(ic: InputConnection, nouveau: String, curseur: Int?) {
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            val extrait = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = LECTURE_MAX }, 0)
            val actuel: String
            val debut: Int // position du début du texte dans le champ (texte partiel)
            if (extrait?.text != null) {
                actuel = extrait.text.toString()
                debut = maxOf(0, extrait.startOffset)
            } else {
                // Sans ExtractedText : on efface tout autour du curseur puis on écrit
                val avant = ic.getTextBeforeCursor(LECTURE_MAX, 0)?.length ?: 0
                val apres = ic.getTextAfterCursor(LECTURE_MAX, 0)?.length ?: 0
                ic.commitText("", 1) // retire la sélection éventuelle
                ic.deleteSurroundingText(avant, apres)
                ic.commitText(nouveau, 1)
                placerCurseur(ic, 0, nouveau, curseur)
                return
            }
            var prefixe = 0
            val maxCommun = minOf(actuel.length, nouveau.length)
            while (prefixe < maxCommun && actuel[prefixe] == nouveau[prefixe]) prefixe++
            var suffixe = 0
            while (suffixe < maxCommun - prefixe &&
                actuel[actuel.length - 1 - suffixe] == nouveau[nouveau.length - 1 - suffixe]
            ) suffixe++
            if (actuel != nouveau) {
                ic.setSelection(debut + prefixe, debut + actuel.length - suffixe)
                ic.commitText(nouveau.substring(prefixe, nouveau.length - suffixe), 1)
            }
            placerCurseur(ic, debut, nouveau, curseur)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun placerCurseur(ic: InputConnection, debut: Int, texte: String, curseur: Int?) {
        val c = (curseur ?: texte.length).coerceIn(0, texte.length)
        ic.setSelection(debut + c, debut + c)
    }

    /** Valide le champ : action de l'éditeur (Rechercher, OK…) ou touche Entrée. */
    fun valider(ic: InputConnection, info: EditorInfo?) {
        val options = info?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val sansAction = options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (!sansAction && ic.performEditorAction(action)) return
        touche(ic, KeyEvent.KEYCODE_ENTER)
    }

    /** Appui + relâchement d'une touche, envoyés au champ. */
    fun touche(ic: InputConnection, code: Int) {
        val t = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
    }

    /** Type de champ pour `focus_text`, d'après EditorInfo ; null si ce n'est pas un champ texte. */
    fun typeChamp(info: EditorInfo?): String? {
        val type = info?.inputType ?: return null
        val classe = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (classe) {
            InputType.TYPE_NULL -> null
            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) Protocole.TypeChamp.MOT_DE_PASSE else Protocole.TypeChamp.NOMBRE
            InputType.TYPE_CLASS_PHONE -> Protocole.TypeChamp.NOMBRE
            InputType.TYPE_CLASS_DATETIME -> Protocole.TypeChamp.NOMBRE
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> Protocole.TypeChamp.MOT_DE_PASSE
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> Protocole.TypeChamp.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> Protocole.TypeChamp.URL
                else ->
                    if (info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH) Protocole.TypeChamp.RECHERCHE
                    else Protocole.TypeChamp.TEXTE
            }
            else -> Protocole.TypeChamp.TEXTE
        }
    }

    /** Action de l'éditeur pour `focus_text`. */
    fun actionChamp(info: EditorInfo?): String = when ((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_GO -> Protocole.ActionChamp.GO
        EditorInfo.IME_ACTION_SEARCH -> Protocole.ActionChamp.SEARCH
        EditorInfo.IME_ACTION_SEND -> Protocole.ActionChamp.SEND
        EditorInfo.IME_ACTION_NEXT -> Protocole.ActionChamp.NEXT
        else -> Protocole.ActionChamp.DONE
    }
}
