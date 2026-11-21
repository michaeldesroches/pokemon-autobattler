package autobattler.lwjgl3;

import autobattler.gfx.AutoBattlerGame;
import autobattler.gfx.GameView;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Lanceur desktop : ./gradlew :lwjgl3:run */
public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pokémon Auto Battler");
        config.setWindowedMode(GameView.W, GameView.H);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new AutoBattlerGame(), config);
    }
}
