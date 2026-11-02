// Pilotage (applis et réglages) : normalisation, regroupement, images à demander, limiteur, décodage.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import {
  lireFonctions, normaliserApps, normaliserImage, cleImage, imagesADemander, paquets,
  normaliserReglage, normaliserReglages, grouperReglages, signatureReglages, proportion, limiteur, Attentes,
} from "../../remote-pwa/js/pilotage.js";
import { decoder } from "../../remote-pwa/js/codec.js";

const PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==";

test("fonctions annoncées : seules les connues ; absent = aucune", () => {
  assert.deepEqual(lireFonctions(["reglages", "apps", "autre", 3]), ["apps", "reglages"]);
  assert.deepEqual(lireFonctions(["apps"]), ["apps"]);
  assert.deepEqual(lireFonctions(undefined), []);
  assert.deepEqual(lireFonctions("apps"), []);
});

test("apps : entrées invalides et doublons écartés, favoris en premier (ordre conservé)", () => {
  const l = normaliserApps([
    { id: "b", nom: "B", favori: false, maj: 2 },
    { id: "a", nom: "A", favori: true, maj: 1.7 },
    { id: "b", nom: "Doublon", favori: true },
    { nom: "Sans id" }, null, { id: "" }, { id: 4 },
    { id: "c", nom: "  ", favori: "oui" },
    { id: "d", nom: "D", favori: true, maj: "x" },
  ]);
  assert.deepEqual(l, [
    { id: "a", nom: "A", favori: true, maj: 1 },
    { id: "d", nom: "D", favori: true, maj: 0 },
    { id: "b", nom: "B", favori: false, maj: 2 },
    { id: "c", nom: "c", favori: false, maj: 0 },
  ]);
  assert.equal(normaliserApps({}), null);
  assert.deepEqual(normaliserApps([]), []);
});

test("app_image : URL data: webp/png/jpeg seulement, forme banniere | icone", () => {
  const ok = { id: "com.a", maj: 5, forme: "banniere", img: PNG };
  assert.deepEqual(normaliserImage(ok), ok);
  assert.equal(normaliserImage({ ...ok, img: "data:image/webp;base64,UklGR" }).forme, "banniere");
  for (const mauvais of [
    { ...ok, forme: "carre" }, { ...ok, img: "https://exemple.fr/a.png" }, { ...ok, img: "data:image/svg+xml;base64,PHN2Zz4=" },
    { ...ok, img: "data:image/png;base64,<script>" }, { ...ok, maj: "5" }, { ...ok, id: "" }, null,
  ]) assert.equal(normaliserImage(mauvais), null);
});

test("images : clé id|maj, seules les manquantes et non demandées, paquets de 100", () => {
  assert.equal(cleImage("com.a", 7), "com.a|7");
  const liste = [{ id: "a", maj: 1 }, { id: "b", maj: 2 }, { id: "c", maj: 3 }, { id: "d", maj: 4 }];
  const connues = new Map([["a|1", {}], ["b|1", {}]]); // b connue mais dans une version plus ancienne
  assert.deepEqual(imagesADemander(liste, connues), ["b", "c", "d"]);
  assert.deepEqual(imagesADemander(liste, connues, new Set(["c|3"])), ["b", "d"]);
  const ids = Array.from({ length: 250 }, (_, i) => `id${i}`);
  const p = paquets(ids);
  assert.deepEqual(p.map((x) => x.length), [100, 100, 50]);
  assert.deepEqual(p.flat(), ids);
  assert.deepEqual(paquets([]), []);
});

test("réglages : chaque type normalisé ; invalides écartés", () => {
  assert.deepEqual(normaliserReglage({ cle: "volume", type: "curseur", groupe: "Son", titre: "Volume", min: 0, max: 15, valeur: 22 }),
    { cle: "volume", type: "curseur", groupe: "Son", titre: "Volume", detail: "", actif: true, min: 0, max: 15, valeur: 15 });
  assert.equal(normaliserReglage({ cle: "v", type: "curseur", min: 5, max: 5, valeur: 5 }), null);
  assert.equal(normaliserReglage({ cle: "v", type: "curseur", max: 5 }), null);
  assert.equal(normaliserReglage({ cle: "m", type: "interrupteur", valeur: "true" }).valeur, false);
  assert.equal(normaliserReglage({ cle: "m", type: "interrupteur", valeur: true, actif: false }).actif, false);
  const choix = normaliserReglage({
    cle: "design", type: "choix", valeur: "pastel",
    options: [{ id: "verre", nom: "Verre", detail: "Flou" }, { id: "pastel", nom: "Pastel" }, { id: "pastel" }, { nom: "?" }, { id: 100, nom: "100 ms" }],
  });
  assert.deepEqual(choix.options, [{ id: "verre", nom: "Verre", detail: "Flou" }, { id: "pastel", nom: "Pastel" }, { id: 100, nom: "100 ms" }]);
  assert.equal(choix.valeur, "pastel");
  assert.equal(normaliserReglage({ cle: "c", type: "choix", valeur: "x", options: [{ id: "a" }] }).valeur, null);
  assert.equal(normaliserReglage({ cle: "c", type: "choix", options: [] }), null);
  const b = normaliserReglage({ cle: "maj", type: "boutons", titre: "iBiTV", progression: 1.4,
    boutons: [{ id: "verifier", nom: "Rechercher" }, { id: "installer", nom: "Installer", principal: true }, { nom: "x" }] });
  assert.deepEqual(b.boutons, [{ id: "verifier", nom: "Rechercher", principal: false }, { id: "installer", nom: "Installer", principal: true }]);
  assert.equal(b.progression, 1);
  assert.equal(normaliserReglage({ cle: "maj", type: "boutons" }).progression, undefined);
  for (const mauvais of [{ type: "interrupteur" }, { cle: "x", type: "liste" }, { cle: "", type: "interrupteur" }, null, "x"]) {
    assert.equal(normaliserReglage(mauvais), null);
  }
  assert.deepEqual(normaliserReglages([{ cle: "a", type: "interrupteur" }, { cle: "a", type: "interrupteur" }, { cle: "b", type: "?" }]).map((i) => i.cle), ["a"]);
  assert.equal(normaliserReglages("x"), null);
});

test("réglages : sections par groupe dans l'ordre reçu", () => {
  const it = (cle, groupe) => ({ cle, groupe });
  const s = grouperReglages([it("volume", "Son"), it("muet", "Son"), it("design", "Affichage"), it("eq", "Son"), it("maj", "Mises à jour")]);
  assert.deepEqual(s.map((x) => [x.groupe, x.items.map((i) => i.cle)]), [
    ["Son", ["volume", "muet", "eq"]], ["Affichage", ["design"]], ["Mises à jour", ["maj"]],
  ]);
});

test("réglages : signature de structure (valeurs, détails et progression n'en font pas partie)", () => {
  const items = normaliserReglages([
    { cle: "volume", type: "curseur", groupe: "Son", min: 0, max: 15, valeur: 3 },
    { cle: "maj", type: "boutons", groupe: "MAJ", detail: "à jour", boutons: [{ id: "verifier", nom: "Rechercher" }] },
  ]);
  const autres = normaliserReglages([
    { cle: "volume", type: "curseur", groupe: "Son", min: 0, max: 15, valeur: 9, titre: "Vol." },
    { cle: "maj", type: "boutons", groupe: "MAJ", detail: "vérification…", progression: 0.4, actif: false, boutons: [{ id: "verifier", nom: "Rechercher" }] },
  ]);
  assert.equal(signatureReglages(items), signatureReglages(autres));
  const plus = structuredClone(items);
  plus[1].boutons.push({ id: "installer", nom: "Installer", principal: true });
  assert.notEqual(signatureReglages(items), signatureReglages(plus));
  assert.equal(proportion(5, 0, 10), 0.5);
  assert.equal(proportion(-1, 0, 10), 0);
});

/** Horloge factice pour le limiteur et les attentes. */
function horloge() {
  const h = { t: 0, minuteries: [] };
  h.maintenant = () => h.t;
  h.plusTard = (f, ms) => { const m = { f, quand: h.t + ms }; h.minuteries.push(m); return m; };
  h.annuler = (m) => { h.minuteries = h.minuteries.filter((x) => x !== m); };
  h.avancer = (ms) => {
    const fin = h.t + ms;
    for (;;) {
      const m = h.minuteries.filter((x) => x.quand <= fin).sort((x, y) => x.quand - y.quand)[0];
      if (!m) break;
      h.t = m.quand;
      h.annuler(m);
      m.f();
    }
    h.t = fin;
  };
  return h;
}

test("limiteur : au plus un envoi toutes les 150 ms pendant le glissé, valeur finale au relâcher", () => {
  const h = horloge();
  const envois = [];
  const lim = limiteur((v) => envois.push([h.t, v]), 150, h);
  lim.appeler(1);               // t=0 : tout de suite
  h.avancer(40); lim.appeler(2);
  h.avancer(40); lim.appeler(3);
  h.avancer(40); lim.appeler(4);
  h.avancer(30);                // t=150 : la dernière en attente
  h.avancer(10); lim.appeler(5);
  h.avancer(200);               // t=300 : 5 (puis t=360)
  assert.deepEqual(envois, [[0, 1], [150, 4], [300, 5]]);
  lim.terminer(5);              // déjà envoyée : rien de plus
  assert.equal(envois.length, 3);
  lim.terminer(5, true);        // sauf si la TV a changé la valeur entre-temps
  assert.deepEqual(envois.at(-1), [360, 5]);
  envois.pop();
  h.avancer(20); lim.appeler(6); // t=380, 80 ms après le dernier envoi : en attente…
  h.avancer(10); lim.terminer(7); // … remplacée par la valeur finale, envoyée aussitôt
  h.avancer(500);
  assert.deepEqual(envois.slice(3), [[390, 7]]);
});

test("attentes : la valeur envoyée reste affichée jusqu'à sa confirmation ou l'échéance", () => {
  const h = horloge();
  const a = new Attentes(1500, h.maintenant);
  assert.ok(a.accepter("volume", 3));
  a.noter("volume", 9);
  assert.equal(a.accepter("volume", 5), false); // réponse en retard d'un envoi intermédiaire
  assert.equal(a.restant("volume"), 1500);
  assert.ok(a.accepter("volume", 9));           // confirmée
  assert.ok(a.accepter("volume", 4));           // plus rien n'attend : changement venu de la TV
  a.noter("muet", true);
  h.avancer(1500);
  assert.ok(a.accepter("muet", false));         // échéance : on se recale sur la TV
});

test("decoder() : messages du pilotage", () => {
  const apps = decoder(JSON.stringify({ v: 1, t: "apps", liste: [{ id: "com.a", nom: "A", favori: false, maj: 1 }, { id: 1 }] }));
  assert.deepEqual(apps.liste, [{ id: "com.a", nom: "A", favori: false, maj: 1 }]);
  assert.equal(decoder('{"v":1,"t":"apps"}'), null);
  const img = decoder(JSON.stringify({ v: 1, t: "app_image", id: "com.a", maj: 1, forme: "icone", img: PNG, autre: "x" }));
  assert.deepEqual(img, { v: 1, t: "app_image", id: "com.a", maj: 1, forme: "icone", img: PNG });
  assert.equal(decoder(JSON.stringify({ v: 1, t: "app_image", id: "com.a", maj: 1, forme: "icone", img: "javascript:1" })), null);
  assert.equal(decoder('{"v":1,"t":"app_ouverte","id":"com.a","ok":"oui"}').ok, false);
  assert.equal(decoder('{"v":1,"t":"app_ouverte","ok":true}'), null);
  const r = decoder(JSON.stringify({ v: 1, t: "reglages", items: [{ cle: "muet", type: "interrupteur", valeur: true }, { cle: "x", type: "?" }] }));
  assert.deepEqual(r.items.map((i) => i.cle), ["muet"]);
  assert.equal(decoder('{"v":1,"t":"reglages","items":{}}'), null);
  // Une image de 48 Ko tient dans un message (< 64 Ko)
  const grosse = "data:image/webp;base64," + "A".repeat(48 * 1024);
  assert.ok(decoder(JSON.stringify({ v: 1, t: "app_image", id: "com.a", maj: 1, forme: "banniere", img: grosse })));
});
