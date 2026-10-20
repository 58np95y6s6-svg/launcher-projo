package fr.projo.launcher.remote

/** Rectangle à l'écran (pixels), sans dépendance à Android pour être testé. */
data class Boite(val gauche: Int, val haut: Int, val droite: Int, val bas: Int) {
    val centreX: Int get() = (gauche + droite) / 2
    val centreY: Int get() = (haut + bas) / 2
    val vide: Boolean get() = droite <= gauche || bas <= haut
}

enum class Direction { HAUT, BAS, GAUCHE, DROITE }

/**
 * Choix de l'élément focusable voisin dans une direction (navigation par le service
 * d'accessibilité, quand l'appli ne sait pas chercher elle-même : Compose, WebView…).
 * Même idée que FocusFinder d'Android : d'abord les éléments « dans le faisceau » (qui se
 * recouvrent sur l'axe perpendiculaire), puis le plus proche avec un poids
 * 13 × distance principale² + décalage².
 */
object GeometrieFocus {

    /** Indice du meilleur candidat, ou -1. Les boîtes vides ou identiques à [depuis] sont ignorées. */
    fun voisin(depuis: Boite, candidats: List<Boite>, direction: Direction): Int {
        var meilleur = -1
        var meilleurFaisceau = false
        var meilleurPoids = Long.MAX_VALUE
        for ((i, c) in candidats.withIndex()) {
            if (c.vide || c == depuis || !dansLaDirection(depuis, c, direction)) continue
            val faisceau = dansLeFaisceau(depuis, c, direction)
            val poids = poids(depuis, c, direction)
            val mieux = when {
                faisceau != meilleurFaisceau -> faisceau
                else -> poids < meilleurPoids
            }
            if (meilleur == -1 || mieux) {
                meilleur = i
                meilleurFaisceau = faisceau
                meilleurPoids = poids
            }
        }
        return meilleur
    }

    /** Premier élément en lisant l'écran (haut puis gauche) : quand rien n'a le focus. */
    fun premier(candidats: List<Boite>): Int =
        candidats.withIndex().filter { !it.value.vide }
            .minWithOrNull(compareBy({ it.value.haut }, { it.value.gauche }))?.index ?: -1

    private fun dansLaDirection(s: Boite, c: Boite, d: Direction): Boolean = when (d) {
        Direction.DROITE -> (s.gauche < c.gauche || s.droite <= c.gauche) && c.centreX > s.centreX
        Direction.GAUCHE -> (s.droite > c.droite || s.gauche >= c.droite) && c.centreX < s.centreX
        Direction.BAS -> (s.haut < c.haut || s.bas <= c.haut) && c.centreY > s.centreY
        Direction.HAUT -> (s.bas > c.bas || s.haut >= c.bas) && c.centreY < s.centreY
    }

    private fun dansLeFaisceau(s: Boite, c: Boite, d: Direction): Boolean = when (d) {
        Direction.DROITE, Direction.GAUCHE -> c.bas > s.haut && c.haut < s.bas
        Direction.HAUT, Direction.BAS -> c.droite > s.gauche && c.gauche < s.droite
    }

    private fun poids(s: Boite, c: Boite, d: Direction): Long {
        val principal = when (d) {
            Direction.DROITE -> c.gauche - s.droite
            Direction.GAUCHE -> s.gauche - c.droite
            Direction.BAS -> c.haut - s.bas
            Direction.HAUT -> s.haut - c.bas
        }.coerceAtLeast(0).toLong()
        val decalage = when (d) {
            Direction.DROITE, Direction.GAUCHE -> c.centreY - s.centreY
            Direction.HAUT, Direction.BAS -> c.centreX - s.centreX
        }.toLong()
        return 13 * principal * principal + decalage * decalage
    }
}
