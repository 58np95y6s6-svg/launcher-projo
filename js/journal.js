// Journal minimal : uniquement des TYPES d'événements (liste fermée) et des codes courts.
// Jamais de texte saisi, de secret, de clé ni de SDP : la fonction n'accepte rien d'autre.

const EVENEMENTS = new Set([
  "demarrage", "signalisation_ouverte", "signalisation_fermee", "signalisation_erreur",
  "connexion", "connectee", "deconnectee", "injoignable", "authentification_echouee",
  "appairage_demande", "appairage_accepte", "appairage_refuse", "revocation",
  "version_incompatible", "tv_oubliee", "mise_a_jour_disponible", "sw_erreur",
]);
// Codes courts acceptés en complément (raisons, états) : lettres, chiffres et « _ » seulement.
const RE_CODE = /^[a-z0-9_]{1,24}$/;

export function journal(evenement, code) {
  if (!EVENEMENTS.has(evenement)) return; // événement inconnu : on n'écrit rien
  const suffixe = typeof code === "string" && RE_CODE.test(code) ? ` ${code}` : "";
  const ligne = `[iBiProjo] ${evenement}${suffixe}`;
  if (globalThis.IBIPROJO_CONFIG?.debug) console.debug(ligne);
  else console.info(ligne);
}
