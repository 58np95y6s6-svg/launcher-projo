# iBiTube

iBiTube est SmartTube (https://github.com/yuliskov/SmartTube, licence MIT) avec une autre identité visuelle :
nom, logo, couleurs bleues, écran de démarrage. Le lecteur, les réglages et toutes les fonctions sont ceux de SmartTube.

- Identifiant : `com.ibi.ibitube`. L'appli s'installe à côté de SmartTube sans le remplacer.
- Version : celle de SmartTube suivie de `-ibi` (ex. `32.68-ibi`).
- Mises à jour automatiques : coupées. iBiTube ne propose jamais l'APK officiel de SmartTube.

## Où sont les changements

Tout passe par une variante Gradle `ibitube` et ses ressources. Le code de SmartTube reste intact, sauf deux lignes repérées par `iBiTube`.

| Fichier | Rôle |
| --- | --- |
| `smarttubetv/ibitube.gradle` | Déclare la variante : identifiant, suffixe `-ibi`, signature de repli, noms des APK |
| `smarttubetv/build.gradle` | **1 ligne ajoutée** : `apply from: 'ibitube.gradle'` |
| `common/.../dialogs/AppUpdatePresenter.java` | **1 bloc ajouté** (`// iBiTube`) : sans adresse de mise à jour, ne cherche rien et répond « dernière version » |
| `smarttubetv/src/ibitube/AndroidManifest.xml` | Thème de l'écran de démarrage sur l'activité de lancement |
| `smarttubetv/src/ibitube/res/values/` | Nom, palette (le rouge devient bleu), coins arrondis, adresses de mise à jour vides |
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
  leanback). Le léger agrandissement au focus est celui de SmartTube.
- Écran de démarrage : fond `#0A1020`, logo centré, nom dessous. Il s'affiche pendant le chargement
  normal, sans attente ajoutée. Sous Android 12 et plus, l'écran de démarrage du système joue le logo
  en fondu avec un léger zoom (600 ms). Avant Android 12 (Fire TV compris), l'écran est fixe : Android ne
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

Sans clé, l'APK est signé avec une clé de débogage : il s'installe, mais une compilation suivante ne
pourra pas le mettre à jour. Pour signer avec ta clé :

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
- dépose les APK en artefact.

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

Le logo est original : un monogramme « iB » dont les deux boucles du B sont des triangles « lecture »
arrondis. Il est posé sur un carré aux coins arrondis, en dégradé de `#1E6BFF` à `#00C2FF`. Le logotype
utilise la police Outfit Bold (SIL Open Font License 1.1), convertie en tracés.

- `ibitube/logo/*.svg` : les sources du logo, de l'icône adaptative (premier plan et fond), de
  l'icône alternative, du logotype, de la bannière TV 320 × 180, du logo d'en-tête et de l'écran de
  démarrage.
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
bleu, y compris celui qui s'appelle « rouge ».
