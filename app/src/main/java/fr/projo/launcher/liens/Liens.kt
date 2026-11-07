package fr.projo.launcher.liens

/*
 * « Envoyer sur la TV » (docs/telecommande.md, « Envoyer sur la TV : liens ») : partie sans
 * Android, testée sur la JVM. Vérification d'un lien reçu (téléphone ou raccourci iOS), titre
 * montré sur la TV, genre du lien (YouTube, Netflix…, vidéo, page web) et applis à essayer dans
 * l'ordre. L'ouverture elle-même (Android) : OuvertureLiens ; le déroulé (VPN, attente) : EnvoiLiens.
 */

/** Lien vérifié : http ou https, avec un hôte, 2 048 caractères au plus. */
data class LienTv(
    /** URL à ouvrir : celle reçue, schéma et hôte en minuscules. */
    val url: String,
    /** Hôte en minuscules (« www.arte.tv »). */
    val hote: String,
    /** Chemin, sans requête ni fragment (« /fr/videos/x.mp4 »). */
    val chemin: String,
    /** Titre montré sur la TV à la place de l'hôte (déjà nettoyé), ou null. */
    val titre: String? = null,
) {
    /** Hôte affiché, sans « www. » (« arte.tv »). */
    val hoteAffiche: String get() = hote.removePrefix("www.")

    /** Ce que la TV montre : le titre, sinon l'hôte. */
    val affichage: String get() = titre ?: hoteAffiche
}

/** Genre d'un lien : il décide des applis essayées, dans l'ordre (voir [Liens.candidats]). */
enum class GenreLien { YOUTUBE, NETFLIX, PRIME_VIDEO, DISNEY, VIDEO, PAGE }

object Liens {
    /** Longueur maximale d'une URL acceptée. */
    const val URL_MAX = 2_048

    /** Longueur maximale du titre montré sur la TV. */
    const val TITRE_MAX = 200

    /** Attente d'un VPN (`vpnDabord`, « Allumer le VPN et ouvrir ») : au plus 60 s. */
    const val ATTENTE_VPN_MS = 60_000L

    /** Bandeau « … envoyé par … — Ouverture dans … » avant l'ouverture, si iBiTV est à l'écran. */
    const val BANDEAU_MS = 1_000L

    /** Choix « automatique » du réglage `liens_pages`. */
    const val PAGES_AUTO = "auto"

    // ------------------------------------------------------------------ applis connues

    /** YouTube : iBiTube, SmartTube, puis YouTube pour TV (Android TV, Fire TV). */
    val YOUTUBE = listOf("com.ibi.ibitube", "com.teamsmart.videomanager.tv", "com.google.android.youtube.tv", "com.amazon.firetv.youtube")
    val NETFLIX = listOf("com.netflix.ninja")
    val PRIME_VIDEO = listOf("com.amazon.firebat", "com.amazon.avod.thirdpartyclient", "com.amazon.avod")
    val DISNEY = listOf("com.disney.disneyplus")

    /** Vidéo directe : VLC, Kodi, MX Player (puis toute appli qui ouvre les vidéos, type « video » générique). */
    val LECTEURS_VIDEO = listOf("org.videolan.vlc", "org.xbmc.kodi", "com.mxtech.videoplayer.ad")

    /** Paquet de VYPN (aussi reconnu à son nom, voir [ordreAuto]). */
    const val VYPN = "net.vypn.app"

    /** Pages web, choix « auto » : VYPN, Silk, TV Bro, Firefox, Chrome, puis les autres. */
    val NAVIGATEURS = listOf(VYPN, "com.amazon.cloud9", "com.phlox.tvwebbrowser", "org.mozilla.tv.firefox", "org.mozilla.firefox", "com.android.chrome")

    /** Applis VPN que la TV sait ouvrir (« Allumer le VPN »), dans l'ordre de préférence : paquet → nom. */
    val APPLIS_VPN: List<Pair<String, String>> = listOf(
        VYPN to "VYPN",
        "ch.protonvpn.android" to "Proton VPN",
        "com.wireguard.android" to "WireGuard",
        "com.cloudflare.onedotonedotonedotone" to "Cloudflare WARP",
        "com.nordvpn.android" to "NordVPN",
        "com.surfshark.vpnclient.android" to "Surfshark",
        "com.expressvpn.vpn" to "ExpressVPN",
        "com.windscribe.vpn" to "Windscribe",
        "net.mullvad.mullvadvpn" to "Mullvad",
    )

    /** Extensions d'une vidéo directe (chemin de l'URL). */
    private val EXTENSIONS_VIDEO = listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".m3u8", ".mpd")

    private val HOTES_YOUTUBE = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    /** amazon.fr, www.amazon.co.uk… */
    private val AMAZON = Regex("(www\\.)?amazon(\\.[a-z]{2,3}){1,2}")

    // ------------------------------------------------------------------ vérification

    /**
     * Lien reçu → [LienTv], ou null s'il est refusé : pas une chaîne, vide, plus de 2 048
     * caractères, espace ou caractère de contrôle, schéma autre que http / https (jamais
     * `file:`, `intent:`, `javascript:`, `content:`…), pas d'hôte, port invalide. Les espaces
     * autour sont retirés ; le schéma et l'hôte passent en minuscules (les applis Android
     * reconnaissent leurs liens en minuscules). [titre] : voir [nettoyerTitre].
     */
    fun lien(url: Any?, titre: Any? = null): LienTv? {
        if (url !is String) return null
        val u = url.trim()
        if (u.isEmpty() || u.length > URL_MAX) return null
        if (u.any { it <= ' ' || it == '\u007f' || it in '\u0080'..'\u009f' }) return null
        val sep = u.indexOf("://")
        if (sep <= 0) return null
        val schema = u.substring(0, sep).lowercase()
        if (schema != "http" && schema != "https") return null
        val reste = u.substring(sep + 3)
        val finAutorite = reste.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) reste.length else it }
        val autorite = reste.substring(0, finAutorite)
        val suite = reste.substring(finAutorite)
        // Identifiants éventuels (« user@hôte ») : gardés tels quels, l'hôte est après le dernier @
        val arobase = autorite.lastIndexOf('@')
        val identifiants = if (arobase >= 0) autorite.substring(0, arobase + 1) else ""
        val (hoteBrut, port) = separerPort(autorite.substring(arobase + 1)) ?: return null
        val hote = hoteBrut.lowercase()
        if (!hoteValide(hote)) return null
        val chemin = suite.substringBefore('#').substringBefore('?')
        val normalisee = "$schema://$identifiants$hote${if (port != null) ":$port" else ""}$suite"
        return LienTv(normalisee, hote.trimEnd('.'), chemin, nettoyerTitre(titre))
    }

    /** « hôte:port » → (hôte, port ou null) ; null si le port est invalide. IPv6 entre crochets. */
    private fun separerPort(hp: String): Pair<String, String?>? {
        val (hote, port) = if (hp.startsWith("[")) {
            val fin = hp.indexOf(']')
            if (fin < 0) return null
            val apres = hp.substring(fin + 1)
            if (apres.isNotEmpty() && !apres.startsWith(":")) return null
            hp.substring(0, fin + 1) to apres.removePrefix(":").takeIf { apres.isNotEmpty() }
        } else {
            val dp = hp.lastIndexOf(':')
            if (dp < 0) hp to null else hp.substring(0, dp) to hp.substring(dp + 1)
        }
        if (port == null || port.isEmpty()) return hote to null
        if (port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65_535) return null
        return hote to port
    }

    private fun hoteValide(h: String): Boolean {
        if (h.isEmpty() || h.length > 253) return false
        if (h.startsWith("[")) {
            return h.length > 2 && h.endsWith("]") && h.substring(1, h.length - 1).all { it.isDigit() || it in 'a'..'f' || it == ':' || it == '.' }
        }
        val etiquettes = h.trimEnd('.').split('.')
        return etiquettes.all { e -> e.isNotEmpty() && e.length <= 63 && e.all { it.isLetterOrDigit() || it == '-' || it == '_' } }
    }

    /**
     * Titre montré sur la TV : chaîne seulement, caractères de contrôle remplacés par des espaces,
     * espaces regroupés, 200 caractères au plus ; null s'il ne reste rien.
     */
    fun nettoyerTitre(titre: Any?): String? {
        if (titre !is String) return null
        val propre = titre.map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim()
        var t = propre.take(TITRE_MAX)
        // Pas de moitié de caractère (émoji…) en fin de titre coupé
        if (t.isNotEmpty() && t.last().isHighSurrogate()) t = t.dropLast(1)
        return t.trim().ifEmpty { null }
    }

    // ------------------------------------------------------------------ genre et applis

    fun genre(l: LienTv): GenreLien {
        val h = l.hote
        fun domaine(d: String) = h == d || h.endsWith(".$d")
        val chemin = l.chemin.lowercase()
        return when {
            h in HOTES_YOUTUBE -> GenreLien.YOUTUBE
            domaine("netflix.com") -> GenreLien.NETFLIX
            domaine("primevideo.com") -> GenreLien.PRIME_VIDEO
            AMAZON.matches(h) && (chemin.startsWith("/gp/video/") || chemin == "/gp/video") -> GenreLien.PRIME_VIDEO
            domaine("disneyplus.com") -> GenreLien.DISNEY
            EXTENSIONS_VIDEO.any { chemin.endsWith(it) } -> GenreLien.VIDEO
            else -> GenreLien.PAGE
        }
    }

    /** Applis propres au genre, dans l'ordre (la page web vient ensuite, en repli ; voir OuvertureLiens). */
    fun candidats(g: GenreLien): List<String> = when (g) {
        GenreLien.YOUTUBE -> YOUTUBE
        GenreLien.NETFLIX -> NETFLIX
        GenreLien.PRIME_VIDEO -> PRIME_VIDEO
        GenreLien.DISNEY -> DISNEY
        GenreLien.VIDEO -> LECTEURS_VIDEO
        GenreLien.PAGE -> emptyList()
    }

    /**
     * Applis des pages web, ordre « auto » : VYPN (son paquet, ou une appli nommée « VYPN »),
     * Silk, TV Bro, Firefox, Chrome, puis les autres par ordre alphabétique ; jamais iBiTV ([soi]).
     * [installees] : applis qui ouvrent une page https (paquet → nom).
     */
    fun ordreAuto(installees: Map<String, String>, soi: String): List<String> {
        val vypn = installees.filter { (p, nom) -> p == VYPN || nom.trim().equals("VYPN", ignoreCase = true) }.keys.sortedBy { if (it == VYPN) 0 else 1 }
        val connus = NAVIGATEURS.filter { it in installees }
        val autres = installees.entries.sortedBy { it.value.lowercase() }.map { it.key }
        return (vypn + connus + autres).distinct().filter { it != soi }
    }

    /** Applis des pages web dans l'ordre d'essai : le choix [pages] (s'il est installé) puis l'ordre « auto ». */
    fun ordrePages(installees: Map<String, String>, pages: String, soi: String): List<String> {
        val auto = ordreAuto(installees, soi)
        return if (pages != PAGES_AUTO && pages in auto) listOf(pages) + (auto - pages) else auto
    }

    /** Appli VPN connue à ouvrir (« Allumer le VPN ») : la première installée de [APPLIS_VPN]. */
    fun appliVpn(installee: (String) -> Boolean): Pair<String, String>? = APPLIS_VPN.firstOrNull { installee(it.first) }

    // ------------------------------------------------------------------ textes

    /** Expéditeur d'un lien du raccourci iOS. */
    const val PAR_IPHONE = "l'iPhone"

    /**
     * « envoyé par … » : nom du téléphone appairé, avec un article quand il commence par un nom
     * commun (« Téléphone de Marie » → « le téléphone de Marie », « iPhone de Paul » →
     * « l'iPhone de Paul ») ; sinon tel quel.
     */
    fun expediteur(nom: String): String {
        val n = nom.trim().ifEmpty { return "un téléphone" }
        val bas = n.lowercase()
        return when {
            bas.startsWith("téléphone ") || bas == "téléphone" -> "le " + n.replaceFirstChar { it.lowercase() }
            bas.startsWith("tablette ") || bas == "tablette" -> "la " + n.replaceFirstChar { it.lowercase() }
            bas.startsWith("iphone") || bas.startsWith("ipad") -> "l'$n"
            else -> n
        }
    }
}
