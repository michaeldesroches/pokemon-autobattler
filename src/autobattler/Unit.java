package autobattler;

/** Un Pokémon possédé par un joueur (banc ou plateau). */
public final class Unit {
    private static int nextId = 1;

    public final int uid;
    public final Species species;
    public final int stars;
    public int invested;

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

    public String displayName() {
        return species.name + (stars > 1 ? " " + stars + "★" : "");
    }
}
