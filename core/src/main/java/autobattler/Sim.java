package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Outil d'équilibrage sans interface : un bot simple joue des parties complètes.
 * Usage : java -cp autobattler.jar autobattler.Sim [nbParties]
 */
public final class Sim {
    public static void main(String[] args) {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int totalRounds = 0, maxRound = 0;
        int[] reached = new int[40];
        for (int g = 0; g < games; g++) {
            GameState s = new GameState(g * 7919L + 1);
            while (s.phase != GameState.Phase.GAMEOVER && s.round < 39) {
                playPrep(s);
                if (!s.startCombat()) break;
                s.battle.runToEnd();
                s.finishCombat();
                if (s.phase == GameState.Phase.RESULT) s.nextRound();
            }
            totalRounds += s.round;
            maxRound = Math.max(maxRound, s.round);
            reached[s.round]++;
        }
        System.out.printf("Parties : %d, manche moyenne atteinte : %.1f, max : %d%n",
                games, totalRounds / (double) games, maxRound);
        StringBuilder sb = new StringBuilder("Distribution : ");
        for (int r = 1; r < reached.length; r++) if (reached[r] > 0) sb.append(r).append('=').append(reached[r]).append(' ');
        System.out.println(sb);
    }

    static double power(Unit u) {
        Fighter f = new Fighter(u, true, 0, 0, null);
        return f.maxHp * f.atk / f.interval;
    }

    static void playPrep(GameState s) {
        // XP : garde au moins 10 or pour les intérêts
        while (s.round >= 3 && s.level < GameState.MAX_LEVEL && s.gold >= GameState.XP_COST + 10) s.buyXp();
        // 1) acheter ce qui complète une fusion, puis le meilleur tier abordable
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
                double score = sp.tier + owned * 3;
                int total = owned + countAll(s);
                if (owned == 0 && countAll(s) >= s.maxUnits() + 3) continue;
                if (score > bestScore && total >= 0) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best >= 0 && s.gold - s.shop[best].tier >= (s.round >= 5 ? 10 : 0) - (bestScore >= 4 ? 99 : 0)) {
                bought = s.buy(best);
            }
        }
        // 2) poser les meilleurs sur le plateau
        List<Unit> all = new ArrayList<Unit>();
        for (int i = 0; i < GameState.BENCH; i++) if (s.bench[i] != null) all.add(s.bench[i]);
        all.addAll(s.boardUnits());
        Collections.sort(all, new Comparator<Unit>() {
            @Override
            public int compare(Unit a, Unit b) {
                return Double.compare(power(b), power(a));
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
        for (GameState.Placed p : GameState.arrange(team, new java.util.Random(s.round))) s.board[p.row][p.col] = p.unit;
        // objets : sur le Pokémon le plus fort qui peut les recevoir
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
