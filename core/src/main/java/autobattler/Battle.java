package autobattler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation déterministe (à graine donnée) d'un combat façon TFT sur grille hexagonale :
 * chaque Pokémon vise l'ennemi le plus proche, s'en approche (A*) jusqu'à être à portée,
 * attaque, accumule du mana et lance sa capacité unique quand le mana est plein.
 */
public final class Battle {
    public static final double TIME_LIMIT = 45;
    public static final double STEP = 0.05;
    /** Facteur de dégâts : dégâts = DAMAGE_BASE × puissance × Atq / Déf × efficacité. */
    public static final double DAMAGE_BASE = 22;
    /** Prolongation : au-delà de ce temps, les dégâts augmentent de 20% par seconde (x2 à 35 s, x4 à 45 s). */
    public static final double OVERTIME = 30;

    public enum Kind { ATTACK, DODGE, FAINT, STATUS, ABILITY, AREA, HEAL }

    public static final class Event {
        public final Kind kind;
        public final Fighter src, tgt;
        public final double amount, mult;
        /** Dégâts issus d'une capacité (et non d'une attaque normale). */
        public final boolean special;
        public final String text;
        public final int cell, radius;
        public final PType type;

        Event(Kind kind, Fighter src, Fighter tgt, double amount, double mult, boolean special,
              String text, int cell, int radius, PType type) {
            this.kind = kind;
            this.src = src;
            this.tgt = tgt;
            this.amount = amount;
            this.mult = mult;
            this.special = special;
            this.text = text;
            this.cell = cell;
            this.radius = radius;
            this.type = type;
        }
    }

    public final List<Fighter> fighters = new ArrayList<Fighter>();
    private final Random rng;
    private final List<Event> events = new ArrayList<Event>();
    private final boolean[] occupied = new boolean[Hex.CELLS];
    private double time;
    private double acc;
    /** 0 = en cours, 1 = victoire du joueur, -1 = défaite (ou temps écoulé). */
    public int result;

    public Battle(List<Fighter> player, List<Fighter> enemy, long seed) {
        fighters.addAll(player);
        fighters.addAll(enemy);
        rng = new Random(seed);
        for (Fighter f : fighters) {
            f.timer = f.interval * (0.3 + 0.5 * rng.nextDouble());
            occupied[f.cell] = true;
        }
        checkEnd();
    }

    public double time() {
        return time;
    }

    /** Multiplicateur de dégâts de la prolongation (1 avant {@link #OVERTIME}). */
    public double overtimeMult() {
        return time > OVERTIME ? 1 + (time - OVERTIME) / 5 : 1;
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
        events.clear();
    }

    public int aliveCount(boolean player) {
        int n = 0;
        for (Fighter f : fighters) if (f.alive && f.player == player) n++;
        return n;
    }

    // ================================================================== boucle

    private void tick(double dt) {
        time += dt;
        for (Fighter f : fighters) {
            if (!f.alive) continue;
            tickStatuses(f, dt);
            if (!f.alive) continue;
            if (f.moving()) {
                f.moveT = Math.max(0, f.moveT - dt);
                if (!f.moving()) f.fromCell = f.cell;
                continue;
            }
            if (f.disabled()) continue;
            f.timer = Math.max(0, f.timer - dt * f.speedMult());

            Fighter t = chooseTarget(f);
            f.target = t;
            if (t == null) continue;
            if (Hex.distance(f.cell, t.cell) <= f.range) {
                if (f.timer <= 0) {
                    if (f.mana >= f.maxMana) cast(f, t);
                    else basicAttack(f, t);
                    f.timer += f.interval;
                }
            } else {
                int step = Hex.nextStep(f.cell, t.cell, f.range, occupied);
                if (step >= 0) moveTo(f, step, Fighter.MOVE_TIME);
            }
        }
        checkEnd();
        if (result == 0 && time >= TIME_LIMIT) result = -1;
    }

    private void tickStatuses(Fighter f, double dt) {
        if (f.regenPct > 0) heal(f, f.regenPct * f.maxHp * dt, false);
        if (f.poisonTime > 0) {
            f.poisonTime -= dt;
            damage(null, f, f.poisonDps * dt);
        }
        if (f.burnTime > 0) {
            f.burnTime -= dt;
            damage(null, f, f.burnDps * dt);
        }
        f.stun = Math.max(0, f.stun - dt);
        f.sleep = Math.max(0, f.sleep - dt);
        f.paraTime = Math.max(0, f.paraTime - dt);
        f.hasteTime = Math.max(0, f.hasteTime - dt);
        f.tauntTime = Math.max(0, f.tauntTime - dt);
        if (f.shieldTime > 0) {
            f.shieldTime -= dt;
            if (f.shieldTime <= 0) f.shield = 0;
        }
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

    // ================================================================== ciblage et déplacement

    Fighter chooseTarget(Fighter f) {
        Fighter best = null;
        // Provocation : un ennemi qui provoque à portée de déplacement raisonnable est prioritaire
        for (Fighter o : fighters) {
            if (o.alive && o.player != f.player && o.tauntTime > 0 && Hex.distance(f.cell, o.cell) <= 3) {
                if (best == null || Hex.distance(f.cell, o.cell) < Hex.distance(f.cell, best.cell)) best = o;
            }
        }
        if (best != null) return best;
        if (f.targetsWeakest) return lowestHpEnemy(f);
        for (Fighter o : fighters) {
            if (!o.alive || o.player == f.player) continue;
            if (best == null) {
                best = o;
                continue;
            }
            int d = Hex.distance(f.cell, o.cell), bd = Hex.distance(f.cell, best.cell);
            if (d < bd || (d == bd && o == f.target) || (d == bd && best != f.target && o.hp < best.hp)) best = o;
        }
        return best;
    }

    private Fighter lowestHpEnemy(Fighter f) {
        Fighter best = null;
        for (Fighter o : fighters) {
            if (o.alive && o.player != f.player && (best == null || o.hp < best.hp)) best = o;
        }
        return best;
    }

    private void moveTo(Fighter f, int cell, double duration) {
        occupied[f.cell] = false;
        occupied[cell] = true;
        f.fromCell = f.cell;
        f.cell = cell;
        f.moveT = duration;
        f.moveDur = duration;
    }

    /** Saut sur une case libre adjacente à la cible (Rapace). */
    private boolean dashNextTo(Fighter f, Fighter t) {
        int best = -1;
        for (int n : Hex.neighbors(t.cell)) {
            if (occupied[n]) continue;
            if (best < 0 || Hex.distance(f.cell, n) < Hex.distance(f.cell, best)) best = n;
        }
        if (best < 0) return false;
        moveTo(f, best, 0.15);
        return true;
    }

    private List<Fighter> enemiesWithin(Fighter f, int center, int radius) {
        List<Fighter> l = new ArrayList<Fighter>();
        for (Fighter o : fighters) {
            if (o.alive && o.player != f.player && Hex.distance(center, o.cell) <= radius) l.add(o);
        }
        return l;
    }

    // ================================================================== attaques et capacités

    private void basicAttack(Fighter f, Fighter t) {
        f.mana = Math.min(f.maxMana, f.mana + 10 * f.manaMult);
        hit(f, t, 1.0, false);
    }

    private void cast(Fighter f, Fighter t) {
        f.mana = 0;
        Ability a = f.ability;
        PType mt = f.species().moveType();
        events.add(new Event(Kind.ABILITY, f, t, 0, 1, true, a.label, f.cell, 0, mt));
        switch (a) {
            case GIGA_SANGSUE: {
                double dealt = hit(f, t, 2.2, true);
                heal(f, dealt * 0.6, true);
                break;
            }
            case LANCE_FLAMMES: {
                // ligne de 3 cases dans la direction de la cible
                List<Fighter> line = new ArrayList<Fighter>();
                line.add(t);
                for (Fighter o : fighters) {
                    if (o == t || !o.alive || o.player == f.player) continue;
                    int dFo = Hex.distance(f.cell, o.cell), dOt = Hex.distance(o.cell, t.cell), dFt = Hex.distance(f.cell, t.cell);
                    boolean onLine = dFo + dOt == dFt || Hex.distance(f.cell, t.cell) + dOt == dFo;
                    if (onLine && dFo <= dFt + 2) line.add(o);
                }
                for (int i = 0; i < Math.min(3, line.size()); i++) {
                    Fighter o = line.get(i);
                    hit(f, o, 1.8, true);
                    burn(o, 0.04, 4);
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, mt));
                break;
            }
            case REPLI:
                addShield(f, 0.35 * f.maxHp, 4);
                break;
            case PARA_SPORE:
                hit(f, t, 1.2, true);
                status(t, "Paralysé");
                t.paraTime = Math.max(t.paraTime, 3);
                break;
            case RAPACE: {
                Fighter weak = lowestHpEnemy(f);
                if (weak != null) {
                    dashNextTo(f, weak);
                    f.target = weak;
                    hit(f, weak, 2.2, true);
                }
                break;
            }
            case CROC_FATAL: {
                double extra = t.hp * 0.20;
                hit(f, t, 1.0, true);
                if (t.alive) damage(f, t, extra);
                break;
            }
            case POUDRE_TOXIK:
                for (Fighter o : enemiesWithin(f, t.cell, 1)) {
                    o.poisonDps = Math.max(o.poisonDps, 0.05 * o.maxHp);
                    o.poisonTime = Math.max(o.poisonTime, 4);
                    status(o, "Empoisonné");
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, mt));
                break;
            case HYPNOSE:
                hit(f, t, 1.2, true);
                if (t.alive) {
                    t.sleep = Math.max(t.sleep, 2);
                    status(t, "Endormi");
                }
                break;
            case CLOSE_COMBAT:
                hit(f, t, 3.2, true);
                break;
            case EBOULEMENT:
                for (Fighter o : enemiesWithin(f, f.cell, 1)) {
                    hit(f, o, 1.3, true);
                    if (o.alive) o.stun = Math.max(o.stun, 1);
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, f.cell, 1, PType.ROCK));
                break;
            case TONNERRE: {
                List<Fighter> hitList = new ArrayList<Fighter>();
                Fighter cur = t;
                for (int i = 0; i < 3 && cur != null; i++) {
                    hitList.add(cur);
                    final Fighter from = cur;
                    Fighter next = null;
                    for (Fighter o : fighters) {
                        if (!o.alive || o.player == f.player || hitList.contains(o)) continue;
                        if (next == null || Hex.distance(from.cell, o.cell) < Hex.distance(from.cell, next.cell)) next = o;
                    }
                    cur = next;
                }
                for (Fighter o : hitList) {
                    hit(f, o, 1.6, true);
                    if (o.alive && rng.nextDouble() < 0.3) {
                        o.paraTime = Math.max(o.paraTime, 3);
                        status(o, "Paralysé");
                    }
                }
                break;
            }
            case FEU_FOLLET:
                hit(f, t, 1.0, true);
                burn(t, 0.08, 4);
                break;
            case VITESSE_EXTREME:
                hit(f, t, 2.0, true);
                f.hasteTime = 4;
                status(f, "Hâte");
                break;
            case PSYKO:
                hit(f, t, 3.0, true);
                break;
            case CAGE_ECLAIR:
                hit(f, t, 1.0, true);
                for (Fighter o : enemiesWithin(f, t.cell, 1)) {
                    o.paraTime = Math.max(o.paraTime, 3);
                    status(o, "Paralysé");
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, mt));
                break;
            case LASER_GLACE:
                hit(f, t, 1.5, true);
                freeze(t, 2);
                break;
            case BALL_OMBRE: {
                Fighter weak = lowestHpEnemy(f);
                hit(f, weak != null ? weak : t, 2.5, true);
                break;
            }
            case PROVOC:
                f.tauntTime = 3;
                addShield(f, 0.25 * f.maxHp, 4);
                for (Fighter o : enemiesWithin(f, f.cell, 2)) o.target = f;
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, f.cell, 2, PType.ROCK));
                break;
            case OSMERANG:
                hit(f, t, 1.4, true);
                if (t.alive) hit(f, t, 1.4, true);
                break;
            case TRANCHE:
                for (Fighter o : enemiesWithin(f, t.cell, 1)) {
                    if (Hex.distance(f.cell, o.cell) <= 2) hit(f, o, 1.8, true);
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, mt));
                break;
            case BLIZZARD:
                for (Fighter o : enemiesWithin(f, t.cell, 1)) {
                    hit(f, o, 1.3, true);
                    freeze(o, 1);
                }
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, PType.ICE));
                break;
            case REPOS:
                heal(f, 0.3 * f.maxHp, true);
                break;
            case OURAGAN:
                for (Fighter o : enemiesWithin(f, t.cell, 1)) hit(f, o, 2.0, true);
                events.add(new Event(Kind.AREA, f, t, 0, 1, true, null, t.cell, 1, PType.DRAGON));
                break;
            default:
                hit(f, t, 2.5, true);
                break;
        }
    }

    /** Applique un coup ; renvoie les dégâts réellement infligés aux PV (0 si esquivé). */
    private double hit(Fighter f, Fighter t, double power, boolean special) {
        if (t == null || !t.alive) return 0;
        if (!special && rng.nextDouble() < t.dodge) {
            events.add(new Event(Kind.DODGE, f, t, 0, 1, false, null, t.cell, 0, null));
            return 0;
        }
        double mult = PType.effectiveness(f.species().moveType(), t.species().types);
        double dmg = DAMAGE_BASE * power * f.atk / t.def * mult * (0.9 + 0.2 * rng.nextDouble()) * overtimeMult();
        events.add(new Event(Kind.ATTACK, f, t, dmg, mult, special, null, t.cell, 0, f.species().moveType()));
        double dealt = damage(f, t, dmg);
        t.mana = Math.min(t.maxMana, t.mana + 5 * t.manaMult);
        if (f.lifesteal > 0 && !special) heal(f, dealt * f.lifesteal, false);
        if (f.poisonPct > 0 && t.alive && !special) {
            t.poisonDps = Math.max(t.poisonDps, f.poisonPct * t.maxHp);
            t.poisonTime = Math.max(t.poisonTime, 3);
        }
        if (f.freezeChance > 0 && t.alive && !special && rng.nextDouble() < f.freezeChance) freeze(t, 1);
        return dealt;
    }

    /** Retire des PV (après le bouclier) ; renvoie les PV effectivement perdus. */
    private double damage(Fighter src, Fighter t, double dmg) {
        if (!t.alive || dmg <= 0) return 0;
        if (t.shield > 0) {
            double absorbed = Math.min(t.shield, dmg);
            t.shield -= absorbed;
            dmg -= absorbed;
        }
        if (t.sleep > 0 && dmg > 0) t.sleep = 0; // les dégâts réveillent
        double before = t.hp;
        t.hp -= dmg;
        if (t.hp <= 0) {
            t.hp = 0;
            t.alive = false;
            occupied[t.cell] = false;
            events.add(new Event(Kind.FAINT, src, t, 0, 1, false, null, t.cell, 0, null));
        }
        return before - t.hp;
    }

    /** Soin (réduit de moitié par la brûlure). */
    private void heal(Fighter f, double amount, boolean show) {
        if (!f.alive || amount <= 0) return;
        if (f.burnTime > 0) amount *= 0.5;
        double before = f.hp;
        f.hp = Math.min(f.maxHp, f.hp + amount);
        if (show && f.hp - before >= 1) events.add(new Event(Kind.HEAL, f, f, f.hp - before, 1, true, null, f.cell, 0, null));
    }

    private void addShield(Fighter f, double amount, double duration) {
        f.shield = Math.max(f.shield, amount);
        f.shieldTime = Math.max(f.shieldTime, duration);
        status(f, "Bouclier");
    }

    private void burn(Fighter t, double pctPerSecond, double duration) {
        if (!t.alive) return;
        t.burnDps = Math.max(t.burnTime > 0 ? t.burnDps : 0, pctPerSecond * t.maxHp);
        t.burnTime = Math.max(t.burnTime, duration);
        status(t, "Brûlé");
    }

    private void freeze(Fighter t, double duration) {
        if (!t.alive) return;
        t.stun = Math.max(t.stun, duration);
        status(t, "Gelé");
    }

    private void status(Fighter t, String text) {
        events.add(new Event(Kind.STATUS, null, t, 0, 1, false, text, t.cell, 0, null));
    }
}
