package autobattler.gfx;

import autobattler.Ability;
import autobattler.Battle;
import autobattler.Fighter;
import autobattler.GameState;
import autobattler.Hex;
import autobattler.Item;
import autobattler.PType;
import autobattler.Species;
import autobattler.Synergies;
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
    private static final float BOARD_CX = 560f, BOARD_Y0 = 126f;
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
    private final GameState state = new GameState(System.currentTimeMillis());
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
        float t;

        Projectile(Fighter src, Fighter tgt, Color color) {
            this.src = src;
            this.tgt = tgt;
            this.color = new Color(color);
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
        float x, y, vx, vy, life, maxLife, size;
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
    }

    private final List<Floater> floaters = new ArrayList<Floater>();
    private final List<Projectile> projectiles = new ArrayList<Projectile>();
    private final List<Area> areas = new ArrayList<Area>();
    private final List<Particle> particles = new ArrayList<Particle>();
    private final Set<Fighter> shownAbility = new HashSet<Fighter>();

    public GameView(Assets assets, PmdSprites pmd, Sounds sounds, Viewport viewport) {
        this.assets = assets;
        this.pmd = pmd;
        this.sounds = sounds;
        this.viewport = viewport;
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
            p.t += dt * speed / 0.2f;
            if (p.t >= 1) it.remove();
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
            p.vy += 160 * dt;
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
                if (!ev.special) play(ev.src, "Attack");
                AnimState ta = anim(ev.tgt);
                if (!ta.once || !"Attack".equals(ta.name)) play(ev.tgt, "Hurt");
                boolean crit = "crit".equals(ev.text);
                Color c = crit ? Canvas.rgb(0xFF5A36) : ev.mult > 1.01 ? ORANGE : ev.mult < 0.99 ? MUTED : WHITE;
                String txt = Math.round(ev.amount) + (ev.mult > 1.01 || crit ? "!" : "");
                float s = persp(Hex.row(ev.tgt.cell));
                floaters.add(new Floater(fx(ev.tgt) + (fxRng.nextFloat() - 0.5f) * 16, fy(ev.tgt) - 58 * s, txt, c, ev.special || crit));
                if (ev.src.range > 1 && !ev.special) projectiles.add(new Projectile(ev.src, ev.tgt, Canvas.rgb(ev.type.rgb)));
                burst(fx(ev.tgt), fy(ev.tgt) - 20 * s, Canvas.rgb(ev.type.rgb), crit ? 9 : 5, crit ? 120 : 80);
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
                    floaters.add(new Floater(fx(ev.src), fy(ev.src) - 72 * persp(Hex.row(ev.src.cell)), ev.text + " !", GOLD, true));
                    sounds.play("ability", 0.6f, 80);
                    shake = Math.max(shake, 4);
                }
                play(ev.src, "Attack");
                if (ev.src.range > 1 && ev.tgt != null) projectiles.add(new Projectile(ev.src, ev.tgt, Canvas.rgb(ev.type.rgb)));
                break;
            case AREA: {
                Color c = Canvas.rgb(ev.type.rgb);
                areas.add(new Area(ev.cell, ev.radius, c));
                for (int cell = 0; cell < Hex.CELLS; cell++) {
                    if (Hex.distance(cell, ev.cell) <= ev.radius) burst(cellX(cell), cellY(cell) - 10, c, 4, 90);
                }
                shake = Math.max(shake, 5);
                break;
            }
            case HEAL:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 58 * persp(Hex.row(ev.tgt.cell)), "+" + Math.round(ev.amount), HEAL, false));
                burst(fx(ev.tgt), fy(ev.tgt) - 20, HEAL, 6, 50);
                break;
            case DODGE:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 50, "Esquive", DODGE, false));
                break;
            case STATUS:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 42, ev.text, statusColor(ev.text), false));
                break;
            case FAINT:
                burst(fx(ev.tgt), fy(ev.tgt) - 20, WHITE, 12, 110);
                sounds.play("faint", 0.5f, 80);
                break;
            default:
                break;
        }
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
        g.text(regular(13), "Victoires " + state.wins, 570, 29, TEXT);
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
        g.text(bold(11), state.isPveRound() ? "POKÉMON SAUVAGES" : "ADVERSAIRE", ARENA.x + 10, ARENA.y + 16,
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
            for (Fighter f : order) drawFighter(g, f);
            for (Projectile p : projectiles) {
                float x = fx(p.src) + (fx(p.tgt) - fx(p.src)) * p.t;
                float y = fy(p.src) - 24 + (fy(p.tgt) - fy(p.src)) * p.t;
                g.ellipse(x, y, 8, 8, g.fade(p.color, 0.45f));
                g.ellipse(x, y, 4, 4, WHITE);
            }
            return;
        }

        // préparation : adversaire puis plateau du joueur, du fond vers l'avant
        List<Object[]> draws = new ArrayList<Object[]>();
        for (GameState.Placed p : state.enemy) draws.add(new Object[]{p.unit, Hex.fromLocal(false, p.row, p.col), Boolean.FALSE, -1});
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLS; col++) {
                int s = GameState.boardSlot(row, col);
                Unit u = state.board[row][col];
                if (u == null || (dragging && pressSlot == s)) continue;
                draws.add(new Object[]{u, slotCell(s), Boolean.TRUE, s});
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
            if (mine && selected == slot) drawTile(g, cell, GOLD, true);
            float s = persp(Hex.row(cell));
            drawPokemon(g, u.species, cellX(cell), cellY(cell), s, mine ? 4 : 0, "Idle", clock + cell * 0.37f, true);
            drawStars(g, u.stars, cellX(cell), cellY(cell) - 54 * s);
            drawHeldItems(g, u, cellX(cell), cellY(cell) + 12 * s, 4.5f);
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
        g.alpha = f.alive ? 1f : Math.max(0f, 0.6f - a.t * 0.8f);
        if (g.alpha <= 0) {
            g.alpha = 1f;
            return;
        }
        if (f.tauntTime > 0) g.ellipse(x, y, 30 * s, 12 * s, g.fade(ACCENT, 0.35f));
        drawPokemon(g, f.species(), x, y, s, a.dir, a.name, a.t, !a.once);
        if (f.stun > 0) g.ellipse(x, y - 22 * s, 26 * s, 26 * s, g.fade(Canvas.rgb(PType.ICE.rgb), 0.4f));
        if (f.flash > 0) g.ellipse(x, y - 22 * s, 20 * s, 20 * s, g.fade(Canvas.rgb(0xFF5050), (float) (0.4 * f.flash)));
        g.alpha = 1f;
        if (!f.alive) return;

        float bw = 44 * s, bx = x - bw / 2, by = y - 62 * s;
        g.rect(bx - 1, by - 1, bw + 2, 10, TRACK);
        double total = Math.max(f.maxHp, f.hp + f.shield);
        float hpW = (float) (bw * f.hp / total), shW = (float) (bw * f.shield / total);
        g.rect(bx, by, hpW, 5, f.player ? GOOD : ACCENT);
        g.rect(bx + hpW, by, shW, 5, Canvas.rgb(0xDDE6F5));
        g.rect(bx, by + 6, (float) (bw * Math.min(1, f.mana / f.maxMana)), 2, BLUE);
        if (f.unit.stars > 1) drawStars(g, f.unit.stars, x, by - 3);
        drawHeldItems(g, f.unit, x, y + 12 * s, 3.5f);

        float sx = x + bw / 2 + 5, sy = by + 3;
        if (f.burnTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.FIRE.rgb));
        if (f.poisonTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.POISON.rgb));
        if (f.paraTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.ELECTRIC.rgb));
        if (f.hasteTime > 0) statusDot(g, sx, sy, Canvas.rgb(0x5DFFB0));
        if (f.sleep > 0) g.text(bold(13), "z", x + 12 * s, y - 40 * s - (clock % 1) * 6, Canvas.rgb(0xB7A6FF));
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
        float scale = 2f * persp * Math.min(1f, 34f / Math.max(18f, pmd.idleHeight(sp.dex)) * 1.25f);
        g.ellipse(gx, gy, 15 * persp, 5.5f * persp, g.fade(BLACK, 0.35f));
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

    private void drawHeldItems(Canvas g, Unit u, float cx, float y, float r) {
        int n = u.items.size();
        for (int i = 0; i < n; i++) {
            float x = cx + (i - (n - 1) / 2f) * (r * 2 + 2);
            g.ellipse(x, y, r + 1, r + 1, BLACK);
            g.ellipse(x, y, r, r, Canvas.rgb(u.items.get(i).rgb));
        }
    }

    private void drawItemIcon(Canvas g, Item it, float cx, float cy, float r) {
        if (!it.isComponent()) g.ellipse(cx, cy, r + 2.5f, r + 2.5f, GOLD);
        g.ellipse(cx, cy, r, r, Canvas.rgb(it.rgb));
        g.ellipse(cx, cy, r * 0.72f, r * 0.72f, g.fade(BLACK, 0.25f));
        g.centered(bold(10), it.abbr, cx, cy + 4, WHITE);
    }

    private void drawSynergies(Canvas g) {
        g.round(LEFT.x, LEFT.y, LEFT.w, LEFT.h, PANEL);
        g.text(bold(15), "Synergies", LEFT.x + 12, LEFT.y + 22, TEXT);
        List<Unit> units = state.boardUnits();
        final Map<PType, Integer> counts = Synergies.counts(units);
        final Map<PType, Integer> levels = Synergies.levels(units);
        if (counts.isEmpty()) {
            g.wrap(regular(12), "Place des Pokémon de même type sur le plateau pour activer des bonus.",
                    LEFT.x + 12, LEFT.y + 46, LEFT.w - 24, 16, MUTED);
            return;
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
        float y = LEFT.y + 36;
        for (PType t : order) {
            if (y > LEFT.y + LEFT.h - 30) break;
            Integer lvl = levels.get(t);
            boolean active = lvl != null;
            int[] th = Synergies.thresholds(t);
            StringBuilder ths = new StringBuilder();
            for (int i = 0; i < th.length; i++) ths.append(i == 0 ? "" : " · ").append(th[i]);
            typeBadge(g, t, LEFT.x + 10, y, active ? 1f : 0.45f);
            g.text(bold(12), counts.get(t) + "  (" + ths + ")", LEFT.x + 84, y + 13, active ? TEXT : MUTED);
            if (active) {
                g.text(regular(12), Synergies.describe(t, lvl), LEFT.x + 12, y + 31, SOFT);
                y += 42;
            } else {
                y += 26;
            }
        }
    }

    private void drawInfo(Canvas g) {
        g.round(RIGHT.x, RIGHT.y, RIGHT.w, RIGHT.h, PANEL);
        float x = RIGHT.x + 14, y = RIGHT.y + 14;

        Unit unit = null;
        Species sp = null;
        boolean owned = false;
        int hoverSlot = slotAt(mx, my);
        int hoverShop = shopAt(mx, my);
        boolean prep = state.phase == GameState.Phase.PREP;
        if (prep && hoverShop >= 0 && state.shop[hoverShop] != null) {
            sp = state.shop[hoverShop];
        } else if (prep && hoverSlot >= 0 && state.get(hoverSlot) != null) {
            unit = state.get(hoverSlot);
            owned = true;
        } else if (selected >= 0 && state.get(selected) != null) {
            unit = state.get(selected);
            owned = true;
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
            float bx = hx;
            for (PType t : sp.types) {
                typeBadge(g, t, bx, y + 30, 1f);
                bx += 72;
            }
            int range = sp.range();
            g.text(regular(12), "Tier " + sp.tier + " · " + (range <= 1 ? "corps à corps" : "distance (portée " + range + ")"), hx, y + 62, MUTED);

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
            if (owned) g.right(regular(13), "Revente : " + unit.invested + " or", RIGHT.x + RIGHT.w - 14, ty + 60, GOLD);
            if (unit != null && !unit.items.isEmpty()) {
                float iy = ty + 84;
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
            drawPokemon(g, u.species, r.cx(), r.y + r.h - 18, 0.95f, 0, "Idle", clock + i * 0.3f, true);
            drawStars(g, u.stars, r.cx(), r.y + 13);
            drawHeldItems(g, u, r.cx(), r.y + r.h - 9, 4f);
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
            drawPokemon(g, sp, r.x + r.w - 36, r.y + 58, 0.85f, 1, "Idle", clock + i * 0.5f, true);
            g.text(bold(13), sp.name, r.x + 10, r.y + 80, TEXT);
            float tx = r.x + 10;
            for (PType t : sp.types) {
                smallBadge(g, t, tx, r.y + 90);
                tx += 54;
            }
            g.text(regular(12), sp.range() <= 1 ? "Corps à corps" : "Distance " + sp.range(), r.x + 10, r.y + 130, MUTED);
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
        g.centered(bold(22), over ? "Partie terminée" : state.lastWin ? "Victoire !" : "Défaite", x + bw / 2, y + 42,
                over ? ACCENT : state.lastWin ? GOOD : ACCENT);
        g.centered(regular(13), over ? "Manche " + state.round + " atteinte · " + state.wins + " victoires"
                : state.lastWin ? "Tes Pokémon ont gagné la manche." : "Tu perds " + state.lastDamage + " PV.", x + bw / 2, y + 66, TEXT);
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
