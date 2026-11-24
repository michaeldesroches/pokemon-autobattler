package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static autobattler.PType.*;

/** Toutes les espèces du jeu. Seules les formes de base sont vendues en boutique. */
public final class Pokedex {
    private static final Map<String, Species> ALL = new LinkedHashMap<String, Species>();
    private static final List<List<Species>> SHOP_BY_TIER = new ArrayList<List<Species>>();
    private static final Map<String, Ability> ABILITY = new java.util.HashMap<String, Ability>();
    private static final Map<String, Integer> RANGE = new java.util.HashMap<String, Integer>();
    private static final Map<String, Trait> ROLE = new java.util.HashMap<String, Trait>();
    private static final java.util.Set<String> STARTERS = new java.util.HashSet<String>(
            java.util.Arrays.asList("bulbi", "sala", "cara", "pika", "brin"));
    private static final java.util.Set<String> LEGENDARIES = new java.util.HashSet<String>(
            java.util.Arrays.asList("arti", "elec", "sulf", "mewt", "rayq", "dark"));

    static {
        // --- Tier 1 (coût 1) ---
        add("bulbizarre", "Bulbizarre", 1, 1, "bulbi", 45, 65, 57, 45, "herbizarre", GRASS, POISON);
        add("herbizarre", "Herbizarre", 2, 1, "bulbi", 60, 80, 71, 60, "florizarre", GRASS, POISON);
        add("florizarre", "Florizarre", 3, 1, "bulbi", 80, 100, 91, 80, null, GRASS, POISON);
        add("salameche", "Salamèche", 4, 1, "sala", 39, 60, 50, 65, "reptincel", FIRE);
        add("reptincel", "Reptincel", 5, 1, "sala", 58, 80, 65, 80, "dracaufeu", FIRE);
        add("dracaufeu", "Dracaufeu", 6, 1, "sala", 78, 109, 85, 100, null, FIRE, FLYING);
        add("carapuce", "Carapuce", 7, 1, "cara", 44, 50, 64, 43, "carabaffe", WATER);
        add("carabaffe", "Carabaffe", 8, 1, "cara", 59, 65, 80, 58, "tortank", WATER);
        add("tortank", "Tortank", 9, 1, "cara", 79, 85, 105, 78, null, WATER);
        // Larvibule et Étourmi remplacent Chenipan et Roucool (mieux dans leur rôle de Tireur et d'Assassin)
        add("larvibule", "Larvibule", 736, 1, "larv", 47, 62, 45, 46, "chrysapile", BUG);
        add("chrysapile", "Chrysapile", 737, 1, "larv", 57, 82, 85, 36, "lucanon", BUG, ELECTRIC);
        add("lucanon", "Lucanon", 738, 1, "larv", 77, 145, 82, 43, null, BUG, ELECTRIC);
        add("etourmi", "Étourmi", 396, 1, "etou", 40, 55, 30, 60, "etourvol", NORMAL, FLYING);
        add("etourvol", "Étourvol", 397, 1, "etou", 55, 75, 45, 80, "etouraptor", NORMAL, FLYING);
        add("etouraptor", "Étouraptor", 398, 1, "etou", 85, 120, 65, 100, null, NORMAL, FLYING);
        add("rattata", "Rattata", 19, 1, "ratt", 30, 56, 35, 72, "rattatac", NORMAL);
        add("rattatac", "Rattatac", 20, 1, "ratt", 55, 81, 65, 97, null, NORMAL);
        add("mystherbe", "Mystherbe", 43, 1, "myst", 45, 75, 60, 30, "ortide", GRASS, POISON);
        add("ortide", "Ortide", 44, 1, "myst", 60, 85, 72, 40, "rafflesia", GRASS, POISON);
        add("rafflesia", "Rafflesia", 45, 1, "myst", 75, 110, 88, 50, null, GRASS, POISON);
        add("ptitard", "Ptitard", 60, 1, "ptit", 40, 50, 40, 90, "tetarte", WATER);
        add("tetarte", "Têtarte", 61, 1, "ptit", 65, 65, 65, 90, "tartard", WATER);
        add("tartard", "Tartard", 62, 1, "ptit", 90, 95, 95, 70, null, WATER, FIGHTING);
        add("machoc", "Machoc", 66, 1, "mach", 70, 80, 42, 35, "machopeur", FIGHTING);
        add("machopeur", "Machopeur", 67, 1, "mach", 80, 100, 65, 45, "mackogneur", FIGHTING);
        add("mackogneur", "Mackogneur", 68, 1, "mach", 90, 130, 82, 55, null, FIGHTING);
        add("racaillou", "Racaillou", 74, 1, "raca", 40, 80, 65, 20, "gravalanch", ROCK, GROUND);
        add("gravalanch", "Gravalanch", 75, 1, "raca", 55, 95, 80, 35, "grolem", ROCK, GROUND);
        add("grolem", "Grolem", 76, 1, "raca", 80, 120, 95, 45, null, ROCK, GROUND);
        // générations suivantes (choisies pour leur rôle)
        add("galekid", "Galekid", 304, 1, "gale", 50, 70, 70, 30, "galegon", STEEL, ROCK);
        add("galegon", "Galegon", 305, 1, "gale", 60, 90, 95, 40, "galeking", STEEL, ROCK);
        add("galeking", "Galeking", 306, 1, "gale", 70, 110, 120, 50, null, STEEL, ROCK);
        add("tarsal", "Tarsal", 280, 1, "tars", 28, 45, 30, 40, "kirlia", PSYCHIC);
        add("kirlia", "Kirlia", 281, 1, "tars", 38, 65, 45, 50, "gardevoir", PSYCHIC);
        add("gardevoir", "Gardevoir", 282, 1, "tars", 68, 110, 85, 80, null, PSYCHIC);
        add("remoraid", "Rémoraid", 223, 1, "remo", 35, 65, 35, 65, "octillery", WATER);
        add("octillery", "Octillery", 224, 1, "remo", 75, 105, 75, 45, null, WATER);
        add("brindibou", "Brindibou", 722, 1, "brin", 68, 55, 52, 42, "effleche", GRASS, FLYING);
        add("effleche", "Efflèche", 723, 1, "brin", 78, 75, 72, 52, "archeduc", GRASS, FLYING);
        add("archeduc", "Archéduc", 724, 1, "brin", 78, 107, 87, 70, null, GRASS, GHOST);

        // --- Tier 2 (coût 2) ---
        add("pikachu", "Pikachu", 25, 2, "pika", 35, 55, 45, 90, "raichu", ELECTRIC);
        add("raichu", "Raichu", 26, 2, "pika", 60, 90, 65, 110, null, ELECTRIC);
        add("goupix", "Goupix", 37, 2, "goup", 38, 50, 65, 65, "feunard", FIRE);
        add("feunard", "Feunard", 38, 2, "goup", 73, 81, 100, 100, null, FIRE);
        add("caninos", "Caninos", 58, 2, "cani", 55, 70, 50, 60, "arcanin", FIRE);
        add("arcanin", "Arcanin", 59, 2, "cani", 90, 110, 80, 95, null, FIRE);
        add("abra", "Abra", 63, 2, "abra", 25, 105, 40, 90, "kadabra", PSYCHIC);
        add("kadabra", "Kadabra", 64, 2, "abra", 40, 120, 55, 105, "alakazam", PSYCHIC);
        add("alakazam", "Alakazam", 65, 2, "abra", 55, 135, 75, 120, null, PSYCHIC);
        add("magneti", "Magnéti", 81, 2, "magn", 25, 95, 63, 45, "magneton", ELECTRIC, STEEL);
        add("magneton", "Magnéton", 82, 2, "magn", 50, 120, 83, 70, "magnezone", ELECTRIC, STEEL);
        add("magnezone", "Magnézone", 462, 2, "magn", 70, 130, 102, 60, null, ELECTRIC, STEEL);
        // Obalie remplace Otaria (un vrai tank Glace/Eau pour les Gardiens)
        add("obalie", "Obalie", 363, 2, "obal", 70, 55, 50, 25, "phogleck", ICE, WATER);
        add("phogleck", "Phogleck", 364, 2, "obal", 90, 75, 70, 45, "kaimorse", ICE, WATER);
        add("kaimorse", "Kaimorse", 365, 2, "obal", 110, 95, 90, 65, null, ICE, WATER);
        add("fantominus", "Fantominus", 92, 2, "fant", 30, 100, 35, 80, "spectrum", GHOST, POISON);
        add("spectrum", "Spectrum", 93, 2, "fant", 45, 115, 50, 95, "ectoplasma", GHOST, POISON);
        add("ectoplasma", "Ectoplasma", 94, 2, "fant", 60, 130, 68, 110, null, GHOST, POISON);
        add("onix", "Onix", 95, 2, "onix", 35, 45, 102, 70, "steelix", ROCK, GROUND);
        add("steelix", "Steelix", 208, 2, "onix", 75, 85, 132, 30, null, STEEL, GROUND);
        add("osselait", "Osselait", 104, 2, "osse", 50, 50, 75, 35, "ossatueur", GROUND);
        add("ossatueur", "Ossatueur", 105, 2, "osse", 60, 80, 90, 45, null, GROUND);
        add("riolu", "Riolu", 447, 2, "riol", 40, 70, 40, 60, "lucario", FIGHTING);
        add("lucario", "Lucario", 448, 2, "riol", 70, 115, 70, 90, null, FIGHTING, STEEL);
        add("farfuret", "Farfuret", 215, 2, "farf", 55, 95, 65, 115, "dimoret", DARK, ICE);
        add("dimoret", "Dimoret", 461, 2, "farf", 70, 120, 75, 125, null, DARK, ICE);
        add("absol", "Absol", 359, 2, "absol", 65, 130, 60, 75, null, DARK);

        // --- Tier 3 (coût 3) ---
        add("insecateur", "Insécateur", 123, 3, "inse", 70, 110, 80, 105, "cizayox", BUG, FLYING);
        add("cizayox", "Cizayox", 212, 3, "inse", 70, 130, 90, 65, null, BUG, STEEL);
        add("lokhlass", "Lokhlass", 131, 3, "lokh", 130, 85, 85, 60, null, WATER, ICE);
        add("ronflex", "Ronflex", 143, 3, "ronf", 160, 110, 88, 30, null, NORMAL);
        add("minidraco", "Minidraco", 147, 3, "mini", 41, 64, 50, 50, "draco", DRAGON);
        add("draco", "Draco", 148, 3, "mini", 61, 84, 65, 70, "dracolosse", DRAGON);
        add("dracolosse", "Dracolosse", 149, 3, "mini", 91, 134, 97, 80, null, DRAGON, FLYING);
        add("griknot", "Griknot", 443, 3, "grik", 58, 70, 45, 42, "carmache", DRAGON, GROUND);
        add("carmache", "Carmache", 444, 3, "grik", 68, 90, 60, 82, "carchacrok", DRAGON, GROUND);
        add("carchacrok", "Carchacrok", 445, 3, "grik", 108, 130, 90, 102, null, DRAGON, GROUND);

        // --- Tier 4 : légendaires (coût 5, 4 exemplaires, pas d'évolution) ---
        add("artikodin", "Artikodin", 144, 4, "arti", 90, 95, 112, 85, null, ICE, FLYING);
        add("electhor", "Électhor", 145, 4, "elec", 90, 125, 87, 100, null, ELECTRIC, FLYING);
        add("sulfura", "Sulfura", 146, 4, "sulf", 90, 125, 87, 90, null, FIRE, FLYING);
        add("mewtwo", "Mewtwo", 150, 4, "mewt", 106, 154, 90, 130, null, PSYCHIC);
        add("rayquaza", "Rayquaza", 384, 4, "rayq", 105, 150, 90, 95, null, DRAGON, FLYING);
        add("darkrai", "Darkrai", 491, 4, "dark", 70, 135, 90, 125, null, DARK);

        // --- Kit de chaque lignée : portée d'attaque (en cases) et capacité unique ---
        kit("arti", 1, Ability.GRAND_FROID);
        kit("elec", 4, Ability.FATAL_FOUDRE);
        kit("sulf", 3, Ability.DEFLAGRATION);
        kit("mewt", 3, Ability.FRAPPE_PSY);
        kit("rayq", 1, Ability.DRACO_ASCENSION);
        kit("dark", 1, Ability.TROU_NOIR);
        kit("bulbi", 2, Ability.GIGA_SANGSUE);
        kit("sala", 2, Ability.LANCE_FLAMMES);
        kit("cara", 1, Ability.REPLI);
        kit("larv", 3, Ability.ELECANON);
        kit("etou", 1, Ability.RAPACE);
        kit("ratt", 1, Ability.CROC_FATAL);
        kit("myst", 3, Ability.POUDRE_TOXIK);
        kit("ptit", 1, Ability.HYPNOSE);
        kit("mach", 1, Ability.CLOSE_COMBAT);
        kit("raca", 1, Ability.EBOULEMENT);
        kit("pika", 3, Ability.TONNERRE);
        kit("goup", 3, Ability.FEU_FOLLET);
        kit("cani", 1, Ability.VITESSE_EXTREME);
        kit("abra", 4, Ability.PSYKO);
        kit("magn", 3, Ability.CAGE_ECLAIR);
        kit("obal", 1, Ability.LASER_GLACE);
        kit("fant", 2, Ability.BALL_OMBRE);
        kit("onix", 1, Ability.PROVOC);
        kit("osse", 1, Ability.OSMERANG);
        kit("inse", 1, Ability.TRANCHE);
        kit("lokh", 3, Ability.BLIZZARD);
        kit("ronf", 1, Ability.REPOS);
        kit("mini", 2, Ability.OURAGAN);
        kit("gale", 1, Ability.TETE_DE_FER);
        kit("tars", 3, Ability.VOEU);
        kit("remo", 3, Ability.OCTAZOOKA);
        kit("brin", 4, Ability.TISSE_OMBRE);
        kit("riol", 1, Ability.AURASPHERE);
        kit("farf", 1, Ability.COUP_BAS);
        kit("absol", 1, Ability.TRANCHE_NUIT);
        kit("grik", 1, Ability.SEISME);
        // --- Classe (rôle) de chaque lignée ---
        role(Trait.GUARDIAN, "arti", "cara", "raca", "onix", "ronf", "obal", "gale");
        role(Trait.FIGHTER, "rayq", "mach", "osse", "cani", "mini", "riol", "grik", "ptit");
        role(Trait.ASSASSIN, "dark", "fant", "etou", "ratt", "inse", "farf", "absol");
        role(Trait.MYSTIC, "sulf", "mewt", "abra", "magn", "lokh", "myst", "goup", "tars");
        role(Trait.MARKSMAN, "elec", "pika", "sala", "larv", "bulbi", "remo", "brin");
        for (Species s : ALL.values()) {
            if (!ABILITY.containsKey(s.line)) throw new IllegalStateException("Lignée sans kit : " + s.line);
            if (!ROLE.containsKey(s.line)) throw new IllegalStateException("Lignée sans classe : " + s.line);
        }

        for (int i = 0; i < GameState.TIERS; i++) SHOP_BY_TIER.add(new ArrayList<Species>());
        List<String> evolved = new ArrayList<String>();
        for (Species s : ALL.values()) if (s.evolvesTo != null) evolved.add(s.evolvesTo);
        for (Species s : ALL.values()) {
            if (!evolved.contains(s.key)) SHOP_BY_TIER.get(s.tier - 1).add(s);
        }
    }

    private static void add(String key, String name, int dex, int tier, String line,
                            int hp, int atk, int def, int spe, String evo, PType... types) {
        ALL.put(key, new Species(key, name, dex, tier, line, hp, atk, def, spe, evo, types));
    }

    private static void role(Trait role, String... lines) {
        for (String l : lines) ROLE.put(l, role);
    }

    /** Classe (rôle) de la lignée. */
    public static Trait role(String line) {
        return ROLE.get(line);
    }

    /** Vrai si la lignée est un Pokémon de départ (Bulbizarre, Salamèche, Carapuce, Pikachu, Brindibou). */
    public static boolean isStarter(String line) {
        return STARTERS.contains(line);
    }

    /** Vrai si la lignée est légendaire (tier 4) : Artikodin, Électhor, Sulfura, Mewtwo, Rayquaza, Darkrai. */
    public static boolean isLegendary(String line) {
        return LEGENDARIES.contains(line);
    }

    private static void kit(String line, int range, Ability ability) {
        RANGE.put(line, range);
        ABILITY.put(line, ability);
    }

    /** Forme de base (vendue en boutique) d'une lignée. */
    public static Species base(String line) {
        for (List<Species> tier : SHOP_BY_TIER)
            for (Species s : tier) if (s.line.equals(line)) return s;
        throw new IllegalArgumentException("Lignée inconnue : " + line);
    }

    /** Portée d'attaque en cases (1 = corps à corps) de la lignée. */
    public static int range(String line) {
        return RANGE.get(line);
    }

    /** Capacité unique de la lignée. */
    public static Ability ability(String line) {
        return ABILITY.get(line);
    }

    public static Species get(String key) {
        Species s = ALL.get(key);
        if (s == null) throw new IllegalArgumentException("Espèce inconnue : " + key);
        return s;
    }

    /** Formes de base vendues en boutique pour un tier donné (1 à 3). */
    public static List<Species> shopPool(int tier) {
        return Collections.unmodifiableList(SHOP_BY_TIER.get(tier - 1));
    }

    public static java.util.Collection<Species> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    private Pokedex() {
    }
}
