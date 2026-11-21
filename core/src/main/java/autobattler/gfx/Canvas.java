package autobattler.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Petite surcouche de dessin sur SpriteBatch en coordonnées « écran » :
 * origine en haut à gauche, y vers le bas (comme la mise en page du jeu).
 */
public final class Canvas {
    private final SpriteBatch batch;
    private final Assets assets;
    private final float height;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tmp = new Color();
    /** Opacité globale appliquée à tout ce qui est dessiné (ex. Pokémon K.O.). */
    public float alpha = 1f;

    public Canvas(SpriteBatch batch, Assets assets, float height) {
        this.batch = batch;
        this.assets = assets;
        this.height = height;
    }

    public static Color rgb(int rgb) {
        return new Color((rgb << 8) | 0xFF);
    }

    public static Color rgba(int rgb, int alpha255) {
        return new Color((rgb << 8) | (alpha255 & 0xFF));
    }

    /** Couleur avec opacité modifiée (objet partagé : à utiliser immédiatement). */
    public Color fade(Color c, float a) {
        return tmp.set(c.r, c.g, c.b, c.a * a);
    }

    private void use(Color c) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
    }

    public void rect(float x, float y, float w, float h, Color c) {
        if (w <= 0 || h <= 0) return;
        use(c);
        batch.draw(assets.white, x, height - y - h, w, h);
    }

    public void round(float x, float y, float w, float h, Color c) {
        if (w <= 0 || h <= 0) return;
        if (w < 14 || h < 14) {
            rect(x, y, w, h, c);
            return;
        }
        use(c);
        assets.round.draw(batch, x, height - y - h, w, h);
    }

    public void outline(float x, float y, float w, float h, Color c) {
        if (w < 14 || h < 14) return;
        use(c);
        assets.outline.draw(batch, x, height - y - h, w, h);
    }

    public void ellipse(float cx, float cy, float rx, float ry, Color c) {
        use(c);
        batch.draw(assets.circle, cx - rx, height - cy - ry, rx * 2, ry * 2);
    }

    /** Hexagone « pointe en haut » de rayon {@code size} (centre → sommet). */
    public void hex(float cx, float cy, float size, Color c) {
        use(c);
        batch.draw(assets.hex, cx - size, height - cy - size, size * 2, size * 2);
    }

    public void hexOutline(float cx, float cy, float size, Color c) {
        use(c);
        batch.draw(assets.hexOutline, cx - size, height - cy - size, size * 2, size * 2);
    }

    public void image(TextureRegion r, float x, float y, float w, float h) {
        batch.setColor(1, 1, 1, alpha);
        batch.draw(r, x, height - y - h, w, h);
    }

    /** Image en miroir horizontal. */
    public void imageFlipped(TextureRegion r, float x, float y, float w, float h) {
        batch.setColor(1, 1, 1, alpha);
        batch.draw(r, x + w, height - y - h, -w, h);
    }

    /** Image teintée (ex. silhouette pour l'aperçu de l'adversaire). */
    public void imageTinted(TextureRegion r, float x, float y, float w, float h, Color c, boolean flip) {
        use(c);
        if (flip) batch.draw(r, x + w, height - y - h, -w, h);
        else batch.draw(r, x, height - y - h, w, h);
    }

    public float width(BitmapFont f, String s) {
        layout.setText(f, s);
        return layout.width;
    }

    /** Texte dont la ligne de base est à {@code baseline}. */
    public void text(BitmapFont f, String s, float x, float baseline, Color c) {
        f.setColor(c.r, c.g, c.b, c.a * alpha);
        f.draw(batch, s, x, height - baseline + f.getCapHeight());
    }

    public void centered(BitmapFont f, String s, float cx, float baseline, Color c) {
        text(f, s, cx - width(f, s) / 2, baseline, c);
    }

    public void right(BitmapFont f, String s, float right, float baseline, Color c) {
        text(f, s, right - width(f, s), baseline, c);
    }

    /** Texte sur plusieurs lignes, coupé aux espaces. */
    public void wrap(BitmapFont f, String s, float x, float y, float maxWidth, float lineHeight, Color c) {
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (width(f, test) > maxWidth && line.length() > 0) {
                text(f, line.toString(), x, y, c);
                y += lineHeight;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) text(f, line.toString(), x, y, c);
    }
}
