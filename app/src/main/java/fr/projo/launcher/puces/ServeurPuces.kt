package fr.projo.launcher.puces

import android.util.Log
import fr.projo.launcher.liens.LienTv
import fr.projo.launcher.liens.Liens
import fr.projo.launcher.liens.ResultatLien
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Route du raccourci iOS « Envoyer sur la TV » (POST / GET /envoyer?c=<code>), avec son propre
 * code. [envoyer] reçoit un lien déjà vérifié et attend le résultat (null : pas de réponse à temps).
 */
class RouteRaccourci(
    /** Réglage `liens_raccourci`. */
    val actif: () -> Boolean,
    /** Code attendu (vide : tout est refusé). */
    val code: () -> String,
    /** Nom de la TV, dans la réponse « Envoyé sur … ». */
    val nomTv: () -> String,
    val envoyer: (LienTv) -> ResultatLien?,
    /** Types d'événements seulement (jamais l'URL, le titre ni le code). */
    val journal: (String) -> Unit = {},
)

/**
 * Petit serveur HTTP de la TV, sur le Wi-Fi de la maison, pour les puces NFC et le raccourci
 * iOS « Envoyer sur la TV ». Démarré dès qu'une des deux fonctions est activée.
 *
 *  - Puces NFC : GET /o/<app>?c=<code>. Le code (secret, dans Réglages → Puces NFC) est
 *    obligatoire ; seules les apps installées s'ouvrent (aucune autre action possible). La page
 *    rendue s'affiche sur le téléphone : « Netflix s'ouvre sur la TV ✓ ». HEAD n'ouvre jamais
 *    rien (aperçus de liens).
 *  - Raccourci iOS : POST /envoyer?c=<code> (corps JSON {url, titre?} ou l'URL seule) ou
 *    GET /envoyer?c=<code>&url=<URL encodée>, avec le code du raccourci ; réponse en texte, que
 *    le raccourci montre (docs/telecommande.md, « Raccourci iOS »).
 *  - Toute autre adresse : 404, rien ne s'ouvre (favicon…).
 *
 * Chaque route a son code, comparé en temps constant ; au-delà de 10 codes faux en une minute,
 * tout est refusé jusqu'à la fin de la minute. Le journal ne contient jamais le code ni l'URL.
 */
class ServeurPuces(
    private val port: Int,
    /** Code attendu (vide : tout est refusé). */
    private val code: () -> String,
    /** Nom de l'adresse (« netflix », un nom de paquet…) → paquet installé, ou null. */
    private val resoudre: (String) -> String?,
    /** Paquet → nom de l'app affiché sur le téléphone. */
    private val libelle: (String) -> String,
    /** Ouvre l'app [paquet] sur la TV. */
    private val ouvrir: (String) -> Unit,
    private val horloge: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Puces NFC activées (sinon /o/… est une adresse inconnue). */
    private val pucesActives: () -> Boolean = { true },
    /** Raccourci iOS ; null : pas de route /envoyer. */
    private val raccourci: RouteRaccourci? = null,
) {

    /** Réponse : page HTML (puces NFC), ou [texte] brut (raccourci iOS : [message] seul). */
    class Reponse(
        val statut: Int,
        val titre: String,
        val message: String,
        val lien: String? = null,
        val ok: Boolean = false,
        val texte: Boolean = false,
    )

    @Volatile
    private var socket: ServerSocket? = null

    /** Arrêté pour de bon : un démarrage encore en route n'ouvre plus le port. */
    @Volatile
    private var arrete = false

    /** Port réellement écouté (utile quand [port] vaut 0, dans les tests). -1 : arrêté. */
    val portEcoute: Int get() = socket?.localPort ?: -1

    // Lancement en double (aperçu puis ouverture, deux lectures de la puce) : une seule fois
    private var dernierPaquet: String? = null
    private var dernierLancement = Long.MIN_VALUE / 2

    // Codes faux : au-delà de 10 en une minute, tout est refusé jusqu'à la fin de la minute
    private var debutEchecs = Long.MIN_VALUE / 2
    private var echecs = 0

    // Idem pour le code du raccourci (compté à part)
    private var debutEchecsRaccourci = Long.MIN_VALUE / 2
    private var echecsRaccourci = 0

    /** Démarre l'écoute (false : port pris, ou déjà arrêté). Sans effet si déjà démarré. Un serveur arrêté ne redémarre pas. */
    @Synchronized
    fun demarrer(): Boolean {
        if (arrete) return false
        if (socket != null) return true
        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port), 8)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Puces NFC : écoute impossible sur le port $port (${e.javaClass.simpleName})")
            return false
        }
        socket = s
        val fil = Thread({ boucle(s) }, "puces-nfc")
        fil.isDaemon = true
        fil.start()
        Log.i(TAG, "Puces NFC : à l'écoute sur le port ${s.localPort}")
        return true
    }

    @Synchronized
    fun arreter() {
        arrete = true
        val s = socket ?: return
        socket = null
        try { s.close() } catch (e: IOException) { /* déjà fermé */ }
        Log.i(TAG, "Puces NFC : écoute arrêtée")
    }

    private fun boucle(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try {
                s.accept()
            } catch (e: IOException) {
                break
            }
            try {
                client.use { servir(it) }
            } catch (e: Exception) {
                // Téléphone parti en cours de route : rien à faire
            }
        }
    }

    private fun servir(client: Socket) {
        client.soTimeout = 3_000
        val entree = BufferedInputStream(client.getInputStream())
        val premiere = lireLigne(entree) ?: return
        // En-têtes lus jusqu'à la ligne vide, au plus 64 (seuls ceux du corps servent)
        val entetes = HashMap<String, String>()
        for (i in 0 until 64) {
            val l = lireLigne(entree) ?: break
            if (l.isEmpty()) break
            val dp = l.indexOf(':')
            if (dp > 0) entetes[l.substring(0, dp).trim().lowercase()] = l.substring(dp + 1).trim()
        }
        val morceaux = premiere.split(' ')
        val methode = morceaux.getOrNull(0) ?: ""
        val cible = morceaux.getOrNull(1) ?: ""
        // Corps : seulement pour le raccourci (POST /envoyer)
        val corpsRecu = if (methode == "POST" && cible.substringBefore('?').trimEnd('/') == "/envoyer") {
            lireCorps(entree, entetes, client)
        } else ""
        val r = repondre(methode, cible, corpsRecu)
        val contenu = (if (r.texte) r.message else page(r)).toByteArray(Charsets.UTF_8)
        val corps = if (methode == "HEAD") ByteArray(0) else contenu
        val longueur = contenu.size
        val entete = buildString {
            append("HTTP/1.1 ").append(r.statut).append(' ').append(raison(r.statut)).append("\r\n")
            append(if (r.texte) "Content-Type: text/plain; charset=utf-8\r\n" else "Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(longueur).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }
        val sortie = client.getOutputStream()
        sortie.write(entete.toByteArray(Charsets.ISO_8859_1))
        sortie.write(corps)
        sortie.flush()
    }

    /**
     * Décide de la réponse (et ouvre l'app ou le lien si tout est bon). [corps] : corps d'un POST
     * (null : trop long ou illisible).
     */
    fun repondre(methode: String, cible: String, corps: String? = ""): Reponse {
        val chemin = cible.substringBefore('?')
        if (chemin.trimEnd('/') == "/envoyer") return envoyer(methode, cible.substringAfter('?', ""), corps)
        if (methode != "GET" && methode != "HEAD") return Reponse(405, "Non", "Seule l'ouverture d'un lien est possible.")
        if (!chemin.startsWith("/o/")) return autreAdresse()
        // Serveur démarré pour le raccourci seulement : une ancienne puce le dit sur le téléphone
        if (!pucesActives()) return Reponse(404, "Puces NFC désactivées", "Active-les sur la TV : Réglages → Puces NFC.")
        return puce(methode, cible)
    }

    /** Puce NFC : GET /o/<app>?c=<code>. */
    @Synchronized
    private fun puce(methode: String, cible: String): Reponse {
        val chemin = cible.substringBefore('?')
        val requete = cible.substringAfter('?', "")
        val nom = decoder(chemin.removePrefix("/o/")).trim('/').lowercase()
        if (nom.isEmpty() || nom.length > 120) return introuvable()
        val maintenant = horloge()
        if (maintenant - debutEchecs > 60_000) { debutEchecs = maintenant; echecs = 0 }
        if (echecs >= ECHECS_MAX) return Reponse(429, "Trop d'essais", "Réessaie dans une minute.")
        val recu = requete.split('&').firstOrNull { it.startsWith("c=") }?.let { decoder(it.removePrefix("c=")) } ?: ""
        if (!codeValide(recu)) {
            echecs++
            Log.w(TAG, "Puces NFC : code refusé")
            return Reponse(
                403, "Puce refusée",
                "Ce lien n'est plus valable : un nouveau code a été créé sur la TV (Réglages → Puces NFC). Réécris la puce avec le nouveau lien.",
            )
        }
        val paquet = resoudre(nom)
            ?: return Reponse(404, "App introuvable", "« $nom » n'est pas installée sur la TV.")
        val app = libelle(paquet)
        if (methode == "HEAD") return Reponse(200, app, "", ok = true)
        val doublon = paquet == dernierPaquet && maintenant - dernierLancement < DOUBLON_MS
        if (!doublon) {
            dernierPaquet = paquet
            dernierLancement = maintenant
            Log.i(TAG, "Puces NFC : ouverture de $paquet")
            ouvrir(paquet)
        }
        return Reponse(200, "$app s'ouvre sur la TV", "Tu peux reposer le téléphone.", lien = "/o/$nom?c=$recu", ok = true)
    }

    private fun introuvable() = Reponse(404, "Adresse inconnue", "Ce lien ne correspond à aucune app.")

    /**
     * Ni puce NFC ni /envoyer : 404. Avec la route du raccourci, même réponse que /envoyer quand
     * il est coupé (docs/telecommande.md : « même réponse pour toute autre adresse »).
     */
    private fun autreAdresse(): Reponse = if (raccourci == null) introuvable() else texte(404, DESACTIVE)

    // ------------------------------------------------------------------ raccourci iOS

    /** POST / GET /envoyer?c=<code> : vérifie le code puis le lien, l'envoie et attend le résultat. */
    private fun envoyer(methode: String, requete: String, corps: String?): Reponse {
        val r = raccourci
        if (r == null || !r.actif()) return texte(404, DESACTIVE)
        if (methode != "GET" && methode != "POST") return texte(405, "Seuls GET et POST sont possibles")
        val params = parametres(requete)
        when (codeRaccourci(r, params["c"] ?: "")) {
            null -> return texte(429, TROP_D_ESSAIS)
            false -> return texte(403, "Code incorrect")
            true -> Unit
        }
        r.journal("lien envoyé (raccourci)")
        val (url, titre) = if (methode == "GET") params["url"] to null else lienDuCorps(corps)
        val lien = Liens.lien(url, titre)
        if (lien == null) {
            r.journal("lien refusé ${ResultatLien.INVALIDE}")
            return texte(400, "Lien invalide")
        }
        return when (val res = r.envoyer(lien)) {
            is ResultatLien.Ouvert -> texte(200, "Envoyé sur ${r.nomTv()} · ouverture dans ${res.appli}")
            ResultatLien.Question -> texte(202, "Le VPN de la TV est coupé : réponds sur la TV")
            is ResultatLien.Refuse -> when (res.raison) {
                ResultatLien.AUCUNE_APPLI -> texte(422, "Aucune appli de la TV ne sait ouvrir ce lien")
                ResultatLien.INVALIDE -> texte(400, "Lien invalide")
                ResultatLien.VPN_COUPE -> texte(202, "Le VPN de la TV est coupé : réponds sur la TV")
                else -> texte(500, "La TV n'a pas pu ouvrir ce lien")
            }
            null -> texte(500, "La TV n'a pas répondu")
        }
    }

    /**
     * Code du raccourci : true s'il est bon, false s'il est faux ou absent (compté), null si
     * tout est refusé (plus de 10 codes faux dans la minute, même le bon).
     */
    @Synchronized
    private fun codeRaccourci(r: RouteRaccourci, recu: String): Boolean? {
        val maintenant = horloge()
        if (maintenant - debutEchecsRaccourci > 60_000) {
            debutEchecsRaccourci = maintenant
            echecsRaccourci = 0
        }
        if (echecsRaccourci >= ECHECS_MAX) return null
        if (codeEgal(r.code(), recu)) return true
        echecsRaccourci++
        r.journal("raccourci : code refusé")
        return false
    }

    /**
     * Corps du POST : JSON {url, titre?}, formulaire url=…&titre=…, ou l'URL seule. Clés JSON sans
     * tenir compte des majuscules ni des espaces autour : Raccourcis met souvent une majuscule (« URL »).
     */
    private fun lienDuCorps(corps: String?): Pair<String?, String?> {
        val t = corps?.trim() ?: return null to null
        if (t.startsWith("{")) {
            return try {
                val o = JSONObject(t)
                val cles = o.keys().asSequence().toList()
                fun champ(nom: String): String? =
                    (o.opt(nom) ?: cles.firstOrNull { it.trim().equals(nom, ignoreCase = true) }?.let { o.opt(it) }) as? String
                champ("url") to champ("titre")
            } catch (e: Exception) {
                null to null
            }
        }
        if (t.startsWith("url=")) {
            val p = parametres(t)
            return p["url"] to p["titre"]
        }
        return t to null
    }

    private fun codeValide(recu: String): Boolean = codeEgal(code(), recu)

    companion object {
        private const val TAG = "Projo"
        private const val ECHECS_MAX = 10
        private const val DOUBLON_MS = 2_500L

        /** Corps d'un POST du raccourci : 16 Ko au plus (une URL de 2 048 caractères et son titre). */
        private const val CORPS_MAX = 16 * 1024

        const val DESACTIVE = "Envoi depuis l'iPhone désactivé sur la TV"
        const val TROP_D_ESSAIS = "Trop d'essais : réessaie dans une minute"

        private fun texte(statut: Int, message: String) = Reponse(statut, "", message, texte = true)

        /** Comparaison en temps constant ; un code attendu vide refuse tout. */
        private fun codeEgal(attendu: String, recu: String): Boolean {
            if (attendu.isEmpty() || recu.isEmpty()) return false
            return MessageDigest.isEqual(attendu.toByteArray(Charsets.UTF_8), recu.toByteArray(Charsets.UTF_8))
        }

        /** « a=1&b=2 » → paramètres décodés (le premier l'emporte). */
        private fun parametres(requete: String): Map<String, String> {
            val m = LinkedHashMap<String, String>()
            for (morceau in requete.split('&')) {
                if (morceau.isEmpty()) continue
                val cle = decoder(morceau.substringBefore('='))
                if (cle.isNotEmpty() && cle !in m) m[cle] = decoder(morceau.substringAfter('=', ""))
            }
            return m
        }

        /**
         * Corps d'un POST (Content-Length ou envoi par morceaux), en UTF-8 ; null s'il dépasse
         * [CORPS_MAX] ou s'il est illisible. Répond « 100 Continue » si le client l'attend.
         */
        private fun lireCorps(entree: InputStream, entetes: Map<String, String>, client: Socket): String? {
            if (entetes["expect"]?.equals("100-continue", ignoreCase = true) == true) {
                client.getOutputStream().apply {
                    write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    flush()
                }
            }
            val sortie = java.io.ByteArrayOutputStream()
            val longueur = entetes["content-length"]
            if (longueur != null) {
                val n = longueur.toIntOrNull() ?: return null
                if (n < 0 || n > CORPS_MAX) return null
                if (!lireOctets(entree, n, sortie)) return null
            } else if (entetes["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
                while (true) {
                    val taille = lireLigne(entree)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: return null
                    if (taille == 0) break
                    if (taille < 0 || sortie.size() + taille > CORPS_MAX) return null
                    if (!lireOctets(entree, taille, sortie)) return null
                    lireLigne(entree) ?: return null
                }
            }
            return String(sortie.toByteArray(), Charsets.UTF_8)
        }

        private fun lireOctets(entree: InputStream, n: Int, sortie: java.io.ByteArrayOutputStream): Boolean {
            val tampon = ByteArray(4_096)
            var reste = n
            while (reste > 0) {
                val lu = entree.read(tampon, 0, minOf(reste, tampon.size))
                if (lu < 0) return false
                sortie.write(tampon, 0, lu)
                reste -= lu
            }
            return true
        }

        private fun decoder(s: String): String = try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            ""
        }

        /** Ligne terminée par \n (sans \r), au plus 4 Ko ; null à la fin du flux. */
        private fun lireLigne(entree: InputStream): String? {
            val b = StringBuilder()
            while (true) {
                val c = entree.read()
                if (c < 0) return if (b.isEmpty()) null else b.toString()
                if (c == '\n'.code) return b.toString().trimEnd('\r')
                if (b.length >= 4_096) return null
                b.append(c.toChar())
            }
        }

        private fun raison(statut: Int) = when (statut) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            403 -> "Forbidden"
            422 -> "Unprocessable Entity"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            429 -> "Too Many Requests"
            else -> "Error"
        }

        private fun echapper(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

        /** Page affichée sur le téléphone (sombre, lisible d'une main). */
        fun page(r: Reponse): String {
            val icone = if (r.ok) "✓" else "✕"
            val couleur = if (r.ok) "#22c55e" else "#f87171"
            val copie = if (r.lien == null) "" else """
<details><summary>Lien de cette puce</summary>
<p class="d">Pour écrire une autre puce ou un Raccourci iPhone : copie ce lien.</p>
<input id="l" readonly value="">
<button id="b" type="button">Copier le lien</button>
</details>
<script>
var l=document.getElementById('l');l.value=location.href;
document.getElementById('b').onclick=function(){l.focus();l.select();l.setSelectionRange(0,9999);
var ok=false;try{ok=document.execCommand('copy')}catch(e){}
this.textContent=ok?'Copié ✓':'Sélectionné : copie-le'};
</script>"""
            return """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark">
<title>iBiTV</title><style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f1115;color:#f3f4f6;
font:17px -apple-system,BlinkMacSystemFont,system-ui,sans-serif;text-align:center}
main{padding:24px 20px;max-width:440px;width:100%;box-sizing:border-box}
.i{width:84px;height:84px;margin:0 auto 14px;border-radius:50%;display:flex;align-items:center;justify-content:center;
font-size:46px;background:${couleur}22;color:$couleur}
h1{font-size:23px;margin:0 0 8px;font-weight:600}p{color:#9ca3af;margin:6px 0;line-height:1.4}
details{margin-top:28px;color:#9ca3af;font-size:14px}summary{cursor:pointer}
.d{font-size:13px}input{width:100%;box-sizing:border-box;margin-top:8px;padding:10px;border-radius:10px;
border:1px solid #374151;background:#1f2937;color:#f3f4f6;font-size:12px}
button{margin-top:10px;padding:11px 20px;border:0;border-radius:10px;background:#3b82f6;color:#fff;font-size:15px}
.m{margin-top:30px;font-size:12px;color:#6b7280}
</style></head><body><main>
<div class="i">$icone</div><h1>${echapper(r.titre)}</h1><p>${echapper(r.message)}</p>$copie
<p class="m">iBiTV · puces NFC</p>
</main></body></html>"""
        }
    }
}
