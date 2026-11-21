package autobattler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTest {

    @Test
    void everyLineHasAKit() {
        for (Species s : Pokedex.all()) {
            assertNotNull(s.ability(), s.name);
            assertTrue(s.range() >= 1 && s.range() <= 4, s.name);
        }
    }

    @Test
    void battlesAlwaysEndAndNeverStackUnits() {
        Random rng = new Random(42);
        for (int round = 1; round <= 16; round += 3) {
            for (int i = 0; i < 40; i++) {
                List<GameState.Placed> a = GameState.generateEnemy(round, rng), b = GameState.generateEnemy(round, rng);
                Battle battle = new Battle(GameState.fighters(a, true), GameState.fighters(b, false), rng.nextLong());
                int ticks = 0;
                while (battle.result == 0) {
                    battle.advance(Battle.STEP);
                    boolean[] seen = new boolean[Hex.CELLS];
                    for (Fighter f : battle.fighters) {
                        if (!f.alive) continue;
                        assertTrue(!seen[f.cell], "deux Pokémon sur la case " + f.cell);
                        seen[f.cell] = true;
                    }
                    assertTrue(++ticks < 2000, "combat sans fin");
                }
                assertNotEquals(0, battle.result);
                assertTrue(battle.time() <= Battle.TIME_LIMIT + Battle.STEP);
            }
        }
    }

    @Test
    void sameSeedGivesSameResult() {
        List<GameState.Placed> a = GameState.generateEnemy(8, new Random(1)), b = GameState.generateEnemy(8, new Random(2));
        Battle x = new Battle(GameState.fighters(a, true), GameState.fighters(b, false), 7);
        Battle y = new Battle(GameState.fighters(a, true), GameState.fighters(b, false), 7);
        x.runToEnd();
        y.runToEnd();
        assertEquals(x.result, y.result);
        assertEquals(x.time(), y.time(), 1e-9);
    }
}
