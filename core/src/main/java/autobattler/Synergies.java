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
            case NORMAL: case FIRE: case POISON: case GROUND: case FLYING: case STEEL: return new int[]{2, 3};
            case WATER: return new int[]{2, 4};
            case PSYCHIC: case GHOST: case DRAGON: return new int[]{1};
            default: return new int[]{2};
        }
    }

    public static String describe(PType t, int level) {
        switch (t) {
            case NORMAL: return level >= 2 ? "+40% PV" : "+20% PV";
            case FIRE: return level >= 2 ? "+50% Attaque" : "+25% Attaque";
            case WATER: return level >= 2 ? "+60% mana gagné" : "+30% mana gagné";
            case GRASS: return "Régénère 3% PV/s";
            case ELECTRIC: return "+30% vitesse d'attaque";
            case ICE: return "20% de geler la cible 1s";
            case FIGHTING: return "Vol de vie 25%";
            case POISON: return level >= 2 ? "Poison : 4% PV/s (3s)" : "Poison : 2% PV/s (3s)";
            case GROUND: return level >= 2 ? "+40% PV" : "+20% PV";
            case FLYING: return level >= 2 ? "30% d'esquive" : "15% d'esquive";
            case PSYCHIC: return "+30 mana au départ";
            case BUG: return "+30% vitesse d'attaque";
            case ROCK: return "+35% Défense";
            case GHOST: return "20% d'esquive, vise le plus faible";
            case DRAGON: return "+25% PV et Attaque";
            case STEEL: return level >= 2 ? "+50% Défense" : "+30% Défense";
            case DARK: return "25% de coups critiques";
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

    /** Nombre de lignées distinctes par classe. */
    public static Map<Trait, Integer> traitCounts(List<Unit> units) {
        Map<Trait, Set<String>> lines = new EnumMap<Trait, Set<String>>(Trait.class);
        for (Unit u : units) {
            for (Trait t : u.species.traits()) {
                Set<String> s = lines.get(t);
                if (s == null) {
                    s = new HashSet<String>();
                    lines.put(t, s);
                }
                s.add(u.species.line);
            }
        }
        Map<Trait, Integer> res = new EnumMap<Trait, Integer>(Trait.class);
        for (Map.Entry<Trait, Set<String>> e : lines.entrySet()) res.put(e.getKey(), e.getValue().size());
        return res;
    }

    /** Niveau de synergie actif (0 = inactif) pour chaque classe. */
    public static Map<Trait, Integer> traitLevels(List<Unit> units) {
        Map<Trait, Integer> res = new EnumMap<Trait, Integer>(Trait.class);
        for (Map.Entry<Trait, Integer> e : traitCounts(units).entrySet()) {
            int lvl = 0;
            for (int th : e.getKey().thresholds()) if (e.getValue() >= th) lvl++;
            if (lvl > 0) res.put(e.getKey(), lvl);
        }
        return res;
    }

    private Synergies() {
    }
}
