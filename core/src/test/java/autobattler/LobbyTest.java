package autobattler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {

    @Test
    void eightPlayersShareOnePool() {
        Lobby l = new Lobby(1);
        assertEquals(Lobby.SIZE, l.players().size());
        GameState h = l.human();
        GameState bot = l.players().get(1);
        // les bots ont déjà acheté au premier tour : le pool est le même pour tous
        int i = 0;
        while (h.shop[i] == null) i++;
        Species sp = h.shop[i];
        int before = bot.poolCount(sp);
        assertEquals(before, h.poolCount(sp));
        h.gold = 10;
        assertTrue(h.buy(i));
        assertEquals(before - 1, bot.poolCount(sp));
    }

    @Test
    void pvpRoundsPairEveryoneSymmetrically() {
        Lobby l = new Lobby(2);
        GameState h = l.human();
        assertTrue(!GameState.isPveRound(h.round));
        for (GameState p : l.players()) {
            int o = l.opponentOf(p);
            assertTrue(o >= 0 && o != l.index(p), p.name);
            assertEquals(l.index(p), l.opponentOf(l.players().get(o)), p.name + " et son adversaire");
            assertTrue(p.opponentName != null && !p.opponentGhost);
        }
        assertTrue(!h.enemy.isEmpty(), "l'équipe de l'adversaire du joueur est visible");
    }

    @Test
    void fullGameEndsWithDistinctPlacements() {
        for (long seed = 3; seed < 8; seed++) {
            Lobby l = new Lobby(seed, Bot.naive());
            GameState h = l.human();
            while (h.phase != GameState.Phase.GAMEOVER && h.round < 80) {
                assertTrue(h.startCombat());
                h.battle.runToEnd();
                h.finishCombat();
                if (h.phase == GameState.Phase.RESULT) h.nextRound();
            }
            assertTrue(h.phase == GameState.Phase.GAMEOVER, "la partie se termine");
            assertTrue(h.placement >= 1 && h.placement <= Lobby.SIZE);
            Set<Integer> seen = new HashSet<Integer>();
            for (GameState p : l.players()) {
                if (p.placement == 0) continue;
                assertTrue(seen.add(p.placement), "classement " + p.placement + " en double");
                if (p != h) assertEquals(0, p.boardCount(), p.name + " éliminé rend ses Pokémon");
            }
        }
    }

    @Test
    void oddSurvivorsFaceAGhost() {
        Lobby l = new Lobby(9, Bot.naive());
        GameState h = l.human();
        boolean sawGhost = false;
        while (h.phase != GameState.Phase.GAMEOVER && h.round < 80 && !sawGhost) {
            h.startCombat();
            h.battle.runToEnd();
            h.finishCombat();
            if (h.phase == GameState.Phase.RESULT) h.nextRound();
            if (l.aliveCount() % 2 == 1 && !GameState.isPveRound(h.round) && h.phase == GameState.Phase.PREP) {
                int ghosts = 0;
                for (GameState p : l.players()) if (p.placement == 0 && p.opponentGhost) ghosts++;
                assertEquals(1, ghosts);
                sawGhost = true;
            }
        }
        assertTrue(sawGhost, "un nombre impair de survivants doit arriver");
    }
}
