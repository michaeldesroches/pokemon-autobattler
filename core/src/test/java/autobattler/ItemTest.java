package autobattler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTest {

    @Test
    void everyPairOfComponentsCombinesSymmetrically() {
        Set<Item> seen = new HashSet<Item>();
        for (Item a : Item.COMPONENTS)
            for (Item b : Item.COMPONENTS) {
                Item c = Item.combine(a, b);
                assertNotNull(c, a.name + " + " + b.name);
                assertTrue(c == Item.combine(b, a), "symétrie " + a.name + " + " + b.name);
                assertTrue(!c.isComponent(), c.name);
                seen.add(c);
            }
        assertEquals(21, seen.size());
        assertEquals(21, Item.combined().size());
    }

    @Test
    void combinedItemsCannotCombineFurther() {
        Item orbe = Item.combine(Item.BANDEAU, Item.BANDEAU);
        assertTrue(Item.combine(orbe, Item.BANDEAU) == null);
    }

    @Test
    void unitCombinesComponentsAndHoldsAtMostThreeItems() {
        Unit u = new Unit(Pokedex.get("machoc"), 1, 1);
        assertTrue(u.equip(Item.BANDEAU));
        assertTrue(u.equip(Item.BAIE));
        assertEquals(1, u.items.size());
        assertTrue(u.items.get(0) == Item.combine(Item.BANDEAU, Item.BAIE));
        assertTrue(u.equip(Item.combined().get(0)));
        assertTrue(u.equip(Item.combined().get(1)));
        assertEquals(3, u.items.size());
        assertTrue(!u.canEquip(Item.PLAQUE));
        assertTrue(!u.equip(Item.combined().get(2)));
    }

    @Test
    void itemsChangeFighterStats() {
        Unit plain = new Unit(Pokedex.get("machoc"), 1, 1);
        Unit equipped = new Unit(Pokedex.get("machoc"), 1, 1);
        equipped.equip(Item.BAIE);
        equipped.equip(Item.BANDEAU);
        Fighter a = new Fighter(plain, true, 0, 0, null), b = new Fighter(equipped, true, 0, 0, null);
        assertEquals((int) a.maxHp + 150, (int) b.maxHp);
        assertTrue(b.atk > a.atk);
        assertTrue(b.omnivamp > 0);
    }
}
