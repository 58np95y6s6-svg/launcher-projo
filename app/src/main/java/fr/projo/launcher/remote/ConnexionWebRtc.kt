package fr.projo.launcher.remote

import android.content.Context
import android.os.Handler
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * Usine WebRTC partagée, créée à la première connexion et libérée quand il n'y en a plus
 * (1 Go de RAM : rien n'est chargé tant qu'aucun téléphone ne se connecte).
 */
object UsineWebRtc {
    private var initialisee = false
    private var usine: PeerConnectionFactory? = null

    fun obtenir(context: Context): PeerConnectionFactory {
        usine?.let { return it }
        if (!initialisee) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions(),
            )
            initialisee = true
        }
        return PeerConnectionFactory.builder().createPeerConnectionFactory().also { usine = it }
    }

    fun liberer() {
        usine?.dispose()
        usine = null
    }
}

/**
 * Une connexion WebRTC avec un téléphone : le téléphone fait l'offre (OFFER) et ouvre le
 * DataChannel « telecommande » ; la TV répond (ANSWER) et échange les candidats ICE.
 *
 * Format des `payload` relayés par le serveur (compatible PeerJS, voir docs/telecommande.md) :
 * - OFFER / ANSWER : `{ sdp: { type, sdp }, type: "data", connectionId }` ;
 * - CANDIDATE : `{ candidate: { candidate, sdpMid, sdpMLineIndex }, type: "data", connectionId }`.
 *
 * Tous les rappels sont ramenés sur [fil].
 */
class ConnexionWebRtc(
    private val usine: PeerConnectionFactory,
    private val fil: Handler,
    /** Client PeerJS par lequel l'offre est arrivée (identifiant principal ou temporaire). */
    val client: ClientPeerJs,
    /** Identifiant PeerJS du téléphone. */
    val distant: String,
    val connectionId: String,
    private val creerSession: (ConnexionWebRtc, dtlsTel: String?, dtlsTv: String?) -> SessionTelecommande,
    private val surFin: (ConnexionWebRtc) -> Unit,
) {
    private var pc: PeerConnection? = null
    private var canal: DataChannel? = null
    private var dtlsTel: String? = null
    private var dtlsTv: String? = null
    private var descriptionDistante = false
    private val candidatsEnAttente = mutableListOf<IceCandidate>()
    var session: SessionTelecommande? = null
        private set
    private var finie = false
    val creeLe = System.currentTimeMillis()

    /** Offre reçue du téléphone. */
    fun demarrer(offre: String) {
        dtlsTel = CryptoTelecommande.empreinteDtls(offre)
        val config = PeerConnection.RTCConfiguration(
            RemoteConfig.STUN.map { PeerConnection.IceServer.builder(it).createIceServer() },
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }
        val p = usine.createPeerConnection(config, observateur) ?: return terminer()
        pc = p
        p.setRemoteDescription(object : SdpSimple() {
            override fun onSetSuccess() {
                fil.post { if (!finie) repondre() }
            }
        }, SessionDescription(SessionDescription.Type.OFFER, offre))
    }

    private fun repondre() {
        val p = pc ?: return
        descriptionDistante = true
        candidatsEnAttente.forEach { p.addIceCandidate(it) }
        candidatsEnAttente.clear()
        p.createAnswer(object : SdpSimple() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                fil.post {
                    if (finie) return@post
                    p.setLocalDescription(SdpSimple(), sdp)
                    dtlsTv = CryptoTelecommande.empreinteDtls(sdp.description)
                    client.envoyer(
                        "ANSWER", distant,
                        JSONObject()
                            .put("sdp", JSONObject().put("type", "answer").put("sdp", sdp.description))
                            .put("type", "data")
                            .put("connectionId", connectionId)
                            .put("browser", "ibiprojo"),
                    )
                }
            }
        }, MediaConstraints())
    }

    /** Candidat ICE du téléphone. */
    fun ajouterCandidat(c: JSONObject) {
        val candidat = IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.optString("candidate"))
        if (candidat.sdp.isEmpty()) return // fin des candidats
        if (descriptionDistante) pc?.addIceCandidate(candidat) else candidatsEnAttente += candidat
    }

    fun envoyer(message: String) {
        val c = canal ?: return
        if (c.state() != DataChannel.State.OPEN) return
        c.send(DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)), false))
    }

    /** Ferme après un court délai (laisse partir le dernier message : revoked, erreur…). */
    fun fermerBientot() {
        fil.postDelayed({ terminer() }, 400)
    }

    fun terminer() {
        if (finie) return
        finie = true
        session?.terminer()
        canal?.let { c -> runCatching { c.unregisterObserver(); c.close(); c.dispose() } }
        canal = null
        pc?.let { p -> runCatching { p.dispose() } } // dispose() ferme aussi la connexion
        pc = null
        surFin(this)
    }

    private val observateur = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            fil.post {
                if (finie) return@post
                client.envoyer(
                    "CANDIDATE", distant,
                    JSONObject()
                        .put("candidate", JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex))
                        .put("type", "data")
                        .put("connectionId", connectionId),
                )
            }
        }

        override fun onDataChannel(dc: DataChannel) {
            fil.post {
                if (finie || canal != null || dc.label() != RemoteConfig.CANAL) {
                    runCatching { dc.close() }
                    return@post
                }
                canal = dc
                dc.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {
                        val etat = dc.state()
                        fil.post { if (etat == DataChannel.State.OPEN) ouvert() else if (etat == DataChannel.State.CLOSED) terminer() }
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) {
                        if (buffer.binary) return
                        val donnees = buffer.data
                        if (donnees.remaining() > Protocole.TAILLE_MAX) return
                        val octets = ByteArray(donnees.remaining()).also { donnees.get(it) }
                        val texte = String(octets, Charsets.UTF_8)
                        fil.post { session?.recevoir(texte) }
                    }
                })
                if (dc.state() == DataChannel.State.OPEN) ouvert()
            }
        }

        override fun onIceConnectionChange(etat: PeerConnection.IceConnectionState) {
            if (etat == PeerConnection.IceConnectionState.FAILED || etat == PeerConnection.IceConnectionState.CLOSED) {
                fil.post { terminer() }
            }
        }

        override fun onSignalingChange(etat: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(recoit: Boolean) {}
        override fun onIceGatheringChange(etat: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidats: Array<out IceCandidate>) {}
        override fun onAddStream(flux: MediaStream) {}
        override fun onRemoveStream(flux: MediaStream) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun ouvert() {
        if (session != null || finie) return
        session = creerSession(this, dtlsTel, dtlsTv)
    }

    /** SdpObserver dont toutes les méthodes sont facultatives. */
    private open inner class SdpSimple : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(erreur: String?) { fil.post { terminer() } }
        override fun onSetFailure(erreur: String?) { fil.post { terminer() } }
    }
}
