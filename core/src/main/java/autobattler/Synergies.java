package autobattler;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Synergies de type : on compte les lignées d'évolution distinctes de chaque type sur le plateau.
 * Les bonus ne s'appliquent qu'aux Pokémon qui possèdent le type.
 */
public final class Synergies {

    /** Paliers (nombre de lignées distinctes) pour chaque type. */
    public static int[] thresholds(PType t) {
        switch (t) {
            case NORMAL: case FIRE: case POISON: case GROUND: case FLYING: return new int[]{2, 3};
            case WATER: return new int[]{2, 4};
            case PSYCHIC: case GHOST: case DRAGON: case STEEL: return new int[]{1};
            default: return new int[]{2};
        }
    }

    public static String describe(PType t, int level) {
        switch (t) {
            case NORMAL: return level >= 2 ? "+40% PV" : "+20% PV";
            case FIRE: return level >= 2 ? "+50% Attaque" : "+25% Attaque";
            case WATER: return level >= 2 ? "+60% énergie gagnée" : "+30% énergie gagnée";
            case GRASS: return "Régénère 3% PV/s";
            case ELECTRIC: return "+30% vitesse d'attaque";
            case ICE: return "20% de geler la cible 1s";
            case FIGHTING: return "Vol de vie 25%";
            case POISON: return level >= 2 ? "Poison : 4% PV/s (3s)" : "Poison : 2% PV/s (3s)";
            case GROUND: return level >= 2 ? "+40% PV" : "+20% PV";
            case FLYING: return level >= 2 ? "30% d'esquive" : "15% d'esquive";
            case PSYCHIC: return "Commence avec 50 énergie";
            case BUG: return "+30% vitesse d'attaque";
            case ROCK: return "+35% Défense";
            case GHOST: return "20% d'esquive, vise le plus faible";
            case DRAGON: return "+25% PV et Attaque";
            case STEEL: return "+30% Défense";
            default: return "";
        }
    }

    /** Nombre de lignées distinctes par type. */
    public static Map<PType, Integer> counts(List<Unit> units) {
        Map<PType, Set<String>> lines = new EnumMap<PType, Set<String>>(PType.class);
        for (Unit u : units) {
            for (PType t : u.species.types) {
                Set<String> s = lines.get(t);
                if (s == null) {
                    s = new HashSet<String>();
                    lines.put(t, s);
                }
                s.add(u.species.line);
            }
        }
        Map<PType, Integer> res = new EnumMap<PType, Integer>(PType.class);
        for (Map.Entry<PType, Set<String>> e : lines.entrySet()) res.put(e.getKey(), e.getValue().size());
        return res;
    }

    /** Niveau de synergie actif (0 = inactif) pour chaque type. */
    public static Map<PType, Integer> levels(List<Unit> units) {
        Map<PType, Integer> res = new EnumMap<PType, Integer>(PType.class);
        for (Map.Entry<PType, Integer> e : counts(units).entrySet()) {
            int lvl = 0;
            for (int th : thresholds(e.getKey())) if (e.getValue() >= th) lvl++;
            if (lvl > 0) res.put(e.getKey(), lvl);
        }
        return res;
    }

    private Synergies() {
    }
}
