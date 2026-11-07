// Petites aides DOM : éléments créés sans innerHTML (les textes reçus de la TV restent du texte).

/** h("button", {class: "x", hidden: false}, "texte", enfant…) ; false / null : attribut absent. */
export function h(tag, attrs = {}, ...enfants) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === false || v == null) continue;
    if (k === "class") el.className = v;
    else el.setAttribute(k, v === true ? "" : String(v));
  }
  for (const e of enfants) if (e != null && e !== false) el.append(e);
  return el;
}

/** Icône du sprite de index.html (<svg class="ic"><use href="#…"/></svg>). */
export function icone(nom, classe = "ic") {
  const ns = "http://www.w3.org/2000/svg";
  const s = document.createElementNS(ns, "svg");
  s.setAttribute("class", classe);
  s.setAttribute("aria-hidden", "true");
  const u = document.createElementNS(ns, "use");
  u.setAttribute("href", `#${nom}`);
  s.append(u);
  return s;
}
