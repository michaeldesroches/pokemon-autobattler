package autobattler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokedexTest {

    @Test
    void crossGenerationEvolutions() {
        assertTrue(Pokedex.get("onix").evolvesTo.equals("steelix"));
        assertTrue(Pokedex.get("insecateur").evolvesTo.equals("cizayox"));
        assertTrue(Pokedex.get("magneton").evolvesTo.equals("magnezone"));
        Unit onix = new Unit(Pokedex.get("onix"), 1, 2);
        Unit merged = onix.merged();
        assertNotNull(merged);
        assertTrue(merged.species == Pokedex.get("steelix"));
        assertTrue(merged.species.role() == Trait.GUARDIAN);
    }

    @Test
    void everyEvolutionExistsAndStaysInItsLine() {
        for (Species s : Pokedex.all()) {
            if (s.evolvesTo == null) continue;
            Species next = Pokedex.get(s.evolvesTo);
            assertNotNull(next, s.evolvesTo);
            assertTrue(next.line.equals(s.line), s.name);
            assertEquals(s.tier, next.tier);
        }
    }

    @Test
    void darkTypeMatchups() {
        assertEquals(0.25, PType.effectiveness(PType.PSYCHIC, new PType[]{PType.DARK}), 1e-9);
        assertEquals(2.0, PType.effectiveness(PType.DARK, new PType[]{PType.GHOST}), 1e-9);
        assertEquals(2.0, PType.effectiveness(PType.FIGHTING, new PType[]{PType.DARK}), 1e-9);
        assertEquals(0.5, PType.effectiveness(PType.DARK, new PType[]{PType.STEEL}), 1e-9);
    }

    @Test
    void newStarterCountsForStarterTrait() {
        assertTrue(Pokedex.isStarter("brin"));
        assertEquals(2, Pokedex.get("brindibou").traits().length);
    }
}
