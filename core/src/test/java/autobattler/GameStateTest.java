package autobattler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateTest {

    @Test
    void buyingXpLevelsUpAndRaisesTeamSize() {
        GameState s = new GameState(1);
        s.gold = 1000;
        assertEquals(GameState.START_LEVEL, s.level);
        assertEquals(GameState.START_LEVEL, s.maxUnits());
        assertTrue(s.buyXp());
        assertEquals(GameState.START_LEVEL + 1, s.level);
        assertEquals(s.level, s.maxUnits());
        assertEquals(1000 - GameState.XP_COST, s.gold);
        while (s.level < GameState.MAX_LEVEL) assertTrue(s.buyXp());
        assertTrue(!s.buyXp());
    }

    @Test
    void sharedPoolShrinksOnBuyAndRefillsOnSell() {
        GameState s = new GameState(2);
        s.gold = 50;
        Species bulbi = Pokedex.get("bulbizarre");
        int before = s.poolCount(bulbi);
        s.shop[0] = bulbi;
        assertTrue(s.buy(0));
        assertEquals(before - 1, s.poolCount(bulbi));
        int slot = -1;
        for (int i = 0; i < GameState.BENCH; i++) if (s.bench[i] != null && s.bench[i].species == bulbi) slot = i;
        assertTrue(s.sell(slot));
        assertEquals(before, s.poolCount(bulbi));
    }

    @Test
    void mergingKeepsCopiesAndItems() {
        GameState s = new GameState(3);
        s.gold = 50;
        s.items.clear();
        Species sala = Pokedex.get("salameche");
        int before = s.poolCount(sala);
        for (int i = 0; i < 3; i++) {
            s.shop[i] = sala;
            assertTrue(s.buy(i));
            if (i == 0) {
                s.items.add(Item.BANDEAU);
                assertTrue(s.equip(0, 0));
            }
        }
        Unit evolved = null;
        for (Unit u : s.bench) if (u != null) evolved = u;
        assertTrue(evolved != null && evolved.species == Pokedex.get("reptincel"));
        assertEquals(3, evolved.copies);
        assertEquals(1, evolved.items.size());
        assertEquals(before - 3, s.poolCount(sala));
        int slot = -1;
        for (int i = 0; i < GameState.BENCH; i++) if (s.bench[i] == evolved) slot = i;
        assertTrue(s.sell(slot));
        assertEquals(before, s.poolCount(sala));
        assertEquals(1, s.items.size());
    }

    @Test
    void pveRoundsGiveLoot() {
        assertTrue(GameState.isPveRound(2) && GameState.isPveRound(6) && !GameState.isPveRound(3));
        GameState s = new GameState(4);
        s.items.clear();
        while (!s.isPveRound()) {
            s.shop[0] = Pokedex.get("ronflex");
            s.gold = 10;
            s.buy(0);
            for (int i = 0; i < GameState.BENCH; i++) if (s.bench[i] != null) s.move(i, GameState.boardSlot(0, 3));
            assertTrue(s.startCombat());
            s.battle.runToEnd();
            s.finishCombat();
            if (s.phase == GameState.Phase.GAMEOVER) return;
            s.nextRound();
        }
        assertTrue(s.startCombat());
        s.battle.runToEnd();
        s.finishCombat();
        assertTrue(!s.lastLoot.isEmpty());
        assertEquals(s.lastLoot.size(), s.items.size());
    }
}
