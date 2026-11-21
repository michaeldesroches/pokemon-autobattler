package autobattler.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Textures, sprites et polices (générés par tools/gen_assets.py dans assets/). */
public final class Assets implements Disposable {
    private static final int[] BOLD_SIZES = {10, 11, 12, 13, 15, 18, 22};
    private static final int[] REGULAR_SIZES = {12, 13};

    private final Texture spritesTex, roundTex, outlineTex, circleTex, hexTex, hexOutlineTex, rolesTex;
    private final Map<String, TextureRegion> sprites = new HashMap<String, TextureRegion>();
    private final Map<String, BitmapFont> fonts = new HashMap<String, BitmapFont>();

    public final NinePatch round, outline;
    public final TextureRegion circle, white, hex, hexOutline;
    /** Icônes de classe blanches (à teinter), indexées par {@code Trait.ordinal()}. */
    private final TextureRegion[] roleIcons;

    public Assets() {
        spritesTex = new Texture(Gdx.files.internal("sprites.png"));
        spritesTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        String index = Gdx.files.internal("sprites.txt").readString("UTF-8");
        for (String line : index.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(" ");
            sprites.put(p[0], new TextureRegion(spritesTex, Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]), Integer.parseInt(p[4])));
        }

        roundTex = linear("ui/round.png");
        outlineTex = linear("ui/round_outline.png");
        circleTex = linear("ui/circle.png");
        hexTex = linear("ui/hex.png");
        hexOutlineTex = linear("ui/hex_outline.png");
        round = new NinePatch(new TextureRegion(roundTex), 14, 14, 14, 14);
        round.scale(0.5f, 0.5f);
        outline = new NinePatch(new TextureRegion(outlineTex), 14, 14, 14, 14);
        outline.scale(0.5f, 0.5f);
        circle = new TextureRegion(circleTex);
        white = new TextureRegion(roundTex, 19, 19, 2, 2);
        hex = new TextureRegion(hexTex);
        hexOutline = new TextureRegion(hexOutlineTex);
        rolesTex = linear("ui/roles.png");
        int n = rolesTex.getHeight();
        roleIcons = new TextureRegion[rolesTex.getWidth() / n];
        for (int i = 0; i < roleIcons.length; i++) roleIcons[i] = new TextureRegion(rolesTex, i * n, 0, n, n);

        for (int s : BOLD_SIZES) loadFont("bold" + s);
        for (int s : REGULAR_SIZES) loadFont("regular" + s);
    }

    private static Texture linear(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return t;
    }

    private void loadFont(String name) {
        BitmapFont f = new BitmapFont(Gdx.files.internal("fonts/" + name + ".fnt"));
        f.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        f.getData().setScale(0.5f);
        f.setUseIntegerPositions(false);
        fonts.put(name, f);
    }

    /** Icône (blanche) d'une classe. */
    public TextureRegion roleIcon(autobattler.Trait t) {
        return roleIcons[t.ordinal()];
    }

    /** Sprite de face ou de dos (repli sur la face si le dos manque), ou null. */
    public TextureRegion sprite(int dex, boolean back) {
        TextureRegion r = sprites.get((back ? "back/" : "front/") + dex);
        if (r == null && back) r = sprites.get("front/" + dex);
        return r;
    }

    /** Police la plus proche de la taille demandée (en pixels du jeu). */
    public BitmapFont font(boolean bold, int size) {
        int[] sizes = bold ? BOLD_SIZES : REGULAR_SIZES;
        int best = sizes[0];
        for (int s : sizes) if (Math.abs(s - size) < Math.abs(best - size)) best = s;
        return fonts.get((bold ? "bold" : "regular") + best);
    }

    @Override
    public void dispose() {
        spritesTex.dispose();
        roundTex.dispose();
        outlineTex.dispose();
        circleTex.dispose();
        hexTex.dispose();
        hexOutlineTex.dispose();
        rolesTex.dispose();
        for (BitmapFont f : fonts.values()) f.dispose();
    }
}
