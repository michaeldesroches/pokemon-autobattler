package autobattler.gfx;

import autobattler.Battle;
import autobattler.Fighter;
import autobattler.GameState;
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

    private static final int AX = 200, AY = 58, CELL = 84, GAP = 14;
    private static final Rect LEFT = new Rect(16, 58, 170, 350);
    private static final Rect RIGHT = new Rect(570, 58, 414, 350);
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
    private static final Color ENEMY_ZONE = Canvas.rgb(0x3A2630);
    private static final Color PLAYER_ZONE = Canvas.rgb(0x233A33);
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

    private final List<Floater> floaters = new ArrayList<Floater>();
    /** Pour Séisme (attaque de zone), n'affiche le nom de l'attaque qu'une fois par lot d'événements. */
    private final Set<Fighter> shownSpecial = new HashSet<Fighter>();

    public GameView(Assets assets, Viewport viewport) {
        this.assets = assets;
        this.viewport = viewport;
    }

    // ================================================================== logique d'animation

    public void update(float dt) {
        if (state.phase == GameState.Phase.COMBAT && state.battle != null) {
            Battle b = state.battle;
            if (b.result == 0) {
                shownSpecial.clear();
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
    }

    private void onEvent(Battle.Event ev) {
        switch (ev.kind) {
            case ATTACK: {
                ev.src.lunge = 1;
                ev.tgt.flash = 1;
                Color c = ev.mult > 1.01 ? ORANGE : ev.mult < 0.99 ? MUTED : WHITE;
                String txt = Math.round(ev.amount) + (ev.mult > 1.01 ? "!" : "");
                floaters.add(new Floater(fighterX(ev.tgt) + (float) ((Math.random() - 0.5) * 20), fighterY(ev.tgt) - 30, txt, c, false));
                if (ev.special && shownSpecial.add(ev.src)) {
                    floaters.add(new Floater(fighterX(ev.src), fighterY(ev.src) - 40, ev.src.species().moveType().move + " !", GOLD, true));
                }
                break;
            }
            case DODGE:
                floaters.add(new Floater(fighterX(ev.tgt), fighterY(ev.tgt) - 20, "Esquive", DODGE, false));
                break;
            case FREEZE:
                floaters.add(new Floater(fighterX(ev.tgt), fighterY(ev.tgt) - 34, "Gelé !", Canvas.rgb(PType.ICE.rgb), false));
                break;
            default:
                break;
        }
    }

    // ================================================================== géométrie

    private static Rect cell(int visualRow, int col) {
        return new Rect(AX + col * CELL, AY + visualRow * CELL + (visualRow >= 2 ? GAP : 0), CELL, CELL);
    }

    private static Rect arena() {
        return cell(0, 0).union(cell(3, 3));
    }

    private static int visualRow(boolean player, int row) {
        return player ? 2 + row : 1 - row;
    }

    private static Rect slotRect(int slot) {
        if (GameState.isBoard(slot)) {
            int i = slot - 100;
            return cell(2 + i / GameState.COLS, i % GameState.COLS);
        }
        return new Rect(BENCH_X + slot * BENCH_STEP, BENCH_Y, BENCH_SIZE, BENCH_SIZE);
    }

    private static int slotAt(float x, float y) {
        for (int r = 0; r < GameState.ROWS; r++)
            for (int c = 0; c < GameState.COLS; c++) {
                int s = GameState.boardSlot(r, c);
                if (slotRect(s).contains(x, y)) return s;
            }
        for (int i = 0; i < GameState.BENCH; i++) if (slotRect(i).contains(x, y)) return i;
        return -1;
    }

    private static Rect shopRect(int i) {
        return new Rect(SHOP_X + i * SHOP_STEP, SHOP_Y, SHOP_W, SHOP_H);
    }

    private static int shopAt(float x, float y) {
        for (int i = 0; i < GameState.SHOP; i++) if (shopRect(i).contains(x, y)) return i;
        return -1;
    }

    private static float fighterX(Fighter f) {
        return cell(visualRow(f.player, f.row), f.col).cx();
    }

    private static float fighterY(Fighter f) {
        return cell(visualRow(f.player, f.row), f.col).cy() - 6;
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
                }
                break;
            case RESULT:
                if (BTN_MAIN.contains(x, y) || arena().contains(x, y)) state.nextRound();
                break;
            case GAMEOVER:
                if (BTN_MAIN.contains(x, y) || arena().contains(x, y)) {
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
        if (Math.abs(state.streak) >= 2) {
            g.text(regular(13), (state.streak > 0 ? "Série de victoires x" : "Série de défaites x") + Math.abs(state.streak),
                    680, 29, state.streak > 0 ? GOOD : ACCENT);
        }
        g.right(bold(13), "POKÉMON AUTO BATTLER", W - 16, 28, MUTED);
    }

    private void drawArena(Canvas g) {
        Rect enemyZone = cell(0, 0).union(cell(1, 3));
        Rect playerZone = cell(2, 0).union(cell(3, 3));
        g.round(enemyZone.x - 4, enemyZone.y - 4, enemyZone.w + 8, enemyZone.h + 8, ENEMY_ZONE);
        g.round(playerZone.x - 4, playerZone.y - 4, playerZone.w + 8, playerZone.h + 8, PLAYER_ZONE);
        for (int vr = 0; vr < 4; vr++)
            for (int c = 0; c < 4; c++) {
                Rect r = cell(vr, c);
                g.rect(r.x, r.y, r.w, r.h, g.fade(WHITE, (vr + c) % 2 == 0 ? 0.055f : 0.025f));
            }
        g.text(bold(10), "ADVERSAIRE", enemyZone.x + 2, enemyZone.y + 10, g.fade(WHITE, 0.35f));
        g.text(bold(10), "TON ÉQUIPE", playerZone.x + 2, playerZone.y + playerZone.h - 3, g.fade(WHITE, 0.35f));

        if (state.phase != GameState.Phase.PREP && state.battle != null) {
            // le plus loin d'abord : profondeur correcte quand les sprites débordent
            for (Fighter f : state.battle.fighters) if (!f.player) drawFighter(g, f);
            for (Fighter f : state.battle.fighters) if (f.player) drawFighter(g, f);
            return;
        }

        for (GameState.Placed p : state.enemy) {
            drawUnitInCell(g, p.unit, cell(visualRow(false, p.row), p.col), true, false, false, false);
        }
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLS; col++) {
                int s = GameState.boardSlot(row, col);
                Rect r = slotRect(s);
                if (dragging && slotAt(mx, my) == s) highlight(g, r, GOLD);
                Unit u = state.board[row][col];
                if (u != null && !(dragging && pressSlot == s)) drawUnitInCell(g, u, r, false, true, selected == s, false);
            }
    }

    private void drawFighter(Canvas g, Fighter f) {
        Rect r = cell(visualRow(f.player, f.row), f.col);
        float dy = (float) (Math.sin(f.lunge * Math.PI) * 14) * (f.player ? -1 : 1);
        Rect rr = new Rect(r.x, r.y + dy, r.w, r.h);
        g.alpha = f.alive ? 1f : 0.18f;
        drawUnitInCell(g, f.unit, rr, !f.player, f.player, false, true);
        g.alpha = 1f;
        if (!f.alive) return;
        float bx = rr.x + 10, by = rr.y + 4, bw = rr.w - 20;
        g.rect(bx, by, bw, 6, TRACK);
        double hf = f.hp / f.maxHp;
        g.rect(bx, by, (float) (bw * hf), 6, hf > 0.5 ? GOOD : hf > 0.25 ? GOLD : ACCENT);
        g.rect(bx, by + 8, bw, 3, TRACK);
        g.rect(bx, by + 8, (float) (bw * Math.min(1, f.energy / 100)), 3, BLUE);
        if (f.flash > 0) g.ellipse(rr.cx(), rr.cy() - 6, 26, 26, g.fade(Canvas.rgb(0xFF5050), (float) (0.45 * f.flash)));
        if (f.stun > 0) g.ellipse(rr.cx(), rr.cy() - 6, 28, 28, g.fade(Canvas.rgb(PType.ICE.rgb), 0.45f));
        if (f.poisonTime > 0) g.ellipse(rr.x + rr.w - 12, rr.y + 20, 4, 4, Canvas.rgb(PType.POISON.rgb));
    }

    private void drawUnitInCell(Canvas g, Unit u, Rect r, boolean enemy, boolean back, boolean sel, boolean combat) {
        if (sel) highlight(g, r, GOLD);
        float cx = r.cx(), cy = r.cy() - 4;
        drawCreature(g, u.species, cx, cy, 22, back);
        drawStars(g, u.stars, cx, r.y + (combat ? 24 : 14));
        g.centered(bold(11), u.species.name, cx, r.y + r.h - 6, TEXT);
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
        g.centered(bold(12), sb.toString(), cx, y, GOLD);
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
            for (GameState.Placed p : state.enemy)
                if (cell(visualRow(false, p.row), p.col).contains(mx, my)) unit = p.unit;
        }
        if (unit != null) sp = unit.species;

        if (sp == null) {
            g.text(bold(15), "Comment jouer", x, y, TEXT);
            String[] lines = {
                    "• Clique une carte de la boutique pour acheter.",
                    "• Glisse (ou clique puis clique) pour placer un Pokémon",
                    "  sur ton plateau. La ligne du haut est en première ligne.",
                    "• 3 exemplaires identiques fusionnent : évolution",
                    "  ou étoile supplémentaire.",
                    "• Clic droit ou glisser vers la boutique : vendre.",
                    "• Les types donnent des synergies (panneau de gauche)",
                    "  et la table des types s'applique aux dégâts.",
                    "• Chaque tranche de 10 or rapporte 1 or d'intérêt.",
                    "• Survole un Pokémon pour voir ses statistiques."
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
            Unit probe = unit != null ? unit : new Unit(sp, 1, sp.tier);
            Fighter f = new Fighter(probe, true, 0, 0, null);
            float sy = y + 56;
            statLine(g, "PV", (int) f.maxHp, 700, x, sy);
            statLine(g, "Attaque", (int) f.atk, 300, x, sy + 24);
            statLine(g, "Défense", (int) f.def, 130, x, sy + 48);
            statLine(g, "Attaques / s", -1, 0, x, sy + 72);
            g.text(bold(13), fmt2(1 / f.interval), x + 110, sy + 72 + 12, TEXT);

            float ty = sy + 112;
            PType mt = sp.moveType();
            String special = mt == PType.GROUND ? "touche tous les ennemis" : mt == PType.PSYCHIC ? "dégâts x3"
                    : mt == PType.GRASS ? "dégâts x2.5 et soigne 20%" : "dégâts x2.5";
            g.text(regular(13), "Spéciale à 100 d'énergie : " + mt.move, x, ty, SOFT);
            g.text(regular(13), "  (" + special + ")", x, ty + 18, MUTED);
            ty += 18;
            Unit m = probe.merged();
            String evo = m == null ? "Niveau maximal atteint."
                    : m.species != probe.species ? "3 exemplaires → évolue en " + m.species.name
                    : "3 exemplaires → " + m.stars + "★";
            g.text(regular(13), evo, x, ty + 22, SOFT);
            boolean ghost = mt == PType.GHOST;
            if (ghost) g.text(regular(13), "Vise toujours l'ennemi le plus affaibli.", x, ty + 44, SOFT);
            if (owned && unit != null) g.text(regular(13), "Revente : " + unit.invested + " or", x, ty + (ghost ? 66 : 44), GOLD);
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

    private void statLine(Canvas g, String label, int value, int max, float x, float y) {
        g.text(regular(13), label, x, y + 12, MUTED);
        if (value < 0) return;
        g.round(x + 110, y + 2, 200, 12, TRACK);
        g.round(x + 110, y + 2, 200 * Math.min(1f, value / (float) max), 12, BLUE);
        g.text(bold(13), String.valueOf(value), x + 320, y + 13, TEXT);
    }

    private void drawBench(Canvas g) {
        g.text(bold(12), "BANC", BENCH_X, BENCH_Y - 6, MUTED);
        for (int i = 0; i < GameState.BENCH; i++) {
            Rect r = slotRect(i);
            g.round(r.x, r.y, r.w, r.h, PANEL);
            if (dragging && slotAt(mx, my) == i) highlight(g, r, GOLD);
            Unit u = state.bench[i];
            if (u != null && !(dragging && pressSlot == i)) drawUnitInCell(g, u, r, false, false, selected == i, false);
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
            BitmapFont font = bold(f.big ? 15 : 13);
            g.centered(font, f.text, f.x + 1, f.y + 1, g.fade(BLACK, 0.6f * a));
            g.centered(font, f.text, f.x, f.y, g.fade(f.color, a));
        }
    }

    private void drawOverlay(Canvas g) {
        if (state.phase != GameState.Phase.RESULT && state.phase != GameState.Phase.GAMEOVER) return;
        Rect a = arena();
        float bw = 300, bh = 120;
        float x = a.x + (a.w - bw) / 2, y = a.y + (a.h - bh) / 2;
        g.rect(a.x - 4, a.y - 4, a.w + 8, a.h + 8, g.fade(BLACK, 0.47f));
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
        drawCreature(g, u.species, mx, my, 24, false);
        drawStars(g, u.stars, mx, my - 28);
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
