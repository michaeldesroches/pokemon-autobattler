package autobattler.gfx;

import autobattler.Ability;
import autobattler.Battle;
import autobattler.Fighter;
import autobattler.GameState;
import autobattler.Hex;
import autobattler.Item;
import autobattler.Lobby;
import autobattler.PType;
import autobattler.Pokedex;
import autobattler.Species;
import autobattler.Synergies;
import autobattler.Trait;
import autobattler.Unit;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Écran de jeu en 1280×720 : plateau hexagonal en perspective, Pokémon animés (sprites PMDCollab),
 * effets, interface façon TFT et interactions souris/tactile.
 */
public final class GameView extends InputAdapter {
    public static final int W = 1280, H = 720;

    // ---- Plateau en perspective : 7 colonnes × 8 lignes (0 = fond adverse, 7 = avant du camp joueur)
    private static final float HW = 84f;          // largeur d'un hexagone à l'échelle 1
    private static final float SQUASH = 0.56f;     // aplatissement vertical
    private static final float BOARD_CX = 560f, BOARD_Y0 = 150f;
    private static final float[] ROW_Y = new float[Hex.ROWS];

    static {
        ROW_Y[0] = BOARD_Y0;
        for (int r = 1; r < Hex.ROWS; r++) {
            float avg = (persp(r - 1) + persp(r)) / 2;
            ROW_Y[r] = ROW_Y[r - 1] + HW * 1.1547f * 0.75f * SQUASH * avg;
        }
    }

    private static final Rect ARENA = new Rect(204, 50, 712, 412);
    private static final Rect LEFT = new Rect(12, 54, 180, 404);
    private static final Rect RIGHT = new Rect(928, 54, 340, 404);
    private static final int BENCH_X = 211, BENCH_Y = 480, BENCH_STEP = 88, BENCH_W = 82, BENCH_H = 76;
    private static final int INV_X = 12, INV_Y = 480, INV_W = 32, INV_H = 34, INV_GAP = 4;
    private static final Rect LEVEL_PANEL = new Rect(12, 568, 180, 142);
    private static final int SHOP_X = 213, SHOP_Y = 568, SHOP_STEP = 140, SHOP_W = 134, SHOP_H = 142;
    private static final Rect SHOP_AREA = new Rect(SHOP_X, SHOP_Y, 5 * SHOP_STEP, SHOP_H);
    private static final Rect BTN_A = new Rect(928, 568, 166, 44);
    private static final Rect BTN_B = new Rect(1102, 568, 166, 44);
    private static final Rect BTN_LOCK = new Rect(928, 618, 166, 40);
    private static final Rect BTN_SELL = new Rect(1102, 618, 166, 40);
    private static final Rect BTN_MAIN = new Rect(928, 664, 340, 46);
    private static final Rect BTN_SOUND = new Rect(1196, 9, 72, 26);

    public static final Color BG = Canvas.rgb(0x161A24);
    private static final Color PANEL = Canvas.rgb(0x242A38);
    private static final Color PANEL_LIGHT = Canvas.rgb(0x323A4C);
    private static final Color DISABLED = Canvas.rgb(0x2A2F3B);
    private static final Color TRACK = Canvas.rgb(0x10131A);
    private static final Color TEXT = Canvas.rgb(0xE8ECF4);
    private static final Color SOFT = Canvas.rgb(0xC5CEDF);
    private static final Color MUTED = Canvas.rgb(0x8C97AD);
    private static final Color GOLD = Canvas.rgb(0xF5C542);
    private static final Color ACCENT = Canvas.rgb(0xE84A5F);
    private static final Color GOOD = Canvas.rgb(0x4CC38A);
    private static final Color BLUE = Canvas.rgb(0x5DA9FF);
    private static final Color PURPLE = Canvas.rgb(0x7E6BE0);
    private static final Color ORANGE = Canvas.rgb(0xFF9F43);
    private static final Color DODGE = Canvas.rgb(0x9AD0FF);
    private static final Color HEAL = Canvas.rgb(0x7CF0A0);
    private static final Color ENEMY_TILE = Canvas.rgb(0x4A2F42);
    private static final Color ENEMY_TILE2 = Canvas.rgb(0x41293A);
    private static final Color PLAYER_TILE = Canvas.rgb(0x27503F);
    private static final Color PLAYER_TILE2 = Canvas.rgb(0x224637);
    private static final Color WHITE = Canvas.rgb(0xFFFFFF);
    private static final Color BLACK = Canvas.rgb(0x000000);
    private static final Color TIER1 = Canvas.rgb(0x7D8799);
    private static final Color TIER2 = Canvas.rgb(0x3FA36B);
    private static final Color TIER3 = Canvas.rgb(0x9B59D0);

    private final Assets assets;
    private final PmdSprites pmd;
    private final Sounds sounds;
    private final Viewport viewport;
    /** Lobby de 8 : le joueur et 7 bots (champions d'arène). */
    private final Lobby lobby = new Lobby(System.currentTimeMillis());
    private final GameState state = lobby.human();
    private final Vector2 tmpVec = new Vector2();
    private final Random fxRng = new Random(1);

    private int speed = 1;
    private double endDelay;
    private float clock;
    private float shake;
    private int lastLevel = state.level;
    private GameState.Phase lastPhase = state.phase;

    private int selected = -1;
    private int pressSlot = -1;
    private int pressItem = -1;
    private boolean pressed;
    private float pressX, pressY;
    private boolean dragging;
    private float mx = -1, my = -1;

    // ---- animations des combattants (côté affichage uniquement)
    private static final class AnimState {
        String name = "Idle";
        float t;
        int dir;
        boolean once;
        /** Halo de lancement de capacité (1 → 0). */
        float glow;
        /** Minuteur des particules d'ambiance (statuts). */
        float fxTimer;
        /** Vrai si l'étourdissement en cours vient du gel. */
        boolean frozen;
    }

    private final Map<Fighter, AnimState> animStates = new HashMap<Fighter, AnimState>();

    private static final class Floater {
        float x, y, life;
        final String text;
        final Color color;
        final boolean big;

        Floater(float x, float y, String text, Color color, boolean big) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = new Color(color);
            this.big = big;
            this.life = big ? 1.2f : 0.8f;
        }
    }

    private static final class Projectile {
        final Fighter src, tgt;
        final Color color;
        final PType type;
        final boolean big;
        /** Positions figées au départ (la cible peut mourir en vol). */
        final float x0, y0, x1, y1;
        float t, trail;

        Projectile(Fighter src, Fighter tgt, PType type, boolean big, float x0, float y0, float x1, float y1) {
            this.src = src;
            this.tgt = tgt;
            this.type = type;
            this.color = Canvas.rgb(type.rgb);
            this.big = big;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        /** Tir en cloche (roche, eau, poison, sol) plutôt qu'en ligne droite. */
        boolean lobbed() {
            return type == PType.ROCK || type == PType.GROUND || type == PType.WATER || type == PType.POISON;
        }

        float x() {
            return x0 + (x1 - x0) * t;
        }

        float y() {
            float arc = lobbed() ? (float) Math.sin(Math.PI * t) * (big ? 46 : 32) : 0;
            return y0 + (y1 - y0) * t - arc;
        }
    }

    /** Onde de choc : anneau qui s'élargit (zones, lancements, K.O.). */
    private static final class Ring {
        final float x, y, maxR;
        final Color color;
        final float duration;
        float t;

        Ring(float x, float y, float maxR, Color color, float duration) {
            this.x = x;
            this.y = y;
            this.maxR = maxR;
            this.color = new Color(color);
            this.duration = duration;
        }
    }

    private static final class Area {
        final int cell, radius;
        final Color color;
        float life = 0.5f;

        Area(int cell, int radius, Color color) {
            this.cell = cell;
            this.radius = radius;
            this.color = new Color(color);
        }
    }

    private static final class Particle {
        float x, y, vx, vy, life, maxLife, size, gravity = 160;
        final Color color;

        Particle(float x, float y, float vx, float vy, float life, float size, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = this.maxLife = life;
            this.size = size;
            this.color = new Color(color);
        }

        Particle gravity(float g) {
            this.gravity = g;
            return this;
        }
    }

    private final List<Floater> floaters = new ArrayList<Floater>();
    private final List<Projectile> projectiles = new ArrayList<Projectile>();
    private final List<Area> areas = new ArrayList<Area>();
    private final List<Ring> rings = new ArrayList<Ring>();
    /** Léger zoom de caméra sur les grosses capacités (0 = aucun). */
    private float zoomPunch;
    private final List<Particle> particles = new ArrayList<Particle>();
    private final Set<Fighter> shownAbility = new HashSet<Fighter>();

    public GameView(Assets assets, PmdSprites pmd, Sounds sounds, Viewport viewport) {
        this.assets = assets;
        this.pmd = pmd;
        this.sounds = sounds;
        this.viewport = viewport;
    }

    /** Zoom de la caméra (1 = normal, < 1 = rapproché). */
    public float zoom() {
        return 1f - zoomPunch;
    }

    public float shakeX() {
        return shake > 0 ? (fxRng.nextFloat() - 0.5f) * 2 * shake : 0;
    }

    public float shakeY() {
        return shake > 0 ? (fxRng.nextFloat() - 0.5f) * 2 * shake : 0;
    }

    // ================================================================== logique d'animation

    public void update(float dt) {
        clock += dt;
        shake = Math.max(0, shake - dt * 18);
        zoomPunch = Math.max(0, zoomPunch - dt * 0.12f);
        if (state.phase == GameState.Phase.COMBAT && state.battle != null) {
            Battle b = state.battle;
            if (b.result == 0) {
                shownAbility.clear();
                for (Battle.Event ev : b.advance(dt * speed)) onEvent(ev);
            } else {
                endDelay += dt;
                if (endDelay > 0.9) state.finishCombat();
            }
            for (Fighter f : b.fighters) {
                f.lunge = Math.max(0, f.lunge - dt * 5 * speed);
                f.flash = Math.max(0, f.flash - dt * 4 * speed);
                updateAnim(f, dt * speed);
            }
        }
        if (state.phase != lastPhase) {
            if (state.phase == GameState.Phase.RESULT || state.phase == GameState.Phase.GAMEOVER) {
                sounds.play(state.lastWin ? "win" : "lose", 0.7f);
            }
            if (state.phase == GameState.Phase.PREP) animStates.clear();
            lastPhase = state.phase;
        }
        if (state.level != lastLevel) {
            if (state.level > lastLevel) sounds.play("levelup", 0.7f);
            lastLevel = state.level;
        }
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            f.life -= dt;
            f.y -= dt * 30;
            if (f.life <= 0) it.remove();
        }
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.t += dt * speed / (p.big ? 0.32f : 0.22f);
            p.trail -= dt;
            if (p.trail <= 0 && p.type != PType.ELECTRIC) {
                p.trail = 0.016f;
                particles.add(new Particle(p.x(), p.y(), (fxRng.nextFloat() - 0.5f) * 30, (fxRng.nextFloat() - 0.5f) * 30,
                        0.25f, p.big ? 3.5f : 2.2f, p.color).gravity(0));
            }
            if (p.t >= 1) {
                burst(p.x1, p.y1, p.color, p.big ? 10 : 5, p.big ? 130 : 80);
                it.remove();
            }
        }
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            r.t += dt * speed / r.duration;
            if (r.t >= 1) it.remove();
        }
        for (Iterator<Area> it = areas.iterator(); it.hasNext(); ) {
            Area a = it.next();
            a.life -= dt * speed;
            if (a.life <= 0) it.remove();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += p.gravity * dt;
            if (p.life <= 0) it.remove();
        }
    }

    private AnimState anim(Fighter f) {
        AnimState a = animStates.get(f);
        if (a == null) {
            a = new AnimState();
            a.dir = f.player ? 4 : 0;
            a.t = fxRng.nextFloat();
            animStates.put(f, a);
        }
        return a;
    }

    private void play(Fighter f, String name) {
        AnimState a = anim(f);
        a.name = name;
        a.t = 0;
        a.once = true;
    }

    private void updateAnim(Fighter f, float dt) {
        AnimState a = anim(f);
        a.t += dt;
        a.glow = Math.max(0, a.glow - dt * 2.2f);
        if (f.alive) statusFx(f, a, dt);
        // orientation : vers la cible, ou dans le sens du déplacement
        float dx = 0, dy = 0;
        if (f.moving()) {
            dx = cellX(f.cell) - cellX(f.fromCell);
            dy = cellY(f.cell) - cellY(f.fromCell);
        } else if (f.target != null && f.target.alive) {
            dx = fx(f.target) - fx(f);
            dy = fy(f.target) - fy(f);
        }
        if (dx != 0 || dy != 0) a.dir = dirFromVector(dx, dy);
        if (!f.alive) {
            if (!"Hurt".equals(a.name)) play(f, "Hurt");
            return;
        }
        if (a.once) {
            PmdSprites.Anim pa = pmd.anim(f.species().dex, a.name);
            if (pa == null || a.t >= pa.duration()) {
                a.once = false;
                a.name = "Idle";
            } else {
                return;
            }
        }
        String loop = f.moving() ? "Walk" : f.sleep > 0 ? "Sleep" : "Idle";
        if (!loop.equals(a.name)) {
            a.name = loop;
            a.t = 0;
        }
    }

    /** Particules d'ambiance des statuts : flammèches, bulles de poison, étincelles, « z ». */
    private void statusFx(Fighter f, AnimState a, float dt) {
        a.fxTimer -= dt;
        if (a.fxTimer > 0) return;
        a.fxTimer = 0.12f;
        float s = persp(Hex.row(f.cell));
        float x = fx(f) + (fxRng.nextFloat() - 0.5f) * 26 * s, y = fy(f) - (10 + fxRng.nextFloat() * 30) * s;
        if (f.burnTime > 0)
            particles.add(new Particle(x, y, 0, -30, 0.5f, 2.6f, Canvas.rgb(PType.FIRE.rgb)).gravity(-40));
        if (f.poisonTime > 0 && fxRng.nextFloat() < 0.6f)
            particles.add(new Particle(x, y, 0, -12, 0.7f, 2.4f, Canvas.rgb(PType.POISON.rgb)).gravity(-20));
        if (f.paraTime > 0 && fxRng.nextFloat() < 0.5f)
            particles.add(new Particle(x, y, (fxRng.nextFloat() - 0.5f) * 120, (fxRng.nextFloat() - 0.5f) * 120,
                    0.15f, 2f, Canvas.rgb(PType.ELECTRIC.rgb)).gravity(0));
        if (f.sleep > 0 && fxRng.nextFloat() < 0.12f)
            floaters.add(new Floater(fx(f) + 14 * s, fy(f) - 60 * s, "z", Canvas.rgb(0xA8C8FF), false));
        if (f.stun <= 0) a.frozen = false;
    }

    /** Direction PMD (0 bas … 4 haut … 7 bas-gauche) depuis un vecteur écran (y vers le bas). */
    private static int dirFromVector(float dx, float dy) {
        double ang = Math.toDegrees(Math.atan2(dy / SQUASH, dx));
        int k = (int) Math.round(ang / 45.0);
        k = ((k % 8) + 8) % 8; // 0 droite, 1 bas-droite, 2 bas, 3 bas-gauche, 4 gauche, 5 haut-gauche, 6 haut, 7 haut-droite
        int[] toPmd = {2, 1, 0, 7, 6, 5, 4, 3};
        return toPmd[k];
    }

    private void onEvent(Battle.Event ev) {
        switch (ev.kind) {
            case ATTACK: {
                ev.src.lunge = 1;
                ev.tgt.flash = 1;
                if (!ev.special) playAttack(ev.src);
                AnimState ta = anim(ev.tgt);
                if (!ta.once || !"Attack".equals(ta.name)) play(ev.tgt, "Hurt");
                boolean crit = "crit".equals(ev.text);
                Color c = crit ? Canvas.rgb(0xFF5A36) : ev.mult > 1.01 ? ORANGE : ev.mult < 0.99 ? MUTED : WHITE;
                String txt = Math.round(ev.amount) + (ev.mult > 1.01 || crit ? "!" : "");
                float s = persp(Hex.row(ev.tgt.cell));
                floaters.add(new Floater(fx(ev.tgt) + (fxRng.nextFloat() - 0.5f) * 16, fy(ev.tgt) - 86 * s, txt, c, ev.special || crit));
                if (ev.src.range > 1 && !ev.special) shoot(ev.src, ev.tgt, ev.type, false);
                burst(fx(ev.tgt), fy(ev.tgt) - 30 * s, Canvas.rgb(ev.type.rgb), crit ? 9 : 5, crit ? 120 : 80);
                if (crit) {
                    shake = Math.max(shake, 3);
                    sounds.play("crit", 0.6f, 60);
                } else {
                    sounds.play("hit", 0.35f, 70);
                }
                break;
            }
            case ABILITY:
                if (shownAbility.add(ev.src)) {
                    floaters.add(new Floater(fx(ev.src), fy(ev.src) - 100 * persp(Hex.row(ev.src.cell)), ev.text + " !", GOLD, true));
                    sounds.play("ability", 0.6f, 80);
                    shake = Math.max(shake, 4);
                }
                castFx(ev);
                break;
            case AREA: {
                Color c = Canvas.rgb(ev.type.rgb);
                areas.add(new Area(ev.cell, ev.radius, c));
                rings.add(new Ring(cellX(ev.cell), cellY(ev.cell), (ev.radius + 0.6f) * HW * persp(Hex.row(ev.cell)), c, 0.45f));
                for (int cell = 0; cell < Hex.CELLS; cell++) {
                    if (Hex.distance(cell, ev.cell) <= ev.radius) burst(cellX(cell), cellY(cell) - 10, c, 4, 90);
                }
                shake = Math.max(shake, 5);
                break;
            }
            case HEAL:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 86 * persp(Hex.row(ev.tgt.cell)), "+" + Math.round(ev.amount), HEAL, false));
                for (int i = 0; i < 8; i++) {
                    float s = persp(Hex.row(ev.tgt.cell));
                    particles.add(new Particle(fx(ev.tgt) + (fxRng.nextFloat() - 0.5f) * 30 * s, fy(ev.tgt) - fxRng.nextFloat() * 30 * s,
                            0, -20 - fxRng.nextFloat() * 30, 0.6f + 0.3f * fxRng.nextFloat(), 2.5f, HEAL).gravity(-30));
                }
                anim(ev.tgt).glow = Math.max(anim(ev.tgt).glow, 0.5f);
                break;
            case DODGE:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 50, "Esquive", DODGE, false));
                break;
            case STATUS:
                if (ev.text.startsWith("Gel")) anim(ev.tgt).frozen = true;
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 42, ev.text, statusColor(ev.text), false));
                break;
            case FAINT: {
                float s = persp(Hex.row(ev.tgt.cell));
                burst(fx(ev.tgt), fy(ev.tgt) - 20, WHITE, 12, 110);
                for (int i = 0; i < 10; i++)
                    particles.add(new Particle(fx(ev.tgt) + (fxRng.nextFloat() - 0.5f) * 40 * s, fy(ev.tgt) - fxRng.nextFloat() * 20 * s,
                            (fxRng.nextFloat() - 0.5f) * 40, -10 - fxRng.nextFloat() * 25, 0.9f, 5 + fxRng.nextFloat() * 4,
                            SMOKE).gravity(-15));
                rings.add(new Ring(fx(ev.tgt), fy(ev.tgt), 34 * s, WHITE, 0.35f));
                sounds.play("faint", 0.5f, 80);
                break;
            }
            default:
                break;
        }
    }

    private static final Color SMOKE = Canvas.rgba(0xC8C8D0, 150);

    /** Attaque normale : animation de tir pour les Pokémon à distance (si le sprite l'a). */
    private void playAttack(Fighter f) {
        if (f.range > 1 && pmd.anim(f.species().dex, "Shoot") != null) play(f, "Shoot");
        else play(f, "Attack");
    }

    private void shoot(Fighter src, Fighter tgt, PType type, boolean big) {
        float ss = persp(Hex.row(src.cell)), ts = persp(Hex.row(tgt.cell));
        projectiles.add(new Projectile(src, tgt, type, big, fx(src), fy(src) - 30 * ss, fx(tgt), fy(tgt) - 28 * ts));
    }

    /** Mise en scène d'une capacité : animation, halo, anneau, projectile, zoom au premier lancement. */
    private void castFx(Battle.Event ev) {
        Fighter f = ev.src;
        boolean support = ev.tgt == null || ev.tgt.player == f.player;
        if (support && pmd.anim(f.species().dex, "Charge") != null) play(f, "Charge");
        else playAttack(f);
        AnimState a = anim(f);
        a.glow = 1;
        float s = persp(Hex.row(f.cell));
        Color c = Canvas.rgb(ev.type.rgb);
        rings.add(new Ring(fx(f), fy(f), 40 * s, c, 0.4f));
        for (int i = 0; i < 14; i++) {
            double ang = Math.PI * 2 * i / 14;
            particles.add(new Particle(fx(f), fy(f) - 24 * s, (float) Math.cos(ang) * 90, (float) Math.sin(ang) * 90 * SQUASH,
                    0.35f, 2.6f, c).gravity(0));
        }
        if (!support && f.range > 1) shoot(f, ev.tgt, ev.type, true);
        if (shownAbility.contains(f)) return;
        zoomPunch = Math.max(zoomPunch, 0.035f);
    }

    private void burst(float x, float y, Color c, int n, float speedMax) {
        for (int i = 0; i < n && particles.size() < 400; i++) {
            double a = fxRng.nextDouble() * Math.PI * 2;
            float v = speedMax * (0.4f + 0.6f * fxRng.nextFloat());
            particles.add(new Particle(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v - 60,
                    0.35f + 0.3f * fxRng.nextFloat(), 2 + fxRng.nextFloat() * 2.5f, c));
        }
    }

    private static Color statusColor(String text) {
        if (text.startsWith("Brûl")) return Canvas.rgb(PType.FIRE.rgb);
        if (text.startsWith("Empois")) return Canvas.rgb(0xC77DD8);
        if (text.startsWith("Gel")) return Canvas.rgb(PType.ICE.rgb);
        if (text.startsWith("Paral")) return Canvas.rgb(PType.ELECTRIC.rgb);
        if (text.startsWith("Endorm")) return Canvas.rgb(0xB7A6FF);
        if (text.startsWith("Bouclier")) return Canvas.rgb(0xDDE6F5);
        return GOLD;
    }

    // ================================================================== géométrie (perspective)

    /** Facteur d'échelle de la ligne (plus petit au fond). */
    private static float persp(int row) {
        return 0.78f + 0.32f * row / (Hex.ROWS - 1);
    }

    private static float cellX(int cell) {
        int r = Hex.row(cell);
        return BOARD_CX + (Hex.col(cell) + ((r & 1) == 1 ? 0.5f : 0f) - 3.25f) * HW * persp(r);
    }

    private static float cellY(int cell) {
        return ROW_Y[Hex.row(cell)];
    }

    private static float tileW(int row) {
        return HW * persp(row);
    }

    private static float tileH(int row) {
        return HW * 1.1547f * SQUASH * persp(row);
    }

    private static int slotCell(int slot) {
        int i = slot - 100;
        return Hex.fromLocal(true, i / GameState.COLS, i % GameState.COLS);
    }

    private static Rect benchRect(int i) {
        return new Rect(BENCH_X + i * BENCH_STEP, BENCH_Y, BENCH_W, BENCH_H);
    }

    /** Case du plateau sous un point (ellipse de chaque tuile), ou -1. */
    private static int cellAt(float x, float y) {
        int best = -1;
        float bestD = 1f;
        for (int c = 0; c < Hex.CELLS; c++) {
            int r = Hex.row(c);
            float nx = (x - cellX(c)) / (tileW(r) * 0.5f), ny = (y - cellY(c)) / (tileH(r) * 0.5f);
            float d = nx * nx + ny * ny;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static int slotAt(float x, float y) {
        int cell = cellAt(x, y);
        if (cell >= 0 && Hex.row(cell) >= Hex.SIDE_ROWS) {
            return GameState.boardSlot(Hex.row(cell) - Hex.SIDE_ROWS, Hex.col(cell));
        }
        for (int i = 0; i < GameState.BENCH; i++) if (benchRect(i).contains(x, y)) return i;
        return -1;
    }

    private static Rect invRect(int i) {
        return new Rect(INV_X + (i % 5) * (INV_W + INV_GAP), INV_Y + (i / 5) * (INV_H + INV_GAP), INV_W, INV_H);
    }

    private int invAt(float x, float y) {
        for (int i = 0; i < state.items.size(); i++) if (invRect(i).contains(x, y)) return i;
        return -1;
    }

    private static Rect shopRect(int i) {
        return new Rect(SHOP_X + i * SHOP_STEP, SHOP_Y, SHOP_W, SHOP_H);
    }

    private static int shopAt(float x, float y) {
        for (int i = 0; i < GameState.SHOP; i++) if (shopRect(i).contains(x, y)) return i;
        return -1;
    }

    private static float fx(Fighter f) {
        float p = f.moveT > 0 ? (float) (1 - f.moveT / f.moveDur) : 1f;
        return cellX(f.fromCell) + (cellX(f.cell) - cellX(f.fromCell)) * p;
    }

    private static float fy(Fighter f) {
        float p = f.moveT > 0 ? (float) (1 - f.moveT / f.moveDur) : 1f;
        return cellY(f.fromCell) + (cellY(f.cell) - cellY(f.fromCell)) * p;
    }

    private static float fScale(Fighter f) {
        float p = f.moveT > 0 ? (float) (1 - f.moveT / f.moveDur) : 1f;
        float a = Hex.row(f.fromCell) + (Hex.row(f.cell) - Hex.row(f.fromCell)) * p;
        return 0.78f + 0.32f * a / (Hex.ROWS - 1);
    }

    // ================================================================== entrées

    private boolean toGame(int screenX, int screenY) {
        viewport.unproject(tmpVec.set(screenX, screenY));
        mx = tmpVec.x;
        my = H - tmpVec.y;
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return toGame(screenX, screenY);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        toGame(screenX, screenY);
        sounds.userGesture();
        pressed = true;
        pressX = mx;
        pressY = my;
        dragging = false;
        pressSlot = -1;
        pressItem = -1;
        if (state.phase != GameState.Phase.PREP) return true;
        int inv = invAt(mx, my);
        if (inv >= 0) {
            pressItem = inv;
            return true;
        }
        int s = slotAt(mx, my);
        if (s >= 0 && state.get(s) != null) {
            if (button == Input.Buttons.RIGHT) {
                if (state.sell(s)) sounds.play("buy", 0.5f);
                if (selected == s) selected = -1;
                pressed = false;
                return true;
            }
            pressSlot = s;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        toGame(screenX, screenY);
        if ((pressSlot >= 0 || pressItem >= 0) && pressed && Vector2.dst(pressX, pressY, mx, my) > 6) dragging = true;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        toGame(screenX, screenY);
        if (!pressed) return true;
        if (dragging && pressItem >= 0) {
            int target = slotAt(mx, my);
            if (target >= 0 && state.get(target) != null && state.equip(pressItem, target)) sounds.play("merge", 0.5f);
            selected = -1;
        } else if (dragging && pressSlot >= 0) {
            int target = slotAt(mx, my);
            if (target >= 0) {
                if (state.move(pressSlot, target)) sounds.play("click", 0.6f);
            } else if (SHOP_AREA.contains(mx, my) && state.sell(pressSlot)) {
                sounds.play("buy", 0.5f);
            }
            selected = -1;
        } else if (button != Input.Buttons.RIGHT) {
            onClick(mx, my);
        }
        dragging = false;
        pressSlot = -1;
        pressItem = -1;
        pressed = false;
        return true;
    }

    private void onClick(float x, float y) {
        if (BTN_SOUND.contains(x, y)) {
            sounds.toggle();
            return;
        }
        switch (state.phase) {
            case PREP:
                if (BTN_A.contains(x, y)) {
                    if (state.reroll()) sounds.play("click", 0.7f);
                } else if (BTN_B.contains(x, y)) {
                    if (state.buyXp()) sounds.play("click", 0.7f);
                } else if (BTN_LOCK.contains(x, y)) {
                    state.toggleLock();
                    sounds.play("click", 0.7f);
                } else if (BTN_SELL.contains(x, y)) {
                    if (selected >= 0 && state.sell(selected)) sounds.play("buy", 0.5f);
                    selected = -1;
                } else if (BTN_MAIN.contains(x, y)) {
                    selected = -1;
                    if (state.startCombat()) {
                        endDelay = 0;
                        floaters.clear();
                        projectiles.clear();
                        areas.clear();
                        animStates.clear();
                        sounds.play("click", 0.7f);
                    }
                } else {
                    int shop = shopAt(x, y);
                    if (shop >= 0) {
                        if (state.buy(shop)) {
                            boolean merged = state.message.contains("évolue") || state.message.contains("★ !");
                            sounds.play(merged ? "merge" : "buy", 0.6f);
                        }
                        selected = -1;
                        return;
                    }
                    int s = slotAt(x, y);
                    if (s < 0) {
                        selected = -1;
                    } else if (selected >= 0 && selected != s) {
                        state.move(selected, s);
                        selected = -1;
                    } else if (state.get(s) != null) {
                        selected = selected == s ? -1 : s;
                    }
                }
                break;
            case COMBAT:
                if (BTN_A.contains(x, y)) speed = speed == 1 ? 2 : speed == 2 ? 4 : 1;
                else if (BTN_B.contains(x, y) && state.battle != null) {
                    state.battle.runToEnd();
                    endDelay = 1;
                    projectiles.clear();
                    areas.clear();
                }
                break;
            case RESULT:
                if (BTN_MAIN.contains(x, y) || ARENA.contains(x, y)) state.nextRound();
                break;
            case GAMEOVER:
                if (BTN_MAIN.contains(x, y) || ARENA.contains(x, y)) {
                    state.newGame();
                    selected = -1;
                    lastLevel = state.level;
                }
                break;
            default:
                break;
        }
    }

    // ================================================================== rendu

    public void draw(Canvas g) {
        drawTopBar(g);
        drawArena(g);
        drawSynergies(g);
        drawInfo(g);
        drawBench(g);
        drawInventory(g);
        drawLevelPanel(g);
        drawShop(g);
        drawButtons(g);
        drawParticles(g);
        drawFloaters(g);
        drawOverlay(g);
        drawDragged(g);
    }

    private BitmapFont bold(int size) {
        return assets.font(true, size);
    }

    private BitmapFont regular(int size) {
        return assets.font(false, size);
    }

    private void drawTopBar(Canvas g) {
        g.rect(0, 0, W, 44, PANEL);
        g.text(bold(18), "Manche " + state.round, 16, 29, TEXT);
        if (state.isPveRound()) g.text(bold(12), "SAUVAGES", 120, 28, GOLD);

        g.text(bold(13), "PV", 212, 28, MUTED);
        g.round(236, 15, 200, 16, TRACK);
        float frac = state.hp / (float) GameState.START_HP;
        g.round(236, 15, 200 * frac, 16, frac > 0.5f ? GOOD : frac > 0.25f ? GOLD : ACCENT);
        g.centered(bold(12), state.hp + " / " + GameState.START_HP, 336, 28, WHITE);

        int bc = state.boardCount();
        g.text(regular(13), "Équipe " + bc + " / " + state.maxUnits(), 460, 29, bc > state.maxUnits() ? ACCENT : TEXT);
        g.text(regular(13), "En lice " + lobby.aliveCount() + " / " + Lobby.SIZE, 570, 29, TEXT);
        Battle b = state.battle;
        if (state.phase == GameState.Phase.COMBAT && b != null && b.overtimeMult() > 1) {
            g.text(bold(13), "PROLONGATION · dégâts x" + fmt1(b.overtimeMult()), 680, 29, ACCENT);
        } else if (state.isPveRound() && state.phase == GameState.Phase.PREP) {
            g.text(bold(13), "Pokémon sauvages : butin d'objets à gagner", 680, 29, GOLD);
        } else if (Math.abs(state.streak) >= 2) {
            g.text(regular(13), (state.streak > 0 ? "Série de victoires x" : "Série de défaites x") + Math.abs(state.streak),
                    680, 29, state.streak > 0 ? GOOD : ACCENT);
        }
        g.right(bold(13), "POKÉMON AUTO BATTLER", BTN_SOUND.x - 16, 28, MUTED);
        boolean hover = BTN_SOUND.contains(mx, my);
        g.round(BTN_SOUND.x, BTN_SOUND.y, BTN_SOUND.w, BTN_SOUND.h, hover ? PANEL_LIGHT.cpy().lerp(WHITE, 0.1f) : PANEL_LIGHT);
        g.centered(bold(12), sounds.enabled() ? "Son : oui" : "Son : non", BTN_SOUND.cx(), BTN_SOUND.y + 18, sounds.enabled() ? TEXT : MUTED);
    }

    private void drawArena(Canvas g) {
        g.round(ARENA.x, ARENA.y, ARENA.w, ARENA.h, Canvas.rgb(0x1C2230));
        boolean prep = state.phase == GameState.Phase.PREP;
        int hoverCell = prep && (dragging || selected >= 0) ? cellAt(mx, my) : -1;
        for (int r = 0; r < Hex.ROWS; r++) {
            for (int c = 0; c < Hex.COLS; c++) {
                int cell = Hex.index(c, r);
                boolean playerSide = r >= Hex.SIDE_ROWS;
                Color base = playerSide ? ((c + r) % 2 == 0 ? PLAYER_TILE : PLAYER_TILE2) : ((c + r) % 2 == 0 ? ENEMY_TILE : ENEMY_TILE2);
                drawTile(g, cell, base, false);
                if (playerSide && cell == hoverCell) {
                    drawTile(g, cell, g.fade(GOLD, 0.3f), false);
                    drawTile(g, cell, GOLD, true);
                }
            }
        }
        g.text(bold(11), state.isPveRound() ? "POKÉMON SAUVAGES" : state.opponentName == null ? "ADVERSAIRE"
                        : (state.opponentGhost ? "FANTÔME DE " : "ADVERSAIRE : ") + state.opponentName.toUpperCase(), ARENA.x + 10, ARENA.y + 16,
                state.isPveRound() ? g.fade(GOLD, 0.8f) : g.fade(WHITE, 0.35f));
        g.text(bold(11), "TON ÉQUIPE", ARENA.x + 10, ARENA.y + ARENA.h - 8, g.fade(WHITE, 0.35f));

        for (Area a : areas) {
            float alpha = Math.min(1f, a.life / 0.5f) * 0.5f;
            for (int c = 0; c < Hex.CELLS; c++) {
                if (Hex.distance(c, a.cell) <= a.radius) drawTile(g, c, g.fade(a.color, alpha), false);
            }
        }

        if (!prep && state.battle != null) {
            List<Fighter> order = new ArrayList<Fighter>(state.battle.fighters);
            Collections.sort(order, new Comparator<Fighter>() {
                @Override
                public int compare(Fighter a, Fighter b) {
                    if (a.alive != b.alive) return a.alive ? 1 : -1;
                    return Float.compare(fy(a), fy(b));
                }
            });
            for (Ring r : rings) {
                float rr = r.maxR * (0.25f + 0.75f * r.t);
                g.centeredTinted(assets.ring, r.x, r.y, rr, rr * SQUASH, g.fade(r.color, 0.8f * (1 - r.t)));
            }
            for (Fighter f : order) drawFighter(g, f);
            for (Projectile p : projectiles) drawProjectile(g, p);
            return;
        }

        // préparation : adversaire puis plateau du joueur, du fond vers l'avant
        List<Object[]> draws = new ArrayList<Object[]>();
        for (GameState.Placed p : state.enemy) draws.add(new Object[]{p.unit, Hex.fromLocal(false, p.row, p.col), Boolean.FALSE, -1, p.row});
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLS; col++) {
                int s = GameState.boardSlot(row, col);
                Unit u = state.board[row][col];
                if (u == null || (dragging && pressSlot == s)) continue;
                draws.add(new Object[]{u, slotCell(s), Boolean.TRUE, s, row});
            }
        Collections.sort(draws, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Integer.compare(Hex.row((Integer) a[1]), Hex.row((Integer) b[1]));
            }
        });
        for (Object[] d : draws) {
            Unit u = (Unit) d[0];
            int cell = (Integer) d[1];
            boolean mine = (Boolean) d[2];
            int slot = (Integer) d[3];
            int localRow = (Integer) d[4];
            boolean misplaced = mine && !u.species.role().suitsRow(localRow);
            if (misplaced) drawTile(g, cell, g.fade(ORANGE, 0.55f + 0.25f * (float) Math.sin(clock * 4)), true);
            if (mine && selected == slot) drawTile(g, cell, GOLD, true);
            float s = persp(Hex.row(cell));
            drawPokemon(g, u.species, cellX(cell), cellY(cell), s, mine ? 4 : 0, "Idle", clock + cell * 0.37f, true);
            drawStars(g, u.stars, cellX(cell), cellY(cell) - 72 * s);
            roleBadge(g, u.species.role(), cellX(cell) - 24 * s, cellY(cell) - 58 * s, 7.5f * s);
            if (misplaced) g.centered(bold(13), "!", cellX(cell) + 24 * s, cellY(cell) - 52 * s, ORANGE);
            drawHeldItems(g, u, cellX(cell), cellY(cell) + 12 * s, 6.5f * s);
        }
    }

    private void drawProjectile(Canvas g, Projectile p) {
        float x = p.x(), y = p.y(), k = p.big ? 1.6f : 1f;
        if (p.type == PType.ELECTRIC) {
            // éclair : segments brisés du lanceur à la tête du tir
            float px = p.x0, py = p.y0;
            int n = 6;
            for (int i = 1; i <= n; i++) {
                float u = p.t * i / n;
                float nx = p.x0 + (p.x1 - p.x0) * u + (i < n ? (fxRng.nextFloat() - 0.5f) * 14 : 0);
                float ny = p.y0 + (p.y1 - p.y0) * u + (i < n ? (fxRng.nextFloat() - 0.5f) * 14 : 0);
                g.line(px, py, nx, ny, 4 * k, g.fade(p.color, 0.6f));
                g.line(px, py, nx, ny, 1.6f * k, WHITE);
                px = nx;
                py = ny;
            }
        }
        g.centeredTinted(assets.glow, x, y, 16 * k, 16 * k, g.fade(p.color, 0.9f));
        if (p.type == PType.ICE || p.type == PType.STEEL) {
            g.line(x - 6 * k, y, x + 6 * k, y, 3 * k, WHITE);
            g.line(x, y - 6 * k, x, y + 6 * k, 3 * k, WHITE);
        } else if (p.type == PType.GRASS || p.type == PType.BUG) {
            g.ellipse(x, y, 6 * k, 3 * k, Canvas.rgb(0xB8F070));
        } else if (p.type == PType.ROCK || p.type == PType.GROUND) {
            g.ellipse(x, y, 5 * k, 4.5f * k, Canvas.rgb(0x8A7550));
        } else {
            g.ellipse(x, y, 3.5f * k, 3.5f * k, WHITE);
        }
    }

    private void drawTile(Canvas g, int cell, Color c, boolean outline) {
        int r = Hex.row(cell);
        float w = tileW(r) * 0.95f / 0.866f, h = tileH(r) * 0.95f;
        float x = cellX(cell), y = cellY(cell);
        if (outline) g.imageTinted(assets.hexOutline, x - w / 2, y - h / 2, w, h, c, false);
        else g.imageTinted(assets.hex, x - w / 2, y - h / 2, w, h, c, false);
    }

    private void drawFighter(Canvas g, Fighter f) {
        float x = fx(f), y = fy(f), s = fScale(f);
        AnimState a = anim(f);
        // K.O. : le Pokémon s'affaisse et s'efface
        g.alpha = f.alive ? 1f : Math.max(0f, 1f - a.t / 0.8f);
        if (g.alpha <= 0) {
            g.alpha = 1f;
            return;
        }
        if (!f.alive) y += Math.min(0.8f, a.t) * 14 * s;
        if (f.tauntTime > 0) g.ellipse(x, y, 30 * s, 12 * s, g.fade(ACCENT, 0.35f));
        Color typeColor = Canvas.rgb(f.species().moveType().rgb);
        if (f.alive && f.maxMana > 0 && f.mana >= 0.85 * f.maxMana) {
            // capacité presque prête : halo qui pulse au sol
            float pulse = 0.35f + 0.2f * (float) Math.sin(clock * 10);
            g.centeredTinted(assets.glow, x, y, 34 * s, 14 * s, g.fade(typeColor, pulse));
        }
        if (a.glow > 0) g.centeredTinted(assets.glow, x, y - 26 * s, 46 * s, 46 * s, g.fade(typeColor, 0.7f * a.glow));
        drawPokemon(g, f.species(), x, y, s, a.dir, a.name, a.t, !a.once);
        if (f.alive && f.shield > 0) {
            float b = 0.55f + 0.1f * (float) Math.sin(clock * 6);
            g.centeredTinted(assets.glow, x, y - 26 * s, 34 * s, 36 * s, g.fade(Canvas.rgb(0x7FB8FF), 0.25f));
            g.centeredTinted(assets.ring, x, y - 26 * s, 32 * s, 34 * s, g.fade(Canvas.rgb(0xA8D4FF), b));
        }
        if (f.alive && f.stun > 0) {
            if (a.frozen) {
                g.ellipse(x, y - 28 * s, 30 * s, 32 * s, g.fade(Canvas.rgb(PType.ICE.rgb), 0.4f));
            } else {
                // étourdi : étoiles qui tournent au-dessus de la tête
                for (int i = 0; i < 3; i++) {
                    double ang = clock * 5 + i * Math.PI * 2 / 3;
                    g.text(bold(12), "★", x + (float) Math.cos(ang) * 14 * s - 5, y - 64 * s + (float) Math.sin(ang) * 5 * s, GOLD);
                }
            }
        }
        if (f.flash > 0) g.ellipse(x, y - 28 * s, 26 * s, 26 * s, g.fade(Canvas.rgb(0xFF5050), (float) (0.35 * f.flash)));
        g.alpha = 1f;
        if (!f.alive) return;

        float bw = 50 * s, bx = x - bw / 2, by = y - 78 * s;
        g.rect(bx - 1, by - 1, bw + 2, 10, TRACK);
        double total = Math.max(f.maxHp, f.hp + f.shield);
        float hpW = (float) (bw * f.hp / total), shW = (float) (bw * f.shield / total);
        g.rect(bx, by, hpW, 5, f.player ? GOOD : ACCENT);
        g.rect(bx + hpW, by, shW, 5, Canvas.rgb(0xDDE6F5));
        g.rect(bx, by + 6, (float) (bw * Math.min(1, f.mana / f.maxMana)), 2, BLUE);
        roleBadge(g, f.species().role(), bx - 8, by + 3, 6f);
        if (f.unit.stars > 1) drawStars(g, f.unit.stars, x, by - 3);
        drawHeldItems(g, f.unit, x, y + 12 * s, 5.5f * s);

        float sx = x + bw / 2 + 5, sy = by + 3;
        if (f.burnTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.FIRE.rgb));
        if (f.poisonTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.POISON.rgb));
        if (f.paraTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.ELECTRIC.rgb));
        if (f.hasteTime > 0) statusDot(g, sx, sy, Canvas.rgb(0x5DFFB0));
        if (f.sleep > 0) g.text(bold(13), "z", x + 16 * s, y - 54 * s - (clock % 1) * 6, Canvas.rgb(0xB7A6FF));
    }

    private float statusDot(Canvas g, float x, float y, Color c) {
        g.ellipse(x, y, 3, 3, c);
        return y + 7;
    }

    /**
     * Dessine un Pokémon animé posé au point (gx, gy) : ombre, puis sprite PMD (ou sprite PokéAPI en secours).
     * {@code persp} = facteur de perspective, {@code dir} = direction PMD.
     */
    private void drawPokemon(Canvas g, Species sp, float gx, float gy, float persp, int dir, String animName, float t, boolean loop) {
        // ~60 px de haut à l'avant du plateau, entre x1,8 et x3 (pixel art)
        float scale = persp * Math.max(1.8f, Math.min(3f, 60f / Math.max(16f, pmd.idleHeight(sp.dex))));
        g.ellipse(gx, gy, 20 * persp, 7 * persp, g.fade(BLACK, 0.35f));
        PmdSprites.Anim a = pmd.anim(sp.dex, animName);
        if (a == null) {
            TextureRegion img = assets.sprite(sp.dex, dir >= 3 && dir <= 5);
            if (img == null) return;
            float h = 52 * persp, w = h * img.getRegionWidth() / img.getRegionHeight();
            g.image(img, gx - w / 2, gy - h + 4, w, h);
            return;
        }
        PmdSprites.Frame fr = pmd.frame(a, dir, t, loop);
        if (fr == null) return;
        float w = fr.region.getRegionWidth() * scale, h = fr.region.getRegionHeight() * scale;
        float y = gy + fr.oy * scale;
        if (PmdSprites.mirrored(dir)) {
            float x = gx - (fr.ox * scale) - w;
            g.imageFlipped(fr.region, x, y, w, h);
        } else {
            g.image(fr.region, gx + fr.ox * scale, y, w, h);
        }
    }

    private void drawPortrait(Canvas g, Species sp, float x, float y, float size) {
        TextureRegion p = pmd.portrait(sp.dex);
        if (p != null) {
            g.rect(x - 1, y - 1, size + 2, size + 2, BLACK);
            g.image(p, x, y, size, size);
        } else {
            drawPokemon(g, sp, x + size / 2, y + size * 0.85f, size / 60f, 0, "Idle", clock, true);
        }
    }

    private void drawStars(Canvas g, int stars, float cx, float y) {
        if (stars <= 1) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars; i++) sb.append('★');
        g.centered(bold(12), sb.toString(), cx, y, GOLD);
    }

    /** Objets tenus, sous le Pokémon : petites icônes sur pastille de la couleur de l'objet. */
    private void drawHeldItems(Canvas g, Unit u, float cx, float y, float r) {
        int n = u.items.size();
        for (int i = 0; i < n; i++) {
            Item it = u.items.get(i);
            float x = cx + (i - (n - 1) / 2f) * (r * 2 + 3);
            g.ellipse(x, y, r + 1.2f, r + 1.2f, it.isComponent() ? BLACK : GOLD);
            g.ellipse(x, y, r, r, g.fade(Canvas.rgb(it.rgb), 0.85f));
            TextureRegion icon = assets.itemIcon(it);
            if (icon != null) g.image(icon, x - r * 1.25f, y - r * 1.25f, r * 2.5f, r * 2.5f);
        }
    }

    /**
     * Icône d'objet : sprite de l'objet sur un disque sombre cerclé de sa couleur ; les objets combinés
     * ont en plus un anneau doré. Sans sprite, pastille colorée avec l'abréviation.
     */
    private void drawItemIcon(Canvas g, Item it, float cx, float cy, float r) {
        if (!it.isComponent()) g.ellipse(cx, cy, r + 2.5f, r + 2.5f, GOLD);
        g.ellipse(cx, cy, r, r, Canvas.rgb(it.rgb));
        TextureRegion icon = assets.itemIcon(it);
        if (icon == null) {
            g.ellipse(cx, cy, r * 0.72f, r * 0.72f, g.fade(BLACK, 0.25f));
            g.centered(bold(10), it.abbr, cx, cy + 4, WHITE);
            return;
        }
        g.ellipse(cx, cy, r * 0.84f, r * 0.84f, Canvas.rgb(0x1E2230));
        float s = r * 2.1f;
        g.image(icon, cx - s / 2, cy - s / 2, s, s);
    }

    private void drawSynergies(Canvas g) {
        g.round(LEFT.x, LEFT.y, LEFT.w, LEFT.h, PANEL);
        g.text(bold(15), "Synergies", LEFT.x + 12, LEFT.y + 22, TEXT);
        List<Unit> units = state.boardUnits();
        final Map<PType, Integer> counts = Synergies.counts(units);
        final Map<PType, Integer> levels = Synergies.levels(units);
        final Map<Trait, Integer> tCounts = Synergies.traitCounts(units);
        final Map<Trait, Integer> tLevels = Synergies.traitLevels(units);
        if (counts.isEmpty() && tCounts.isEmpty()) {
            g.wrap(regular(12), "Place des Pokémon de même classe ou de même type sur le plateau pour activer des bonus.",
                    LEFT.x + 12, LEFT.y + 46, LEFT.w - 24, 16, MUTED);
            return;
        }
        float bottom = LEFT.y + LEFT.h - 8;
        float y = LEFT.y + 42;

        // Classes : actives d'abord, puis par nombre de lignées
        List<Trait> traits = new ArrayList<Trait>(tCounts.keySet());
        Collections.sort(traits, new Comparator<Trait>() {
            @Override
            public int compare(Trait a, Trait b) {
                int la = tLevels.containsKey(a) ? 1 : 0, lb = tLevels.containsKey(b) ? 1 : 0;
                if (la != lb) return lb - la;
                if (!tCounts.get(a).equals(tCounts.get(b))) return tCounts.get(b) - tCounts.get(a);
                return a.ordinal() - b.ordinal();
            }
        });
        if (!traits.isEmpty()) {
            g.text(bold(10), "CLASSES", LEFT.x + 12, y, MUTED);
            y += 6;
            for (Trait t : traits) {
                if (y > bottom - 20) break;
                Integer lvl = tLevels.get(t);
                boolean active = lvl != null;
                float a = active ? 1f : 0.45f;
                float w = traitPill(g, t, LEFT.x + 10, y, a);
                g.text(bold(12), tCounts.get(t) + " (" + thresholdsText(t.thresholds()) + ")", LEFT.x + 16 + w, y + 13, active ? TEXT : MUTED);
                if (active) y = g.wrap(regular(12), t.describe(lvl), LEFT.x + 12, y + 31, LEFT.w - 22, 14, SOFT) - 6;
                else y += 24;
            }
            y += 8;
        }

        List<PType> order = new ArrayList<PType>(counts.keySet());
        Collections.sort(order, new Comparator<PType>() {
            @Override
            public int compare(PType a, PType b) {
                int la = levels.containsKey(a) ? 1 : 0, lb = levels.containsKey(b) ? 1 : 0;
                if (la != lb) return lb - la;
                return counts.get(b) - counts.get(a);
            }
        });
        if (order.isEmpty() || y > bottom - 30) return;
        g.text(bold(10), "TYPES", LEFT.x + 12, y, MUTED);
        y += 6;
        int hidden = 0;
        for (PType t : order) {
            Integer lvl = levels.get(t);
            boolean active = lvl != null;
            if (y > bottom - (active ? 34 : 20)) {
                hidden++;
                continue;
            }
            typeBadge(g, t, LEFT.x + 10, y, active ? 1f : 0.45f);
            g.text(bold(12), counts.get(t) + " (" + thresholdsText(Synergies.thresholds(t)) + ")", LEFT.x + 84, y + 13, active ? TEXT : MUTED);
            if (active) y = g.wrap(regular(12), Synergies.describe(t, lvl), LEFT.x + 12, y + 31, LEFT.w - 22, 14, SOFT) - 6;
            else y += 24;
        }
        if (hidden > 0) g.text(regular(12), "+ " + hidden + " autre" + (hidden > 1 ? "s" : ""), LEFT.x + 12, bottom, MUTED);
    }

    private static String thresholdsText(int[] th) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < th.length; i++) sb.append(i == 0 ? "" : " · ").append(th[i]);
        return sb.toString();
    }

    private void drawInfo(Canvas g) {
        g.round(RIGHT.x, RIGHT.y, RIGHT.w, RIGHT.h, PANEL);
        float x = RIGHT.x + 14, y = RIGHT.y + 14;

        Unit unit = null;
        Species sp = null;
        boolean owned = false;
        int infoSlot = -1;
        int hoverSlot = slotAt(mx, my);
        int hoverShop = shopAt(mx, my);
        boolean prep = state.phase == GameState.Phase.PREP;
        if (prep && hoverShop >= 0 && state.shop[hoverShop] != null) {
            sp = state.shop[hoverShop];
        } else if (prep && hoverSlot >= 0 && state.get(hoverSlot) != null) {
            unit = state.get(hoverSlot);
            owned = true;
            infoSlot = hoverSlot;
        } else if (selected >= 0 && state.get(selected) != null) {
            unit = state.get(selected);
            owned = true;
            infoSlot = selected;
        } else if (prep) {
            int cell = cellAt(mx, my);
            for (GameState.Placed p : state.enemy)
                if (Hex.fromLocal(false, p.row, p.col) == cell) unit = p.unit;
        } else if (state.battle != null) {
            int cell = cellAt(mx, my);
            for (Fighter f : state.battle.fighters) if (f.alive && f.cell == cell) unit = f.unit;
        }
        if (unit != null) sp = unit.species;

        int hoverItem = prep ? invAt(mx, my) : -1;
        if (dragging && pressItem >= 0) hoverItem = pressItem;
        if (hoverItem >= 0 && hoverItem < state.items.size()) {
            drawItemInfo(g, state.items.get(hoverItem), x, y);
        } else if (sp == null && !(state.round == 1 && prep)) {
            drawStandings(g, x, y);
        } else if (sp == null) {
            g.text(bold(15), "Comment jouer", x, y + 14, TEXT);
            String[] lines = {
                    "• Achète des Pokémon dans la boutique.",
                    "• Glisse-les sur ton camp (en bas du plateau).",
                    "• Chacun fonce sur l'ennemi le plus proche :",
                    "  corps à corps devant, distance derrière.",
                    "• Mana plein : capacité unique.",
                    "• 3 exemplaires identiques fusionnent.",
                    "• Glisse les objets sur tes Pokémon :",
                    "  2 composants = 1 objet combiné.",
                    "• Le niveau fixe la taille de l'équipe.",
                    "• Clic droit ou glisser sur la boutique : vendre.",
                    "• 10 or en réserve = +1 or par manche.",
                    "• Après 30 s de combat, les dégâts montent."
            };
            float ly = y + 40;
            for (String l : lines) {
                g.text(regular(13), l, x, ly, SOFT);
                ly += 19;
            }
        } else {
            int stars = unit == null ? 1 : unit.stars;
            drawPortrait(g, sp, x, y, 64);
            float hx = x + 76;
            g.text(bold(22), sp.name, hx, y + 20, TEXT);
            if (stars > 1) g.text(bold(15), stars + "★", hx + g.width(bold(22), sp.name) + 8, y + 19, GOLD);
            g.right(regular(12), "Tier " + sp.tier, RIGHT.x + RIGHT.w - 14, y + 18, MUTED);
            float bx = hx;
            for (PType t : sp.types) {
                typeBadge(g, t, bx, y + 26, 1f);
                bx += 72;
            }
            bx = hx;
            for (Trait t : sp.traits()) bx += traitPill(g, t, bx, y + 48, 1f) + 6;
            int range = sp.range();
            g.text(regular(12), range <= 1 ? "mêlée" : "portée " + range, bx + 2, y + 61, MUTED);

            Unit probe = unit != null ? unit : new Unit(sp, 1, sp.tier);
            Fighter f = new Fighter(probe, true, 0, 0, null);
            float sy = y + 80;
            statLine(g, "PV", (int) f.maxHp, 900, x, sy);
            statLine(g, "Attaque", (int) f.atk, 300, x, sy + 20);
            statLine(g, "Défense", (int) f.def, 150, x, sy + 40);
            g.text(regular(13), "Attaques / s", x, sy + 72, MUTED);
            g.text(bold(13), fmt2(1 / f.interval), x + 96, sy + 72, TEXT);

            float ty = sy + 102;
            Ability a = sp.ability();
            g.text(bold(13), a.label, x, ty, GOLD);
            g.right(regular(12), (int) f.maxMana + " mana (départ " + a.startMana + ")", RIGHT.x + RIGHT.w - 14, ty, MUTED);
            g.wrap(regular(13), a.description, x, ty + 19, RIGHT.w - 28, 17, SOFT);
            Unit m = probe.merged();
            String evo = m == null ? "Niveau maximal atteint."
                    : m.species != probe.species ? "3 exemplaires → " + m.species.name
                    : "3 exemplaires → " + m.stars + "★";
            g.text(regular(13), evo, x, ty + 60, SOFT);
            int hoverRow = owned && GameState.isBoard(infoSlot) ? (infoSlot - 100) / GameState.COLS : -1;
            if (hoverRow >= 0 && !sp.role().suitsRow(hoverRow))
                g.text(bold(12), "! " + sp.role().label + " : " + (sp.role().frontline ? "à mettre devant" : "à mettre derrière"),
                        x, ty + 78, ORANGE);

            if (owned) g.right(regular(13), "Revente : " + unit.invested + " or", RIGHT.x + RIGHT.w - 14, ty + 60, GOLD);
            if (unit != null && !unit.items.isEmpty()) {
                float iy = ty + 98;
                for (Item it : unit.items) {
                    drawItemIcon(g, it, x + 9, iy - 4, 9);
                    g.text(regular(12), it.name + " — " + shorten(it.description, 34), x + 24, iy, SOFT);
                    iy += 20;
                }
            }
        }

        if (state.message != null && !state.message.isEmpty()) {
            g.round(RIGHT.x + 8, RIGHT.y + RIGHT.h - 40, RIGHT.w - 16, 32, PANEL_LIGHT);
            g.wrap(bold(12), state.message, RIGHT.x + 16, RIGHT.y + RIGHT.h - 25, RIGHT.w - 32, 14, GOLD);
        }
    }

    private void drawItemInfo(Canvas g, Item it, float x, float y) {
        drawItemIcon(g, it, x + 16, y + 14, 16);
        g.text(bold(22), it.name, x + 42, y + 22, TEXT);
        g.text(regular(12), it.isComponent() ? "Composant" : "Objet combiné", x + 42, y + 40, MUTED);
        g.text(bold(13), it.statsText(), x, y + 64, BLUE);
        if (it.isComponent()) {
            g.text(regular(13), "Deux composants sur le même Pokémon", x, y + 88, SOFT);
            g.text(regular(13), "se combinent :", x, y + 106, SOFT);
            float ly = y + 132;
            for (Item other : Item.COMPONENTS) {
                Item c = Item.combine(it, other);
                drawItemIcon(g, other, x + 9, ly - 4, 8);
                g.text(regular(12), "→", x + 22, ly, MUTED);
                drawItemIcon(g, c, x + 44, ly - 4, 8);
                g.text(bold(12), c.name, x + 58, ly, TEXT);
                g.text(regular(12), shorten(c.description, 30), x + 58, ly + 13, SOFT);
                ly += 32;
            }
        } else {
            g.wrap(regular(13), it.description, x, y + 90, RIGHT.w - 28, 18, SOFT);
            drawItemIcon(g, it.a, x + 9, y + 146, 9);
            g.text(regular(13), it.a.name + "  +", x + 24, y + 150, MUTED);
            float bx = x + 24 + g.width(regular(13), it.a.name + "  +") + 16;
            drawItemIcon(g, it.b, bx, y + 146, 9);
            g.text(regular(13), it.b.name, bx + 15, y + 150, MUTED);
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String fmt2(double v) {
        long c = Math.round(v * 100);
        long frac = c % 100;
        return (c / 100) + "." + (frac < 10 ? "0" : "") + frac;
    }

    private static String fmt1(double v) {
        long c = Math.round(v * 10);
        return (c / 10) + "." + (c % 10);
    }

    private void statLine(Canvas g, String label, int value, int max, float x, float y) {
        g.text(regular(13), label, x, y + 12, MUTED);
        g.round(x + 96, y + 3, 170, 11, TRACK);
        g.round(x + 96, y + 3, 170 * Math.min(1f, value / (float) max), 11, BLUE);
        g.text(bold(13), String.valueOf(value), x + 274, y + 13, TEXT);
    }

    private void drawBench(Canvas g) {
        g.text(bold(12), "BANC", BENCH_X, BENCH_Y - 6, MUTED);
        for (int i = 0; i < GameState.BENCH; i++) {
            Rect r = benchRect(i);
            g.round(r.x, r.y, r.w, r.h, PANEL);
            if (dragging && slotAt(mx, my) == i) highlight(g, r, GOLD);
            Unit u = state.bench[i];
            if (u == null || (dragging && pressSlot == i)) continue;
            if (selected == i) highlight(g, r, GOLD);
            drawPokemon(g, u.species, r.cx(), r.y + r.h - 16, 0.85f, 0, "Idle", clock + i * 0.3f, true);
            drawStars(g, u.stars, r.cx(), r.y + 13);
            roleBadge(g, u.species.role(), r.x + 12, r.y + 12, 7f);
            drawHeldItems(g, u, r.cx(), r.y + r.h - 9, 6f);
        }
    }

    private void drawInventory(Canvas g) {
        g.text(bold(12), "OBJETS " + state.items.size() + "/" + GameState.MAX_INVENTORY, INV_X, INV_Y - 6, MUTED);
        for (int i = 0; i < GameState.MAX_INVENTORY; i++) {
            Rect r = invRect(i);
            g.round(r.x, r.y, r.w, r.h, PANEL);
            if (i >= state.items.size() || (dragging && pressItem == i)) continue;
            if (r.contains(mx, my) && state.phase == GameState.Phase.PREP) g.outline(r.x, r.y, r.w, r.h, GOLD);
            drawItemIcon(g, state.items.get(i), r.cx(), r.cy(), 11);
        }
    }

    private void drawLevelPanel(Canvas g) {
        Rect p = LEVEL_PANEL;
        g.round(p.x, p.y, p.w, p.h, PANEL);
        g.text(bold(22), "Niveau " + state.level, p.x + 12, p.y + 30, TEXT);
        int need = state.xpToNext();
        g.round(p.x + 12, p.y + 42, p.w - 24, 14, TRACK);
        if (need > 0) {
            g.round(p.x + 12, p.y + 42, (p.w - 24) * state.xp / (float) need, 14, PURPLE);
            g.centered(bold(11), "XP " + state.xp + " / " + need, p.cx(), p.y + 53, WHITE);
        } else {
            g.round(p.x + 12, p.y + 42, p.w - 24, 14, PURPLE);
            g.centered(bold(11), "NIVEAU MAX", p.cx(), p.y + 53, WHITE);
        }
        int[] odds = GameState.shopOdds(state.level);
        g.text(regular(12), "Tiers : ", p.x + 12, p.y + 76, MUTED);
        float ox = p.x + 54;
        Color[] tc = {TIER1, TIER2, TIER3};
        for (int i = 0; i < 3; i++) {
            String s = odds[i] + "%";
            g.text(bold(12), s, ox, p.y + 76, tc[i].cpy().lerp(WHITE, 0.35f));
            ox += g.width(bold(12), s) + 10;
        }
        g.text(bold(22), "● " + state.gold, p.x + 12, p.y + 112, GOLD);
        int interest = Math.min(5, state.gold / 10);
        g.text(regular(12), "intérêts +" + interest, p.x + 12, p.y + 130, MUTED);
    }

    private void drawShop(Canvas g) {
        g.text(bold(12), dragging && pressSlot >= 0 ? "DÉPOSE ICI POUR VENDRE" : "BOUTIQUE" + (state.shopLocked ? "  (verrouillée)" : ""),
                SHOP_X, SHOP_Y - 6, MUTED);
        if (dragging && pressSlot >= 0) highlight(g, SHOP_AREA, ACCENT);
        boolean prep = state.phase == GameState.Phase.PREP;
        for (int i = 0; i < GameState.SHOP; i++) {
            Rect r = shopRect(i);
            Species sp = state.shop[i];
            g.round(r.x, r.y, r.w, r.h, PANEL);
            if (sp == null) continue;
            if (!prep) g.alpha = 0.4f;
            Color tierColor = sp.tier == 1 ? TIER1 : sp.tier == 2 ? TIER2 : TIER3;
            boolean hover = r.contains(mx, my) && prep;
            g.round(r.x, r.y, r.w, 60, g.fade(tierColor, 0.25f));
            g.outline(r.x, r.y, r.w, r.h, hover ? GOLD : tierColor);
            drawPortrait(g, sp, r.x + 10, r.y + 10, 48);
            drawPokemon(g, sp, r.x + r.w - 36, r.y + 60, 0.7f, 1, "Idle", clock + i * 0.5f, true);
            g.text(bold(13), sp.name, r.x + 10, r.y + 80, TEXT);
            float tx = r.x + 10;
            for (PType t : sp.types) {
                smallBadge(g, t, tx, r.y + 90);
                tx += 54;
            }
            Trait role = sp.role();
            roleBadge(g, role, r.x + 17, r.y + 126, 7f);
            g.text(bold(12), role.label, r.x + 29, r.y + 130, Canvas.rgb(role.rgb).lerp(WHITE, 0.3f));
            if (Pokedex.isStarter(sp.line)) roleBadge(g, Trait.STARTER, r.x + 37 + g.width(bold(12), role.label), r.y + 126, 6f);
            g.right(bold(15), "● " + sp.tier, r.x + r.w - 10, r.y + 131, state.gold >= sp.tier ? GOLD : ACCENT);
            g.alpha = 1f;
        }
    }

    private void drawButtons(Canvas g) {
        switch (state.phase) {
            case PREP:
                button(g, BTN_A, "Relancer  ● 1", state.gold >= 1, PANEL_LIGHT);
                button(g, BTN_B, "Acheter XP  ● " + GameState.XP_COST,
                        state.gold >= GameState.XP_COST && state.level < GameState.MAX_LEVEL, Canvas.rgb(0x4A3F8C));
                button(g, BTN_LOCK, state.shopLocked ? "Déverrouiller" : "Verrouiller", true, state.shopLocked ? Canvas.rgb(0x5A4A20) : PANEL_LIGHT);
                button(g, BTN_SELL, "Vendre", selected >= 0, PANEL_LIGHT);
                button(g, BTN_MAIN, "COMBAT !", state.boardCount() > 0, ACCENT);
                break;
            case COMBAT:
                button(g, BTN_A, "Vitesse x" + speed, true, PANEL_LIGHT);
                button(g, BTN_B, "Passer", true, PANEL_LIGHT);
                button(g, BTN_LOCK, "En combat", false, PANEL_LIGHT);
                button(g, BTN_SELL, state.isPveRound() ? "Butin à la clé" : "", false, PANEL_LIGHT);
                button(g, BTN_MAIN, "…", false, ACCENT);
                break;
            case RESULT:
                button(g, BTN_MAIN, "Continuer", true, GOOD);
                break;
            case GAMEOVER:
                button(g, BTN_MAIN, "Rejouer", true, GOOD);
                break;
            default:
                break;
        }
    }

    private void drawParticles(Canvas g) {
        for (Particle p : particles) {
            float a = p.life / p.maxLife;
            g.ellipse(p.x, p.y, p.size, p.size, g.fade(p.color, a));
        }
    }

    /** Classement du lobby : survivants par PV, puis éliminés par place. */
    private void drawStandings(Canvas g, float x, float y) {
        g.text(bold(15), "Classement", x, y + 14, TEXT);
        g.right(regular(12), GameState.isPveRound(state.round) ? "Manche contre des sauvages" : "Manche " + state.round,
                RIGHT.x + RIGHT.w - 14, y + 13, MUTED);
        List<GameState> order = new ArrayList<GameState>(lobby.players());
        Collections.sort(order, new Comparator<GameState>() {
            @Override
            public int compare(GameState a, GameState b) {
                if ((a.placement == 0) != (b.placement == 0)) return a.placement == 0 ? -1 : 1;
                if (a.placement != 0) return a.placement - b.placement;
                if (a.hp != b.hp) return b.hp - a.hp;
                return b.level - a.level;
            }
        });
        int opp = lobby.opponentOf(state);
        GameState foe = opp >= 0 ? lobby.players().get(opp) : null;
        float ry = y + 28, rw = RIGHT.w - 28, rh = 38;
        int rank = 1;
        for (GameState p : order) {
            boolean me = p == state, out = p.placement != 0;
            boolean vs = p == foe && state.placement == 0;
            Color bg = me ? g.fade(GOLD, 0.14f) : vs ? g.fade(ACCENT, 0.16f) : g.fade(WHITE, 0.04f);
            g.round(x, ry, rw, rh - 4, bg);
            if (vs) g.outline(x, ry, rw, rh - 4, g.fade(ACCENT, 0.8f));
            float alpha = out ? 0.4f : 1f;
            g.centered(bold(13), out ? Lobby.ordinal(p.placement) : String.valueOf(rank), x + 14, ry + 22, g.fade(MUTED, alpha + 0.2f));
            Species avatar = avatar(p);
            if (avatar != null) {
                g.alpha = alpha;
                drawPortrait(g, avatar, x + 30, ry + 3, 28);
                g.alpha = 1f;
            } else {
                g.round(x + 30, ry + 3, 28, 28, g.fade(WHITE, 0.06f));
            }
            g.text(bold(13), p.name, x + 66, ry + 14, g.fade(me ? GOLD : TEXT, alpha));
            String sub = out ? "éliminé" : "niv. " + p.level + (Math.abs(p.streak) >= 2 ? " · série " + (p.streak > 0 ? "+" : "-") + Math.abs(p.streak) : "");
            if (vs) sub = (state.opponentGhost ? "fantôme · " : "ton adversaire · ") + sub;
            g.text(regular(12), sub, x + 66, ry + 28, g.fade(vs ? ACCENT : MUTED, alpha + 0.2f));
            float bx = x + rw - 98, bw = 88;
            g.round(bx, ry + 11, bw, 12, TRACK);
            float frac = Math.max(0, p.hp) / (float) GameState.START_HP;
            if (!out) g.round(bx, ry + 11, bw * frac, 12, frac > 0.5f ? GOOD : frac > 0.25f ? GOLD : ACCENT);
            g.centered(bold(11), out ? "—" : String.valueOf(p.hp), bx + bw / 2, ry + 21, WHITE);
            ry += rh;
            if (!out) rank++;
        }
    }

    /** Portrait affiché pour un joueur : son Pokémon le plus avancé sur le plateau. */
    private static Species avatar(GameState p) {
        Unit best = null;
        for (Unit u : p.boardUnits())
            if (best == null || u.stars * 10 + u.species.tier * 3 + (u.species.evolvesTo == null ? 2 : 0)
                    > best.stars * 10 + best.species.tier * 3 + (best.species.evolvesTo == null ? 2 : 0)) best = u;
        return best == null ? null : best.species;
    }

    private void drawFloaters(Canvas g) {
        for (Floater f : floaters) {
            float a = Math.max(0, Math.min(1, f.life / 0.4f));
            BitmapFont font = bold(f.big ? 15 : 13);
            g.centered(font, f.text, f.x + 1, f.y + 1, g.fade(BLACK, 0.6f * a));
            g.centered(font, f.text, f.x, f.y, g.fade(f.color, a));
        }
    }

    private void drawOverlay(Canvas g) {
        if (state.phase != GameState.Phase.RESULT && state.phase != GameState.Phase.GAMEOVER) return;
        Rect a = ARENA;
        float bw = 320, bh = 140;
        float x = a.x + (a.w - bw) / 2, y = a.y + (a.h - bh) / 2;
        g.round(a.x, a.y, a.w, a.h, g.fade(BLACK, 0.45f));
        g.round(x, y, bw, bh, PANEL);
        boolean over = state.phase == GameState.Phase.GAMEOVER;
        boolean champion = over && state.placement == 1;
        String vs = state.opponentName == null ? "" : (state.opponentGhost ? " contre le fantôme de " : " contre ") + state.opponentName;
        g.centered(bold(22), champion ? "Champion !" : over ? "Éliminé" : state.lastWin ? "Victoire !" : "Défaite", x + bw / 2, y + 42,
                champion ? GOLD : over ? ACCENT : state.lastWin ? GOOD : ACCENT);
        g.centered(regular(13), over ? Lobby.ordinal(state.placement) + " sur " + Lobby.SIZE + " · manche " + state.round + " · " + state.wins + " victoires"
                : state.lastWin ? "Tu gagnes la manche" + vs + "." : "Tu perds " + state.lastDamage + " PV" + vs + ".", x + bw / 2, y + 66, TEXT);
        if (!over && !state.lastLoot.isEmpty()) {
            int n = state.lastLoot.size();
            for (int i = 0; i < n; i++) drawItemIcon(g, state.lastLoot.get(i), x + bw / 2 + (i - (n - 1) / 2f) * 30, y + 88, 10);
        }
        g.centered(regular(13), "Clique pour " + (over ? "rejouer" : "continuer"), x + bw / 2, y + 124, MUTED);
    }

    private void drawDragged(Canvas g) {
        if (dragging && pressItem >= 0 && pressItem < state.items.size()) {
            drawItemIcon(g, state.items.get(pressItem), mx, my, 12);
            return;
        }
        if (!dragging || pressSlot < 0) return;
        Unit u = state.get(pressSlot);
        if (u == null) return;
        drawPokemon(g, u.species, mx, my + 20, 1f, 0, "Walk", clock, true);
    }

    // ================================================================== éléments d'interface

    private void highlight(Canvas g, Rect r, Color c) {
        g.round(r.x, r.y, r.w, r.h, g.fade(c, 0.24f));
        g.outline(r.x, r.y, r.w, r.h, c);
    }

    private void typeBadge(Canvas g, PType t, float x, float y, float alpha) {
        g.round(x, y, 66, 18, g.fade(Canvas.rgb(t.rgb), alpha));
        g.centered(bold(11), t.label, x + 33, y + 13, g.fade(WHITE, Math.max(0.6f, alpha)));
    }

    /** Pastille ronde de la couleur de la classe avec son icône. */
    private void roleBadge(Canvas g, Trait t, float cx, float cy, float r) {
        g.ellipse(cx, cy, r + 1.5f, r + 1.5f, g.fade(BLACK, 0.7f));
        g.ellipse(cx, cy, r, r, Canvas.rgb(t.rgb));
        float ir = r * 0.68f;
        g.imageTinted(assets.roleIcon(t), cx - ir, cy - ir, ir * 2, ir * 2, WHITE, false);
    }

    /** Étiquette de classe (icône + nom), renvoie sa largeur. */
    private float traitPill(Canvas g, Trait t, float x, float y, float alpha) {
        float w = g.width(bold(11), t.label) + 26;
        g.round(x, y, w, 18, g.fade(Canvas.rgb(t.rgb), alpha));
        g.imageTinted(assets.roleIcon(t), x + 4, y + 2, 14, 14, g.fade(WHITE, Math.max(0.6f, alpha)), false);
        g.text(bold(11), t.label, x + 20, y + 13, g.fade(WHITE, Math.max(0.6f, alpha)));
        return w;
    }

    private void smallBadge(Canvas g, PType t, float x, float y) {
        g.round(x, y, 50, 15, Canvas.rgb(t.rgb));
        g.centered(bold(10), t.label, x + 25, y + 11, WHITE);
    }

    private void button(Canvas g, Rect r, String label, boolean enabled, Color base) {
        boolean hover = enabled && r.contains(mx, my);
        Color c = !enabled ? DISABLED : hover ? new Color(base).lerp(WHITE, 0.15f) : base;
        g.round(r.x, r.y, r.w, r.h, c);
        BitmapFont f = bold(r == BTN_MAIN ? 18 : 15);
        g.centered(f, label, r.cx(), r.cy() + f.getCapHeight() / 2, enabled ? WHITE : MUTED);
    }
}
