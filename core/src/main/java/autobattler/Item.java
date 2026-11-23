package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Objets tenus, façon TFT : 6 composants, et 21 objets combinés (toute paire de composants, y compris
 * deux identiques). Un objet combiné cumule les statistiques de ses deux composants et ajoute un effet.
 */
public final class Item {

    /** Effets spéciaux des objets combinés (valeur dans {@link #value}). */
    public enum Effect {
        NONE, DAMAGE_AMP, CRIT, ATKSPEED_STACK, THORNS, OMNIVAMP, MANA_ON_HIT, ABILITY_AMP, ABILITY_STACK,
        SHIELD_START, SITRUS, MANA_REFUND, STUN_CHANCE, ARMOR_PEN, HEAL_ON_CAST, CC_IMMUNE, FOCUS_SASH,
        SHIELD_ON_CAST, REGEN, MANA_REDUCE, HASTE_ON_CAST, ATTACK_SPEED
    }

    public final String id, name, abbr, description;
    public final int rgb;
    public final Item a, b;
    // Statistiques (cumulées des composants pour un objet combiné)
    public final double atkPct, abilityPct, speedPct, defPct, hp, mana;
    public final Effect effect;
    public final double value;

    private Item(String id, String name, String abbr, int rgb, String description, Item a, Item b,
                 double atkPct, double abilityPct, double speedPct, double defPct, double hp, double mana,
                 Effect effect, double value) {
        this.id = id;
        this.name = name;
        this.abbr = abbr;
        this.rgb = rgb;
        this.description = description;
        this.a = a;
        this.b = b;
        this.atkPct = atkPct;
        this.abilityPct = abilityPct;
        this.speedPct = speedPct;
        this.defPct = defPct;
        this.hp = hp;
        this.mana = mana;
        this.effect = effect;
        this.value = value;
    }

    public boolean isComponent() {
        return a == null;
    }

    // ------------------------------------------------------------------ composants

    public static final Item BANDEAU = component("bandeau", "Bandeau Muscle", "BM", 0xD9534F, "+12% Attaque", 0.12, 0, 0, 0, 0, 0);
    public static final Item LUNETTES = component("lunettes", "Lunettes Sages", "LS", 0x9B59D0, "+20% puissance des capacités", 0, 0.20, 0, 0, 0, 0);
    public static final Item VIVE_GRIFFE = component("griffe", "Vive Griffe", "VG", 0xF0B429, "+12% vitesse d'attaque", 0, 0, 0.12, 0, 0, 0);
    public static final Item PLAQUE = component("plaque", "Plaque Fer", "PF", 0x95A5B8, "+20% Défense", 0, 0, 0, 0.20, 0, 0);
    public static final Item BAIE = component("baie", "Baie Oran", "BO", 0x4CC38A, "+150 PV", 0, 0, 0, 0, 150, 0);
    public static final Item ENCENS = component("encens", "Encens Mer", "EM", 0x5DA9FF, "+15 mana au départ", 0, 0, 0, 0, 0, 15);

    public static final List<Item> COMPONENTS = Collections.unmodifiableList(java.util.Arrays.asList(
            BANDEAU, LUNETTES, VIVE_GRIFFE, PLAQUE, BAIE, ENCENS));

    private static final Map<String, Item> COMBINED = new HashMap<String, Item>();
    private static final List<Item> ALL_COMBINED = new ArrayList<Item>();

    static {
        combine(BANDEAU, BANDEAU, "Orbe Vie", "OV", "Dégâts infligés +20%.", Effect.DAMAGE_AMP, 0.20);
        combine(BANDEAU, LUNETTES, "Griffe Rasoir", "GR", "25% de coups critiques (x1,6), capacités comprises.", Effect.CRIT, 0.25);
        combine(BANDEAU, VIVE_GRIFFE, "Mouchoir Choix", "MC", "Chaque attaque : +5% vitesse d'attaque (max 10 fois).", Effect.ATKSPEED_STACK, 0.05);
        combine(BANDEAU, PLAQUE, "Casque Brut", "CB", "Renvoie 25% des dégâts subis à l'attaquant.", Effect.THORNS, 0.25);
        combine(BANDEAU, BAIE, "Grelot Coque", "GC", "Vol de vie 25% sur tous les dégâts.", Effect.OMNIVAMP, 0.25);
        combine(BANDEAU, ENCENS, "Bandeau Choix", "BC", "Les attaques rendent 5 mana de plus.", Effect.MANA_ON_HIT, 5);
        combine(LUNETTES, LUNETTES, "Lunettes Choix", "LC", "Puissance des capacités +30% (en plus).", Effect.ABILITY_AMP, 0.30);
        combine(LUNETTES, VIVE_GRIFFE, "Métronome", "ME", "Chaque capacité : +12% de puissance (cumulable).", Effect.ABILITY_STACK, 0.12);
        combine(LUNETTES, PLAQUE, "Gilet Assaut", "GA", "Bouclier de 30% des PV pendant 5 s au début.", Effect.SHIELD_START, 0.30);
        combine(LUNETTES, BAIE, "Baie Sitrus", "BS", "Une fois sous 50% PV : soigne 35% des PV max.", Effect.SITRUS, 0.35);
        combine(LUNETTES, ENCENS, "Perle Lustrée", "PL", "Après une capacité, récupère 30 mana.", Effect.MANA_REFUND, 30);
        combine(VIVE_GRIFFE, VIVE_GRIFFE, "Poudre Vite", "PV", "+30% vitesse d'attaque (en plus).", Effect.ATTACK_SPEED, 0.30);
        combine(VIVE_GRIFFE, PLAQUE, "Lentille Zoom", "LZ", "Les attaques ignorent 40% de la Défense.", Effect.ARMOR_PEN, 0.40);
        combine(VIVE_GRIFFE, BAIE, "Roche Royale", "RR", "20% de chances d'étourdir 1 s à chaque attaque.", Effect.STUN_CHANCE, 0.20);
        combine(VIVE_GRIFFE, ENCENS, "Mouchoir Vif", "MV", "Après une capacité : +60% vitesse d'attaque 3 s.", Effect.HASTE_ON_CAST, 3);
        combine(PLAQUE, PLAQUE, "Herbe Blanche", "HB", "Immunisé : gel, sommeil, paralysie, étourdissement.", Effect.CC_IMMUNE, 0);
        combine(PLAQUE, BAIE, "Ceinture Force", "CF", "Survit à un coup fatal (1 fois), +25% de bouclier.", Effect.FOCUS_SASH, 0.25);
        combine(PLAQUE, ENCENS, "Écaille Océane", "EO", "Chaque capacité : bouclier de 20% des PV (3 s).", Effect.SHIELD_ON_CAST, 0.20);
        combine(BAIE, BAIE, "Restes", "RE", "Régénère 3% des PV max par seconde.", Effect.REGEN, 0.03);
        combine(BAIE, ENCENS, "Encens Pur", "EP", "Chaque capacité soigne 15% des PV max.", Effect.HEAL_ON_CAST, 0.15);
        combine(ENCENS, ENCENS, "Plaque Esprit", "PE", "Mana max -20%.", Effect.MANA_REDUCE, 0.20);
    }

    private static Item component(String id, String name, String abbr, int rgb, String desc,
                                  double atk, double ap, double as, double def, double hp, double mana) {
        return new Item(id, name, abbr, rgb, desc, null, null, atk, ap, as, def, hp, mana, Effect.NONE, 0);
    }

    private static void combine(Item a, Item b, String name, String abbr, String desc, Effect effect, double value) {
        Item it = new Item(a.id + "+" + b.id, name, abbr, mix(a.rgb, b.rgb), desc, a, b,
                a.atkPct + b.atkPct, a.abilityPct + b.abilityPct, a.speedPct + b.speedPct,
                a.defPct + b.defPct, a.hp + b.hp, a.mana + b.mana, effect, value);
        COMBINED.put(key(a, b), it);
        ALL_COMBINED.add(it);
    }

    private static int mix(int x, int y) {
        int r = (((x >> 16) & 255) + ((y >> 16) & 255)) / 2;
        int g = (((x >> 8) & 255) + ((y >> 8) & 255)) / 2;
        int b = ((x & 255) + (y & 255)) / 2;
        return (r << 16) | (g << 8) | b;
    }

    private static String key(Item a, Item b) {
        return a.id.compareTo(b.id) <= 0 ? a.id + "|" + b.id : b.id + "|" + a.id;
    }

    /** Objet obtenu en combinant deux composants (null si l'un n'est pas un composant). */
    public static Item combine(Item a, Item b) {
        if (a == null || b == null || !a.isComponent() || !b.isComponent()) return null;
        return COMBINED.get(key(a, b));
    }

    public static List<Item> combined() {
        return Collections.unmodifiableList(ALL_COMBINED);
    }

    /** Résumé des statistiques, ex. « +12% Attaque, +150 PV ». */
    public String statsText() {
        List<String> p = new ArrayList<String>();
        if (atkPct > 0) p.add("+" + pct(atkPct) + " Attaque");
        if (abilityPct > 0) p.add("+" + pct(abilityPct) + " puissance");
        if (speedPct > 0) p.add("+" + pct(speedPct) + " vit. d'attaque");
        if (defPct > 0) p.add("+" + pct(defPct) + " Défense");
        if (hp > 0) p.add("+" + (int) hp + " PV");
        if (mana > 0) p.add("+" + (int) mana + " mana");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.size(); i++) sb.append(i == 0 ? "" : ", ").append(p.get(i));
        return sb.toString();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    @Override
    public String toString() {
        return name;
    }
}
