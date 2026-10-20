// Invitation à installer la PWA : beforeinstallprompt (Android/Chrome), instructions sur iOS.

export const estIOS = () =>
  /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

export const estInstallee = () =>
  matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;

let invitation = null; // événement beforeinstallprompt différé

export function initialiserInstallation() {
  addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    invitation = e;
    majCartes();
  });
  addEventListener("appinstalled", () => { invitation = null; majCartes(); });
  majCartes();
}

/** Remplit toutes les zones [data-install] selon la plateforme. */
export function majCartes() {
  for (const zone of document.querySelectorAll("[data-install]")) {
    zone.replaceChildren();
    zone.hidden = true;
    if (estInstallee()) continue;
    const icone = Object.assign(document.createElement("img"), { src: "icones/icone-192.png", alt: "" });
    const texte = document.createElement("p");
    if (invitation) {
      texte.innerHTML = "<strong>Installez la télécommande</strong><br>Elle s'ouvrira comme une app, en plein écran.";
      const bouton = Object.assign(document.createElement("button"), { type: "button", className: "btn-petit", textContent: "Installer" });
      bouton.addEventListener("click", async () => {
        const ev = invitation;
        if (!ev) return;
        invitation = null;
        try { await ev.prompt(); await ev.userChoice; } catch {}
        majCartes();
      });
      zone.append(icone, texte, bouton);
      zone.hidden = false;
    } else if (estIOS()) {
      texte.innerHTML =
        '<strong>Installez la télécommande</strong><br>Touchez <svg class="ic"><use href="#i-partager"/></svg> ' +
        "<strong>Partager</strong>, puis <strong>Sur l'écran d'accueil</strong> " +
        '<svg class="ic"><use href="#i-ajout-ecran"/></svg>.';
      zone.append(icone, texte);
      zone.hidden = false;
    }
  }
}
