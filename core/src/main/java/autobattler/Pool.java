package autobattler;

import java.util.HashMap;
import java.util.Map;

/**
 * Exemplaires restants de chaque Pokémon de base. Partagé par les 8 joueurs d'un lobby :
 * ce qu'un bot achète ne peut plus sortir dans la boutique des autres.
 */
public final class Pool {
    private final Map<Species, Integer> counts = new HashMap<Species, Integer>();

    public Pool() {
        fill();
    }

    /** Remet tous les exemplaires (nouvelle partie). */
    public void fill() {
        counts.clear();
        for (int tier = 1; tier <= 3; tier++)
            for (Species s : Pokedex.shopPool(tier)) counts.put(s, GameState.POOL_SIZE[tier - 1]);
    }

    public int count(Species base) {
        Integer n = counts.get(base);
        return n == null ? 0 : n;
    }

    public void add(Species base, int n) {
        counts.put(base, count(base) + n);
    }

    public boolean take(Species base) {
        int n = count(base);
        if (n <= 0) return false;
        counts.put(base, n - 1);
        return true;
    }
}
