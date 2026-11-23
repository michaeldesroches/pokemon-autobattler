package autobattler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Lobby de 8 joueurs : le joueur (indice 0) et 7 bots, avec un pool partagé.
 * À chaque manche JcJ, les survivants sont appariés (si possible pas contre l'adversaire de la manche
 * précédente) ; s'ils sont en nombre impair, l'un d'eux affronte le « fantôme » d'un autre (sans dégâts
 * pour le propriétaire). Les combats entre bots sont simulés sans affichage à la fin du combat du joueur.
 */
public final class Lobby {
    public static final int SIZE = 8;
    /** Champions d'arène de Kanto (noms français). */
    private static final String[] NAMES = {"Pierre", "Ondine", "Major Bob", "Érika", "Koga", "Morgane", "Auguste", "Giovanni"};

    private final Random rng;
    private final Pool pool = new Pool();
    private final List<GameState> players = new ArrayList<GameState>();
    private final Bot[] bots = new Bot[SIZE];
    private final int[] opponent = new int[SIZE];
    private final int[] lastOpponent = new int[SIZE];
    private final boolean[] ghost = new boolean[SIZE];
    /** Pilote du joueur 0 pour la simulation (null en jeu : c'est l'humain qui joue). */
    private final Bot humanBot;

    public Lobby(long seed) {
        this(seed, null);
    }

    /** {@code humanBot} non nul : le joueur 0 est lui aussi joué par un bot (simulation). */
    public Lobby(long seed, Bot humanBot) {
        this.humanBot = humanBot;
        rng = new Random(seed);
        List<String> names = new ArrayList<String>();
        for (String n : NAMES) names.add(n);
        Collections.shuffle(names, rng);
        players.add(new GameState(rng.nextLong(), pool, this, "Toi"));
        for (int i = 1; i < SIZE; i++) {
            players.add(new GameState(rng.nextLong(), pool, this, names.get(i - 1)));
            bots[i] = Bot.random(rng);
        }
        restart();
    }

    public GameState human() {
        return players.get(0);
    }

    public List<GameState> players() {
        return Collections.unmodifiableList(players);
    }

    public Bot bot(int i) {
        return bots[i];
    }

    public int index(GameState p) {
        return players.indexOf(p);
    }

    /** Adversaire de la manche (-1 contre des Pokémon sauvages). */
    public int opponentOf(GameState p) {
        return opponent[index(p)];
    }

    public int aliveCount() {
        int n = 0;
        for (GameState p : players) if (p.placement == 0) n++;
        return n;
    }

    /** Relance une partie complète. */
    void restart() {
        pool.fill();
        for (GameState p : players) p.reset();
        java.util.Arrays.fill(lastOpponent, -1);
        startRound();
    }

    /** Appelé par le joueur 0 quand il passe à la manche suivante. */
    void afterNextRound(GameState p) {
        if (p == human()) startRound();
    }

    /** Les bots passent à la manche suivante, préparent leur équipe, puis on apparie. */
    private void startRound() {
        for (int i = 1; i < SIZE; i++) {
            GameState b = players.get(i);
            if (b.placement != 0) continue;
            if (b.phase == GameState.Phase.RESULT) b.nextRound();
            bots[i].playPrep(b);
        }
        if (humanBot != null && human().placement == 0) humanBot.playPrep(human());
        pair();
    }

    private void pair() {
        java.util.Arrays.fill(opponent, -1);
        java.util.Arrays.fill(ghost, false);
        if (GameState.isPveRound(human().round)) return;
        List<Integer> alive = new ArrayList<Integer>();
        for (int i = 0; i < SIZE; i++) if (players.get(i).placement == 0) alive.add(i);
        if (alive.size() < 2) return;
        // plusieurs mélanges, on garde celui qui répète le moins d'affrontements de la manche précédente
        List<Integer> best = null;
        int bestRepeats = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 30 && bestRepeats > 0; attempt++) {
            List<Integer> order = new ArrayList<Integer>(alive);
            Collections.shuffle(order, rng);
            int repeats = 0;
            for (int k = 0; k + 1 < order.size(); k += 2)
                if (lastOpponent[order.get(k)] == order.get(k + 1)) repeats++;
            if (repeats < bestRepeats) {
                bestRepeats = repeats;
                best = order;
            }
        }
        for (int k = 0; k + 1 < best.size(); k += 2) {
            opponent[best.get(k)] = best.get(k + 1);
            opponent[best.get(k + 1)] = best.get(k);
        }
        if (best.size() % 2 == 1) {
            int lone = best.get(best.size() - 1);
            int copy = best.get(rng.nextInt(best.size() - 1));
            opponent[lone] = copy;
            ghost[lone] = true;
        }
        for (int i : alive) {
            GameState p = players.get(i);
            int o = opponent[i];
            p.enemy.clear();
            p.enemy.addAll(players.get(o).playerTeam());
            p.opponentName = players.get(o).name;
            p.opponentGhost = ghost[i];
            lastOpponent[i] = o;
        }
    }

    /** Appelé quand le combat du joueur 0 est fini : résout tous les autres combats de la manche. */
    void afterCombat(GameState p) {
        if (p != human()) return;
        GameState h = human();
        boolean[] done = new boolean[SIZE];
        done[0] = true;
        int ho = opponent[0];
        if (ho > 0 && !ghost[0]) {
            mirror(players.get(ho), h);
            done[ho] = true;
        }
        for (int i = 1; i < SIZE; i++) {
            GameState b = players.get(i);
            if (done[i] || b.placement != 0) continue;
            done[i] = true;
            fightHeadless(b);
            int o = opponent[i];
            if (o > 0 && !ghost[i] && !done[o]) {
                mirror(players.get(o), b);
                done[o] = true;
            }
        }
        eliminate();
    }

    /** Combat sans affichage du joueur {@code b} contre son adversaire de la manche. */
    private void fightHeadless(GameState b) {
        int o = opponent[index(b)];
        if (o >= 0) {
            // l'équipe adverse telle qu'elle est maintenant (le joueur a pu la modifier après l'appariement)
            b.enemy.clear();
            b.enemy.addAll(players.get(o).playerTeam());
        }
        if (!b.startCombat()) {
            // plateau vide : défaite d'office
            b.applyOutcome(false, b.enemy.size(), false);
            return;
        }
        b.battle.runToEnd();
        b.applyOutcome(b.battle.result > 0, b.battle.aliveCount(false), b.battle.time() >= Battle.TIME_LIMIT);
    }

    /** Applique à {@code other} le résultat inverse du combat de {@code fought} (même combat vu d'en face). */
    private void mirror(GameState other, GameState fought) {
        Battle bt = fought.battle;
        if (bt == null) {
            // fought n'a pas pu combattre (plateau vide) : other gagne
            other.applyOutcome(true, 0, false);
            return;
        }
        boolean timeUp = bt.time() >= Battle.TIME_LIMIT;
        // au temps limite, les deux perdent
        boolean otherWins = !timeUp && bt.result < 0;
        other.applyOutcome(otherWins, bt.aliveCount(true), timeUp);
    }

    /** Joueurs tombés à 0 PV : classement, Pokémon rendus au pool, fin de partie éventuelle. */
    private void eliminate() {
        List<GameState> fallen = new ArrayList<GameState>();
        for (GameState p : players) if (p.placement == 0 && p.hp <= 0) fallen.add(p);
        // ceux qui ont subi le plus de dégâts ce tour-ci finissent derrière
        Collections.sort(fallen, new java.util.Comparator<GameState>() {
            @Override
            public int compare(GameState a, GameState b) {
                return b.lastDamage - a.lastDamage;
            }
        });
        int alive = aliveCount();
        StringBuilder news = new StringBuilder();
        for (GameState p : fallen) {
            p.placement = alive--;
            p.phase = GameState.Phase.GAMEOVER;
            if (p != human()) {
                p.releaseAll();
                news.append(news.length() == 0 ? "" : ", ").append(p.name);
            }
        }
        GameState h = human();
        if (h.placement == 0 && aliveCount() == 1) {
            h.placement = 1;
            h.phase = GameState.Phase.GAMEOVER;
            h.message = "Victoire ! Tu remportes le lobby face aux 7 champions !";
        } else if (h.placement > 0) {
            h.message = "Éliminé : tu termines " + ordinal(h.placement) + " sur " + SIZE + ".";
        } else if (news.length() > 0) {
            h.message += " Éliminé" + (fallen.size() > 1 ? "s" : "") + " : " + news + ".";
        }
    }

    public static String ordinal(int n) {
        return n == 1 ? "1er" : n + "e";
    }
}
