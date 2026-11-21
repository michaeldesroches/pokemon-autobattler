package autobattler.teavm;

import autobattler.gfx.AutoBattlerGame;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

/** Lanceur web (TeaVM) : la page occupe toute la fenêtre, le jeu est mis à l'échelle (FitViewport). */
public final class WebLauncher {
    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = 0;
        config.height = 0;
        config.usePhysicalPixels = true;
        config.antialiasing = true;
        new WebApplication(new AutoBattlerGame(), config);
    }
}
