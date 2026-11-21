package autobattler.gfx;

/** Rectangle en coordonnées écran (origine en haut à gauche). */
public final class Rect {
    public final float x, y, w, h;

    public Rect(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public Rect union(Rect o) {
        float nx = Math.min(x, o.x), ny = Math.min(y, o.y);
        return new Rect(nx, ny, Math.max(x + w, o.x + o.w) - nx, Math.max(y + h, o.y + o.h) - ny);
    }

    public float cx() {
        return x + w / 2;
    }

    public float cy() {
        return y + h / 2;
    }
}
