package autobattler;

/** Une espèce de Pokémon avec ses statistiques simplifiées (Atq = max(Atq, Atq Spé), Déf = moyenne des défenses). */
public final class Species {
    public final String key;
    public final String name;
    public final int dex;
    public final int tier;
    public final String line;
    public final int hp, atk, def, spe;
    public final String evolvesTo;
    public final PType[] types;

    Species(String key, String name, int dex, int tier, String line,
            int hp, int atk, int def, int spe, String evolvesTo, PType... types) {
        this.key = key;
        this.name = name;
        this.dex = dex;
        this.tier = tier;
        this.line = line;
        this.hp = hp;
        this.atk = atk;
        this.def = def;
        this.spe = spe;
        this.evolvesTo = evolvesTo;
        this.types = types;
    }

    public PType moveType() {
        return types[0];
    }

    public boolean hasType(PType t) {
        for (PType x : types) if (x == t) return true;
        return false;
    }

    public Species evolution() {
        return evolvesTo == null ? null : Pokedex.get(evolvesTo);
    }
}
