package autobattler;

/** Un Pokémon possédé par un joueur (banc ou plateau). */
public final class Unit {
    private static int nextId = 1;

    public final int uid;
    public final Species species;
    public final int stars;
    public int invested;
    /** Nombre d'exemplaires de base « contenus » (1, 3, 9…) : rendus au pool partagé à la revente. */
    public int copies = 1;
    /** Objets tenus (3 au maximum). */
    public final java.util.List<Item> items = new java.util.ArrayList<Item>();
    public static final int MAX_ITEMS = 3;

    public Unit(Species species, int stars, int invested) {
        this.uid = nextId++;
        this.species = species;
        this.stars = stars;
        this.invested = invested;
    }

    /** Clé de fusion : 3 unités ayant la même clé fusionnent. */
    public String mergeKey() {
        return species.key + "#" + stars;
    }

    /** Résultat de la fusion de 3 exemplaires, ou null si déjà au maximum. */
    public Unit merged() {
        Species evo = species.evolution();
        if (evo != null && stars == 1) return new Unit(evo, 1, invested * 3);
        if (stars < 3) return new Unit(species, stars + 1, invested * 3);
        return null;
    }

    /**
     * Peut recevoir cet objet : un composant se combine avec un composant déjà tenu,
     * sinon il faut une place libre.
     */
    public boolean canEquip(Item it) {
        if (it.isComponent()) {
            for (Item x : items) if (x.isComponent()) return true;
        }
        return items.size() < MAX_ITEMS;
    }

    /** Équipe l'objet (en combinant avec un composant déjà tenu si possible). */
    public boolean equip(Item it) {
        if (it.isComponent()) {
            for (int i = 0; i < items.size(); i++) {
                Item combined = Item.combine(items.get(i), it);
                if (combined != null) {
                    items.set(i, combined);
                    return true;
                }
            }
        }
        if (items.size() >= MAX_ITEMS) return false;
        items.add(it);
        return true;
    }

    public String displayName() {
        return species.name + (stars > 1 ? " " + stars + "★" : "");
    }
}
