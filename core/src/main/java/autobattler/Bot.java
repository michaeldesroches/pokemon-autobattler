package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Joueur automatique : achète, place et équipe, comme un joueur prudent. Une « personnalité »
 * (classe et type préférés) oriente ses achats pour que les 7 adversaires d'un lobby jouent différemment.
 */
public final class Bot {
    /** Classe privilégiée (null : aucune). */
    public final Trait favoriteTrait;
    /** Type privilégié (null : aucun). */
    public final PType favoriteType;
    /** Or gardé en réserve pour les intérêts à partir de la manche 5. */
    private final int savings;
    /** Or au-delà duquel le bot achète de l'XP. */
    private final int xpThreshold;

    public Bot(Trait favoriteTrait, PType favoriteType, int savings, int xpThreshold) {
        this.favoriteTrait = favoriteTrait;
        this.favoriteType = favoriteType;
        this.savings = savings;
        this.xpThreshold = xpThreshold;
    }

    /** Le bot « naïf » du simulateur d'équilibrage. */
    public static Bot naive() {
        return new Bot(null, null, 10, 14);
    }

    /** Une personnalité tirée au hasard. */
    public static Bot random(Random rng) {
        Trait[] roles = {Trait.GUARDIAN, Trait.FIGHTER, Trait.ASSASSIN, Trait.MYSTIC, Trait.MARKSMAN};
        PType[] types = PType.values();
        return new Bot(roles[rng.nextInt(roles.length)], types[rng.nextInt(types.length)],
                6 + rng.nextInt(5) * 2, 12 + rng.nextInt(4) * 2);
    }

    static double power(Unit u) {
        Fighter f = new Fighter(u, true, 0, 0, null);
        return f.maxHp * f.atk / f.interval;
    }

    private double affinity(Species sp) {
        double a = 0;
        if (favoriteTrait != null && sp.role() == favoriteTrait) a += 1.5;
        if (favoriteType != null)
            for (PType t : Pokedex.base(sp.line).types) if (t == favoriteType) a += 1;
        return a;
    }

    public void playPrep(GameState s) {
        // XP : garde de quoi toucher les intérêts
        while (s.round >= 3 && s.level < GameState.MAX_LEVEL && s.gold >= GameState.XP_COST + xpThreshold - 4) s.buyXp();
        // 1) acheter ce qui complète une fusion, puis le meilleur tier abordable (et ce qui colle à ses goûts)
        boolean bought = true;
        while (bought) {
            bought = false;
            int best = -1;
            double bestScore = -1;
            for (int i = 0; i < GameState.SHOP; i++) {
                Species sp = s.shop[i];
                if (sp == null || sp.tier > s.gold) continue;
                int owned = 0;
                for (int slot = 0; slot < GameState.BENCH; slot++) if (s.bench[slot] != null && s.bench[slot].species == sp) owned++;
                for (Unit u : s.boardUnits()) if (u.species == sp) owned++;
                double score = sp.tier + owned * 3 + affinity(sp);
                if (owned == 0 && countAll(s) >= s.maxUnits() + 3) continue;
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best >= 0 && s.gold - s.shop[best].tier >= (s.round >= 5 ? savings : 0) - (bestScore >= 4 ? 99 : 0)) {
                bought = s.buy(best);
            }
        }
        // 2) poser les meilleurs sur le plateau, selon leur rôle
        List<Unit> all = new ArrayList<Unit>();
        for (int i = 0; i < GameState.BENCH; i++) if (s.bench[i] != null) all.add(s.bench[i]);
        all.addAll(s.boardUnits());
        Collections.sort(all, new Comparator<Unit>() {
            @Override
            public int compare(Unit a, Unit b) {
                return Double.compare(power(b) * (1 + 0.15 * affinity(b.species)), power(a) * (1 + 0.15 * affinity(a.species)));
            }
        });
        java.util.Arrays.fill(s.bench, null);
        for (Unit[] r : s.board) java.util.Arrays.fill(r, null);
        int max = s.maxUnits(), benchI = 0;
        List<Unit> team = new ArrayList<Unit>();
        for (Unit u : all) {
            if (team.size() < max) team.add(u);
            else if (benchI < GameState.BENCH) s.bench[benchI++] = u;
            else s.gold += u.invested;
        }
        for (GameState.Placed p : GameState.arrange(team, new Random(s.round))) s.board[p.row][p.col] = p.unit;
        // 3) objets : d'abord sur le plus fort, puis sur le premier qui peut les recevoir
        for (int i = s.items.size() - 1; i >= 0; i--) {
            for (int r = 0; r < GameState.ROWS; r++)
                for (int c = 0; c < GameState.COLS; c++) {
                    if (i >= s.items.size()) continue;
                    Unit u = s.board[r][c];
                    if (u != null && u == team.get(0) && u.canEquip(s.items.get(i))) s.equip(i, GameState.boardSlot(r, c));
                }
        }
        for (int i = s.items.size() - 1; i >= 0; i--) {
            boolean done = false;
            for (int r = 0; r < GameState.ROWS && !done; r++)
                for (int c = 0; c < GameState.COLS && !done; c++) {
                    Unit u = s.board[r][c];
                    if (u != null && i < s.items.size() && u.canEquip(s.items.get(i))) done = s.equip(i, GameState.boardSlot(r, c));
                }
        }
    }

    static int countAll(GameState s) {
        int n = s.boardCount();
        for (Unit u : s.bench) if (u != null) n++;
        return n;
    }
}
