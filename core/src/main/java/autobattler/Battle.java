package autobattler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Simulation déterministe (à graine donnée) d'un combat automatique. */
public final class Battle {
    public static final double TIME_LIMIT = 45;
    public static final double STEP = 0.05;

    public enum Kind { ATTACK, DODGE, FAINT, FREEZE }

    public static final class Event {
        public final Kind kind;
        public final Fighter src, tgt;
        public final double amount, mult;
        public final boolean special;

        Event(Kind kind, Fighter src, Fighter tgt, double amount, double mult, boolean special) {
            this.kind = kind;
            this.src = src;
            this.tgt = tgt;
            this.amount = amount;
            this.mult = mult;
            this.special = special;
        }
    }

    public final List<Fighter> fighters = new ArrayList<Fighter>();
    private final Random rng;
    private final List<Event> events = new ArrayList<Event>();
    private double time;
    private double acc;
    /** 0 = en cours, 1 = victoire du joueur, -1 = défaite (ou temps écoulé). */
    public int result;

    public Battle(List<Fighter> player, List<Fighter> enemy, long seed) {
        fighters.addAll(player);
        fighters.addAll(enemy);
        rng = new Random(seed);
        for (Fighter f : fighters) f.timer = f.interval * (0.3 + 0.5 * rng.nextDouble());
        checkEnd();
    }

    public double time() {
        return time;
    }

    /** Avance la simulation de dt secondes (par pas fixes) et renvoie les événements produits. */
    public List<Event> advance(double dt) {
        events.clear();
        acc += dt;
        while (acc >= STEP && result == 0) {
            acc -= STEP;
            tick(STEP);
        }
        return new ArrayList<Event>(events);
    }

    /** Joue le combat jusqu'au bout. */
    public void runToEnd() {
        while (result == 0) tick(STEP);
    }

    private void tick(double dt) {
        time += dt;
        for (Fighter f : fighters) {
            if (!f.alive) continue;
            if (f.regenPct > 0) f.hp = Math.min(f.maxHp, f.hp + f.regenPct * f.maxHp * dt);
            if (f.poisonTime > 0) {
                f.poisonTime -= dt;
                damage(null, f, f.poisonDps * dt);
                if (!f.alive) continue;
            }
            if (f.stun > 0) {
                f.stun -= dt;
                continue;
            }
            f.timer -= dt;
            if (f.timer <= 0) {
                Fighter t = target(f);
                if (t != null) attack(f, t);
                f.timer += f.interval;
            }
        }
        checkEnd();
        if (result == 0 && time >= TIME_LIMIT) result = -1;
    }

    private void checkEnd() {
        boolean p = false, e = false;
        for (Fighter f : fighters) {
            if (!f.alive) continue;
            if (f.player) p = true; else e = true;
        }
        if (!e) result = 1;
        else if (!p) result = -1;
    }

    Fighter target(Fighter f) {
        Fighter best = null;
        boolean frontAlive = false;
        for (Fighter o : fighters) if (o.alive && o.player != f.player && o.row == 0) frontAlive = true;
        for (Fighter o : fighters) {
            if (!o.alive || o.player == f.player) continue;
            if (f.targetsWeakest) {
                if (best == null || o.hp < best.hp) best = o;
                continue;
            }
            if (frontAlive && o.row != 0) continue;
            if (best == null) {
                best = o;
                continue;
            }
            int d = Math.abs(o.col - f.col), bd = Math.abs(best.col - f.col);
            if (d < bd || (d == bd && o.hp < best.hp)) best = o;
        }
        return best;
    }

    private void attack(Fighter f, Fighter t) {
        boolean special = f.energy >= 100;
        PType mt = f.species().moveType();
        if (special) {
            f.energy = 0;
            if (mt == PType.GROUND) {
                for (Fighter o : fighters) if (o.alive && o.player != f.player) hit(f, o, 1.2, true);
            } else {
                hit(f, t, mt == PType.PSYCHIC ? 3.0 : 2.5, true);
                if (mt == PType.GRASS) f.hp = Math.min(f.maxHp, f.hp + 0.2 * f.maxHp);
            }
        } else {
            f.energy = Math.min(100, f.energy + 20 * f.energyMult);
            hit(f, t, 1.0, false);
        }
    }

    private void hit(Fighter f, Fighter t, double power, boolean special) {
        if (rng.nextDouble() < t.dodge) {
            events.add(new Event(Kind.DODGE, f, t, 0, 1, special));
            return;
        }
        double mult = PType.effectiveness(f.species().moveType(), t.species().types);
        double dmg = 16 * power * f.atk / t.def * mult * (0.9 + 0.2 * rng.nextDouble());
        events.add(new Event(Kind.ATTACK, f, t, dmg, mult, special));
        damage(f, t, dmg);
        t.energy = Math.min(100, t.energy + 6 * t.energyMult);
        if (f.lifesteal > 0) f.hp = Math.min(f.maxHp, f.hp + dmg * f.lifesteal);
        if (f.poisonPct > 0 && t.alive) {
            t.poisonDps = Math.max(t.poisonDps, f.poisonPct * t.maxHp);
            t.poisonTime = 3;
        }
        if (f.freezeChance > 0 && t.alive && rng.nextDouble() < f.freezeChance) {
            t.stun = 1;
            events.add(new Event(Kind.FREEZE, f, t, 0, 1, false));
        }
    }

    private void damage(Fighter src, Fighter t, double dmg) {
        if (!t.alive) return;
        t.hp -= dmg;
        if (t.hp <= 0) {
            t.hp = 0;
            t.alive = false;
            events.add(new Event(Kind.FAINT, src, t, 0, 1, false));
        }
    }

    public int aliveCount(boolean player) {
        int n = 0;
        for (Fighter f : fighters) if (f.alive && f.player == player) n++;
        return n;
    }
}
