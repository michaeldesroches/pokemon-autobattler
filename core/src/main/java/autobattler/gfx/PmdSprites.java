package autobattler.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprites animés PMDCollab (assets/pmd*.png + pmd.txt, générés par tools/gen_assets.py).
 * Directions PMD : 0 bas, 1 bas-droite, 2 droite, 3 haut-droite, 4 haut, 5 haut-gauche, 6 gauche, 7 bas-gauche.
 * Seules 0 à 4 sont stockées : 5, 6 et 7 sont obtenues par symétrie de 3, 2 et 1.
 */
public final class PmdSprites implements Disposable {

    /** Une image d'animation : région rognée et décalage de son coin haut-gauche par rapport au point au sol. */
    public static final class Frame {
        public final TextureRegion region;
        public final int ox, oy;

        Frame(TextureRegion region, int ox, int oy) {
            this.region = region;
            this.ox = ox;
            this.oy = oy;
        }
    }

    public static final class Anim {
        public final int frames;
        public final int[] durations;
        public final int totalTicks;
        final Frame[][] byDir = new Frame[5][];

        Anim(int frames, int[] durations) {
            this.frames = frames;
            this.durations = durations;
            int t = 0;
            for (int d : durations) t += d;
            this.totalTicks = Math.max(1, t);
        }

        /** Durée totale en secondes. */
        public float duration() {
            return totalTicks / 60f;
        }
    }

    private final List<Texture> pages = new ArrayList<Texture>();
    private final Map<Integer, Map<String, Anim>> anims = new HashMap<Integer, Map<String, Anim>>();
    private final Map<Integer, TextureRegion> portraits = new HashMap<Integer, TextureRegion>();
    private final Map<Integer, Float> heights = new HashMap<Integer, Float>();

    public PmdSprites() {
        String[] lines = Gdx.files.internal("pmd.txt").readString("UTF-8").split("\n");
        int nPages = Integer.parseInt(lines[0].trim().split(" ")[1]);
        for (int p = 0; p < nPages; p++) {
            Texture t = new Texture(Gdx.files.internal("pmd" + p + ".png"));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            pages.add(t);
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split(" ");
            switch (f[0]) {
                case "A": {
                    int dex = Integer.parseInt(f[1]);
                    String[] ds = f[6].split(",");
                    int[] durs = new int[ds.length];
                    for (int k = 0; k < ds.length; k++) durs[k] = Integer.parseInt(ds[k]);
                    Anim a = new Anim(Integer.parseInt(f[3]), durs);
                    for (int d = 0; d < 5; d++) a.byDir[d] = new Frame[a.frames];
                    animsOf(dex).put(f[2], a);
                    break;
                }
                case "F": {
                    int dex = Integer.parseInt(f[1]);
                    Anim a = animsOf(dex).get(f[2]);
                    if (a == null) break;
                    int dir = Integer.parseInt(f[3]), frame = Integer.parseInt(f[4]);
                    TextureRegion r = new TextureRegion(pages.get(Integer.parseInt(f[5])), Integer.parseInt(f[6]),
                            Integer.parseInt(f[7]), Integer.parseInt(f[8]), Integer.parseInt(f[9]));
                    if (dir < 5 && frame < a.frames) a.byDir[dir][frame] = new Frame(r, Integer.parseInt(f[10]), Integer.parseInt(f[11]));
                    break;
                }
                case "P": {
                    portraits.put(Integer.parseInt(f[1]), new TextureRegion(pages.get(Integer.parseInt(f[2])),
                            Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6])));
                    break;
                }
                default:
                    break;
            }
        }
    }

    private Map<String, Anim> animsOf(int dex) {
        Map<String, Anim> m = anims.get(dex);
        if (m == null) {
            m = new HashMap<String, Anim>();
            anims.put(dex, m);
        }
        return m;
    }

    public boolean has(int dex) {
        return anims.containsKey(dex) && anims.get(dex).containsKey("Idle");
    }

    /** Animation demandée, ou Idle si elle n'existe pas pour ce Pokémon. */
    public Anim anim(int dex, String name) {
        Map<String, Anim> m = anims.get(dex);
        if (m == null) return null;
        Anim a = m.get(name);
        return a != null ? a : m.get("Idle");
    }

    public TextureRegion portrait(int dex) {
        return portraits.get(dex);
    }

    /** Image à afficher : {@code time} en secondes depuis le début de l'animation. */
    public Frame frame(Anim a, int dir8, float time, boolean loop) {
        int ticks = (int) (time * 60);
        if (loop) ticks %= a.totalTicks;
        else ticks = Math.min(ticks, a.totalTicks - 1);
        int idx = 0;
        while (idx < a.frames - 1 && ticks >= a.durations[idx]) {
            ticks -= a.durations[idx];
            idx++;
        }
        return a.byDir[storedDir(dir8)][idx];
    }

    /** Vrai si la direction doit être dessinée en miroir. */
    public static boolean mirrored(int dir8) {
        return dir8 >= 5;
    }

    private static int storedDir(int dir8) {
        return dir8 <= 4 ? dir8 : 8 - dir8;
    }

    /** Hauteur du Pokémon dans sa première image d'attente (pour l'échelle d'affichage). */
    public float idleHeight(int dex) {
        Float h = heights.get(dex);
        if (h == null) {
            Anim a = anim(dex, "Idle");
            Frame f = a == null ? null : a.byDir[0][0];
            h = f == null ? 32f : (float) f.region.getRegionHeight();
            heights.put(dex, h);
        }
        return h;
    }

    @Override
    public void dispose() {
        for (Texture t : pages) t.dispose();
    }
}
