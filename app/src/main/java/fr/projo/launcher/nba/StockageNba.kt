package fr.projo.launcher.nba

import java.io.File

/** Cache disque du design NBA (classement, équipes) : un texte par nom ; remplacé dans les tests. */
internal interface StockageNba {
    fun lire(nom: String): String?
    fun ecrire(nom: String, texte: String)
}

/** Fichiers d'un dossier de l'appli ; écriture dans un fichier temporaire puis renommage (jamais de fichier à moitié écrit). */
internal class FichiersNba(private val dossier: File) : StockageNba {

    override fun lire(nom: String): String? = try {
        File(dossier, nom).takeIf { it.isFile }?.readText()
    } catch (e: Exception) {
        null
    }

    override fun ecrire(nom: String, texte: String) {
        try {
            dossier.mkdirs()
            val provisoire = File(dossier, "$nom.tmp")
            provisoire.writeText(texte)
            val cible = File(dossier, nom)
            if (!provisoire.renameTo(cible)) {
                cible.delete()
                provisoire.renameTo(cible)
            }
        } catch (e: Exception) {
            // Disque plein ou indisponible : le cache sera refait au prochain chargement
        }
    }
}
