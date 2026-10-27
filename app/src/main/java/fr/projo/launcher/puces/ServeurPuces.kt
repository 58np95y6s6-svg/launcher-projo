package fr.projo.launcher.puces

import android.util.Log
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Petit serveur HTTP du Fire TV, sur le Wi-Fi de la maison, pour les puces NFC.
 *
 * Une seule adresse : GET /o/<app>?c=<code>. Le code (secret, dans Réglages → Puces NFC)
 * est obligatoire ; seules les apps installées s'ouvrent (aucune autre action possible).
 * La page rendue s'affiche sur le téléphone : « Netflix s'ouvre sur la TV ✓ ».
 * HEAD et les autres adresses n'ouvrent jamais rien (aperçus de liens, favicon…).
 *
 * Le journal ne contient jamais le code.
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
) {

    class Reponse(val statut: Int, val titre: String, val message: String, val lien: String? = null, val ok: Boolean = false)

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
        // En-têtes lus jusqu'à la ligne vide (ignorés), au plus 64
        for (i in 0 until 64) {
            val l = lireLigne(entree) ?: break
            if (l.isEmpty()) break
        }
        val morceaux = premiere.split(' ')
        val methode = morceaux.getOrNull(0) ?: ""
        val cible = morceaux.getOrNull(1) ?: ""
        val r = repondre(methode, cible)
        val corps = if (methode == "HEAD") ByteArray(0) else page(r).toByteArray(Charsets.UTF_8)
        val longueur = if (methode == "HEAD") page(r).toByteArray(Charsets.UTF_8).size else corps.size
        val entete = buildString {
            append("HTTP/1.1 ").append(r.statut).append(' ').append(raison(r.statut)).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
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

    /** Décide de la réponse (et ouvre l'app si tout est bon). */
    @Synchronized
    fun repondre(methode: String, cible: String): Reponse {
        if (methode != "GET" && methode != "HEAD") return Reponse(405, "Non", "Seule l'ouverture d'un lien est possible.")
        val chemin = cible.substringBefore('?')
        val requete = cible.substringAfter('?', "")
        if (!chemin.startsWith("/o/")) return introuvable()
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

    private fun codeValide(recu: String): Boolean {
        val attendu = code()
        if (attendu.isEmpty() || recu.isEmpty()) return false
        // Comparaison en temps constant
        return MessageDigest.isEqual(attendu.toByteArray(Charsets.UTF_8), recu.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val TAG = "Projo"
        private const val ECHECS_MAX = 10
        private const val DOUBLON_MS = 2_500L

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
            403 -> "Forbidden"
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
<title>iBiProjo</title><style>
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
<p class="m">iBiProjo · puces NFC</p>
</main></body></html>"""
        }
    }
}
