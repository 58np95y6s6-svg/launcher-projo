// Interface de la télécommande : écrans, gestes, champ texte, liste des TV, mises à jour.

import { Controleur } from "./controleur.js";
import { lireFragment, lireSaisieAppairage } from "./appairage.js";
import { TEXTES_VERSION } from "./codec.js";
import { initialiserInstallation, majCartes, estIOS, estInstallee } from "./installation.js";
import { journal } from "./journal.js";

const config = globalThis.IBIPROJO_CONFIG;
const $ = (id) => document.getElementById(id);
const ctrl = new Controleur(config);

let infosAppairage = null; // { mode, idTV, secret, empreinteTV? , code? }
let nomAppairage = "";

// ---------- Utilitaires ----------

function montrer(id) {
  for (const e of document.querySelectorAll(".ecran")) e.hidden = e.id !== id;
  document.body.dataset.ecran = id;
  if (id !== "ecran-telecommande") document.body.classList.remove("clavier");
  window.scrollTo(0, 0);
}

const echapper = (s) => String(s).replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

const reglageLocal = {
  lire(cle) { try { return localStorage.getItem(cle); } catch { return null; } },
  ecrire(cle, v) { try { localStorage.setItem(cle, v); } catch {} },
};

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
  montrer("ecran-telecommande");
  majEtat({ etat: ctrl.etat });
  ctrl.connecter();
}

function majEtat({ etat, raison, prochainEssaiMs }) {
  const tv = ctrl.tvActive;
  $("nom-tv").textContent = tv ? tv.nom : "TV";
  $("voyant").dataset.etat = etat;
  $("ecran-telecommande").dataset.etat = etat;
  let texte = TEXTES_ETAT[etat] || "";
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
  $("bandeau-etat").hidden = !bandeau;
  if (bandeau) $("bandeau-texte").textContent = bandeau;
}

ctrl.addEventListener("etat", (e) => majEtat(e.detail));
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
      : "La télécommande est plus récente que l'app de la TV. Installez la dernière version d'iBiProjo sur la TV.",
    ok: recharger ? "Recharger" : "OK",
    annuler: recharger ? "Plus tard" : null,
  });
  if (recharger && ok) rechargerAvecMiseAJour();
});

// ---------- Télécommande : touches ----------

const REPETITION_DELAI = 380;
const REPETITION_INTERVALLE = 110;

function brancherTouche(el) {
  const k = el.dataset.k;
  const repete = el.hasAttribute("data-repete") || el.classList.contains("dpad-dir");
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
for (const el of document.querySelectorAll("#ecran-telecommande [data-k]")) brancherTouche(el);

// Pavé directionnel ou pavé tactile (choix mémorisé sur ce téléphone)
function modeNav(pave) {
  $("dpad").hidden = pave;
  $("pave").hidden = !pave;
  $("btn-mode-nav").setAttribute("aria-pressed", String(pave));
  $("btn-mode-nav").setAttribute("aria-label", pave ? "Pavé directionnel" : "Pavé tactile");
  $("btn-mode-nav").querySelector("use").setAttribute("href", pave ? "#i-croix-dir" : "#i-pave");
  reglageLocal.ecrire("ibiprojo-mode-nav", pave ? "pave" : "dpad");
}
modeNav(reglageLocal.lire("ibiprojo-mode-nav") === "pave");
$("btn-mode-nav").addEventListener("click", () => { vibrer(); modeNav($("dpad").hidden === false); });

// Pavé tactile : un pas toutes les SEUIL px dans la direction dominante, une touche courte = OK
(() => {
  const pave = $("pave");
  const point = pave.querySelector(".pave-point");
  const SEUIL = 46;
  let suivi = null;
  const position = (e) => {
    const r = pave.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  pave.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    try { pave.setPointerCapture(e.pointerId); } catch {}
    const p = position(e);
    suivi = { id: e.pointerId, ref: p, depart: p, t: performance.now(), pas: 0, max: 0 };
    point.style.transform = `translate(${p.x}px, ${p.y}px)`;
    pave.classList.add("actif");
  });
  pave.addEventListener("pointermove", (e) => {
    if (!suivi || e.pointerId !== suivi.id) return;
    const p = position(e);
    point.style.transform = `translate(${p.x}px, ${p.y}px)`;
    suivi.max = Math.max(suivi.max, Math.hypot(p.x - suivi.depart.x, p.y - suivi.depart.y));
    const dx = p.x - suivi.ref.x, dy = p.y - suivi.ref.y;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < SEUIL) return;
    if (Math.abs(dx) > Math.abs(dy)) {
      ctrl.touche(dx > 0 ? "right" : "left");
      suivi.ref = { x: suivi.ref.x + Math.sign(dx) * SEUIL, y: p.y };
    } else {
      ctrl.touche(dy > 0 ? "down" : "up");
      suivi.ref = { x: p.x, y: suivi.ref.y + Math.sign(dy) * SEUIL };
    }
    suivi.pas++;
    vibrer(8);
  });
  const fin = (e) => {
    if (!suivi || e.pointerId !== suivi.id) return;
    if (e.type === "pointerup" && suivi.pas === 0 && suivi.max < 12 && performance.now() - suivi.t < 400) {
      ctrl.touche("ok");
      vibrer(14);
      vibrerIOS();
    }
    suivi = null;
    pave.classList.remove("actif");
  };
  pave.addEventListener("pointerup", fin);
  pave.addEventListener("pointercancel", fin);
})();

// ---------- Télécommande : champ texte ----------

const champ = $("champ-texte");
const saisie = $("saisie");
const TYPES_INPUT = {
  texte: ["text", "text"], email: ["email", "email"], motdepasse: ["password", "text"],
  nombre: ["text", "decimal"], url: ["url", "url"], recherche: ["search", "search"],
};
let typeChamp = "texte";
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
}

function curseur() {
  try { return Number.isInteger(champ.selectionEnd) ? champ.selectionEnd : undefined; } catch { return undefined; }
}

champ.addEventListener("input", () => ctrl.texte(champ.value, curseur()));
// Toucher le champ (même déjà « focus » sans clavier, cas d'iOS) ouvre le clavier : fin de l'invitation
const champTouche = () => {
  document.body.classList.add("clavier");
  saisie.classList.remove("invite");
  if (saisie.classList.contains("actif")) $("saisie-aide").hidden = true;
};
champ.addEventListener("focus", champTouche);
champ.addEventListener("click", champTouche);
champ.addEventListener("blur", () => document.body.classList.remove("clavier"));
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

ctrl.addEventListener("focus_text", ({ detail }) => {
  typeChamp = detail.type;
  afficherMdp = false; // mot de passe toujours masqué par défaut
  appliquerType();
  champ.enterKeyHint = detail.action;
  champ.value = detail.s;
  saisie.classList.add("actif");
  const aide = $("saisie-aide");
  aide.textContent = detail.type === "motdepasse" ? "Mot de passe demandé sur la TV" : "Champ texte actif sur la TV";
  aide.hidden = false;
  vibrer(15);
  // Ouverture automatique du clavier. Android/Chrome : focus() suffit en général.
  // iOS : focus() sans geste de l'utilisateur n'ouvre pas le clavier → invitation à toucher le champ.
  try { champ.focus({ preventScroll: true }); } catch {}
  setTimeout(() => {
    if (!saisie.classList.contains("actif")) return;
    const vv = window.visualViewport;
    const clavierVisible = document.activeElement === champ &&
      (!estIOS() || (vv && vv.height < window.innerHeight * 0.85));
    if (!clavierVisible) {
      document.body.classList.remove("clavier"); // pas de clavier : on garde toute la télécommande
      saisie.classList.add("invite");
      aide.textContent = "Touchez le champ pour ouvrir le clavier";
      aide.hidden = false;
    }
  }, 450);
});

function finSaisie() {
  saisie.classList.remove("actif", "invite");
  $("saisie-aide").hidden = true;
  if (document.activeElement === champ) champ.blur();
  champ.value = "";
  typeChamp = "texte";
  afficherMdp = false;
  appliquerType();
  champ.enterKeyHint = "done";
}
ctrl.addEventListener("focus_lost", finSaisie);
appliquerType();

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
      <span class="tv-detail">${tv.id === ctrl.active ? "Pilotée" : "Toucher pour piloter"}${tv.versionApp ? ` · iBiProjo ${echapper(tv.versionApp)}` : ""}</span></span>`;
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
$("feuille-tvs").addEventListener("click", (e) => { if (e.target === $("feuille-tvs")) fermerFeuille(); });
$("btn-ajouter-tv").addEventListener("click", () => { fermerFeuille(); montrerAccueil(); });
ctrl.addEventListener("tvs", () => {
  if (!$("feuille-tvs").hidden) majListeTVs();
  const tv = ctrl.tvActive;
  if (tv) $("nom-tv").textContent = tv.nom;
});
addEventListener("keydown", (e) => {
  if (e.key === "Escape") { fermerFeuille(); }
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
