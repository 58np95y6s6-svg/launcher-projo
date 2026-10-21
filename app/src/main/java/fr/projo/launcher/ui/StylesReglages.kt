package fr.projo.launcher.ui

import androidx.compose.ui.graphics.Color

/*
 * HABILLAGE du panneau Réglages : un style par design.
 *
 * Le contenu (tuiles, actions, focus) est dans SettingsPanel.kt et ne change jamais ; un style
 * décrit seulement COMMENT le dessiner : position et forme du panneau, matière, forme des
 * tuiles, sélection, pastilles, typographie, en-tête et animation d'ouverture.
 * Le dessin lui-même est dans RenduReglages.kt.
 *
 * AJOUTER UN STYLE (nouveau design) : une branche dans Design.styleReglages() ci-dessous, en
 * combinant les variantes existantes (Disposition, FormeTuiles, Selection, Pastille, EnTete,
 * Jauge, Ouverture). Une variante vraiment nouvelle s'ajoute à son enum et à la fonction de
 * RenduReglages.kt qui la dessine (le compilateur signale les « when » à compléter).
 */

/** Position et forme du panneau à l'écran. */
enum class Disposition {
    /** Feuille collée ou flottante contre le bord droit. */
    FEUILLE_DROITE,
    /** Feuille contre le bord gauche. */
    FEUILLE_GAUCHE,
    /** Fenêtre au centre de l'écran. */
    FENETRE_CENTREE,
    /** Feuille qui monte du bas, sur toute la largeur. */
    FEUILLE_BAS,
    /** Tout l'écran. */
    PLEIN_ECRAN,
}

/** Forme des tuiles et façon de les assembler. */
enum class FormeTuiles {
    /** Lignes séparées, chacune sur sa carte. */
    LIGNES,
    /** Listes groupées façon iOS : un fond par section, lignes séparées par des filets. */
    GROUPES,
    /** Liste segmentée façon Android 16 : lignes jointives, grands coins aux extrémités du groupe. */
    SEGMENTS,
    /** Grille de cartes, sans intitulés : pastille et indicateur en haut, titre et valeur en bas. */
    CARTES,
    /** Grille de tuiles, sans intitulés : pastille et titre en haut, valeur en bas. */
    TUILES,
    /** Gros boutons arrondis en colonnes (façon menu de console de salon). */
    BOUTONS,
}

/** Apparence de la tuile sélectionnée. */
enum class Selection {
    /** Plateau opaque qui se soulève (clair sur fond sombre, encre sur fond clair). */
    PLATEAU,
    /** Anneau décollé de la tuile (couleur StyleReglages.couleurs.anneau). */
    ANNEAU,
    /** Tuile remplie de sa propre teinte, avec un anneau blanc décollé. */
    TEINTE,
    /** Liseré bleu épaissi et lumineux (boutons façon console de salon). */
    LISERE,
}

/** Fond des icônes. */
enum class Pastille {
    /** Rond discret : rempli de la teinte du réglage sur un plateau clair, disque blanc sur une carte sombre. */
    RONDE,
    /** Carré arrondi plein de la teinte du réglage (façon iOS). */
    CARREE_COULEUR,
    /** Carré arrondi de la teinte assombrie (« soir »), noir sur la tuile sélectionnée. */
    CARREE_SOIR,
    /** Rond tonal clair (Material You). */
    RONDE_TONALE,
    /** Icône seule, sans fond. */
    AUCUNE,
}

/** En-tête du panneau. */
enum class EnTete {
    /** « Réglages » en gras et version dessous, alignés à gauche. */
    SIMPLE,
    /** Titre centré, bouton × à gauche. */
    CENTRE,
    /** Grand titre gras à gauche, bouton × à droite. */
    TITRE_FERMER,
    /** Très grand titre fin à gauche, aides de la télécommande à droite. */
    AIDES,
    /** Affiche : aplat de la couleur du réglage sélectionné, son nom en géant et sa valeur. */
    AFFICHE,
    /** Bandeau gris avec titre, façon menu de console de salon. */
    BANDEAU,
}

/** Dessin des jauges (volume, stockage). */
enum class Jauge {
    /** Barre fine. */
    FINE,
    /** Piste, remplissage en dégradé et bouton rond. */
    CURSEUR,
    /** Piste épaisse avec icône, poignée verticale et point d'arrêt (Material 3 Expressive). */
    EXPRESSIVE,
    /** Remplissage ondulé puis piste fine (indicateur Material 3). */
    ONDULEE,
    /** Barres pleines de la couleur d'accent, en blocs arrondis. */
    BLOCS,
}

/** Animation d'ouverture. */
enum class Ouverture(val duree: Int) {
    GLISSE_DROITE(260),
    GLISSE_BAS(320),
    ZOOM(300),
    FONDU(240),
}

/** Couleurs du panneau. */
class CouleursReglages(
    /** Fond du panneau, en dégradé vertical (une seule couleur : aplat). */
    val fond: List<Color>,
    val texte: Color,
    val texteDiscret: Color,
    /** Fond d'une tuile (ou d'un groupe) au repos. */
    val carte: Color,
    /** Fond de la tuile sélectionnée, en dégradé vertical. */
    val carteFocus: List<Color>,
    val texteFocus: Color,
    val texteDiscretFocus: Color,
    /** Jauges, chiffres mis en avant. */
    val accent: Color,
    /** Fond d'une tuile mise en avant (mise à jour…). */
    val accentFond: Color,
    /** Filets et contours. */
    val filet: Color,
    /** Anneau de sélection. */
    val anneau: Color,
    /** Fond des pastilles neutres. */
    val pastille: Color,
    /** Messages à signaler. */
    val alerte: Color,
    /** Voile posé sur l'accueil. */
    val voile: Color,
    /** Intitulés de section (sinon : texte discret en capitales, accent en minuscules). */
    val section: Color? = null,
)

/** Tailles des textes (unités .us) et poids. */
class TypoReglages(
    val titre: Int = 14,
    val valeur: Int = 11,
    val section: Int = 10,
    val chiffre: Int = 14,
    val enTete: Int = 28,
    val titreGras: Boolean = true,
    /** Intitulés de section en capitales espacées (sinon « Connexions », couleur d'accent). */
    val sectionsCapitales: Boolean = true,
)

/** Mesures (unités .u). */
class MesuresReglages(
    /** Largeur du panneau (feuilles latérales, fenêtre). */
    val largeur: Int = 372,
    /** Hauteur du panneau (feuille du bas). */
    val hauteur: Int = 540,
    /** Écarts aux bords de l'écran : 0 = panneau collé au bord. */
    val haut: Int = 0,
    val bas: Int = 0,
    val cote: Int = 0,
    /** Rayon des coins du panneau. */
    val rayon: Int = 26,
    /** Marges intérieures du panneau. */
    val marge: Int = 22,
    /** Rayon des tuiles (ou des groupes). */
    val rayonTuile: Int = 16,
    /** Écart entre deux tuiles. */
    val ecart: Int = 8,
    /** Marges intérieures d'une tuile. */
    val padH: Int = 14,
    val padV: Int = 11,
    /** Taille de la pastille d'icône. */
    val pastille: Int = 32,
    /** Hauteur minimale d'une tuile (cartes, boutons). */
    val hauteurTuile: Int = 0,
)

/** Habillage complet du panneau Réglages pour un design. */
class StyleReglages(
    val disposition: Disposition,
    val tuiles: FormeTuiles,
    val selection: Selection,
    val pastille: Pastille,
    val enTete: EnTete,
    val ouverture: Ouverture,
    val couleurs: CouleursReglages,
    val mesures: MesuresReglages = MesuresReglages(),
    val typo: TypoReglages = TypoReglages(),
    /** Colonnes (cartes, boutons). */
    val colonnes: Int = 1,
    /** Valeur à droite du titre quand la place le permet (sinon dessous). */
    val valeurADroite: Boolean = false,
    /** Chevron › au bout des lignes. */
    val chevron: Boolean = false,
    /** Agrandissement de la tuile sélectionnée. */
    val agrandissement: Float = 1.03f,
    val jaugeVolume: Jauge = Jauge.FINE,
    val jaugeStockage: Jauge = Jauge.FINE,
    /** Matière : reflet clair en haut à gauche et liseré lumineux (verre sombre). */
    val reflet: Boolean = false,
    /**
     * Matière : verre clair (Studio) — fond laiteux et lumières douces, liseré blanc et contour
     * fin ; tuiles en verre posées sur une ombre douce qui s'élargit à la sélection.
     */
    val verreClair: Boolean = false,
    /** Matière : hauteur (unités) du fondu transparent en haut du panneau (feuille sans bord net). */
    val fonduHaut: Int = 0,
    /** Matière : fines rayures horizontales (fond de console de salon). */
    val rayures: Boolean = false,
    /** Ombre portée du panneau (unités). */
    val ombre: Int = 0,
    /** Teintes propres au design (sinon celles de l'objet Teintes). */
    val teintes: Map<String, Color> = emptyMap(),
)

/**
 * Style du panneau Réglages pour chaque design : LE SEUL ENDROIT où ajouter celui d'un
 * nouveau design (le compilateur exige une branche par design).
 */
fun Design.styleReglages(): StyleReglages = when (this) {
    Design.VERRE -> StyleVerre
    Design.PASTEL -> StylePastel
    Design.CONSOLE -> StyleConsole
    Design.UNE -> StyleUne
    Design.CHAINES -> StyleChaines
    Design.STUDIO -> StyleStudio
}

// ------------------------------------------------------------------ Verre

/** Verre (maquette t1) : fenêtre de verre centrée façon visionOS, listes groupées à icônes carrées colorées. */
private val StyleVerre = StyleReglages(
    disposition = Disposition.FENETRE_CENTREE,
    tuiles = FormeTuiles.GROUPES,
    selection = Selection.PLATEAU,
    pastille = Pastille.CARREE_COULEUR,
    enTete = EnTete.CENTRE,
    ouverture = Ouverture.ZOOM,
    couleurs = CouleursReglages(
        // Verre sans flou : dégradé opaque pris sur la maquette (lilas en haut, indigo profond en bas)
        fond = listOf(Color(0xFF5F5396), Color(0xFF3D3470), Color(0xFF2A2349)),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.66f),
        carte = Color.White.copy(alpha = 0.075f),
        carteFocus = listOf(Color.White, Color(0xFFEEF0F6)),
        texteFocus = Color(0xFF111111), texteDiscretFocus = Color(0xFF111111).copy(alpha = 0.62f),
        accent = Color(0xFF0A84FF), accentFond = Color(0xFF0A84FF).copy(alpha = 0.22f),
        filet = Color.White.copy(alpha = 0.13f), anneau = Color.White,
        pastille = Color.White.copy(alpha = 0.12f), alerte = Color(0xFFFFB0A8),
        voile = Color(0xFF060416).copy(alpha = 0.82f),
    ),
    mesures = MesuresReglages(
        largeur = 508, haut = 31, bas = 62, rayon = 26, marge = 19, rayonTuile = 15, ecart = 11,
        padH = 13, padV = 10, pastille = 27,
    ),
    typo = TypoReglages(titre = 15, valeur = 12, section = 10, chiffre = 17, enTete = 20),
    valeurADroite = true,
    chevron = true,
    agrandissement = 1.035f,
    jaugeVolume = Jauge.CURSEUR,
    jaugeStockage = Jauge.CURSEUR,
    reflet = true,
    ombre = 24,
    teintes = mapOf(
        "volume" to Color(0xFFFF375F), "wifi" to Color(0xFF0A84FF), "bluetooth" to Color(0xFF0A84FF),
        "stockage" to Color(0xFF8E8E93), "hdmi" to Color(0xFF5E5CE6), "design" to Color(0xFFBF5AF2),
        "accueil" to Color(0xFF30B0C7), "masquees" to Color(0xFF8E8E93), "maj" to Color(0xFFFF9F0A),
    ),
)

// ------------------------------------------------------------------ Pastel

/** Pastel (maquette t3) : feuille latérale flottante Material You, liste segmentée, anneau décollé. */
private val StylePastel = StyleReglages(
    disposition = Disposition.FEUILLE_DROITE,
    tuiles = FormeTuiles.SEGMENTS,
    selection = Selection.ANNEAU,
    pastille = Pastille.RONDE_TONALE,
    enTete = EnTete.TITRE_FERMER,
    ouverture = Ouverture.GLISSE_DROITE,
    couleurs = CouleursReglages(
        fond = listOf(Color(0xFFF8F3FA)),
        texte = Color(0xFF1D1A22), texteDiscret = Color(0xFF4A4453),
        carte = Color(0xFFFEFCFF),
        carteFocus = listOf(Color(0xFFE7DAFF)),
        texteFocus = Color(0xFF2F1868), texteDiscretFocus = Color(0xFF4A3B78),
        accent = Color(0xFF6A4FB3), accentFond = Color(0xFFFFDBCC),
        filet = Color(0xFFCDC4D6), anneau = Color(0xFF6A4FB3),
        pastille = Color(0xFFEDE5F6), alerte = Color(0xFFB3261E),
        voile = Color(0xFF2F1868).copy(alpha = 0.32f),
    ),
    mesures = MesuresReglages(
        largeur = 386, haut = 30, bas = 30, cote = 30, rayon = 24, marge = 18, rayonTuile = 16, ecart = 2,
        padH = 12, padV = 10, pastille = 32,
    ),
    typo = TypoReglages(titre = 15, valeur = 12, section = 12, chiffre = 17, enTete = 32, sectionsCapitales = false),
    chevron = true,
    agrandissement = 1.02f,
    jaugeVolume = Jauge.EXPRESSIVE,
    jaugeStockage = Jauge.ONDULEE,
    ombre = 14,
    teintes = mapOf("design" to Color(0xFF9A4A2E), "maj" to Color(0xFF9A4A2E)),
)

// ------------------------------------------------------------------ Console

/** Console (maquette t4) : centre de contrôle qui monte du bas, grille de cartes sombres. */
private val StyleConsole = StyleReglages(
    disposition = Disposition.FEUILLE_BAS,
    tuiles = FormeTuiles.CARTES,
    selection = Selection.ANNEAU,
    pastille = Pastille.RONDE,
    enTete = EnTete.AIDES,
    ouverture = Ouverture.GLISSE_BAS,
    couleurs = CouleursReglages(
        // Voile de lisibilité : fondu en haut (fonduHaut), bleu nuit opaque sous les cartes
        fond = listOf(Color(0xFF050817)),
        texte = Color(0xFFF4F6FB), texteDiscret = Color(0xFFF4F6FB).copy(alpha = 0.72f),
        carte = Color(0xFF161928),
        carteFocus = listOf(Color(0xFF1F2333)),
        texteFocus = Color(0xFFF4F6FB), texteDiscretFocus = Color(0xFFF4F6FB).copy(alpha = 0.78f),
        accent = Color(0xFFF4F6FB), accentFond = Color(0xFF2A2F4A),
        filet = Color.White.copy(alpha = 0.09f), anneau = Color.White,
        pastille = Color.White.copy(alpha = 0.10f), alerte = Color(0xFFFFB86B),
        voile = Color(0xFF040716).copy(alpha = 0.45f),
    ),
    mesures = MesuresReglages(
        hauteur = 344, marge = 48, rayon = 0, rayonTuile = 10, ecart = 10, padH = 14, padV = 12,
        pastille = 28, hauteurTuile = 106,
    ),
    typo = TypoReglages(titre = 15, valeur = 12, section = 10, chiffre = 40, enTete = 36, titreGras = false),
    colonnes = 4,
    agrandissement = 1.0f,
    jaugeVolume = Jauge.FINE,
    jaugeStockage = Jauge.FINE,
    fonduHaut = 26,
)

// ------------------------------------------------------------------ À la une

/** « Une » (maquette t9) : affiche de la couleur du réglage, nom géant, grille de cartes 4 × n. */
private val StyleUne = StyleReglages(
    disposition = Disposition.FEUILLE_BAS,
    tuiles = FormeTuiles.TUILES,
    selection = Selection.TEINTE,
    pastille = Pastille.CARREE_SOIR,
    enTete = EnTete.AFFICHE,
    ouverture = Ouverture.GLISSE_BAS,
    couleurs = CouleursReglages(
        fond = listOf(Color(0xFF0E0E12)),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.78f),
        carte = Color(0xFF17171D),
        carteFocus = listOf(Color(0xFF8B5CF6)),
        texteFocus = Color.White, texteDiscretFocus = Color.White.copy(alpha = 0.9f),
        accent = Color.White, accentFond = Color(0xFF1E1A14),
        filet = Color.White.copy(alpha = 0.08f), anneau = Color.White,
        pastille = Color(0xFF0B0B0D), alerte = Color(0xFFFFC14D),
        voile = Color(0xFF050508).copy(alpha = 0.55f),
    ),
    mesures = MesuresReglages(
        hauteur = 467, marge = 48, rayon = 18, rayonTuile = 10, ecart = 12, padH = 13, padV = 12,
        pastille = 30, hauteurTuile = 80,
    ),
    typo = TypoReglages(titre = 16, valeur = 12, section = 10, chiffre = 12, enTete = 104),
    colonnes = 4,
    agrandissement = 1.0f,
    jaugeVolume = Jauge.FINE,
    jaugeStockage = Jauge.FINE,
)

// ------------------------------------------------------------------ Chaînes

/** Chaînes (style console de salon blanche) : plein écran clair, gros boutons arrondis à liseré bleu. */
private val StyleChaines = StyleReglages(
    disposition = Disposition.PLEIN_ECRAN,
    tuiles = FormeTuiles.BOUTONS,
    selection = Selection.LISERE,
    pastille = Pastille.AUCUNE,
    enTete = EnTete.BANDEAU,
    ouverture = Ouverture.FONDU,
    couleurs = CouleursReglages(
        fond = listOf(ChainesFondHaut, ChainesFondBas),
        texte = ChainesGrisFonce, texteDiscret = ChainesGris,
        carte = Color.White,
        carteFocus = listOf(Color(0xFFF2FBFF), Color(0xFFD8F1FC)),
        texteFocus = Color(0xFF0E4A63), texteDiscretFocus = Color(0xFF3F7389),
        accent = ChainesBleu, accentFond = Color(0xFFE6F6FD),
        filet = ChainesBleuFilet, anneau = ChainesBleu,
        pastille = Color(0xFFEFF1F3), alerte = Color(0xFFD9534F),
        voile = Color(0xFF6E757C).copy(alpha = 0.35f),
    ),
    mesures = MesuresReglages(
        marge = 56, rayon = 0, rayonTuile = 22, ecart = 14, padH = 20, padV = 12, pastille = 26, hauteurTuile = 64,
    ),
    typo = TypoReglages(titre = 18, valeur = 12, section = 11, chiffre = 18, enTete = 26),
    colonnes = 2,
    agrandissement = 1.04f,
    jaugeVolume = Jauge.BLOCS,
    jaugeStockage = Jauge.BLOCS,
    rayures = true,
)

// ------------------------------------------------------------------ Studio

/**
 * Studio : feuille de verre clair flottante à droite, comme les panneaux de son accueil
 * (StudioVerre.kt) — titre gras à l'encre, cartes de verre séparées, icônes monochromes,
 * intitulés gris en minuscules ; la tuile sélectionnée devient une plaque d'encre qui se
 * soulève (comme les pastilles de l'en-tête), sur une ombre douce plutôt qu'un halo.
 */
private val StyleStudio = StyleReglages(
    disposition = Disposition.FEUILLE_DROITE,
    tuiles = FormeTuiles.LIGNES,
    selection = Selection.PLATEAU,
    pastille = Pastille.RONDE,
    enTete = EnTete.SIMPLE,
    ouverture = Ouverture.GLISSE_DROITE,
    couleurs = CouleursReglages(
        // Blanc laiteux → gris très clair, comme le fond de l'accueil (fondStudio), sans transparence
        fond = listOf(Color(0xFFFAFBFC), Color(0xFFF1F2F5), Color(0xFFE8EAEE)),
        texte = StudioEncre, texteDiscret = StudioGris,
        carte = Color.White.copy(alpha = 0.66f),
        carteFocus = listOf(Color(0xFF2C2C2E), StudioEncre),
        texteFocus = Color.White, texteDiscretFocus = Color.White.copy(alpha = 0.72f),
        accent = Color(0xFF0A84FF), accentFond = Color(0xFF0A84FF).copy(alpha = 0.12f),
        filet = StudioContour, anneau = StudioEncre,
        pastille = StudioEncre.copy(alpha = 0.06f), alerte = Color(0xFFC4251C),
        voile = StudioEncre.copy(alpha = 0.26f),
        section = StudioGris,
    ),
    mesures = MesuresReglages(
        largeur = 384, haut = 20, bas = 20, cote = 20, rayon = 28, marge = 20, rayonTuile = 14, ecart = 9,
        padH = 14, padV = 11, pastille = 32,
    ),
    typo = TypoReglages(titre = 14, valeur = 11, section = 12, chiffre = 15, enTete = 28, sectionsCapitales = false),
    agrandissement = 1.035f,
    jaugeVolume = Jauge.CURSEUR,
    jaugeStockage = Jauge.CURSEUR,
    verreClair = true,
    ombre = 22,
)
