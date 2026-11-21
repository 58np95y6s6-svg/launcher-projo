// Client minimal du protocole WebSocket du serveur PeerJS (sans la bibliothèque PeerJS).
//
// Connexion : ws(s)://hote:port{chemin}peerjs?key=<clé>&id=<notre id>&token=<jeton>
// Serveur → client : {"type":"OPEN"} | {"type":"ID-TAKEN","payload":{"msg":…}}
//                    | {"type":"ERROR","payload":{"msg":…}}
//                    | {"type":"OFFER"|"ANSWER"|"CANDIDATE"|"LEAVE","src":…,"dst":…,"payload":{…}}
//                    | {"type":"EXPIRE","src":<pair injoignable>,"dst":<nous>}
// Client → serveur : {"type":"HEARTBEAT"} toutes les 5 s,
//                    {"type":"OFFER"|…,"dst":<pair>,"payload":{…}} (le serveur ajoute « src »).

import { journal } from "./journal.js";

export function urlSignalisation(peerjs, id, jeton) {
  const schema = peerjs.securise ? "wss" : "ws";
  let chemin = peerjs.chemin || "/";
  if (!chemin.endsWith("/")) chemin += "/";
  const q = new URLSearchParams({ key: peerjs.cle, id, token: jeton });
  return `${schema}://${peerjs.hote}:${peerjs.port}${chemin}peerjs?${q}`;
}

export class ErreurSignalisation extends Error {
  constructor(code) { super(code); this.code = code; } // "id_pris" | "serveur" | "fermee"
}

export class Signalisation extends EventTarget {
  constructor({ peerjs, id, jeton, WebSocketImpl = globalThis.WebSocket }) {
    super();
    this.peerjs = peerjs;
    this.id = id;
    this.jeton = jeton;
    this.WebSocketImpl = WebSocketImpl;
    this.ws = null;
    this.ouverte = false;
    this._hb = null;
  }

  /** Ouvre la connexion. Résolue à OPEN (ou après delaiOpenMs sans erreur : reprise d'id). */
  ouvrir() {
    this.fermer();
    return new Promise((resoudre, rejeter) => {
      let fini = false;
      const terminer = (err) => {
        if (fini) return;
        fini = true;
        clearTimeout(minuterie);
        if (err) { this.fermer(); rejeter(err); }
        else { this.ouverte = true; journal("signalisation_ouverte"); resoudre(); }
      };
      let minuterie = null;
      let ws;
      try {
        ws = new this.WebSocketImpl(urlSignalisation(this.peerjs, this.id, this.jeton));
      } catch {
        return rejeter(new ErreurSignalisation("serveur"));
      }
      this.ws = ws;
      ws.onopen = () => {
        this._hb = setInterval(() => this._brut({ type: "HEARTBEAT" }), this.peerjs.intervalleHeartbeatMs || 5000);
        minuterie = setTimeout(() => terminer(), this.peerjs.delaiOpenMs ?? 2500);
      };
      ws.onmessage = (ev) => {
        let m;
        try { m = JSON.parse(ev.data); } catch { return; }
        if (!m || typeof m.type !== "string") return;
        switch (m.type) {
          case "OPEN": terminer(); break;
          case "ID-TAKEN": journal("signalisation_erreur", "id_pris"); terminer(new ErreurSignalisation("id_pris")); break;
          case "ERROR": journal("signalisation_erreur", "serveur"); terminer(new ErreurSignalisation("serveur")); break;
          case "HEARTBEAT": break;
          default:
            this.dispatchEvent(new CustomEvent("message", { detail: m }));
        }
      };
      // Échec de connexion : certains environnements ne déclenchent pas « close » ensuite
      ws.onerror = () => { if (!fini) terminer(new ErreurSignalisation("fermee")); };
      ws.onclose = () => {
        if (this.ws !== ws) return;
        clearInterval(this._hb);
        this.ws = null;
        const etaitOuverte = this.ouverte;
        this.ouverte = false;
        terminer(new ErreurSignalisation("fermee"));
        if (etaitOuverte) {
          journal("signalisation_fermee");
          this.dispatchEvent(new CustomEvent("fermee"));
        }
      };
    });
  }

  /** Envoie un message de mise en relation à un pair. */
  envoyer(type, dst, payload) {
    return this._brut({ type, dst, payload });
  }

  _brut(obj) {
    if (!this.ws || this.ws.readyState !== 1) return false;
    this.ws.send(JSON.stringify(obj));
    return true;
  }

  fermer() {
    clearInterval(this._hb);
    const ws = this.ws;
    this.ws = null;
    this.ouverte = false;
    if (ws) {
      ws.onclose = ws.onmessage = ws.onopen = null;
      try { ws.close(); } catch {}
    }
  }
}
