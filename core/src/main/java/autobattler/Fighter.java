package autobattler;

import java.util.Map;

/** Un Pokémon pendant un combat : stats finales, position sur la grille hexagonale, mana et statuts. */
public final class Fighter {
    private static final double[] HP_STAR = {1.0, 1.8, 3.2};
    private static final double[] ATK_STAR = {1.0, 1.5, 2.2};
    /** Durée d'un déplacement d'une case, en secondes. */
    public static final double MOVE_TIME = 0.45;

    public final Unit unit;
    public final boolean player;
    /** Position locale de départ (0 = première ligne du camp). */
    public final int row, col;
    public final int range;
    public final Ability ability;

    // Stats
    public double maxHp, hp, atk, def, interval;
    public double maxMana, mana, manaMult = 1;
    public double dodge, lifesteal, regenPct, poisonPct, freezeChance;
    public boolean targetsWeakest;

    // Position : case actuelle (destination si en mouvement), case de départ du mouvement en cours
    public int cell, fromCell;
    public double moveT, moveDur = MOVE_TIME;
    public double timer;
    public Fighter target;

    // Statuts (durées en secondes)
    public double stun, sleep, paraTime, hasteTime, tauntTime;
    public double shield, shieldTime;
    public double poisonDps, poisonTime, burnDps, burnTime;
    public boolean alive = true;

    // Animation (utilisé uniquement par l'interface)
    public double lunge, flash;

    public Fighter(Unit unit, boolean player, int row, int col, Map<PType, Integer> syn) {
        this.unit = unit;
        this.player = player;
        this.row = row;
        this.col = col;
        Species s = unit.species;
        range = s.range();
        ability = s.ability();
        int st = unit.stars - 1;
        maxHp = (s.hp * 3 + 60) * HP_STAR[st];
        atk = s.atk * ATK_STAR[st];
        def = s.def;
        maxMana = ability.maxMana;
        mana = ability.startMana;
        double atkSpeed = 1.0;

        for (PType t : s.types) {
            Integer lvlObj = syn == null ? null : syn.get(t);
            int lvl = lvlObj == null ? 0 : lvlObj;
            if (lvl == 0) continue;
            switch (t) {
                case NORMAL: case GROUND: maxHp *= lvl >= 2 ? 1.4 : 1.2; break;
                case FIRE: atk *= lvl >= 2 ? 1.5 : 1.25; break;
                case WATER: manaMult += lvl >= 2 ? 0.6 : 0.3; break;
                case GRASS: regenPct = 0.03; break;
                case ELECTRIC: case BUG: atkSpeed += 0.3; break;
                case ICE: freezeChance = 0.2; break;
                case FIGHTING: lifesteal = 0.25; break;
                case POISON: poisonPct = lvl >= 2 ? 0.04 : 0.02; break;
                case FLYING: dodge = Math.max(dodge, lvl >= 2 ? 0.30 : 0.15); break;
                case PSYCHIC: mana += 30; break;
                case ROCK: def *= 1.35; break;
                case GHOST: dodge = Math.max(dodge, 0.20); break;
                case DRAGON: maxHp *= 1.25; atk *= 1.25; break;
                case STEEL: def *= 1.3; break;
                default: break;
            }
        }
        mana = Math.min(mana, maxMana - 1);
        targetsWeakest = s.moveType() == PType.GHOST;
        interval = 1.8 * 100.0 / (s.spe + 60) / atkSpeed;
        hp = maxHp;
        cell = fromCell = Hex.fromLocal(player, row, col);
    }

    public Species species() {
        return unit.species;
    }

    public boolean moving() {
        return moveT > 0;
    }

    /** Ne peut ni attaquer ni bouger (étourdi, gelé ou endormi). */
    public boolean disabled() {
        return stun > 0 || sleep > 0;
    }

    /** Multiplicateur de vitesse d'attaque actuel (paralysie, hâte). */
    public double speedMult() {
        double m = 1;
        if (paraTime > 0) m *= 0.6;
        if (hasteTime > 0) m *= 1.6;
        return m;
    }
}
