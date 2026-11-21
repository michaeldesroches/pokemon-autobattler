package autobattler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprites PokéAPI (https://github.com/PokeAPI/sprites) embarqués dans le jar sous /sprites/front et /sprites/back.
 * Les marges transparentes sont rognées au chargement pour un affichage plus grand.
 */
public final class Sprites {
    private static final Map<String, BufferedImage> CACHE = new HashMap<String, BufferedImage>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** Sprite de face (back = false) ou de dos (back = true), ou null s'il est introuvable. */
    public static BufferedImage get(int dex, boolean back) {
        String key = (back ? "back/" : "front/") + dex;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = load("/sprites/" + key + ".png");
            CACHE.put(key, img);
        }
        return img == MISSING ? null : img;
    }

    private static BufferedImage load(String path) {
        try {
            InputStream in = Sprites.class.getResourceAsStream(path);
            if (in == null) return MISSING;
            try {
                BufferedImage raw = ImageIO.read(in);
                return raw == null ? MISSING : trim(raw);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return MISSING;
        }
    }

    private static BufferedImage trim(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((src.getRGB(x, y) >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        if (maxX < 0) return MISSING;
        int tw = maxX - minX + 1, th = maxY - minY + 1;
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < th; y++)
            for (int x = 0; x < tw; x++)
                out.setRGB(x, y, src.getRGB(minX + x, minY + y));
        return out;
    }

    private Sprites() {
    }
}
