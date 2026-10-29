# iBiTube

iBiTube est SmartTube (https://github.com/yuliskov/SmartTube, licence MIT) avec une autre identité visuelle :
nom, logo, couleurs bleues, vagues, écran de démarrage. Le lecteur, les réglages et toutes les fonctions sont ceux de SmartTube.

- Identifiant : `com.ibi.ibitube`. L'appli s'installe à côté de SmartTube sans le remplacer.
- Version : celle de SmartTube suivie de `-ibi` (ex. `32.68-ibi`).
- Mises à jour automatiques : coupées. iBiTube ne propose jamais l'APK officiel de SmartTube.

## Télécharger

Chaque push sur la branche `ibitube` publie l'APK à une adresse qui ne change jamais :

- **Fire TV** : https://github.com/58np95y6s6-svg/launcher-projo/releases/download/ibitube-apk/iBiTube-armeabi-v7a.apk
- **Toute TV Android** (APK universel, plus lourd) : https://github.com/58np95y6s6-svg/launcher-projo/releases/download/ibitube-apk/iBiTube.apk

Sur le Fire TV, avec l'appli **Downloader** : tape l'adresse, télécharge, puis « Installer ». La
première fois, Fire OS demande d'autoriser Downloader à installer des applis inconnues. Une nouvelle
version s'installe par-dessus l'ancienne, sans perdre les réglages.

## Où sont les changements

Tout passe par une variante Gradle `ibitube` et ses ressources. Le code de SmartTube reste intact, sauf deux lignes repérées par `iBiTube`.

| Fichier | Rôle |
| --- | --- |
| `smarttubetv/ibitube.gradle` | Déclare la variante : identifiant, suffixe `-ibi`, clé de test, noms des APK |
| `smarttubetv/build.gradle` | **1 ligne ajoutée** : `apply from: 'ibitube.gradle'` |
| `common/.../dialogs/AppUpdatePresenter.java` | **1 bloc ajouté** (`// iBiTube`) : sans adresse de mise à jour, ne cherche rien et répond « dernière version » |
| `smarttubetv/src/ibitube/AndroidManifest.xml` | Thème de l'écran de démarrage sur l'activité de lancement |
| `smarttubetv/src/ibitube/res/values/` | Nom, palette (le rouge devient bleu), coins arrondis, fond à vagues de l'accueil, adresses de mise à jour vides |
| `smarttubetv/src/ibitube/res/drawable*/`, `mipmap*/` | Logo, icônes, bannière TV, écran de démarrage, halo de focus des cartes |
| `smarttubetv/src/ibitube/res/xml/searchable.xml` | Recherche Android TV branchée sur iBiTube, pas sur SmartTube |
| `ibitube/logo/` | Sources SVG du logo et le script qui produit les PNG |
| `.github/workflows/build.yml` | Compilation, signature, artefacts, versions GitHub |

### Comment le reste suit sans toucher au code

- Les ressources d'une variante priment sur celles de `src/main` et des modules. iBiTube redéfinit donc
  les couleurs de l'amont sous le même nom : `red`, `semi_red`, `dark_red`… deviennent bleues. Les barres
  de progression, le lecteur, les badges « EN DIRECT » et la sélection suivent sans autre changement.
- Les modules `common` et `leanbackassistant` n'ont pas de variante `ibitube`. Ils prennent leur
  variante F-Droid (`matchingFallbacks`), la seule sans adresse de mise à jour, et dont le fournisseur
  de recherche dépend de l'identifiant de l'appli.
- Cartes : coins arrondis de 10 dp, et halo bleu sur la carte qui a le focus (premier plan des cartes
  leanback). Le texte de la carte en focus s'affiche en blanc sur un bandeau bleu. Le léger
  agrandissement au focus est celui de SmartTube.
- Vagues : trois couches de bleu en bas de l'écran de démarrage et du fond de l'accueil. Sur l'accueil, elles passent par l'attribut de thème `shelfBackground`, que SmartTube étire
  derrière les rangées. Pour cela, le style `App.Theme.DarkGrey.Browse` (le jeu de couleurs par défaut)
  est recopié dans `ibitube_styles.xml`, avec seulement le fond qui change. Si SmartTube modifie ce
  style, reporte ses changements dans la copie.
- Écran de démarrage : fond `#0A1020`, vagues en bas, le mot « iBiTube » au centre. Il s'affiche
  pendant le chargement normal, sans attente ajoutée. Sous Android 12 et plus, l'écran de démarrage du
  système fait apparaître le mot en fondu avec un léger zoom (600 ms). Avant Android 12 (Fire TV compris), l'écran est fixe : Android ne
  sait pas animer ce fond sans code. `androidx.core:core-splashscreen` n'apporterait rien ici, car il
  n'anime pas non plus avant Android 12 et demanderait du code dans `SplashActivity`.

## Compiler

Il faut le JDK 17 et le SDK Android.

```bash
git clone --recurse-submodules -b ibitube https://github.com/58np95y6s6-svg/launcher-projo.git ibitube
cd ibitube
./gradlew assembleIbitubeRelease
# APK : smarttubetv/build/outputs/apk/ibitube/release/iBiTube_<version>_<abi>.apk
```

Sur un Fire TV, prends l'APK `armeabi-v7a`. En cas de doute, prends `universal`.

## Signature

Sans secrets, l'APK est signé avec la clé de test du dépôt (`ibitube/signature/test.keystore`, mots
de passe Android standard). Toutes ces versions s'installent les unes par-dessus les autres. Cette clé
ne protège rien, puisqu'elle est publique. Pour signer avec ta propre clé :

1. Crée la clé une seule fois, et garde le fichier et les mots de passe en lieu sûr. Si tu la perds,
   il faudra désinstaller iBiTube pour installer la version suivante.
   ```bash
   keytool -genkeypair -v -keystore ibitube.jks -alias ibitube \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Encode-la en base64 :
   ```bash
   base64 -w0 ibitube.jks > ibitube.jks.b64     # Linux
   base64 -i ibitube.jks -o ibitube.jks.b64     # macOS
   ```
3. Sur GitHub, va dans **Settings → Secrets and variables → Actions** et crée 4 secrets :
   - `KEYSTORE_BASE64` : le contenu de `ibitube.jks.b64` ;
   - `KEYSTORE_PASSWORD` : le mot de passe du fichier ;
   - `KEY_ALIAS` : `ibitube` ;
   - `KEY_PASSWORD` : le mot de passe de la clé.

Passer de la clé de test à ta clé change la signature : il faudra désinstaller iBiTube une fois
avant d'installer la version signée.

Ces 4 secrets portent les mêmes noms que ceux du launcher Projo, dans le même dépôt. S'ils existent
déjà, iBiTube est signé avec la clé du launcher, ce qui ne pose aucun problème. Pour une clé à part,
il faudrait des noms de secrets différents dans `build.yml`.

En local, la même signature passe par un fichier `keystore.properties` à la racine. Ne le commite
jamais, ni le `.jks` : le `.gitignore` de SmartTube ne les exclut pas, et iBiTube n'y touche pas pour
éviter les conflits de fusion.

```properties
storeFile=/chemin/vers/ibitube.jks
storePassword=...
keyAlias=ibitube
keyPassword=...
```

## Intégration continue

`.github/workflows/build.yml` se lance à chaque push sur `ibitube`, sur les pull requests et sur les
tags `v*`. Il fait les étapes suivantes :

- récupère les sous-modules (`SharedModules`, `MediaServiceCore`) ;
- installe le JDK 17 et le cache Gradle ;
- compile `assembleIbitubeRelease` ;
- vérifie l'APK : identifiant, suffixe `-ibi`, nom affiché, aucune autorité ni adresse de mise à
  jour de SmartTube ;
- dépose les APK en artefact ;
- sur la branche `ibitube`, remplace la pré-version `ibitube-apk` (liens de téléchargement fixes).

### Publier une version

```bash
git tag v32.68-ibi
git push origin v32.68-ibi
```

Une version GitHub est créée avec les APK. Elle est marquée « pré-version » et jamais « latest ».
Ainsi, la mise à jour du launcher Projo, qui lit la dernière version du dépôt, ne la confond pas avec
la sienne. La publication échoue si les secrets de signature manquent.

Le `release.yml` du launcher ne se lance pas sur ces tags, car il n'existe pas dans la branche `ibitube`.

## Suivre SmartTube (mise à jour depuis l'amont)

`ibitube` est une branche, pas un fork GitHub : il n'y a donc pas de bouton « Sync fork ». La mise à
jour se fait en 4 commandes :

```bash
git remote add upstream https://github.com/yuliskov/SmartTube.git   # une seule fois
git fetch upstream
git checkout ibitube
git merge upstream/master
git push origin ibitube
```

La fusion met aussi à jour les pointeurs des sous-modules. La CI recompile ensuite. Seules 2 zones
peuvent entrer en conflit, et seulement si SmartTube modifie exactement ces lignes :

- `smarttubetv/build.gradle` : garde la ligne `apply from: 'ibitube.gradle'` juste sous
  `apply plugin: 'com.android.application'` ;
- `AppUpdatePresenter.start()` : garde le bloc `// iBiTube` au début de la méthode.

Si SmartTube ajoute une nouvelle couleur rouge, ou un nouveau logo dans `src/main/res`, ajoute la
ressource du même nom dans `smarttubetv/src/ibitube/res/`.

## Logo

Pas de pictogramme : l'identité, c'est le mot « iBiTube », « iBi » en bleu `#1E90FF` et « Tube » en
blanc, sur le fond nuit `#0A1020`. Il apparaît sur l'icône, la bannière TV, l'en-tête de l'accueil et
l'écran de démarrage. Police : Outfit Bold (SIL Open Font License 1.1), convertie en tracés.

- `ibitube/logo/*.svg` : les sources du mot, de l'icône (et de son premier plan adaptatif), de la
  bannière TV 320 × 180, de l'en-tête, des vagues et de l'écran de démarrage.
- `ibitube/logo/outils/` : le script qui produit ces SVG, les VectorDrawable et les PNG. Il utilise
  Python avec `fonttools` et `uharfbuzz`, Node avec Playwright, et la police
  `npm pack @fontsource/outfit` décompressée dans `outils/fonts/outfit/`.

## Palette

| Rôle | Couleur |
| --- | --- |
| Fond | `#0A1020` |
| Surfaces | `#141C30` |
| Accent (focus, progression, boutons, sélection) | `#1E90FF` |
| Texte | `#FFFFFF` |
| Texte secondaire | `#A8B3CF` |

Les jeux de couleurs de SmartTube (réglages, Interface) restent choisissables. Ils passent tous au
bleu, y compris celui qui s'appelle « rouge ». Les vagues ne sont que sur le jeu par défaut ; les jeux
« OLED » restent noirs.
