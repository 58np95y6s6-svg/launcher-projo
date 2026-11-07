// Envoyer sur la TV (liens) : adresses, messages, état du VPN, résultats, récents, raccourci iOS.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import {
  lireLien, urlValable, hoteDe, adresseLisible, titreDeduit, libelleLien, teinteDe, champsLienOuvrir,
  normaliserLiens, normaliserLienOuvert, etatVpn, interpreterResultat, normaliserRecents, ajouterRecent,
  dateRelative, detailRecent, adresseAChange, MAX_RECENTS,
} from "../../remote-pwa/js/liens.js";
import { decoder } from "../../remote-pwa/js/codec.js";
import { lireFonctions } from "../../remote-pwa/js/pilotage.js";
import { journal } from "../../remote-pwa/js/journal.js";

test("lireLien : adresses complètes gardées, domaine sans schéma complété en https://", () => {
  assert.equal(lireLien("arte.tv/x"), "https://arte.tv/x");
  assert.equal(lireLien("www.arte.tv"), "https://www.arte.tv/");
  assert.equal(lireLien("youtu.be/dQw4w9WgXcQ"), "https://youtu.be/dQw4w9WgXcQ");
  assert.equal(lireLien("arte.tv:8080/x?a=1#b"), "https://arte.tv:8080/x?a=1#b");
  assert.equal(lireLien("https://www.youtube.com/watch?v=abc"), "https://www.youtube.com/watch?v=abc");
  assert.equal(lireLien("  HTTPS://Example.COM \n"), "https://example.com/");
  assert.equal(lireLien("http://192.168.1.2:8080/v.mp4"), "http://192.168.1.2:8080/v.mp4");
  assert.equal(lireLien("http://nas.local/films/a.mkv"), "http://nas.local/films/a.mkv");
});

test("lireLien : adresse trouvée dans un texte partagé, ponctuation finale retirée", () => {
  assert.equal(lireLien("Regarde ça : https://youtu.be/abc !"), "https://youtu.be/abc");
  assert.equal(lireLien("Voir (https://fr.wikipedia.org/wiki/Paris_(France))."), "https://fr.wikipedia.org/wiki/Paris_(France)");
  assert.equal(lireLien("deux https://a.fr/1 https://b.fr/2"), "https://a.fr/1");
  assert.equal(lireLien("Voir arte.tv/x demain"), null, "domaine sans schéma : seulement seul dans le champ");
});

test("lireLien : autres schémas, e-mails, mots, adresses trop longues refusés", () => {
  for (const t of [
    "", "   ", "bonjour", "arte", "a.b", "1.2.3.4", "localhost:8080", "192.168.1.2/x", "paul@gmail.com",
    "javascript:alert(1)", "file:///etc/passwd", "intent://x#Intent;end", "content://a/b", "ftp://a.fr",
    "mailto:a@b.fr", "data:text/html,<b>x</b>", "http://", "https://", "http:example.com",
    "https://a.fr/" + "x".repeat(2048), null, undefined, 42,
  ]) assert.equal(lireLien(t), null, String(t).slice(0, 40));
  assert.equal(lireLien("https://a.fr/" + "x".repeat(2000)).length, 2013);
  assert.equal(urlValable("https://a.fr/" + "x".repeat(2035)), "https://a.fr/" + "x".repeat(2035)); // 2048
  assert.equal(urlValable("https://a.fr/" + "x".repeat(2036)), null);
});

test("hôte, adresse lisible, titre tiré de l'adresse, libellé d'un récent", () => {
  assert.equal(hoteDe("https://www.arte.tv/fr/"), "arte.tv");
  assert.equal(hoteDe("https://m.youtube.com/watch?v=1"), "m.youtube.com");
  assert.equal(hoteDe("pas une url"), "");
  assert.equal(adresseLisible("https://www.arte.tv/fr/videos/"), "arte.tv/fr/videos");
  assert.equal(adresseLisible("https://youtu.be/abc?t=4"), "youtu.be/abc?t=4");
  assert.equal(adresseLisible("http://nas.local:8080/%C3%A9t%C3%A9.mp4"), "nas.local:8080/été.mp4");
  assert.equal(titreDeduit("https://www.arte.tv/fr/videos/RC-014035/les-grands-documentaires/"), "Les grands documentaires");
  assert.equal(titreDeduit("https://www.france.tv/f2/123456-emission-du-jeudi-7-novembre-2026.html"), "Emission du jeudi 7 novembre");
  assert.equal(titreDeduit("http://nas.local/films/conference-keynote.mp4"), "conference-keynote.mp4");
  for (const u of ["https://www.youtube.com/watch?v=abc", "https://youtu.be/dQw4w9WgXcQ", "https://a.fr/", "https://a.fr/RC-014035", "https://netflix.com/watch/81234567"]) {
    assert.equal(titreDeduit(u), "", u);
  }
  assert.equal(libelleLien({ url: "https://youtu.be/abc", titre: "Bande-annonce" }), "Bande-annonce");
  assert.equal(libelleLien({ url: "https://www.arte.tv/fr/videos/RC-1/les-grands-documentaires/" }), "Les grands documentaires");
  assert.equal(libelleLien({ url: "https://www.youtube.com/watch?v=abc" }), "youtube.com/watch?v=abc");
  assert.equal(teinteDe("youtube.com"), 0);
  assert.equal(teinteDe("youtu.be"), 0);
  assert.ok(teinteDe("arte.tv") >= 0 && teinteDe("arte.tv") < 360);
  assert.equal(teinteDe("arte.tv"), teinteDe("arte.tv"));
});

test("lien_ouvrir : url, puis titre / sansVpn / vpnDabord seulement s'ils servent", () => {
  assert.deepEqual(champsLienOuvrir("https://a.fr/"), { url: "https://a.fr/" });
  assert.deepEqual(champsLienOuvrir("https://a.fr/", { sansVpn: true }), { url: "https://a.fr/", sansVpn: true });
  assert.deepEqual(champsLienOuvrir("https://a.fr/", { vpnDabord: true, sansVpn: false, titre: "" }), { url: "https://a.fr/", vpnDabord: true });
  assert.deepEqual(champsLienOuvrir("https://a.fr/", { titre: " Un\u0007 titre\n" }), { url: "https://a.fr/", titre: "Un titre" });
  assert.equal(champsLienOuvrir("https://a.fr/", { titre: "x".repeat(300) }).titre.length, 200);
  assert.deepEqual(champsLienOuvrir("https://a.fr/", { sansVpn: "oui" }), { url: "https://a.fr/" });
});

test("liens : état normalisé (VPN, appli des pages, appli VPN, adresse du raccourci)", () => {
  assert.deepEqual(normaliserLiens({ vpn: true, seulementVpn: true, appliPages: "VYPN", appliVpn: "VYPN",
    raccourci: { adresse: "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD" } }), {
    vpn: true, seulementVpn: true, appliPages: "VYPN", appliVpn: "VYPN",
    raccourci: { adresse: "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD" },
  });
  assert.deepEqual(normaliserLiens({}), { vpn: false, seulementVpn: false, appliPages: "" });
  assert.deepEqual(normaliserLiens({ vpn: "oui", appliPages: 3, appliVpn: "  ", raccourci: { adresse: "javascript:alert(1)" } }),
    { vpn: false, seulementVpn: false, appliPages: "" });
  assert.equal(normaliserLiens({ raccourci: "http://a" }).raccourci, undefined);
  assert.equal(normaliserLiens(null), null);
});

test("lien_ouvert : url obligatoire, raison seulement si refusé (code court)", () => {
  assert.deepEqual(normaliserLienOuvert({ url: "https://a.fr/", ok: true, ouvertDans: "VYPN" }), { url: "https://a.fr/", ok: true, ouvertDans: "VYPN" });
  assert.deepEqual(normaliserLienOuvert({ url: "https://a.fr/", ok: false, raison: "vpn_coupe" }), { url: "https://a.fr/", ok: false, raison: "vpn_coupe" });
  assert.deepEqual(normaliserLienOuvert({ url: "https://a.fr/", ok: true, raison: "vpn_coupe" }), { url: "https://a.fr/", ok: true });
  assert.deepEqual(normaliserLienOuvert({ url: "https://a.fr/", ok: "true", raison: "Pas <b>ça</b>" }), { url: "https://a.fr/", ok: false });
  for (const m of [null, {}, { url: "" }, { url: 5, ok: true }]) assert.equal(normaliserLienOuvert(m), null);
});

test("decoder() : liens et lien_ouvert ; fonction « liens » annoncée", () => {
  assert.deepEqual(decoder(JSON.stringify({ v: 1, t: "liens", vpn: false, seulementVpn: true, appliPages: "Silk", autre: 1 })),
    { v: 1, t: "liens", vpn: false, seulementVpn: true, appliPages: "Silk" });
  assert.deepEqual(decoder(JSON.stringify({ v: 1, t: "lien_ouvert", url: "https://a.fr/", ok: false, raison: "attente_vpn" })),
    { v: 1, t: "lien_ouvert", url: "https://a.fr/", ok: false, raison: "attente_vpn" });
  assert.equal(decoder(JSON.stringify({ v: 1, t: "lien_ouvert", ok: true })), null);
  assert.deepEqual(lireFonctions(["apps", "liens", "reglages"]), ["apps", "reglages", "liens"]);
  assert.deepEqual(lireFonctions(["liens"]), ["liens"]);
});

test("ligne d'état du VPN", () => {
  assert.equal(etatVpn(null), null);
  const actif = etatVpn({ vpn: true, seulementVpn: true, appliPages: "VYPN" });
  assert.equal(actif.etat, "actif");
  assert.equal(actif.texte, "VPN de la TV actif · les pages s'ouvrent dans VYPN");
  assert.deepEqual(actif.morceaux.filter(([, gras]) => gras).map(([t]) => t), ["actif", "VYPN"]);
  assert.equal(etatVpn({ vpn: true, seulementVpn: false, appliPages: "" }).texte, "VPN de la TV actif");
  const coupe = etatVpn({ vpn: false, seulementVpn: true, appliPages: "VYPN" });
  assert.deepEqual([coupe.etat, coupe.texte], ["coupe", "VPN de la TV coupé · la TV demandera de l'allumer"]);
  const libre = etatVpn({ vpn: false, seulementVpn: false, appliPages: "VYPN" });
  assert.deepEqual([libre.etat, libre.texte], ["libre", "VPN de la TV coupé"]);
});

test("résultat d'un envoi : confirmation, feuille VPN, attente, messages d'erreur", () => {
  const o = { nomTV: "Salon" };
  assert.deepEqual(interpreterResultat({ url: "u", ok: true, ouvertDans: "VYPN" }, o), { type: "ok", texte: "Envoyé sur Salon · ouverture dans VYPN" });
  assert.deepEqual(interpreterResultat({ url: "u", ok: true }, o), { type: "ok", texte: "Envoyé sur Salon" });
  assert.deepEqual(interpreterResultat({ url: "u", ok: false, raison: "vpn_coupe" }, o), { type: "vpn_coupe" });
  assert.deepEqual(interpreterResultat({ url: "u", ok: false, raison: "attente_vpn" }, o),
    { type: "attente", texte: "Allume le VPN sur la TV : le lien s'ouvrira ensuite" });
  const textes = new Set();
  for (const raison of ["delai_vpn", "pas_d_appli_vpn", "remplace", "invalide", "aucune_appli", "autre", undefined]) {
    const r = interpreterResultat({ url: "u", ok: false, raison }, o);
    assert.equal(r.type, "erreur", String(raison));
    assert.ok(r.texte.length > 10);
    textes.add(r.texte);
  }
  assert.equal(textes.size, 6, "un message propre à chaque raison connue");
  assert.match(interpreterResultat({ url: "u", ok: false, raison: "aucune_appli" }, o).texte, /Aucune appli de la TV/);
});

test("récents : en tête, sans doublon, 20 au plus, titre gardé, données invalides écartées", () => {
  let l = [];
  for (let i = 0; i < 25; i++) l = ajouterRecent(l, { url: `https://a.fr/${i}`, date: i });
  assert.equal(l.length, MAX_RECENTS);
  assert.equal(l[0].url, "https://a.fr/24");
  assert.equal(l.at(-1).url, "https://a.fr/5");
  l = ajouterRecent(l, { url: "https://a.fr/10", date: 99, ouvertDans: "VYPN" });
  assert.equal(l.length, MAX_RECENTS);
  assert.deepEqual(l[0], { url: "https://a.fr/10", date: 99, ouvertDans: "VYPN" });
  assert.equal(l.filter((e) => e.url === "https://a.fr/10").length, 1);
  l = ajouterRecent([{ url: "https://b.fr/", date: 1, titre: "Titre" }], { url: "https://b.fr/", date: 2 });
  assert.deepEqual(l, [{ url: "https://b.fr/", date: 2, titre: "Titre" }]);
  assert.deepEqual(normaliserRecents([
    { url: "https://a.fr/", date: 1, ouvertDans: "  VLC " }, { url: "https://a.fr/", date: 2 }, { url: "javascript:x", date: 1 },
    { url: "https://b.fr/" }, null, "https://c.fr/", { url: "https://d.fr/", date: 3, titre: 7 },
  ]), [{ url: "https://a.fr/", date: 1, ouvertDans: "VLC" }, { url: "https://d.fr/", date: 3 }]);
  assert.deepEqual(normaliserRecents("x"), []);
});

test("détail d'un récent : « hôte · ouvert dans X · date »", () => {
  const m = new Date(2026, 10, 7, 15, 0).getTime(); // samedi 7 novembre 2026, 15 h
  assert.equal(dateRelative(m - 5000, m), "à l'instant");
  assert.equal(dateRelative(m + 5000, m), "à l'instant");
  assert.equal(dateRelative(m - 5 * 60000, m), "il y a 5 min");
  assert.equal(dateRelative(new Date(2026, 10, 7, 9, 5).getTime(), m), "à 09:05");
  assert.equal(dateRelative(new Date(2026, 10, 6, 23, 50).getTime(), m), "hier");
  assert.equal(dateRelative(new Date(2026, 10, 2, 9, 0).getTime(), m), "lundi");
  assert.equal(dateRelative(new Date(2026, 9, 20).getTime(), m), "20 oct.");
  assert.equal(dateRelative(new Date(2025, 10, 2).getTime(), m), "2 nov. 2025");
  assert.equal(detailRecent({ url: "https://www.arte.tv/x", ouvertDans: "VYPN", date: m - 1000 }, m), "arte.tv · ouvert dans VYPN · à l'instant");
  assert.equal(detailRecent({ url: "https://youtu.be/x", date: m - 86400000 }, m), "youtu.be · hier");
});

test("raccourci iOS : l'adresse a changé depuis la dernière copie", () => {
  const a = "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD";
  assert.equal(adresseAChange(a, null), false, "jamais copiée : rien à signaler");
  assert.equal(adresseAChange(a, a), false);
  assert.equal(adresseAChange(a.replace("42", "57"), a), true);
  assert.equal(adresseAChange(null, a), false, "raccourci désactivé : pas de bandeau");
});

test("journal : lien_envoye et lien_refuse <raison>, jamais l'URL", () => {
  const lignes = [];
  const orig = console.info;
  console.info = (...a) => lignes.push(a.join(" "));
  try {
    journal("lien_envoye");
    journal("lien_refuse", "vpn_coupe");
    journal("lien_refuse", "https://a.fr/secret");
  } finally { console.info = orig; }
  assert.deepEqual(lignes, ["[iBiTV] lien_envoye", "[iBiTV] lien_refuse vpn_coupe", "[iBiTV] lien_refuse"]);
});
