# Projo — launcher Android TV pour projecteur

Écran d'accueil personnalisé, au design inspiré de l'Apple TV, pour le projecteur
**COOLiD C3** (Android 11, 1 Go de RAM). Léger, fluide, piloté à 100 % à la télécommande.

- Fond sombre avec une lueur colorée qui prend la couleur de l'app sélectionnée
- Salutation, grande horloge et date ; pastilles **Sources**, **Wi-Fi**, **Bluetooth**, **Réglages**
- Rangée **Favoris** (grandes cartes) et rangée **Toutes les apps**
- Appui long (ou touche Menu) sur une app : favoris, déplacer, masquer, infos de l'app
- Tout est enregistré sur le projecteur ; aucune connexion à un serveur

---

## 1. Installer sur le projecteur avec Downloader

1. Sur le projecteur, installe **Downloader** (éditeur AFTVnews) depuis le Play Store.
2. Ouvre Downloader et saisis l'adresse de l'APK :

   | Version | Adresse |
   |---|---|
   | **Stable** (dernière version publiée) | `https://github.com/58np95y6s6-svg/launcher-projo/releases/latest/download/launcher.apk` |
   | Test (dernière compilation de la branche de test) | `https://github.com/58np95y6s6-svg/launcher-projo/releases/download/test/launcher-test.apk` |

3. **Astuce lien court** : taper une longue adresse à la télécommande est pénible.
   Depuis ton téléphone, va sur **go.aftvnews.com**, colle l'adresse : tu obtiens un **code de
   quelques chiffres**. Dans Downloader, tape simplement ce code. Comme l'adresse « Stable » ne
   change jamais, le même code installera toujours la dernière version.
4. Si Android le demande, autorise Downloader à **installer des applications inconnues**,
   puis touche **Installer**.

> Les versions **test** et **stable** sont signées avec des clés différentes : pour passer de
> l'une à l'autre, désinstalle d'abord Projo (Réglages → Applications → Projo → Désinstaller).
> Entre deux versions stables, la mise à jour s'installe par-dessus, sans rien perdre.

## 2. Faire de Projo l'écran d'accueil

Projo se déclare comme « écran d'accueil ». Selon les appareils :

**a. Au premier appui sur le bouton Maison**, Android peut demander quel écran utiliser :
choisis **Projo** puis **Toujours**.

**b. Par les réglages** : Paramètres → Applications → **Applications par défaut** →
**Application d'accueil** → Projo. Raccourci : dans Projo, **maintiens OK sur la pastille
« Réglages »** → « Choisir l'écran d'accueil ». Cette fenêtre indique aussi quel accueil est
actuellement utilisé.

**c. Par ADB** (si le projecteur ne propose aucun choix) : active le débogage
(Paramètres → À propos → cliquer 7 fois sur la version, puis Options pour les développeurs →
Débogage USB / réseau), puis depuis un PC sur le même Wi-Fi :

```bash
adb connect <adresse-ip-du-projecteur>
adb shell cmd package set-home-activity fr.projo.launcher/.MainActivity
```

Pour revenir à l'accueil d'origine, remplace par l'activité du fabricant, ou désinstalle Projo.

**Limites connues** : sur certains projecteurs, le fabricant force son propre accueil : le
bouton Maison rouvre alors toujours l'accueil d'origine, même si Projo est choisi. Dans ce
cas, on peut désactiver l'accueil du fabricant par ADB
(`adb shell pm disable-user --user 0 <paquet-de-l-accueil>`), **à tes risques** : garde
toujours un moyen de revenir en arrière (`pm enable <paquet>`), et ne le fais pas sans
avoir vérifié que Projo fonctionne bien. Autre solution sans risque : une app qui
redirige le bouton Maison (ex. « Button Mapper »).

## 3. Utilisation

| Action | Résultat |
|---|---|
| Flèches | se déplacer ; la page défile toute seule |
| OK | ouvrir l'app |
| Maintenir OK (ou touche Menu) sur une app | ajouter/retirer des favoris, déplacer, masquer, infos |
| Retour | ne quitte jamais l'accueil |
| Maison (déjà sur l'accueil) | revient au premier favori |
| Fire TV (ouverture automatique activée) : maintenir Maison | accueil Amazon, sans retour forcé vers Projo jusqu'au prochain appui court sur Maison, la mise en veille, l'affichage de Projo, ou 10 min au plus |
| Fire TV, après une mise à jour de Projo | redémarrer le Fire TV (ou Réglages → Ouverture automatique → « Activer en un clic »), sinon Maison repasse par l'accueil Amazon |
| Maintenir OK sur « Sources » | choisir ce qu'ouvre la pastille (app HDMI du fabricant, etc.) |
| Maintenir OK sur « Réglages » | diagnostic : écran d'accueil par défaut, version |
| Carte « Apps masquées » (fin de la 2ᵉ rangée) | ré-afficher une app masquée |

**Favoris par défaut** (au premier lancement, s'ils sont installés) : Netflix, Prime Video,
Canal+, SmartTube, Twitch. Ils sont retrouvés par leur nom ; les paquets trouvés sont écrits
dans les journaux (`adb logcat -s Projo`).

**Sources (HDMI)** : Android n'a pas de commande standard. Projo essaie, dans l'ordre : ton
choix (maintenir OK sur « Sources »), l'entrée HDMI du système TV d'Android, puis une app ou un
écran dont le nom contient « HDMI », « Source » ou « Input ». À défaut, il ouvre les réglages
avec un message.

## 4. Publier une nouvelle version

### Une seule fois : créer ta clé de signature

Toutes les versions doivent être signées avec **la même clé**, sinon Android refuse la mise
à jour. On la crée une fois, puis on la donne à GitHub sous forme de « secrets ».

1. Sur un ordinateur avec Java installé (ou Android Studio), ouvre un terminal et tape :

   ```bash
   keytool -genkeypair -v -keystore projo-publication.jks -alias projo \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Il demande un **mot de passe** (note-le !) puis ton nom, etc. (tu peux tout laisser vide
   et confirmer par « oui »).
2. Convertis la clé en texte (base64) :
   - macOS / Linux : `base64 -i projo-publication.jks | tr -d '\n' > cle.txt`
   - Windows (PowerShell) : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("projo-publication.jks")) > cle.txt`
3. Sur GitHub : dépôt **launcher-projo** → **Settings** → **Secrets and variables** →
   **Actions** → **New repository secret**, et crée ces 4 secrets :

   | Nom | Valeur |
   |---|---|
   | `KEYSTORE_BASE64` | tout le contenu de `cle.txt` |
   | `KEYSTORE_PASSWORD` | le mot de passe choisi à l'étape 1 |
   | `KEY_ALIAS` | `projo` |
   | `KEY_PASSWORD` | le même mot de passe |

4. **Sauvegarde `projo-publication.jks` et son mot de passe** (gestionnaire de mots de passe,
   clé USB…). Sans eux, impossible de publier une mise à jour installable par-dessus.
   Ne mets jamais ce fichier dans le dépôt.

### À chaque version

**Depuis le site GitHub (le plus simple)** : dépôt → **Releases** → **Draft a new release** →
**Choose a tag** → tape `v1.0.0` (puis `v1.0.1`, `v1.1.0`…) → **Create new tag** → titre libre →
**Publish release**.

**Ou en ligne de commande** : `git tag v1.0.0 && git push origin v1.0.0`.

Dans les deux cas, le workflow **Publication** compile l'APK, le signe avec ta clé et l'ajoute
à la release sous le nom `launcher.apk` (environ 5 minutes, suivi dans l'onglet **Actions**).
Le numéro de version interne (versionCode) augmente automatiquement. Sur le projecteur,
relance Downloader avec le même code court.

## 5. Pour les curieux : structure du projet

```
app/src/main/java/fr/projo/launcher/
  MainActivity.kt          activité « écran d'accueil », fenêtres, bouton Retour
  LauncherViewModel.kt     état de l'accueil (apps, favoris, masquées)
  data/                    liste des apps, logos + couleurs, préférences, favoris par défaut
  system/                  ouverture des réglages / sources, nom du Wi-Fi
  ui/                      écran d'accueil, cartes, pastilles, horloge, lueur, menus
.github/workflows/
  build.yml                compile à chaque push ; publie l'APK de test (branche de test)
  release.yml              tag v* → APK signé « launcher.apk » dans GitHub Releases
signature/debug-test.keystore   clé de TEST publique (versions de test uniquement)
```

Compilation locale (Android Studio ou ligne de commande) : `./gradlew assembleDebug`.

## Télécommande smartphone (PWA)

Le dossier `remote-pwa/` contient la télécommande pour téléphone, publiée sur
`https://58np95y6s6-svg.github.io/launcher-projo/` (branche `gh-pages`). Protocole, déploiement,
tests et limites iOS : voir [docs/telecommande.md](docs/telecommande.md).
