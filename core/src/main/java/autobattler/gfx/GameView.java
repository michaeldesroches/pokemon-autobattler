package autobattler.gfx;

import autobattler.Ability;
import autobattler.Battle;
import autobattler.Fighter;
import autobattler.GameState;
import autobattler.Hex;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Écran de jeu : mise en page, rendu et interactions souris/tactile. */
public final class GameView extends InputAdapter {
    public static final int W = 1000, H = 690;

    // Champ de bataille hexagonal : 7 colonnes × 8 lignes, hexagones de rayon HEX
    private static final float HEX = 28f;
    private static final float HEX_W = HEX * 1.7320508f;
    private static final float AX = 200, AY = 58;
    private static final Rect ARENA = new Rect(AX - 4, AY - 4, HEX_W * 7.5f + 8, HEX * 1.5f * 7 + HEX * 2 + 8);

    private static final Rect LEFT = new Rect(16, 58, 170, 350);
    private static final Rect RIGHT = new Rect(574, 58, 410, 350);
    private static final int BENCH_Y = 432, BENCH_X = 16, BENCH_STEP = 88, BENCH_SIZE = 82;
    private static final int SHOP_Y = 540, SHOP_X = 16, SHOP_STEP = 124, SHOP_W = 118, SHOP_H = 136;
    private static final Rect SHOP_AREA = new Rect(SHOP_X, SHOP_Y, 5 * SHOP_STEP, SHOP_H);
    private static final Rect BTN_A = new Rect(650, 540, 160, 40);
    private static final Rect BTN_B = new Rect(824, 540, 160, 40);
    private static final Rect BTN_C = new Rect(650, 590, 160, 86);
    private static final Rect BTN_MAIN = new Rect(824, 590, 160, 86);

    public static final Color BG = Canvas.rgb(0x1B1F2A);
    private static final Color PANEL = Canvas.rgb(0x262C3A);
    private static final Color PANEL_LIGHT = Canvas.rgb(0x323A4C);
    private static final Color DISABLED = Canvas.rgb(0x2A2F3B);
    private static final Color TRACK = Canvas.rgb(0x151922);
    private static final Color TEXT = Canvas.rgb(0xE8ECF4);
    private static final Color SOFT = Canvas.rgb(0xC5CEDF);
    private static final Color MUTED = Canvas.rgb(0x8C97AD);
    private static final Color GOLD = Canvas.rgb(0xF5C542);
    private static final Color ACCENT = Canvas.rgb(0xE84A5F);
    private static final Color GOOD = Canvas.rgb(0x4CC38A);
    private static final Color BLUE = Canvas.rgb(0x5DA9FF);
    private static final Color ORANGE = Canvas.rgb(0xFF9F43);
    private static final Color DODGE = Canvas.rgb(0x9AD0FF);
    private static final Color HEAL = Canvas.rgb(0x7CF0A0);
    private static final Color ENEMY_TILE = Canvas.rgb(0x40293A);
    private static final Color PLAYER_TILE = Canvas.rgb(0x23423A);
    private static final Color WHITE = Canvas.rgb(0xFFFFFF);
    private static final Color BLACK = Canvas.rgb(0x000000);
    private static final Color TIER1 = Canvas.rgb(0x7D8799);
    private static final Color TIER2 = Canvas.rgb(0x3FA36B);
    private static final Color TIER3 = Canvas.rgb(0x9B59D0);

    private final Assets assets;
    private final Viewport viewport;
    private final GameState state = new GameState(System.currentTimeMillis());
    private final Vector2 tmpVec = new Vector2();

    private int speed = 1;
    private double endDelay;

    private int selected = -1;
    private int pressSlot = -1;
    private boolean pressed;
    private float pressX, pressY;
    private boolean dragging;
    private float mx = -1, my = -1;

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

    private final List<Floater> floaters = new ArrayList<Floater>();
    private final List<Projectile> projectiles = new ArrayList<Projectile>();
    private final List<Area> areas = new ArrayList<Area>();
    /** Pour les capacités de zone : un seul libellé par lanceur et par lot d'événements. */
    private final Set<Fighter> shownAbility = new HashSet<Fighter>();

    public GameView(Assets assets, Viewport viewport) {
        this.assets = assets;
        this.viewport = viewport;
    }

    // ================================================================== logique d'animation

    public void update(float dt) {
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
            }
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
    }

    private void onEvent(Battle.Event ev) {
        switch (ev.kind) {
            case ATTACK: {
                ev.src.lunge = 1;
                ev.tgt.flash = 1;
                Color c = ev.mult > 1.01 ? ORANGE : ev.mult < 0.99 ? MUTED : WHITE;
                String txt = Math.round(ev.amount) + (ev.mult > 1.01 ? "!" : "");
                floaters.add(new Floater(fx(ev.tgt) + (float) ((Math.random() - 0.5) * 16), fy(ev.tgt) - 34, txt, c, ev.special));
                if (ev.src.range > 1 && !ev.special) projectiles.add(new Projectile(ev.src, ev.tgt, Canvas.rgb(ev.type.rgb)));
                break;
            }
            case ABILITY:
                if (shownAbility.add(ev.src)) {
                    floaters.add(new Floater(fx(ev.src), fy(ev.src) - 46, ev.text + " !", GOLD, true));
                }
                if (ev.src.range > 1 && ev.tgt != null) projectiles.add(new Projectile(ev.src, ev.tgt, Canvas.rgb(ev.type.rgb)));
                break;
            case AREA:
                areas.add(new Area(ev.cell, ev.radius, Canvas.rgb(ev.type.rgb)));
                break;
            case HEAL:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 34, "+" + Math.round(ev.amount), HEAL, false));
                break;
            case DODGE:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 30, "Esquive", DODGE, false));
                break;
            case STATUS:
                floaters.add(new Floater(fx(ev.tgt), fy(ev.tgt) - 22, ev.text, statusColor(ev.text), false));
                break;
            default:
                break;
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

    // ================================================================== géométrie

    private static float cellX(int cell) {
        int r = Hex.row(cell);
        return AX + HEX_W / 2 + HEX_W * (Hex.col(cell) + ((r & 1) == 1 ? 0.5f : 0f));
    }

    private static float cellY(int cell) {
        return AY + HEX + HEX * 1.5f * Hex.row(cell);
    }

    private static int slotCell(int slot) {
        int i = slot - 100;
        return Hex.fromLocal(true, i / GameState.COLS, i % GameState.COLS);
    }

    private static Rect benchRect(int i) {
        return new Rect(BENCH_X + i * BENCH_STEP, BENCH_Y, BENCH_SIZE, BENCH_SIZE);
    }

    /** Case hexagonale du champ de bataille sous un point, ou -1. */
    private static int cellAt(float x, float y) {
        int best = -1;
        float bestD = HEX * 0.95f;
        for (int c = 0; c < Hex.CELLS; c++) {
            float d = Vector2.dst(x, y, cellX(c), cellY(c));
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

    private static Rect shopRect(int i) {
        return new Rect(SHOP_X + i * SHOP_STEP, SHOP_Y, SHOP_W, SHOP_H);
    }

    private static int shopAt(float x, float y) {
        for (int i = 0; i < GameState.SHOP; i++) if (shopRect(i).contains(x, y)) return i;
        return -1;
    }

    /** Position affichée d'un combattant (interpolée pendant un déplacement). */
    private static float fx(Fighter f) {
        float p = f.moveT > 0 ? (float) (1 - f.moveT / f.moveDur) : 1f;
        return cellX(f.fromCell) + (cellX(f.cell) - cellX(f.fromCell)) * p;
    }

    private static float fy(Fighter f) {
        float p = f.moveT > 0 ? (float) (1 - f.moveT / f.moveDur) : 1f;
        return cellY(f.fromCell) + (cellY(f.cell) - cellY(f.fromCell)) * p;
    }

    // ================================================================== entrées

    /** Convertit des coordonnées écran (pixels) en coordonnées du jeu (origine en haut à gauche). */
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
        pressed = true;
        pressX = mx;
        pressY = my;
        dragging = false;
        pressSlot = -1;
        if (state.phase != GameState.Phase.PREP) return true;
        int s = slotAt(mx, my);
        if (s >= 0 && state.get(s) != null) {
            if (button == Input.Buttons.RIGHT) {
                state.sell(s);
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
        if (pressSlot >= 0 && pressed && Vector2.dst(pressX, pressY, mx, my) > 6) dragging = true;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        toGame(screenX, screenY);
        if (!pressed) return true;
        if (dragging && pressSlot >= 0) {
            int target = slotAt(mx, my);
            if (target >= 0) state.move(pressSlot, target);
            else if (SHOP_AREA.contains(mx, my)) state.sell(pressSlot);
            selected = -1;
        } else if (button != Input.Buttons.RIGHT) {
            onClick(mx, my);
        }
        dragging = false;
        pressSlot = -1;
        pressed = false;
        return true;
    }

    private void onClick(float x, float y) {
        switch (state.phase) {
            case PREP:
                if (BTN_A.contains(x, y)) {
                    state.reroll();
                } else if (BTN_B.contains(x, y)) {
                    state.toggleLock();
                } else if (BTN_C.contains(x, y)) {
                    if (selected >= 0) state.sell(selected);
                    selected = -1;
                } else if (BTN_MAIN.contains(x, y)) {
                    selected = -1;
                    if (state.startCombat()) {
                        endDelay = 0;
                        floaters.clear();
                        projectiles.clear();
                        areas.clear();
                    }
                } else {
                    int shop = shopAt(x, y);
                    if (shop >= 0) {
                        state.buy(shop);
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
        drawShop(g);
        drawButtons(g);
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

        g.text(bold(13), "PV", 140, 28, MUTED);
        g.round(164, 15, 150, 16, TRACK);
        float frac = state.hp / (float) GameState.START_HP;
        g.round(164, 15, 150 * frac, 16, frac > 0.5f ? GOOD : frac > 0.25f ? GOLD : ACCENT);
        g.centered(bold(12), state.hp + " / " + GameState.START_HP, 164 + 75, 28, WHITE);

        g.text(bold(15), "● " + state.gold + " or", 335, 29, GOLD);

        int bc = state.boardCount();
        g.text(regular(13), "Équipe " + bc + " / " + state.maxUnits(), 445, 29, bc > state.maxUnits() ? ACCENT : TEXT);
        g.text(regular(13), "Victoires " + state.wins, 570, 29, TEXT);
        Battle b = state.battle;
        if (state.phase == GameState.Phase.COMBAT && b != null && b.overtimeMult() > 1) {
            g.text(bold(13), "PROLONGATION · dégâts x" + fmt1(b.overtimeMult()), 680, 29, ACCENT);
        } else if (Math.abs(state.streak) >= 2) {
            g.text(regular(13), (state.streak > 0 ? "Série de victoires x" : "Série de défaites x") + Math.abs(state.streak),
                    680, 29, state.streak > 0 ? GOOD : ACCENT);
        }
        g.right(bold(13), "POKÉMON AUTO BATTLER", W - 16, 28, MUTED);
    }

    private void drawArena(Canvas g) {
        g.round(ARENA.x, ARENA.y, ARENA.w, ARENA.h, Canvas.rgb(0x20252F));
        boolean prep = state.phase == GameState.Phase.PREP;
        int hoverCell = prep ? cellAt(mx, my) : -1;
        for (int c = 0; c < Hex.CELLS; c++) {
            boolean playerSide = Hex.row(c) >= Hex.SIDE_ROWS;
            g.hex(cellX(c), cellY(c), HEX * 0.94f, playerSide ? PLAYER_TILE : ENEMY_TILE);
            if (playerSide && prep && (c == hoverCell && (dragging || selected >= 0))) {
                g.hex(cellX(c), cellY(c), HEX * 0.94f, g.fade(GOLD, 0.25f));
                g.hexOutline(cellX(c), cellY(c), HEX * 0.94f, GOLD);
            }
        }
        g.text(bold(10), "ADVERSAIRE", ARENA.x + 6, ARENA.y + 12, g.fade(WHITE, 0.35f));
        g.text(bold(10), "TON ÉQUIPE", ARENA.x + 6, ARENA.y + ARENA.h - 5, g.fade(WHITE, 0.35f));

        for (Area a : areas) {
            float alpha = Math.min(1f, a.life / 0.5f) * 0.45f;
            for (int c = 0; c < Hex.CELLS; c++) {
                if (Hex.distance(c, a.cell) <= a.radius) g.hex(cellX(c), cellY(c), HEX * 0.94f, g.fade(a.color, alpha));
            }
        }

        if (!prep && state.battle != null) {
            // tri par ligne d'affichage : ce qui est plus bas passe devant
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
                float y = fy(p.src) - 10 + (fy(p.tgt) - fy(p.src)) * p.t;
                g.ellipse(x, y, 6, 6, g.fade(p.color, 0.5f));
                g.ellipse(x, y, 3.5f, 3.5f, WHITE);
            }
            return;
        }

        for (GameState.Placed p : state.enemy) {
            int cell = Hex.fromLocal(false, p.row, p.col);
            drawUnitOnHex(g, p.unit, cellX(cell), cellY(cell), false, false);
        }
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLS; col++) {
                int s = GameState.boardSlot(row, col);
                Unit u = state.board[row][col];
                if (u == null || (dragging && pressSlot == s)) continue;
                int cell = slotCell(s);
                drawUnitOnHex(g, u, cellX(cell), cellY(cell), true, selected == s);
            }
    }

    private void drawUnitOnHex(Canvas g, Unit u, float cx, float cy, boolean back, boolean sel) {
        if (sel) g.hexOutline(cx, cy, HEX * 0.94f, GOLD);
        drawCreature(g, u.species, cx, cy - 2, 17, back);
        drawStars(g, u.stars, cx, cy - 21);
    }

    private void drawFighter(Canvas g, Fighter f) {
        float x = fx(f), y = fy(f);
        if (f.lunge > 0 && f.target != null) {
            float dx = fx(f.target) - x, dy = fy(f.target) - y, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1) {
                float k = (float) Math.sin(f.lunge * Math.PI) * 8 / len;
                x += dx * k;
                y += dy * k;
            }
        }
        g.alpha = f.alive ? 1f : 0.15f;
        if (f.tauntTime > 0) g.hexOutline(x, y, HEX * 0.94f, ACCENT);
        drawCreature(g, f.species(), x, y - 2, 17, f.player);
        if (f.stun > 0) g.ellipse(x, y - 4, 18, 18, g.fade(Canvas.rgb(PType.ICE.rgb), 0.45f));
        if (f.flash > 0) g.ellipse(x, y - 4, 16, 16, g.fade(Canvas.rgb(0xFF5050), (float) (0.45 * f.flash)));
        g.alpha = 1f;
        if (!f.alive) return;

        // barres : PV (+ bouclier) et mana
        float bw = 38, bx = x - bw / 2, by = y - 28;
        g.rect(bx - 1, by - 1, bw + 2, 9, TRACK);
        double total = Math.max(f.maxHp, f.hp + f.shield);
        float hpW = (float) (bw * f.hp / total), shW = (float) (bw * f.shield / total);
        g.rect(bx, by, hpW, 4, f.player ? GOOD : ACCENT);
        g.rect(bx + hpW, by, shW, 4, Canvas.rgb(0xDDE6F5));
        g.rect(bx, by + 5, (float) (bw * Math.min(1, f.mana / f.maxMana)), 2, BLUE);
        if (f.unit.stars > 1) drawStars(g, f.unit.stars, x, by - 3);

        // pastilles de statut
        float sx = x + bw / 2 + 4, sy = by + 2;
        if (f.burnTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.FIRE.rgb));
        if (f.poisonTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.POISON.rgb));
        if (f.paraTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(PType.ELECTRIC.rgb));
        if (f.hasteTime > 0) sy = statusDot(g, sx, sy, Canvas.rgb(0x5DFFB0));
        if (f.sleep > 0) g.text(bold(12), "z", x + 10, y - 12, Canvas.rgb(0xB7A6FF));
    }

    private float statusDot(Canvas g, float x, float y, Color c) {
        g.ellipse(x, y, 3, 3, c);
        return y + 7;
    }

    /** Sprite PokéAPI posé sur un socle aux couleurs de son type principal. */
    private void drawCreature(Canvas g, Species sp, float cx, float cy, float rad, boolean back) {
        Color c1 = Canvas.rgb(sp.types[0].rgb);
        g.ellipse(cx, cy + rad * 0.92f, rad * 1.1f, rad * 0.37f, g.fade(c1, 0.35f));
        g.ellipse(cx, cy + rad * 0.92f, rad * 0.8f, rad * 0.22f, g.fade(BLACK, 0.25f));
        TextureRegion img = assets.sprite(sp.dex, back);
        if (img == null) {
            g.ellipse(cx, cy, rad, rad, c1);
            return;
        }
        float box = rad * 2.6f;
        float scale = Math.min(3f, Math.min(box / img.getRegionWidth(), box / img.getRegionHeight()));
        float w = img.getRegionWidth() * scale, h = img.getRegionHeight() * scale;
        float bottom = cy + rad * 0.95f;
        g.image(img, cx - w / 2, bottom - h, w, h);
    }

    private void drawStars(Canvas g, int stars, float cx, float y) {
        if (stars <= 1) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars; i++) sb.append('★');
        g.centered(bold(11), sb.toString(), cx, y, GOLD);
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
            g.text(bold(12), counts.get(t) + "  (" + ths + ")", LEFT.x + 82, y + 13, active ? TEXT : MUTED);
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
        float x = RIGHT.x + 16, y = RIGHT.y + 28;

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

        if (sp == null) {
            g.text(bold(15), "Comment jouer", x, y, TEXT);
            String[] lines = {
                    "• Clique une carte de la boutique pour acheter.",
                    "• Glisse un Pokémon sur une case de ton camp (en bas).",
                    "• Au combat, chacun fonce sur l'ennemi le plus proche :",
                    "  corps à corps devant, attaquants à distance derrière.",
                    "• Mana plein : capacité unique (survole pour la voir).",
                    "• 3 exemplaires identiques fusionnent (évolution, ★).",
                    "• Clic droit ou glisser vers la boutique : vendre.",
                    "• Les types donnent des synergies (panneau de gauche).",
                    "• Chaque tranche de 10 or rapporte 1 or d'intérêt.",
                    "• Après 30 s de combat, les dégâts augmentent."
            };
            float ly = y + 26;
            for (String l : lines) {
                g.text(regular(13), l, x, ly, SOFT);
                ly += 20;
            }
        } else {
            int stars = unit == null ? 1 : unit.stars;
            g.text(bold(22), sp.name + (stars > 1 ? "  " + stars + "★" : ""), x, y, TEXT);
            g.right(regular(12), "N° " + sp.dex + " · Tier " + sp.tier + " · " + sp.tier + " or", RIGHT.x + RIGHT.w - 16, y, MUTED);
            float bx = x;
            for (PType t : sp.types) {
                typeBadge(g, t, bx, y + 12, 1f);
                bx += 72;
            }
            int range = sp.range();
            g.right(regular(12), range <= 1 ? "Corps à corps" : "Distance · portée " + range, RIGHT.x + RIGHT.w - 16, y + 25, SOFT);
            Unit probe = unit != null ? unit : new Unit(sp, 1, sp.tier);
            Fighter f = new Fighter(probe, true, 0, 0, null);
            float sy = y + 48;
            statLine(g, "PV", (int) f.maxHp, 700, x, sy);
            statLine(g, "Attaque", (int) f.atk, 300, x, sy + 22);
            statLine(g, "Défense", (int) f.def, 130, x, sy + 44);
            g.text(regular(13), "Attaques / s", x, sy + 66 + 12, MUTED);
            g.text(bold(13), fmt2(1 / f.interval), x + 110, sy + 66 + 12, TEXT);

            float ty = sy + 104;
            Ability a = sp.ability();
            g.text(bold(13), "Capacité : " + a.label, x, ty, GOLD);
            g.right(regular(12), (int) f.maxMana + " mana (départ " + a.startMana + ")", RIGHT.x + RIGHT.w - 16, ty, MUTED);
            g.wrap(regular(13), a.description, x, ty + 19, RIGHT.w - 32, 17, SOFT);
            Unit m = probe.merged();
            String evo = m == null ? "Niveau maximal atteint."
                    : m.species != probe.species ? "3 exemplaires → évolue en " + m.species.name
                    : "3 exemplaires → " + m.stars + "★";
            g.text(regular(13), evo, x, ty + 58, SOFT);
            if (owned && unit != null) g.right(regular(13), "Revente : " + unit.invested + " or", RIGHT.x + RIGHT.w - 16, ty + 58, GOLD);
        }

        if (state.message != null && !state.message.isEmpty()) {
            g.round(RIGHT.x + 8, RIGHT.y + RIGHT.h - 40, RIGHT.w - 16, 32, PANEL_LIGHT);
            g.text(bold(13), state.message, RIGHT.x + 18, RIGHT.y + RIGHT.h - 19, GOLD);
        }
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
        g.round(x + 110, y + 2, 200, 12, TRACK);
        g.round(x + 110, y + 2, 200 * Math.min(1f, value / (float) max), 12, BLUE);
        g.text(bold(13), String.valueOf(value), x + 320, y + 13, TEXT);
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
            drawCreature(g, u.species, r.cx(), r.cy() - 4, 22, false);
            drawStars(g, u.stars, r.cx(), r.y + 14);
            g.centered(bold(11), u.species.name, r.cx(), r.y + r.h - 6, TEXT);
        }
    }

    private void drawShop(Canvas g) {
        g.text(bold(12), dragging ? "DÉPOSE ICI POUR VENDRE" : "BOUTIQUE" + (state.shopLocked ? "  (verrouillée)" : ""),
                SHOP_X, SHOP_Y - 6, MUTED);
        if (dragging) highlight(g, SHOP_AREA, ACCENT);
        boolean prep = state.phase == GameState.Phase.PREP;
        for (int i = 0; i < GameState.SHOP; i++) {
            Rect r = shopRect(i);
            Species sp = state.shop[i];
            g.round(r.x, r.y, r.w, r.h, PANEL);
            if (sp == null) continue;
            if (!prep) g.alpha = 0.4f;
            Color tierColor = sp.tier == 1 ? TIER1 : sp.tier == 2 ? TIER2 : TIER3;
            boolean hover = r.contains(mx, my) && prep;
            g.outline(r.x, r.y, r.w, r.h, hover ? GOLD : tierColor);
            drawCreature(g, sp, r.cx(), r.y + 40, 26, false);
            g.centered(bold(13), sp.name, r.cx(), r.y + 84, TEXT);
            float tx = r.x + (r.w - (sp.types.length * 54 - 4)) / 2;
            for (PType t : sp.types) {
                smallBadge(g, t, tx, r.y + 92);
                tx += 54;
            }
            g.centered(bold(13), "● " + sp.tier, r.cx(), r.y + 128, state.gold >= sp.tier ? GOLD : ACCENT);
            g.alpha = 1f;
        }
    }

    private void drawButtons(Canvas g) {
        switch (state.phase) {
            case PREP:
                button(g, BTN_A, "Relancer  ● 1", state.gold >= 1, PANEL_LIGHT);
                button(g, BTN_B, state.shopLocked ? "Déverrouiller" : "Verrouiller", true, state.shopLocked ? Canvas.rgb(0x5A4A20) : PANEL_LIGHT);
                button(g, BTN_C, "Vendre", selected >= 0, PANEL_LIGHT);
                button(g, BTN_MAIN, "COMBAT !", state.boardCount() > 0, ACCENT);
                break;
            case COMBAT:
                button(g, BTN_A, "Vitesse x" + speed, true, PANEL_LIGHT);
                button(g, BTN_B, "Passer", true, PANEL_LIGHT);
                button(g, BTN_C, "En combat", false, PANEL_LIGHT);
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

    private void drawFloaters(Canvas g) {
        for (Floater f : floaters) {
            float a = Math.max(0, Math.min(1, f.life / 0.4f));
            BitmapFont font = bold(f.big ? 15 : 12);
            g.centered(font, f.text, f.x + 1, f.y + 1, g.fade(BLACK, 0.6f * a));
            g.centered(font, f.text, f.x, f.y, g.fade(f.color, a));
        }
    }

    private void drawOverlay(Canvas g) {
        if (state.phase != GameState.Phase.RESULT && state.phase != GameState.Phase.GAMEOVER) return;
        Rect a = ARENA;
        float bw = 300, bh = 120;
        float x = a.x + (a.w - bw) / 2, y = a.y + (a.h - bh) / 2;
        g.rect(a.x, a.y, a.w, a.h, g.fade(BLACK, 0.47f));
        g.round(x, y, bw, bh, PANEL);
        boolean over = state.phase == GameState.Phase.GAMEOVER;
        g.centered(bold(22), over ? "Partie terminée" : state.lastWin ? "Victoire !" : "Défaite", x + bw / 2, y + 42,
                over ? ACCENT : state.lastWin ? GOOD : ACCENT);
        g.centered(regular(13), over ? "Manche " + state.round + " atteinte · " + state.wins + " victoires"
                : state.lastWin ? "Tes Pokémon ont gagné la manche." : "Tu perds " + state.lastDamage + " PV.", x + bw / 2, y + 72, TEXT);
        g.centered(regular(13), "Clique pour " + (over ? "rejouer" : "continuer"), x + bw / 2, y + 100, MUTED);
    }

    private void drawDragged(Canvas g) {
        if (!dragging || pressSlot < 0) return;
        Unit u = state.get(pressSlot);
        if (u == null) return;
        drawCreature(g, u.species, mx, my, 22, false);
        drawStars(g, u.stars, mx, my - 26);
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
        BitmapFont f = bold(r.h > 60 ? 22 : 15);
        g.centered(f, label, r.cx(), r.cy() + f.getCapHeight() / 2, enabled ? WHITE : MUTED);
    }
}
