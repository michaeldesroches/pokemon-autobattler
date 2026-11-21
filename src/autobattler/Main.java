package autobattler;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Point d'entrée. Avec l'argument --web (utilisé par la page CheerpJ), la fenêtre est sans bordure, collée en haut à gauche. */
public final class Main {
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean web = args.length > 0 && "--web".equals(args[0]);
                JFrame frame = new JFrame("Pokémon Auto Battler");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setUndecorated(web);
                frame.setResizable(false);
                frame.setContentPane(new GamePanel());
                frame.pack();
                if (web) frame.setLocation(0, 0);
                else frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
