package autobattler;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Rendu Java2D et interactions souris. */
public final class GamePanel extends JPanel {
    public static final int W = 1000, H = 690;

    private static final int AX = 200, AY = 58, CELL = 84, GAP = 14;
    private static final Rectangle LEFT = new Rectangle(16, 58, 170, 350);
    private static final Rectangle RIGHT = new Rectangle(570, 58, 414, 350);
    private static final int BENCH_Y = 432, BENCH_X = 16, BENCH_STEP = 88, BENCH_SIZE = 82;
    private static final int SHOP_Y = 540, SHOP_X = 16, SHOP_STEP = 124, SHOP_W = 118, SHOP_H = 136;
    private static final Rectangle SHOP_AREA = new Rectangle(SHOP_X, SHOP_Y, 5 * SHOP_STEP, SHOP_H);
    private static final Rectangle BTN_A = new Rectangle(650, 540, 160, 40);
    private static final Rectangle BTN_B = new Rectangle(824, 540, 160, 40);
    private static final Rectangle BTN_C = new Rectangle(650, 590, 160, 86);
    private static final Rectangle BTN_MAIN = new Rectangle(824, 590, 160, 86);

    private static final Color BG = new Color(0x1B1F2A);
    private static final Color PANEL = new Color(0x262C3A);
    private static final Color PANEL_LIGHT = new Color(0x323A4C);
    private static final Color TEXT = new Color(0xE8ECF4);
    private static final Color MUTED = new Color(0x8C97AD);
    private static final Color GOLD = new Color(0xF5C542);
    private static final Color ACCENT = new Color(0xE84A5F);
    private static final Color GOOD = new Color(0x4CC38A);

    private final GameState state = new GameState(System.nanoTime());
    private int speed = 1;
    private double endDelay;

    private int selected = -1;
    private int pressSlot = -1;
    private Point pressPoint;
    private boolean dragging;
    private Point mouse = new Point(-1, -1);

    private static final class Floater {
        double x, y, life;
        final String text;
        final Color color;
        final boolean big;

        Floater(double x, double y, String text, Color color, boolean big) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.big = big;
            this.life = big ? 1.2 : 0.8;
        }
    }

    private final List<Floater> floaters = new ArrayList<Floater>();

    public GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onRelease(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouse = e.getPoint();
                if (pressSlot >= 0 && pressPoint != null && pressPoint.distance(mouse) > 6) dragging = true;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);

        final long[] last = {System.nanoTime()};
        new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long now = System.nanoTime();
                double dt = Math.min(0.1, (now - last[0]) / 1e9);
                last[0] = now;
                update(dt);
                repaint();
            }
        }).start();
    }

    // ================================================================== logique d'animation

    private void update(double dt) {
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
                Point p = fighterCenter(ev.tgt);
                Color c = ev.mult > 1.01 ? new Color(0xFF9F43) : ev.mult < 0.99 ? MUTED : Color.WHITE;
                String txt = String.valueOf((int) Math.round(ev.amount)) + (ev.mult > 1.01 ? "!" : "");
                floaters.add(new Floater(p.x + rnd(10), p.y - 30, txt, c, false));
                if (ev.special && shownSpecial.add(ev.src)) {
                    Point s = fighterCenter(ev.src);
                    floaters.add(new Floater(s.x, s.y - 40, ev.src.species().moveType().move + " !", GOLD, true));
                }
                break;
            }
            case DODGE: {
                Point p = fighterCenter(ev.tgt);
                floaters.add(new Floater(p.x, p.y - 20, "Esquive", new Color(0x9AD0FF), false));
                break;
            }
            case FREEZE: {
                Point p = fighterCenter(ev.tgt);
                floaters.add(new Floater(p.x, p.y - 34, "Gelé !", PType.ICE.color, false));
                break;
            }
            default:
                break;
        }
    }

    /** Pour Séisme (attaque de zone), n'affiche le nom de l'attaque qu'une fois par lot d'événements. */
    private final java.util.Set<Fighter> shownSpecial = new java.util.HashSet<Fighter>();

    private static double rnd(double amp) {
        return (Math.random() - 0.5) * 2 * amp;
    }

    // ================================================================== géométrie

    private static Rectangle cell(int visualRow, int col) {
        return new Rectangle(AX + col * CELL, AY + visualRow * CELL + (visualRow >= 2 ? GAP : 0), CELL, CELL);
    }

    private static int visualRow(boolean player, int row) {
        return player ? 2 + row : 1 - row;
    }

    private static Rectangle slotRect(int slot) {
        if (GameState.isBoard(slot)) {
            int i = slot - 100;
            return cell(2 + i / GameState.COLS, i % GameState.COLS);
        }
        return new Rectangle(BENCH_X + slot * BENCH_STEP, BENCH_Y, BENCH_SIZE, BENCH_SIZE);
    }

    private static int slotAt(Point p) {
        for (int r = 0; r < GameState.ROWS; r++)
            for (int c = 0; c < GameState.COLS; c++) {
                int s = GameState.boardSlot(r, c);
                if (slotRect(s).contains(p)) return s;
            }
        for (int i = 0; i < GameState.BENCH; i++) if (slotRect(i).contains(p)) return i;
        return -1;
    }

    private static Rectangle shopRect(int i) {
        return new Rectangle(SHOP_X + i * SHOP_STEP, SHOP_Y, SHOP_W, SHOP_H);
    }

    private static int shopAt(Point p) {
        for (int i = 0; i < GameState.SHOP; i++) if (shopRect(i).contains(p)) return i;
        return -1;
    }

    private Point fighterCenter(Fighter f) {
        Rectangle r = cell(visualRow(f.player, f.row), f.col);
        return new Point(r.x + r.width / 2, r.y + r.height / 2 - 6);
    }

    // ================================================================== souris

    private void onPress(MouseEvent e) {
        mouse = e.getPoint();
        pressPoint = e.getPoint();
        dragging = false;
        pressSlot = -1;
        if (state.phase != GameState.Phase.PREP) return;
        int s = slotAt(e.getPoint());
        if (s >= 0 && state.get(s) != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                state.sell(s);
                if (selected == s) selected = -1;
                pressPoint = null;
                return;
            }
            pressSlot = s;
        }
    }

    private void onRelease(MouseEvent e) {
        Point p = e.getPoint();
        if (pressPoint == null) return;
        if (dragging && pressSlot >= 0) {
            int target = slotAt(p);
            if (target >= 0) state.move(pressSlot, target);
            else if (SHOP_AREA.contains(p)) state.sell(pressSlot);
            selected = -1;
        } else if (!SwingUtilities.isRightMouseButton(e)) {
            onClick(p);
        }
        dragging = false;
        pressSlot = -1;
        pressPoint = null;
    }

    private void onClick(Point p) {
        switch (state.phase) {
            case PREP:
                if (BTN_A.contains(p)) {
                    state.reroll();
                } else if (BTN_B.contains(p)) {
                    state.toggleLock();
                } else if (BTN_C.contains(p)) {
                    if (selected >= 0) state.sell(selected);
                    selected = -1;
                } else if (BTN_MAIN.contains(p)) {
                    selected = -1;
                    if (state.startCombat()) {
                        endDelay = 0;
                        floaters.clear();
                    }
                } else {
                    int shop = shopAt(p);
                    if (shop >= 0) {
                        state.buy(shop);
                        selected = -1;
                        return;
                    }
                    int s = slotAt(p);
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
                if (BTN_A.contains(p)) speed = speed == 1 ? 2 : speed == 2 ? 4 : 1;
                else if (BTN_B.contains(p) && state.battle != null) {
                    state.battle.runToEnd();
                    endDelay = 1;
                }
                break;
            case RESULT:
                if (BTN_MAIN.contains(p) || cell(0, 0).union(cell(3, 3)).contains(p)) state.nextRound();
                break;
            case GAMEOVER:
                if (BTN_MAIN.contains(p) || cell(0, 0).union(cell(3, 3)).contains(p)) {
                    state.newGame();
                    selected = -1;
                }
                break;
            default:
                break;
        }
    }

    // ================================================================== rendu

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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

    private void drawTopBar(Graphics2D g) {
        g.setColor(PANEL);
        g.fillRect(0, 0, W, 44);
        g.setFont(font(Font.BOLD, 18));
        g.setColor(TEXT);
        g.drawString("Manche " + state.round, 16, 29);

        // PV
        g.setFont(font(Font.BOLD, 13));
        g.setColor(MUTED);
        g.drawString("PV", 140, 28);
        roundRect(g, 164, 15, 150, 16, new Color(0x151922));
        double frac = state.hp / (double) GameState.START_HP;
        roundRect(g, 164, 15, (int) (150 * frac), 16, frac > 0.5 ? GOOD : frac > 0.25 ? GOLD : ACCENT);
        g.setColor(Color.WHITE);
        centered(g, state.hp + " / " + GameState.START_HP, 164 + 75, 28);

        g.setColor(GOLD);
        g.setFont(font(Font.BOLD, 16));
        g.drawString("● " + state.gold + " or", 335, 29);

        g.setColor(TEXT);
        g.setFont(font(Font.PLAIN, 14));
        int bc = state.boardCount();
        g.setColor(bc > state.maxUnits() ? ACCENT : TEXT);
        g.drawString("Équipe " + bc + " / " + state.maxUnits(), 445, 29);
        g.setColor(TEXT);
        g.drawString("Victoires " + state.wins, 570, 29);
        if (Math.abs(state.streak) >= 2) {
            g.setColor(state.streak > 0 ? GOOD : ACCENT);
            g.drawString((state.streak > 0 ? "Série de victoires x" : "Série de défaites x") + Math.abs(state.streak), 680, 29);
        }
        g.setColor(MUTED);
        g.setFont(font(Font.BOLD, 13));
        rightAligned(g, "POKÉMON AUTO BATTLER", W - 16, 28);
    }

    private void drawArena(Graphics2D g) {
        // fond des deux camps
        Rectangle enemyZone = cell(0, 0).union(cell(1, 3));
        Rectangle playerZone = cell(2, 0).union(cell(3, 3));
        roundRect(g, enemyZone.x - 4, enemyZone.y - 4, enemyZone.width + 8, enemyZone.height + 8, new Color(0x3A2630));
        roundRect(g, playerZone.x - 4, playerZone.y - 4, playerZone.width + 8, playerZone.height + 8, new Color(0x233A33));
        g.setFont(font(Font.BOLD, 10));
        g.setColor(new Color(255, 255, 255, 90));
        g.drawString("ADVERSAIRE", enemyZone.x + 2, enemyZone.y + 10);
        g.drawString("TON ÉQUIPE", playerZone.x + 2, playerZone.y + playerZone.height - 3);

        for (int vr = 0; vr < 4; vr++)
            for (int c = 0; c < 4; c++) {
                Rectangle r = cell(vr, c);
                g.setColor(new Color(255, 255, 255, (vr + c) % 2 == 0 ? 14 : 6));
                g.fillRect(r.x, r.y, r.width, r.height);
            }

        if (state.phase != GameState.Phase.PREP && state.battle != null) {
            for (Fighter f : state.battle.fighters) drawFighter(g, f);
            return;
        }

        // préparation : ennemi en aperçu + plateau du joueur
        for (GameState.Placed p : state.enemy) {
            Rectangle r = cell(visualRow(false, p.row), p.col);
            drawUnitInCell(g, p.unit, r, true, false, 1, -1);
        }
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLS; col++) {
                int s = GameState.boardSlot(row, col);
                Rectangle r = slotRect(s);
                if (dragging && slotAt(mouse) == s) highlight(g, r, GOLD);
                Unit u = state.board[row][col];
                if (u != null && !(dragging && pressSlot == s)) drawUnitInCell(g, u, r, false, selected == s, 1, -1);
            }
    }

    private void drawFighter(Graphics2D g, Fighter f) {
        Rectangle r = cell(visualRow(f.player, f.row), f.col);
        int dy = (int) (Math.sin(f.lunge * Math.PI) * 14) * (f.player ? -1 : 1);
        Rectangle rr = new Rectangle(r.x, r.y + dy, r.width, r.height);
        float alpha = f.alive ? 1f : 0.18f;
        drawUnitInCell(g, f.unit, rr, !f.player, false, alpha, f.alive ? f.hp / f.maxHp : 0);
        if (!f.alive) return;
        // barres PV / énergie
        int bx = rr.x + 10, by = rr.y + 4, bw = rr.width - 20;
        roundRect(g, bx, by, bw, 6, new Color(0x10131A));
        double hf = f.hp / f.maxHp;
        roundRect(g, bx, by, (int) (bw * hf), 6, hf > 0.5 ? GOOD : hf > 0.25 ? GOLD : ACCENT);
        roundRect(g, bx, by + 8, bw, 3, new Color(0x10131A));
        roundRect(g, bx, by + 8, (int) (bw * Math.min(1, f.energy / 100)), 3, new Color(0x5DA9FF));
        if (f.flash > 0) {
            g.setColor(new Color(255, 80, 80, (int) (110 * f.flash)));
            g.fill(new Ellipse2D.Double(rr.x + rr.width / 2.0 - 26, rr.y + rr.height / 2.0 - 32, 52, 52));
        }
        if (f.stun > 0) {
            g.setColor(new Color(152, 216, 216, 120));
            g.fill(new Ellipse2D.Double(rr.x + rr.width / 2.0 - 28, rr.y + rr.height / 2.0 - 34, 56, 56));
        }
        if (f.poisonTime > 0) {
            g.setColor(PType.POISON.color);
            g.fillOval(rr.x + rr.width - 16, rr.y + 16, 8, 8);
        }
    }

    private void drawUnitInCell(Graphics2D g, Unit u, Rectangle r, boolean enemy, boolean sel, float alpha, double hp) {
        if (sel) highlight(g, r, GOLD);
        java.awt.Composite old = g.getComposite();
        if (alpha < 1f) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        int cx = r.x + r.width / 2, cy = r.y + r.height / 2 - 4;
        drawCreature(g, u.species, cx, cy, 22, enemy);
        drawStars(g, u.stars, cx, r.y + 26 - (hp >= 0 ? 0 : 6));
        g.setFont(font(Font.BOLD, 11));
        g.setColor(TEXT);
        centered(g, u.species.name, cx, r.y + r.height - 6);
        g.setComposite(old);
    }

    /** Petite créature stylisée : corps aux couleurs du type, yeux tournés vers l'adversaire. */
    private void drawCreature(Graphics2D g, Species sp, int cx, int cy, int rad, boolean enemy) {
        Color c1 = sp.types[0].color;
        Color c2 = sp.types.length > 1 ? sp.types[1].color : c1.darker();
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(cx - rad, cy + rad - 6, rad * 2, 10);
        g.setPaint(new GradientPaint(cx, cy - rad, c1.brighter(), cx, cy + rad, c1.darker()));
        g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
        g.setStroke(new BasicStroke(4f));
        g.setColor(c2);
        if (sp.types.length > 1) {
            g.draw(new Arc2D.Double(cx - rad + 2, cy - rad + 2, rad * 2 - 4, rad * 2 - 4, 200, 140, Arc2D.OPEN));
        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(c1.darker().darker());
        g.draw(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
        g.setStroke(new BasicStroke(1f));
        // yeux
        int look = enemy ? 3 : -3;
        int ey = cy - 4 + (enemy ? 2 : 0);
        for (int s = -1; s <= 1; s += 2) {
            int ex = cx + s * 8;
            g.setColor(Color.WHITE);
            g.fillOval(ex - 5, ey - 6, 10, 12);
            g.setColor(new Color(0x1B1B22));
            g.fillOval(ex - 3, ey - 4 + look, 6, 7);
            g.setColor(Color.WHITE);
            g.fillOval(ex - 1, ey - 3 + look, 2, 2);
        }
        // initiales (aide à reconnaître)
        g.setFont(font(Font.BOLD, 9));
        g.setColor(new Color(0, 0, 0, 120));
        centered(g, "#" + sp.dex, cx, cy + rad - 5);
    }

    private void drawStars(Graphics2D g, int stars, int cx, int y) {
        if (stars <= 1) return;
        g.setFont(font(Font.BOLD, 12));
        g.setColor(GOLD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars; i++) sb.append('★');
        centered(g, sb.toString(), cx, y);
    }

    private void drawSynergies(Graphics2D g) {
        roundRect(g, LEFT.x, LEFT.y, LEFT.width, LEFT.height, PANEL);
        g.setFont(font(Font.BOLD, 14));
        g.setColor(TEXT);
        g.drawString("Synergies", LEFT.x + 12, LEFT.y + 22);
        List<Unit> units = state.boardUnits();
        Map<PType, Integer> counts = Synergies.counts(units);
        Map<PType, Integer> levels = Synergies.levels(units);
        if (counts.isEmpty()) {
            g.setFont(font(Font.PLAIN, 12));
            g.setColor(MUTED);
            wrap(g, "Place des Pokémon de même type sur le plateau pour activer des bonus.", LEFT.x + 12, LEFT.y + 46, LEFT.width - 24, 16);
            return;
        }
        List<PType> order = new ArrayList<PType>(counts.keySet());
        final Map<PType, Integer> lv = levels, ct = counts;
        java.util.Collections.sort(order, new java.util.Comparator<PType>() {
            @Override
            public int compare(PType a, PType b) {
                int la = lv.containsKey(a) ? 1 : 0, lb = lv.containsKey(b) ? 1 : 0;
                if (la != lb) return lb - la;
                return ct.get(b) - ct.get(a);
            }
        });
        int y = LEFT.y + 36;
        for (PType t : order) {
            if (y > LEFT.y + LEFT.height - 30) break;
            Integer lvl = levels.get(t);
            boolean active = lvl != null;
            int[] th = Synergies.thresholds(t);
            StringBuilder ths = new StringBuilder();
            for (int i = 0; i < th.length; i++) ths.append(i == 0 ? "" : " · ").append(th[i]);
            typeBadge(g, t, LEFT.x + 10, y, active ? 1f : 0.45f);
            g.setFont(font(Font.BOLD, 12));
            g.setColor(active ? TEXT : MUTED);
            g.drawString(counts.get(t) + "  (" + ths + ")", LEFT.x + 82, y + 13);
            if (active) {
                g.setFont(font(Font.PLAIN, 11));
                g.setColor(new Color(0xB9C3D6));
                g.drawString(Synergies.describe(t, lvl), LEFT.x + 12, y + 31);
                y += 42;
            } else {
                y += 26;
            }
        }
    }

    private void drawInfo(Graphics2D g) {
        roundRect(g, RIGHT.x, RIGHT.y, RIGHT.width, RIGHT.height, PANEL);
        int x = RIGHT.x + 16, y = RIGHT.y + 28;

        Unit unit = null;
        Species sp = null;
        boolean owned = false;
        int hoverSlot = slotAt(mouse);
        int hoverShop = shopAt(mouse);
        if (state.phase == GameState.Phase.PREP && hoverShop >= 0 && state.shop[hoverShop] != null) {
            sp = state.shop[hoverShop];
        } else if (state.phase == GameState.Phase.PREP && hoverSlot >= 0 && state.get(hoverSlot) != null) {
            unit = state.get(hoverSlot);
            owned = true;
        } else if (selected >= 0 && state.get(selected) != null) {
            unit = state.get(selected);
            owned = true;
        } else if (state.phase == GameState.Phase.PREP) {
            for (GameState.Placed p : state.enemy)
                if (cell(visualRow(false, p.row), p.col).contains(mouse)) unit = p.unit;
        }
        if (unit != null) sp = unit.species;

        if (sp == null) {
            g.setFont(font(Font.BOLD, 16));
            g.setColor(TEXT);
            g.drawString("Comment jouer", x, y);
            g.setFont(font(Font.PLAIN, 13));
            g.setColor(new Color(0xC5CEDF));
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
            int ly = y + 26;
            for (String l : lines) {
                g.drawString(l, x, ly);
                ly += 20;
            }
        } else {
            int stars = unit == null ? 1 : unit.stars;
            g.setFont(font(Font.BOLD, 20));
            g.setColor(TEXT);
            g.drawString(sp.name + (stars > 1 ? "  " + stars + "★" : ""), x, y);
            g.setFont(font(Font.PLAIN, 12));
            g.setColor(MUTED);
            rightAligned(g, "N° " + sp.dex + " · Tier " + sp.tier + " · " + sp.tier + " or", RIGHT.x + RIGHT.width - 16, y);
            int bx = x;
            for (PType t : sp.types) {
                typeBadge(g, t, bx, y + 12, 1f);
                bx += 72;
            }
            Fighter f = new Fighter(unit != null ? unit : new Unit(sp, 1, sp.tier), true, 0, 0, null);
            int sy = y + 56;
            statLine(g, "PV", (int) f.maxHp, 700, x, sy);
            statLine(g, "Attaque", (int) f.atk, 300, x, sy + 24);
            statLine(g, "Défense", (int) f.def, 130, x, sy + 48);
            statLine(g, "Attaques / s", -1, 0, x, sy + 72);
            g.setColor(TEXT);
            g.setFont(font(Font.BOLD, 13));
            g.drawString(String.format("%.2f", 1 / f.interval), x + 110, sy + 72 + 12);

            g.setFont(font(Font.PLAIN, 13));
            g.setColor(new Color(0xC5CEDF));
            int ty = sy + 112;
            PType mt = sp.moveType();
            String special = mt == PType.GROUND ? "touche tous les ennemis" : mt == PType.PSYCHIC ? "dégâts x3"
                    : mt == PType.GRASS ? "dégâts x2.5 et soigne 20%" : "dégâts x2.5";
            g.drawString("Spéciale à 100 d'énergie : " + mt.move, x, ty);
            g.setColor(MUTED);
            g.drawString("  (" + special + ")", x, ty + 18);
            g.setColor(new Color(0xC5CEDF));
            ty += 18;
            Unit probe = unit != null ? unit : new Unit(sp, 1, sp.tier);
            Unit m = probe.merged();
            String evo = m == null ? "Niveau maximal atteint."
                    : m.species != probe.species ? "3 exemplaires → évolue en " + m.species.name
                    : "3 exemplaires → " + m.stars + "★";
            g.drawString(evo, x, ty + 22);
            if (sp.moveType() == PType.GHOST) g.drawString("Vise toujours l'ennemi le plus affaibli.", x, ty + 44);
            if (owned && unit != null) {
                g.setColor(GOLD);
                g.drawString("Revente : " + unit.invested + " or", x, ty + (sp.moveType() == PType.GHOST ? 66 : 44));
            }
        }

        if (state.message != null && !state.message.isEmpty()) {
            roundRect(g, RIGHT.x + 8, RIGHT.y + RIGHT.height - 40, RIGHT.width - 16, 32, PANEL_LIGHT);
            g.setFont(font(Font.BOLD, 13));
            g.setColor(GOLD);
            g.drawString(state.message, RIGHT.x + 18, RIGHT.y + RIGHT.height - 19);
        }
    }

    private void statLine(Graphics2D g, String label, int value, int max, int x, int y) {
        g.setFont(font(Font.PLAIN, 13));
        g.setColor(MUTED);
        g.drawString(label, x, y + 12);
        if (value < 0) return;
        roundRect(g, x + 110, y + 2, 200, 12, new Color(0x151922));
        roundRect(g, x + 110, y + 2, (int) (200 * Math.min(1, value / (double) max)), 12, new Color(0x5DA9FF));
        g.setFont(font(Font.BOLD, 13));
        g.setColor(TEXT);
        g.drawString(String.valueOf(value), x + 320, y + 13);
    }

    private void drawBench(Graphics2D g) {
        g.setFont(font(Font.BOLD, 12));
        g.setColor(MUTED);
        g.drawString("BANC", BENCH_X, BENCH_Y - 6);
        for (int i = 0; i < GameState.BENCH; i++) {
            Rectangle r = slotRect(i);
            roundRect(g, r.x, r.y, r.width, r.height, PANEL);
            if (dragging && slotAt(mouse) == i) highlight(g, r, GOLD);
            Unit u = state.bench[i];
            if (u != null && !(dragging && pressSlot == i)) drawUnitInCell(g, u, r, false, selected == i, 1, -1);
        }
    }

    private void drawShop(Graphics2D g) {
        g.setFont(font(Font.BOLD, 12));
        g.setColor(MUTED);
        g.drawString(dragging ? "DÉPOSE ICI POUR VENDRE" : "BOUTIQUE" + (state.shopLocked ? "  (verrouillée)" : ""), SHOP_X, SHOP_Y - 6);
        if (dragging) highlight(g, SHOP_AREA, ACCENT);
        for (int i = 0; i < GameState.SHOP; i++) {
            Rectangle r = shopRect(i);
            Species sp = state.shop[i];
            Color tierColor = sp == null ? PANEL : sp.tier == 1 ? new Color(0x7D8799) : sp.tier == 2 ? new Color(0x3FA36B) : new Color(0x9B59D0);
            roundRect(g, r.x, r.y, r.width, r.height, PANEL);
            if (sp == null) continue;
            boolean hover = r.contains(mouse) && state.phase == GameState.Phase.PREP;
            boolean afford = state.gold >= sp.tier;
            g.setColor(tierColor);
            g.setStroke(new BasicStroke(hover ? 3f : 2f));
            g.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 14, 14);
            g.setStroke(new BasicStroke(1f));
            drawCreature(g, sp, r.x + r.width / 2, r.y + 40, 24, false);
            g.setFont(font(Font.BOLD, 13));
            g.setColor(TEXT);
            centered(g, sp.name, r.x + r.width / 2, r.y + 84);
            int tx = r.x + (r.width - (sp.types.length * 54 - 4)) / 2;
            for (PType t : sp.types) {
                smallBadge(g, t, tx, r.y + 92);
                tx += 54;
            }
            g.setFont(font(Font.BOLD, 13));
            g.setColor(afford ? GOLD : ACCENT);
            centered(g, "● " + sp.tier, r.x + r.width / 2, r.y + 128);
            if (state.phase != GameState.Phase.PREP) {
                g.setColor(new Color(27, 31, 42, 150));
                g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            }
        }
    }

    private void drawButtons(Graphics2D g) {
        switch (state.phase) {
            case PREP:
                button(g, BTN_A, "Relancer  ● 1", state.gold >= 1, PANEL_LIGHT);
                button(g, BTN_B, state.shopLocked ? "Déverrouiller" : "Verrouiller", true, state.shopLocked ? new Color(0x5A4A20) : PANEL_LIGHT);
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

    private void drawFloaters(Graphics2D g) {
        for (Floater f : floaters) {
            float a = (float) Math.max(0, Math.min(1, f.life / 0.4));
            g.setFont(font(Font.BOLD, f.big ? 15 : 14));
            g.setColor(new Color(0, 0, 0, (int) (160 * a)));
            centered(g, f.text, (int) f.x + 1, (int) f.y + 1);
            g.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), (int) (255 * a)));
            centered(g, f.text, (int) f.x, (int) f.y);
        }
    }

    private void drawOverlay(Graphics2D g) {
        if (state.phase != GameState.Phase.RESULT && state.phase != GameState.Phase.GAMEOVER) return;
        Rectangle a = cell(0, 0).union(cell(3, 3));
        int bw = 300, bh = 120;
        int x = a.x + (a.width - bw) / 2, y = a.y + (a.height - bh) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(a.x - 4, a.y - 4, a.width + 8, a.height + 8);
        roundRect(g, x, y, bw, bh, PANEL);
        boolean over = state.phase == GameState.Phase.GAMEOVER;
        g.setFont(font(Font.BOLD, 24));
        g.setColor(over ? ACCENT : state.lastWin ? GOOD : ACCENT);
        centered(g, over ? "Partie terminée" : state.lastWin ? "Victoire !" : "Défaite", x + bw / 2, y + 42);
        g.setFont(font(Font.PLAIN, 14));
        g.setColor(TEXT);
        centered(g, over ? "Manche " + state.round + " atteinte · " + state.wins + " victoires"
                : state.lastWin ? "Tes Pokémon ont gagné la manche." : "Tu perds " + state.lastDamage + " PV.", x + bw / 2, y + 72);
        g.setColor(MUTED);
        centered(g, "Clique pour " + (over ? "rejouer" : "continuer"), x + bw / 2, y + 100);
    }

    private void drawDragged(Graphics2D g) {
        if (!dragging || pressSlot < 0) return;
        Unit u = state.get(pressSlot);
        if (u == null) return;
        drawCreature(g, u.species, mouse.x, mouse.y, 24, false);
        drawStars(g, u.stars, mouse.x, mouse.y - 28);
    }

    // ================================================================== helpers de dessin

    private static final Map<String, Font> FONTS = new java.util.HashMap<String, Font>();

    private static Font font(int style, int size) {
        String k = style + ":" + size;
        Font f = FONTS.get(k);
        if (f == null) {
            f = new Font(Font.SANS_SERIF, style, size);
            FONTS.put(k, f);
        }
        return f;
    }

    private static void roundRect(Graphics2D g, int x, int y, int w, int h, Color c) {
        if (w <= 0 || h <= 0) return;
        g.setColor(c);
        g.fillRoundRect(x, y, w, h, Math.min(14, h), Math.min(14, h));
    }

    private static void highlight(Graphics2D g, Rectangle r, Color c) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 12, 12);
        g.setStroke(new BasicStroke(1f));
    }

    private static void centered(Graphics2D g, String s, int cx, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
    }

    private static void rightAligned(Graphics2D g, String s, int right, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, right - fm.stringWidth(s), baseline);
    }

    private static void wrap(Graphics2D g, String text, int x, int y, int width, int lh) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(test) > width && line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += lh;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, y);
    }

    private static void typeBadge(Graphics2D g, PType t, int x, int y, float alpha) {
        Color c = t.color;
        roundRect(g, x, y, 66, 18, new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * alpha)));
        g.setFont(font(Font.BOLD, 11));
        g.setColor(new Color(255, 255, 255, (int) (255 * Math.max(0.6f, alpha))));
        centered(g, t.label, x + 33, y + 13);
    }

    private static void smallBadge(Graphics2D g, PType t, int x, int y) {
        roundRect(g, x, y, 50, 15, t.color);
        g.setFont(font(Font.BOLD, 10));
        g.setColor(Color.WHITE);
        centered(g, t.label, x + 25, y + 11);
    }

    private void button(Graphics2D g, Rectangle r, String label, boolean enabled, Color base) {
        boolean hover = enabled && r.contains(mouse);
        Color c = enabled ? (hover ? base.brighter() : base) : new Color(0x2A2F3B);
        roundRect(g, r.x, r.y, r.width, r.height, c);
        g.setFont(font(Font.BOLD, r.height > 60 ? 20 : 14));
        g.setColor(enabled ? Color.WHITE : MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
    }
}
