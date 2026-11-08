// Interface de la télécommande : écrans, gestes, champ texte, liste des TV, mises à jour.

import { Controleur } from "./controleur.js";
import { lireFragment, lireSaisieAppairage } from "./appairage.js";
import { TEXTES_VERSION } from "./codec.js";
import { initialiserInstallation, majCartes, estIOS, estInstallee } from "./installation.js";
import { journal } from "./journal.js";
import { OngletApplis } from "./applis.js";
import { OngletReglages } from "./reglages.js";
import { OngletEnvoyer } from "./envoyer.js";
import { BandeauNba } from "./bandeau-nba.js";
import { lireDesign, themePour, texteConnectee, appliquerTheme, NOMS_DESIGN } from "./theme.js";

// Comportement d'appli : pas de zoom au pincement (iOS ignore user-scalable=no).
// Le zoom au double appui est déjà coupé par touch-action: manipulation (CSS).
for (const ev of ["gesturestart", "gesturechange", "gestureend"]) {
  document.addEventListener(ev, (e) => e.preventDefault(), { passive: false });
}

const config = globalThis.IBITV_CONFIG;
const $ = (id) => document.getElementById(id);
const ctrl = new Controleur(config);

let infosAppairage = null; // { mode, idTV, secret, empreinteTV? , code? }
let nomAppairage = "";

// ---------- Utilitaires ----------

function montrer(id) {
  for (const e of document.querySelectorAll(".ecran")) e.hidden = e.id !== id;
  document.body.dataset.ecran = id;
  if (id !== "ecran-telecommande") fermerClavier();
  window.scrollTo(0, 0);
}

const echapper = (s) => String(s).replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

// Retour haptique : navigator.vibrate (Android) ; iOS 18+ : interrupteur natif (geste requis).
function vibrer(ms = 12) {
  if (typeof navigator.vibrate === "function") { try { navigator.vibrate(ms); } catch {} }
}
function vibrerIOS() {
  if (typeof navigator.vibrate !== "function" && estIOS()) { try { $("haptique-ios").click(); } catch {} }
}

/** Boîte d'alerte. Renvoie une promesse : true (OK) / false (Annuler). */
function alerte({ titre, texte, ok = "OK", annuler = null }) {
  return new Promise((resoudre) => {
    $("alerte-titre").textContent = titre;
    $("alerte-texte").textContent = texte;
    $("alerte-ok").textContent = ok;
    $("alerte-annuler").hidden = !annuler;
    if (annuler) $("alerte-annuler").textContent = annuler;
    $("alerte").hidden = false;
    $("alerte-ok").focus({ preventScroll: true });
    const fin = (v) => {
      $("alerte").hidden = true;
      $("alerte-ok").onclick = $("alerte-annuler").onclick = null;
      resoudre(v);
    };
    $("alerte-ok").onclick = () => fin(true);
    $("alerte-annuler").onclick = () => fin(false);
  });
}

// ---------- Accueil (aucune TV / ajout) ----------

function montrerAccueil() {
  $("btn-accueil-retour").hidden = ctrl.tvs.length === 0;
  $("erreur-code").hidden = true;
  montrer("ecran-accueil");
}

$("btn-accueil-retour").addEventListener("click", () => montrerTelecommande());

$("form-code").addEventListener("submit", (e) => {
  e.preventDefault();
  const infos = lireSaisieAppairage($("champ-code").value);
  if (!infos) {
    $("erreur-code").textContent = "Code invalide : 8 caractères, par exemple ABCD-EFGH (ou le lien d'appairage complet).";
    $("erreur-code").hidden = false;
    vibrer(40);
    return;
  }
  $("champ-code").value = "";
  demarrerAppairage(infos);
});

// ---------- Appairage ----------

function demarrerAppairage(infos) {
  infosAppairage = infos;
  $("appairage-source").textContent = infos.mode === "qr"
    ? "TV reconnue grâce au QR code."
    : `Code de secours ${infos.code}`;
  $("champ-nom").value = ctrl.identite?.nom || "";
  $("erreur-nom").hidden = true;
  $("carte-ios-lien").hidden = !(infos.mode === "qr" && estIOS() && !estInstallee());
  montrer("ecran-appairage");
}

$("btn-appairage-retour").addEventListener("click", () => {
  infosAppairage = null;
  ctrl.tvs.length ? montrerTelecommande() : montrerAccueil();
});

$("btn-copier-lien").addEventListener("click", async () => {
  if (!infosAppairage || infosAppairage.mode !== "qr") return;
  const { idTV, secret, empreinteTV } = infosAppairage;
  const lien = `${config.urlBase}#pair=${idTV}.${secret}.${empreinteTV}`;
  try {
    await navigator.clipboard.writeText(lien);
    $("btn-copier-lien").textContent = "Lien copié ✓";
  } catch {
    $("btn-copier-lien").textContent = "Copie impossible : utilisez le code de secours";
  }
});

$("form-nom").addEventListener("submit", (e) => {
  e.preventDefault();
  const nom = $("champ-nom").value.trim().replace(/\s+/g, " ");
  if (!nom) {
    $("erreur-nom").textContent = "Donnez un nom à ce téléphone.";
    $("erreur-nom").hidden = false;
    $("champ-nom").focus();
    return;
  }
  $("champ-nom").blur();
  nomAppairage = nom;
  lancerAppairage();
});

function lancerAppairage() {
  majAttente("connexion");
  montrer("ecran-attente");
  ctrl.appairer(infosAppairage, nomAppairage);
}

function majAttente(etape) {
  if (etape === "connexion") {
    $("attente-titre").textContent = "Connexion à la TV…";
    $("attente-texte").textContent = "Gardez l'écran d'appairage ouvert sur la TV.";
  } else {
    $("attente-titre").textContent = "Confirmez sur la TV";
    $("attente-texte").innerHTML =
      `Sur la TV, choisissez <strong>Autoriser</strong> pour « ${echapper(nomAppairage)} ».`;
  }
}

$("btn-annuler").addEventListener("click", () => {
  ctrl.annulerAppairage();
  ctrl.tvs.length ? montrerTelecommande() : demarrerAppairage(infosAppairage);
});

const MESSAGES_ECHEC = {
  refuse: ["Appairage refusé", "L'appairage a été refusé sur la TV."],
  expire: ["Code expiré", "Le code n'est plus valable. Relancez « Appairer un téléphone » sur la TV."],
  invalide: ["Code non reconnu", "Ce code ou ce QR code n'est pas (ou plus) valable. Relancez l'appairage sur la TV."],
  trop_d_essais: ["Trop d'essais", "Patientez une minute, puis réessayez."],
  injoignable: ["TV injoignable", "Vérifiez que la TV est allumée, que l'écran d'appairage est ouvert et que le téléphone est sur le même Wi-Fi."],
  perdue: ["Connexion perdue", "La connexion avec la TV s'est interrompue. Réessayez."],
  auth: ["TV non authentifiée", "La TV n'a pas pu prouver son identité. Scannez à nouveau le QR code affiché sur la TV."],
  serveur: ["Pas de connexion", "Le serveur de mise en relation est injoignable. Vérifiez la connexion Internet du téléphone."],
  id_pris: ["Déjà ouverte", "La télécommande est déjà ouverte dans un autre onglet ou une autre fenêtre. Fermez-la, puis réessayez."],
};

ctrl.addEventListener("appairage", (e) => {
  const d = e.detail;
  if (d.etape === "connexion" || d.etape === "confirmation") {
    majAttente(d.etape);
    if (d.etape === "confirmation") vibrer(20);
    return;
  }
  if (d.etape === "reussi") {
    infosAppairage = null;
    vibrer([20, 60, 20]);
    $("resultat-icone").className = "pastille-resultat";
    $("resultat-icone").innerHTML = '<svg class="ic"><use href="#i-ok"/></svg>';
    $("resultat-titre").textContent = "Appairée !";
    $("resultat-texte").textContent = `« ${d.tv.nom} » est prête à être pilotée depuis ce téléphone.`;
    $("btn-resultat").textContent = "Commencer";
    $("btn-resultat").onclick = () => montrerTelecommande();
    $("btn-resultat-2").hidden = true;
    montrer("ecran-resultat");
    return;
  }
  // Échec
  if (d.raison === "fermee") return;
  if (d.raison === "version") return retourApresEchec(); // l'alerte de version s'affiche par-dessus
  const cle = d.raison === "refus" ? d.refus : d.raison;
  const [titre, texte] = MESSAGES_ECHEC[cle] || MESSAGES_ECHEC.perdue;
  vibrer(60);
  $("resultat-icone").className = "pastille-resultat echec";
  $("resultat-icone").innerHTML = '<svg class="ic"><use href="#i-croix"/></svg>';
  $("resultat-titre").textContent = titre;
  $("resultat-texte").textContent = texte;
  const reessayable = ["injoignable", "perdue", "serveur", "id_pris"].includes(cle);
  $("btn-resultat").textContent = reessayable ? "Réessayer" : "Retour";
  $("btn-resultat").onclick = () => (reessayable ? lancerAppairage() : retourApresEchec());
  $("btn-resultat-2").hidden = !reessayable;
  $("btn-resultat-2").textContent = "Retour";
  $("btn-resultat-2").onclick = retourApresEchec;
  montrer("ecran-resultat");
});

function retourApresEchec() {
  infosAppairage = null;
  ctrl.tvs.length ? montrerTelecommande() : montrerAccueil();
}

// ---------- Télécommande : état ----------

const TEXTES_ETAT = {
  connecte: "Connectée",
  connexion: "Connexion en cours…",
  injoignable: "Box injoignable",
  erreur: "Connexion impossible",
  aucune: "Aucune TV",
};

function montrerTelecommande() {
  if (!ctrl.tvActive) return montrerAccueil();
  majOnglets();
  montrer("ecran-telecommande");
  majEtat({ etat: ctrl.etat });
  ctrl.connecter();
}

let etatAffiche = null;
let dernierEtat = { etat: "aucune" };

function majEtat(detail) {
  dernierEtat = detail;
  const { etat, raison, prochainEssaiMs } = detail;
  const tv = ctrl.tvActive;
  // Nouvelle connexion : les onglets ouverts redemandent leurs données (abonnement aux réglages) ;
  // le box score NBA s'abonne à chaque connexion (bandeau de la télécommande)
  if (etat === "connecte" && etatAffiche !== "connecte") {
    applis.surConnexion(); envoyer.surConnexion(); reglages.surConnexion(); nba.surConnexion();
  }
  etatAffiche = etat;
  $("nom-tv").textContent = tv ? tv.nom : "TV";
  $("voyant").dataset.etat = etat;
  $("ecran-telecommande").dataset.etat = etat;
  // « Connectée · design Galerie » : la télécommande a pris le style de la TV
  let texte = etat === "connecte" ? texteConnectee(tv?.design) : TEXTES_ETAT[etat] || "";
  let bandeau = null;
  if (etat === "injoignable") {
    bandeau = raison === "id_pris"
      ? "La télécommande est déjà ouverte ailleurs (autre onglet ?)."
      : "Box injoignable. Vérifiez qu'elle est allumée et sur le même Wi-Fi."
        + (prochainEssaiMs ? ` Nouvel essai dans ${Math.round(prochainEssaiMs / 1000)} s.` : "");
  } else if (etat === "erreur") {
    if (raison === "version") {
      texte = "Version incompatible";
      bandeau = "Versions incompatibles entre la TV et la télécommande.";
    } else {
      texte = "Authentification impossible";
      bandeau = "La TV n'a pas pu être authentifiée. Oubliez-la puis appairez-la de nouveau.";
    }
  }
  $("etat-tv").textContent = texte;
  majPastille(texte);
  $("bandeau-etat").hidden = !bandeau;
  if (bandeau) $("bandeau-texte").textContent = bandeau;
}

ctrl.addEventListener("etat", (e) => majEtat(e.detail));

// ---------- Thème : suit le design de la TV active ----------
// Gardé avec chaque TV (IndexedDB) et appliqué dès l'ouverture, avant la connexion ;
// sans TV ou sans design connu : Galerie (clair) ou Verre (téléphone en mode sombre).

const sombre = matchMedia("(prefers-color-scheme: dark)");
function majTheme() {
  const design = lireDesign(ctrl.tvActive?.design);
  appliquerTheme(themePour(design, sombre.matches), { retenir: !!design });
}
sombre.addEventListener?.("change", majTheme);
$("btn-reessayer").addEventListener("click", () => { vibrer(); ctrl.connecter({ reinitialiser: true }); });

ctrl.addEventListener("revoquee", async (e) => {
  vibrer([40, 60, 40]);
  await alerte({
    titre: "Accès retiré",
    texte: `« ${e.detail.tv.nom} » a retiré ce téléphone. Appairez-le de nouveau pour piloter cette TV.`,
  });
  ctrl.tvs.length ? montrerTelecommande() : montrerAccueil();
});

ctrl.addEventListener("version", async (e) => {
  const verdict = e.detail.verdict;
  const recharger = verdict === "recharger";
  const ok = await alerte({
    titre: TEXTES_VERSION[verdict],
    texte: recharger
      ? "La TV utilise une version plus récente du protocole. Rechargez la télécommande pour obtenir la dernière version."
      : "La télécommande est plus récente que l'app de la TV. Installez la dernière version d'iBiTV sur la TV.",
    ok: recharger ? "Recharger" : "OK",
    annuler: recharger ? "Plus tard" : null,
  });
  if (recharger && ok) rechargerAvecMiseAJour();
});

// ---------- Télécommande : touches ----------

const REPETITION_DELAI = 380;
const REPETITION_INTERVALLE = 110;

/** Touche (Retour, Accueil, Options, volume, lecture) : envoyée dès l'appui ; volume : répétée si maintenue. */
function brancherTouche(el) {
  const k = el.dataset.k;
  const repete = el.hasAttribute("data-repete");
  let t1 = null, t2 = null;
  const stop = () => {
    clearTimeout(t1); clearInterval(t2); t1 = t2 = null;
    el.classList.remove("appui");
  };
  el.addEventListener("pointerdown", (e) => {
    if (e.button > 0) return;
    e.preventDefault();
    try { el.setPointerCapture(e.pointerId); } catch {}
    el.classList.add("appui");
    vibrer();
    ctrl.touche(k);
    if (repete) t1 = setTimeout(() => { t2 = setInterval(() => { ctrl.touche(k); vibrer(6); }, REPETITION_INTERVALLE); }, REPETITION_DELAI);
  });
  el.addEventListener("pointerup", () => { stop(); vibrerIOS(); });
  el.addEventListener("pointercancel", stop);
  el.addEventListener("lostpointercapture", stop);
  // Clavier physique / lecteur d'écran (clic sans pointeur)
  el.addEventListener("click", (e) => { if (e.detail === 0) ctrl.touche(k); });
  el.addEventListener("contextmenu", (e) => e.preventDefault());
}
for (const el of document.querySelectorAll("#panneau-telecommande .rangee [data-k]")) brancherTouche(el);

// ---------- Télécommande : pavé unique ----------
// Glisser n'importe où sur le pavé : un pas toutes les SEUIL px dans la direction dominante.
// Toucher une flèche (tout son secteur) : un pas ; la garder appuyée : répétition. Toucher OK : valider.
// La touche part au relâcher : un glissé qui commence sur OK ou sur une flèche n'envoie qu'un déplacement.

(() => {
  const pave = $("pave");
  const point = pave.querySelector(".pave-point");
  const SEUIL = 46;     // px par pas pendant un glissé
  const IMMOBILE = 12;  // en deçà : un appui, pas un glissé
  const eclairs = new Map();
  let suivi = null;
  const position = (e) => {
    const r = pave.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  // Retour visuel bref du secteur (ou de OK) qui vient de partir
  const eclair = (k) => {
    const el = pave.querySelector(`[data-k="${k}"]`);
    if (!el) return;
    el.classList.add("appui");
    clearTimeout(eclairs.get(el));
    eclairs.set(el, setTimeout(() => { if (suivi?.cible !== el) el.classList.remove("appui"); }, 160));
  };

  const oublier = () => {
    if (!suivi) return;
    clearTimeout(suivi.t1);
    clearInterval(suivi.t2);
    suivi.cible?.classList.remove("appui");
    pave.classList.remove("glisse");
    suivi = null;
  };

  pave.addEventListener("pointerdown", (e) => {
    if (e.button > 0) return;
    e.preventDefault();
    oublier(); // un seul doigt suivi : le dernier posé (un suivi perdu ne bloque jamais le pavé)
    try { pave.setPointerCapture(e.pointerId); } catch {}
    const p = position(e);
    const cible = e.target.closest?.("[data-k]");
    const s = (suivi = {
      id: e.pointerId, ref: p, depart: p, pas: 0, max: 0,
      cible: cible && pave.contains(cible) ? cible : null, glisse: false, repete: false, t1: null, t2: null,
    });
    s.cible?.classList.add("appui");
    point.style.transform = `translate(${p.x}px, ${p.y}px)`;
    const k = s.cible?.dataset.k;
    if (k && k !== "ok") {
      // Flèche maintenue : répétition, comme une vraie télécommande
      s.t1 = setTimeout(() => {
        s.repete = true;
        ctrl.touche(k);
        vibrer();
        s.t2 = setInterval(() => { ctrl.touche(k); vibrer(6); }, REPETITION_INTERVALLE);
      }, REPETITION_DELAI);
    }
  });

  pave.addEventListener("pointermove", (e) => {
    const s = suivi;
    if (!s || e.pointerId !== s.id) return;
    const p = position(e);
    s.max = Math.max(s.max, Math.hypot(p.x - s.depart.x, p.y - s.depart.y));
    if (s.repete) return; // flèche maintenue : le doigt peut bouger un peu
    if (!s.glisse) {
      if (s.max < IMMOBILE) return;
      s.glisse = true;
      clearTimeout(s.t1);
      s.cible?.classList.remove("appui");
      pave.classList.add("glisse");
    }
    point.style.transform = `translate(${p.x}px, ${p.y}px)`;
    const dx = p.x - s.ref.x, dy = p.y - s.ref.y;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < SEUIL) return;
    let k;
    if (Math.abs(dx) > Math.abs(dy)) {
      k = dx > 0 ? "right" : "left";
      s.ref = { x: s.ref.x + Math.sign(dx) * SEUIL, y: p.y };
    } else {
      k = dy > 0 ? "down" : "up";
      s.ref = { x: p.x, y: s.ref.y + Math.sign(dy) * SEUIL };
    }
    ctrl.touche(k);
    s.pas++;
    eclair(k);
    vibrer(8);
  });

  const fin = (e) => {
    const s = suivi;
    if (!s || e.pointerId !== s.id) return;
    oublier();
    // Appui (sans glisser ni répéter) : la flèche touchée, ou OK
    if (e.type === "pointerup" && !s.glisse && !s.repete && s.max < IMMOBILE && s.cible) {
      const k = s.cible.dataset.k;
      ctrl.touche(k);
      eclair(k);
      vibrer(k === "ok" ? 14 : 12);
      vibrerIOS();
    }
  };
  pave.addEventListener("pointerup", fin);
  pave.addEventListener("pointercancel", fin);
  pave.addEventListener("lostpointercapture", fin);
  pave.addEventListener("contextmenu", (e) => e.preventDefault());
  // Clavier physique / lecteur d'écran (clic sans pointeur)
  for (const b of pave.querySelectorAll("[data-k]")) {
    b.addEventListener("click", (e) => { if (e.detail === 0) { ctrl.touche(b.dataset.k); vibrer(); } });
  }
})();

// ---------- Clavier : feuille du bas ----------
// S'ouvre seule quand la TV attend du texte (focus_text), ou avec « Aa » (texte libre, envoyé
// pareil) ; focus_lost la ferme. Chaque frappe envoie `text` (texte complet + curseur), Entrée `submit`.

const champ = $("champ-texte");
const saisie = $("saisie");
const feuilleClavier = $("feuille-clavier");
const TYPES_INPUT = {
  texte: ["text", "text"], email: ["email", "email"], motdepasse: ["password", "text"],
  nombre: ["text", "decimal"], url: ["url", "url"], recherche: ["search", "search"],
};
const LIBELLES_TYPE = {
  texte: "La TV attend du texte", email: "La TV attend une adresse e-mail", motdepasse: "La TV attend un mot de passe",
  nombre: "La TV attend un nombre", url: "La TV attend une adresse web", recherche: "La TV attend une recherche",
};
const LIBELLES_ACTION = {
  search: "Rechercher sur la TV", go: "Valider sur la TV", send: "Envoyer sur la TV", next: "Suivant", done: "Valider sur la TV",
};
let typeChamp = "texte";
let actionChamp = "done";
let afficherMdp = false;

function appliquerType() {
  const [type, mode] = TYPES_INPUT[typeChamp] || TYPES_INPUT.texte;
  champ.type = typeChamp === "motdepasse" && afficherMdp ? "text" : type;
  champ.inputMode = mode;
  const mdp = typeChamp === "motdepasse";
  $("btn-afficher").hidden = !mdp;
  $("btn-afficher").setAttribute("aria-pressed", String(afficherMdp));
  $("btn-afficher").setAttribute("aria-label", afficherMdp ? "Masquer" : "Afficher");
  $("btn-afficher").querySelector("span").textContent = afficherMdp ? "Masquer" : "Afficher";
  $("btn-afficher").querySelector("use").setAttribute("href", afficherMdp ? "#i-oeil-barre" : "#i-oeil");
  // Ce que la TV attend, et le bouton qui valide
  const tv = saisie.classList.contains("actif");
  $("saisie-aide").textContent = tv ? LIBELLES_TYPE[typeChamp] || LIBELLES_TYPE.texte : "Écrire sur la TV";
  $("btn-entree").textContent = LIBELLES_ACTION[actionChamp] || LIBELLES_ACTION.done;
}

function curseur() {
  try { return Number.isInteger(champ.selectionEnd) ? champ.selectionEnd : undefined; } catch { return undefined; }
}

/** iOS superpose son clavier : la feuille remonte juste au-dessus (zone visible du visualViewport). */
function placerClavier() {
  const vv = window.visualViewport;
  const bas = vv ? Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop)) : 0;
  feuilleClavier.style.setProperty("--clavier-bas", `${bas}px`);
  feuilleClavier.firstElementChild.classList.toggle("clavier-ouvert", bas > 40);
}
for (const ev of ["resize", "scroll"]) {
  window.visualViewport?.addEventListener(ev, () => { if (!feuilleClavier.hidden) placerClavier(); });
}

function ouvrirClavier() {
  appliquerType();
  feuilleClavier.hidden = false;
  placerClavier();
  // Android/Chrome : le clavier s'ouvre ; iOS : seulement pendant un geste (« Aa », ou toucher le champ)
  try { champ.focus({ preventScroll: true }); } catch {}
}

function fermerClavier({ rendreFocus = false } = {}) {
  if (feuilleClavier.hidden) return;
  if (document.activeElement === champ) champ.blur();
  feuilleClavier.hidden = true;
  saisie.classList.remove("invite");
  $("saisie-invite").hidden = true;
  feuilleClavier.style.setProperty("--clavier-bas", "0px");
  if (rendreFocus && !$("btn-clavier").hidden) $("btn-clavier").focus({ preventScroll: true });
}

$("btn-clavier").addEventListener("click", () => { vibrer(); ouvrirClavier(); });
$("btn-fermer-clavier").addEventListener("click", () => { vibrer(); fermerClavier({ rendreFocus: true }); });
feuilleClavier.addEventListener("click", (e) => { if (e.target === feuilleClavier) fermerClavier(); });

champ.addEventListener("input", () => ctrl.texte(champ.value, curseur()));
// Toucher le champ (même déjà « focus » sans clavier, cas d'iOS) ouvre le clavier : fin de l'invitation
const champTouche = () => {
  saisie.classList.remove("invite");
  $("saisie-invite").hidden = true;
};
champ.addEventListener("focus", champTouche);
champ.addEventListener("click", champTouche);
saisie.addEventListener("submit", (e) => {
  e.preventDefault();
  vibrer();
  ctrl.valider();
});
champ.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.isComposing) { e.preventDefault(); vibrer(); ctrl.valider(); }
});

// « Afficher » : ne pas faire perdre le focus (le clavier resterait ouvert)
$("btn-afficher").addEventListener("pointerdown", (e) => e.preventDefault());
$("btn-afficher").addEventListener("click", () => {
  afficherMdp = !afficherMdp;
  appliquerType();
  vibrer();
});
// Valider : le clavier du téléphone reste ouvert (la TV peut demander une suite)
$("btn-entree").addEventListener("pointerdown", (e) => { if (document.activeElement === champ) e.preventDefault(); });

ctrl.addEventListener("focus_text", ({ detail }) => {
  // La TV attend du texte : retour à la télécommande, feuille du clavier ouverte
  if (onglet !== "telecommande") choisirOnglet("telecommande");
  nba.fermerFeuille();
  typeChamp = detail.type;
  actionChamp = detail.action;
  afficherMdp = false; // mot de passe toujours masqué par défaut
  saisie.classList.add("actif");
  champ.enterKeyHint = detail.action;
  champ.value = detail.s;
  vibrer(15);
  ouvrirClavier();
  // iOS : focus() sans geste de l'utilisateur n'ouvre pas le clavier → invitation à toucher le champ
  setTimeout(() => {
    if (!saisie.classList.contains("actif") || feuilleClavier.hidden) return;
    const vv = window.visualViewport;
    const clavierVisible = document.activeElement === champ &&
      (!estIOS() || (vv && vv.height < window.innerHeight * 0.85));
    if (!clavierVisible) {
      saisie.classList.add("invite");
      $("saisie-invite").hidden = false;
    }
  }, 450);
});

function finSaisie() {
  saisie.classList.remove("actif", "invite");
  if (document.activeElement === champ) champ.blur();
  champ.value = "";
  typeChamp = "texte";
  actionChamp = "done";
  afficherMdp = false;
  appliquerType();
  champ.enterKeyHint = "done";
  fermerClavier();
}
ctrl.addEventListener("focus_lost", finSaisie);
appliquerType();

// ---------- Onglets : Télécommande | Applis | Envoyer | Réglages ----------
// Montrés seulement pour les fonctions annoncées par la TV (hello.fonctions) ; sans annonce,
// la télécommande reste seule, comme avant.

const ONGLETS = ["telecommande", "applis", "envoyer", "reglages"];
const FONCTION_ONGLET = { applis: "apps", envoyer: "liens", reglages: "reglages" };
let onglet = "telecommande";
let idTVAffichee;
let minuterieToast = null;

/** Confirmation discrète (au-dessus des onglets), lue par les lecteurs d'écran. */
function annoncer(texte, { erreur = false, duree = erreur ? 3500 : 2600 } = {}) {
  const toast = $("toast");
  clearTimeout(minuterieToast);
  $("toast-texte").textContent = texte;
  toast.classList.toggle("erreur", erreur);
  toast.querySelector("use").setAttribute("href", erreur ? "#i-alerte" : "#i-ok");
  toast.classList.add("visible");
  minuterieToast = setTimeout(() => {
    toast.classList.remove("visible");
    minuterieToast = setTimeout(() => { $("toast-texte").textContent = ""; }, 300);
  }, duree);
}

/** Pastille « ● Salon » des onglets Applis, Envoyer et Réglages (point : état de la connexion). */
function majPastille(texteEtat = $("etat-tv").textContent) {
  const nom = ctrl.tvActive?.nom || "TV";
  $("pastille-nom").textContent = nom;
  $("pastille-voyant").dataset.etat = ctrl.etat;
  $("pastille-tv").setAttribute("aria-label", `${nom} : ${texteEtat}. Changer de TV`);
}

const applis = new OngletApplis({ ctrl, annoncer, vibrer });
const reglages = new OngletReglages({ ctrl, annoncer, vibrer });
const envoyer = new OngletEnvoyer({ ctrl, annoncer, vibrer, confirmer: alerte });
const nba = new BandeauNba({ ctrl, annoncer, vibrer });

function ongletsDisponibles() {
  const f = ctrl.fonctions;
  return ONGLETS.filter((o) => o === "telecommande" || f.includes(FONCTION_ONGLET[o]));
}

function majOnglets() {
  if (ctrl.active !== idTVAffichee) {
    idTVAffichee = ctrl.active;
    applis.changerTV(ctrl.active);
    envoyer.changerTV(ctrl.active);
    reglages.changerTV();
    nba.changerTV();
  }
  nba.majFonctions();
  const dispo = ongletsDisponibles();
  const avec = dispo.length > 1;
  $("onglets").hidden = !avec;
  $("ecran-telecommande").classList.toggle("avec-onglets", avec);
  for (const b of document.querySelectorAll(".onglet")) b.hidden = !dispo.includes(b.dataset.onglet);
  $("onglets").style.setProperty("--n", String(dispo.length));
  $("onglets").dataset.n = String(dispo.length); // 4 onglets : libellés un peu plus petits
  if (!dispo.includes(onglet)) choisirOnglet("telecommande");
  else $("onglets").style.setProperty("--i", String(dispo.indexOf(onglet)));
}

function choisirOnglet(nom, { focus = false } = {}) {
  onglet = nom;
  for (const b of document.querySelectorAll(".onglet")) {
    const actif = b.dataset.onglet === nom;
    b.setAttribute("aria-selected", String(actif));
    b.tabIndex = actif ? 0 : -1;
    if (actif && focus) b.focus();
  }
  $("onglets").style.setProperty("--i", String(Math.max(0, ongletsDisponibles().indexOf(nom))));
  $("ecran-telecommande").dataset.onglet = nom;
  $("panneau-telecommande").hidden = nom !== "telecommande";
  $("panneau-applis").hidden = nom !== "applis";
  $("panneau-envoyer").hidden = nom !== "envoyer";
  $("panneau-reglages").hidden = nom !== "reglages";
  // En-tête : la TV et « Aa » sur la télécommande ; ailleurs, pastille compacte de la TV
  $("btn-tvs").hidden = nom !== "telecommande";
  $("btn-clavier").hidden = nom !== "telecommande";
  $("pastille-tv").hidden = nom === "telecommande";
  majPastille();
  if (nom !== "telecommande") { fermerClavier(); nba.fermerFeuille(); }
  if (nom === "applis") applis.afficher(); else applis.masquer();
  if (nom === "envoyer") envoyer.afficher(); else envoyer.masquer();
  if (nom === "reglages") reglages.afficher(); else reglages.masquer();
}

for (const b of document.querySelectorAll(".onglet")) {
  b.addEventListener("click", () => {
    vibrer();
    if (b.dataset.onglet !== onglet) return choisirOnglet(b.dataset.onglet);
    // Onglet déjà ouvert : retour en haut de la liste (« Envoyer » : de l'écran « Bouton Partager » à l'onglet)
    if (onglet === "envoyer" && envoyer.guideOuvert) return envoyer.fermerGuide();
    (onglet === "envoyer" ? envoyer.defile : $(`${onglet}-defile`))?.scrollTo?.({ top: 0, behavior: "smooth" });
  });
}
// Clavier : flèches entre les onglets (motif « tablist »)
$("onglets").addEventListener("keydown", (e) => {
  const dispo = ongletsDisponibles();
  const i = dispo.indexOf(onglet);
  const j = { ArrowLeft: i - 1, ArrowRight: i + 1, Home: 0, End: dispo.length - 1 }[e.key];
  if (j === undefined) return;
  e.preventDefault();
  choisirOnglet(dispo[(j + dispo.length) % dispo.length], { focus: true });
});

// ---------- Liste des TV ----------

function majListeTVs() {
  const liste = $("liste-tvs");
  liste.replaceChildren();
  if (!ctrl.tvs.length) {
    liste.innerHTML = '<li class="liste-vide">Aucune TV appairée.</li>';
  }
  for (const tv of ctrl.tvs) {
    const li = document.createElement("li");
    const choix = document.createElement("button");
    choix.type = "button";
    choix.className = "tv-choix";
    choix.setAttribute("aria-current", String(tv.id === ctrl.active));
    choix.innerHTML = `<svg class="ic"><use href="#i-tv"/></svg><span class="tv-textes">
      <span class="tv-nom">${echapper(tv.nom)}</span>
      <span class="tv-detail">${tv.id === ctrl.active ? "Pilotée" : "Toucher pour piloter"}${lireDesign(tv.design) ? ` · design ${NOMS_DESIGN[tv.design]}` : ""}${tv.versionApp ? ` · iBiTV ${echapper(tv.versionApp)}` : ""}</span></span>`;
    choix.addEventListener("click", () => {
      vibrer();
      fermerFeuille();
      if (tv.id !== ctrl.active) ctrl.choisir(tv.id);
    });
    const oublier = document.createElement("button");
    oublier.type = "button";
    oublier.className = "tv-oublier";
    oublier.textContent = "Oublier";
    oublier.setAttribute("aria-label", `Oublier ${tv.nom}`);
    oublier.addEventListener("click", async () => {
      const ok = await alerte({
        titre: `Oublier « ${tv.nom} » ?`,
        texte: "Ce téléphone ne pourra plus piloter cette TV sans un nouvel appairage.",
        ok: "Oublier",
        annuler: "Annuler",
      });
      if (!ok) return;
      await ctrl.oublier(tv.id);
      if (!ctrl.tvs.length) { fermerFeuille(); montrerAccueil(); }
    });
    li.append(choix, oublier);
    liste.append(li);
  }
}

function ouvrirFeuille() {
  majListeTVs();
  majCartes();
  $("feuille-tvs").hidden = false;
}
function fermerFeuille() { $("feuille-tvs").hidden = true; }

$("btn-tvs").addEventListener("click", () => { vibrer(); ouvrirFeuille(); });
$("pastille-tv").addEventListener("click", () => { vibrer(); ouvrirFeuille(); });
$("feuille-tvs").addEventListener("click", (e) => { if (e.target === $("feuille-tvs")) fermerFeuille(); });
$("btn-ajouter-tv").addEventListener("click", () => { fermerFeuille(); montrerAccueil(); });
ctrl.addEventListener("tvs", () => {
  if (!$("feuille-tvs").hidden) majListeTVs();
  majTheme(); // design de la TV active (annoncé, poussé, ou gardé), ou thème par défaut
  majEtat(dernierEtat); // nom de la TV, « Connectée · design … »
  majOnglets(); // fonctions annoncées par la TV, ou autre TV choisie
});
addEventListener("keydown", (e) => {
  if (e.key === "Escape") { fermerFeuille(); fermerClavier({ rendreFocus: true }); }
});
$("version-app").textContent = `Télécommande ${config.versionApp} · protocole v${config.versionProtocole}`;

// ---------- Service worker et mises à jour ----------

let rechargementDemande = false;
let enregistrementSW = null;

function montrerBanniereMaj() {
  journal("mise_a_jour_disponible");
  $("banniere-maj").hidden = false;
}

function rechargerAvecMiseAJour() {
  rechargementDemande = true;
  const attente = enregistrementSW?.waiting;
  if (attente) attente.postMessage({ type: "SKIP_WAITING" });
  else location.reload();
}
$("btn-recharger").addEventListener("click", rechargerAvecMiseAJour);

async function initialiserSW() {
  if (!("serviceWorker" in navigator)) return;
  try {
    const reg = (enregistrementSW = await navigator.serviceWorker.register("sw.js", { updateViaCache: "none" }));
    const surveiller = (w) => w && w.addEventListener("statechange", () => {
      if (w.state === "installed" && navigator.serviceWorker.controller) montrerBanniereMaj();
    });
    if (reg.waiting && navigator.serviceWorker.controller) montrerBanniereMaj();
    surveiller(reg.installing);
    reg.addEventListener("updatefound", () => surveiller(reg.installing));
    navigator.serviceWorker.addEventListener("controllerchange", () => {
      if (rechargementDemande) location.reload();
    });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") reg.update().catch(() => {});
    });
  } catch {
    journal("sw_erreur");
  }
}

// ---------- Démarrage ----------

function lireLienAppairage() {
  if (!location.hash) return null;
  const infos = lireFragment(location.hash);
  // Le secret ne doit rester ni dans l'historique, ni dans l'adresse de l'app installée
  history.replaceState(null, "", location.pathname + location.search);
  return infos || "invalide";
}

let demarrage = null;
addEventListener("hashchange", async () => {
  await demarrage; // l'écran initial ne doit pas recouvrir l'appairage
  const infos = lireLienAppairage();
  if (infos && infos !== "invalide") demarrerAppairage(infos);
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") ctrl.reveil();
});
addEventListener("online", () => ctrl.reveil());

async function demarrer() {
  initialiserInstallation();
  initialiserSW();
  const lien = lireLienAppairage();
  try {
    await ctrl.init();
  } catch {
    await alerte({ titre: "Stockage indisponible", texte: "Le navigateur refuse le stockage local (navigation privée ?). La télécommande ne peut pas fonctionner." });
    return;
  }
  if (lien && lien !== "invalide") return demarrerAppairage(lien);
  if (lien === "invalide") {
    montrerAccueil();
    $("erreur-code").textContent = "Lien d'appairage invalide ou incomplet. Scannez à nouveau le QR code, ou saisissez le code de secours.";
    $("erreur-code").hidden = false;
    return;
  }
  ctrl.tvActive ? montrerTelecommande() : montrerAccueil();
}

demarrage = demarrer();
