package autobattler;

/**
 * Classes de combat (en plus des types) : chaque lignée a un rôle, et certaines sont aussi « Starter ».
 * Comme pour les types, on compte les lignées distinctes sur le plateau ; les bonus ne s'appliquent
 * qu'aux Pokémon qui ont la classe.
 */
public enum Trait {
    GUARDIAN("Gardien", 0x5B8DEF, true, new int[]{2, 4}, "Encaisse en première ligne"),
    FIGHTER("Combattant", 0xE0662F, true, new int[]{2, 4}, "Corps à corps durable"),
    ASSASSIN("Assassin", 0x9B6BE0, false, new int[]{2, 3}, "Plonge sur l'arrière-ligne"),
    MYSTIC("Mystique", 0xE05BB5, false, new int[]{2, 4}, "Capacités puissantes, à l'arrière"),
    MARKSMAN("Tireur", 0x3FB27F, false, new int[]{2, 3}, "Dégâts à distance, à l'arrière"),
    STARTER("Starter", 0xF5C542, false, new int[]{3}, "Pokémon de départ"),
    LEGENDARY("Légendaire", 0x6FD8E8, false, new int[]{1, 2}, "Pokémon légendaire (tier 4)");

    public final String label;
    public final int rgb;
    /** Rôle prévu pour la première ligne (sinon l'arrière, sauf Assassin qui peut être n'importe où). */
    public final boolean frontline;
    private final int[] thresholds;
    public final String role;

    Trait(String label, int rgb, boolean frontline, int[] thresholds, String role) {
        this.label = label;
        this.rgb = rgb;
        this.frontline = frontline;
        this.thresholds = thresholds;
        this.role = role;
    }

    /** Paliers (nombre de lignées distinctes). */
    public int[] thresholds() {
        return thresholds.clone();
    }

    /** Vrai pour les classes qui sont un rôle (toutes sauf Starter). */
    public boolean isRole() {
        return this != STARTER && this != LEGENDARY;
    }

    /**
     * Vrai si la classe est à sa place sur la ligne locale donnée (0 = première ligne, 3 = fond) :
     * Gardien/Combattant devant (lignes 0-1), Mystique/Tireur pas en première ligne, Assassin partout.
     */
    public boolean suitsRow(int localRow) {
        if (frontline) return localRow <= 1;
        if (this == MYSTIC || this == MARKSMAN) return localRow >= 1;
        return true;
    }

    public String describe(int level) {
        boolean high = level >= 2;
        switch (this) {
            case GUARDIAN: return high ? "Bouclier de 35% PV au début" : "Bouclier de 20% PV au début";
            case FIGHTER: return high ? "+30% PV, vol de vie 20%" : "+15% PV, vol de vie 10%";
            case ASSASSIN: return high ? "Saut à l'arrière, 50% crit, +30% dégâts" : "Saut à l'arrière, 30% crit, +15% dégâts";
            case MYSTIC: return high ? "+70% puissance, +20 mana au départ" : "+35% puissance, +20 mana au départ";
            case MARKSMAN: return high ? "+1 portée, +30% vitesse d'attaque" : "+20% vitesse d'attaque";
            case STARTER: return "+20% PV et Attaque";
            case LEGENDARY: return high ? "Toute l'équipe : +5% Attaque et PV" : "Toute l'équipe : +5% Attaque";
            default: return "";
        }
    }
}
