package fr.projo.launcher.remote

import android.os.Handler
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client du serveur de mise en relation PeerJS, sans la bibliothèque PeerJS : on parle
 * directement son protocole WebSocket (messages JSON `type` / `src` / `dst` / `payload`).
 *
 * - `OPEN` : identifiant accepté ; `ID-TAKEN` / `ERROR` : refus (on réessaie plus tard) ;
 * - `HEARTBEAT` envoyé toutes les [RemoteConfig.HEARTBEAT_MS] ;
 * - `OFFER`, `CANDIDATE`, `LEAVE`, `EXPIRE` transmis à [surMessage] ;
 * - reconnexion avec un délai croissant (2 s, 4 s, 8 s… 2 min), remis à zéro à chaque `OPEN`.
 *
 * Tous les rappels arrivent sur [fil] (le fil de la télécommande).
 */
class ClientPeerJs(
    private val http: OkHttpClient,
    private val fil: Handler,
    val id: String,
    private val jeton: String,
    private val surMessage: (type: String, src: String, payload: JSONObject?) -> Unit,
    private val surEtat: (connecte: Boolean) -> Unit = {},
) {
    private var socket: WebSocket? = null
    private var actif = false
    private var ouvert = false
    private var delai = RemoteConfig.RECONNEXION_MIN_MS
    private var generation = 0

    private val battement = object : Runnable {
        override fun run() {
            if (!ouvert) return
            socket?.send("{\"type\":\"HEARTBEAT\"}")
            fil.postDelayed(this, RemoteConfig.HEARTBEAT_MS)
        }
    }
    private val reconnexion = Runnable { connecter() }

    /** Se connecte (et reste connecté) jusqu'à [arreter]. */
    fun demarrer() {
        if (actif) return
        actif = true
        delai = RemoteConfig.RECONNEXION_MIN_MS
        connecter()
    }

    fun arreter() {
        actif = false
        fil.removeCallbacks(reconnexion)
        fil.removeCallbacks(battement)
        generation++
        ouvert = false
        socket?.close(1000, null)
        socket = null
        surEtat(false)
    }

    /** Réseau revenu : on retente tout de suite. */
    fun relancer() {
        if (!actif || ouvert) return
        fil.removeCallbacks(reconnexion)
        delai = RemoteConfig.RECONNEXION_MIN_MS
        connecter()
    }

    val connecte: Boolean get() = ouvert

    fun envoyer(type: String, dst: String, payload: JSONObject) {
        val s = socket ?: return
        if (!ouvert) return
        s.send(JSONObject().put("type", type).put("dst", dst).put("payload", payload).toString())
    }

    private fun connecter() {
        if (!actif) return
        socket?.cancel()
        val gen = ++generation
        val requete = Request.Builder().url(RemoteConfig.urlPeerJs(id, jeton)).build()
        socket = http.newWebSocket(requete, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                fil.post { if (gen == generation) recu(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                fil.post { if (gen == generation) perdu() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fil.post { if (gen == generation) perdu() }
            }
        })
    }

    private fun recu(texte: String) {
        val m = try { JSONObject(texte) } catch (e: Exception) { return }
        when (val type = m.optString("type")) {
            "OPEN" -> {
                ouvert = true
                delai = RemoteConfig.RECONNEXION_MIN_MS
                Log.i(TAG, "Serveur de mise en relation : connecté")
                fil.removeCallbacks(battement)
                fil.postDelayed(battement, RemoteConfig.HEARTBEAT_MS)
                surEtat(true)
            }
            "ID-TAKEN", "ERROR", "INVALID-KEY" -> {
                Log.w(TAG, "Serveur de mise en relation : refus ($type)")
                socket?.close(1000, null)
                perdu()
            }
            "HEARTBEAT" -> Unit
            else -> surMessage(type, m.optString("src"), m.optJSONObject("payload"))
        }
    }

    private fun perdu() {
        val etaitOuvert = ouvert
        ouvert = false
        generation++
        socket = null
        fil.removeCallbacks(battement)
        if (etaitOuvert) surEtat(false)
        if (!actif) return
        fil.removeCallbacks(reconnexion)
        fil.postDelayed(reconnexion, delai)
        delai = (delai * 2).coerceAtMost(RemoteConfig.RECONNEXION_MAX_MS)
    }

    companion object {
        private const val TAG = "Projo.Telecommande"

        /** Client HTTP partagé, léger (une seule WebSocket à la fois, ou deux pendant l'appairage). */
        fun clientHttp(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
