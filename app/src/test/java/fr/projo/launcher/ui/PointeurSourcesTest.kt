package fr.projo.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Garde-fou du pointeur (souris, tactile) : les composants activables de tv-material ne
 * réagissent qu'à la télécommande. Tout le code doit passer par leurs enveloppes
 * (SurfaceCliquable, LigneCliquable de PointeurTv.kt), qui ajoutent Modifier.cliquable.
 * Ce test lit les sources : un nouvel import direct de Surface, ListItem, Card, Button…
 * de tv-material (sauf dans PointeurTv.kt) le fait échouer, avec le fichier en cause.
 */
class PointeurSourcesTest {

    /** Composants activables de tv-material (ceux qui prennent onClick). */
    private val interdits = listOf(
        "Surface", "ListItem", "Card", "ClassicCard", "CompactCard", "WideClassicCard", "StandardCardContainer",
        "WideCardContainer", "Button", "OutlinedButton", "WideButton", "IconButton", "OutlinedIconButton",
        "Tab", "FilterChip", "AssistChip", "InputChip", "SuggestionChip", "NavigationDrawerItem",
        "Switch", "Checkbox", "RadioButton", "DenseListItem",
    )

    @Test
    fun aucunComposantTvSansPointeur() {
        val racine = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: return fail("sources introuvables depuis ${File(".").absolutePath}")
        val fichiers = racine.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("trop peu de sources lues (${fichiers.size})", fichiers.size > 50)
        val motif = Regex("""^import androidx\.tv\.material3\.(\w+)\s*$""", RegexOption.MULTILINE)
        val fautes = fichiers.filter { it.name != "PointeurTv.kt" }.flatMap { f ->
            motif.findAll(f.readText()).map { it.groupValues[1] }.filter { it in interdits }.map { "${f.name} : $it" }
        }
        if (fautes.isNotEmpty()) {
            fail("Composants tv-material importés directement (pas de souris ni de tactile) : $fautes. " +
                "Utiliser SurfaceCliquable / LigneCliquable (PointeurTv.kt) ou Modifier.cliquable (Pointeur.kt).")
        }
    }
}
