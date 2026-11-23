package autobattler;

/** Types Pokémon, table d'efficacité et nom de l'attaque spéciale associée. */
public enum PType {
    NORMAL("Normal", 0xA8A878, "Plaquage"),
    FIRE("Feu", 0xF08030, "Lance-Flammes"),
    WATER("Eau", 0x6890F0, "Hydrocanon"),
    GRASS("Plante", 0x78C850, "Giga-Sangsue"),
    ELECTRIC("Électrik", 0xF8D030, "Tonnerre"),
    ICE("Glace", 0x98D8D8, "Laser Glace"),
    FIGHTING("Combat", 0xC03028, "Close Combat"),
    POISON("Poison", 0xA040A0, "Bomb-Beurk"),
    GROUND("Sol", 0xE0C068, "Séisme"),
    FLYING("Vol", 0xA890F0, "Rapace"),
    PSYCHIC("Psy", 0xF85888, "Psyko"),
    BUG("Insecte", 0xA8B820, "Plaie-Croix"),
    ROCK("Roche", 0xB8A038, "Lame de Roc"),
    GHOST("Spectre", 0x705898, "Ball'Ombre"),
    DRAGON("Dragon", 0x7038F8, "Colère"),
    STEEL("Acier", 0xB8B8D0, "Tête de Fer"),
    DARK("Ténèbres", 0x705848, "Mâchouille");

    public final String label;
    /** Couleur officielle du type, en 0xRRGGBB. */
    public final int rgb;
    public final String move;

    PType(String label, int rgb, String move) {
        this.label = label;
        this.rgb = rgb;
        this.move = move;
    }

    private static final double[][] CHART = new double[values().length][values().length];

    static {
        for (double[] row : CHART) java.util.Arrays.fill(row, 1.0);
        set(NORMAL, 0.5, ROCK, STEEL); set(NORMAL, 0, GHOST);
        set(FIRE, 0.5, FIRE, WATER, ROCK, DRAGON); set(FIRE, 2, GRASS, ICE, BUG, STEEL);
        set(WATER, 0.5, WATER, GRASS, DRAGON); set(WATER, 2, FIRE, GROUND, ROCK);
        set(ELECTRIC, 0.5, ELECTRIC, GRASS, DRAGON); set(ELECTRIC, 2, WATER, FLYING); set(ELECTRIC, 0, GROUND);
        set(GRASS, 0.5, FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL); set(GRASS, 2, WATER, GROUND, ROCK);
        set(ICE, 0.5, FIRE, WATER, ICE, STEEL); set(ICE, 2, GRASS, GROUND, FLYING, DRAGON);
        set(FIGHTING, 0.5, POISON, FLYING, PSYCHIC, BUG); set(FIGHTING, 2, NORMAL, ICE, ROCK, STEEL, DARK); set(FIGHTING, 0, GHOST);
        set(POISON, 0.5, POISON, GROUND, ROCK, GHOST); set(POISON, 2, GRASS); set(POISON, 0, STEEL);
        set(GROUND, 0.5, GRASS, BUG); set(GROUND, 2, FIRE, ELECTRIC, POISON, ROCK, STEEL); set(GROUND, 0, FLYING);
        set(FLYING, 0.5, ELECTRIC, ROCK, STEEL); set(FLYING, 2, GRASS, FIGHTING, BUG);
        set(PSYCHIC, 0.5, PSYCHIC, STEEL); set(PSYCHIC, 2, FIGHTING, POISON); set(PSYCHIC, 0, DARK);
        set(BUG, 0.5, FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL); set(BUG, 2, GRASS, PSYCHIC, DARK);
        set(ROCK, 0.5, FIGHTING, GROUND, STEEL); set(ROCK, 2, FIRE, ICE, FLYING, BUG);
        set(GHOST, 0, NORMAL); set(GHOST, 2, PSYCHIC, GHOST); set(GHOST, 0.5, DARK);
        set(DRAGON, 0.5, STEEL); set(DRAGON, 2, DRAGON);
        set(STEEL, 0.5, FIRE, WATER, ELECTRIC, STEEL); set(STEEL, 2, ICE, ROCK);
        set(DARK, 0.5, FIGHTING, DARK, STEEL); set(DARK, 2, PSYCHIC, GHOST);
    }

    private static void set(PType atk, double mult, PType... defs) {
        for (PType d : defs) CHART[atk.ordinal()][d.ordinal()] = mult;
    }

    /** Multiplicateur d'efficacité. Les immunités sont adoucies à x0.25 pour rester jouables en auto battler. */
    public static double effectiveness(PType atk, PType[] defender) {
        double m = 1.0;
        for (PType d : defender) {
            double v = CHART[atk.ordinal()][d.ordinal()];
            m *= v == 0 ? 0.25 : v;
        }
        return m;
    }
}
