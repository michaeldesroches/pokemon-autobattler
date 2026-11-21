package autobattler.gfx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** Point d'entrée libGDX commun au desktop (LWJGL3) et au web (TeaVM). */
public final class AutoBattlerGame extends ApplicationAdapter {
    private SpriteBatch batch;
    private FitViewport viewport;
    private Assets assets;
    private GameView view;
    private Canvas canvas;
    private PmdSprites pmd;
    private Sounds sounds;

    @Override
    public void create() {
        batch = new SpriteBatch();
        viewport = new FitViewport(GameView.W, GameView.H);
        assets = new Assets();
        pmd = new PmdSprites();
        sounds = new Sounds();
        canvas = new Canvas(batch, assets, GameView.H);
        view = new GameView(assets, pmd, sounds, viewport);
        Gdx.input.setInputProcessor(view);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        float dt = Math.min(0.1f, Gdx.graphics.getDeltaTime());
        view.update(dt);
        ScreenUtils.clear(GameView.BG);
        viewport.apply();
        // tremblement d'écran : on décale la caméra
        viewport.getCamera().position.set(GameView.W / 2f + view.shakeX(), GameView.H / 2f + view.shakeY(), 0);
        viewport.getCamera().update();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        view.draw(canvas);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        assets.dispose();
        pmd.dispose();
        sounds.dispose();
    }
}
