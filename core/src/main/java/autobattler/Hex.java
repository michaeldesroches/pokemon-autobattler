package autobattler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Champ de bataille hexagonal (hexagones « pointe en haut », coordonnées décalées odd-r).
 * 7 colonnes × 8 lignes : lignes 0 à 3 pour l'adversaire (0 = fond), 4 à 7 pour le joueur (4 = première ligne).
 * Une case est désignée par son index {@code row * COLS + col}.
 */
public final class Hex {
    public static final int COLS = 7, ROWS = 8, SIDE_ROWS = 4, CELLS = COLS * ROWS;

    private static final int[][] EVEN = {{1, 0}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}};
    private static final int[][] ODD = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {0, 1}, {1, 1}};
    private static final int[][] NEIGHBORS = new int[CELLS][];

    static {
        for (int i = 0; i < CELLS; i++) {
            int c = col(i), r = row(i);
            int[][] dirs = (r & 1) == 0 ? EVEN : ODD;
            List<Integer> n = new ArrayList<Integer>();
            for (int[] d : dirs) {
                int nc = c + d[0], nr = r + d[1];
                if (nc >= 0 && nc < COLS && nr >= 0 && nr < ROWS) n.add(index(nc, nr));
            }
            NEIGHBORS[i] = new int[n.size()];
            for (int k = 0; k < n.size(); k++) NEIGHBORS[i][k] = n.get(k);
        }
    }

    public static int index(int col, int row) {
        return row * COLS + col;
    }

    public static int col(int cell) {
        return cell % COLS;
    }

    public static int row(int cell) {
        return cell / COLS;
    }

    /** Case du champ de bataille pour une position locale (ligne 0 = première ligne du camp). */
    public static int fromLocal(boolean player, int localRow, int col) {
        return index(col, player ? SIDE_ROWS + localRow : SIDE_ROWS - 1 - localRow);
    }

    public static int[] neighbors(int cell) {
        return NEIGHBORS[cell];
    }

    public static int distance(int a, int b) {
        int ra = row(a), rb = row(b);
        int xa = col(a) - (ra - (ra & 1)) / 2, xb = col(b) - (rb - (rb & 1)) / 2;
        int dx = xa - xb, dz = ra - rb, dy = -dx - dz;
        return (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) / 2;
    }

    /**
     * A* : premier pas vers une case libre située à {@code range} cases ou moins de {@code goal}.
     * {@code blocked[i]} indique les cases occupées (la case de départ est ignorée).
     * Renvoie -1 si aucun chemin n'existe.
     */
    public static int nextStep(int start, int goal, int range, boolean[] blocked) {
        if (distance(start, goal) <= range) return -1;
        final int[] g = new int[CELLS];
        final int[] f = new int[CELLS];
        int[] parent = new int[CELLS];
        Arrays.fill(g, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[CELLS];
        PriorityQueue<Integer> open = new PriorityQueue<Integer>(16, (x, y) -> f[x] != f[y] ? f[x] - f[y] : x - y);
        g[start] = 0;
        f[start] = Math.max(0, distance(start, goal) - range);
        open.add(start);
        while (!open.isEmpty()) {
            int cur = open.poll();
            if (closed[cur]) continue;
            closed[cur] = true;
            if (cur != start && distance(cur, goal) <= range) {
                int step = cur;
                while (parent[step] != start) step = parent[step];
                return step;
            }
            for (int n : NEIGHBORS[cur]) {
                if (closed[n] || blocked[n]) continue;
                int ng = g[cur] + 1;
                if (ng < g[n]) {
                    g[n] = ng;
                    f[n] = ng + Math.max(0, distance(n, goal) - range);
                    parent[n] = cur;
                    open.add(n);
                }
            }
        }
        return -1;
    }

    private Hex() {
    }
}
