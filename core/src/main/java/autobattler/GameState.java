package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * État d'une partie : économie, niveau et XP, boutique et pool partagé, banc, plateau, objets,
 * adversaire de la manche. Les emplacements sont codés en int : 0..7 = banc, 100 + ligne*7 + colonne = plateau.
 */
public final class GameState {
    public static final int BENCH = 8, ROWS = Hex.SIDE_ROWS, COLS = Hex.COLS, SHOP = 5;
    public static final int START_HP = 60;
    public static final int START_LEVEL = 3, MAX_LEVEL = 9;
    public static final int XP_COST = 4, XP_PER_BUY = 4, XP_PER_ROUND = 2;
    public static final int MAX_INVENTORY = 10;
    /** Exemplaires de chaque Pokémon de base dans le pool partagé, par tier. */
    public static final int[] POOL_SIZE = {18, 14, 10};

    /** XP nécessaire pour passer du niveau i au niveau i+1. */
    private static final int[] XP_TO_NEXT = {0, 0, 0, 4, 8, 14, 20, 30, 44, 0};

    /** Probabilités (%) de tirer un tier 1, 2, 3 selon le niveau (index = niveau). */
    private static final int[][] ODDS = {
            {100, 0, 0}, {100, 0, 0}, {100, 0, 0}, {100, 0, 0},
            {70, 30, 0}, {50, 40, 10}, {40, 40, 20}, {30, 40, 30}, {20, 40, 40}, {15, 35, 50}
    };

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
    public int round, hp, gold, wins, streak, level, xp;
    public boolean lastWin, shopLocked;
    public final Unit[] bench = new Unit[BENCH];
    public final Unit[][] board = new Unit[ROWS][COLS];
    public final Species[] shop = new Species[SHOP];
    public final List<Item> items = new ArrayList<Item>();
    public final List<Placed> enemy = new ArrayList<Placed>();
    private final Map<Species, Integer> pool = new HashMap<Species, Integer>();
    public Phase phase = Phase.PREP;
    public Battle battle;
    public String message = "";
    public int lastDamage;
    /** Butin de la dernière manche contre des Pokémon sauvages. */
    public final List<Item> lastLoot = new ArrayList<Item>();

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
        level = START_LEVEL;
        xp = 0;
        lastWin = false;
        shopLocked = false;
        java.util.Arrays.fill(bench, null);
        for (Unit[] r : board) java.util.Arrays.fill(r, null);
        items.clear();
        lastLoot.clear();
        pool.clear();
        for (int tier = 1; tier <= 3; tier++)
            for (Species s : Pokedex.shopPool(tier)) pool.put(s, POOL_SIZE[tier - 1]);
        items.add(randomComponent(rng));
        battle = null;
        phase = Phase.PREP;
        rollShop();
        prepareEnemy();
        message = "Achète des Pokémon et place-les sur le plateau, puis lance le combat !";
    }

    // ------------------------------------------------------------------ niveau, XP, chances

    public int maxUnits() {
        return level;
    }

    /** XP nécessaire pour le niveau suivant (0 au niveau max). */
    public int xpToNext() {
        return level >= MAX_LEVEL ? 0 : XP_TO_NEXT[level];
    }

    public static int[] shopOdds(int level) {
        return ODDS[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    /** Niveau « type » d'un adversaire à une manche donnée. */
    public static int enemyLevel(int round) {
        return Math.min(MAX_LEVEL, 3 + (round - 1) / 2);
    }

    /** Manches contre des Pokémon sauvages (butin d'objets) : 2, 6, 10, 14… */
    public static boolean isPveRound(int round) {
        return round % 4 == 2;
    }

    public boolean isPveRound() {
        return isPveRound(round);
    }

    public boolean buyXp() {
        if (phase != Phase.PREP) return false;
        if (level >= MAX_LEVEL) {
            message = "Niveau maximal atteint.";
            return false;
        }
        if (gold < XP_COST) {
            message = "Pas assez d'or (" + XP_COST + " requis).";
            return false;
        }
        gold -= XP_COST;
        gainXp(XP_PER_BUY);
        return true;
    }

    private void gainXp(int amount) {
        if (level >= MAX_LEVEL) return;
        xp += amount;
        while (level < MAX_LEVEL && xp >= XP_TO_NEXT[level]) {
            xp -= XP_TO_NEXT[level];
            level++;
            message = "Niveau " + level + " : jusqu'à " + level + " Pokémon sur le plateau.";
        }
        if (level >= MAX_LEVEL) xp = 0;
    }

    // ------------------------------------------------------------------ pool partagé

    public int poolCount(Species s) {
        Integer n = pool.get(s);
        return n == null ? 0 : n;
    }

    private void returnToPool(Unit u) {
        Species base = Pokedex.base(u.species.line);
        pool.put(base, poolCount(base) + u.copies);
    }

    /** Tirage pondéré par les exemplaires restants, selon les chances du niveau. */
    private Species drawFromPool() {
        int[] odds = shopOdds(level);
        int r = rng.nextInt(100), tier = 1;
        if (r >= odds[0]) tier = r >= odds[0] + odds[1] ? 3 : 2;
        for (int attempt = 0; attempt < 3; attempt++) {
            int t = ((tier - 1 + attempt) % 3) + 1;
            int total = 0;
            for (Species s : Pokedex.shopPool(t)) total += poolCount(s);
            if (total == 0) continue;
            int pick = rng.nextInt(total);
            for (Species s : Pokedex.shopPool(t)) {
                pick -= poolCount(s);
                if (pick < 0) return s;
            }
        }
        return null;
    }

    private static Species randomSpecies(int level, Random rng) {
        int[] odds = shopOdds(level);
        int r = rng.nextInt(100), tier = 1;
        if (r >= odds[0]) tier = r >= odds[0] + odds[1] ? 3 : 2;
        List<Species> p = Pokedex.shopPool(tier);
        return p.get(rng.nextInt(p.size()));
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

    // ------------------------------------------------------------------ boutique

    public void rollShop() {
        for (int i = 0; i < SHOP; i++) shop[i] = drawFromPool();
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
        if (poolCount(sp) <= 0) {
            message = "Plus aucun " + sp.name + " dans le pool partagé.";
            shop[i] = null;
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
            pool.put(sp, poolCount(sp) - 1);
            shop[i] = null;
            Unit bought = new Unit(sp, 1, sp.tier);
            mergeInto(same.get(0), get(same.get(0)), get(same.get(1)), bought, same.get(1), -1);
            checkMerges();
            return true;
        }
        gold -= sp.tier;
        pool.put(sp, poolCount(sp) - 1);
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

    /** Fusionne a, b, c en une unité placée à {@code target} ; vide les emplacements {@code clearB}/{@code clearC} (-1 = aucun). */
    private void mergeInto(int target, Unit a, Unit b, Unit c, int clearB, int clearC) {
        Unit m = a.merged();
        m.invested = a.invested + b.invested + c.invested;
        m.copies = a.copies + b.copies + c.copies;
        List<Item> held = new ArrayList<Item>();
        held.addAll(a.items);
        held.addAll(b.items);
        held.addAll(c.items);
        // les objets combinés d'abord, puis les composants (qui peuvent se combiner)
        Collections.sort(held, new Comparator<Item>() {
            @Override
            public int compare(Item x, Item y) {
                return Boolean.compare(x.isComponent(), y.isComponent());
            }
        });
        int lost = 0;
        for (Item it : held) {
            if (m.canEquip(it)) m.equip(it);
            else if (!addItem(it)) lost++;
        }
        if (clearB >= 0) set(clearB, null);
        if (clearC >= 0) set(clearC, null);
        set(target, m);
        message = mergeMessage(a, m) + (lost > 0 ? " (inventaire plein : " + lost + " objet(s) perdu(s))" : "");
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
                mergeInto(g.get(0), get(g.get(0)), get(g.get(1)), get(g.get(2)), g.get(1), g.get(2));
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
        returnToPool(u);
        int lost = 0;
        for (Item it : u.items) if (!addItem(it)) lost++;
        set(slot, null);
        message = u.displayName() + " vendu pour " + u.invested + " or" + (u.items.isEmpty() ? "." : ", objets récupérés.")
                + (lost > 0 ? " (" + lost + " perdu(s), inventaire plein)" : "");
        return true;
    }

    public boolean move(int from, int to) {
        if (phase != Phase.PREP || from == to) return false;
        Unit a = get(from), b = get(to);
        if (a == null) return false;
        if (isBoard(to) && !isBoard(from) && b == null && boardCount() >= maxUnits()) {
            message = "Équipe complète (" + maxUnits() + " max au niveau " + level + "). Achète de l'XP !";
            return false;
        }
        set(to, a);
        set(from, b);
        return true;
    }

    public void toggleLock() {
        shopLocked = !shopLocked;
    }

    // ------------------------------------------------------------------ objets

    public boolean addItem(Item it) {
        if (items.size() >= MAX_INVENTORY) return false;
        items.add(it);
        return true;
    }

    /** Équipe l'objet n° {@code index} de l'inventaire sur l'unité de l'emplacement {@code slot}. */
    public boolean equip(int index, int slot) {
        if (phase != Phase.PREP || index < 0 || index >= items.size()) return false;
        Unit u = get(slot);
        if (u == null) return false;
        Item it = items.get(index);
        if (!u.canEquip(it)) {
            message = u.species.name + " tient déjà " + Unit.MAX_ITEMS + " objets.";
            return false;
        }
        List<Item> before = new ArrayList<Item>(u.items);
        u.equip(it);
        items.remove(index);
        Item combined = null;
        for (int i = 0; i < before.size(); i++) if (u.items.get(i) != before.get(i)) combined = u.items.get(i);
        message = combined != null
                ? "Les composants se combinent : " + u.species.name + " tient " + combined.name + " !"
                : u.species.name + " tient " + it.name + ".";
        return true;
    }

    private static Item randomComponent(Random rng) {
        return Item.COMPONENTS.get(rng.nextInt(Item.COMPONENTS.size()));
    }

    // ------------------------------------------------------------------ combat

    public static List<Fighter> fighters(List<Placed> team, boolean player) {
        List<Unit> units = new ArrayList<Unit>();
        for (Placed p : team) units.add(p.unit);
        Map<PType, Integer> syn = Synergies.levels(units);
        Map<Trait, Integer> traits = Synergies.traitLevels(units);
        List<Fighter> res = new ArrayList<Fighter>();
        for (Placed p : team) res.add(new Fighter(p.unit, player, p.row, p.col, syn, traits));
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
        lastLoot.clear();
        if (isPveRound()) {
            int n = lastWin ? (round >= 10 ? 3 : 2) : 1;
            for (int i = 0; i < n; i++) {
                Item it = randomComponent(rng);
                if (addItem(it)) lastLoot.add(it);
            }
            if (lastWin) gold += 2;
        }
        if (lastWin) {
            wins++;
            streak = Math.max(1, streak + 1);
            lastDamage = 0;
            message = "Victoire !";
        } else {
            streak = Math.min(-1, streak - 1);
            lastDamage = round + 2 * battle.aliveCount(false);
            if (isPveRound()) lastDamage = (lastDamage + 1) / 2;
            hp = Math.max(0, hp - lastDamage);
            message = battle.time() >= Battle.TIME_LIMIT ? "Temps écoulé… -" + lastDamage + " PV" : "Défaite… -" + lastDamage + " PV";
        }
        if (!lastLoot.isEmpty()) {
            StringBuilder sb = new StringBuilder(" Butin : ");
            for (int i = 0; i < lastLoot.size(); i++) sb.append(i == 0 ? "" : ", ").append(lastLoot.get(i).name);
            message += sb.toString() + (lastWin ? " et 2 or." : ".");
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
        int levelBefore = level;
        gainXp(XP_PER_ROUND);
        if (!shopLocked) rollShop();
        shopLocked = false;
        prepareEnemy();
        phase = Phase.PREP;
        message = "Manche " + round + " : +" + income + " or (dont " + interest + " d'intérêts)"
                + (level > levelBefore ? ", niveau " + level + " !" : ".")
                + (isPveRound() ? " Pokémon sauvages : butin à gagner !" : "");
    }

    private void prepareEnemy() {
        enemy.clear();
        enemy.addAll(isPveRound() ? generateWild(round, rng) : generateEnemy(round, rng));
    }

    // ------------------------------------------------------------------ IA adverse

    public static List<Placed> generateEnemy(int round, Random rng) {
        int lvl = enemyLevel(round);
        int n = round == 1 ? 2 : lvl;
        List<Unit> units = new ArrayList<Unit>();
        double base = (round - 3) / 6.0;
        double itemChance = Math.min(0.5, (round - 4) * 0.05);
        for (int i = 0; i < n; i++) {
            Species sp = randomSpecies(lvl, rng);
            Unit u = new Unit(sp, 1, sp.tier);
            int steps = round <= 2 ? 0 : Math.max(0, Math.min(3, (int) Math.floor(base + rng.nextDouble())));
            for (int k = 0; k < steps; k++) {
                Unit m = u.merged();
                if (m != null) u = m;
            }
            if (rng.nextDouble() < itemChance) u.equip(randomCombined(rng));
            if (round >= 12 && rng.nextDouble() < itemChance / 2) u.equip(randomCombined(rng));
            units.add(u);
        }
        return arrange(units, rng);
    }

    /** Pokémon sauvages : moins nombreux mais plus évolués, sans objets. */
    public static List<Placed> generateWild(int round, Random rng) {
        int lvl = enemyLevel(round);
        int n = Math.max(2, Math.min(6, 1 + round / 3));
        double base = (round - 1) / 6.0;
        List<Unit> units = new ArrayList<Unit>();
        for (int i = 0; i < n; i++) {
            Species sp = randomSpecies(lvl, rng);
            Unit u = new Unit(sp, 1, sp.tier);
            int steps = Math.max(0, Math.min(3, (int) Math.floor(base + rng.nextDouble())));
            for (int k = 0; k < steps; k++) {
                Unit m = u.merged();
                if (m != null) u = m;
            }
            units.add(u);
        }
        return arrange(units, rng);
    }

    private static Item randomCombined(Random rng) {
        List<Item> all = Item.combined();
        return all.get(rng.nextInt(all.size()));
    }

    /** Placement automatique : corps à corps en première ligne (les plus robustes au centre), distance derrière. */
    public static List<Placed> arrange(List<Unit> units, Random rng) {
        units = new ArrayList<Unit>(units);
        Collections.sort(units, new Comparator<Unit>() {
            @Override
            public int compare(Unit a, Unit b) {
                return Double.compare(tankiness(b), tankiness(a));
            }
        });
        boolean[][] used = new boolean[ROWS][COLS];
        List<Placed> res = new ArrayList<Placed>();
        int[] centerOrder = {3, 2, 4, 1, 5, 0, 6};
        for (Unit u : units) {
            int range = u.species.range();
            Trait role = u.species.role();
            int preferred = role.frontline ? 0 : role == Trait.ASSASSIN ? 3 : range <= 2 ? 2 : 3;
            boolean placed = false;
            for (int dr = 0; dr < ROWS && !placed; dr++) {
                int row = preferred + (preferred == 0 ? dr : -dr);
                if (row < 0 || row >= ROWS) continue;
                List<Integer> cols = new ArrayList<Integer>();
                for (int c : centerOrder) cols.add(c);
                if (range > 1) Collections.shuffle(cols, rng);
                for (int c : cols) {
                    if (!used[row][c]) {
                        used[row][c] = true;
                        res.add(new Placed(u, row, c));
                        placed = true;
                        break;
                    }
                }
            }
        }
        return res;
    }

    private static double tankiness(Unit u) {
        Fighter f = new Fighter(u, false, 0, 0, null);
        return f.maxHp * f.def;
    }
}
