package autobattler;

/**
 * Capacités uniques, une par lignée d'évolution. Elles se lancent quand le mana atteint son maximum,
 * à la place de l'attaque normale. Les dégâts suivent la formule des attaques (Attaque du lanceur,
 * Défense de la cible, table des types), donc ils progressent avec l'évolution et les étoiles.
 */
public enum Ability {
    GIGA_SANGSUE("Giga-Sangsue", "220% de dégâts, se soigne de 60% des dégâts infligés.", 80, 20),
    LANCE_FLAMMES("Lance-Flammes", "Souffle sur 3 cases en ligne : 180% de dégâts et brûlure 4 s.", 90, 20),
    REPLI("Repli", "Bouclier de 35% de ses PV max pendant 4 s.", 70, 30),
    PARA_SPORE("Para-Spore", "120% de dégâts et paralyse la cible 3 s.", 60, 0),
    RAPACE("Rapace", "Fond sur l'ennemi le plus affaibli : 220% de dégâts.", 70, 20),
    CROC_FATAL("Croc Fatal", "100% de dégâts + 20% des PV restants de la cible.", 60, 0),
    POUDRE_TOXIK("Poudre Toxik", "Empoisonne la cible et ses voisins : 5% PV max/s pendant 4 s.", 80, 20),
    HYPNOSE("Hypnose", "Endort la cible 2 s et inflige 120% de dégâts.", 80, 20),
    CLOSE_COMBAT("Close Combat", "Frappe violente : 320% de dégâts.", 70, 0),
    EBOULEMENT("Éboulement", "130% de dégâts aux ennemis adjacents, étourdis 1 s.", 90, 30),
    TONNERRE("Tonnerre", "Rebondit sur 3 ennemis : 160% de dégâts, 30% de paralysie.", 80, 20),
    FEU_FOLLET("Feu Follet", "100% de dégâts et forte brûlure : 8% PV max/s pendant 4 s.", 60, 20),
    VITESSE_EXTREME("Vitesse Extrême", "200% de dégâts et +60% de vitesse d'attaque 4 s.", 70, 10),
    PSYKO("Psyko", "Onde psychique : 300% de dégâts.", 90, 30),
    CAGE_ECLAIR("Cage-Éclair", "100% de dégâts, paralyse la cible et ses voisins 3 s.", 80, 20),
    LASER_GLACE("Laser Glace", "150% de dégâts et gèle la cible 2 s.", 80, 30),
    BALL_OMBRE("Ball'Ombre", "250% de dégâts à la cible la plus affaiblie.", 70, 20),
    PROVOC("Provoc", "Provoque les ennemis à 2 cases (3 s), bouclier de 25% PV.", 80, 40),
    OSMERANG("Osmerang", "Lance son os deux fois : 2 × 140% de dégâts.", 70, 20),
    TRANCHE("Tranche", "Fauche la cible et les ennemis adjacents : 180% de dégâts.", 70, 10),
    BLIZZARD("Blizzard", "130% de dégâts autour de la cible et gel 1 s.", 100, 40),
    REPOS("Repos", "Récupère 30% de ses PV max.", 90, 30),
    OURAGAN("Ouragan", "200% de dégâts à la cible et à ses voisins.", 90, 20);

    public final String label;
    public final String description;
    public final int maxMana;
    public final int startMana;

    Ability(String label, String description, int maxMana, int startMana) {
        this.label = label;
        this.description = description;
        this.maxMana = maxMana;
        this.startMana = startMana;
    }
}
