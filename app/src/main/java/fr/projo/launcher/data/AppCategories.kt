package fr.projo.launcher.data

import android.content.pm.ApplicationInfo

/** Rubriques de la bibliothèque « Toutes les apps », dans l'ordre d'affichage. */
enum class Rubrique(val titre: String) {
    STREAMING("Vidéo et streaming"),
    TV("TV et direct"),
    MUSIQUE("Musique et audio"),
    JEUX("Jeux"),
    STORE("Stores et téléchargement"),
    OUTILS("Internet et outils"),
    SYSTEME("Système et réglages"),
    AUTRES("Autres"),
}

/**
 * Classe une app par sujet : d'abord par nom (apps connues), puis par la catégorie
 * déclarée par l'app, puis « Système » pour les apps du fabricant.
 */
object AppCategories {

    private val regles: List<Pair<Rubrique, Regex>> = listOf(
        Rubrique.TV to Regex(
            "molotov|francetv|france\\.tv|tf1|mytf1|\\bm6\\b|6play|\\barte\\b|bfm|rmc|pluto|livetv|iptv|tivimate|" +
                "smarters|channels|\\btnt\\b|awsource|hdmi|tvinput|tvcenter|tv ?en ?direct",
        ),
        Rubrique.STREAMING to Regex(
            "netflix|primevideo|amazon\\.avod|prime video|disney|canal|youtube|smarttube|twitch|plex|kodi|vlc|" +
                "mxplayer|mx player|crunchyroll|paramount|hbo|\\bmax\\b|appletv|apple tv|rakuten|dazn|bein|\\bocs\\b|" +
                "stremio|jellyfin|emby|infuse|video|vidéo|player|cinema|ciné|movie|film|anime|\\badn\\b",
        ),
        Rubrique.MUSIQUE to Regex(
            "spotify|deezer|music|musique|radio|podcast|soundcloud|tidal|qobuz|audible|shazam|sonos|airplay|audio",
        ),
        Rubrique.JEUX to Regex("game|jeux|jeu |gaming|xbox|steam|geforce|retroarch|emulat"),
        Rubrique.STORE to Regex(
            "vending|play store|aptoide|downloader|aurora|appstore|app store|apkpure|fdroid|f-droid|apkmirror|" +
                "market|galaxystore|uptodown",
        ),
        Rubrique.OUTILS to Regex(
            "chrome|browser|navigateur|firefox|tvbro|puffin|opera|edge|duckduckgo|file|fichier|explorer|" +
                "commander|cleaner|nettoy|speedtest|vpn|remote|télécommande|cast|miracast|screen|mirror|" +
                "photo|gallery|galerie|image|calcul|calendar|clock|horloge|weather|météo",
        ),
        Rubrique.SYSTEME to Regex(
            "settings|réglages|reglages|paramètres|parametres|setup|update|\\bota\\b|system|système|launcher|" +
                "diagnostic|factory|bluetooth|wifi|keystone|focus|projector|projecteur",
        ),
    )

    fun rubrique(app: AppInfo): Rubrique {
        if (app.packageName == AppInfo.HDMI_ID) return Rubrique.TV
        val texte = (app.label + " " + app.packageName).lowercase()
        regles.firstOrNull { (_, r) -> r.containsMatchIn(texte) }?.let { return it.first }
        return when (app.categorieSysteme) {
            ApplicationInfo.CATEGORY_VIDEO -> Rubrique.STREAMING
            ApplicationInfo.CATEGORY_AUDIO -> Rubrique.MUSIQUE
            ApplicationInfo.CATEGORY_GAME -> Rubrique.JEUX
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_MAPS -> Rubrique.OUTILS
            else -> if (app.systeme) Rubrique.SYSTEME else Rubrique.AUTRES
        }
    }

    /** Apps regroupées par rubrique (rubriques vides omises), ordre alphabétique dans chacune. */
    fun grouper(apps: List<AppInfo>): List<Pair<Rubrique, List<AppInfo>>> =
        apps.groupBy { rubrique(it) }
            .toSortedMap(compareBy { it.ordinal })
            .map { (r, l) -> r to l.sortedBy { it.label.lowercase() } }
}
