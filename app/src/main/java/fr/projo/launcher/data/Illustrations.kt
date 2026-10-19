package fr.projo.launcher.data

/**
 * Illustrations des apps connues, livrées avec Projo dans assets/illustrations :
 *  - « <id>.webp » : bannière 16:9 (640 × 360), pour les tuiles larges ;
 *  - « <id>-icone.webp » : carré opaque (512 × 512), pour les tuiles carrées ou rondes
 *    (le logo doit tenir dans le cercle central : cette image est aussi découpée en rond).
 *
 * Une app est reconnue par son nom de paquet (variantes TV, Fire TV et mobile connues), puis,
 * pour les variantes inconnues, par son nom affiché. Une image absente n'est pas une erreur :
 * l'app garde alors le visuel fabriqué depuis sa propre icône.
 */
object Illustrations {

    /** Dossier des images dans les assets de l'app. */
    const val DOSSIER = "illustrations"

    fun fichierBanniere(id: String) = "$id.webp"
    fun fichierIcone(id: String) = "$id-icone.webp"

    class Regle(
        /** Nom des fichiers (« youtube » → youtube.webp et youtube-icone.webp). */
        val id: String,
        /** Paquets connus de l'app (toutes versions). */
        val paquets: Set<String>,
        /**
         * Noms affichés acceptés, comparés au nom ENTIER une fois normalisé (minuscules, sans
         * accents, espaces ni ponctuation, « + » → « plus ») : « TF1+ » → « tf1plus ».
         */
        val libelle: Regex,
        /** Couleur de la lueur (ARGB) ; null = couleur dominante de l'image. */
        val couleur: Long? = null,
    )

    private fun regle(id: String, libelle: String, vararg paquets: String, couleur: Long? = null) =
        Regle(id, paquets.toSet(), Regex(libelle), couleur)

    /**
     * Les règles. Pour les noms, la première qui correspond l'emporte : les plus précises
     * d'abord (YouTube Kids et YouTube Music avant YouTube). Les motifs portent sur le nom
     * entier : « max » ne reconnaît pas « Maxdome » ni « Smart Max ».
     */
    val regles: List<Regle> = listOf(
        regle(
            "netflix", "netflix.*",
            "com.netflix.ninja", "com.netflix.mediaclient",
            couleur = 0xFFE50914,
        ),
        regle(
            "primevideo", "(amazon)?primevideo.*|amazonvideo",
            "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient", "com.amazon.avod",
            "com.amazon.firebat",
            couleur = 0xFF00A8E1,
        ),
        regle(
            "youtubekids", "youtubekids.*",
            "com.google.android.youtube.tvkids", "com.google.android.apps.youtube.kids",
            couleur = 0xFFFF0000,
        ),
        regle(
            "youtubemusic", "youtubemusic.*|ytmusic",
            "com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music",
            couleur = 0xFFFF0000,
        ),
        // SmartTube (lecteur YouTube sans pub) prend le logo YouTube
        regle(
            "youtube", "youtube|youtubefor(android)?tv|smarttube.*|smartyoutubetv.*",
            "com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.google.android.youtube",
            "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta",
            "org.smarttube.stable", "org.smarttube.beta",
            couleur = 0xFFFF0000,
        ),
        regle("disneyplus", "disneyplus.*|disney", "com.disney.disneyplus", couleur = 0xFF0063E5),
        regle("arte", "arte(tv|plus7)?", "tv.arte.plus7", couleur = 0xFFFA481C),
        regle("tf1plus", "tf1plus.*|mytf1.*|tf1", "fr.tf1.mytf1", couleur = 0xFF1E4BFF),
        regle("m6plus", "m6plus.*|6play.*|m6", "fr.m6.m6replay"),
        regle("francetv", "francetv.*|francetelevisions?", "fr.francetv.pluzz"),
        regle("canalplus", "mycanal.*|canalplus.*|canal", "com.canal.android.canal"),
        // Apple TV seulement (pas Apple Music)
        regle(
            "appletv", "appletv(plus)?",
            "com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv",
        ),
        regle(
            "twitch", "twitch.*",
            "tv.twitch.android.app", "tv.twitch.android.viewer",
            couleur = 0xFF9146FF,
        ),
        regle("molotov", "molotov.*", "tv.molotov.app"),
        // Plex, pas Plexamp (lecteur de musique au logo différent)
        regle("plex", "plex(mediaserver|htpc)?", "com.plexapp.android", couleur = 0xFFE5A00D),
        regle("kodi", "kodi.*", "org.xbmc.kodi", couleur = 0xFF17B2E7),
        regle("vlc", "vlc|vlcforandroid.*", "org.videolan.vlc", couleur = 0xFFFF8800),
        regle("spotify", "spotify.*", "com.spotify.tv.android", "com.spotify.music", couleur = 0xFF1ED760),
        regle("deezer", "deezer.*", "deezer.android.tv", "deezer.android.app", couleur = 0xFFA238FF),
        regle("max", "max|hbo(max|go)?", "com.wbd.stream", "com.hbo.hbonow", couleur = 0xFF002BE7),
        regle("paramountplus", "paramountplus.*|paramount", "com.cbs.ott", couleur = 0xFF0064FF),
        regle("crunchyroll", "crunchyroll.*", "com.crunchyroll.crunchyroid", couleur = 0xFFFF5E00),
        regle("plutotv", "plutotv.*|pluto", "tv.pluto.android"),
        regle("rakutentv", "rakuten(tv)?", "tv.wuaki", couleur = 0xFFBF0000),
        regle("dazn", "dazn.*", "com.dazn"),
        regle("beinsports", "beinsports.*|beinconnect|bein", couleur = 0xFF5C2D91),
        regle("lequipe", "lequipe.*", "fr.lequipe.networking", couleur = 0xFFE30613),
        regle(
            "jellyfin", "jellyfin.*",
            "org.jellyfin.androidtv", "org.jellyfin.mobile",
            couleur = 0xFF00A4DC,
        ),
        regle("stremio", "stremio.*", "com.stremio.one", couleur = 0xFF685CEE),
        regle("steamlink", "steamlink", "com.valvesoftware.steamlink", couleur = 0xFF1A9FFF),
        regle("geforcenow", "(nvidia)?geforcenow", "com.nvidia.geforcenow", couleur = 0xFF76B900),
        regle(
            "amazonmusic", "amazonmusic.*",
            "com.amazon.mp3", "com.amazon.bueller.music",
            couleur = 0xFF25D1DA,
        ),
        regle("mubi", "mubi", "com.mubi"),
        // Downloader d'AFTVnews : nom exact seulement (« downloader » est un mot courant)
        regle("downloader", "downloader(byaftvnews)?", "com.esaba.downloader", couleur = 0xFFF7931E),
        regle("tidal", "tidal.*", "com.aspiro.tidal"),
        regle("soundcloud", "soundcloud.*", "com.soundcloud.android", couleur = 0xFFFF5500),
        regle("ocs", "ocs|ocsgo"),
        regle("oqee", "oqee.*", "net.oqee.androidtv"),
        regle("tvorange", "(la)?tvdorange|orangetv|tvorange", couleur = 0xFFFF7900),
        regle("bboxtv", "btv|bboxtv"),
        regle("sfrtv", "sfrtv.*", couleur = 0xFFE2001A),
    )

    private val indexPaquets: Map<String, Regle> by lazy {
        regles.flatMap { r -> r.paquets.map { it to r } }.toMap()
    }

    private val indexIds: Map<String, Regle> by lazy { regles.associateBy { it.id } }

    fun parPaquet(paquet: String): Regle? = indexPaquets[paquet]

    fun parId(id: String): Regle? = indexIds[id]

    /** Règle dont le motif reconnaît ce nom d'app (null si aucune). */
    fun parLibelle(libelle: String): Regle? {
        val nom = DefaultFavorites.normaliser(libelle)
        if (nom.isEmpty()) return null
        return regles.firstOrNull { it.libelle.matches(nom) }
    }
}
