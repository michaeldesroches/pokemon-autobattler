package autobattler;

import java.util.Map;

/** Un Pokémon pendant un combat (copie de l'unité avec stats finales et état de combat). */
public final class Fighter {
    private static final double[] HP_STAR = {1.0, 1.8, 3.2};
    private static final double[] ATK_STAR = {1.0, 1.5, 2.2};

    public final Unit unit;
    public final boolean player;
    /** 0 = première ligne, 1 = arrière. */
    public final int row;
    public final int col;

    public double maxHp, hp, atk, def, interval;
    public double timer, energy, energyMult = 1;
    public double dodge, lifesteal, regenPct, poisonPct, freezeChance;
    public double stun, poisonDps, poisonTime;
    public boolean targetsWeakest;
    public boolean alive = true;

    // Animation (utilisé uniquement par l'interface)
    public double lunge, flash;

    public Fighter(Unit unit, boolean player, int row, int col, Map<PType, Integer> syn) {
        this.unit = unit;
        this.player = player;
        this.row = row;
        this.col = col;
        Species s = unit.species;
        int st = unit.stars - 1;
        maxHp = (s.hp * 3 + 60) * HP_STAR[st];
        atk = s.atk * ATK_STAR[st];
        def = s.def;
        double atkSpeed = 1.0;

        for (PType t : s.types) {
            Integer lvlObj = syn == null ? null : syn.get(t);
            int lvl = lvlObj == null ? 0 : lvlObj;
            if (lvl == 0) continue;
            switch (t) {
                case NORMAL: case GROUND: maxHp *= lvl >= 2 ? 1.4 : 1.2; break;
                case FIRE: atk *= lvl >= 2 ? 1.5 : 1.25; break;
                case WATER: energyMult += lvl >= 2 ? 0.6 : 0.3; break;
                case GRASS: regenPct = 0.03; break;
                case ELECTRIC: case BUG: atkSpeed += 0.3; break;
                case ICE: freezeChance = 0.2; break;
                case FIGHTING: lifesteal = 0.25; break;
                case POISON: poisonPct = lvl >= 2 ? 0.04 : 0.02; break;
                case FLYING: dodge = Math.max(dodge, lvl >= 2 ? 0.30 : 0.15); break;
                case PSYCHIC: energy = 50; break;
                case ROCK: def *= 1.35; break;
                case GHOST: dodge = Math.max(dodge, 0.20); break;
                case DRAGON: maxHp *= 1.25; atk *= 1.25; break;
                case STEEL: def *= 1.3; break;
                default: break;
            }
        }
        targetsWeakest = s.moveType() == PType.GHOST;
        interval = 1.8 * 100.0 / (s.spe + 60) / atkSpeed;
        hp = maxHp;
    }

    public Species species() {
        return unit.species;
    }
}
