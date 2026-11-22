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
    // Objets
    public double abilityPower = 1, damageAmp = 1, critChance, thorns, omnivamp, manaOnHit, abilityStack;
    public double sitrus, manaRefund, stunChance, armorPen, healOnCast, shieldOnCast, hasteOnCast, startShield;
    public boolean ccImmune, focusSash;
    /** Classe Assassin active : saute sur l'arrière-ligne adverse au début du combat. */
    public boolean leapsAtStart;
    public int speedStacks;
    public double speedStackValue;
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
        this(unit, player, row, col, syn, null);
    }

    public Fighter(Unit unit, boolean player, int row, int col, Map<PType, Integer> syn, Map<Trait, Integer> traits) {
        this.unit = unit;
        this.player = player;
        this.row = row;
        this.col = col;
        Species s = unit.species;
        int bonusRange = 0;
        Integer marks = traits == null ? null : traits.get(Trait.MARKSMAN);
        if (marks != null && marks >= 2 && s.role() == Trait.MARKSMAN) bonusRange = 1;
        range = s.range() + bonusRange;
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
                case STEEL: def *= lvl >= 2 ? 1.5 : 1.3; break;
                case DARK: critChance += 0.25; break;
                default: break;
            }
        }
        for (Trait t : s.traits()) {
            Integer lvlObj = traits == null ? null : traits.get(t);
            int lvl = lvlObj == null ? 0 : lvlObj;
            if (lvl == 0) continue;
            switch (t) {
                case GUARDIAN: startShield = Math.max(startShield, lvl >= 2 ? 0.35 : 0.20); break;
                case FIGHTER:
                    maxHp *= lvl >= 2 ? 1.30 : 1.15;
                    lifesteal = Math.max(lifesteal, lvl >= 2 ? 0.20 : 0.10);
                    break;
                case ASSASSIN:
                    leapsAtStart = true;
                    critChance += lvl >= 2 ? 0.50 : 0.30;
                    damageAmp += lvl >= 2 ? 0.30 : 0.15;
                    break;
                case MYSTIC:
                    abilityPower += lvl >= 2 ? 0.70 : 0.35;
                    mana += 20;
                    break;
                case MARKSMAN: atkSpeed += lvl >= 2 ? 0.30 : 0.20; break;
                case STARTER:
                    maxHp *= 1.20;
                    atk *= 1.20;
                    break;
                default: break;
            }
        }
        for (Item it : unit.items) applyItem(it);
        atkSpeed += itemSpeed;
        mana = Math.min(mana, maxMana - 1);
        targetsWeakest = s.moveType() == PType.GHOST;
        interval = 1.8 * 100.0 / (s.spe + 60) / atkSpeed;
        hp = maxHp;
        cell = fromCell = Hex.fromLocal(player, row, col);
    }

    private double itemSpeed;

    private void applyItem(Item it) {
        atk *= 1 + it.atkPct;
        abilityPower += it.abilityPct;
        itemSpeed += it.speedPct;
        def *= 1 + it.defPct;
        maxHp += it.hp;
        mana += it.mana;
        double v = it.value;
        switch (it.effect) {
            case DAMAGE_AMP: damageAmp += v; break;
            case CRIT: critChance += v; break;
            case ATKSPEED_STACK: speedStackValue = Math.max(speedStackValue, v); break;
            case THORNS: thorns += v; break;
            case OMNIVAMP: omnivamp += v; break;
            case MANA_ON_HIT: manaOnHit += v; break;
            case ABILITY_AMP: abilityPower += v; break;
            case ABILITY_STACK: abilityStack += v; break;
            case SHIELD_START: startShield = Math.max(startShield, v); break;
            case SITRUS: sitrus = Math.max(sitrus, v); break;
            case MANA_REFUND: manaRefund += v; break;
            case STUN_CHANCE: stunChance += v; break;
            case ARMOR_PEN: armorPen = Math.min(0.8, armorPen + v); break;
            case HEAL_ON_CAST: healOnCast += v; break;
            case CC_IMMUNE: ccImmune = true; break;
            case FOCUS_SASH: focusSash = true; break;
            case SHIELD_ON_CAST: shieldOnCast += v; break;
            case REGEN: regenPct += v; break;
            case MANA_REDUCE: maxMana *= 1 - v; break;
            case HASTE_ON_CAST: hasteOnCast = Math.max(hasteOnCast, v); break;
            case ATTACK_SPEED: itemSpeed += v; break;
            default: break;
        }
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
        return m * (1 + speedStacks * speedStackValue);
    }
}
