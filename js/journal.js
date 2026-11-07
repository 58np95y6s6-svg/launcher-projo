// Journal minimal : uniquement des TYPES d'événements (liste fermée) et des codes courts.
// Jamais de texte saisi, de secret, de clé ni de SDP : la fonction n'accepte rien d'autre.

const EVENEMENTS = new Set([
  "demarrage", "signalisation_ouverte", "signalisation_fermee", "signalisation_erreur",
  "connexion", "connectee", "deconnectee", "injoignable", "authentification_echouee",
  "appairage_demande", "appairage_accepte", "appairage_refuse", "revocation",
  "version_incompatible", "tv_oubliee", "mise_a_jour_disponible", "sw_erreur",
  "appli_ouverte", "appli_refusee", "reglage",
  "lien_envoye", "lien_refuse", // jamais l'URL : seulement la raison d'un refus
]);
// Codes courts acceptés en complément (raisons, états) : lettres, chiffres et « _ » seulement.
const RE_CODE = /^[a-z0-9_]{1,24}$/;

export function journal(evenement, code) {
  if (!EVENEMENTS.has(evenement)) return; // événement inconnu : on n'écrit rien
  const suffixe = typeof code === "string" && RE_CODE.test(code) ? ` ${code}` : "";
  const ligne = `[iBiTV] ${evenement}${suffixe}`;
  if (globalThis.IBITV_CONFIG?.debug) console.debug(ligne);
  else console.info(ligne);
}
