package fr.projo.launcher.modules

/*
 * ============================================================================================
 *  AJOUTER UN MODULE À LA ZONE DE MODULES (ex-bandeau NBA)
 * ============================================================================================
 *
 * La zone de modules remplace le bandeau NBA de chaque design (ui/NbaPanel.kt n'est plus
 * qu'une façade qui appelle ZoneModules). L'utilisateur y affiche 1 ou 2 modules, choisis dans
 * Réglages → Modules. Les fichiers Home*.kt n'ont pas à être modifiés.
 *
 * 1. Créer modules/<nom>/Module<Nom>.kt avec un `object Module<Nom> : Module(...)` :
 *      object ModuleHorloges : Module(
 *          id = "horloges",                       // stable : enregistré dans les préférences
 *          nom = "Horloges du monde",
 *          description = "L'heure à New York, Tokyo…",
 *          icone = ProjoIcons.Horloge,              // ImageVector 24 × 24
 *          tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
 *      ) {
 *          @Composable
 *          override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
 *              when (ctx.taille) {
 *                  TailleModule.PETIT -> …
 *                  else -> …
 *              }
 *          }
 *      }
 *
 * 2. L'ajouter à la liste [RegistreModules.tous] ci-dessous (l'ordre est celui du dialogue).
 *
 * 3. Tailles (place intérieure, unités .u avant l'échelle du design, voir TailleModule) :
 *      PETIT ≈ 155 × 125 (deux modules côte à côte dans l'emplacement NBA),
 *      MOYEN ≈ 324 × 127 (l'emplacement NBA), GRAND ≥ 300 × 240 (si un design le permet).
 *    Le cadre (fond, bordure, coins, marge de 8) est dessiné par la zone : le module remplit
 *    `modifier` et ne doit RIEN faire déborder (textes sur une ligne, maxLines/ellipsis).
 *    ctx.largeur / ctx.hauteur donnent la place exacte. Préférer `ligne(…)` pour fixer la
 *    hauteur de ligne des textes (le thème TV impose sinon un interligne de 24 sp).
 *
 * 4. Style : ctx.style (NbaStyle du design) : texte, texteDiscret, fond, ongletActif,
 *    focusFond / focusTexte (élément sélectionné), pastilleFond, filet. Ne jamais coder de
 *    couleur de texte en dur : le design Pastel est clair (texte foncé).
 *
 * 5. Focus et navigation : la zone doit rester atteignable à la télécommande.
 *      - module sans élément focalisable : envelopper le contenu dans CarteModule(ctx) ;
 *      - sinon poser `Modifier.focusRequester(ctx.entree)` sur le premier élément focalisable.
 *    ◀ (quand rien n'est plus à gauche dans le module) et Retour sont gérés par la zone :
 *    module voisin ou retour aux favoris. ctx.aLeFocus indique si le module a le focus.
 *
 * 6. Données (réseau…) : `ctx.rememberDonnees(cle, delai = { ms }) { charger() }` (Donnees.kt).
 *    Le chargement se fait hors du thread principal, avec cache en mémoire : la clé (ex.
 *    « meteo:Lyon ») n'est rechargée qu'après `delai`, et seulement pendant que l'accueil est
 *    affiché. Réseau : Reseau.lireTexte(url) (HTTPS, délais courts) ; jamais de boucle serrée.
 *    Prévoir les trois états : chargement, erreur (message court), valeur.
 *
 * 7. Réglages (facultatif) : `override val aDesReglages = true`, `resumeReglages(r)` et
 *    `@Composable override fun Reglages(reglages, donnees, fermer)` qui affiche un
 *    ui.ListeDialog. Lecture : reglages.texte("cle") / reglages.liste("cle", defaut) ;
 *    écriture : reglages.mettre("cle", valeur) (enregistré dans LauncherPrefs, clé
 *    « id.cle »). Le dialogue « Modules » propose automatiquement « Réglages · <nom> ».
 *
 * 8. Tests (app/src/test/…/ui/ModulesTest.kt) : DonneesModules(factices = { cle -> … })
 *    fournit les données sans réseau ; HoteModules(choix = listOf("horloges"), …) affiché par
 *    le banc d'essai (BancAccueil.hoteModules). Ajouter une capture du module seul et avec un
 *    autre module, dans 2 ou 3 designs, et vérifier que rien n'est coupé.
 * ============================================================================================
 */

import fr.projo.launcher.modules.infos.ModuleInfos
import fr.projo.launcher.modules.meteo.ModuleMeteo
import fr.projo.launcher.modules.photo.ModulePhoto
import fr.projo.launcher.modules.sport.ModuleSport
import fr.projo.launcher.modules.tv.ModuleTv

/** Liste unique des modules disponibles. */
object RegistreModules {

    val tous: List<Module> = listOf(
        ModuleSport,
        ModuleMeteo,
        ModuleTv,
        ModuleInfos,
        ModulePhoto,
    )

    /** Module affiché quand l'utilisateur n'a rien choisi (l'ancien bandeau NBA). */
    val parDefaut: Module get() = ModuleSport

    fun trouver(id: String): Module? = tous.firstOrNull { it.id == id }
}
