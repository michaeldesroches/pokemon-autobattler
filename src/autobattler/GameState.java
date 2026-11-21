package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * État d'une partie : économie, boutique, banc, plateau, ennemi de la manche.
 * Les emplacements sont codés en int : 0..7 = banc, 100 + ligne*4 + colonne = plateau.
 */
public final class GameState {
    public static final int BENCH = 8, ROWS = 2, COLS = 4, SHOP = 5;
    public static final int START_HP = 60;

    public enum Phase { PREP, COMBAT, RESULT, GAMEOVER }

    public static final class Placed {
        public final Unit unit;
        public final int row, col;

        Placed(Unit unit, int row, int col) {
            this.unit = unit;
            this.row = row;
            this.col = col;
        }
    }

    private final Random rng;
    public int round, hp, gold, wins, streak;
    public boolean lastWin, shopLocked;
    public final Unit[] bench = new Unit[BENCH];
    public final Unit[][] board = new Unit[ROWS][COLS];
    public final Species[] shop = new Species[SHOP];
    public final List<Placed> enemy = new ArrayList<Placed>();
    public Phase phase = Phase.PREP;
    public Battle battle;
    public String message = "";
    public int lastDamage;

    public GameState(long seed) {
        rng = new Random(seed);
        newGame();
    }

    public void newGame() {
        round = 1;
        hp = START_HP;
        gold = 6;
        wins = 0;
        streak = 0;
        lastWin = false;
        shopLocked = false;
        java.util.Arrays.fill(bench, null);
        for (Unit[] r : board) java.util.Arrays.fill(r, null);
        battle = null;
        phase = Phase.PREP;
        rollShop();
        enemy.clear();
        enemy.addAll(generateEnemy(round, rng));
        message = "Achète des Pokémon et place-les sur le plateau, puis lance le combat !";
    }

    // ------------------------------------------------------------------ règles de base

    public static int maxUnits(int round) {
        return Math.min(3 + (round - 1) / 2, 8);
    }

    public int maxUnits() {
        return maxUnits(round);
    }

    /** Probabilités (en %) de tirer un tier 1, 2, 3 selon la manche. */
    public static int[] shopOdds(int round) {
        if (round <= 2) return new int[]{100, 0, 0};
        if (round <= 4) return new int[]{75, 25, 0};
        if (round <= 7) return new int[]{55, 35, 10};
        if (round <= 10) return new int[]{40, 40, 20};
        return new int[]{30, 40, 30};
    }

    private static Species randomSpecies(int round, Random rng) {
        int[] odds = shopOdds(round);
        int r = rng.nextInt(100), tier = 1;
        if (r >= odds[0]) tier = r >= odds[0] + odds[1] ? 3 : 2;
        List<Species> pool = Pokedex.shopPool(tier);
        return pool.get(rng.nextInt(pool.size()));
    }

    // ------------------------------------------------------------------ emplacements

    public static boolean isBoard(int slot) {
        return slot >= 100;
    }

    public static int boardSlot(int row, int col) {
        return 100 + row * COLS + col;
    }

    public Unit get(int slot) {
        if (isBoard(slot)) return board[(slot - 100) / COLS][(slot - 100) % COLS];
        return bench[slot];
    }

    private void set(int slot, Unit u) {
        if (isBoard(slot)) board[(slot - 100) / COLS][(slot - 100) % COLS] = u;
        else bench[slot] = u;
    }

    /** Emplacements dans l'ordre de priorité de fusion : plateau d'abord, puis banc. */
    private List<Integer> allSlots() {
        List<Integer> s = new ArrayList<Integer>();
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) s.add(boardSlot(r, c));
        for (int i = 0; i < BENCH; i++) s.add(i);
        return s;
    }

    public List<Unit> boardUnits() {
        List<Unit> l = new ArrayList<Unit>();
        for (Unit[] r : board) for (Unit u : r) if (u != null) l.add(u);
        return l;
    }

    public int boardCount() {
        return boardUnits().size();
    }

    private int freeBench() {
        for (int i = 0; i < BENCH; i++) if (bench[i] == null) return i;
        return -1;
    }

    // ------------------------------------------------------------------ actions de préparation

    public void rollShop() {
        for (int i = 0; i < SHOP; i++) shop[i] = randomSpecies(round, rng);
    }

    public boolean reroll() {
        if (phase != Phase.PREP) return false;
        if (gold < 1) {
            message = "Pas assez d'or pour relancer.";
            return false;
        }
        gold--;
        rollShop();
        message = "";
        return true;
    }

    public boolean buy(int i) {
        if (phase != Phase.PREP || shop[i] == null) return false;
        Species sp = shop[i];
        if (gold < sp.tier) {
            message = "Pas assez d'or (" + sp.tier + " requis).";
            return false;
        }
        int free = freeBench();
        if (free < 0) {
            // Banc plein : on autorise l'achat seulement s'il déclenche une fusion.
            List<Integer> same = new ArrayList<Integer>();
            for (int s : allSlots()) {
                Unit u = get(s);
                if (u != null && u.species == sp && u.stars == 1) same.add(s);
            }
            if (same.size() < 2) {
                message = "Banc plein ! Vends un Pokémon (clic droit).";
                return false;
            }
            gold -= sp.tier;
            shop[i] = null;
            Unit a = get(same.get(0)), b = get(same.get(1));
            Unit m = a.merged();
            m.invested = a.invested + b.invested + sp.tier;
            set(same.get(0), m);
            set(same.get(1), null);
            message = mergeMessage(a, m);
            checkMerges();
            return true;
        }
        gold -= sp.tier;
        shop[i] = null;
        bench[free] = new Unit(sp, 1, sp.tier);
        message = sp.name + " rejoint ton équipe.";
        checkMerges();
        return true;
    }

    private static String mergeMessage(Unit from, Unit to) {
        if (from.species != to.species) return "Quoi ? " + from.species.name + " évolue en " + to.species.name + " !";
        return from.species.name + " passe " + to.stars + "★ !";
    }

    /** Fusionne tant qu'il existe 3 unités identiques. */
    public void checkMerges() {
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, List<Integer>> groups = new LinkedHashMap<String, List<Integer>>();
            for (int s : allSlots()) {
                Unit u = get(s);
                if (u == null || u.merged() == null) continue;
                List<Integer> g = groups.get(u.mergeKey());
                if (g == null) {
                    g = new ArrayList<Integer>();
                    groups.put(u.mergeKey(), g);
                }
                g.add(s);
            }
            for (List<Integer> g : groups.values()) {
                if (g.size() < 3) continue;
                Unit a = get(g.get(0));
                Unit m = a.merged();
                m.invested = a.invested + get(g.get(1)).invested + get(g.get(2)).invested;
                for (int k = 0; k < 3; k++) set(g.get(k), null);
                set(g.get(0), m);
                message = mergeMessage(a, m);
                changed = true;
                break;
            }
        }
    }

    public boolean sell(int slot) {
        if (phase != Phase.PREP) return false;
        Unit u = get(slot);
        if (u == null) return false;
        gold += u.invested;
        set(slot, null);
        message = u.displayName() + " vendu pour " + u.invested + " or.";
        return true;
    }

    public boolean move(int from, int to) {
        if (phase != Phase.PREP || from == to) return false;
        Unit a = get(from), b = get(to);
        if (a == null) return false;
        if (isBoard(to) && !isBoard(from) && b == null && boardCount() >= maxUnits()) {
            message = "Équipe complète (" + maxUnits() + " max à cette manche).";
            return false;
        }
        set(to, a);
        set(from, b);
        return true;
    }

    public void toggleLock() {
        shopLocked = !shopLocked;
    }

    // ------------------------------------------------------------------ combat

    public static List<Fighter> fighters(List<Placed> team, boolean player) {
        List<Unit> units = new ArrayList<Unit>();
        for (Placed p : team) units.add(p.unit);
        Map<PType, Integer> syn = Synergies.levels(units);
        List<Fighter> res = new ArrayList<Fighter>();
        for (Placed p : team) res.add(new Fighter(p.unit, player, p.row, p.col, syn));
        return res;
    }

    public List<Placed> playerTeam() {
        List<Placed> l = new ArrayList<Placed>();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c] != null) l.add(new Placed(board[r][c], r, c));
        return l;
    }

    public boolean startCombat() {
        if (phase != Phase.PREP) return false;
        if (boardCount() == 0) {
            message = "Place au moins un Pokémon sur le plateau !";
            return false;
        }
        battle = new Battle(fighters(playerTeam(), true), fighters(enemy, false), rng.nextLong());
        phase = Phase.COMBAT;
        message = "";
        return true;
    }

    /** À appeler quand battle.result != 0. */
    public void finishCombat() {
        if (phase != Phase.COMBAT || battle == null || battle.result == 0) return;
        lastWin = battle.result > 0;
        if (lastWin) {
            wins++;
            streak = Math.max(1, streak + 1);
            lastDamage = 0;
            message = "Victoire !";
        } else {
            streak = Math.min(-1, streak - 1);
            lastDamage = round + 2 * battle.aliveCount(false);
            hp = Math.max(0, hp - lastDamage);
            message = battle.time() >= Battle.TIME_LIMIT ? "Temps écoulé… -" + lastDamage + " PV" : "Défaite… -" + lastDamage + " PV";
        }
        phase = hp <= 0 ? Phase.GAMEOVER : Phase.RESULT;
    }

    public void nextRound() {
        if (phase != Phase.RESULT) return;
        int interest = Math.min(5, gold / 10);
        int income = 5 + interest + (lastWin ? 1 : 0) + (Math.abs(streak) >= 3 ? 1 : 0);
        gold += income;
        round++;
        battle = null;
        if (!shopLocked) rollShop();
        shopLocked = false;
        enemy.clear();
        enemy.addAll(generateEnemy(round, rng));
        phase = Phase.PREP;
        message = "Manche " + round + " : +" + income + " or (dont " + interest + " d'intérêts).";
    }

    // ------------------------------------------------------------------ IA adverse

    public static List<Placed> generateEnemy(int round, Random rng) {
        int n = round == 1 ? 2 : maxUnits(round);
        List<Unit> units = new ArrayList<Unit>();
        double base = (round - 3) / 6.0;
        for (int i = 0; i < n; i++) {
            Species sp = randomSpecies(round, rng);
            Unit u = new Unit(sp, 1, sp.tier);
            int steps = round <= 2 ? 0 : Math.max(0, Math.min(3, (int) Math.floor(base + rng.nextDouble())));
            for (int k = 0; k < steps; k++) {
                Unit m = u.merged();
                if (m != null) u = m;
            }
            units.add(u);
        }
        // Les plus robustes devant
        Collections.sort(units, new Comparator<Unit>() {
            @Override
            public int compare(Unit a, Unit b) {
                return Double.compare(tankiness(b), tankiness(a));
            }
        });
        int front = Math.min(4, (n + 1) / 2);
        List<Integer> frontCols = shuffledCols(rng), backCols = shuffledCols(rng);
        List<Placed> res = new ArrayList<Placed>();
        for (int i = 0; i < units.size(); i++) {
            if (i < front) res.add(new Placed(units.get(i), 0, frontCols.get(i)));
            else res.add(new Placed(units.get(i), 1, backCols.get(i - front)));
        }
        return res;
    }

    private static double tankiness(Unit u) {
        Fighter f = new Fighter(u, false, 0, 0, null);
        return f.maxHp * f.def;
    }

    private static List<Integer> shuffledCols(Random rng) {
        List<Integer> l = new ArrayList<Integer>();
        for (int c = 0; c < COLS; c++) l.add(c);
        Collections.shuffle(l, rng);
        return l;
    }
}
