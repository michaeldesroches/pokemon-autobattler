package autobattler.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Bruitages et musique (générés par tools/gen_audio.py). La musique démarre à la première interaction. */
public final class Sounds implements Disposable {
    private static final String[] NAMES = {"hit", "crit", "ability", "buy", "merge", "levelup", "win", "lose", "click", "faint"};

    private final Map<String, Sound> sounds = new HashMap<String, Sound>();
    private final Map<String, Long> lastPlay = new HashMap<String, Long>();
    private Music music;
    private boolean enabled = true;
    private boolean started;

    public Sounds() {
        try {
            for (String n : NAMES) sounds.put(n, Gdx.audio.newSound(Gdx.files.internal("sfx/" + n + ".wav")));
            music = Gdx.audio.newMusic(Gdx.files.internal("music/theme.mp3"));
            music.setLooping(true);
            music.setVolume(0.35f);
        } catch (Throwable t) {
            Gdx.app.error("Sounds", "Audio indisponible : " + t.getMessage());
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
        if (music == null) return;
        if (enabled) music.play();
        else music.pause();
    }

    /** À appeler lors d'une interaction (les navigateurs bloquent l'audio avant). */
    public void userGesture() {
        if (started || music == null) return;
        started = true;
        if (enabled) music.play();
    }

    public void play(String name, float volume) {
        play(name, volume, 0);
    }

    /** Joue un son, au plus une fois toutes les {@code minGapMs} millisecondes. */
    public void play(String name, float volume, long minGapMs) {
        if (!enabled) return;
        Sound s = sounds.get(name);
        if (s == null) return;
        long now = System.currentTimeMillis();
        Long last = lastPlay.get(name);
        if (minGapMs > 0 && last != null && now - last < minGapMs) return;
        lastPlay.put(name, now);
        s.play(volume);
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
        if (music != null) music.dispose();
    }
}
