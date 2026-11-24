package autobattler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryTest {

    @Test
    void sixLegendariesInTier4AtFiveGold() {
        List<Species> tier4 = Pokedex.shopPool(4);
        assertEquals(6, tier4.size());
        for (Species s : tier4) {
            assertTrue(Pokedex.isLegendary(s.line), s.name);
            assertEquals(5, s.cost(), s.name);
            assertTrue(s.evolvesTo == null, s.name + " n'évolue pas");
            assertEquals(2, s.traits().length);
            assertTrue(s.traits()[1] == Trait.LEGENDARY);
            assertEquals(4, new Pool().count(s), "4 exemplaires de " + s.name);
        }
        // chaque rôle a son légendaire
        for (Trait role : new Trait[]{Trait.GUARDIAN, Trait.FIGHTER, Trait.ASSASSIN, Trait.MYSTIC, Trait.MARKSMAN}) {
            boolean found = false;
            for (Species s : tier4) found |= s.role() == role;
            assertTrue(found, role.label);
        }
    }

    @Test
    void legendariesOnlyFromLevel7() {
        for (int lvl = GameState.START_LEVEL; lvl <= GameState.MAX_LEVEL; lvl++) {
            int[] odds = GameState.shopOdds(lvl);
            assertEquals(GameState.TIERS, odds.length);
            int sum = 0;
            for (int o : odds) sum += o;
            assertEquals(100, sum, "niveau " + lvl);
            assertTrue(lvl >= 7 ? odds[3] > 0 : odds[3] == 0, "niveau " + lvl);
        }
    }

    @Test
    void buyingALegendaryCostsFiveAndSellsForFive() {
        GameState s = new GameState(3);
        s.shop[0] = Pokedex.get("mewtwo");
        s.gold = 4;
        assertTrue(!s.buy(0), "4 or ne suffisent pas");
        s.gold = 5;
        assertTrue(s.buy(0));
        assertEquals(0, s.gold);
        assertEquals(5, s.bench[0].invested);
    }

    @Test
    void legendaryBonusHelpsTheWholeTeam() {
        List<Unit> team = new ArrayList<Unit>();
        Unit machoc = new Unit(Pokedex.get("machoc"), 1, 1);
        team.add(machoc);
        Fighter without = new Fighter(machoc, true, 0, 0, null, Synergies.traitLevels(team));
        team.add(new Unit(Pokedex.get("artikodin"), 1, 5));
        Fighter with1 = new Fighter(machoc, true, 0, 0, null, Synergies.traitLevels(team));
        assertEquals(without.atk * 1.10, with1.atk, 1e-6);
        assertEquals(without.maxHp, with1.maxHp, 1e-6);
        team.add(new Unit(Pokedex.get("darkrai"), 1, 5));
        Fighter with2 = new Fighter(machoc, true, 0, 0, null, Synergies.traitLevels(team));
        assertEquals(without.maxHp * 1.15, with2.maxHp, 1e-6);
    }
}
