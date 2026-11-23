package autobattler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraitTest {

    private static Unit unit(String key) {
        return new Unit(Pokedex.get(key), 1, 1);
    }

    @Test
    void everyLineHasExactlyOneRole() {
        for (Species s : Pokedex.all()) {
            assertNotNull(s.role(), s.name);
            assertTrue(s.role().isRole(), s.name);
            assertTrue(s.role() == Pokedex.base(s.line).role(), s.name);
        }
        assertTrue(Pokedex.get("pikachu").traits().length == 2);
        assertTrue(Pokedex.get("onix").traits().length == 1);
    }

    @Test
    void traitLevelsCountDistinctLines() {
        List<Unit> team = new ArrayList<Unit>();
        team.add(unit("carapuce"));
        team.add(unit("carabaffe")); // même lignée : ne compte qu'une fois
        assertTrue(!Synergies.traitLevels(team).containsKey(Trait.GUARDIAN));
        team.add(unit("onix"));
        assertEquals(1, (int) Synergies.traitLevels(team).get(Trait.GUARDIAN));
    }

    @Test
    void bonusesApplyOnlyToUnitsWithTheTrait() {
        List<Unit> team = new ArrayList<Unit>();
        team.add(unit("machoc"));
        team.add(unit("osselait"));
        team.add(unit("abra"));
        Map<Trait, Integer> traits = Synergies.traitLevels(team);
        Fighter fighter = new Fighter(team.get(0), true, 0, 0, null, traits);
        Fighter plain = new Fighter(team.get(0), true, 0, 0, null, null);
        Fighter mystic = new Fighter(team.get(2), true, 0, 0, null, traits);
        Fighter mysticPlain = new Fighter(team.get(2), true, 0, 0, null, null);
        assertTrue(fighter.maxHp > plain.maxHp);
        assertTrue(fighter.lifesteal > 0);
        assertEquals(mysticPlain.maxHp, mystic.maxHp, 1e-9);
    }

    @Test
    void marksmenGainRangeAtSecondTier() {
        List<Unit> team = new ArrayList<Unit>();
        team.add(unit("pikachu"));
        team.add(unit("salameche"));
        Fighter tier1 = new Fighter(team.get(0), true, 3, 3, null, Synergies.traitLevels(team));
        assertEquals(Pokedex.get("pikachu").range(), tier1.range);
        team.add(unit("larvibule"));
        Fighter tier2 = new Fighter(team.get(0), true, 3, 3, null, Synergies.traitLevels(team));
        assertEquals(Pokedex.get("pikachu").range() + 1, tier2.range);
    }

    @Test
    void assassinsLeapToTheEnemyBacklineAtStart() {
        List<Unit> mine = new ArrayList<Unit>();
        mine.add(unit("fantominus"));
        mine.add(unit("rattata"));
        List<GameState.Placed> enemy = GameState.arrange(java.util.Arrays.asList(unit("onix"), unit("abra")), new Random(1));
        List<GameState.Placed> placed = new ArrayList<GameState.Placed>();
        for (GameState.Placed p : GameState.arrange(mine, new Random(2))) placed.add(p);
        Battle b = new Battle(GameState.fighters(placed, true), GameState.fighters(enemy, false), 5);
        for (Fighter f : b.fighters) {
            if (!f.player) continue;
            assertTrue(f.moving(), f.unit.species.name + " doit sauter au début");
            assertTrue(Hex.row(f.cell) <= 1, f.unit.species.name + " doit atterrir près du fond adverse (ligne " + Hex.row(f.cell) + ")");
        }
    }

    @Test
    void autoPlacementPutsGuardiansInFront() {
        List<Unit> units = java.util.Arrays.asList(unit("abra"), unit("onix"), unit("pikachu"), unit("machoc"));
        for (GameState.Placed p : GameState.arrange(units, new Random(3))) {
            Trait r = p.unit.species.role();
            if (r.frontline) assertEquals(0, p.row, p.unit.species.name);
            else assertTrue(p.row >= 2, p.unit.species.name);
        }
    }

    @Test
    void placementHintsMatchRoles() {
        assertTrue(Trait.GUARDIAN.suitsRow(0) && Trait.GUARDIAN.suitsRow(1) && !Trait.GUARDIAN.suitsRow(3));
        assertTrue(!Trait.MARKSMAN.suitsRow(0) && Trait.MARKSMAN.suitsRow(3));
        assertTrue(!Trait.MYSTIC.suitsRow(0) && Trait.MYSTIC.suitsRow(2));
        for (int r = 0; r < GameState.ROWS; r++) assertTrue(Trait.ASSASSIN.suitsRow(r));
        // le placement automatique respecte toujours les indications
        List<Unit> units = new ArrayList<Unit>();
        for (Species s : Pokedex.all()) if (s.tier == 1 && Pokedex.base(s.line) == s) units.add(new Unit(s, 1, 1));
        for (GameState.Placed p : GameState.arrange(units.subList(0, Math.min(9, units.size())), new Random(4)))
            assertTrue(p.unit.species.role().suitsRow(p.row), p.unit.species.name + " ligne " + p.row);
    }
}
