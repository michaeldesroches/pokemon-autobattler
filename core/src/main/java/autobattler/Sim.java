package autobattler;

/**
 * Outil d'équilibrage sans interface : un bot simple joue des parties complètes.
 * Usage : java -cp autobattler.jar autobattler.Sim [nbParties]
 */
public final class Sim {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("lobby")) {
            lobby(args.length > 1 ? Integer.parseInt(args[1]) : 200);
            return;
        }
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int totalRounds = 0, maxRound = 0;
        int[] reached = new int[40];
        for (int g = 0; g < games; g++) {
            GameState s = new GameState(g * 7919L + 1);
            while (s.phase != GameState.Phase.GAMEOVER && s.round < 39) {
                playPrep(s);
                if (!s.startCombat()) break;
                s.battle.runToEnd();
                s.finishCombat();
                if (s.phase == GameState.Phase.RESULT) s.nextRound();
            }
            totalRounds += s.round;
            maxRound = Math.max(maxRound, s.round);
            reached[s.round]++;
        }
        System.out.printf("Parties : %d, manche moyenne atteinte : %.1f, max : %d%n",
                games, totalRounds / (double) games, maxRound);
        StringBuilder sb = new StringBuilder("Distribution : ");
        for (int r = 1; r < reached.length; r++) if (reached[r] > 0) sb.append(r).append('=').append(reached[r]).append(' ');
        System.out.println(sb);
    }

    /** Lobbys de 8 où le joueur 0 est le bot naïf : classement moyen et durée des parties. */
    static void lobby(int games) {
        int placementSum = 0, endRoundSum = 0, wins = 0, top4 = 0;
        int[] byPlace = new int[Lobby.SIZE + 1];
        for (int g = 0; g < games; g++) {
            Lobby l = new Lobby(g * 104729L + 3, Bot.naive());
            GameState h = l.human();
            while (h.phase != GameState.Phase.GAMEOVER && h.round < 60) {
                if (!h.startCombat()) break;
                h.battle.runToEnd();
                h.finishCombat();
                if (h.phase == GameState.Phase.RESULT) h.nextRound();
            }
            placementSum += h.placement;
            byPlace[h.placement]++;
            endRoundSum += h.round;
            if (h.placement == 1) wins++;
            if (h.placement <= 4) top4++;
        }
        System.out.printf("Lobbys : %d, classement moyen du bot naïf : %.2f, victoires %d%%, top 4 %d%%, fin à la manche %.1f%n",
                games, placementSum / (double) games, wins * 100 / games, top4 * 100 / games, endRoundSum / (double) games);
        StringBuilder sb = new StringBuilder("Classements : ");
        for (int p = 1; p <= Lobby.SIZE; p++) sb.append(Lobby.ordinal(p)).append('=').append(byPlace[p]).append(' ');
        System.out.println(sb);
    }

    static void playPrep(GameState s) {
        NAIVE.playPrep(s);
    }

    private static final Bot NAIVE = Bot.naive();
}
