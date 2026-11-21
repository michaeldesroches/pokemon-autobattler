package autobattler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexTest {

    @Test
    void neighborsAreAtDistanceOne() {
        for (int c = 0; c < Hex.CELLS; c++) {
            int[] n = Hex.neighbors(c);
            assertTrue(n.length >= 2 && n.length <= 6, "voisins de " + c);
            for (int x : n) assertEquals(1, Hex.distance(c, x), "distance " + c + " -> " + x);
        }
    }

    @Test
    void distanceIsSymmetricAndZeroOnSelf() {
        for (int a = 0; a < Hex.CELLS; a++) {
            assertEquals(0, Hex.distance(a, a));
            for (int b = 0; b < Hex.CELLS; b++) assertEquals(Hex.distance(a, b), Hex.distance(b, a));
        }
    }

    @Test
    void localRowsMapToEachSide() {
        assertEquals(Hex.SIDE_ROWS, Hex.row(Hex.fromLocal(true, 0, 3)));
        assertEquals(Hex.SIDE_ROWS - 1, Hex.row(Hex.fromLocal(false, 0, 3)));
        assertEquals(Hex.ROWS - 1, Hex.row(Hex.fromLocal(true, Hex.SIDE_ROWS - 1, 0)));
        assertEquals(0, Hex.row(Hex.fromLocal(false, Hex.SIDE_ROWS - 1, 0)));
    }

    @Test
    void nextStepGetsCloserAndAvoidsBlockedCells() {
        boolean[] blocked = new boolean[Hex.CELLS];
        int start = Hex.index(0, 7), goal = Hex.index(6, 0);
        int cur = start, steps = 0;
        while (Hex.distance(cur, goal) > 1) {
            int next = Hex.nextStep(cur, goal, 1, blocked);
            assertTrue(next >= 0, "pas de chemin depuis " + cur);
            assertEquals(Hex.distance(cur, goal) - 1, Hex.distance(next, goal));
            cur = next;
            assertTrue(++steps < 20);
        }
        // un mur sur toute une ligne sauf une case : le chemin passe par l'ouverture
        java.util.Arrays.fill(blocked, false);
        for (int c = 0; c < Hex.COLS - 1; c++) blocked[Hex.index(c, 4)] = true;
        int step = Hex.nextStep(Hex.index(0, 6), Hex.index(0, 2), 1, blocked);
        assertTrue(step >= 0 && !blocked[step]);
    }
}
